---
id: qc-realtime
title: 语音质检 - 实时分片质检
domain: call_qc
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-overview, qc-media-chain, qc-orchestration, ai-asr, callchain-qc-realtime, ADR-004]
source_of_truth:
  - vi-assets/vi-media-master/internal/proxy/stream_outbound.go
  - vi-assets/vi-merge-master/processor/ac2_processor.go
  - vi-assets/scheduler-master/sources/dev/zhuanxian_qc_2.xml
---

# 实时分片质检

二期在通话进行中按分片质检。媒体仍由 vi-as 用 HTTP 驱动，推流从 vi-media 到 vi-codec，再以 PCM 进入 vi-merge。

```text
vi-as inspect → vi-media 录满阈值
  → WebSocket /media/stream → vi-codec
  → WebSocket /v1/merge → vi-merge 分片
  → StreamSegmentMerged → vi-billcenter → qa-server
  → scheduler 按 callId 串行分片
推流失败 → fallback=true → 回到准实时离线合音
```

逐步说明见 `05-code/call-chain/qc-realtime.md`。决策背景见 `09-decisions/ADR-004-realtime-segment-qc.md`。

## 1. 分片参数

| 参数 | 行为 | 位置 |
| --- | --- | --- |
| `frames` | 录满多少秒才开始推流，代码按 `frames*50` 帧计 | vi-media `MediaInspect` |
| `initialSegments` | 首片秒数，小于 1 时用 `segments` | vi-merge `X-Params` |
| `segments` | 后续每片秒数，小于 1 时默认 30 | vi-merge |
| `isStream` | qa-server 路由为 realtime；scheduler 走分片串行 | qa-server `resolve_segment_route`、scheduler `ShardTaskService` |

每个分片一条 `StreamSegmentMerged`：`segmentId` 从 0，`endFlag` 只在最后一片为 true，`duration` 是累计时长。整通录音上传 ZoneM，但 vi-merge 不为此发 MQ。

## 2. 调度侧串行

同一 callId 的分片在 MongoDB `shard_task` 里排队。前一片 `finish` 之后才让下一片进入 ASR。文本累加到 `shard_task.asrText`，并回写当前子任务。命中高风险后 Redis `interruptShard=1`，后续分片直接结束。

二期编排文件是 `sources/dev/zhuanxian_qc_2.xml`。prod 目录里的专线流程是离线的 `zhuanxian_qc.xml`。生产是否加载 dev 文件待确认。

## 3. 失败回退

vi-codec 握手失败、异常通知或写队列失败时，vi-media 关闭实时质检并在会话结束发送 `fallback=true` 的离线合音消息，由 offline-merge 按整通录音处理。推流队列满时帧被静默丢弃，不会自动回退。

## 4. 通话中管控

设计文档把“质检结果回推后由 vi-as 下发接续/放音/挂断”标为待定。qa-server 会在异常结果的 userData 上附加 `ctrlAction` 等字段，但是本次 vi-as 代码没有消费这些字段去改进行中的通话。`control_action` 与策略侧 `control_command` 不是同一套编码，见 `01-mid-number/traffic-control/codes.md`。
