---
id: mn-overview
title: 中间号 - 总览
domain: mid_number
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-business-flow, mn-700-overview, mn-call-routing-overview, mn-number-pool-overview, mn-charging-overview, mn-traffic-control-overview, ctx-business-overview]
source_of_truth:
  - vi-assets/vi-as-master/
  - vi-assets/vi-billcenter-master/
  - vi-assets/vi-cront-jobs-master/
---

# 中间号总览

本目录描述专线质检**复用 700 平台**的那一层：号码如何被识别、呼叫如何接续、话单如何留下、接通前如何管控。

本次 9 个仓库没有实现隐私号绑定（AXB/AXN）、号码池冷却和出海。那些能力如果存在，在别的系统里，不在 `vi-assets/`。

## 1. 子域

| 子域 | 目录 | 本次资产里的事实 |
| --- | --- | --- |
| 700 号码 | `700/` | 7001X 前缀进入专线；15 位 700 仍走大网 |
| 呼叫路由 | `call-routing/` | vi-as 作为 SIP B2BUA |
| 号码报备 | `number-pool/` | `biz_num_auth`、号段、ICSCF。不是可分配号码池 |
| 话单与计费 | `charging/` | `bill_new` 生命周期；批价在外部计费中心 |
| 话务管控 | `traffic-control/` | vi-cront-jobs 写 Redis，呼叫时查 strategy-service |
| 出海 | `overseas/` | 无实现 |

## 2. 核心对象

| 对象 | 在哪里 | 说明 |
| --- | --- | --- |
| 报备号码 | `user_qc.biz_num_auth` | 匹配失败则拒绝或放通不质检，取决于接入方案 |
| 话单 | `vi_bill.bill_new` | vi-as 插入，结束时更新并发给 vi-billcenter |
| 策略命中 | Redis 管控 key | 不直接写话单；vi-as 把 strategy-service 返回的 appendReason 写入话单 |

服务入口是 vi-as，见 `05-code/services/vi-as.yaml` 与 `05-code/modules/vi-as.md`。
