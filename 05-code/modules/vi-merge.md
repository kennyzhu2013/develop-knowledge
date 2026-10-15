---
id: module-vi-merge
title: vi-merge 代码深度解析（流式分片合音）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-merge]
source_of_truth: [vi-assets/vi-merge-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-merge-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-merge.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-merge 仓库分析报告（实时分片合音）

> 仓库：`/workspace/vi-assets/vi-merge-master`（Go，模块名 `vi-merge`）
> README 原文仅一句：“专线质检，实时分片合音”。以下全部结论来自代码阅读；不确定处标“待确认”。
> 敏感信息处理：密码/AK/SK/ETCD 与 MQ 接入点等只写配置项名称；号码举例统一用 `700xxxx0001` 形式。

---

## 1. 定位

vi-merge 是二期“实时分片质检”链路中的**流式合音服务**：上游（设计图中为 vi-codec）在通话过程中通过 WebSocket `GET /v1/merge` 推送已解码的 PCM 帧（每帧首字节标识主叫/被叫声道），vi-merge 在内存中按“首片时长 initialSegments + 后续固定时长 segments”切片，把主被叫两路单声道 PCM 交织成双声道（或保持单声道）并加 WAV 头，**每切出一片就上传到 vi-mdown（`/inner/media/upload`，zone=ZoneC）并向 RocketMQ 发一条分片合音消息给话单（vi-bill）**，供实时分片质检使用；通话结束（收到 `EOS` 或连接断开）时上传最后一片（`endFlag=true`），并把整通录音（WAV，可选经 ffmpeg 转 MP3）以 zone=ZoneM 上传 vi-mdown，但**整通录音不再发 MQ**（代码已注释）。异常（上传失败、服务停止）通过 `X-Notice` 回调地址以 HTTP 通知媒体侧。

---

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言 / 版本 | Go 1.24（`go.mod`） |
| Web 框架 | gin v1.11.0；WebSocket：gorilla/websocket v1.5.3 |
| MQ | Apache RocketMQ 5.x Go 客户端 `rocketmq-clients/golang/v5 v5.1.3`（仅 Producer） |
| 注册中心 | etcd client v3.5.21（自研 `pkg/registry`） |
| 配置 | spf13/viper，读取工作目录下 `config/app.yaml`（`internal/app/app.go:ServerInit`） |
| 监控 | prometheus client_golang v1.19.1，gin-contrib/pprof |
| 音频工具 | 外部 `ffmpeg` 可执行文件（仓库内 `bin/ffmpeg` 为约 74MB 的静态二进制，未读取内容） |
| 构建 | `build.sh <version>`：`go build -ldflags "-X vi-merge/pkg/version.Version=..."` 输出 `bin/vi-merge`；`.cloudbuild/build.yml` 为云构建模板（BUILD 步骤只有 `echo hello`，含代码仓库地址，此处不展开） |
| 启动入口 | `cmd/vimerge/main.go:main` → `app.ServerInit()` → `systemSetting()`（RLIMIT_CORE 放开、RLIMIT_NOFILE=0x7FFFF）→ `app.StartService()` → `app.Stop()` |
| 监听端口 | 业务端口 `appInfo.port`（prod=**9033**，WS 入口）；运维端口 = port+1（prod=**9034**，/metrics、/ops、pprof），见 `conf/config.go:Init` |
| 部署脚本 | `bin/start.sh`：对 `./ffmpeg` `chmod 755` 后 `GOTRACEBACK=crash nohup ./vi-merge &`；`bin/stop.sh <port>`：`lsof` 找端口进程，`kill -TERM` 后轮询等待退出 |
| 环境区分 | 仅有 `config.prod/app.yaml`，值为 `{{ip}}`、`{{id}}`、`{{etcd1..3}}`、`{{rocketmq_ak}}` 等模板占位符，由部署系统渲染并放到 `config/app.yaml`（待确认渲染方式）。无 dev/test 配置目录 |
| 启动前置校验 | `app.ffmpegInitCheck`：要求 `<工作目录>/ffmpeg` 存在且权限**恰好**为 0755 或 0777（`internal/util/runtime_permissions.go:CanExecute`），否则 panic。配置项 `ffmpeg.staticPath` **未被使用** |

---

## 3. 代码结构

| 目录 / 包 | 职责 |
| --- | --- |
| `cmd/vimerge/main.go` | 进程入口、忽略 SIGPIPE、设置 rlimit |
| `conf/config.go` | 配置结构体 `AppConfig`（appInfo/logInfo/rocketMq/outbound/ffmpeg/bufferIO/bizSetting），计算 `HttpAddress`、`InnerHttpAddress` |
| `internal/app/app.go` | 初始化顺序：配置→日志→RocketMQ Producer→ETCD 注册→metrics→路由→ffmpeg 校验；启动 HTTP；`Stop()` 优雅停止 |
| `internal/service/route/router.go` | 业务路由 `GET /v1/merge`；运维路由 `/metrics`、`/ops/*`、pprof |
| `internal/service/route/wshandler/conn_handler.go` | WS 握手、参数解析、会话数上限、`readPump` 读循环 |
| `internal/service/route/wshandler/msg_router.go` | `BusinessRouter.RouteHandler`：解析消息内容 → 交给 `WsPcmProcessor.Process`，失败时经 WS 回写错误 |
| `internal/service/route/ops/handlers.go` | 运行时改配置、查看配置清单、ETCD 上下线（pause/resume） |
| `internal/service/ws_service.go` | WS 发消息/广播封装（当前业务未使用） |
| `internal/session/conn_manager.go` | 全局连接表（sessionId → conn、是否落盘、noticeUrl），落盘会话计数 `diskRwCnt`，停机时逐个通知媒体 |
| `internal/models/` | `bmessage.go`（Header 解析 `ParseBusinessHeader`、`HeaderParams`、EOS 常量）、`parser.go`（`MergeParser`：文本/二进制帧解析）、`wsconst.go`、`wserror.go`、`msgchan.go` |
| `internal/processor/wspcm_processor.go` | 每连接一个：按声道缓存 PCM，达到阈值（1/2/5 秒）后打包送合音协程；`Flush` 处理结束 |
| `internal/processor/ac2_processor.go` | 每连接一个合音协程：单/双声道合成、累积到分片长度后上传+发 MQ；结束时生成整通录音 |
| `internal/processor/file_cache.go` | 整通录音缓存：内存 `ByteBufferIO` → 超阈值切换磁盘 `FileBufIO`（`/data/merge/<yyyyMMdd>/<sessionId>/<sessionId>.pcm`） |
| `internal/processor/file_processor.go` | `FileStore`：选 vi-mdown 节点、上传、失败切节点重试、失败落本地盘 |
| `internal/processor/processor.go` | `GetPath`：PFS 相对路径 `userId/yyyy-MM-dd/unix秒` |
| `internal/httpinterface/filehttp.go` | 上传 HTTP 客户端（multipart / octet-stream / mock 三种实现，当前用 multipart） |
| `internal/httpinterface/customer/qa_http.go` | 直接调 QA 的 HTTP 客户端 `SoundQualityInspection`（**未被引用，死代码**） |
| `internal/notify/` | 异步 HTTP 通知（`HttpNotifyMedia` → X-Notice 地址）；`wsnotify/` 经 WS 回写错误 |
| `internal/pkg/broker/broker.go` | RocketMQ Producer 与 `BillMessage` 结构 |
| `internal/pkg/ff/` | ffmpeg 调用（`Pcm2Mp3Ac2`、`exeCmd` 带超时） |
| `internal/pkg/wavbox/` | WAV 头生成/解析、单声道→双声道交织（`mono16ToStereoDurationLong`） |
| `internal/pkg/registry/registry.go` | ETCD 注册、按服务名取节点（轮询） |
| `internal/pkg/sysmem/` | 读 `/proc/meminfo`，带 30 秒缓存（落盘决策用） |
| `internal/metrics/` | Prometheus 指标定义，命名空间 `vimerge` |
| `internal/middleware/` | 访问日志、panic 恢复 |
| `internal/util/` | 文件读写、目录、时间格式、权限判断、`PcmWriter`（仅调试注释代码使用） |
| `pkg/logger`、`pkg/metric`、`pkg/registry`、`pkg/version`、`pkg/process` | 公共库（日志、指标、etcd 注册发现、版本信息） |
| `pkg/mem/` | `SlabBuffer`、`directio`（**未被 internal 引用**） |
| `bin/ffmpeg` | ffmpeg 静态二进制（未读取） |

---

## 4. 核心业务流程

### 4.1 WebSocket 建连（`wshandler/conn_handler.go:ConnHandler.AudioPcmWs`）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 超时 |
| --- | --- | --- | --- |
| 1 | 解析请求头 `X-Params`（JSON）、`X-Notice`、`X-Business-Type`、`X-Version`；`userData` 先按 JSON 字符串反序列化，失败则去掉反斜杠后原样使用 | `models/bmessage.go:ParseBusinessHeader` | JSON 非法、`source>3`、`sampleRate%8000!=0` → HTTP 400 `{"error":...}`，指标 `stream_merge_ws_fail_count` |
| 2 | 默认值：BusinessType 空→`merge`；Version 空→`v1`；`segments<1`→30 | 同上 | — |
| 3 | 会话数检查：`当前连接数 > maxSession` 拒绝 | `AudioPcmWs` | HTTP 503 `{"error":"session over max session"}` |
| 4 | WS Upgrade（ReadBuffer/WriteBuffer 2048，CheckOrigin 只要求 GET） | `upgrader` | 失败回 400 |
| 5 | sessionId 取 `X-Params.sessionId`；为空则生成 `merge_<纳秒时间戳>`（**请求头 X-Session-Id 只用于日志**） | `AudioPcmWs` | — |
| 6 | `SetReadLimit(1MB)`，登记到 `ConnManager`，启动 `readPump` 协程；心跳 `writePump` 已于“20260605”屏蔽 | `AudioPcmWs` | — |
| 7 | `readPump` 创建 `WsPcmProcessor`，获取 `merge` 解析器，回写文本 `{"code":"0000","message":"Wait..."}` 通知上游可以开始推流 | `readPump` | 解析器不存在回 `{"code":"-1000","message":"BusinessType Invalid"}` 后退出 |

### 4.2 PCM 接收与切块（`processor/wspcm_processor.go:WsPcmProcessor.Process`）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 超时 |
| --- | --- | --- | --- |
| 1 | 循环 `ReadMessage`，每次读前设置 60 秒读超时（`READ_DEALLINE_TIME`） | `readPump` | 读错误/超时/对端关闭 → `Flush("End_Internal")` 后退出循环 |
| 2 | 二进制帧：长度 <10 字节视为控制帧；否则 `content[0]` 为 source（1=主叫，2=被叫），其余为 PCM | `models/parser.go:MergeParser.ParseContent` | 文本帧 JSON 解析失败 → 记日志，经 WS 回写错误（见 4.6） |
| 3 | 首个消息时初始化 `WavAc2Processor`，计算切块阈值 `mergeThreshold` | `Process` / `mergeTrigger` | source 越界被纠正为 1 或 3 |
| 4 | 二进制内容恰为 `EOS`（`[]byte{69,79,83}`）→ `Flush("EOS")` | `Process` | — |
| 5 | 按 source 写入 `leftBuf` / `rightBuf`；前 3 帧检查 `len%32`，不满足仅告警 | `Process` | 写失败只记日志 |
| 6 | 达到阈值：source=3 需左右都 ≥ 阈值；source=1/2 只看对应一路。读出一块（阈值字节数）放入合音通道 | `writeAudio2Merge` / `threshold` | 通道满（容量 250）→ 返回 `dataChan full`，**该块音频被丢弃**，指标 `stream_pcmmix_chanfull_count` |
| 7 | 文本帧 `{"event":"EOS"}` 同样触发 `Flush` | `Process` | 其它 event 忽略 |
| 8 | `Flush`：把左右缓冲剩余全部读出，以 Event 写入通道，失败最多重试 2 次、间隔 1 秒（阻塞读循环），随后清空并置空缓冲（之后再收到的 PCM 被静默丢弃） | `Flush` / `cleanBuffer` | — |
| 9 | `readPump` 退出时 `WsPcmProcessor.Close()` → 关闭合音协程的 `exit` 通道；`ConnManager.RemoveConn` 关闭 WS | `readPump` defer | 见第 12 节竞态 |

切块阈值 `mergeTrigger(sampleRate, initialSegments, segments)`：基础值 8k=16000 字节（1 秒 S16LE 单声道）、非 8k=32000 字节；若两个时长都能被 5 整除则×5，能被 2 整除则×2，否则×1。目的是让切块边界与分片时长对齐。

### 4.3 合音与分片（`processor/ac2_processor.go:WavAc2Processor.process`）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 超时 |
| --- | --- | --- | --- |
| 1 | 计算首片长度 `t1FragmentLen = initialSegments(<1 时取 segments) × frameLen × 50 × channelNum`、后续片长 `oneFragmentLen = segments × ...`；`frameLen = 320 × (sampleRate/8000)` 字节（20ms 帧） | `calFragmentLen` / `channelFrameLen` | — |
| 2 | 取出一块：source=3 → `wavbox.Pcm2Stereo` 左右按采样交织（短的一边补静音字节：s16le=0x0000，pcma=0xd5，pcmu=0xff）；source=1/2 → 直接用单声道。奇数字节长度先补 0 | `pcmMix`、`wavbox/audiochannel.go:mono16ToStereoDurationLong` | `ChannelErr` 时跳过 |
| 3 | 追加到 `pcmOutBuffer`，累计 `outPcmLen`（用于时长） | `process` | — |
| 4 | `pcmOutBuffer ≥ 当前片长` 或收到结束事件 → 整个缓冲读出成一片，`segId` 自增 | `process` | — |
| 5 | 非结束片：**另起协程** `mergeWavOutbound`；结束片：同步执行 | `process` | 代码 TODO：“考虑并发引起的乱序情况” |
| 6 | `mergeWavOutbound`：加 WAV 头 → 上传（zone=ZoneC，文件名 `Segment_<sessionId>_<segId>.wav`）→ 成功才发 MQ 分片消息 | `mergeWavOutbound`、`mergeAudioUpload`、`pubMessageMQ` | 上传失败：不发 MQ、回调媒体 EXCEPTION；分片不落本地 |
| 7 | 每片 PCM（无头）追加到整通录音缓存 `FileStore.recordPcm` | `file_processor.go:recordPcm` | 写失败只记日志 |
| 8 | 结束片：`finalFullAudio` 生成整通录音并上传（见 4.4）；之后 `cleanFullMergeFile` 删除本地缓存目录，协程退出 | `process` | — |

### 4.4 整通录音（`FileStore.finalFullAudio` + `mergeAudioUpload(isFinal=true)`）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 超时 |
| --- | --- | --- | --- |
| 1 | flush 缓存。缓存类型：起初为内存 `ByteBufferIO`；当 `decisionType=0` 且“落盘会话数 < diskRwMax”且缓存字节 > `buffer2DiskMinutes × 1,920,000` 时切换为磁盘 `FileBufIO`；`decisionType≠0` 时按主机内存使用率 > `memUsedPercent` 切换 | `file_cache.go:is2Disk`、`ByteBuffer2FileBuffer` | 切换失败继续用内存 |
| 2 | 文件名 `<sessionId>_<yyyyMMddHHmmss>.wav`（时间来自 `startTime`，格式 `2006-01-02 15:04:05`，本地时区） | `getFinalAudioName` | 时间解析失败时文件名中时间为空串 |
| 3 | 仅当 **`finalSuffixMp3=true` 且缓存已落盘**（存在本地 .pcm 文件）时，调用 ffmpeg 转 MP3；否则直接加 WAV 头 | `finalFullAudio`、`mergeAudioUpload` | ffmpeg 失败/输出 ≤100 字节 → 回退为 WAV（但此时 `pcmAc2DataAll` 为空，见第 12 节） |
| 4 | 上传 zone=ZoneM | `SelectNodeUpload` | 失败且无 MP3 文件 → 存本地 `/data/<userId>/<yyyy-MM-dd>/<unix秒>/<文件名>`（`saveDisk`），指标 `stream_save_disk_count`；同时回调媒体 EXCEPTION |
| 5 | **不发 MQ**（`pubMessageMQ(..., full=true)` 已注释，注释为“暂时不需要去通知”） | `mergeWavOutbound` | — |

ffmpeg 命令（`internal/pkg/ff/audio_trans.go:Pcm2Mp3Ac2`）：

```text
ffmpeg -hide_banner -y -loglevel quiet -f s16le -ar <sampleRate> -ac <1|2> -i <本地.pcm> -ar <sampleRate> -ab 32k -ac <1|2> -f mp3 <本地.mp3>
```

超时 `GetWaitTimeMs(1MB)` = 50 秒（`exec.CommandContext`）。成功判定：输出文件 >100 字节。上传成功后删除本地 MP3。**分片永远是 WAV，不调用 ffmpeg**。

### 4.5 上传 vi-mdown（`file_processor.go:FileStore.SelectNodeUpload` / `nodeUpload`）

| 步骤 | 做什么 | 失败处理 |
| --- | --- | --- |
| 1 | 同一会话优先复用上次节点 `fileNode`；为空时从 ETCD 取 `outbound.cluster=vii` / `outbound.appName=com.cmic.vi.mdownload` 的 UP 节点（`InnerHost:InnerPort`，全局轮询），最多尝试 3 次挑一个与当前不同的 | ETCD 无节点 → 失败，指标 `stream_node_notfound_count` |
| 2 | `POST http://<node>/inner/media/upload/<fileName>?path=<path>&zone=<ZoneC|ZoneM>&volume=<volume>`，multipart 字段名 `media` | — |
| 3 | 结果：HTTP 2xx 视为成功（不看响应体 code），若响应 `data`（volume）长度 >3 则缓存供后续分片和 MQ 使用 | — |
| 4 | 连接错误或 5xx（`NodeAccessError`）→ 换节点再传 **1 次** | 4xx 或第二次失败 → `upload fail`，指标 `stream_merge_upload_fail_count` |

超时：`outbound.timeout`（prod 10 秒）作为 Client.Timeout/ResponseHeaderTimeout；数据 >6MB 用 2 倍超时；拨号 5 秒、TCP keepalive 15 秒。

### 4.6 异常通知

- **HTTP 回调媒体**（`notify/global_notify.go:HttpNotifyMedia`）：目标为建连时 `X-Notice` 地址（长度 <3 则不发）。`POST`，body `{"sessionId":..,"message":..,"event":"EXCEPTION","data":{"code":"-1000","message":..}}`，头 `X-Session-Id`、`X-Sequence-Id=<MaxInt>`。触发：上传失败；服务停止时对每个在线会话发 `"con stop"`（`ConnManager.Stop`，每个间隔 100ms）。异步队列容量 20、1 个 worker、超时 3 秒、`RetryTimes=1`（即不重试），队列满直接丢弃（`notify/http_notice.go`）。
- **WS 回写错误**（`wsnotify/ws_notifier.go:NotifyClient`）：处理器返回错误时写文本帧 `{"Code":-1002,"Message":"processor execute failed: ..."}`（结构体无 json tag，键名首字母大写），写超时 2 秒，写失败移除连接。

### 4.7 停机（`app.Stop`）

收到 SIGTERM/SIGINT → `ConnManager.Stop`（逐会话 HTTP 通知媒体“con stop”，**不等待会话结束**）→ 关闭通知队列并等待发送完 → ETCD 注销 → 关闭 Producer → `server.Shutdown`。进行中的合音会随进程退出丢失。

---

## 5. 对外接口（入站）

| 协议 | 方法 + 路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| WebSocket | `GET /v1/merge`（业务端口 9033） | `wshandler/conn_handler.go:AudioPcmWs` | Header：`X-Params`（JSON，见下）、`X-Notice`（异常回调 URL，可选）、`X-Session-Id`（仅日志）、`X-Business-Type`（默认 merge）、`X-Version`（默认 v1） | 握手成功后文本帧 `{"code":"0000","message":"Wait..."}`；错误帧见 4.6 | 一个 WS = 一通通话 |
| WS 二进制帧 | 上行 | `models/parser.go:MergeParser` | `byte[0]` = source（1 主叫 / 2 被叫）+ PCM 数据；`EOS` 三字节 = 流结束 | — | 单帧上限 1MB（`WS_READ_LIMIT`） |
| WS 文本帧 | 上行 | 同上 | `{"event":"EOS","data":"..."}` | — | 只识别 EOS |
| HTTP | `GET /metrics`（运维端口 9034） | `router.go` | — | Prometheus 文本 | — |
| HTTP | `GET|PUT /ops/config/:config?value=` | `ops/handlers.go:Config` | `logLevel`、`maxSession`(200~5000)、`buffer2DiskMinutes`、`diskRwMax`、`decisionType`、`memUsedPercent`、`finalSuffixMp3`(1=开) | `{"message":"ok"}` 或 400 | 运行时修改，不持久化，**无鉴权** |
| HTTP | `GET /ops/configManifest` | `ConfigManifest` | — | 当前配置 | — |
| HTTP | `GET /ops/omu/:ops` | `OMUServiceStatus` | `status` / `pause`（ETCD 置 MAINTENANCE）/ `resume`（置 UP） | — | 用于摘流 |
| HTTP | `/debug/pprof/*` | `pprof.Register(InnerRouter)` | — | — | 运维端口 |

`X-Params` 字段（`models/bmessage.go:HeaderParams`）：

| 字段 | 类型 | 说明（代码行为） |
| --- | --- | --- |
| `sessionId` | string | 会话 ID，作为连接 key、文件名、MQ key |
| `callerNum` / `calleeNum` | string | 主被叫，透传到 MQ（例：`700xxxx0001`） |
| `userId` | string | 企业 ID，PFS 路径第一级 |
| `sampleRate` | int | 只校验 `%8000==0`（0、24000 等也能通过，见第 12 节） |
| `fmt` | string | `s16le`（默认）/`pcma`/`pcmu`，**大小写敏感** |
| `userData` | JSON 字符串 | 透传 MQ `userData` |
| `initialSegments` | int | 首片秒数，<1 时用 segments |
| `segments` | int | 后续片秒数，<1 时默认 30 |
| `startTime` | string | `yyyy-MM-dd HH:mm:ss`，用于 PFS 路径和整通文件名 |
| `source` | uint8 | 1 主叫 / 2 被叫 / 3 主被叫（3 时输出双声道，左=主叫，右=被叫） |

---

## 6. 消息

| 方向 | 中间件 | topic / tag | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 生产 | RocketMQ 5（namespace `VI`） | `rocketMq.topic`，prod=`TID_VI_MERGE_MSG_BILL_NORMAL`；tag 写死 `"*"`；keys=sessionId | —（Producer 无 group） | `BillMessage`，见下表 | `internal/pkg/broker/broker.go:PubBillMessage`，调用方 `ac2_processor.go:pubMessageMQ` |
| 消费 | — | 无 | — | — | 本服务不消费 MQ |

`BillMessage` JSON 字段（每个成功上传的分片一条）：

| 字段 | 取值 |
| --- | --- |
| `sessionId` | 会话 ID |
| `segmentId` | 分片序号，从 0 开始（`full=true` 时省略，当前不会出现） |
| `callerNum` / `calleeNum` / `userId` | 来自 X-Params |
| `fileNode` | 上传成功的 vi-mdown 节点 `InnerHost:InnerPort`（**不带 http://**） |
| `version` | 未赋值（omitempty） |
| `endFlag` | 结束片为 true |
| `duration` | `outPcmLen / (16000×channelNum)`（16k 为 32000），**是截至当前的累计时长**，不是单片时长 |
| `path` | `userId/yyyy-MM-dd/unix秒` |
| `fileName` | `Segment_<sessionId>_<segId>.wav`（**含扩展名**） |
| `volume` | vi-mdown 返回的 PFS 根 |
| `suffix` | 固定 `wav` |
| `isStream` | 固定 true |
| `uploadFlag` | `len(fileNode)>1`（实际只有上传成功才发消息，所以总为 true） |
| `fullAudio` | omitempty，当前恒 false |
| `userData` | X-Params.userData 文本 |

发送超时 3 秒，SDK `maxAttempts`（prod=3）。失败只计指标 `stream_merge_bill_fail_count` 并记日志，**不落盘、不补发**。Producer 为空时每次发送前尝试重建。

---

## 7. 依赖调用（出站）

| 目标 | 方式 | 路径 / 用途 | 超时 |
| --- | --- | --- | --- |
| vi-mdown（ETCD 服务名 `com.cmic.vi.mdownload`，集群 `vii`） | HTTP POST multipart | `/inner/media/upload/<fileName>?path=&zone=ZoneC|ZoneM&volume=`，上传分片与整通录音 | `outbound.timeout`（10 秒），>6MB 文件 20 秒 |
| 媒体回调（`X-Notice` 地址，设计上为 vi-media/vi-codec） | HTTP POST JSON | 异常/停机通知 | 3 秒，不重试 |
| RocketMQ | gRPC（SDK） | 分片合音消息 | 3 秒 |
| ETCD | etcd v3 | 自身注册 `com.cmic.vi.merge`；发现 vi-mdown | 租约 TTL 30 秒、续约间隔 15 秒（`pkg/registry/registry.go` 默认值） |
| ffmpeg（本地进程） | exec | PCM → MP3（仅整通录音、且满足条件时） | 50 秒 |
| QA（`customer/qa_http.go`） | HTTP | **未使用** | — |

---

## 8. 数据

- **数据库 / Redis / MongoDB**：无。
- **ETCD key**：注册 `/vii/micro-registry/com.cmic.vi.merge/<host>:<port>`（value 为 Node JSON：id、name、host、port、innerHost、innerPort、status UP/MAINTENANCE/OVERLOADED、version、metadata.Description="stream merge service"），带租约；读取 `/vii/micro-registry/com.cmic.vi.mdownload/` 前缀下 UP 节点。
- **PFS（经 vi-mdown）**：`<volume>/<userId>/<yyyy-MM-dd>/<unix秒>/Segment_<sid>_<n>.wav`（ZoneC）与 `<sid>_<yyyyMMddHHmmss>.wav|mp3`（ZoneM）。volume 与 zone 的落地语义由 vi-mdown 决定（待确认）。
- **本地路径**：
  - `/data/merge/<yyyyMMdd>/<sessionId>/<sessionId>.pcm`：长通话整通录音落盘缓存，结束时整个目录删除（`FileBufIO.BufferClose` → `util.RemoveDir`）；startTime 解析失败时为相对路径 `data/merge/<sessionId>/`。
  - `/data/<userId>/<yyyy-MM-dd>/<unix秒>/<整通文件名>`：整通录音上传失败时的本地兜底，代码中**没有补传逻辑**（待确认是否有外部脚本）。
  - 日志：`logInfo.logPath/logFile`（prod `/var/log/vi-merge/all.log`）。

---

## 9. 状态与枚举

| 名称 | 取值 | 含义 | 位置 |
| --- | --- | --- | --- |
| `EVENT_END` | `"EOS"` | 上游正常结束（二进制或文本 event） | `models/bmessage.go` |
| `EVENT_END_INTERNAL` | `"End_Internal"` | 读失败/断连时内部结束 | 同上 |
| `BusinessTypeMerge` 等 | `merge`（chat/game/notification 为遗留） | X-Business-Type | 同上 |
| `MergeWav` | `"mergeWav"` | 指标 label / ProType | 同上 |
| source | 1 主叫(左) / 2 被叫(右) / 3 双声道 | `SOURCE_LEFT/RIGHT/BOTH` | `processor/wspcm_processor.go` |
| `FmtType` | `FmtS16le`(16bit, code 1) / `FmtPcma`(8bit, code 6) / `FmtPcmu`(8bit, code 7) | WAV 头压缩码 | `wavbox/audiochannel.go` |
| Zone | `ZoneC`=分片，`ZoneM`=整通合音 | 上传 query 参数 | `processor/file_processor.go` |
| 上传结果码 | 0 成功 / -1 连接错误 / -2 5xx / -3 其它失败 | `httpinterface/filehttp.go` | — |
| ffmpeg 结果 | 1 成功 / -1 失败 / -2 输出过小 | `pkg/ff/runsh.go` | — |
| WS 错误码 | -1001 无处理器 / -1002 处理失败；文本码 `"0000"` 就绪、`"-1000"` 业务类型非法/异常 | `models/wserror.go`、`conn_handler.go` | — |
| 节点状态 | `UP` / `MAINTENANCE` / `OVERLOADED` | `pkg/registry/node.go` | — |
| 常量 | `BytesBufferLimit=4MB`（超过则不归还 sync.Pool）、`BufWriterSize=2MB`、`ReadBufSize=4MB`、`Buffer2DiskMinuteThreshold=1,920,000`、`LONG_FILE=6MB`、`WS_READ_LIMIT=1MB`、读超时 60 秒、dataChan 容量 250 | 各文件 | — |

Prometheus 指标（命名空间 `vimerge`）：`stream_merge_fragment_count`、`stream_merge_source_count{source}`、`stream_merge_bill_fail_count`、`stream_merge_upload_fail_count`、`stream_merge_ws_fail_count{reason}`、`stream_pcmmix_chanfull_count`、`stream_node_notfound_count`、`stream_save_disk_count`、`stream_buffer2file_count`、直方图 `stream_merge_upload_time`、Gauge `stream_merge_active_session`。

---

## 10. 定时任务

无业务定时任务。后台常驻协程：ETCD 续约/自愈/watch（`pkg/registry/etcd.go`）、`MemInfoCache` 按 30 秒过期惰性刷新（非定时器）、通知 worker。`httpinterface.StartCleanTicker`（定时清理空闲连接）有定义但注释“暂时不用”，未调用。

---

## 11. 关键配置项（`config.prod/app.yaml`，仅 prod 一套）

| 配置项 | 含义 | prod 取值 / 说明 |
| --- | --- | --- |
| `appInfo.host` / `port` | 监听地址；运维端口=port+1 | `{{ip}}` / 9033 |
| `appInfo.registry.clusterName` / `serverName` / `serverId` / `address` | ETCD 前缀、服务名、实例 ID、ETCD 地址 | `vii` / `com.cmic.vi.merge` / `{{id}}` / `{{etcd1..3}}` |
| `appInfo.maxSession` | 最大并发 WS 会话 | 2000（运维接口可调 200~5000） |
| `logInfo.*` | 日志级别、路径、文件、保留数 | info，`/var/log/vi-merge`，all.log，50 |
| `rocketMq.namespace` / `topic` / `maxAttempts` | MQ 命名空间、合音消息 topic、SDK 重试 | `VI` / `TID_VI_MERGE_MSG_BILL_NORMAL` / 3 |
| `rocketMq.accessKey` / `secretKey` / `endpoint` | MQ 凭证与接入点 | 模板占位，不在此展开 |
| `outbound.cluster` / `appName` / `uri` / `timeout` | vi-mdown 集群、服务名、上传 URI、超时秒 | `vii` / `com.cmic.vi.mdownload` / `/inner/media/upload` / 10 |
| `bufferIO.buffer2DiskMinutes` | 整通缓存超过 N 分钟（按 8k 双声道 1.92MB/分钟估算）转磁盘 | 4 |
| `bufferIO.diskRwMax` | 同时落盘会话上限（超过则继续用内存） | 300 |
| `bufferIO.memUsedPercent` | `decisionType≠0` 时主机内存使用率阈值 | 50 |
| `bufferIO.decisionType` | 0=按时长+并发判断落盘；其它=按内存使用率 | 0 |
| `ffmpeg.staticPath` | ffmpeg 路径 | `/home/production/vi-merge/ffmpeg`，**代码未使用**（固定取 `<工作目录>/ffmpeg`） |
| `bizSetting.finalSuffixMp3` | 整通录音是否转 MP3 | false |

---

## 12. 坑与特殊逻辑

1. **最后一片为空时不发 `endFlag=true`**：如果结束时 `pcmOutBuffer` 为空（例如刚好在分片边界结束，或 EOS 之前数据已全部切走），`readBuffer` 返回 nil → `merge` 因 `GetWavHead(0)` 报 “data empty” → `PcmEmptyError` → `mergeWavOutbound` 不发 MQ。下游可能**永远收不到结束标识**（`ac2_processor.go:mergeWavOutbound` / `wavbox/header_writer.go:wavHeader`）。
2. **分片异步上传导致乱序 + 整通录音拼接顺序错误**：非结束片用 `go mergeWavOutbound`，而 `recordPcm`（追加整通录音）在**上传完成后**才执行，所以整通录音的拼接顺序取决于上传完成顺序。上传慢（最长 10~20 秒）的片可能晚于结束片，导致整通录音缺片；若在 `cleanFullMergeFile` 之后才追加：内存缓存 → “pcmBuffer is nil” 报错；磁盘缓存 → `FileBufIO.Create` 会**重新建目录和文件且不再清理**（磁盘泄漏）。代码自带 TODO “考虑并发引起的乱序情况”。
3. **结束事件可能被丢弃**：`readPump` 退出时先 `Flush`（写通道）再 `Close`（关闭 `exit`）。合音协程 `select` 同时看到 `exit` 关闭和通道有数据时，Go 随机选择，可能直接退出 → 结束片、整通录音都不生成。正常 EOS 后上游很快断开时同样有风险。
4. **数据竞争**：`FileStore.fileNode`、`volume` 在多个上传协程中无锁读写；`getDur()` 在协程中读 `outPcmLen`（主协程同时在写），所以分片消息里的 duration 可能包含后续数据；`ConnManager.ConnDiskRw` 在读锁下修改；`notify` 全局 client 的 `sId` 被并发覆盖（日志 key 错乱）。
5. **duration 语义**：消息里的 `duration` 是累计时长，与设计文档“当前分片音频时长”不一致（见第 14 节）。
6. **pcma/pcmu 支持不完整**：分片长度、`getDur`、`mergeTrigger` 都按 2 字节/采样（S16LE）计算；fmt=pcma/pcmu 时分片实际时长翻倍、上报时长减半；ffmpeg 转 MP3 写死 `-f s16le`。`fmt` 大小写敏感（文档写 `S16LE/ALAW/ULAW`，代码只认小写 `s16le/pcma/pcmu`，`ALAW` 会被当成 s16le）。
7. **sampleRate 校验宽松**：只校验 `%8000==0`，`0` 也能通过 → `frameLen=0` → 片长为 0，每块都成一片；WAV 头采样率为 0。
8. **MP3 生效条件隐蔽**：`finalSuffixMp3=true` 只对**已落盘**的长通话生效；短通话（内存缓存）总是 WAV。MP3 上传失败时不 `saveDisk`（因 MP3 文件存在），随后目录被清理，整通录音丢失。另外，MP3 路径下 `pcmAc2DataAll` 为空，如果 ffmpeg 读文件失败回退 WAV，会因数据为空失败。
9. **上传失败无补偿**：分片上传失败只通知媒体，不重试、不落盘；整通失败落本地 `/data/...`，无补传；MQ 发送失败不补发。
10. **`X-Session-Id` 被忽略**：真正的会话 key 来自 `X-Params.sessionId`；两者不一致时日志难以关联；X-Params 无 sessionId 时生成 `merge_<ns>` 作为 key，但 MQ/文件名中的 `sessionId` 仍为空串（`WavAc2Processor.SessionId` 取的是生成值，`bHeader.SessionId` 为空，`GetPath` 失败分支会用到空值）。
11. **maxSession 判断用 `>`**：实际允许 maxSession+1 个连接。
12. **心跳已关闭**（20260605）：完全依赖 60 秒读超时；上游静音时不发帧会被断开（待确认上游是否持续发静音帧）。
13. **WS 错误帧格式不统一**：就绪/业务类型错误为 `{"code":"0000"/"-1000",...}`（字符串码），处理错误为 `{"Code":-1002,"Message":...}`（大写键、数字码）。
14. **`HttpNotifyMedia` 用 `fmt.Sprintf` 拼 JSON**，错误信息含引号时 `data` 非法 JSON。
15. **ffmpeg 权限检查过严**：`CanExecute` 要求权限恰好 0755/0777，0775、0750 都会启动失败（`start.sh` 会先 chmod 755）。`ffmpeg.staticPath` 配置无效。
16. **`UploadMultipartFile` 未对 path/volume 做 URL 转义**（旧的 `Upload` 有转义）；上传耗时直方图只在非 2xx/非 5xx 分支记录，成功的上传耗时没有统计。
17. **日志含明文主被叫号码和 userData**（`HeaderParams.String`、`BillMessage.String`、`PubBillMessage` 成功日志打印完整 JSON），与“日志不得写真实手机号”的要求冲突。
18. **停机不等待会话结束**：`Stop` 只通知媒体并 sleep 100ms/会话，进行中合音丢失。
19. 死代码 / 遗留：`customer/qa_http.go`、`WsService`、`writePump`、`pkg/mem`、`Hit10Percent`、`UploadTest`、chat/game 业务常量；`ConfigManifest` 未返回 `finalSuffixMp3`。
20. 运维接口（`/ops/config`、`/ops/omu/pause`）和 pprof **无鉴权**，只靠端口隔离。

---

## 13. 与其它仓库的关系

| 方向 | 对端 | 契约 |
| --- | --- | --- |
| 上游 | vi-codec（设计图：vi-media → vi-codec → vi-merge；vi-codec 不在本次源码中） | WS `GET /v1/merge` + Header `X-Params`/`X-Notice`，二进制帧 `source+PCM`，`EOS` 结束。vi-media 代码中有相同的 Header 常量 `X-Params`、`X-Notice`（`vi-media-master/internal/proxy/consts.go`），推测 vi-codec 透传 vi-media 的参数（待确认） |
| 回调 | X-Notice 地址（媒体侧） | HTTP POST `MediaInspectNotice{sessionId,message,event=EXCEPTION,data}` |
| 下游 | vi-mdown（ETCD `vii/com.cmic.vi.mdownload`，不在源码中） | `POST /inner/media/upload/<name>?path&zone&volume`，multipart `media`；响应 `{"code","data"=volume,"message"}` |
| 下游 | vi-bill / vi-billcenter | RocketMQ `TID_VI_MERGE_MSG_BILL_NORMAL`，`BillMessage`（分片，`isStream=true`）。**本次 vi-billcenter 源码只订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`（`RecordReceiver`，按 `code`/`callId` 解析，属一期 DCC 格式），未找到消费本 topic、解析 `segmentId` 的代码**（待确认二期话单消费方） |
| 兄弟 | offline-merge | 分工见 `offline-merge.md`：vi-merge 负责通话中实时分片（ZoneC）+ 整通 WAV（ZoneM，不发 MQ）；offline-merge 在通话结束后根据 vi-media 的录音文件生成整通录音并发 `endFlag=true`、`isStream=false` 的消息到另一个 topic |
| 注册 | ETCD | 自身注册 `com.cmic.vi.merge` |

---

## 14. 待确认问题清单

1. 二期话单中心实际消费哪个 topic？代码 `TID_VI_MERGE_MSG_BILL_NORMAL`、设计文档 `TID_VI_MERGE_BILL_RECORD_NORMAL`（该 topic 实际被 offline-merge 使用）、vi-billcenter 源码 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL` 三者不一致。
2. 文档与代码不一致（以代码为准）：
   - `startTime`：文档为 ISO8601 或毫秒时间戳，代码只解析 `yyyy-MM-dd HH:mm:ss`；
   - `segments` 默认值：文档 60，代码 30；
   - `fmt` 可选值：文档 `S16LE/ALAW/ULAW`，代码 `s16le/pcma/pcmu`；
   - `sampleRate`：文档仅 8000/16000，代码只校验 8000 的倍数；
   - MQ `fileName`：文档“不含扩展名”，代码含 `.wav`；`duration`：文档为当前分片时长，代码为累计时长；`fileNode`：文档要求带协议头，代码为 `host:port`。
3. 整通录音（ZoneM）上传后不发 MQ，下游如何得知其存在？是否完全由 offline-merge 的整通消息替代？
4. vi-merge 的 PFS 路径第一级为 `X-Params.userId`，vi-media/offline-merge 的 path 第一级为媒体侧 appId（文档样例为 `FZ001` vs userId `843`），两份整通录音是否会落在不同目录或相互覆盖？
5. vi-mdown 对 `zone=ZoneC/ZoneM` 的处理（保存时长、是否不同存储区）。
6. 上游在静音期是否持续发帧？心跳关闭后 60 秒读超时是否会误断长静音通话？
7. 本地 `/data/...` 兜底录音是否有补传脚本或人工流程？
8. `X-Notice` 实际由谁提供（vi-codec 还是 vi-media），收到 EXCEPTION 后是否触发降级（vi-media 有 `fallback` 逻辑）？
9. 上游 PCM 帧大小约定（代码按 32 字节倍数告警、按 20ms=320 字节/8k 计算），16k 时是否为 640 字节/帧？
