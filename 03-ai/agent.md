---
id: ai-agent
title: 质检 / 反诈 Agent
domain: ai
kind: stable
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [ai-llm, ai-rag, fraud-overview, svc-fraud-agent]
---

# 质检 / 反诈 Agent

## 1. 工作流

```mermaid
flowchart LR
    A[转写文本] --> B[规则初筛]
    B -->|疑似| C[RAG 检索相似案例]
    C --> D[LLM 推理：匹配诈骗模式与风险行为]
    D --> E[输出 FP / RB / 置信度 / 证据片段]
    B -->|未命中| F[结束]
```

## 2. 工具与输出

| 项 | 说明 |
| --- | --- |
| 可用工具 | TODO |
| 输出结构 | TODO：包含 pattern_id、behaviors、confidence、evidence |
| 失败兜底 | TODO：超时 / 解析失败时的默认结论 |

## 3. 评测

TODO：评测集位置、指标（准确率、召回率、误报率）、上线门槛。
