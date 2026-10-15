---
id: api-media-file-transfer
title: 合音结果上传 vi-mdown
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-media-stream-websocket
  - svc-vi-merge
  - svc-offline-merge
  - module-vi-merge
  - module-offline-merge
  - qc-media-chain
source_of_truth:
  - vi-assets/vi-merge-master/internal/processor/file_processor.go
  - vi-assets/offline-merge-master/internal/processor/bparser/vi_parser.go
  - vi-assets/offline-merge-master/internal/processor/upload_processor.go
  - vi-assets/offline-merge-master/internal/httpinterface/filehttp.go
  - vi-assets/vi-media-master/internal/router/media.go
---

# 合音结果上传 vi-mdown

vi-mdown（ETCD 应用名 `com.cmic.vi.mdownload`，集群 `vii`）源码不在本次仓库。vi-merge 与 offline-merge 都向它上传合音文件，由它写入 PFS。本文只写这两个调用方的上传契约。节点地址来自 ETCD 的 InnerHost 与 InnerPort，本文不写具体主机。

## 1. 上传接口

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/inner/media/upload/<fileName>?path=&zone=&volume=` | vi-merge | vi-mdown | multipart 字段名 `media`。zone 为 ZoneC（分片）或 ZoneM（整通） | 调用方把 HTTP 2xx 当成功，不看 body 里的 code。连接错误或 5xx 换节点再传 1 次；4xx 或第二次失败即失败 | `vi-assets/vi-merge-master/internal/processor/file_processor.go:nodeUpload` |
| POST | `/inner/media/upload/<fileName>?path=&volume=` | offline-merge（Vi 模式） | vi-mdown | multipart 字段名 `media`。头 `Volume`、`session`。查询参数没有 zone，也不做 URL 转义 | 客户端自用码：0 成功，-1 连接错误，-2 为 5xx，-3 上传失败，-11 读超时，-14 不可达，-15 为 404，-16 Socket 异常 | `vi-assets/offline-merge-master/internal/processor/bparser/vi_parser.go:UploadUri`；`vi-assets/offline-merge-master/internal/httpinterface/filehttp.go` |

| 项 | vi-merge | offline-merge（Vi） |
| --- | --- | --- |
| 发现 | 同一会话优先复用上次节点，否则轮询 UP 节点，最多试 3 个 | `UploadNextNode` 轮询 |
| 超时 | 配置 outbound.timeout，生产模板 10 秒；大于 6MB 用 2 倍；拨号 5 秒 | 同样 10 秒量级，大文件放宽 |
| 成功后的 volume | 响应 data 长度大于 3 时缓存，后续分片和 MQ 沿用 | 写入出站消息的 volume |
| 失败落盘 | 整通录音可落到本地目录，代码里没有补传 | 可落到 `/data/offlocal/`，没有扫描补传任务 |

分片文件名 `Segment_<sessionId>_<segId>.wav`，永远是 wav。整通文件名 `<sessionId>_<yyyyMMddHHmmss>.wav`，仅在配置允许且缓存已落盘时才转 mp3。PFS 相对路径为 `userId/yyyy-MM-dd/<unix 秒>`。zone、volume 在 vi-mdown 上的目录含义待确认。

offline-merge 的 DCC 模式改传 `POST /media/upload/<fileName>`（body 为字节流），目标是 `com.cmic.media`，不是 vi-mdown。专线 Vi 模式不要走这条。

## 2. offline-merge 下载后再上传

离线合音先从 vi-media 回源，再上传。

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/media/download/<文件名>` | offline-merge | vi-media（消息里的 address） | body `{"fileName":...}`，头 `session`。不重试 | 下载长度不超过 100 字节视为坏腿 | `vi-assets/offline-merge-master/internal/httpinterface/filehttp.go:DownFilePost`；`vi-assets/vi-media-master/internal/router/media.go` |

vi-media 提供 GET 与 POST 两种 download。offline-merge 使用 POST。文件先查内存区，再查磁盘区。

上传失败时，offline-merge 仍可能发出“合音完成”消息，消息里没有上传是否成功的字段。下游看到 fileName 时，文件可能只在本机。待确认消费方如何辨别。

## 3. 待确认

1. vi-mdown 对缺少 zone 的请求如何选 PFS 根目录。
2. 响应体里 volume 的正式字段名。vi-merge 读的是 data，且长度要大于 3。
3. `/data/offlocal/` 与 vi-merge 本地兜底目录是否有外部补传。
4. 设计文档没有上传接口定义，上表完全来自调用方代码。
