---
id: lesson-known-issues-vi-pusher
title: vi-pusher 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-pusher
  - module-vi-pusher
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-qa-server
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionResultReceiver.java
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/config/RestTemplateConfig.java
---

# vi-pusher 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-pusher-master`，2026-10-15。当前生效的是质检结果推送。话单/录音推送代码还在，但订阅和 URL 已失效。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 主消费受 `pusher.inspectionpusher` 控制，延时重试消费不受这个开关控制。主消费关掉的实例仍会消费重试队列并真正推给客户。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionResultReceiver.java`、`vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionDelayReceiver.java` | 只想停推送时，已经进延时队列的消息还会发出去。关主消费的实例也必须能访问代理和数据库。各实例开关取值待确认。 | 重试消费者是失败补偿。删掉它，第一次失败的结果没有第二次机会，只能等人工。开关不对称要改条件，不要只删重试。 |
| 2 | 给代理的超时是 10 秒，本端响应超时是 8 秒。客户在 8 到 10 秒之间返回时，本端判失败并在 60 秒后重推，客户可能已经处理过。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java`、`vi-assets/vi-pusher-master/src/main/java/com/cmic/config/RestTemplateConfig.java` | 重复推送。客户必须按 listId 或 taskId 幂等，约定待确认。代理是否自己再重试，待确认。 | 8 秒超时是为了不让推送线程被慢客户占满。删超时会把线程池拖死。应把两端超时改成同一量级，并保留重试。 |
| 3 | 查不到 `bill_new` 就直接返回。移表后的重复消息这样丢弃是合理的。话单 ID 写错或数据库瞬时异常（异常被捕获后仍然 ACK）时，结果静默丢失，没有计数。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 该推给客户的结果没有推，也没有告警。 | 查不到就返回是移表后的去重。改成失败重试会让已经成功的话单每分钟再打一次数据库。应区分“行不存在”和“查询异常”。 |
| 4 | `result_push_url` 为空时状态记为 3（与真正推送成功相同）。是否有意，待确认。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 运营统计把“没配地址”算成推送成功。 | 记 3 并移表是为了让没配回调的话单离开 `bill_new`。删移表会让这些话单一直占着在途表。应改成单独状态，而不是不移表。 |
| 5 | 固定 60 秒、最多再试 3 次，没有指数退避。第 5 次到达后置 -3 并移表。没有死信、补推接口，只有失败计数。延时消息本身发送失败时，结果彻底丢失。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionDelaySender.java` | 客户故障超过约 4 分钟，结果不再推。是否需要补推，待确认。 | 次数上限是为了避免坏地址无限重试。删上限会把代理打满。删延时发送则一次失败就结束。 |
| 6 | 重试期间 `bill_new` 的状态就是 -3，看不出“还在重试”还是“最终失败”。最终失败的在历史表里，状态仍是 -3。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 只查在途表会把重试中的话单当成已经失败。 | -3 被移表条件和告警共用。改成另一个中间状态要同时改移表 SQL。不要直接删状态更新。 |
| 7 | 企业或客户缓存缺失也走重试。配置漏配时，大约 4 分钟后置 -3 并移表。缓存只在版本键变化或重启时刷新。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 漏配回调地址的企业，话单会被当成推送失败移走，补配之后也不会自动再推。 | 走重试是为了等缓存刷新。删掉这段会让一次缓存空窗直接丢结果。 |
| 8 | 移表先 INSERT 再 DELETE，不是事务。并发重复消息时第二次 INSERT 主键冲突，被捕获后置 -3 并发延时。下次查不到话单就丢弃。客户可能已经收到两次。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/dao/BillHistoryDao.java` | 历史表重复行或重复推送。二期新增列目前没有进历史表 INSERT，移表后这些列只剩默认值。是否要带给客户，待确认。 | 先插入再删除是防话单丢失。删历史插入会让在途表删除后没有归档。补列时要改 INSERT，不能删移表。 |
| 9 | 异常日志的占位符少了话单 ID 参数，异常文本落在第一个占位符，排障看不到话单 ID。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 失败日志无法按话单检索。 | 这是失败现场的唯一日志。删掉 error 日志会让推送异常没有记录。应补参数。 |
| 10 | INFO 打印完整消息体和推送响应，可能含转写和号码。本文不摘录。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionResultReceiver.java` | 日志采集保存通话内容和号码。 | 应脱敏。不要为了脱敏删掉接收函数。 |
| 11 | 对客报文原样转发内部字段：随路数据里的企业/客户标识、内部 taskId、data。没有单独的对外契约层。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | qa-server 回调字段一变，客户就收到新字段或丢字段。 | 透传是当前对客契约。删字段会让已经联调过的客户解析失败。改结构前要做影响分析。 |
| 12 | 服务发现拿不到代理节点时，URL 变成字面量 `null` 加路径，抛异常后进入重试，没有专门告警。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java` | 代理全挂时，结果会按普通失败重试，最终 -3，和客户拒绝混在一起。 | 重试给了代理恢复的时间。删重试会让一次发现失败就移表。应加“节点为空”的指标。 |
| 13 | 话单/录音推送的 URL 被写成空串，对应订阅注解被注释，延时发送器没有注入（调用会空指针），有的延时 topic 没有消费者。若干依赖没有被读取。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/billpusher/CommonBillPusher.java` | 不要以为话单和录音还在推给客户。当前只有质检结果链路。是否计划恢复，待确认。 | 删除前查 `09-decisions/` 和本目录，确认没有回切。质检结果的 Receiver 和 Pusher 不能一起删。空 URL 若被重新接上订阅，会把请求发到空地址。 |
| 14 | 生产配置里有外部地址，以及注释掉的消息队列账号字段。测试 profile 的构建会尝试上传并远程重启。本文不抄地址和凭据。是否已轮换，待确认。 | `vi-assets/vi-pusher-master/config.prod/application.yml`、`vi-assets/vi-pusher-master/pom.xml` | 仓库泄漏历史凭据；默认打包可能打到测试机。 | 凭据要移出仓库并轮换。不要在未改代码的情况下只删配置键，见下一条。 |
| 15 | `second-reach-*` 是必填配置，没有默认值。删掉这些看起来无用的键，进程起不来。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/utils/Parameters.java` | 清理配置会导致启动失败。 | 字段还在 `@Value` 里。先删字段和引用，再删配置。不要只删 yaml。 |
| 16 | test profile 的 mainClass 指向不存在的类，上传的 jar 名也和实际 artifact 不一致。wagon 上传预期会失败，待确认。 | `vi-assets/vi-pusher-master/pom.xml` | 用 test profile 打包/上传不可用。 | 改 profile 时保留 prod 的启动方式。不要把不存在的主类写进生产插件配置。 |
| 17 | HTTP 客户端信任所有证书，RestTemplate 关闭主机名校验。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/utils/HttpClientFactory.java`、`vi-assets/vi-pusher-master/src/main/java/com/cmic/config/RestTemplateConfig.java` | 到代理或客户的 TLS 可以被冒充。 | 这是为了适应当前证书。直接删客户端会让推送全部失败。应收紧并验证对端证书。 |
| 18 | `ApplicationInit` 和 `Parameters` 都在 `@PostConstruct` 里初始化，顺序不确定。缓存加载和 ETCD 初始化分属两边，任一失败都 `System.exit(1)`。 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/utils/ApplicationInit.java`、`vi-assets/vi-pusher-master/src/main/java/com/cmic/utils/Parameters.java` | 启动偶发先查缓存、后连 ETCD，空配置窗口或启动失败。 | exit 是为了避免半初始化后继续推送。删掉会让没有 ETCD 的进程把结果推错地址。应改成显式顺序，保留失败即退出。 |
