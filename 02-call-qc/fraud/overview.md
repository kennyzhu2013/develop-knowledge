---
id: fraud-overview
title: 涉诈识别 - 总览
domain: call_qc
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-rules, ai-model-catalog, ai-rag, ai-llm, qc-orchestration]
source_of_truth:
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
  - vi-assets/qa-server-master/apps/result_consumer.py
---

# 涉诈识别

本次仓库里没有名为 fraud-agent 的服务。涉诈识别是 scheduler 流程里的一组模型适配器，由 XML 编排，结果回到 qa-server。

## 1. 运行时怎么判

专线离线流程 `zhuanxian_qc.xml`：

```text
ASR → 脱敏 → 关键词 / 关键词反诈
    → Qwen → RAG（高相似记高风险）
    → 融通（特定标签记低风险）
    → DeepSeek（命中记中风险）
```

模型队列与字段见 `03-ai/model-catalog.md`。RAG 的诈骗类型说明写在流程 XML 的 DeepSeek prompt 分支里，也写在 RAG 命中结果的 `metadata.note` 里。

qa-server 消费结果时，把融通 `svcType="0"` 且 `firstLabel="4"` 视为涉诈。风险等级 1 或 2 时会再 POST 到配置项 `STRATEGY_CONFIG_URL`（接收方待确认）。

## 2. 知识库里的三层模型

下面这套 ID 是知识库索引，方便人和 Agent 对齐模式、行为、规则。它不会被 scheduler 加载。

```text
诈骗模式 (FP-*) ──包含──> 风险行为 (RB-*) ──被检测于──> 规则 (RULE-FRAUD-*)
```

| 文件 | 内容 |
| --- | --- |
| `fraud-patterns.yaml` | 诈骗模式 |
| `risk-behaviors.yaml` | 可观测风险行为 |
| `rules.yaml` | 检测规则，应能指回 XML 动作或词包 |
| `cases/` | 脱敏案例 |

新增模式时用 Skill `add-fraud-pattern`：先改这里的 YAML，再改 scheduler XML 或 qa-server 词包。只改 YAML 不会改变线上判定。
