---
id: mn-overview
title: 中间号 - 总览
domain: mid_number
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [mn-business-flow, svc-mid-number-service]
---

# 中间号总览

## 1. 是什么

TODO：一段话说明中间号平台的定位、服务对象（如网约车、外卖、物流等行业客户）和核心价值。

## 2. 子域

| 子域 | 目录 | 说明 |
| --- | --- | --- |
| 700 号码 | `700/` | TODO |
| 出海 | `overseas/` | TODO |
| 呼叫路由 | `call-routing/` | TODO |
| 号码池 | `number-pool/` | TODO |
| 计费 | `charging/` | TODO |

## 3. 核心对象

| 对象 | 说明 | 存储 | 契约 |
| --- | --- | --- | --- |
| 绑定关系 | TODO | `06-data/tables/`（TODO 表名） | `06-data/api/` |
| 中间号资源 | TODO | TODO | |
| 话单 | TODO | TODO | `06-data/events/events.yaml` |

## 4. 服务

- `mid-number-service`：见 `05-code/services/mid-number-service.yaml`
- TODO：其它服务

## 5. 必须知道的约束

- TODO：例如“绑定关系有效期到期后号码进入冷却期，冷却期内不得再分配”
- TODO：监管相关约束（实名、留存时长等）
