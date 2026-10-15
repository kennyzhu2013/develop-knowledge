---
id: module-offline-merge
title: offline-merge 代码深度解析（离线合音）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-offline-merge]
source_of_truth: [vi-assets/offline-merge-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/offline-merge-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/offline-merge.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# offline-merge 仓库分析报告（离线合音）

> 仓库：`/workspace/vi-assets/offline-merge-master`（Go module `offline-merge`）
> 分析方式：只读代码与配置；结论均来自代码，标注“待确认”的为推测或代码无法证实的部分。
> 安全说明：本文不包含任何密钥、连接串、主机地址；号码示例一律使用 `700xxxx0001` 形式的虚构值。

---

## 1. 定位

- README 原文仅一句：“离线合音代码”。
- **职责**：通话**结束后**，消费 vi-media 发出的“合音消息”（RocketMQ topic `TID_VI_MEDIA_RECORD_MERGE_NORMAL`），从媒体节点 HTTP 下载主叫/被叫两条腿的录音分段文件（每条腿可能是逗号分隔的多个文件，即“分段”），用 ffmpeg 合成一个完整录音文件（默认 mp3，可 wav），按需生成单腿文件/早媒体/全链路文件，然后通过 HTTP multipart 上传到 vi-mdown（`/inner/media/upload`），最后向 vi-bill 方向发送“合音完成”消息（`TID_VI_MERGE_BILL_RECORD_NORMAL`）。
- **双业务模式**（同一套代码）：
  - `voiceInspection`（语音质检，生产配置 `rocketMq.modeBiz` 即此值）：解析器 `ViParser`，上传 vi-mdown，发 `ViBillMessage`。
  - `dccMerge`（DCC/700 号码录音兼容）：解析器 `DccParser`，上传 `com.cmic.media` 的 `/media/upload`，发 `DccBillMessage` 到 `TID_DCC_MERGE_MSG_BILL_NORMAL`，支持早媒体与全链路合并。
- **与 vi-merge 的分工**（详见 §13）：vi-merge 是“通话中、WebSocket 收 PCM、实时分片合音”；offline-merge 是“通话后、MQ 触发、对已落盘文件做整通合音”。

## 2. 技术栈

| 项 | 内容 | 位置 |
| --- | --- | --- |
| 语言 | Go 1.24 | `go.mod` |
| Web | gin v1.11 + gin-contrib/pprof | `internal/service/route/router.go` |
| MQ | apache/rocketmq-clients golang v5.1.3（PushConsumer + Producer） | `internal/pkg/broker/` |
| 注册中心 | etcd client v3.5.21（自注册 + 服务发现） | `pkg/registry/`、`internal/pkg/registry/file_node.go` |
| 配置 | viper + cobra，读取 `config/app.yaml`（仓库只有 `config.prod/app.yaml`，值为 `{{}}` 占位符，部署时渲染） | `conf/config.go` |
| JSON | bytedance/sonic + encoding/json | `internal/processor/parser_processer.go` |
| 监控 | prometheus client_golang，namespace `offlinemerge` | `internal/metrics/` |
| 音频处理 | 外部 ffmpeg 进程（`exec.CommandContext` 带超时） | `internal/pkg/ff/runsh.go` |
| 音频格式识别 | 自研文件头探测、AMR 帧解析 | `internal/audioformat/detector.go`、`internal/pkg/parser/amr_parser.go` |
| 日志 | 自研 logger（`pkg/logger`），目录配置项 `logPath` | `internal/pkg/log/log.go` |

ffmpeg 来源：`bin/ffmpeg` 被 `.gitignore` 忽略（提交 `fe39312` 不再跟踪约 74MB 的二进制），运行时优先使用工作目录下的 `./ffmpeg`，没有时回退到配置项 `ffmpeg.staticPath`；代码内默认常量是 `/home/production/ffmpeg-4.1.3-amd64-static/ffmpeg`（`internal/pkg/ff/runsh.go`）。本次分析未读取任何 ffmpeg 二进制。

## 3. 代码结构

| 路径 | 作用 |
| --- | --- |
| `cmd/offlinemerge/main.go` | 入口，cobra 命令，调用 `app.ServerInit`，`watchSignal` 优雅退出 |
| `conf/config.go` | 配置结构体与加载（server / rocketMq / outbound / ffmpeg / etcd / log） |
| `internal/app/app.go` | 启动编排：配置 → 日志 → producer → consumer → ETCD 注册 → metrics → 路由 → ffmpeg 检查；停止流程 |
| `internal/pkg/broker/receive/mq_consumer.go` | PushConsumer（实际使用）与 SimpleConsumer（未使用）实现，`handleMessage` 入口 |
| `internal/pkg/broker/pub/` | `mq_producer.go`（按 mode 选择 producer）、`vi_producer.go`（ViBillMessage）、`dcc_producer.go`（DccBillMessage） |
| `internal/processor/file_merge_processer.go` | **主流程** `FfMerge.MergeSh` |
| `internal/processor/parser_processer.go` | 消息解析、分段配对、目录与文件命名 |
| `internal/processor/bparser/` | 业务差异抽象 `BizParser`：`ViParser` / `DccParser` |
| `internal/processor/down_processor.go` | 并发下载分段文件、识别编码、计算时长 |
| `internal/processor/call_processor.go` | 通话段合音（双腿/单腿/空音频/多段 concat/分离腿） |
| `internal/processor/fulllink_processor.go` | 早媒体合音及“早媒体+通话”全链路拼接（仅 DCC） |
| `internal/processor/handler/` | `Mp3Transcoder` / `WavTranscoder` 两种输出转码器，空音频、concat 工具 |
| `internal/pkg/ff/` | ffmpeg 命令拼装（`audio_mix.go`）、执行与超时（`runsh.go`）、时长探测（`timesh.go`） |
| `internal/processor/upload_processor.go` | 上传结果文件、节点切换、失败落本地盘 |
| `internal/processor/clean_processer.go` | 清理本地临时文件 |
| `internal/processor/pubmsg/mq_pub.go` | 组装并发送合音完成消息 |
| `internal/httpinterface/filehttp.go` | 下载 / 上传 HTTP 客户端 |
| `internal/audioformat/` | 格式探测、内置空音频字节（MP3/S16LE 8k/16k） |
| `internal/models/` | 消息结构、上下文、错误、参数 |
| `internal/service/route/` | 业务端口 `/v1/test`，内部端口 ops/metrics/health/pprof |
| `pkg/registry/` | ETCD 注册与发现通用库 |
| `bin/start.sh`、`bin/stop.sh`、`build.sh` | 启停与构建脚本（产物 `bin/offline-merge`） |

## 4. 核心业务流程

入口：`internal/pkg/broker/receive/mq_consumer.go:MqPushConsumer.Start` 的回调 → `handleMessage` → `internal/processor/file_merge_processer.go:FfMerge.MergeSh`。

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| 1 收消息 | PushConsumer 订阅 `mergeTopic`（SUB_ALL，tag 不过滤），取 keys 作为 sessionId 日志标识，计数 `mq_receive_count` | `receive/mq_consumer.go:MqPushConsumer.Start`、`getKey` | 回调返回 nil→SUCCESS（ack），否则 FAILURE→由 RocketMQ 重投（次数受服务端/消费组策略控制，待确认） |
| 2 解析消息 | `ParserMergeJson`：反序列化 `MediaMerge`；`config` 字符串再解析为 `Config{source,userData}`；`options` 先去掉反斜杠再解析 `MergeOptions` | `processor/parser_processer.go:ParserMergeJson` | 主体 JSON 失败→返回 `MqMessageInvalid`，`handleMessage` 记 `mq_message_invalid_count` 后**直接 ack 丢弃**；options 失败→使用默认值（recordSuffix=mp3、isRecord=true 等） |
| 3 选模式 | `options.mode` 为空时用配置 `modeBiz`；`fallback=true` 记 `merge_fallback_count`；`GetBizParser`：`dccMerge`→`DccParser`，其它→`ViParser` | `file_merge_processer.go:MergeSh`、`GetBizParser` | — |
| 4 构造上下文 | `MergeCtx`：生成配对列表（`ParseCallAudioPair`）、早媒体（`ParseEarlyAudioPath`，仅 `FullMerge()=true` 即 DCC）、各输出项及本地目录 | `file_merge_processer.go:MergeCtx`、`parser_processer.go` | startTime 解析失败→结果名时间段为空，记 `merge_starttime_wrong_count` |
| 5 下载分段 | 每个分段文件一个 goroutine 并发 `POST http://<address>/media/download/<文件名>`，写入本地，探测编码与时长 | `processor/down_processor.go:BatchFileDown.FileDown`、`singleDown` | 失败只记 `merge_down_fail_count` 与日志，不重试；该腿长度为 0，后续视为“损坏腿” |
| 6 通话合音 | 按两腿健康状况选择：都坏→写空音频；一腿坏→单腿/补空腿；都好→短的一侧 apad 补齐后 amerge 成立体声（ac=1 时 amix 成单声道）；多段配对→concat | `processor/call_processor.go:CallMerge.multiLegMerge`、`multiCallResult` | ffmpeg 超时按文件大小 5s~160s；执行失败用 PATH 中的 `ffmpeg` 再试一次；concat 失败回退为第一段结果 |
| 7 分离腿（可选） | `separateLeg=true` 时额外输出主叫/被叫单声道文件 | `call_processor.go`、`handler/audiohandler.go:OneLegOutPath` | 同上 |
| 8 早媒体/全链路（DCC） | 早媒体单独合成 `Early_Record_...`，再与通话段 concat 成全链路文件 | `processor/fulllink_processor.go:FullLinkMerge.EarlyCallMerge`、`fullConcat` | Vi 模式跳过 |
| 9 上传 | `FileStore.SaveFile`：收集全链路/通话/主叫/被叫结果项，多个时并发上传 | `processor/upload_processor.go:FileStore.SaveFile`、`oneFileSave`、`selectNodeFileUpload`、`nodeFileUpload` | 连接失败或 5xx（`NodeAccessError`）→换一个节点重试 **1 次**；最终失败且配置 `uploadFailSaveLocal=true`→拷贝到 `/data/offlocal/<文件名>`，否则累计失败数；**错误不向上返回** |
| 10 清理 | 删除下载的分段文件与结果文件 | `processor/clean_processer.go:cleanUp` | 删除失败只记日志；目录与部分中间文件不删除（见 §12） |
| 11 发消息 | 按 mode 发 `ViBillMessage` 或 `DccBillMessage`，keys=sessionId，3s 超时 | `processor/pubmsg/mq_pub.go:PubMessageMQ`、`pub/vi_producer.go:PubBillMessage` | **唯一会导致消费失败的环节**：发送失败→返回 error→消息 FAILURE 重投，整条流程（下载/合音/上传）重做 |

ffmpeg 调用细节（`internal/pkg/ff/audio_mix.go`、`runsh.go`）：

- 双声道合并：`-filter_complex "[0][1]amerge"`；时长不等时 `[0]apad[a];[a][1]amerge` 或 `[1:a]apad[a1];[0:a][a1]amerge`。
- 单声道混音：`amix=inputs=2:duration=longest`。
- 输出参数：`-acodec libmp3lame|pcm_s16le -ar 8000 -ab <码率> -ac <1|2> -f mp3|wav`。mp3 码率 16000×ac，wav 128000×ac（`handler/audiohandler.go:audioAb`）。**输出采样率固定 8000**（`DefaultSample`），与输入是否 16k 无关。
- 输入解码：按探测出的编码追加 `-f`/`-acodec` 参数（AMR-WB/NB、pcma、pcmu、s16le、g729、pcm），见 `runsh.go:getDecoderCodec`、`getPcmConfig`。
- 声道方向：默认左=主叫、右=被叫；`reverseLeg=true` 时交换。
- 多段拼接：concat demuxer `-f concat -safe 0 -i <list> -c copy`，列表文件 `/data/offline/fileList_<sid>_<毫秒>.txt`（`handler/audiohandler.go:ConcatAudios`、`ff/runsh.go:ConcatAudio`），超时 = 文件大小 × 2s 量级。
- 成功判定：输出文件存在且大于 100 字节（`runsh.go:shSuc`）。
- 时长探测：AMR 用帧解析；其它按“大小/码率”估算；兜底执行 `ffmpeg -i <file> -f null -` 解析输出中的 `time=`，超时 5s（`ff/timesh.go:GetTimeSh`、`DurationCal`）。

## 5. 对外接口（入站）

服务两个端口：业务端口 9036（配置项 `server.port`），内部端口 = 业务端口 + 1（9037）。所有接口**无鉴权**。

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | GET/POST `/v1/test` | `service/route/bizhandler/test_handler.go:HttpHandler.TestHandler` | 无（使用代码内硬编码的样例消息） | 合音结果 | **危险**：会真实执行下载、合音、上传并发送账单 MQ；样例中含疑似真实号码，本文不引用 |
| HTTP | GET `/metrics` | `router.go:Init`（内部端口） | — | Prometheus 指标 | |
| HTTP | GET `/health` | 同上 | — | 健康状态 | |
| HTTP | GET `/testmetrics` | 同上 | — | — | 会写入测试指标值，污染监控 |
| HTTP | GET/POST `/ops/config/:config` | `service/route/ops/handlers.go:OMHandler.Config` | `logLevel`、`fileAppName`（长度>10 才生效）、`uploadFailSaveLocal` | 生效结果 | 运行时修改配置，不持久化 |
| HTTP | GET `/ops/configManifest` | `ops/handlers.go:ConfigManifest` | — | 可修改项清单 | |
| HTTP | GET `/ops/omu/status|pause|resume|stop` | `ops/handlers.go:OMUServiceStatus` | — | 状态 | `stop` 会从 ETCD 注销 |
| HTTP | GET `/ops/mq/stop|start` | `ops/handlers.go:MqOpsStatus` | — | 状态 | 停止/恢复消费 |
| HTTP | `/debug/pprof/*` | gin-contrib/pprof | — | — | 内部端口 |

没有 WebSocket 接口（区别于 vi-merge）。

## 6. 消息

| 方向 | 中间件 | topic / tag | group | 关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 入（消费） | RocketMQ v5，namespace `VI` | `TID_VI_MEDIA_RECORD_MERGE_NORMAL`（配置项 `rocketMq.mergeTopic`），tag 全部 | `GID_VI_MERGE_RECORD`（`rocketMq.mergeGroup`） | 见下方 `MediaMerge` | `receive/mq_consumer.go:MqPushConsumer.Start`、`handleMessage` |
| 出（Vi 模式） | RocketMQ | `TID_VI_MERGE_BILL_RECORD_NORMAL`（`rocketMq.viTopic`），tag `*`，keys=sessionId | — | 见下方 `ViBillMessage` | `pub/vi_producer.go:ViProducer.PubBillMessage` |
| 出（DCC 模式） | RocketMQ | `TID_DCC_MERGE_MSG_BILL_NORMAL`（`rocketMq.dccTopic`） | — | 见下方 `DccBillMessage` | `pub/dcc_producer.go:DccProducer.PubBillMessage` |

消费参数：线程数配置项 `consumptionThreadCount`（生产 20，缺省 10），`MaxCacheMessageCount=1024`，等待时长 5s；producer 的 `maxAttempts=3`（`rocketMq.maxAttempts`）。

**入站消息 `MediaMerge`**（`internal/models/merge_message.go`）：

| 字段 | 含义 |
| --- | --- |
| `sessionId` | 会话 ID，用于文件名、日志 |
| `callerNum` / `calleeNum` | 主被叫号码（例：`700xxxx0001`） |
| `userId`、`msId`、`version` | 用户 / 媒体服务标识 |
| `address` | 下载分段文件的媒体节点地址（host:port）；DCC 模式也作为上传节点 |
| `path`、`volume` | 存储路径与卷，透传给上传接口 |
| `callerAudioFile` / `calleeAudioFile` | 主/被叫腿文件名，**逗号分隔的多个分段** |
| `callerVideoFile` / `calleeVideoFile` | 视频文件（未见处理逻辑） |
| `earlyAudioFile` | 早媒体文件（DCC） |
| `startTime` | 通话开始时间，int64，10 位秒或 13 位毫秒 |
| `options` | JSON 字符串 → `MergeOptions{recordSuffix, separateLeg, reverseLeg, separateEarly, isRecord, bindId, mode, userData}` |
| `config` | JSON 字符串 → `Config{source, userData}`；`source` 1=只录主叫 2=只录被叫 3=双向 |
| `fallback` | vi-media 侧 VI 实时流失败降级标志 |
| `pcmFormat` / `pcmSample` | 当分段是裸 PCM 时的格式和采样率 |
| rtp 统计字段 | 透传给 DCC 消息 |

**出站 `ViBillMessage`**（`pub/vi_producer.go`）：`sessionId`、`segmentId`（固定 0）、`callerNum`、`calleeNum`、`userId`、`fileNode`（实际上传节点 host:port）、`endFlag`（固定 true）、`isStream`（固定 false）、`duration`（通话结果时长，float32 秒）、`path`、`volume`（上传响应返回的卷）、`fileName`（通话结果文件名）、`suffix`、`userData`（优先 `config.userData`，否则 `options.userData`）、`fallback`。**没有“上传是否成功”的字段。**

**出站 `DccBillMessage`**（`pub/dcc_producer.go`）：`code`（固定 0）、`callId`（**从未赋值**）、`fileName`、`leftFile`/`rightFile`、各文件长度、`userId`、`serverId`（=消息 `address`）、`fileDuration`/`fullDuration`/`earlyDuration`（向上取整）、`acodec`（输入编码）、`separateEarly`、`fullRecord`、`bindId`（JSON tag 写成了 `bindIdm`）、rtp 计数。

## 7. 依赖调用（出站）

| 目标 | 协议 / 接口 | 发现方式 | 代码位置 | 说明 |
| --- | --- | --- | --- | --- |
| 媒体节点（消息中的 `address`，通常是 vi-media / 录音缓存） | HTTP POST `/media/download/<url 转义的文件名>`，body `{"fileName":...}`，header `session` | 消息字段 `address` 直连 | `httpinterface/filehttp.go:FileServer.DownFilePost`、`down_processor.go:singleDown` | 不重试 |
| vi-mdown（Vi 模式） | HTTP POST multipart，`/inner/media/upload/<文件名>?path=<path>&volume=<volume>`，表单字段 `media`，header `Volume`、`session` | ETCD：cluster `vii`、app `com.cmic.vi.mdownload`（配置项 `outbound.fileCluster`/`fileAppName`/`fileUri`），轮询取 InnerHost:InnerPort | `bparser/vi_parser.go:UploadUri`、`registry/file_node.go:UploadNextNode`、`filehttp.go:UploadFile` | 参数**不做 URL 转义**；**不带 `zone` 参数**（vi-merge 带 ZoneC/ZoneM） |
| com.cmic.media（DCC 模式） | HTTP POST `/media/upload/<文件名>?path=&volume=`（转义），body 为 octet-stream | 消息 `address` 长度>10 时直连，否则 ETCD cluster `dcc`、app `com.cmic.media`（`mediaCluster`/`mediaAppName`/`mediaUri`） | `bparser/dcc_parser.go`、`registry/file_node.go:MediaNextNode`、`filehttp.go:Upload` | 注释为“700 兼容” |
| HTTP 超时 | 配置项 `outbound.timeout`（10，单位按代码为秒），大文件按长度放宽 | — | `filehttp.go:GetHttpClient` | |
| RocketMQ | 见 §6 | 配置项 endpoint / AK / SK（占位符） | `pub/mq_producer.go:clientProducer` | |
| ETCD | 自注册 `/vii/micro-registry/com.cmic.vi.offline/<host>:<port>`，TTL 30s，keepalive 15s，metadata `offline merge service` | 配置项 etcd 段（占位符） | `pkg/registry/etcd.go` | |
| ffmpeg | 本地进程 | `./ffmpeg` → `ffmpeg.staticPath` → PATH | `ff/runsh.go` | |

## 8. 数据

- **DB / Redis / Mongo**：无。
- **ETCD**：只用于服务注册和发现（§7）。
- **本地文件**（全部为临时文件，无持久化）：

| 路径 | 用途 | 来源 |
| --- | --- | --- |
| `/tmp/mergeCall_<j>/` | 第 j 对分段的下载文件和通话结果（**按序号建目录，不按会话**，多会话共用，靠文件名中的 sid 区分） | `parser_processer.go:ParseCallAudioPair`、`createTmpDir` |
| `/tmp/mergeCall/` | 多段 concat 后的最终通话结果 | `parser_processer.go:callLocalResultItem` |
| `/tmp/mergeSingle/` | 分离腿单声道结果 | 同上 |
| `/tmp/mergeEarly/`、`/tmp/mergeFull/` | 早媒体、全链路（DCC） | `parser_processer.go:earlyLocalResultItem` |
| `/data/offline/fileList_<sid>_<毫秒>.txt` | concat 列表文件 | `handler/audiohandler.go:ConcatAudios` |
| `/data/<path>/` | `MergeCtx` 中创建的 `LocalStoreDir`，**未见使用** | `file_merge_processer.go:MergeCtx` |
| `/data/offlocal/<文件名>` | 上传失败时的本地兜底存放 | `upload_processor.go:saveDisk` |
| `/var/log/offline-merge` | 日志（配置项 `logPath`） | `config.prod/app.yaml` |

- **文件命名**：

| 文件 | 格式 | 位置 |
| --- | --- | --- |
| 通话结果（Vi / DCC 相同） | `<sid>_<yyyyMMddHHmmss>.<suffix>`，时间是 startTime 转成东八区；14 位原样保留；其它长度为空串 | `bparser/vi_parser.go:CallOutName`、`dcc_parser.go:CallOutName`、`parser_processer.go:getStartTime` |
| 早媒体结果 | `Early_Record_<sid>_<time>.<suffix>` | `parser_processer.go:getEarlyResultName` |
| 主叫单腿 / 被叫单腿 | `Left_Record_<sid>_<suffix>.<suffix>` / `Right_Record_<sid>_<suffix>.<suffix>`（后缀重复出现） | `getCallerOutName`、`getCalleeOutName` |
| 补空腿 | `<sid>_empty_8k.mp3` | `handler/audiohandler.go:WriteEmpty8KMonoFile` |
| 早媒体中间文件 | `Early_%v.amr` | `fulllink_processor.go` |
| 分段输入 | 消息给的原名，`chooseLegList` 只保留长度 > 18 的名字 | `parser_processer.go:chooseLegList` |

## 9. 状态与枚举

| 枚举 | 取值 | 位置 |
| --- | --- | --- |
| 业务模式 | `voiceInspection`（BizVi）、`dccMerge`（BizMerge） | `internal/models/` |
| `config.source` | 1 只录主叫、2 只录被叫、其它（3）双向 | `parser_processer.go:ParseCallAudioPair` |
| 输出声道 `AudioChannels` | Vi：source 1/2→1，否则 2；DCC：固定 2 | `bparser/*_parser.go:AudioChannels` |
| 输入编码 InCode | AMR-WB、AMR-NB、pcma=4、pcmu=5、s16=6、g729=7、PCM S16LE/F32LE 等 | `audioformat/formet_const.go`、`detector.go` |
| 业务错误 | `MqMessageInvalid`、`NodeEmptyErr`、`NodeAccessError`、`NodeUploadErr`、`FileSaveLocal` | `models/global_errors.go` |
| HTTP 返回码 | 0、-1、-2、-3、-11、-14、-15、-16 | `httpinterface/codemap.go` |
| 损坏腿判定 | 下载长度 ≤ 100 字节 | `call_processor.go:multiLegMerge` |

## 10. 定时任务

无业务定时任务。只有 ETCD 租约续期（15s keepalive，`pkg/registry/etcd.go`）。没有扫描或补传 `/data/offlocal/` 的任务，落盘文件需要人工处理（待确认是否由其它系统处理）。

## 11. 关键配置项（`config.prod/app.yaml`，敏感值为占位符，本文不展开）

| 配置项 | 生产值 / 说明 |
| --- | --- |
| `server.port` | 9036（内部端口 +1 = 9037） |
| `rocketMq.namespace` | `VI` |
| `rocketMq.endpoint` / `accessKey` / `secretKey` | 占位符，敏感，不写值 |
| `rocketMq.maxAttempts` | 3 |
| `rocketMq.mergeTopic` / `mergeGroup` | `TID_VI_MEDIA_RECORD_MERGE_NORMAL` / `GID_VI_MERGE_RECORD` |
| `rocketMq.viTopic` | `TID_VI_MERGE_BILL_RECORD_NORMAL` |
| `rocketMq.dccTopic` | `TID_DCC_MERGE_MSG_BILL_NORMAL` |
| `rocketMq.consumptionThreadCount` | 20 |
| `rocketMq.modeBiz` | `voiceInspection`（只初始化该模式的 producer） |
| `outbound.fileCluster` / `fileAppName` / `fileUri` | `vii` / `com.cmic.vi.mdownload` / `/inner/media/upload` |
| `outbound.mediaCluster` / `mediaAppName` / `mediaUri` | `dcc` / `com.cmic.media` / `/media/upload` |
| `outbound.timeout` | 10 |
| `outbound.uploadFailSaveLocal` | true |
| `ffmpeg.staticPath` | ffmpeg 回退路径 |
| etcd 地址等 | 占位符，不写值 |
| `logPath` | `/var/log/offline-merge` |

## 12. 坑与特殊逻辑

1. **`source=1`（只录主叫）时不产出任何配对**：`ParseCallAudioPair` 里 `maxSize := len(calleePairLegs)`，判断条件也写成了 `len(calleePairLegs) > maxSize`（永远为假），所以配对数只看被叫腿。只录主叫时被叫列表为空，循环 0 次，通话结果缺失；双向录音时如果主叫分段多于被叫，多出的主叫分段会被**静默丢弃**。明显是 caller/callee 写反了。（`parser_processer.go:ParseCallAudioPair`）
2. **上传失败也会 ack 并发送“合音完成”消息**：`SaveFile` 不向上返回错误，`ViBillMessage` 里也没有上传结果字段。下游会拿到 `fileName`/`fileNode`，但文件可能不在 vi-mdown 上（只在本机 `/data/offlocal/`，或者根本不存在）。
3. **只有 MQ 发送失败会触发重投**，而重投会把下载、ffmpeg、上传全部重做一遍，可能造成重复上传。
4. **PCM 格式大小写不一致**：`GetPcmAttr` 缺省返回小写 `s16le`，而探测逻辑按大写 `S16LE`/`F32LE` 比较，结果是 UNKNOWN，ffmpeg 解码参数可能错误。（`models/merge_message.go:GetPcmAttr`、`audioformat/detector.go`）
5. **`detectFormatBytes` 直接切 `src[0:21]`**，文件小于 21 字节会 panic，靠 recover 兜住。
6. **`getKey` 要求 `len(keys) > 1`**，只有一个 key 时取不到 sessionId（只影响日志）。（`receive/mq_consumer.go:getKey`）
7. **`-timelimit` 单位**：代码按毫秒算出的值直接传给 ffmpeg `-timelimit`，但 ffmpeg 的这个参数单位是秒，相当于没有限制，实际只靠 `exec.CommandContext` 的超时兜底。（`ff/runsh.go:getWaitTimes`）
8. **输出固定 8k 采样**，16k 宽带输入也会降采样（mp3 码率 16kbps/声道）。
9. **清理不彻底**：只删除下载的分段和最终结果；`/tmp/mergeCall_<j>`、`/data/<path>` 等目录、多段 concat 的中间结果、`/data/offline/fileList_*.txt`、补空腿文件都没有显式删除（列表文件是否删除待确认），长期运行会占满磁盘。
10. **并发上传存在数据竞争**：多个结果并行上传时同时写 `FileStore.fileNode`、`volume`、`uploadFailCnt`，没有加锁；消息中的 `fileNode`/`volume` 可能来自另一个文件的上传。
11. **Vi 上传 URL 不做转义**，`path`/`volume` 带特殊字符时会出错；DCC 分支会转义，两边不一致。
12. **DCC 消息 `callId` 从未赋值、`code` 固定 0，`bindId` 的 JSON tag 是 `bindIdm`**。而 vi-billcenter 的 DCC 格式消费方会读取 `code`/`callId`（见 §13）。
13. **只初始化 `modeBiz` 对应的 producer**（初始化错误被忽略）：如果消息 `options.mode=dccMerge`，而服务 `modeBiz=voiceInspection`，DCC producer 没有初始化，发送时会失败或 panic（待确认具体表现）。
14. **停机顺序**：先 `StopProducer` 再 `MqGracefulStop`，停机期间仍在处理的消息会发送失败，进而被重投。
15. **`/v1/test` 会对生产环境产生真实影响**（上传、发账单消息），而且没有鉴权；`/testmetrics` 会污染监控；ops 接口可以停掉消费或从 ETCD 注销，同样没有鉴权。
16. **日志中有明文号码**：`DccParser.CallOutName` 等日志会打印主被叫号码（顺带一个 bug：被叫位置打印的也是主叫号码）。
17. 早媒体/全链路只在 DCC 模式启用（`ViParser.FullMerge()=false`）。
18. 下载与 concat 使用的 `/tmp/mergeCall_<j>` 是共享目录，同一 sid 的消息被重投时可能和正在进行的处理发生文件冲突（待确认）。
19. `bin/start.sh` 不做 `chmod`，ffmpeg 的可执行权限依赖 `ffmpegInitCheck`（检查 0755/0777）。`main` 没有忽略 SIGPIPE（vi-merge 有）。
20. SimpleConsumer 的实现保留在代码里但没有使用。

## 13. 与其它仓库的关系

```text
vi-media ──MQ TID_VI_MEDIA_RECORD_MERGE_NORMAL──▶ offline-merge
   ▲  (通话结束 audioRecord.Cancel 发出 MediaMergeMessage)      │
   │                                                            │ HTTP 下载 /media/download
   └────────────────────────────────────────────────────────────┘
offline-merge ──HTTP /inner/media/upload──▶ vi-mdown (ETCD vii/com.cmic.vi.mdownload)
offline-merge ──MQ TID_VI_MERGE_BILL_RECORD_NORMAL──▶ vi-bill / vi-billcenter（消费方待确认）
offline-merge ──(dccMerge) HTTP /media/upload──▶ com.cmic.media；MQ TID_DCC_MERGE_MSG_BILL_NORMAL
```

- **上游 vi-media**：`vi-media-master/internal/proxy/record_audio.go:audioRecord.Cancel` 在通话结束时发送合音消息，`options`=合音配置，`config`=VI 配置，`fallback`=会话降级标志（外呼 VI 流失败、`DisableVI` 后为 true，见 `stream_outbound.go`）。
- **下游 vi-billcenter**：`vi-billcenter-master` 的 RecordReceiver 订阅的是 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`（group `GID_VI_ONE_BILLCENTER_RECORD`），按 DCC 格式解析 `code`/`callId`；本仓库发送的 `TID_VI_MERGE_BILL_RECORD_NORMAL` 的消费方没有在已有仓库中找到（待确认）。
- **与 vi-merge 的分工和差异**：

| 维度 | vi-merge | offline-merge |
| --- | --- | --- |
| 时机 | 通话中实时 | 通话结束后 |
| 触发 | vi-codec 通过 WebSocket `GET /v1/merge` 推送 PCM | MQ 合音消息 |
| 输入 | PCM 帧（首字节表示声源） | 媒体节点上已落盘的分段文件（AMR/WAV/G729/PCM），HTTP 下载 |
| “分段”含义 | 按 mergeTrigger 时长切出的实时片段 `Segment_<sid>_<segId>.wav` | 一条腿由多个文件组成（逗号分隔），合并成一个文件 |
| 编码转换 | 自己写 WAV 头、单声道交织成立体声；mp3 转换走 ffmpeg | 全部走 ffmpeg（解码、apad/amerge/amix、concat） |
| 输出 | 分段 WAV + 整通 WAV/MP3 | 整通 mp3/wav（8k），可选单腿、早媒体、全链路 |
| 上传 | `/inner/media/upload`，带 `zone=ZoneC/ZoneM` | `/inner/media/upload`，不带 zone；DCC 模式走 `/media/upload` |
| 消息 | `TID_VI_MERGE_MSG_BILL_NORMAL`，`isStream=true`，多条 segmentId，最后 `endFlag` | `TID_VI_MERGE_BILL_RECORD_NORMAL`，单条 `segmentId=0`、`endFlag=true`、`isStream=false` |
| 业务模式 | 只有质检 | 质检 + DCC/700 兼容 |
| 端口 / ETCD 名 | 不同（见 vi-merge 报告） | 9036/9037，`com.cmic.vi.offline` |

两边的整通结果文件名格式都是 `<sid>_<yyyyMMddHHmmss>.<suffix>`。如果两条链路都对同一会话生效，`path` 不同时不会冲突，但是否会互相覆盖需要确认（待确认）。

- 设计文档 `/tmp/vi/docs/专线质检-媒体实现详细设计接口.txt` 里合音→账单的 topic 也是 `TID_VI_MERGE_BILL_RECORD_NORMAL`，与代码一致；文档中 `startTime` 写的是毫秒时间戳，代码同时兼容 10 位和 13 位；文档中 `pcmFormat` 取值为 `pcm_s16le` 等形式，和代码比较的 `S16LE` 不一致。CONFLICT：`/tmp/vi/docs/专线质检-媒体实现详细设计接口.txt` vs `internal/audioformat/detector.go`：pcmFormat 取值的大小写和前缀不同。

## 14. 待确认问题清单

1. `source=1` 不产出通话结果，是线上真实存在的问题，还是线上不会出现 source=1？（§12-1）
2. `TID_VI_MERGE_BILL_RECORD_NORMAL` 由哪个服务消费？下游如何区分上传成功和失败？
3. 上传失败后落盘到 `/data/offlocal/` 的文件由谁补传、多久清理一次？
4. RocketMQ 消费组的最大重投次数和死信队列配置是什么？重投导致的重复上传和重复账单消息是否在下游去重？
5. 生产环境是否有消息走 `dccMerge` 模式？如果有，DCC producer 没有初始化的问题怎么处理？`callId` 为空、`bindIdm` 拼写错误对下游有没有影响？
6. vi-media 实际传的 `pcmFormat` 取值是什么？是否会触发大小写不匹配问题？
7. 16k 输入固定降到 8k 输出，是不是有意的设计（质检 ASR 是否需要 16k）？
8. vi-merge 和 offline-merge 是否会对同一会话都执行合音？两者的结果文件会不会在 vi-mdown 中重名或覆盖？上传不带 zone 时，vi-mdown 会用哪个默认 zone？
9. `/tmp/mergeCall_<j>`、`/data/offline` 等临时目录和中间文件，是否有外部的清理脚本或 crontab？
10. 生产环境是否开放了 `/v1/test`、`/testmetrics` 和 ops 接口？有没有网络层的访问控制？
11. `callerVideoFile`/`calleeVideoFile` 字段是否已经废弃？
