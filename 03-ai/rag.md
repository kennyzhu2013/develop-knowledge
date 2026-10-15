---
id: ai-rag
title: RAG 检索增强
domain: ai
kind: stable
audience: [architect, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-overview, fraud-overview, ai-model-catalog]
source_of_truth:
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
---

# RAG

这里的 RAG 是质检流程里的检索，不是本知识库给 Code Agent 用的检索。

| 项 | 说明 |
| --- | --- |
| 队列 | `scheduler.rag.exchange` / `topic.rag.key`，响应 `scheduler.rag.response.queue` |
| 请求 | `jobId, text, topK, kbType`。text 默认取 `asrText`，XML 可用属性改成其它字段 |
| 写回 | `ragResults[{id, text, score, metadata}]` |
| 专线判定 | `zhuanxian_qc.xml`：第一条 score ≥ 0.9 记高风险 riskLevel=1 |
| topK | vi-billcenter 建任务时写死 `ragTopK=1` |

知识库文件 `02-call-qc/fraud/` 里的模式和案例**不会**被这个 RAG 加载。线上库的内容在适配器一侧，本次源码看不到。改案例若只改知识库 YAML，线上结果不变。
