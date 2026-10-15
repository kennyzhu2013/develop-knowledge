---
id: module-vi-media
title: vi-media 代码深度解析（媒体锚定与录音）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-media]
source_of_truth: [vi-assets/vi-media-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-media-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-media.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-media（Xcache 媒体服务）仓库分析报告

> 分析角色：developer（研发视角的代码深读），兼顾架构/运维关注点。
> 分析范围：`/workspace/vi-assets/vi-media-master`（Go），全部目录逐个阅读；交叉核对了 vi-as-master、offline-merge-master 与设计文档 `专线质检-媒体实现详细设计接口.docx`。
> 约定：位置引用格式为 `相对路径:类型.方法`（Go 中"类"即 struct 或包级函数）；所有事实来自代码，不确定处标"待确认"。报告不含任何密钥、主机地址、真实号码。

---

## 1. 定位

vi-media 是专线质检链路中的**媒体锚定服务（Xcache）**。vi-AS 完成 SIP/SDP 信令后，通过 **HTTP/JSON** 调用 vi-media 创建媒体会话、更新双方 RTP 地址/编码、接通、挂机；vi-media 在本机分配 UDP 端口，**接收主被叫双方 RTP 并相互转发（媒体锚定）**，同时：

- 将双方音频（及可选视频）**录制为分段文件**（amr/wav/g729/h264），落本地两级缓存（内存盘 → 磁盘），供下游通过 HTTP 下载；
- 会话结束时向 RocketMQ 发布**分段合音消息**（`TID_VI_MEDIA_RECORD_MERGE_NORMAL`），由 offline-merge 消费并回源下载文件合音；
- 二期实时质检：收到 `/call/media/inspect` 后，通过 **WebSocket 将 RTP 负载帧 + 质检配置 + 事件推给 vi-codec**（`/media/stream`），vi-codec 通过 HTTP 回调 `/call/media/inspect/notice` 报告异常/结束；实时推流成功则**不再发合音 MQ**，失败则回退到离线合音（fallback=true）；
- 放音（早媒体/通话中提示音、DuDu 回铃音、静音、TTS）与 DTMF 收号（RFC2833 / 带内），放音文件从 cdn（`com.cmic.cdn`）拉取并本地缓存；
- 通过 HTTP 回调 vi-AS（`callbackUrl`，即 vi-AS 的 `/media`）上报放音完成、收号完成、流结束、会话超时释放等事件。

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言/版本 | Go 1.24（`go.mod`） |
| 主要框架/库 | gin（HTTP）、gorilla/websocket、spf13/viper（TOML 配置）、spf13/cobra（命令行）、apache rocketmq-clients golang v5（仅生产者）、etcd clientv3 + 内部 go-sdk registry、prometheus client、sony/gobreaker（熔断）、x/sync/singleflight、maypok86/otter（W-TinyLFU 视频缓存）、robfig/cron（带秒）、gopsutil（CPU 监控）、内部 codecx（仅用于带内 DTMF 解码） |
| 构建 | `build.sh <version>`：`go build`，通过 ldflags 注入 go-sdk/version 的 Version/GitCommit/BuildTime，产物 `bin/vi-media`；`.gitignore` 忽略 go.sum（构建依赖内部 GOPROXY，待确认） |
| 启动入口 | `cmd/media/main.go`（cobra，`--conf` 指定配置，默认 `config/app.toml`，见 `internal/app.go` 常量 defaultConfigFile；含 `version` 子命令）→ `internal/app.go:ServeAndListen` |
| 监听端口 | HTTP API：`apiAddress`，生产模板为 `:9060`（`config.prod/app.toml`）；可选 TLS（serverKey/serverCert，模板为空）。RTP/UDP：`ims.start`~`ims.end` = 10000~30000 |
| 部署脚本 | `bin/start.sh`：`GOTRACEBACK=crash nohup ./vi-media`（**不带 --conf**，依赖部署时把配置放到 `config/app.toml`）；`bin/stop.sh <port>`：lsof 找进程 → SIGTERM → 循环等待退出 |
| 环境区分 | 仅有 `config.prod/app.toml`，且为 `{{占位符}}` 模板（部署平台渲染）；无 dev/test 配置目录 |
| 运行前置 | `internal/pkg/rlimit:Reserve` 设置 core 无限、NOFILE=0x7FFFF；`internal/app.go:ServeAndListen` 校验 `/proc/sys/net/ipv4/ip_local_reserved_ports` 已配置（输出长度 ≥9），非 Windows 下未配置则**启动失败**（防止 RTP 端口被临时端口占用） |
| 观测 | `/metrics`（prometheus）、pprof、access log 中间件 |

## 3. 代码结构

| 目录/包 | 职责 |
| --- | --- |
| `cmd/media` | main 入口，cobra 命令 |
| `internal/app.go` | 启动/关闭编排：rlimit、端口保留检查、metric、broker、kv、proxy、gin、registry 注册、CPU 监控、优雅停机 |
| `internal/conf` | 配置结构体与加载（viper） |
| `internal/router` | 路由注册：`proxy.go`（`/call`）、`media.go`（`/media` 文件下载/删除）、`som.go`（运维 `/som`） |
| `internal/handler` | HTTP 处理：会话中间件（X-Session-Id / X-Sequence-Id）、各接口参数绑定与调用 proxy |
| `internal/middleware` | AccessLog（记录请求体及 json/sdp 响应体）、Recovery（500 "server error"） |
| `internal/dto` | 请求 DTO 与校验、`Result{code,data,message}` 统一响应 |
| `internal/proxy` | **核心**：会话管理器（槽位/端口）、Session FSM 与事件循环、RTP 收发转发、录音分段、实时推流 outbound、放音（rtpPlayer/rtpSender）、DTMF、回调、avcache 初始化、统计与指标 |
| `internal/pkg/mediasource` | WebSocket 客户端协议：outbound（推 vi-codec）、inbound（未使用）、资源解析（registry 寻址） |
| `internal/pkg/broker` | RocketMQ 生产者与 `MediaMergeMessage` |
| `internal/pkg/cache` | 录音文件两级缓存（ZoneC：mem → disk），过期迁移、索引重写、目录清理 cron |
| `internal/pkg/kv` | etcd 客户端、Watch/Notice |
| `internal/pkg/kv/registry` | 服务注册/发现（etcd 前缀 `/<cluster>/micro-registry`），状态 Up/Maintenance/Overloaded，权重，RoundRobin / ConsistentHash 负载均衡 |
| `internal/pkg/tts` | TTS 调用（outbound 应用 `/media/voice/tts`） |
| `internal/pkg/monitor` | CPU 采样，超阈值把注册状态切 Overloaded |
| `internal/pkg/rlimit` | 资源限制 |
| `internal/pkg/log` | 异步日志封装（go-sdk） |
| `pkg/ministore` | 类 bitcask 的追加写索引库（`miniDb.data`），Rewrite/Rebuild，卷选择策略（WeightedRoundRobin），磁盘 IO 流 |
| `pkg/format/record` | 录音文件写入：AMR（BE/OA）、G.711 WAV、G.729、H.264；静音/丢帧补偿、乱序处理、内存缓冲 |
| `pkg/rtp` | RTP 包池、Track 绑定、UDP 监听（SO_REUSEPORT）、RTCP 解析（未用）、分片 |
| `pkg/rtp/codec` | 编码参数、放音文件分片为 RTP 负载（`Sharding`/`ShardingFile`） |
| `pkg/dtmf` | RFC2833 与带内（Goertzel + codecx）DTMF 检测 |
| `pkg/fsm` | 通用有限状态机 |
| `pkg/hash` | 一致性哈希环 |
| `pkg/cmd` | `bash -c` 执行封装（用于目录清理） |
| `pkg/cache` | 通用 SimpleCache（FIFO 按容量淘汰） |
| `pkg/concurrent/promise` | 异步 Promise |
| `test/mstream` | 模拟 vi-codec 的 WS 服务（`/media/stream` 统计二进制帧直到 END，`/media/notice` 回 200） |
| `test/simulation` | 独立 module，呼叫模拟/压测器（Create→Update→Inspect→Pickup→Stop，发 RTP） |
| `test/simulation/resources` | amr/wav 测试音频（见 §12.10） |
| `config.prod` | 生产配置模板 `app.toml` |
| `bin` | start/stop 脚本 |

## 4. 核心业务流程

### 4.1 启动

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 读取配置 | `internal/conf/config.go` | 失败退出 |
| 2 | rlimit 设置 | `internal/pkg/rlimit:Reserve` | 记录日志 |
| 3 | 校验 ip_local_reserved_ports | `internal/app.go:ServeAndListen` | 未配置直接返回错误（非 Windows） |
| 4 | 信号上下文 SIGTERM/SIGINT | `internal/app.go:ServeAndListen` | — |
| 5 | metric、broker（RocketMQ producer）、kv（etcd，DialTimeout 5s） | `internal/pkg/broker:Init`、`internal/pkg/kv:Init` | 失败退出 |
| 6 | proxy.Init：会话管理器、avcache（cdn 预热、DuDu）、录音缓存 ZoneC、etcd watch | `internal/proxy/proxy.go:Init`、`internal/proxy/rtp.go:initAvCache` | cdn 预热失败记录日志（待确认是否阻断） |
| 7 | gin 路由 `/metrics` `/som` `/media` `/call` + pprof，HTTP/TLS 监听 | `internal/app.go`、`internal/router/*` | — |
| 8 | registry.Register 注册到 etcd | `internal/pkg/kv/registry` | 每 3s 重试 |
| 9 | 可选 CPU 监控 | `internal/pkg/monitor/cpu.go` | 超阈值切 Overloaded，恢复切 Up |

### 4.2 优雅停机

`internal/app.go:Shutdown`：registry.Close（摘流量）→ proxy.Stop（**阻塞直到所有会话槽位空闲**，每 1s 检查）→ server.Shutdown → cancel ctx → proxy.Wait（等待缓存扫描协程）→ broker.Stop。注意 stop.sh 无超时，长通话（最长 4 小时）会使停机等待很久。

### 4.3 会话生命周期（vi-AS 驱动）

| 步骤 | 做什么 | 代码位置 | 失败/超时 |
| --- | --- | --- | --- |
| Create | 分配会话槽位与端口（audio=base，video=base+2），启动 `run()` 事件循环，状态 NONE→START；返回 ip(=ims.host)/audioPort/videoPort | `internal/handler/proxy.go:SessionCreate`、`internal/proxy/session_manager.go:GetFree`、`internal/proxy/session.go:Create` | 无空闲槽位 503 "session has reached a limit"；参数校验失败 400 |
| Update | 设置主被叫 RTP 地址、编码、DTMF payload、视频参数；首次打开 UDP 监听；START→ACTIVE（IN_CALL 时保持）；编码变化时新开录音分段并向 vi-codec 发 CODEC_SWITCH | `internal/proxy/session_process.go:update`、`internal/proxy/stream_audio.go:Observe` | FSM 校验不通过返回错误；解析地址失败 400 |
| Play/Dtmf（可选） | 早媒体/通话中放音、收号 | `session_process.go:play`、`session_process.go:dtmf` | 完成时回调 AUDIO_DONE/VIDEO_DONE/DTMF_DONE |
| Inspect（二期） | 设置 needVI/enableVI、viFrames=frames*50、viSource、viConfig | `internal/proxy/session.go:MediaInspect` | 校验失败 400 |
| Pickup | 解析 callTime，停止放音，`audioRecord.Update(s,false)` 创建录音文件，ACTIVE→IN_CALL，向 outbound 发 CALLING | `session_process.go:pickup` | callTime 解析失败则保持 0（不报错） |
| Stop | 直接调用 `stop()`（不走事件队列），cancel ctx；`run()` 退出时依次：stopPlay → outbound.Close → wg.Wait → streamAudioProxy.Close（关闭 UDP、DTMF、`audioRecord.Cancel` 落缓存 + 发 MQ）→ `Manager.Free` | `session.go:Stop`、`session.go:run`、`record_audio.go:Cancel` | — |
| 超时扫描 | 每 3 分钟：START 超 60s、ACTIVE 超 90s（自 createTime）、IN_CALL 超 14400s（自 callTime）→ 回调 SESSION_RELEASE 并 stop | `session_manager.go:scan`、`session.go:Scan` | 注释写 3 小时，实际 4 小时 |
| RTP 超时 | IN_CALL 状态下连续无包，10ms 读超时累加超过 50000ms（MaxRtpLatency）→ 回调 SESSION_RELEASE "session timeout" 并 stop | `stream_audio.go:Receive` | — |

### 4.4 事件有序执行（X-Sequence-Id）

| 步骤 | 做什么 | 代码位置 |
| --- | --- | --- |
| 1 | `Session` 中间件：要求 X-Session-Id 与整数 X-Sequence-Id，找会话；不存在返回 400 "session is not exists" | `internal/handler/proxy.go:Session` |
| 2 | 每个请求封装为 `event{id,name,invoke}`，写入容量 3 的 channel，阻塞直到 run 循环接收或 ctx 结束 | `internal/proxy/session.go` |
| 3 | `processEvent`：id=255 为内部事件立即执行；id≤curId 丢弃；乱序事件暂存 `events[5]`（满则丢弃）；按序执行并 curId++ | `session.go:processEvent` |
| 4 | 请求被 abort 且状态非 500 时调用 `session.Fallback(seqId)` 推入空事件，使序号前进，避免后续事件卡死 | `internal/handler/proxy.go:Session` |

### 4.5 RTP 接收与转发

`internal/proxy/stream_audio.go:Receive`（在 `run()` 的忙循环里，UDP 读超时 10ms）：

1. 来源判定：源地址 == callerAddr 或 preCallerAddr → 主叫；其他 → 被叫，并标记网络音频就绪（priority=Net 的放音会因此停止）。
2. DTMF 检测（RFC2833 或带内）。
3. 写录音（`pkg/format/record`），得到 (frames 字节, nFrames, mFrames 静音补帧数)。
4. 若 outbound 已激活且 viSource 允许该方向 → `outbound.Send(KindAudio, source, mFrames, frames)`。
5. 转发到对端：主叫包除非 callerAddr==calleeAddr 否则转发；被叫包仅在 IN_CALL 或源地址等于 calleeAddr 时转发（单通保护）。放音期间 `Discard` 替换转发函数（仍放行 telephone-event 包）。
6. 实时推流触发：enableVI 且 `record.Frames(mime, CallerRecordCnt 或 CalleeRecordCnt) >= viFrames` → 启动 outbound 推流，enableVI=false。

### 4.6 实时分片质检（二期）推流

| 步骤 | 做什么 | 代码位置 | 失败/超时 |
| --- | --- | --- | --- |
| 1 | 触发：某一侧已录满 `frames` 秒（frames*50 帧） | `stream_audio.go:Receive` | — |
| 2 | 建 WS：`ws://<vi-codec 节点>/media/stream`，节点由 registry 按 `outbound.clusterName@appName` 轮询；头 X-Session-Id、X-Params=viConfig、X-Notice=`http://<apiAddress>/call/media/inspect/notice` | `internal/proxy/stream_outbound.go`、`internal/pkg/mediasource/conn.go:getResource` | 握手超时 5s；失败走 onCancel |
| 3 | onSuccess（内部事件 255）：若已 IN_CALL 发 CALLING(callTime 秒)，needVI 时 `audioRecord.Flush` 回灌；再发 CODEC_SWITCH(mime)；可选推视频 | `stream_outbound.go:onSuccess` | — |
| 4 | Flush 回灌：对已关闭分段逐个先发 CODEC_SWITCH，再按 1 秒块（50*FrameSize 字节）主被叫交替发送；一侧缺数据时发 FRAME(N-1)+1 个静音帧对齐；再回灌当前分段内存缓冲，`MarkInMemory(false)` | `internal/proxy/record_audio.go:Flush` | — |
| 5 | 之后每个 RTP 包实时转发帧 | `stream_audio.go:Receive` | 队列满**静默丢弃** |
| 6 | vi-codec 异常/结束通过 HTTP `/call/media/inspect/notice` 回调 → `outbound.Sema` → 写 END、关闭、Callback | `session_process.go:inspectStreamNotice`、`mediasource` outbound `process` | — |
| 7 | onCancel：回调 vi-AS STREAM_DONE（有 err 则 code 500）；err≠nil 且 needVI → DisableVI + FallbackVI（会话结束时发 MQ 且 fallback=true） | `stream_outbound.go:onCancel` | — |

### 4.7 会话结束与分段合音消息

`internal/proxy/record_audio.go:Cancel`（仅 IN_CALL 且录音时）：关闭当前分段 → 文件加入缓存 ZoneC → 组装 `broker.MediaMergeMessage` → **若 needVI && !enableVI（实时推流已进行且未回退）则不发**，否则 `broker.PubMergeMessage`（tag `*`，keys=sessionId，发送超时 3s，maxAttempts 3）。

### 4.8 放音

`internal/proxy/rtp.go`、`rtp_sender.go`：源可以是逗号列表（顺序播放）、`;` 分隔（diff 模式：主被叫各放各的）、特殊值 `DuDu`（回铃音）/`Mute`/`TTS:<文本>`。音频先查本地 SimpleCache，未命中 POST cdn `/audio/get?name=` 拉取（超时 audioTimeout 2s；404 负缓存）；文件经 `pkg/rtp/codec:Sharding` 切成 RTP 负载，每 20ms 发一包；视频走 `/video/get`（熔断 + singleflight）、3gp 解复用、按 fps 发包。完成回调 AUDIO_DONE/VIDEO_DONE（data=period）。编码切换时 `switchAudioSource` 切换对应编码变体。

### 4.9 DTMF 收号

`session_process.go:dtmf`：可先放提示音（失败回退默认源）；dtmfPayload>0 用 RFC2833 检测器，否则带内（Goertzel + codecx 解码）；默认长度 4、超时 20s；结束原因 Timeout/HangUp/Len/sign；回调 DTMF_DONE `{digit,endCause,source}`。

## 5. 对外接口（入站）

统一响应 `dto.Result{code,data,message}`（`internal/dto/result.go`）。`/call` 下除 create 外均需 Header `X-Session-Id`、`X-Sequence-Id`。

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | POST `/call/session/create` | `internal/handler/proxy.go:SessionCreate` | sessionId、userId（→AppId）、callerNum、calleeNum、mergeConfig（必填）、startTime（`2006-01-02 15:04:05`）、callbackUrl（必填）、recordMode（1 录音/2 全量/3 不录）、**direction（1/2/3 必填）**、seqId(uint8) | sessionId、ip、audioPort、videoPort、seqId | 无槽位 503 |
| HTTP | POST `/call/session/update` | `internal/handler/proxy.go` → `session_process.go:update` | audioCallerIp/Port、audioCalleeIp/Port（必填）、audioCode（pcma/pcmu/amr/amr-octet/amr-wb/amr-wb-octet/g729）、audioPayload、audioDtmfPayload、video ip/port/payload/code（h264-25/30/50/60）、videoObserve | — | 地址解析后加 "audio/""video/" 前缀 |
| HTTP | POST `/call/session/pickup` | `session_process.go:pickup` | startTime、disableVIS（未使用）、seqId | — | 进入 IN_CALL |
| HTTP | POST `/call/session/stop` | `session.go:Stop` | endTime、seqId | — | 不走事件队列 |
| HTTP | POST `/call/media/dtmf` | `session_process.go:dtmf` | direction（注释称支持 4，绑定只允许 1/2/3）、endSign、backSign、expectLen、timeout、intervalTimeout、timingMode、interrupted、recordMode、mode（1/2）、提示音源 | — | 结果异步回调 DTMF_DONE |
| HTTP | POST `/call/media/play` | `session_process.go:play` | audio/loop 源、audioCode/audioPayload（direction=4 时）、video 源、period（Early/InCall）、priority（Local/Net）、direction 1–4、recordMode 0–3 | — | 异步回调 AUDIO_DONE/VIDEO_DONE |
| HTTP | POST `/call/media/inspect` | `session.go:MediaInspect` | sessionId、config（必填，透传给 vi-codec 的 X-Params）、frames（秒，>0）、direction 1/2/3 | — | 二期实时质检开关 |
| HTTP | POST `/call/media/inspect/notice` | `session_process.go:inspectStreamNotice` | sessionId、message、event（EXCEPTION/TERMINATION）、data | — | **vi-codec 回调入口**（经 X-Notice 下发），同样需要 X-Sequence-Id（待确认 vi-codec 如何填） |
| HTTP | POST `/call/media/stream` | `internal/handler/proxy.go` | sources、mediaSource、config（必填）、pushVideo、recordMode | — | 媒体流推送（与 inbound 相关，inbound 未启用，待确认实际用途） |
| HTTP | POST `/call/media/stop` | `internal/handler/proxy.go` | mark（DTMF/Media） | — | 停止收号/放音 |
| HTTP | GET/POST `/media/download/:filename` | `internal/router/media.go` | filename | 文件内容 | 从 ZoneC 读取（先 mem 后 disk）；offline-merge 用 POST |
| HTTP | GET/DELETE `/media/delete/:filename` | `internal/router/media.go` | filename | — | 删除缓存文件 |
| HTTP | GET `/som/config/:config?value=` | `internal/router/som.go` | cMemLifeSpan、cDiskLifeSpan、expiredScan（fetch 大小）、flush、logLevel（0=debug）、audioTimeout、videoTimeout | — | 运维动态调参 |
| HTTP | GET `/som/service/:ops` | `internal/router/som.go` | pause/resume/status/weight/cpu_threshold | — | 注册状态/权重管理 |
| HTTP | GET `/som/cache/media/reload?name=`、lookup、elements | `internal/router/som.go` | name | — | 放音缓存重载/查询 |
| HTTP | GET `/som/cache/index/rewrite?zone=&level=` | `internal/router/som.go` | zone、level | — | 手动索引重写 |
| HTTP | GET `/metrics`、pprof | `internal/app.go` | — | — | 观测 |
| UDP/RTP | `ims.host:10000~30000` | `internal/proxy/stream_audio.go`、`stream_video.go` | RTP（音频 base 端口，视频 base+2） | 转发给对端 | 每会话 4 个端口位，RTCP（base+1）未打开 |

**结论：vi-AS → vi-media 之间没有 SIP/SDP，全部是 HTTP/JSON；SIP/SDP 由 vi-AS 自己处理，vi-media 只返回 ip/port 供 vi-AS 填 SDP。**

## 6. 消息（MQ）

| 方向 | 中间件 | topic / tag | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 生产 | RocketMQ v5（namespace `VI`） | `TID_VI_MEDIA_RECORD_MERGE_NORMAL` / tag `*`，keys=sessionId | 生产者无 group；消费方 offline-merge（设计文档写 GID_VI_MERGE_RECORD） | sessionId、callerNum、calleeNum、userId、msId（registry.appId）、address（apiAddress，供回源下载）、version、volume（未赋值）、path、callerAudioFile/calleeAudioFile（逗号分隔的分段文件名）、callerVideoFile/calleeVideoFile（**未赋值**）、earlyAudioFile（未赋值）、options（mergeConfig）、config（viConfig）、startTime（**unix 秒**）、fallback、音视频 RTP/通话中 RTP/录音计数 | `internal/pkg/broker/broker.go:PubMergeMessage`、`internal/proxy/record_audio.go:Cancel` |

无消费者。

### 6.1 WebSocket 推流协议（vi-media → vi-codec，非 MQ 但属消息契约）

`internal/pkg/mediasource`：

- **二进制帧**：`[kind 1B: 0x01 音频 / 0x02 视频][source 1B: 1 主叫 / 2 被叫][payload]`
  - AMR：存储格式帧（TOC 字节 + 语音比特，无文件头）
  - G.711 / G.729：RTP 原始负载
  - 视频：原始 RTP 包
- **文本帧** JSON `{"event":..., "data":...}`：

| event | data | 时机 |
| --- | --- | --- |
| FRAME | 需补的静音帧数 | 二进制帧前，mFrames>0 时 |
| CALLING | callTime（unix 秒） | 接通时/推流建立时已接通 |
| CODEC_SWITCH | mime，如 `audio/amr` | 推流建立、编码变化、Flush 每个分段前 |
| END | — | 退出前 |

- 单向写：`ReadEnabled` 从未设置，`readLoop`/`onStream` 不运行；vi-codec 的反馈走 HTTP `/call/media/inspect/notice`。
- 写超时 30ms（`outbound.writeTimeout`），握手 5s；队列长度 QSize = viFrames/50*viSource+15；`Send`/`Notice` 非阻塞，满则**静默丢弃**。

### 6.2 cdn 预热 WebSocket（vi-media → cdn）

`internal/proxy/rtp.go:initAvCache`：`ws://<cdn 节点>/audio/accelerate`，每条二进制消息 `[4B nameLen BE][4B mimeLen BE][name][mime][文件数据]`，文本 `END` 结束。

## 7. 依赖调用（出站）

| 目标 | 协议/路径 | 用途 | 超时/保护 | 代码位置 |
| --- | --- | --- | --- | --- |
| vi-AS | HTTP POST `callbackUrl`（vi-AS 的 `/media`） | 回调事件 `{sessionId,event,code "200"/"500",message,data,seqId}` | 10s，InsecureSkipVerify，**失败只记日志不重试** | `internal/proxy/callback.go:callback` |
| vi-codec | WS `/media/stream`（registry：`outbound.clusterName@appName`） | 实时推流 | 握手 5s，写 30ms | `internal/proxy/stream_outbound.go` |
| TTS（outbound 应用） | HTTP POST `/media/voice/tts` `{sessionId,mimeType,text}` | 文本转语音放音 | 5s | `internal/pkg/tts/tts.go` |
| cdn（`com.cmic.cdn`） | WS `/audio/accelerate` | 启动时批量预热放音 | — | `internal/proxy/rtp.go:initAvCache` |
| cdn | HTTP POST `/audio/get?name=` | 拉音频放音文件，ROUND_ROBIN | audioTimeout 2s；404 负缓存 | `internal/proxy/rtp.go` 音频 Loader |
| cdn | HTTP POST `/video/get` | 拉视频文件，HASH_RING | videoTimeout 2s；gobreaker "cb-cdn" + singleflight | `internal/proxy/callback.go:cbPost` |
| RocketMQ | — | 合音消息 | 3s，maxAttempts 3 | `internal/pkg/broker` |
| etcd | clientv3 | 注册发现、放音重载 watch | DialTimeout 5s | `internal/pkg/kv` |

## 8. 数据

- **数据库表 / Redis / MongoDB**：无。
- **ETCD key**：
  - `/<cluster>/micro-registry/...`：服务注册（cluster=registry.clusterName，生产为 `vii`），含状态、权重；同时用于发现 vi-codec、cdn、TTS 节点（`internal/pkg/kv/registry`）。
  - `/vi-media/audio-capture`（前缀 watch）：值为逗号分隔的放音文件名，变化时逐个 `rSync` 重载（`internal/proxy/rtp.go`）。
- **本地文件**：
  - 录音文件：`<volume>/<appId>/<YYYY-MM-DD>/<activeTime unix>/[n]Caller_<sessionId>_<YYYYMMDDhhmmss>.amr|.wav|.g729|.h264`（Callee 同理；n 为分段序号前缀），volume 先为 `storage.data.mem.call`（生产 `/tmp/media/call`，生命期 10 分钟），过期后迁移到 `storage.data.disk.call`（生产 `/data/media/call`，生命期 `diskLifeSpan` 模板值）（`pkg/format/record`、`internal/pkg/cache`）。
  - 索引库：`storage.db.mem.call`、`storage.db.disk.call` 目录下 `miniDb.data`（追加写，实体头 `[keySize 4][valSize 4][mark 2]`，mark PUT=0/DEL=1），重写产生 `miniDb.data.merge`，重建产生 `miniDb.data.rebuild`（`pkg/ministore`）。
  - CacheItem（JSON）：Name、Path、Volume、LifeSpan、Metadata、CreatedOn、AccessedOn（Get 不更新 AccessedOn）。
  - ring 相关配置（db/data 的 ring）未使用。
- **内存缓存**：音频放音 SimpleCache（FIFO，maxAudioCapacity MB）；视频 otter W-TinyLFU；DuDu 预置 `global/global_dudu_{nb.amr,wb.amr,pcma.wav,pcmu.wav,bcg.g729}`。

## 9. 状态与枚举

| 名称 | 取值 | 含义 / 位置 |
| --- | --- | --- |
| 会话状态 | NONE / START / ACTIVE / IN_CALL / STOPPED | `internal/proxy/session.go` FSM；事件 CREATE / UPDATE / PICKUP / CANCEL |
| 内部事件 id | 255（MaxUint8） | 不受序号约束立即执行 |
| MaxWaitEvents | 5 | 乱序事件暂存上限 |
| 回调事件 | AUDIO_DONE、VIDEO_DONE、DTMF_DONE、STREAM_DONE、SESSION_RELEASE | `internal/proxy/callback.go` 及调用处 |
| 回调 code | "200" / "500" | 成功 / 失败 |
| recordMode | 1 录音 / 2 全量 / 3 不录（play/dtmf 允许 0） | `internal/dto` |
| direction / Source | 1 主叫 / 2 被叫 / 3 双方 / 4 diff | `internal/dto`、`internal/pkg/mediasource` |
| WS kind | 0x01 音频 / 0x02 视频 | `internal/pkg/mediasource` |
| WS 事件 | FRAME / CALLING / CODEC_SWITCH / END | 同上 |
| Notice 事件 | BROKEN / EXCEPTION / TERMINATION | 同上；HTTP 入参只接受 EXCEPTION/TERMINATION |
| outbound 状态 | New / Prepare / Running / Terminated | 同上 |
| period | Early / InCall | 放音时机 |
| priority | Local / Net | Net：对端网络音频到达即停放音 |
| 特殊放音源 | DuDu / Mute / `TTS:<text>` | `internal/proxy/rtp.go` |
| DTMF endCause | Timeout / HangUp / Len / sign | `pkg/dtmf` |
| media stop mark | DTMF / Media | `internal/dto` |
| 注册状态 | Up / Maintenance / Overloaded | `internal/pkg/kv/registry` |
| audioCode | pcma, pcmu, amr, amr-octet, amr-wb, amr-wb-octet, g729 | `internal/dto` |
| videoCode | h264-25/30/50/60 | `internal/dto` |
| 关键常量 | FrameRate 50（20ms/帧）、MaxRtpLatency 50000ms、MaxRtpOutOfOrder 5、录音缓冲初始 488000/刷盘 97600 字节、AMR 补帧最多 25、会话超时 60s/90s/14400s | `pkg/rtp/codec`、`stream_audio.go`、`pkg/format/record`、`session.go` |

## 10. 定时任务

| 任务 | 周期 | 内容 | 位置 |
| --- | --- | --- | --- |
| 会话扫描 | 每 3 分钟 | 重算 active、处理 START/ACTIVE/IN_CALL 超时 | `internal/proxy/session_manager.go:scan` |
| 缓存过期扫描 | 每 10s | ZoneC 过期项迁移（mem→disk）或删除；FetchSize 10000、ExpiredFetchSize 2000 | `internal/pkg/cache` |
| 索引重写 | cron `0 0 3 * * ?`（`storage.db.rewrite`） | 所有表的 ministore 压缩 | `internal/pkg/cache`、`pkg/ministore` |
| 目录清理 | cron `0 0 4 * * ?`（`storage.data.cleanup`） | `bash -c "rm -Rf <dir>/*/<7 天前日期>"`，mem 与 disk 各一次 | `internal/pkg/cache:dirPurge` |
| CPU 监控 | tick 5s / interval 10s（默认关闭） | 超阈值 50% 切 Overloaded | `internal/pkg/monitor/cpu.go` |
| 注册重试 | 每 3s 直到成功 | — | `internal/pkg/kv/registry` |

## 11. 关键配置项

仅有 `config.prod/app.toml`（模板），无其他环境差异可比。敏感值不列出。

| 配置项 | 含义 | 生产模板 |
| --- | --- | --- |
| `apiAddress` | HTTP 监听地址，同时作为 X-Notice 回调地址与 MQ `address` 字段 | 端口 9060 |
| `serverKey` / `serverCert` | TLS 证书，空则 HTTP | 空 |
| `ims.host` | RTP 本机地址，Create 返回给 vi-AS 填 SDP | 占位符 |
| `ims.start` / `ims.end` | RTP 端口范围；会话数 = (end-start)/4 | 10000 / 30000（5000 会话） |
| `registry.clusterName/appName/appId/weight/address` | etcd 注册 | clusterName `vii`，weight 100，3 个 etcd 地址（占位符） |
| `message.namespace/ak/sk/endpoint/maxAttempts/topic` | RocketMQ（ak/sk 为敏感项） | namespace `VI`，maxAttempts 3，topic `TID_VI_MEDIA_RECORD_MERGE_NORMAL` |
| `mediaSource.inbound.*` | 拉流入站（appName com.cmic.xmts，uri `/livestream/subscribe`） | **代码未使用** |
| `mediaSource.outbound.clusterName/appName` | vi-codec 所在注册集群/应用 | 占位符 |
| `mediaSource.outbound.uri` | 推流路径 | `/media/stream` |
| `mediaSource.outbound.tts` | TTS 路径 | `/media/voice/tts` |
| `mediaSource.outbound.writeTimeout` | WS 写超时 | **30ms** |
| `mediaSource.outbound.handshakeTimeout` | WS 握手超时 | 5s |
| `cache.cdn.startup` | 启动预热的 cdn 节点，非空则走 `/audio/accelerate` | 占位符 |
| `cache.cdn.clusterName/appName` | cdn 注册信息 | appName `com.cmic.cdn` |
| `cache.cdn.uri/audioData/videoData` | 预热/音频/视频路径 | `/audio/accelerate`、`/audio/get`、`/video/get` |
| `cache.cdn.audioTimeout/videoTimeout` | 拉取超时（可经 som 调整） | 2s / 2s |
| `cache.cdn.audioLB/videoLB` | 负载均衡 | ROUND_ROBIN / HASH_RING |
| `cache.circuitBreaker.*` | 视频拉取熔断 | maxRequests 20、interval 5、timeout 3、consecutiveFailures 10 |
| `cache.baseAudioPath/baseVideoPath` | 本地放音根目录 | 占位符，**代码未读取** |
| `cache.maxAudioCapacity` | 音频缓存容量（MB） | 4096 |
| `cache.maxVideoCapacity` | 视频缓存容量 | 512（**未做 MB 换算**） |
| `storage.db.mem.call` / `storage.db.disk.call` | 两级索引目录 | 路径模板 |
| `storage.db.rewrite` | 索引重写 cron | `0 0 3 * * ?` |
| `storage.data.mem.call` + lifeSpan | 一级录音目录与生命期 | `/tmp/media/call`，10 分钟 |
| `storage.data.disk.call` + lifeSpan | 二级录音目录与生命期 | `/data/media/call`，`diskLifeSpan` 模板 |
| `storage.data.cleanup` | 目录清理 cron | `0 0 4 * * ?` |
| `storage.*.ring` | ring 区域 | 未使用 |
| `monitor.cpu.enable/tick/interval/threshold` | CPU 过载保护 | false / 5 / 10 / 50 |
| `logger.level/file/path` | 日志 | info / all.log / `/var/log/vi-media` |

## 12. 坑与特殊逻辑

1. **direction 契约不一致**：`internal/dto` SessionCreate 的 direction 为 `binding:"required"`，而 vi-AS `MediaManager.java` 构造 create 参数时没有 direction → 按代码应返回 400。可能是 vi-AS 版本较旧，或 vi-media 新增了必填（待确认）。
2. **时区不一致**：`session.go:Create` 用 `time.ParseInLocation(..., time.Local)` 解析 startTime；`session_process.go:pickup` 用 `time.Parse`（UTC）解析 callTime。在东八区部署时 callTime 会比实际晚 8 小时，导致：IN_CALL 4 小时超时判定被推迟 8 小时，CALLING 事件携带的时间戳偏 8 小时（潜在 bug）。
3. **IN_CALL 超时**：注释写 3 小时，实际常量是 14400s（4 小时）。
4. **WS 队列满静默丢帧**：`mediasource` 的 `Send`/`Notice` 非阻塞，满即丢，没有指标或日志；写超时只有 30ms。网络抖动时下游质检可能缺帧且难以发现。
5. **二进制写失败后 fall-through**：outbound `process()` 中二进制帧写失败后没有 `continue`，会继续执行后面写 DATA notice 的分支（潜在 bug）。
6. **TERMINATION 也被当异常**：`Sema` 无论 EXCEPTION 还是 TERMINATION 都以 "disconnect due to exception with <msg>" 回调 → onCancel 中 err≠nil → DisableVI + Fallback。这意味着 vi-codec 正常结束也会触发离线合音回退（待确认是否故意如此）。
7. **实时推流成功则不发 MQ**：`record_audio.go:Cancel` 在 `needVI && !enableVI` 时跳过发布；若 vi-codec 中途丢数据但未报异常，离线兜底也不会发生。
8. **视频文件名从未发布**：`record_audio.go` 中 `r.video` 从未赋值，MQ 的 caller/calleeVideoFile 始终为空；volume、earlyAudioFile 也未赋值。
9. **maxVideoCapacity 无单位换算**：音频按 MB 换算，视频直接用 512 作为 otter 权重（字节），视频缓存几乎无效（潜在 bug）。
10. **测试资源用途**：`test/simulation/resources` 中 l* 为主叫、r* 为被叫；`l1/r1_pcma.wav`（A-law 8k，约 28s）、`l2/r2_nb.amr`（约 27s）、`l3/r3_wb.amr`（约 28.5–29s）。序号 1→2→3 即播放顺序 pcma → amr-nb → amr-wb，用于测试**通话中编码切换**（CODEC_SWITCH、多分段录音文件、Flush 回灌跨分段）。它们不是线上放音文件。
11. **som flush 越界**：`/som/config/flush` 接受 0–5，但 `scanSigns` 只有 2 个元素，2–5 会触发 panic（被 Recovery 捕获并返回 500）。
12. **dirPurge 只删一天**：清理命令只删除"7 天前那一天"的目录；如果某天 cron 未执行（停机），该日目录会永久残留。它与 ministore 生命期迁移/删除是两套机制。
13. **ReadEnabled 未设置**：outbound 不读 WS，`readLoop`/`onStream` 是死代码；vi-codec 的反馈全部依赖 HTTP notice。
14. **inbound、baseAudioPath/baseVideoPath、ring 配置、RTCP、`startDiffVideo`、disableVIS 均未使用**，属预留或遗留代码。
15. **TTS 寻址集群**：`internal/pkg/tts` 用 `registry.GetInstance` 在**本服务的注册集群**里查找 `outbound.appName` 节点，而推流使用 `outbound.clusterName`；两者不同时 TTS 会找不到节点（待确认）。
16. **DTMF direction 4**：注释称支持 4，绑定只允许 1/2/3。
17. **事件机制**：Stop 不走序号队列（直接 cancel），保证挂机不被乱序请求阻塞；Fallback 推空事件推进序号，这是防止"缺号导致后续事件永远等待"的兜底，**不要删除**。乱序暂存满了（5 个）以后的事件直接丢弃，不报错。
18. **端口模型**：槽位首次使用时从 PortPool 取端口且永久持有，从不 Put 回池；每会话 4 个端口位。`Get`/`GetFree` 线性扫描 5000 槽位；`Size` 无锁读取 active。
19. **并发模型**：每个会话一个 `run()` goroutine，忙循环（select ctx/event，无事件时 10ms 读超时收 UDP），5000 会话满载时有可观的空转开销；放音、视频、outbound 各有独立 goroutine，由 wg 回收；outbound 单 goroutine 串行写。
20. **AMR Flush 分块**：FrameSize 按最高速率帧计算（NB 32 / WB 61 字节），1 秒块可能把可变长度帧切断（待确认 vi-codec 是否按字节流重组）。
21. **InMemory 模式**：needVI 时录音缓冲同时保留在内存以便回灌，文件仍正常落盘。
22. **`record.New` 传参**：把 `opts.NonDivided` 作为 bEarly 参数传入，命名不一致（待确认语义）。
23. **负缓存**：cdn 返回 404 的放音名永久负缓存，直到 som reload、Remove 或 etcd watch 触发重载；新上传的同名文件需要主动重载。
24. **启动硬依赖**：未配置 `ip_local_reserved_ports` 时直接启动失败，这是有意设计，防止 RTP 端口被临时端口占用。
25. **停机耗时**：proxy.Stop 会等所有会话自然结束（不强制），stop.sh 无超时。
26. **回调不重试**：vi-AS 回调失败只记日志；SESSION_RELEASE 丢失时 vi-AS 侧可能残留会话（待确认 vi-AS 是否有自身超时）。
27. `test/mstream/main.go` 与 `test/simulation` 中有硬编码的内网地址和类号码字符串，本报告未转录。

## 13. 与其它仓库的关系

| 关系 | 仓库 | 契约 |
| --- | --- | --- |
| 上游（控制面） | vi-as-master（Java） | HTTP `/call/session/{create,update,pickup,stop}`、`/call/media/{dtmf,play,stop,...}`，Header X-Session-Id/X-Sequence-Id（create 时 seqId=1）；vi-AS 通过 `MyRegistryService.getRecordHost` 选 vi-media 节点，create 失败换节点重试；回调 vi-AS `callbackHost + "/media"` |
| 上游（媒体面） | 通信网 / xproxy | RTP 到 `ims.host:10000~30000` |
| 下游（实时） | vi-codec（不在本次源码） | WS `/media/stream` 二进制+JSON 协议（§6.1）；回调 HTTP `/call/media/inspect/notice` |
| 下游（离线） | offline-merge-master | 消费 `TID_VI_MEDIA_RECORD_MERGE_NORMAL`（offline-merge `config.prod/app.yaml` 的 mergeTopic）；以消息中 `address` 拼 `http://<address>/media/download/<fileName>` POST 回源下载（offline-merge `internal/processor/down_processor.go`、`internal/httpinterface/filehttp.go:DownFilePost`、`models:MediaMerge.GetMediaUrl`） |
| 依赖 | cdn（`com.cmic.cdn`，从 PFS 拉放音文件） | WS `/audio/accelerate`、HTTP `/audio/get`、`/video/get` |
| 依赖 | TTS 服务（outbound 应用） | HTTP `/media/voice/tts` |
| 间接 | vi-merge-master | 设计文档中 `/v1/merge` WS（X-Session-Id/X-Params/X-Notice，参数 initialSegments/segments/sampleRate/source）是 vi-merge 的接口；vi-media 直连的是 vi-codec 的 `/media/stream`，`/media/inspect` 的 config 内容最终透传给 vi-codec/vi-merge |
| 基础设施 | etcd、RocketMQ | §8、§6 |

### 文档与代码冲突

- CONFLICT: `/workspace/vi-assets/专线质检-媒体实现详细设计接口.docx` vs `internal/pkg/broker/broker.go:MediaMergeMessage` / `internal/proxy/record_audio.go:Cancel`: 文档 startTime 为毫秒，代码发送 unix 秒。
- CONFLICT: `/workspace/vi-assets/专线质检-媒体实现详细设计接口.docx` vs `internal/pkg/broker/broker.go:MediaMergeMessage`: 文档列有 pcmFormat、pcmSample 字段，代码中不存在。
- CONFLICT: `/workspace/vi-assets/专线质检-媒体实现详细设计接口.docx` vs `internal/dto`（MediaInspect）: 文档 `/call/media/inspect` 章节的示例请求体实际是 create 的请求体，与代码 MediaInspect{sessionId,config,frames,direction,seqId} 不一致。
- CONFLICT: `vi-as-master MediaManager.java`（create 参数）vs `vi-media-master internal/dto`（SessionCreate.direction required）: vi-AS 不传 direction，vi-media 要求必填。
- CONFLICT: `vi-media-master internal/proxy/session.go:Scan` 注释（3 小时）vs 常量 14400s（4 小时）。
- 备注：`二期-实时分片质检.docx`、`媒体.docx` 基本为空，没有可对照的内容。

## 14. 待确认问题清单

1. vi-AS 当前线上版本 create 是否传 direction？若不传，vi-media 如何兼容（是否线上 DTO 不同）？
2. vi-AS 中未找到调用 `/call/media/inspect` 的代码：二期实时质检由谁触发（vi-AS 新版本 / 其他服务）？
3. pickup 用 UTC 解析 callTime 是否已知问题？线上 CALLING 时间戳与 4 小时超时是否受影响？
4. vi-codec 发送 TERMINATION 时是否应触发 fallback 离线合音？
5. WS 写超时 30ms 与静默丢帧是否为有意取舍？是否需要丢帧指标？
6. 实时推流成功后不发合音 MQ：录音完整文件由谁归档到 PFS（vi-merge/vi-mdown）？
7. MQ 中视频文件名、volume、earlyAudioFile 恒为空，offline-merge 是否依赖？
8. `maxVideoCapacity` 是否本意为 MB？
9. TTS 应使用 `outbound.clusterName` 还是本集群？
10. `/call/media/inspect/notice` 也经过 Session 中间件，需要 X-Sequence-Id；vi-codec 回调时填什么值（若填 255 则立即执行）？
11. `/call/media/stream` 与 `mediaSource.inbound`（com.cmic.xmts 拉流）的实际业务场景与启用计划。
12. AMR Flush 按最大帧长切 1 秒块，vi-codec 是否能处理跨块的帧？
13. `diskLifeSpan` 线上取值，以及与 7 天目录清理的关系（哪个先生效）。
14. vi-AS 认为 G.729-only 呼叫不支持，而 vi-media 支持 g729：是否需要统一？
15. cdn 预热失败是否阻断启动？
16. offline-merge 消费组名称是否为设计文档中的 GID_VI_MERGE_RECORD（以 offline-merge 配置为准）。
