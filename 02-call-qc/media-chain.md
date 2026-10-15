---
id: qc-media-chain
title: 语音质检 - 媒体与合音链路
domain: call_qc
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-overview, qc-pipeline, qc-realtime, callchain-qc-near-realtime, callchain-qc-realtime, svc-vi-media, svc-vi-merge, svc-offline-merge]
source_of_truth:
  - vi-assets/vi-media-master/internal/proxy/
  - vi-assets/vi-merge-master/processor/
  - vi-assets/offline-merge-master/internal/processor/
---

# 媒体与合音链路

vi-as 只处理 SIP。媒体锚定、录音、实时推流和合音分别由 vi-media、vi-codec（源码不在本次资产）、vi-merge、offline-merge 完成。vi-as 与 vi-media 之间没有 SIP/SDP，只有 HTTP/JSON；vi-media 返回的 ip/port 由 vi-as 填进 SDP。

## 1. 三种处理模式

依据媒体设计图与 `vi-assets/vi-media-master/internal/proxy/record_audio.go:Cancel`：

| 模式 | 路径 | 何时发生 |
| --- | --- | --- |
| 离线 | RTP → 分段落盘 → RocketMQ `MediaRecordMergeRequested` → offline-merge | 未开启实时推流，或推流已回退 |
| 实时分片 | RTP → WebSocket 到 vi-codec → PCM 到 vi-merge → 分片上传 | `inspect` 打开后某一侧录满阈值 |
| 实时转离线 | 推流失败后 `fallback=true`，会话结束仍发离线合音消息 | vi-codec 异常且 `needVI` |

会话结束时：若 `needVI && !enableVI`（实时推流已进行且未回退），vi-media **不发**离线合音消息。

## 2. vi-as → vi-media 控制面

`/call` 下除 create 外都要带 `X-Session-Id` 与整数 `X-Sequence-Id`。事件写入容量 3 的 channel，按序号执行；乱序最多暂存 5 个，满则丢弃。请求被 abort 且状态非 500 时会 `Fallback(seqId)`，避免后续事件卡死。

| 步骤 | 接口 | 作用 |
| --- | --- | --- |
| 创建 | POST `/call/session/create` | 分配槽位与端口（音频 base，视频 base+2），状态 NONE→START |
| 协商 | POST `/call/session/update` | 设置主被叫 RTP 地址与编码，首次打开 UDP，START→ACTIVE |
| 质检开关 | POST `/call/media/inspect` | 二期：`config`、`frames`（秒）、`direction` |
| 接通 | POST `/call/session/pickup` | ACTIVE→IN_CALL，创建录音文件 |
| 结束 | POST `/call/session/stop` | 不走事件队列，直接 `stop()` |

CONFLICT: `vi-assets/vi-media-master/internal/handler/proxy.go:SessionCreate` 要求 `direction`（1/2/3）必填；`vi-assets/vi-as-master` 的 create 请求不传该字段。

超时（`session_manager.go:scan`、`stream_audio.go:Receive`）：START 超过 60s、ACTIVE 超过 90s、IN_CALL 超过 14400s（注释写 3 小时，代码是 4 小时）回调 `SESSION_RELEASE`。IN_CALL 连续无 RTP 超过 50000ms 同样释放。无空闲槽位返回 503。

录音文件先落内存卷（生产路径模板 `/tmp/media/call`，生命期约 10 分钟），再迁到磁盘卷（`/data/media/call`）。offline-merge 用 POST `/media/download/:filename` 从 ZoneC 回源。

## 3. 实时推流（vi-media → vi-codec）

触发：`enableVI` 且某一侧已录满 `frames*50` 帧（`stream_audio.go:Receive`）。

| 项 | 事实 |
| --- | --- |
| 地址 | `ws://<vi-codec>/media/stream`，节点来自 ETCD 轮询 |
| 头 | `X-Session-Id`、`X-Params=viConfig`、`X-Notice=http://<apiAddress>/call/media/inspect/notice` |
| 二进制帧 | `[kind 1B][source 1B][payload]`，kind `0x01` 音频 / `0x02` 视频，source `1` 主叫 / `2` 被叫 |
| 文本事件 | `FRAME`（补静音）、`CALLING`（unix 秒）、`CODEC_SWITCH`、`END` |
| 失败 | 握手 5s；写超时 30ms；队列满静默丢弃。失败回调 vi-as `STREAM_DONE`，并 `FallbackVI`（结束时 MQ `fallback=true`） |

vi-codec 的反馈不走 WebSocket 读，而走 HTTP `/call/media/inspect/notice`。该接口同样要求 `X-Sequence-Id`（vi-codec 如何填写待确认）。

## 4. 实时合音（vi-merge）

入口 `GET /v1/merge`（业务端口 9033）。一个 WebSocket 对应一通通话。`X-Params.sessionId` 为空时生成 `merge_<纳秒>`；请求头 `X-Session-Id` 只用于日志。

| 步骤 | 代码 | 结果 |
| --- | --- | --- |
| 收 PCM | `processor/wspcm_processor.go` | 首字节 source，其余为 PCM；读超时 60s |
| 切块合音 | `processor/ac2_processor.go` | 首片 `initialSegments` 秒，后续 `segments` 秒（小于 1 则默认 30） |
| 上传分片 | `file_processor.go:SelectNodeUpload` | zone=ZoneC，文件名 `Segment_<sessionId>_<segId>.wav` |
| 发消息 | `broker.go:PubBillMessage` | topic 配置为 `TID_VI_MERGE_MSG_BILL_NORMAL`，事件 `StreamSegmentMerged` |
| 整通录音 | `finalFullAudio` | 上传 ZoneM；**不发 MQ**（`full=true` 的发送已注释） |

`source=3` 时输出双声道，左主叫、右被叫。分片上传失败不发 MQ，并回调 `X-Notice` 的 EXCEPTION。通道满（容量 250）时该块被丢弃。停机时逐会话通知媒体后直接退出，进行中的合音会丢失。

`duration` 是截至当前片的累计时长，不是单片时长。`segmentId` 从 0 开始。

## 5. 离线合音（offline-merge）

消费 `TID_VI_MEDIA_RECORD_MERGE_NORMAL`（组 `GID_VI_MERGE_RECORD`），流程在 `file_merge_processer.go:FfMerge.MergeSh`：

1. 解析失败的消息直接 ack 丢弃。
2. 按 `address` 并发 POST `/media/download/<文件名>`，下载失败不重试，该腿视为损坏。
3. ffmpeg 合音：双声道 `amerge`（短侧 `apad`），单声道 `amix`。输出采样率固定 8000。左主叫、右被叫；`reverseLeg=true` 时交换。
4. 上传 vi-mdown。连接失败或 5xx 换节点再试 1 次；仍失败且 `uploadFailSaveLocal=true` 时拷到 `/data/offlocal/`。上传错误不向上返回。
5. 发 `TID_VI_MERGE_BILL_RECORD_NORMAL`（Vi 模式，`isStream=false`，`endFlag=true`）或 `TID_DCC_MERGE_MSG_BILL_NORMAL`（DCC 模式）。**只有发消息失败才会让消费 FAILURE 重投**，于是整段下载和合音重做。

`fallback=true` 只记指标，处理路径与普通离线相同。

## 6. 合音结果 topic 不一致

CONFLICT: vi-billcenter 订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`（`vi-assets/vi-billcenter-master` 的 `RecordReceiver`），消息字段按 DCC 的 `code` / `callId` 解析。生产者配置分别是：

| 生产者 | topic |
| --- | --- |
| vi-media | `TID_VI_MEDIA_RECORD_MERGE_NORMAL`（给 offline-merge，不是给话单中心） |
| vi-merge | `TID_VI_MERGE_MSG_BILL_NORMAL` |
| offline-merge Vi | `TID_VI_MERGE_BILL_RECORD_NORMAL` |
| offline-merge DCC | `TID_DCC_MERGE_MSG_BILL_NORMAL` |

哪一个是生产实际值待确认。事件名对照见 `06-data/events/events.yaml`。

## 7. 约束

- vi-media 停机会等到所有会话槽位空闲，最长通话 4 小时，`stop.sh` 无超时。
- vi-media 回调 vi-as 失败只记日志，不重试。
- offline-merge 的 `/v1/test` 会真实下载、合音、上传并发 MQ，且无鉴权。
- 运维接口 `/ops/*`、`/som/*` 无鉴权。
