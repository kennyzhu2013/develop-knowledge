---
id: qc-rules
title: 语音质检 - 规则体系
domain: call_qc
kind: dynamic
audience: [developer, qa, architect]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-overview, qc-orchestration, fraud-overview, ai-model-catalog]
source_of_truth:
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
  - vi-assets/qa-server-master/apps/service.py
  - vi-assets/qa-server-master/apps/strategy.py
---

# 质检规则体系

专线质检的“规则”不在一个规则引擎 JAR 里，而是三层：

| 层 | 事实源 | 作用 |
| --- | --- | --- |
| 流程编排 | scheduler 的 XML（prod：`zhuanxian_qc.xml`） | 决定先跑哪些模型、什么条件下判风险 |
| 词包 / 话术 / 正则 | qa-server 维护，任务参数带给 scheduler | 本地关键词、关键词组合、正则、高危禁词 |
| 客户策略 | `user_qc.qa_strategy_config` | 质检结果为异常时，按风险等级附加管控字段 |
| 抽检路由 | `customer_tab` 的 T1/T2 与抽样比例 | 决定 realtime / near_realtime / offline / no_ai |

知识库里的 `02-call-qc/fraud/rules.yaml` 是给 Agent 用的结构化索引，不是运行时加载的文件。改线上行为要改 XML、词包或策略表，并同步这里。

## 1. 专线离线流程里的判定

以 `zhuanxian_qc.xml` 为准：

1. ASR 文本长度不超过 45 字则失败结束。
2. 脱敏后并行做本地关键词和高阈值关键词反诈。
3. 命中后走 Qwen，再走 RAG。`ragResults[0].score ≥ 0.9` 记高风险 `riskLevel=1`。
4. 未走到高风险时走融通。`firstLabel==4` 记低风险 `3`。
5. DeepSeek `result==1` 记中风险 `2`。
6. 回调属性里的 `modelQcResult`、`riskLevel` 由 XML 的 `attribute` 动作写入。

二期 `zhuanxian_qc_2.xml` 在 dev 目录，增加分片串行和 `interruptShard`。反诈提示词写在 XML 里，不在独立 Prompt 服务。

## 2. qa-server 侧

- 词包：`/v1/keyword/create|update`，表在质检库 `qa_word_package`。
- 话术：`/v1/script/create|update`。
- 企业开通时按场景 `enterprise_scene` 克隆规则。
- 结果消费时，融通 `svcType=0` 且 `firstLabel=4` 记为涉诈（`rtDetectionResult=0`）。
- 失败重试最多 5 次，重试期间不把中间失败回调给客户。

## 3. 改规则时要一起改的东西

- scheduler XML 与 MongoDB `process.aiModels`。
- qa-server 规则缓存和 ETCD 版本 key（`/config/version` 一类，以 `06-data/etcd/keys.yaml` 为准）。
- 本目录 `fraud/rules.yaml`、`fraud/fraud-patterns.yaml`（若模式定义变了）。
- 样例不得含真实号码和真实转写。
