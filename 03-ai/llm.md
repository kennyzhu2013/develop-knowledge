---
id: ai-llm
title: 通话大模型
domain: ai
kind: stable
audience: [architect, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-overview, ai-model-catalog, ai-model-serving]
source_of_truth:
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
---

# 通话大模型

专线流程里的大模型是 Qwen 与 DeepSeek，都由 scheduler 把 `DyHttpRequest` 丢给适配器。URL、版本和鉴权在 MongoDB `model_meta`，XML 可以指定 `version`。`model_meta` 没有管理接口。

| 模型 | 在专线流程中的位置 | 写回 |
| --- | --- | --- |
| Qwen（XML 中为 `qwen3-4b`） | RAG 之前，对转写做一轮分类/改写 | `qwenStatus, qwenResult` |
| DeepSeek | 末段。prompt 按 RAG 命中的诈骗类型选择，类型说明写在 XML 里 | `deepSeekStatus, deepSeekResult, deepSeekReason`。`result==1` 记中风险 |

vi-billcenter 把 `deepSeekPromptId=38`、`qwenPromptId=39` 写死在请求里。改提示词要同时看 XML 和这两个 id 的配置来源（待确认它们在 qa-server 侧如何生效）。

DIFY 与通用 HTTP 模型走 `ai_model` 集合，由无鉴权的管理接口维护，专线 prod XML 未使用。
