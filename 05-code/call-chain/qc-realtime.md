---
id: callchain-qc-realtime
title: 调用链 - 实时分片质检
domain: code
kind: stable
audience: [developer, architect, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-realtime, qc-media-chain, qc-orchestration, ADR-004, module-vi-media, module-vi-merge, module-scheduler]
source_of_truth:
  - vi-assets/vi-media-master/internal/proxy/stream_audio.go
  - vi-assets/vi-media-master/internal/proxy/stream_outbound.go
  - vi-assets/vi-merge-master/wshandler/conn_handler.go
  - vi-assets/vi-merge-master/processor/ac2_processor.go
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ShardTaskService.java
---

# 调用链：实时分片质检

在准实时链路的媒体锚定之后分叉。通话结束消息和客户推送仍走 vi-billcenter / vi-pusher。

| # | 服务 | 代码 | 动作 | 失败时 |
| --- | --- | --- | --- | --- |
| 1 | vi-as | 媒体设计：信令在接通前调用 inspect | POST `/call/media/inspect`，下发 config、frames、direction | 校验失败 400。vi-as 是否每通都调用待确认 |
| 2 | vi-media | `stream_audio.go:Receive` | 某一侧录满 `frames*50` 帧后建连 | 未满阈值前只本地录音 |
| 3 | vi-media | `stream_outbound.go` | WebSocket ` /media/stream` 到 vi-codec，回灌已录分段 | 握手 5s 失败 → `STREAM_DONE` + `FallbackVI` |
| 4 | vi-codec（外部） | 不在本次源码 | 解码为 PCM，再连 vi-merge | 异常时 HTTP 通知 `/call/media/inspect/notice` |
| 5 | vi-merge | `ConnHandler.AudioPcmWs` | `GET /v1/merge`，按 `initialSegments`/`segments` 切 WAV | 超最大会话 503；通道满丢块 |
| 6 | vi-merge | `mergeWavOutbound` | 分片上传 ZoneC 后发 `StreamSegmentMerged` | 上传失败不发 MQ，回调 EXCEPTION |
| 7 | vi-merge | `finalFullAudio` | 结束时整通录音上传 ZoneM | 不发 MQ。ffmpeg 失败回退 WAV |
| 8 | vi-media | `record_audio.go:Cancel` | 推流未回退则**不发**离线合音消息；回退则 `fallback=true` | 回退后走 `05-code/call-chain/qc-near-realtime.md` 第 8 步 |
| 9 | vi-billcenter | 同准实时 | 用分片消息发起质检 | topic 名冲突同样存在 |
| 10 | qa-server | `resolve_segment_route` | `is_stream` 为真走 realtime 优先级 | 短通话可能被路由成 no_ai，不进模型 |
| 11 | scheduler | `ShardTaskService` | 同一 callId 按 segmentId 串行，ASR 文本累加 | 前一片未完成则后一片等待。CAS 已注释，可能重复发送 |
| 12 | scheduler | `zhuanxian_qc_2.xml`（dev） | 高风险写 `interruptShard`，后续分片直接结束 | 该 XML 不在 prod 目录，生产是否使用待确认 |

vi-codec 与 vi-merge 之间的 PCM 帧：首字节是 source（1 主叫 / 2 被叫），其余为 PCM。`source=3` 时合成立体声。
