---
id: ctx-system-boundary
title: 系统边界与外部依赖
domain: context
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-overall, ctx-business-overview]
source_of_truth:
  - knowledge.yaml
  - 05-code/services/
---

# 系统边界与外部依赖

## 1. 本知识库有源码的服务

vi-as、vi-media、vi-merge、offline-merge、vi-billcenter、vi-pusher、qa-server、scheduler、vi-cront-jobs。仓库路径在 `05-code/repositories.yaml`，契约在 `05-code/services/`。

## 2. 外部系统

只记录调用关系。源码不在 `vi-assets/`。

| 外部系统 | 我们如何使用 | 协议 | 文档 |
| --- | --- | --- | --- |
| xproxy | 识别 7001X 非 15 位被叫，把呼叫送给 vi-as | SIP | `01-mid-number/call-routing/overview.md` |
| ims | 实体卡签约呼叫进入 700 平台 | SIP | `01-mid-number/700/overview.md` |
| xsipproxy | vi-as 向 700 被叫发 INVITE 时加 Route | SIP | `05-code/services/vi-as.yaml` |
| strategy-service | 呼叫时返回 opType、放音、appendReason | HTTP `/strategy` | `01-mid-number/traffic-control/overview.md` |
| vi-codec | 把 vi-media 的帧转成 PCM 再送给 vi-merge | WebSocket | `02-call-qc/media-chain.md` |
| vi-mdown | 接收合音文件，供 ASR 和试听拉取 | HTTP 上传 | `02-call-qc/media-chain.md` |
| pfs | vi-mdown 背后的文件存储 | — | `04-architecture/storage.md` |
| cdn | 向 vi-media 提供放音文件 | HTTP / WebSocket | `05-code/services/vi-media.yaml` |
| tts | 文本转语音放音 | HTTP | `05-code/services/vi-media.yaml` |
| httpproxy | vi-pusher 经它访问客户回调 | HTTP `/proxy` | `06-data/api/result-push-to-customer.md` |
| customer-system | 接收质检结果的客户系统 | HTTP 回调 | 同上 |
| ops-platform | 报备、开户、门户查询 | HTTP | `06-data/api/reporting-interface-v1.0.1.md` |
| xpusher | qa-server 准实时话单的另一条 RabbitMQ 通道 | RabbitMQ | `06-data/mq/topics.yaml` |
| asr | scheduler 的 ASR 适配器消费队列后转写 | RabbitMQ | `03-ai/model-catalog.md` |
| ai-models | 反诈、RAG、大模型等适配器 | RabbitMQ | `03-ai/model-catalog.md` |

## 3. 边界规则

外部行为以对方接口为准。本库只写我们发出的字段和已经在代码里看到的差异。差异用 `CONFLICT:` 单独成行。
