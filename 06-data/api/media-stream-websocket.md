---
id: api-media-stream-websocket
title: 实时媒体 WebSocket（vi-codec 与 vi-merge）
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-vi-as-to-vi-media
  - api-media-file-transfer
  - svc-vi-media
  - svc-vi-merge
  - module-vi-media
  - module-vi-merge
  - qc-media-chain
source_of_truth:
  - vi-assets/vi-media-master/config.prod/app.toml
  - vi-assets/vi-media-master/internal/pkg/mediasource/consts.go
  - vi-assets/vi-media-master/internal/pkg/mediasource/conn_outbound.go
  - vi-assets/vi-merge-master/internal/service/route/router.go
  - vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go
  - vi-assets/vi-merge-master/internal/models/bmessage.go
---

# 实时媒体 WebSocket

二期实时分片里有两段 WebSocket。vi-codec 的源码不在本次仓库，`/media/stream` 只记录 vi-media 作为客户端写出的契约；`/v1/merge` 以 vi-merge 服务端代码为准。

## 1. vi-media 推给 vi-codec

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| GET（WebSocket） | `/media/stream` | vi-media | vi-codec | 二进制帧与文本事件，见下表 | 握手失败由客户端记日志；写超时 30 毫秒 | `vi-assets/vi-media-master/config.prod/app.toml`（outbound.uri）；`vi-assets/vi-media-master/internal/pkg/mediasource/conn_outbound.go:Send` |

节点从 ETCD 按 outbound 的集群名和应用名发现。握手超时 5 秒。连接只写不读；vi-codec 的反馈走 HTTP `POST /call/media/inspect/notice`，不走这条 WebSocket。队列满时 `Send` / `Notice` 直接丢弃，没有错误码。

二进制帧：`[kind 1 字节][source 1 字节][payload]`。

| 字段 | 取值 |
| --- | --- |
| kind | `0x01` 音频，`0x02` 视频 |
| source | 1 主叫，2 被叫 |
| payload | AMR 为存储格式帧；G.711 / G.729 为 RTP 负载；视频为原始 RTP 包 |

文本帧 JSON：`{"event":"...","data":...}`。

| event | data | 时机 |
| --- | --- | --- |
| FRAME | 需要补的静音帧数 | 二进制帧之前，且待补帧数大于 0 |
| CALLING | callTime，unix 秒 | 接通时，或推流建立时已经接通 |
| CODEC_SWITCH | mime，如 `audio/amr` | 推流建立、编码变化、每个分段 Flush 前 |
| END | 空 | 退出前 |

## 2. vi-codec 推给 vi-merge

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| GET | `/v1/merge` | vi-codec（设计如此；源码不在本次仓库） | vi-merge | 头 X-Params、X-Notice、X-Business-Type、X-Version；X-Session-Id 只打日志 | HTTP 400 / 503；文本帧 code | `vi-assets/vi-merge-master/internal/service/route/router.go`；`vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go:AudioPcmWs` |

业务端口 9033。Upgrade 成功后服务端回文本 `{"code":"0000","message":"Wait..."}`，上游再推流。会话数超过 maxSession 返回 HTTP 503，body `{"error":"session over max session"}`。头 JSON 非法、`source>3` 或采样率不能被 8000 整除时返回 HTTP 400。

| 头 | 必填 | 说明 |
| --- | --- | --- |
| X-Params | 是 | JSON，字段见下表。sessionId 为空时服务端生成 `merge_<纳秒时间戳>` |
| X-Notice | 否 | 异常回调 URL。长度小于 3 则不回调 |
| X-Session-Id | 否 | 只用于日志。设计文档写成必填，代码不以它做会话键 |
| X-Business-Type | 否 | 空则 `merge`。非 merge 时文本帧 code 为 `"-1000"` |
| X-Version | 否 | 空则 `v1` |

X-Params：

| 字段 | 说明 |
| --- | --- |
| sessionId、callerNum、calleeNum、userId | 透传到后续 MQ。号码示例 `700xxxx0001` |
| sampleRate | 代码只要求能被 8000 整除。设计文档写只允许 8000 或 16000 |
| fmt | `s16le`（默认）/ `pcma` / `pcmu`，大小写敏感 |
| userData | JSON 字符串，原样进 MQ |
| initialSegments | 首片秒数，小于 1 时改用 segments |
| segments | 后续片秒数，小于 1 时默认 30 |
| startTime | 代码按 `yyyy-MM-dd HH:mm:ss` 使用，用于 PFS 路径和整通文件名 |
| source | 1 主叫 / 2 被叫 / 3 双声道（左主叫、右被叫） |

CONFLICT: 专线质检-媒体实现详细设计接口（WebSocket startTime 为 ISO8601 或毫秒级 Unix 时间戳字符串；sampleRate 仅 8000 或 16000） vs `vi-assets/vi-merge-master/internal/models/bmessage.go` 与 `vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go:AudioPcmWs`: startTime 按 `yyyy-MM-dd HH:mm:ss` 使用，sampleRate 只校验能被 8000 整除。

上行二进制：第 1 字节为 source（1 或 2），其余为 PCM。内容恰为 `EOS` 三字节表示结束。单帧上限 1MB，读超时 60 秒。文本帧只认 `{"event":"EOS"}`。处理器失败时回写 `{"Code":-1002,"Message":"..."}`（键名首字母大写）。

异常时 vi-merge 向 X-Notice POST `{"sessionId","message","event":"EXCEPTION","data":{"code":"-1000","message"}}`，并带头 `X-Session-Id`、`X-Sequence-Id`。超时 3 秒，不重试。

## 3. 待确认

1. vi-codec 连接 `/media/stream` 时期望的路径、子协议和是否回读。
2. 设计文档把 X-Session-Id 列为必填，代码只用来打日志，联调以哪边为准。
3. 队列满静默丢帧是否需要指标。
4. TERMINATION 通知是否应触发离线合音回退。
