---
id: qc-overview
title: 语音质检 - 总览
domain: call_qc
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-pipeline, qc-realtime, qc-rules, fraud-overview, svc-call-qc-service]
---

# 语音质检总览

## 1. 是什么

TODO：质检的对象（哪些通话、抽检还是全量）、目的（涉诈、投诉、骚扰、服务质量）、输出（风险事件、质检报告）。

## 2. 质检类型

| 类型 | 触发 | 时效 | 文档 |
| --- | --- | --- | --- |
| 离线质检 | 通话结束 | TODO | `pipeline.md` |
| 实时质检 | 通话中 | TODO | `realtime-qc.md` |
| 语义质检 | 转写文本 | TODO | `rules.md` |
| 涉诈识别 | 转写文本 + 通话特征 | TODO | `fraud/overview.md` |
| 投诉 / 骚扰识别 | 转写文本 | TODO | `rules.md` |

## 3. 判定方式

- **规则**：关键词、正则、组合条件，见 `rules.md`
- **模型**：分类模型 / 通话大模型，见 `03-ai/llm.md`
- **Agent**：多步推理 + RAG（反诈），见 `03-ai/agent.md`

多种判定结果的融合策略：TODO（例如规则命中直接判定，模型结果需要置信度阈值）。

## 4. 服务

- `call-qc-service`：质检调度与规则执行
- `fraud-agent`：涉诈识别 Agent
- `asr-service`：转写

## 5. 必须知道的约束

- 录音与转写文本属于敏感数据：TODO 留存期限、脱敏要求
- TODO
