---
id: ai-model-serving
title: 模型服务与版本
domain: ai
kind: dynamic
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-overview, ai-model-catalog, ai-llm]
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/AIModelController.java
---

# 模型服务与版本

模型进程不在本次仓库。能从代码确认的只有适配契约和元数据存放位置。

## 1. 版本放在哪里

| 模型 | 版本来源 | 谁改 |
| --- | --- | --- |
| Qwen、DeepSeek | MongoDB `model_meta` 的 version、url、参数 | 直接写库，无管理接口 |
| DIFY、通用 HTTP | MongoDB `ai_model` | `/admin/aiModel/http/create|update|delete` |
| 流程用哪一版 | XML 属性 `version`，缺省用 `model_meta` 默认版本 | `/admin/process/update` 热更新 |

查询 HTTP 模型的接口会把 `authentication` 一并返回。接口无鉴权。

## 2. 发布时要核对

- 专线任务是否仍指向同一个 `processId`（vi-billcenter 写死了一个 id）。
- prod 与 dev 的 XML 文件名不同，实例的 `xml.path` 必须和流程记录里的 `xmlFileName` 一致。
- 模型无单次超时，只有 24h/48h 的主任务兜底。适配器变慢时，子任务会一直占着，直到兜底把未回调的子任务按失败补回调。

具体队列见 `03-ai/model-catalog.md`。不要在本文写 URL 或密钥。
