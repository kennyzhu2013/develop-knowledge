---
id: api-overview
title: 接口契约索引
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - data-overview
  - api-reporting-interface
  - api-vi-as-to-vi-media
  - api-strategy-service
  - api-vi-billcenter-to-qa-server
  - api-media-stream-websocket
  - api-media-file-transfer
  - api-result-push-to-customer
  - api-scheduler-admin
  - api-qa-server-management
source_of_truth:
  - vi-assets/vi-as-master
  - vi-assets/vi-media-master
  - vi-assets/vi-merge-master
  - vi-assets/offline-merge-master
  - vi-assets/vi-billcenter-master
  - vi-assets/vi-pusher-master
  - vi-assets/qa-server-master
  - vi-assets/scheduler-master
  - vi-assets/vi-cront-jobs-master
---

# 接口契约索引

本目录记录专线质检链路上已核对过的 HTTP / WebSocket 契约。事实以 `vi-assets/` 源码为准；设计文档与代码不一致时，各篇正文用 `CONFLICT` 标出。MQ、表结构不在本索引展开。

服务名使用 vi-as、vi-media、vi-merge、offline-merge、vi-billcenter、vi-pusher、qa-server、scheduler、vi-cront-jobs。源码不在本次 9 个仓库中的外部系统：strategy-service、vi-codec、vi-mdown、httpproxy、ops-platform。

## 1. 文档目录

| 文档 | id | 调用方 | 被调方 | 范围 |
| --- | --- | --- | --- | --- |
| `06-data/api/reporting-interface-v1.0.1.md` | api-reporting-interface | 语音专线平台 | 质检侧（说明书路径待定） | 企业 / 场景 / 话术 / 号码报备；EOPAUTH |
| `06-data/api/vi-as-to-vi-media.md` | api-vi-as-to-vi-media | vi-as | vi-media | 会话 create / update / pickup / stop 与放音收号 |
| `06-data/api/strategy-service.md` | api-strategy-service | vi-as | strategy-service | `POST /strategy`，opType 与 appendReason |
| `06-data/api/vi-billcenter-to-qa-server.md` | api-vi-billcenter-to-qa-server | vi-billcenter | qa-server | 生产 `POST /v1/task/customize` |
| `06-data/api/media-stream-websocket.md` | api-media-stream-websocket | vi-media；vi-codec | vi-codec；vi-merge | `/media/stream` 与 `/v1/merge` |
| `06-data/api/media-file-transfer.md` | api-media-file-transfer | vi-merge、offline-merge | vi-mdown | 合音文件上传 PFS |
| `06-data/api/result-push-to-customer.md` | api-result-push-to-customer | vi-pusher | httpproxy，再转发客户 | `POST /proxy` |
| `06-data/api/scheduler-admin-api.md` | api-scheduler-admin | 运维 / 管理端 | scheduler | 入站 HTTP，管理接口未鉴权 |
| `06-data/api/qa-server-management-api.md` | api-qa-server-management | vi-billcenter、运营 / 专线平台 | qa-server | 任务、分片路由、企业与号码、回调 |

## 2. 阅读约定

| 项 | 约定 |
| --- | --- |
| 表格列 | 方法、路径、调用方、被调方、关键字段、错误码、代码位置 |
| 号码示例 | `700xxxx0001`、`138xxxx0001` |
| 鉴权算法 | 只写算法名与拼接顺序，不写密钥、Token、签名样例 |
| 地址 | 不写内网 IP、主机名、域名和真实回调 URL |
| 外部实现 | strategy-service、vi-codec、vi-mdown、httpproxy、ops-platform 只写调用方能看到的契约 |

## 3. 已标出的冲突（详见各篇）

| 主题 | 文档 |
| --- | --- |
| vi-media create 要求 direction，vi-as 不传 | api-vi-as-to-vi-media |
| 设计文档写 `/v2/task/create`，生产走 `/v1/task/customize`；startTime 单位不一致 | api-vi-billcenter-to-qa-server |
| 报备说明书字段表与请求示例、以及与 qa-server `/vi` 回调 result 编码 | api-reporting-interface |
