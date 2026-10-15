---
id: lesson-known-issues-vi-merge
title: vi-merge 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-merge
  - module-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-vi-billcenter
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-merge-master/internal/processor/ac2_processor.go
  - vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go
  - vi-assets/vi-merge-master/config.prod/app.yaml
---

# vi-merge 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-merge-master`，2026-10-15。Topic 名与 vi-billcenter 不一致，见 `10-lessons/pitfalls/doc-code-conflicts.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 结束时 `pcmOutBuffer` 为空（刚好在分片边界，或数据已经切完）则 `readBuffer` 得到空，`GetWavHead(0)` 报 data empty，转成 `PcmEmptyError`，`mergeWavOutbound` 不发 MQ。下游可能永远等不到 `endFlag=true`。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go` | 实时分片质检收不到结束片，任务挂起。 | 空数据不发 WAV 是为了避免 0 长度文件。删掉空判断会把坏文件传下去；正确修法是空结束时仍发一条结束消息。不要把“不发 MQ”整段删成永远不发。 |
| 2 | 非结束片用 goroutine 上传，整通录音在上传完成后才追加，顺序取决于上传完成顺序。慢片可能晚于结束片，整通缺片。在清理之后才追加时，内存缓存报 pcmBuffer is nil；磁盘缓存会新建文件且不再清理。代码里有并发乱序的 TODO。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`、`vi-assets/vi-merge-master/internal/processor/file_cache.go` | 整通录音缺片或磁盘泄漏。 | 异步上传是为了不堵住实时切片。改回同步会拉长分片间隔。修乱序时应保留上传，而不是删整通拼接。 |
| 3 | `readPump` 退出时先 Flush 再关 exit。合音协程的 select 同时看到退出和通道数据时，可能选中退出，结束片和整通录音都不生成。 | `vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go` | 上游 EOS 后马上断开时，最后一段丢失。 | Flush 是把剩余 PCM 送进合音。删 Flush 会稳定丢掉尾部。应让退出等待通道排空，而不是删退出信号。 |
| 4 | `fileNode`、`volume` 在多个上传协程里无锁读写。`getDur` 在协程里读 `outPcmLen`，时长可能含后续数据。`ConnDiskRw` 在读锁下修改。全局 notify client 的 sessionId 会被并发覆盖，日志 key 串。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`、`vi-assets/vi-merge-master/internal/notify/global_notify.go` | 分片消息的存储节点、时长可能属于别的片；日志对不上会话。 | 这些字段是上传结果的回写。删字段会让 MQ 没有 fileNode。应加锁，不要删上传回写。 |
| 5 | 消息里的 `duration` 是累计时长，设计文档写的是当前分片时长。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go` | 下游若按分片时长切 ASR，会把累计值当成单片长度。 | 累计值被下游用来判断进度。改语义必须和消费方一起改，不能只删字段。 |
| 6 | 分片长度、时长、触发都按 2 字节/采样（S16LE）算。pcma/pcmu 时实际时长翻倍、上报时长减半。ffmpeg 转 MP3 写死 s16le。`fmt` 只认小写 `s16le`/`pcma`/`pcmu`，大写 `ALAW` 会被当成 s16le。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go` | 非 s16le 的分片时长和转码结果是错的。 | 写死 s16le 是因为当前主路径就是 PCM16。删掉格式分支会让 pcma 会话无法建 WAV 头。应补格式，而不是删转码。 |
| 7 | `sampleRate` 只校验能被 8000 整除，0 也能通过。frameLen 为 0 时每块都成一片，WAV 头采样率为 0。 | `vi-assets/vi-merge-master/internal/models/bmessage.go` | 错误采样率会打出大量空片或无法播放的文件。 | 校验是挡非法流的唯一门槛。删校验会让任意采样率进入切片。应拒绝 0，不要放宽到不检查。 |
| 8 | `finalSuffixMp3=true` 只对已经落盘的长通话生效；短通话走内存，总是 WAV。MP3 上传失败不 saveDisk（因为 MP3 文件还在），随后目录被清理，整通丢失。ffmpeg 失败回退 WAV 时 pcm 缓冲可能已空。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`、`vi-assets/vi-merge-master/conf/config.go` | 短通话没有 MP3；长通话 MP3 失败后整通没了。 | MP3 开关是对客录音格式。删分支会让所有整通突然变成 WAV 或突然全部走 ffmpeg。失败落盘是兜底，应补上，不能删。 |
| 9 | 分片上传失败只通知媒体，不重试、不落盘。整通失败落本地 `/data` 路径，没有补传。MQ 发送失败不补发。是否有外部补传，待确认。 | `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`、`vi-assets/vi-merge-master/internal/notify/global_notify.go` | 存储或 MQ 抖动时分片永久缺失，实时质检缺片。 | 通知媒体是触发降级的信号。删通知会让 vi-media 不知道合音失败，离线兜底也不一定启动。本地落盘是整通的最后副本，不能删。 |
| 10 | 真正的会话 key 来自 `X-Params.sessionId`，`X-Session-Id` 被忽略。两者不一致时日志对不上。X-Params 没有 sessionId 时内部 key 是 `merge_<纳秒>`，但消息和文件名里的 sessionId 仍可能是空串。 | `vi-assets/vi-merge-master/internal/models/bmessage.go` | 空 sessionId 的文件路径和下游关联会失败。 | X-Params 是 vi-codec 透传的业务头。改成只认 X-Session-Id 会和现网透传约定不一致。空 key 的生成是为了还能记日志，删掉会在缺参时直接崩。 |
| 11 | maxSession 用 `>` 比较，实际允许多一个连接。 | `vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go` | 容量比配置多 1，一般不是故障，但限流告警会对不齐。 | 限流是保护 ffmpeg 和磁盘。删比较会让连接数无上限。 |
| 12 | 心跳已关（注释日期 20260605），只靠 60 秒读超时。上游静音若不发帧会被断开。上游是否持续发静音帧，待确认。 | `vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go` | 长静音通话可能被拆，后续 PCM 进不来，结束片丢失。 | 读超时是发现死连接的唯一手段。删超时会让半开连接一直占着 maxSession。 |
| 13 | WS 错误帧两种形状：就绪/业务错误是字符串码、小写键；处理错误是数字码、大写键。 | `vi-assets/vi-merge-master/internal/service/route/wshandler/conn_handler.go` | 上游若只解析一种 JSON，会把另一类错误当成成功或解析失败。 | 两种响应都在用。删其中一种会让对应失败没有响应，上游一直等到超时。 |
| 14 | `HttpNotifyMedia` 用 `fmt.Sprintf` 拼 JSON。错误信息里若有引号，`data` 不是合法 JSON。 | `vi-assets/vi-merge-master/internal/notify/global_notify.go` | vi-media 解析降级通知失败，Fallback 可能不触发。 | 这个通知是合音失败的降级入口。删发送会让实时合音失败后没有离线回退。应改成 JSON 编码。 |
| 15 | `CanExecute` 要求权限恰好是 0755 或 0777。0775、0750 都会启动失败。`start.sh` 会先 chmod 755。`ffmpeg.staticPath` 配置不生效。 | `vi-assets/vi-merge-master/internal/util/runtime_permissions.go`、`vi-assets/vi-merge-master/internal/app/app.go` | 镜像里 ffmpeg 权限略有不同就起不来。改 staticPath 配置没有用。 | 权限检查是为了避免运行中才发现 ffmpeg 不能执行。删检查会把故障推迟到第一通需要转 MP3 的呼叫。 |
| 16 | 分片上传的 path、volume 没有 URL 转义（旧的 Upload 有转义）。上传耗时直方图只在非 2xx 且非 5xx 时记录，成功没有统计。 | `vi-assets/vi-merge-master/internal/httpinterface/filehttp.go` | path 含特殊字符时上传 URL 非法，文件不上 PFS。成功耗时没有指标。 | 上传函数是录音落存储的唯一路径。删上传会让所有分片只留在本地。应补转义，保留上传。 |
| 17 | 日志打印主被叫和 userData 全文，以及成功时的完整合音 JSON。和“日志不写真实号码”冲突。本文不摘录样例。 | `vi-assets/vi-merge-master/internal/models/bmessage.go` | 日志采集会存下号码和随路数据。 | 删掉整个 String 方法会让现有日志格式化空指针或退化为无内容。应改成脱敏后再打。 |
| 18 | `Stop` 只通知媒体并按会话 sleep 约 100ms，不等合音结束。 | `vi-assets/vi-merge-master/internal/app/app.go` | 发布过程中的通话没有整通录音，也没有结束消息。 | 通知媒体是让上游别再推流。删通知会让停机后上游继续往已关闭的端口送数据。应加长等待，而不是删停机逻辑。 |
| 19 | 遗留代码包括客户 HTTP、独立 WS 服务、写泵、内存工具、抽样和上传测试、chat/game 常量。运维查询不返回 finalSuffixMp3。 | `vi-assets/vi-merge-master/internal/httpinterface/customer/qa_http.go`、`vi-assets/vi-merge-master/internal/service/route/ops/handlers.go` | 运维接口看不到 MP3 开关的当前值。这些包不在主合音路径上。 | 运维接口能改 `finalSuffixMp3`，但查询不返回它。删查询接口会让现网改配置没有入口。遗留包删除前搜引用和 `09-decisions/`。 |
| 20 | `/ops/config`、`/ops/omu/pause` 和 pprof 无鉴权，只靠端口隔离。 | `vi-assets/vi-merge-master/internal/service/route/router.go` | 能访问该端口的人可以改配置、暂停或看堆栈。 | 这是现网改日志级别和摘流量的入口。加固应加鉴权。在替代手段就绪前不要删 pause。 |
