---
id: callchain-qc-near-realtime
title: 调用链 - 准实时质检
domain: code
kind: stable
audience: [developer, architect, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-pipeline, qc-media-chain, qc-orchestration, module-vi-as, module-offline-merge, module-vi-billcenter, module-qa-server, module-scheduler]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/DtmfEngine.java
  - vi-assets/vi-media-master/internal/proxy/record_audio.go
  - vi-assets/offline-merge-master/internal/processor/file_merge_processer.go
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java
  - vi-assets/qa-server-master/apps/views.py
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
---

# 调用链：准实时质检

通话结束后才质检。事件名见 `06-data/events/events.yaml`。

| # | 服务 | 代码 | 动作 | 失败时 |
| --- | --- | --- | --- | --- |
| 1 | xproxy（外部） | 设计文档《专线质检方案+需求》§2.1.1 | 被叫以 7001X 开头且总长不是 15 位，送到 vi-as | 15 位 700 走原大网，不进本链路 |
| 2 | vi-as | `DtmfEngine.doBuildController` | 解析主被叫，用 bizNum 查 `biz_num_auth` | 无授权：非 GZH 回 603；GZH 纯转发，无话单 |
| 3 | vi-as | `ControllerAgent.start` | 企业/客户 status≠1 走 ErrorController；startMode=0 时 HTTP 查 strategy-service | 策略超时 500ms。服务未发现或审计开关关闭时跳过策略（是否仍放行见模块文档） |
| 4 | vi-as | `MediaService.createMediaSession` | ETCD 找 vi-media，POST `/call/session/create` | 失败换节点再试一次。仍失败则呼叫继续但不录音 |
| 5 | vi-as | `BaseController` / `CallSession` | 改写 SDP 为媒体 ip/port；180/200 时 update；200 时 pickup；结束时 stop | 只支持 G.729 则不锚定媒体 |
| 6 | vi-as | `DbAsyncService`、`BillCenterService` | 插入 `bill_new`，结束后发 `CallBillCompleted` | 发送失败写 `bill_fail` |
| 7 | vi-media | `record_audio.go:Cancel` | 未走实时推流时发 `MediaRecordMergeRequested` | 发送超时 3s，最多 3 次 |
| 8 | offline-merge | `FfMerge.MergeSh` | 下载分段、ffmpeg、上传 vi-mdown，发 `OfflineRecordMerged` | 解析失败直接 ack。只有出站 MQ 失败才重投 |
| 9 | vi-billcenter | `RecordReceiver.consume` | 消费合音结果，与通话消息关联，HTTP 建质检任务 | 消费始终 SUCCESS。topic 名与生产者不一致，见下 |
| 10 | qa-server | `POST /v1/task/customize` | 鉴权后异步发 `QcTaskSubmitted` | 接口已返回成功后异步失败，调用方无感知 |
| 11 | scheduler | `TaskConsumer`、`zhuanxian_qc.xml` | ASR、脱敏、关键词、Qwen、RAG、融通、DeepSeek | 校验失败静默丢弃。模型路由失败静默丢失 |
| 12 | qa-server | `QaResultConsumeThread` | 写 Redis 分钟桶，HTTP 回调 vi-billcenter | 回调失败进延迟重推 |
| 13 | vi-billcenter → vi-pusher | 模块文档 §4 | 发 `InspectionResultReady`，httpproxy `/proxy` 推客户 | 推送失败有延时重试 topic |

CONFLICT: 第 9 步订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`，offline-merge 生产 `TID_VI_MERGE_BILL_RECORD_NORMAL`，vi-merge 生产 `TID_VI_MERGE_MSG_BILL_NORMAL`。字段也不对齐：订阅侧按 `code`/`callId` 解析，Vi 模式消息没有这两个字段。

录音开关在 `BusinessEvent.setRecordParams` 里写死 `needRecord=true`。是否真正录音取决于授权、策略、编码和媒体是否可用，而不是逐通话配置。
