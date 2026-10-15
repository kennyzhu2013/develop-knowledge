---
id: ai-overview
title: AI 能力 - 总览
domain: ai
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-asr, ai-llm, ai-rag, ai-agent, ai-model-serving, ai-model-catalog, qc-orchestration]
source_of_truth:
  - vi-assets/scheduler-master/
---

# AI 能力总览

AI 能力都挂在 scheduler 上，通过 RabbitMQ 适配器调用。本仓库没有独立的 asr-service 或 fraud-agent 进程。

| 能力 | 用在哪里 | 实现 | 文档 |
| --- | --- | --- | --- |
| ASR | 整通录音或分片 | 队列 `scheduler.asr.*`，另有优先队列和九天 ASR | `asr.md` |
| 脱敏 | 覆盖 asrText | `desensitization` 队列 | `model-catalog.md` |
| 关键词 / 反诈模型 | 专线流程的风险等级 | 本地匹配 + 关键词反诈 + 融通 | `model-catalog.md` |
| 大模型 | Qwen、DeepSeek，提示词在 XML | `model_meta` 里的 URL | `llm.md` |
| RAG | 高相似判高风险 | `scheduler.rag.*` | `rag.md` |
| 编排 | 流程 XML + SpEL | scheduler | `agent.md` |

专线 prod 流程只用目录里的一个子集。其余适配器是否启用，看 MongoDB 里 `status=1` 的流程。
