---
id: lesson-known-issues-offline-merge
title: offline-merge 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-offline-merge
  - module-offline-merge
  - lesson-known-issues-vi-media
  - lesson-known-issues-vi-merge
  - lesson-known-issues-vi-billcenter
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/offline-merge-master/internal/processor/parser_processer.go
  - vi-assets/offline-merge-master/internal/processor/upload_processor.go
  - vi-assets/offline-merge-master/config.prod/app.yaml
  - vi-assets/offline-merge-master/internal/pkg/broker/pub/dcc_producer.go
---

# offline-merge 已知坑与不要随手删的逻辑

核对 `vi-assets/offline-merge-master`，2026-10-15。本服务发 `TID_VI_MERGE_BILL_RECORD_NORMAL`，vi-billcenter 不订这个名字，见 `10-lessons/pitfalls/doc-code-conflicts.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | `source=1`（只录主叫）时不产出配对。`maxSize` 取被叫腿长度，判断条件也和被叫比，永远不会改用主叫长度。只录主叫时循环 0 次。双向录音时多出来的主叫分段被静默丢掉。线上是否会出现 source=1，待确认。 | `vi-assets/offline-merge-master/internal/processor/parser_processer.go` | 只录主叫的通话没有合音结果；主叫分段多于被叫时多段丢失。 | 配对循环是整通合音的入口。删函数会让所有离线合音停掉。应把长度改成两侧的较大值，并保留被叫为空时的单腿输出。 |
| 2 | `SaveFile` 不把上传错误返回给上层。合音完成消息里也没有上传结果。下游拿到 fileName、fileNode，文件可能只在本机失败目录，或者根本不存在。 | `vi-assets/offline-merge-master/internal/processor/upload_processor.go` | 话单中心或质检按 URL 拉录音会 404，但消息看起来成功。谁补传 `/data/offlocal/`，待确认。 | 失败落盘是唯一副本。删落盘会让上传失败的录音彻底消失。应在消息里标失败，而不是删发送。 |
| 3 | 只有 MQ 发送失败会触发消费重投。重投会把下载、ffmpeg、上传再做一遍，可能重复上传。 | `vi-assets/offline-merge-master/internal/processor/upload_processor.go` | 下游可能收到重复的合音完成消息。最大重投和死信，待确认。 | 重投是 MQ 失败的补偿。改成失败也 ack，消息就丢了。下游需要按会话去重，不能靠删重投来“防重复”。 |
| 4 | `GetPcmAttr` 缺省返回小写 `s16le`，探测按大写 `S16LE` / `F32LE` 比较，结果变成 UNKNOWN，ffmpeg 参数可能错。 | `vi-assets/offline-merge-master/internal/models/merge_message.go`、`vi-assets/offline-merge-master/internal/audioformat/detector.go` | 媒体实际传入的 pcmFormat 若带 `pcm_` 前缀或大小写不同，解码参数错误。取值待确认。 | 探测是选择 ffmpeg 解码器的依据。删探测会让所有文件走同一解码器，AMR/G729 更解不开。应统一大小写。 |
| 5 | `detectFormatBytes` 直接切前 21 字节。文件小于 21 字节会 panic，靠 recover 接住。 | `vi-assets/offline-merge-master/internal/audioformat/detector.go` | 空文件或残片让这一次合音失败，而不是给出明确错误。 | recover 是防进程退出的兜底。删掉之后短文件会打崩处理协程。应先判断长度，保留 recover。 |
| 6 | `getKey` 要求 key 个数大于 1。只有一个 key 时取不到 sessionId，只影响日志。 | `vi-assets/offline-merge-master/internal/pkg/broker/receive/mq_consumer.go` | 单 key 消息的日志没有会话号，排障对不上。 | 消费循环本身不能删。取 key 失败只该降级日志，不要因此 nack 或丢掉消息。 |
| 7 | 传给 ffmpeg 的 `-timelimit` 用的是按毫秒算出来的数，ffmpeg 该参数单位是秒，等于没有限制。真正停住靠 `CommandContext` 的超时。 | `vi-assets/offline-merge-master/internal/pkg/ff/runsh.go` | 坏文件不会被 ffmpeg 自己的 timelimit 杀掉，只靠外层超时。 | 外层超时是防 ffmpeg 卡死的兜底。删 context 取消会让一个坏文件占住 worker。`-timelimit` 参数应改单位，不要删整个命令封装。 |
| 8 | 输出固定 8k。16k 宽带输入也会降采样，mp3 码率按 16kbps/声道。是否故意（ASR 是否要 16k），待确认。 | `vi-assets/offline-merge-master/internal/pkg/ff/runsh.go` | 质检若要宽带，离线合音给不出 16k。 | 8k 是和现网 ASR/存储约定绑在一起的。突然删掉重采样会改变文件大小和下游解码参数。 |
| 9 | 清理只删下载的分段和最终结果。临时目录、concat 中间文件、fileList、补空腿文件没有逐一删除。长期运行会占满磁盘。是否有外部清理，待确认。 | `vi-assets/offline-merge-master/internal/processor/parser_processer.go` | 磁盘被临时文件打满后，后续合音全部失败。 | 现有删除是防止成功任务留下分段原件。删掉这段清理会让每通通话都留一份下载副本。应补全临时文件删除，而不是去掉已有清理。 |
| 10 | 多个结果并行上传时同时写 `fileNode`、`volume`、失败计数，没有锁。消息里的节点可能来自另一个文件。 | `vi-assets/offline-merge-master/internal/processor/upload_processor.go` | 话单指向了错误的存储节点，录音 URL 404 或串文件。 | 这些字段是上传成功后告诉下游“文件在哪”。删字段会让消息没有落点。应加锁。 |
| 11 | 质检模式上传 URL 不做转义，path 或 volume 有特殊字符会错。DCC 分支会转义，两边不一致。 | `vi-assets/offline-merge-master/internal/processor/upload_processor.go` | 带特殊字符的路径上传失败，但可能仍然发完成消息（见第 2 条）。 | DCC 的转义是已经验证过的写法。删 DCC 转义会让旧模式也失败。质检分支应补上同样的转义。 |
| 12 | DCC 消息的 `callId` 没有赋值，`code` 固定 0，`bindId` 的 JSON 名是 `bindIdm`。vi-billcenter 的 DCC 消费读 `code` 和 `callId`。 | `vi-assets/offline-merge-master/internal/pkg/broker/pub/dcc_producer.go` | 若生产仍有 dccMerge，下游要么对不上话单，要么把失败也当成 code=0 成功。生产是否还有 dccMerge，待确认。 | `bindIdm` 这个错误 tag 可能已经被下游将错就错地读取。改名必须和消费方一起发。不要在未确认消费方之前“顺手改正”。 |
| 13 | 只初始化 `modeBiz` 对应的 producer，初始化错误被忽略。消息是 dccMerge 而服务是 voiceInspection 时，DCC producer 没初始化，发送会失败或 panic。 | `vi-assets/offline-merge-master/internal/pkg/broker/pub/mq_producer.go` | 模式配错时合音结果发不出去，还可能打崩发送路径。 | 按模式初始化是为了不连用不到的 topic。删掉判断会让两种 producer 都起来，但空指针发送更危险。应在发送前检查 nil。 |
| 14 | 停机先停 producer，再停消费者。停机过程中还在处理的消息会发送失败，然后被重投。 | `vi-assets/offline-merge-master/internal/app/app.go` | 发布窗口内的合音会重做一遍，存在重复上传。 | 先停发送是为了不要在退出过程中再挂新的 broker 调用。倒过来可能在进程已退出时仍写 MQ。调整顺序要同时保留重投，避免消息丢在半成品上。 |
| 15 | `/v1/test` 会真正上传并发合音消息，没有鉴权。`/testmetrics` 会打监控点。ops 可以停消费或从 ETCD 注销，也没有鉴权。生产是否开放，待确认。 | `vi-assets/offline-merge-master/internal/service/route/router.go`、`vi-assets/offline-merge-master/internal/service/route/bizhandler/test_handler.go` | 能访问端口的人可以制造假合音消息、污染指标或摘掉节点。测试夹具注释里有未脱敏样例，本文不转录。 | ops 是摘流量和改日志的入口。删 pause 之前要有替代的发布手段。`/v1/test` 可以从生产路由去掉，但不要连同真正的上传函数一起删。 |
| 16 | 日志打印主被叫。被叫占位打出来的也是主叫。本文不摘录号码。 | `vi-assets/offline-merge-master/internal/processor/bparser/vi_parser.go`、`vi-assets/offline-merge-master/internal/processor/bparser/dcc_parser.go` | 日志里出现未脱敏号码，排障时还会把被叫看成主叫。 | 删整段文件名函数会让合音结果没有输出名。应脱敏并改对字段。 |
| 17 | 早媒体和全链路只在 DCC 模式启用。质检解析的 FullMerge 为 false。 | `vi-assets/offline-merge-master/internal/processor/parser_processer.go` | 质检离线合音没有早媒体拼进整通。和 vi-media 消息里空的 early 文件一致。 | DCC 的早媒体拼接是旧中间号兼容。删 DCC 分支会让仍在跑的 dccMerge 失去早媒体。质检侧不要为了“对齐 DCC”默默打开全链路。 |
| 18 | 下载和 concat 使用共享的 `/tmp/mergeCall_<序号>`。同一会话的消息被重投时，可能和正在进行的处理抢同一批文件。待确认。 | `vi-assets/offline-merge-master/internal/processor/parser_processer.go` | 重投和在途任务互相覆盖临时文件，合音结果随机。 | 临时目录是 ffmpeg concat 的输入。删目录创建会让合音无法落地。应改成按会话加隔离，保留目录。 |
| 19 | `bin/start.sh` 不 chmod。ffmpeg 可执行依赖启动检查（0755/0777）。main 没有忽略 SIGPIPE（vi-merge 有）。 | `vi-assets/offline-merge-master/bin/start.sh` | 权限不对时起不来；管道断开时可能被 SIGPIPE 杀掉。 | 启动脚本是现网拉起方式。删检查会把 ffmpeg 权限问题推迟到第一通呼叫。 |
| 20 | `MqSimpleConsumer` 留在代码里。当前启动是否接上它，以初始化调用为准；报告认为这条实现没有走主路径。 | `vi-assets/offline-merge-master/internal/pkg/broker/receive/mq_consumer.go` | 排障时不要假设有两条消费者同时在 ack。 | 未接线的实现可能是回切入口。删除前确认没有脚本直接调用。正在使用的消费循环不能一起删。 |
