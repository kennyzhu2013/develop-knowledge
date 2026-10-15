---
id: lesson-known-issues-scheduler
title: scheduler 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-scheduler
  - module-scheduler
  - lesson-known-issues-qa-server
  - lesson-security-risks
  - lesson-doc-code-conflicts
  - lesson-open-questions
  - arch-security
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/CalculateCondition.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/CallbackService.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/TaskDerepeatConsumer.java
---

# scheduler 已知坑与不要随手删的逻辑

核对 `vi-assets/scheduler-master`，2026-10-15。运行时任务在 Mongo，不是 MySQL DDL。表达式执行风险见 `10-lessons/pitfalls/security-risks.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 条件表达式用 `StandardEvaluationContext`，能调用任意 Java 类型。流程 XML 可由未鉴权的管理接口上传。 | `vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/CalculateCondition.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/TaskCondition.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/RedisCondition.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java` | 能访问管理端口的人可以让流程执行任意代码。 | 表达式是流程分支的引擎，删掉条件执行会让所有 XML 质检流程停住。应换成受限上下文并给管理接口加鉴权，不要删流程引擎。 |
| 2 | 模型凭据会放进发往模型队列的消息。查询接口会把 authentication 返回给调用方。 | `vi-assets/scheduler-master/src/main/java/com/cmic/dao/AIModelDaoImpl.java` | 凭据出现在 MQ 和查询响应里。本文不抄字段值。 | 模型调用依赖这份凭据。删字段会让下游鉴权失败、质检无结果。应限制查询返回并改投递方式。 |
| 3 | 本地正则使用任务方传入的表达式，存在正则灾难性回溯。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/ai/KeywordRegexService.java` | 一条恶意或写坏的正则可以占住调度线程。 | 正则匹配是关键词能力本身。删服务会让关键词流程空转直到超时。应加超时和长度限制。 |
| 4 | 测试脚本含疑似内网地址和由号码组成的录音文件名。本文不转录。 | `vi-assets/scheduler-master/src/test/shell/curl.sh` | 仓库外传会带出未脱敏样例。 | 只清理测试脚本。不要删同目录外的正式流程 XML。 |
| 5 | 子任务回调日志去掉转写正文后仍打印其余字段，其中可以有通话内容。子任务 toString 会打 attributes 和响应。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/CallbackService.java` | 日志里留下通话内容。 | 回调组装是结果回 qa-server 的唯一路径。应脱敏日志，不要删回调。 |
| 6 | 子任务全部完成后，主任务回调必须经过 master 才有的去重消费者。没有 master 时主任务永不回调。多个 master 时进程内 1000 条 LRU 去重失效，重启即丢，也不能跨实例。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/TaskDerepeatConsumer.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/service/JobService.java` | 主任务结果不回到 qa-server，或重复回调。生产是否保证有且仅有一个 master，待确认。 | 先入重复队列再由 master 去重，是多实例同时收齐子任务时只回调一次的兜底。删队列会变成每个实例各回调一次。 |
| 7 | 建任务校验失败时，向上游发送失败原因的语句被注释，只有日志。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/CallbackService.java` | qa-server 不知道任务为什么没建起来，只能等超时。 | 注释掉的发送是缺陷。恢复发送前不要把整个错误回调方法删掉，那是以后补通知的位置。 |
| 8 | 子任务回调先发送、后把 callback 标成已回调，发送异常只记 message。流程被禁用、模型名不在枚举里导致空指针时，也会被标成已回调但消息没出去。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/CallbackService.java` | qa-server 永远等不到这条子任务结果，主任务可能一直不汇总。 | 先发再标记是为了至少尝试一次。改顺序可以，不要删标记，否则超时兜底和迟到结果会重复写结论。 |
| 9 | 流程上的 timeout（1–60 分钟）只存和校验，不参与扫描。真正超时是 24 小时和 48 小时兜底，且只扫 3 天内的任务。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/CallbackService.java` | 实时质检的分钟级 SLA 不会由这个字段保证。更早的任务在 master 长时间不可用后永远不回调。是否符合预期，待确认。 | 24/48 小时扫描是漏回调的最后兜底。删扫描会让卡死的任务永远停在 Mongo。分钟级 timeout 要另做，不要拿掉长超时。 |
| 10 | 流程 status 改为停用后，内存命中不再检查 status，热更新也不管 status，重启前仍然可用。processMap 是非线程安全的 HashMap，却被消费线程和热更新并发读写。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/ProcessService.java` | 停用流程仍会继续质检，直到重启。并发更新可能丢流程或读到半截。 | 内存缓存是为了不每条消息都查 Mongo。删缓存会让每条任务打库。应在命中时检查 status，保留缓存。 |
| 11 | 流程 XML 只写在收到上传请求的那台机器。其它实例从各自本地盘重载。`xml.path` 若不是共享存储，其它实例会加载旧文件或找不到文件，随后匹配空指针。是否共享，待确认。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/ProcessService.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/service/XmlEventLoaderService.java` | 多实例下流程更新只在一台生效，其余继续跑旧规则或 NPE。 | 写本地再广播是现有热更新协议。删广播会让所有实例永远用启动时的 XML。应改成共享存储或把文件内容放进消息。 |
| 12 | 启动时递归扫描整个 xml 目录，同名文件取第一个。dev/test/prod 目前没有同名文件。打包时三套流程都会进生产包。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/XmlEventLoaderService.java` | 一旦出现同名文件，生产可能加载到 dev 流程。 | 扫描是加载流程的方式。删递归会让子目录里的正式流程加载不到。应限制只扫当前环境目录。 |
| 13 | 分片的“已处理”标记被注释。第 N 片到达和第 N-1 片完成同时发生时，可能把第 N 片发两次 ASR。追加转写也不是原子的。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ShardTaskService.java` | 实时分片重复识别、转写串段。实时版流程是否已上线，待确认。 | 被注释的 CAS 是防重的草稿。不要把发送 ASR 的主路径一起删掉。恢复标记前要确认并发语义。 |
| 14 | 模型响应先读状态再处理，两条重复响应可以同时通过。并行分支同时到齐时可能重复发下一步、重复结束子任务。dyHttp 的状态键和去重键不一致，等于没有去重。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MessageConsumer.java` | 重复调用模型、重复回调。 | 已回调则直接 ack 是防超时之后的迟到结果覆盖结论，这个判断不能删。缺的是原子去重，不是多出来的 ack。 |
| 15 | 消费者异常都 nack 且不重入队。队列是否有死信，待确认。流程不存在时有的消费者 return 但不 ack，消息一直 unacked，直到连接断开。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MessageConsumer.java` | 失败子任务只能等 24/48 小时兜底。不 ack 的那条会占着 prefetch。 | 不重入队是为了避免毒消息死循环。改回重入队之前要有死信和次数上限。超时兜底不能删。 |
| 16 | 发送确认关掉。mandatory 打开但没有退回回调，路由不到的消息静默丢失。信号量 acquire 没有使用超时配置。线程中断时 finally 仍 release，许可可能多还一个。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/MqService.java` | 模型队列名配错时任务发出去就没了，也没有失败回调。 | 信号量是限流。删限流会把模型端打满。应补确认回调，保留限流。 |
| 17 | 先写子任务再写主任务。requestId 并发重复时主任务因唯一索引失败，子任务变成孤儿。事务注解对 Mongo 无效。HTTP 错误日志把异常 toString 当成占位参数，栈被丢掉。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ProcessJobService.java` | 库里留下没有主任务的子任务，不会被正常回调。 | 子任务文档是模型回写的载体。倒序写入可以，不要删子任务插入。唯一索引是防重复建任务的，不能删。 |
| 18 | 脱敏回包用脱敏文本替换转写，原文不保留。鉴伪回包会用鉴伪时长覆盖 ASR 时长。两个模型都有时，时长取决于谁后到。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MessageConsumer.java` | 回调里的转写是脱敏后的；时长可能不是 ASR 时长。 | 替换原文是为了不在回调和库里长期留通话文本，和下面的 unset 是一套。不要为了排障把原文写回去。时长覆盖要改字段，不要删鉴伪结果。 |
| 19 | `allNotEquals` 的实现和“全部相等”相同：全部相等才返回 true。XML 里用它时期望会反。 | `vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/CalculateCondition.java` | 配了“不全等”的流程会把相等当成命中。 | 方法被流程 XML 按名字调用。删方法会让已有 XML 在运行时找不到函数，任务走到异常兜底。应改实现并回归 XML。 |
| 20 | 模型耗时直方图的减法少了括号，数值没有意义。按流程计数任务时忽略 stream 参数，写死成流式。超时指标是计数器，每小时把存量再累加一次。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/metrics/CommonMetrics.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/dao/TaskDaoImpl.java` | 看板上的耗时和任务量不能直接当 SLA。 | 指标是现网告警的来源。删注册会让看板断线。应改计算，保留指标名或做兼容。 |
| 21 | 实时版专线流程的结束事件里，同一状态判断写了两次，疑似其中一个应是另一个状态。离线版在中风险分支把质检结果写成 1，其它命中分支写成 0。是笔误还是有意，待确认。实时版在 dev 目录，prod 目录是离线版。 | `vi-assets/scheduler-master/sources/dev/zhuanxian_qc_2.xml`、`vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml` | 关键词分支可能无法按设计结束。中风险的对客结果可能和其它命中不一致。 | 这两份 XML 是专线质检实际规则。不要在未确认前改结果值或删结束事件，否则线上风险等级和是否结束都会变。 |
| 22 | dev 里有的流程引用了不存在的动作，或调用了只支持 match 的动作去发 MQ，子任务会挂到超时。部分队列的声明和监听已注释。这些能力是否还在 broker 上，待确认。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/MqService.java` | 误用这些流程时，任务不会失败回调，只能等长超时。 | 已注释的监听说明这些模型当前不调度。恢复监听会把流量打到可能已下线的队列。删除 XML 前确认没有客户还绑着这些 processId。 |
| 23 | HTTP 和 MQ 的优先级校验等价，但 MQ 消息里 priority 为 null 时会空指针，然后 nack 丢弃。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ProcessJobService.java` | 缺优先级的任务被丢掉，上游没有失败原因（见第 7 条）。 | 校验是为了拒绝非法优先级。删校验会让任意字符串进入队列优先级。应把 null 当成缺省，保留范围检查。 |
| 24 | 消费者 ack 使用 multiple=true。同一 channel 上 prefetch 大于 1 时，会把前面还没处理完的消息一起确认。是否会造成宕机丢消息，待确认。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MessageConsumer.java` | 进程在处理中崩溃时，已被提前确认的消息不会重投。 | ack 本身不能删，否则消息一直 unacked。应改成单条确认。 |
| 25 | 启动脚本 shebang、按进程名停止、测试环境重启路径存在问题。dev 包可能没有 bin。实际由谁拉起，待确认。 | `vi-assets/scheduler-master/bin/start.sh` | `stop` 可能停不掉进程，发布脚本误判成功。 | 脚本是现网入口。修改前要和实际的进程托管方式对上，不要只删脚本导致发布没有启动命令。 |
| 26 | 回调前从子任务结果里去掉转写、ASR 详情和部分智能体详情，控制 Mongo 体积，也避免长期留通话文本。 | `vi-assets/scheduler-master/src/main/java/com/cmic/dao/SubTaskDaoImpl.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/service/JobService.java` | 回调发出去之后，库里不再有原文。排障不能靠回读 Mongo 还原转写。 | 这是隐私和容量兜底。删 unset 会让每条质检的转写留在 Mongo。 |
| 27 | 子任务已经回调之后再到达的模型响应直接 ack，只计超时指标，不覆盖已回调结论。 | `vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MessageConsumer.java` | 超时兜底之后的迟到结果被丢弃，最终结论保持超时那一次。 | 这是防止迟到结果改写已经推给客户的结论。删掉会让超时失败后又被迟到成功覆盖，客户收到两次相反结果。 |
| 28 | 多个分支都会写风险等级，策略只允许往更高风险改。 | `vi-assets/scheduler-master/src/main/java/com/cmic/engine/strategy/RiskLevelOverrideStrategy.java` | 最终风险取各分支的最高值，低风险分支不能把高风险改回去。 | 这是多模型并行时的汇合规则。删掉会让后写的低风险覆盖先写的高风险，漏报。 |
| 29 | 主任务回调先到 repeat 队列，由 master 去重，再到负载均衡队列由任一实例执行。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/JobService.java` | 多实例同时收齐时只回调一次，同时把回调负载摊开。 | 两段队列是“只回调一次”和“谁都可以发”同时成立的原因。删掉任一段都会变成重复回调或只有 master 能发。 |
| 30 | 发给模型的 jobId 是子任务 ID 加分隔符加业务 dataId。scheduler 只取分隔符前面的部分。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/MqService.java` | 模型侧能看到业务 ID 便于排障。改格式会导致回写对不上子任务。 | 这是和模型适配器的契约。删后缀可以，但分隔规则必须和解析一起改。不要只改一边。 |
| 31 | 有的交换机和队列同名，催收复用场景交换机。名字来自历史，模型适配器依赖这些字符串。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/MqService.java` | 改名会让对应模型收不到任务，质检挂到超时。 | 看起来重复的名字是契约，不是复制粘贴错误。改名要和适配器同时发。 |
| 32 | 失败记录是否重试、异常消息怎么处理，代码里仍是 TODO。事件接口标了下线 TODO。取 HTTP 配置的方法恒返回 null。 | `vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ProcessJobService.java`、`vi-assets/scheduler-master/src/main/java/com/cmic/controller/EventController.java` | 失败没有自动重试。不要调用那个恒为 null 的配置方法来做分支。 | TODO 说明行为未完成，不是可以删的废代码。删事件接口前要确认没有旧客户端。null 方法若被接上配置，调用方会空指针，应显式失败。 |
| 33 | 北向 MQ、ClickHouse、groovy、旧的 Redis 任务模型、MySQL DDL 都还在仓库里，运行时任务不走 MySQL。 | `vi-assets/scheduler-master/src/main/resources/mysql/sub_task.ddl` | 按 DDL 理解状态码会和 Mongo 里的写入相反，见冲突篇。 | DDL 和旧模型可能仍被别的系统阅读。删除前要确认没有报表或脚本在用。正在使用的 Mongo 写入不能一起删。 |
