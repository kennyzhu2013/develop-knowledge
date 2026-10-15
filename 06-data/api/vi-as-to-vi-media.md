---
id: api-vi-as-to-vi-media
title: vi-as 调用 vi-media 的 HTTP 控制面
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-media-stream-websocket
  - svc-vi-as
  - svc-vi-media
  - module-vi-as
  - module-vi-media
  - qc-media-chain
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaSession.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/util/DateUitl.java
  - vi-assets/vi-media-master/internal/app.go
  - vi-assets/vi-media-master/internal/router/proxy.go
  - vi-assets/vi-media-master/internal/handler/proxy.go
  - vi-assets/vi-media-master/internal/dto/session_create.go
---

# vi-as 调用 vi-media 的 HTTP 控制面

vi-as 通过 ETCD 服务名 `com.cmic.vi.media`（或启动参数指定的媒体地址）选节点，用 HTTP JSON 驱动媒体会话。接通通知对应 pickup，释放会话对应 stop。双方之间没有 SIP；vi-media 返回的 ip 与端口由 vi-as 写入 SDP。

统一响应 `{"code":<int>,"data":...,"message":...}`。成功时 code 为 200。`/call` 下除 create 外要求头 `X-Session-Id`、`X-Sequence-Id`。vi-as 在 create 时也会带这两个头，seqId 为 1。

## 1. 主路径

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/call/session/create` | vi-as | vi-media | sessionId（话单 id）、userId、callerNum、calleeNum、mergeConfig、startTime、recordMode、seqId、callbackUrl、direction | 200；400；503 | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java:createMediaSession`；`vi-assets/vi-media-master/internal/handler/proxy.go:SessionCreate` |
| POST | `/call/session/update` | vi-as | vi-media | 两侧 audio ip/port、audioCode、audioPayload、audioDtmfPayload、video 地址与编码、seqId | 400（缺会话或绑定失败） | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaSession.java:commit`；`vi-assets/vi-media-master/internal/proxy/session_process.go` |
| POST | `/call/session/pickup` | vi-as | vi-media | sessionId、startTime、seqId。接通后进入 IN_CALL | 400 | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaSession.java`；`vi-assets/vi-media-master/internal/proxy/session_process.go:pickup` |
| POST | `/call/session/stop` | vi-as | vi-media | sessionId、endTime、seqId。不进事件队列，避免挂机被乱序挡住 | 400 | `vi-assets/vi-media-master/internal/proxy/session.go:Stop` |

create 成功时 data 含 sessionId、ip、audioPort、videoPort、seqId。无空闲槽位返回 HTTP 503。绑定失败或会话已存在返回 HTTP 400。vi-as 把 code 不是 200 视为失败，并换节点再试 1 次。update / pickup / stop 走异步重试，只在 Socket 超时时重试，最多 2 轮。

startTime、endTime 由 `DateUitl.formatDateyyyyMMddHHmmss` 格式化为 `yyyy-MM-dd HH:mm:ss`（方法名不含分隔符，实际模式带分隔符）。vi-media create 用本地时区按同一模式解析。

callbackUrl 为 vi-as 的 `POST /media`。vi-media 回调体为 sessionId、event、code（`"200"` / `"500"`）、message、data、seqId。回调超时 10 秒，失败只记日志。

userId：vi-as 传入 appId 或 companyId。recordMode：1 录音、2 全量、3 不录；主流程创建时传 1。

## 2. direction 冲突

CONFLICT: `vi-assets/vi-media-master/internal/dto/session_create.go:SessionCreate.Direction` vs `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java:createMediaSession`: vi-media create 要求 direction（`binding:"required,oneof=1 2 3"`，1 主叫 / 2 被叫 / 3 双方），缺失时 `ShouldBindJSON` 失败并返回 HTTP 400；vi-as 组装的 create JSON 没有 direction 字段。以这两处代码为准。线上是否另有兼容版本，待确认。

## 3. 媒体动作

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/call/media/play` | vi-as | vi-media | 音视频源、period（Early/InCall）、priority（Local/Net）、direction 1–4、recordMode | 400 | `vi-assets/vi-media-master/internal/proxy/session_process.go:play` |
| POST | `/call/media/dtmf` | vi-as | vi-media | direction（绑定只允许 1/2/3）、endSign、expectLen、timeout | 400；完成回调 DTMF_DONE | 同上 `dtmf` |
| POST | `/call/media/stop` | vi-as | vi-media | mark：DTMF 或 Media | 400 | `vi-assets/vi-media-master/internal/handler/proxy.go` |
| POST | `/call/media/enhance` | vi-as | vi-media | 增强参数 | 400 | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` |
| POST | `/call/media/inspect` | 本次 vi-as 源码未调用 | vi-media | sessionId、config、frames（秒，大于 0）、direction 1/2/3、seqId | 400 | `vi-assets/vi-media-master/internal/proxy/session.go:MediaInspect` |

设计文档把 inspect 的路径写成 `/call//media/inspect`（双斜杠），示例体却是 create 的字段。代码路径是单斜杠 `/call/media/inspect`，入参是 config、frames、direction、seqId。谁在生产触发 inspect，待确认。

vi-as 仍保留一组 `/v1/session/bupdate`、`/v1/video/playearly`、`/v1/audio/dtmf` 等旧路径，主流程不依赖。

## 4. 待确认

1. 线上 vi-as 是否已经补传 direction；若未补传，create 会稳定 400。
2. create 响应是否还返回 storeFlag。当前 `SessionCreateResp` 没有该字段，vi-as 读不到时按 0。
3. `/call/media/inspect` 的生产调用方。
4. pickup 用 UTC 解析 startTime，与 create 的本地时区不一致，东八区是否造成接通时间偏差。
