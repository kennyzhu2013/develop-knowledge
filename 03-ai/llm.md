---
id: ai-llm
title: 通话大模型
domain: ai
kind: stable
audience: [architect, developer]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [ai-overview, ai-model-serving]
---

# 通话大模型

## 1. 用途

| 场景 | 输入 | 输出 | Prompt 位置 |
| --- | --- | --- | --- |
| 语义质检 | 转写文本 | 标签 + 理由 | TODO：代码路径 |
| 涉诈推理 | 转写文本 + RAG 结果 | 风险判定 | TODO |
| 通话摘要 | 转写文本 | 摘要 | TODO |

## 2. 约束

- Prompt 属于代码，放在代码仓库中版本化管理；本文件只做索引
- 输出必须是结构化格式（TODO：JSON Schema 位置），并有解析失败兜底
- TODO：上下文长度限制与长通话切分策略
