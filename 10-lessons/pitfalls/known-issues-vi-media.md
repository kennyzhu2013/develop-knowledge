---
id: lesson-known-issues-vi-media
title: vi-media 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-media
  - module-vi-media
  - lesson-known-issues-vi-as
  - lesson-known-issues-offline-merge
  - lesson-doc-code-conflicts
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-media-master/internal/dto/session_create.go
  - vi-assets/vi-media-master/internal/proxy/session.go
  - vi-assets/vi-media-master/internal/proxy/record_audio.go
  - vi-assets/vi-media-master/internal/proxy/stream_outbound.go
---

# vi-media 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-media-master`，2026-10-15。与 vi-as 的 create 字段差异见 `10-lessons/pitfalls/doc-code-conflicts.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | `SessionCreate.direction` 为必填，只允许 1/2/3。vi-as 的 create 参数没有这个字段。按当前两边源码应返回 400。线上是否旧版兼容，待确认。 | `vi-assets/vi-media-master/internal/dto/session_create.go`、`vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` | 校验若严格生效，媒体会话建不起来，后续录音和实时质检都不会开始。 | direction 决定推流声道。删掉 `binding:"required"` 会让缺省值 0 进入会话，实时质检方向变成未定义。应先确认线上 DTO，再改契约。 |
| 2 | create 用本地时区解析 `startTime`。pickup 用 `time.Parse`（UTC）解析 `callTime`。东八区部署时 callTime 会晚 8 小时。 | `vi-assets/vi-media-master/internal/proxy/session.go`、`vi-assets/vi-media-master/internal/proxy/session_process.go` | IN_CALL 超时判断被推迟；CALLING 事件时间戳偏移。是否已在线上观察到，待确认。 | 两处解析都是会话时钟。删 pickup 的时间解析会让接通态没有 callTime，超时扫描更不可用。应改成同一时区。 |
| 3 | IN_CALL 超时注释和日志写“超过 3 小时”，常量是 14400 秒（4 小时）。 | `vi-assets/vi-media-master/internal/proxy/session.go` | 按注释排障会提前一小时怀疑超时未生效。 | 14400 是强制拆会话的保护。删判断会让异常长会话一直占端口和 goroutine。改数字前要和质检侧超时对齐。 |
| 4 | 推流 `Send` / `Notice` 非阻塞，队列满就丢，没有指标。写超时来自配置 `outbound.writeTimeout`（prod 配置为 30，单位毫秒）。 | `vi-assets/vi-media-master/internal/pkg/mediasource/conn_outbound.go`、`vi-assets/vi-media-master/config.prod/app.toml` | 网络抖动时下游缺帧，且没有丢帧计数。是否有意取舍，待确认。 | 非阻塞是为了不让 RTP 读循环被下游堵住。改成阻塞写会把 vi-codec 变慢放大成媒体线程堆积。要加指标，而不是删发送。 |
| 5 | outbound `process` 在二进制帧写失败后没有 `continue`，会落到后面的 DATA notice 分支。 | `vi-assets/vi-media-master/internal/pkg/mediasource/conn_outbound.go` | 写失败后可能再发一帧语义不对的 notice。 | 这个循环是推流主循环。删掉二进制发送会让实时质检没有音频。应补 `continue`，保留循环。 |
| 6 | 无论 EXCEPTION 还是 TERMINATION，回调文案都带 exception。`onCancel` 看到 err 就 `DisableVI` 并 Fallback。正常结束也可能触发离线合音。是否故意，待确认。 | `vi-assets/vi-media-master/internal/proxy/stream_outbound.go` | 实时链路正常结束时仍可能再走一遍离线合音，下游收到两份录音结果。 | Fallback 是实时失败后的离线兜底。整段删掉后，vi-codec 真异常时不会再发离线合音消息，录音只剩实时半成品。应区分结束原因，保留异常回退。 |
| 7 | `needVI && !enableVI` 时 `Cancel` 跳过合音 MQ。实时推流一旦被标记成功，即使中途丢数据且没报异常，也不会发离线兜底。 | `vi-assets/vi-media-master/internal/proxy/record_audio.go` | 实时“成功”但缺帧时，offline-merge 不会收到这段会话。完整文件由谁归档到 PFS，待确认。 | 跳过 MQ 是为了避免实时、离线各出一份整通录音。删条件会让每通实时质检都再触发离线合音。 |
| 8 | `video`、`volume`、`earlyAudioFile` 没有赋进合音消息，视频文件名和 early 文件在 MQ 里为空。 | `vi-assets/vi-media-master/internal/proxy/record_audio.go` | offline-merge 若依赖这些字段，视频和早媒体合音不会发生。是否依赖，待确认。 | 消息结构是和 offline-merge 的契约。删字段会导致对端反序列化缺键；应补赋值或明确废弃，而不是只删结构体字段。 |
| 9 | 音频缓存按 MB 换算，视频容量直接用配置值 512 当权重（字节量级），视频缓存几乎存不下。配置项名是 `maxVideoCapacity`。 | `vi-assets/vi-media-master/config.prod/app.toml` | 视频缓存命中率极低，等于每次读盘。是否本意是 MB，待确认。 | 删视频缓存初始化会让视频路径直接失败，而不是“反正没用”。改单位前要确认内存预算。 |
| 10 | `test/simulation/resources` 里 l* 为主叫、r* 为被叫，序号对应 pcma、amr-nb、amr-wb，用于模拟通话中编码切换。不是线上放音文件。 | `vi-assets/vi-media-master/test/simulation/resources/` | 误当成放音资源部署会把测试音放进真实呼叫。 | 这是编码切换回归素材。删资源会让仿真无法覆盖 CODEC_SWITCH。不要和线上放音目录一起清理。 |
| 11 | `/som/config/flush` 接受 0–5，`scanSigns` 只有 2 个元素，下标 2–5 会 panic，被 Recovery 收成 500。 | `vi-assets/vi-media-master/internal/pkg/cache/cache.go` | 运维 flush 传错下标得到 500，扫描没有执行。 | 扫描通道是缓存过期的触发器。删 flush 接口会让运维无法主动扫过期对象。应限制下标，保留扫描。 |
| 12 | 目录清理只删“7 天前那一天”。某一天 cron 没跑，该日目录就永远留下。和 ministore 的生命周期是两套机制。 | `vi-assets/vi-media-master/internal/pkg/cache/schedule.go` | 停机一天后磁盘残留不再被这条命令清掉。与 `diskLifeSpan` 谁先生效，待确认。 | 这是防止录音盘打满的兜底。删任务会让 7 天前的目录完全依赖另一套机制，而那套机制是否覆盖同一路径尚未确认。 |
| 13 | outbound 不读 WebSocket，`readLoop` / `onStream` 是死代码。vi-codec 的反馈走 HTTP notice。 | `vi-assets/vi-media-master/internal/pkg/mediasource/conn_outbound.go` | 不要在 outbound 连接上等 vi-codec 的 WS 回包。 | HTTP notice 才是异常和结束信号。删 notice 处理会让 Fallback 失去触发源。死代码删除前要确认没有构建标签把它编进来。 |
| 14 | inbound 拉流、`baseAudioPath` / `baseVideoPath`、ring 配置、RTCP、`startDiffVideo`、disableVIS 未见使用。 | `vi-assets/vi-media-master/internal/conf/config.go` | 改这些配置不会改变当前通话。启用计划待确认。 | 配置键可能已在现网 toml 里。先删代码再留配置，反序列化会失败或静默丢字段。删除前搜配置样例和调用。 |
| 15 | TTS 用 `registry.GetInstance` 在本服务注册集群里找 `outbound.appName`。推流用的是 `outbound.clusterName`。两者不一致时找不到 TTS 节点。待确认该用哪一个。 | `vi-assets/vi-media-master/internal/pkg/tts/tts.go` | 放音/TTS 失败，呼叫媒体面仍在。 | 删 TTS 寻址会让所有 TTS 放音不可用。应统一集群名，而不是去掉查找。 |
| 16 | DTMF 注释写支持 direction 4，校验只允许 1/2/3。 | `vi-assets/vi-media-master/internal/dto/media_dtmf.go` | 传 4 会被 400 拒绝，收号不生效。 | 校验收紧是为了避免非法方向写进 RTP。放宽前要确认 4 的媒体语义，不要直接删 `oneof`。 |
| 17 | Stop 不进序号队列，直接 cancel，避免挂机被前面的乱序请求堵住。Fallback 推一个空事件把序号往前拨。乱序暂存超过 5 个后直接丢，不报错。 | `vi-assets/vi-media-master/internal/proxy/session.go` | 缺号时若没有空事件，后面的事件会一直等。乱序过多的请求被静默丢弃。 | Fallback 的空事件是防“缺号导致后续事件永远等待”的兜底，不要删。Stop 绕过队列是为了挂机一定执行。 |
| 18 | 槽位第一次使用就从端口池取端口。`PortPool.Put` 存在，但 `SessionManager` 释放会话时不归还端口。每会话多个端口位，获取时线性扫描。 | `vi-assets/vi-media-master/internal/proxy/rtp_port.go`、`vi-assets/vi-media-master/internal/proxy/session_manager.go` | 端口占用随历史会话增长，直到进程重启。容量按槽位数而不是按瞬时会话数估算。 | 不归还是为了避免通话中途端口被另一会话拿走。只删 `Get` 会让 RTP 无法绑定。改成归还要同时确认对端已经放开该端口。 |
| 19 | 每会话一个 `run` 循环，无事件时用短超时收 UDP。放音、视频、outbound 各自有 goroutine。outbound 单协程串行写。 | `vi-assets/vi-media-master/internal/proxy/session.go` | 会话打满时空转开销明显。outbound 写是串行的，慢下游会拖这一路的发送。 | 串行写保证同一条 WS 的帧顺序。删锁或改成多协程并发写会打乱实时质检的包序。 |
| 20 | AMR Flush 按最高速率帧长切块（NB 32 字节 / WB 61 字节），可变长帧可能被从中间切开。vi-codec 是否按字节流重组，待确认。 | `vi-assets/vi-media-master/pkg/format/record/record.go` | 编码切换或变长帧时，下游可能解出坏帧。 | Flush 是把内存里的录音按块交给合音/推流。删 Flush 会让缓冲一直留到会话结束，内存和时延都变大。 |
| 21 | InMemory 模式下，needVI 的录音除了落盘还留在内存，供实时失败时回灌。 | `vi-assets/vi-media-master/internal/proxy/record_audio.go` | 实时会话内存高于纯落盘。关掉之后 Fallback 没有可回灌的数据。 | 内存副本是离线回退的输入。删 InMemory 等于拆掉 Fallback 的数据来源。 |
| 22 | `record.New` 把 `opts.NonDivided` 传进 bEarly 位置，参数名和语义对不上。待确认。 | `vi-assets/vi-media-master/pkg/format/record/record.go` | 早媒体分段和“不分段”标志可能绑在同一个布尔值上。 | 这个布尔值同时影响文件名和分段。删参数会改变录音文件是一段还是多段，offline-merge 的文件列表对不上。 |
| 23 | cdn 返回 404 的放音名会负缓存，直到 som reload、Remove 或 etcd watch。新上传的同名文件不重载就仍然 404。 | `vi-assets/vi-media-master/internal/pkg/avcache/avcache.go` | 刚上传的放音在重载前播不出来。预热失败是否阻断启动，待确认。 | 负缓存是为了避免不存在的放音反复打 cdn。删掉会让坏文件名把 cdn 打满。正确做法是上传后触发重载。 |
| 24 | 未配置 `ip_local_reserved_ports` 时进程直接启动失败，避免 RTP 端口被临时端口占用。 | `vi-assets/vi-media-master/internal/app.go` | 操作系统没预留端口时媒体起不来，这是有意的。 | 删掉检查会让 RTP 端口和内核临时端口重叠，出现随机无声。这是启动护栏，不是多余校验。 |
| 25 | `SessionManager.Stop` 每秒轮询，直到全部会话自由才返回，不强制掐断。停止脚本没有超时。 | `vi-assets/vi-media-master/internal/proxy/session_manager.go` | 发布或重启会一直等到长会话结束，停机时间不可控。 | 删掉等待会把还在录音的会话直接拆掉，实时质检和离线文件都不完整。要加超时也要保留收尾。 |
| 26 | 回调 vi-as 失败只记日志，不重试。SESSION_RELEASE 丢失时，vi-as 侧可能留下会话。vi-as 是否另有超时，待确认。 | `vi-assets/vi-media-master/internal/proxy/callback.go` | 媒体已释放、AS 仍认为会话在，端口和会话计数可能泄漏在 AS 侧。 | 回调是 AS 回收会话的信号。删回调发送会让 AS 只能靠自己的超时，而那个超时是否存在还待确认。 |
| 27 | `test/mstream/main.go` 和 `test/simulation` 含硬编码地址和类号码字符串。本文不转录。 | `vi-assets/vi-media-master/test/simulation/` | 测试包外传会带出未脱敏样例。 | 只清理测试夹具，不要删 `internal/proxy` 里同名的协议字段。 |
