---
id: qc-overview
title: 语音质检 - 总览
domain: call_qc
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-pipeline, qc-realtime, qc-media-chain, qc-orchestration, qc-rules, fraud-overview, ctx-business-overview]
source_of_truth:
  - vi-assets/vi-as-master/
  - vi-assets/qa-server-master/
  - vi-assets/scheduler-master/
---

# 语音质检总览

本次代码资产是**专线质检**（vi）：把语音专线或 IMS 固话呼叫接入 700 平台，录音后做质检，并把正常/异常与风险等级推给客户。它复用 700 话路，不是一套独立的隐私号绑定平台。

## 1. 质检类型

| 类型 | 触发 | 时效 | 文档 |
| --- | --- | --- | --- |
| 准实时（一期） | 通话结束且合音完成 | 通话后 | `pipeline.md` |
| 实时分片（二期） | 通话中按片推流 | 通话中 | `realtime-qc.md` |
| 抽检 / 不质检 | qa-server 按时长阈值与比例路由为 offline 或 no_ai | 视路由 | `orchestration.md` |
| 话务管控 | 呼叫建立前查策略，与内容质检分开 | 接通前 | `01-mid-number/traffic-control/overview.md` |

## 2. 服务

| 服务 | 职责 |
| --- | --- |
| vi-as | SIP B2BUA，号码授权、策略查询、驱动媒体、写话单 |
| vi-media | RTP 锚定、分段录音、实时推流 |
| vi-merge | 实时 PCM 合音与分片上传 |
| offline-merge | 离线 ffmpeg 合音 |
| vi-billcenter | 话单与合音结果关联，向 qa-server 建任务，接收回调 |
| qa-server | 鉴权、任务、分片路由、结果入库与回调 |
| scheduler | 按 XML 编排 ASR 与模型 |
| vi-pusher | 经 httpproxy 把结果推给客户 |
| vi-cront-jobs | 话务管控策略计算，写 Redis |

外部组件（源码不在本仓库）：xproxy、vi-codec、vi-mdown、strategy-service、httpproxy、asr、ai-models。服务契约见 `05-code/services/`。

## 3. 输出

- 通话话单：MySQL `vi_bill.bill_new`（及失败表、按日表）。
- 质检明细：先写 Redis 分钟桶，再进入海山（StarRocks）`qa_detail` / `qa_segment`。
- 客户推送：风险等级、正常/异常、录音位置。字段以 `06-data/api/result-push-to-customer.md` 为准。
- 通话中打断/放音：设计有，vi-as 尚未消费质检回推的管控指令。

## 4. 组织层级

质检账户与号码按四级企业贯穿：`mobile_id`（省）、`channel_id`（市）、`company_id`（接入企业）、`customer_id`（终端客户），另有 `app_id` 标识能开侧质检主体。`channelId` 在报备接口里又被用作 app_id，和表字段 `channel_id` 不是同一个东西。
