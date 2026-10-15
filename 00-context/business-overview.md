---
id: ctx-business-overview
title: 业务总览
domain: context
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ctx-glossary, ctx-system-boundary, arch-overall, qc-overview, mn-overview]
---

# 业务总览

这套系统把语音专线和 IMS 固话呼叫接进 700 平台，完成接通前管控、录音质检，并把结果回推给客户。本次 `vi-assets/` 里的 9 个仓库是专线质检实现，不是一套独立的 AXB 隐私号绑定平台。

## 1. 谁在用

| 对象 | 说明 |
| --- | --- |
| 终端企业 | 催收、金融、教育、房产、政务、电商、办公、快递、医疗等场景的外呼方 |
| 四级组织 | 省 `mobile_id`、市 `channel_id`、接入企业 `company_id`、终端客户 `customer_id` |
| 质检主体 | 能开侧 `app_id`。报备接口里的 `channelId` 常指这个 id，与表字段 `channel_id` 不同 |
| 内部运营 | 对客质检门户与后台（ops-platform，源码不在本次资产） |

## 2. 主链路

```text
业务方给被叫加 7001X 前缀（总长不是 15 位）
  → xproxy 把呼叫送给 vi-as
  → 号码报备匹配、话务策略
  → vi-media 锚定并录音（二期再分片推流）
  → 合音上传
  → vi-billcenter 向 qa-server 建任务
  → scheduler 做 ASR 与反诈模型
  → vi-pusher 把结果推给客户
```

准实时见 `02-call-qc/pipeline.md`，实时分片见 `02-call-qc/realtime-qc.md`，接通前管控见 `01-mid-number/traffic-control/overview.md`。

## 3. 和“中间号”目录的关系

`01-mid-number/` 记录的是这次资产里**已经落地**的 700 接入、呼叫、号码报备和话单。传统隐私号的绑定/解绑、号码池冷却、出海，在这 9 个仓库和随附设计文档里没有实现，对应文档会明确写“不在本次资产”。
