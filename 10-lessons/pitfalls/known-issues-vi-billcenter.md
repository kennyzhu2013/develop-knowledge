---
id: lesson-known-issues-vi-billcenter
title: vi-billcenter 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-billcenter
  - module-vi-billcenter
  - lesson-known-issues-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-qa-server
  - lesson-known-issues-vi-pusher
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/controller/ApplicationController.java
  - vi-assets/vi-billcenter-master/config.prod/application.yml
---

# vi-billcenter 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-billcenter-master`，2026-10-15。合音 Topic 与质检 URL 见 `10-lessons/pitfalls/doc-code-conflicts.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 各 Consumer 异常也返回 SUCCESS。取连接超时、数据库异常、质检请求失败都不会让 RocketMQ 重投。`RecordService` 里有延时队列的 TODO，没有实现，这类消息直接丢掉。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java`、`vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/BillReceiver.java`、`vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java` | 短暂的数据库或下游故障会造成话单或合音消息永久丢失，只能靠日志补偿。 | 无条件 ACK 是为了避免毒消息堵队列。改成失败重投之前，要先有幂等（见第 6 条）。不能把 ACK 删掉让消费线程抛异常退出。 |
| 2 | `isPickUP=true` 只等合音。合音一直不到，或 `code=1` 合音失败时，话单停在 `bill_new`，质检状态保持未发起，不移表。仓库内没有清理任务。谁补偿，待确认。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java` | `bill_new` 会堆积接通但没有质检的话单。 | 等合音是因为接通录音还没就绪，不能在通话结束消息到达时立刻质检。删等待会让没有录音 URL 的话单发起质检。应加超时兜底，而不是删等待。 |
| 3 | 合音先于话单到达时，60 秒重试 3 次，之后只写 recover 日志。是否有人工补偿流程，待确认。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordDelayReceiver.java` | 话单晚到超过约 3 分钟，这通合音结果只留在日志里。 | 延时重试是应对 vi-as 话单尚未插入的兜底。删延时队列会让“合音先到”的通话全部丢失。 |
| 4 | `finish_state==0` 时延时 5 秒，但到期后不再看状态，直接移表。5 秒内 vi-as 还没更新的话，历史表里就是未完成状态。`BillDelayReceiver` 要求 billId 纯数字，非数字 ID 被静默跳过，话单永远留在 `bill_new`。ID 是否一定是数字，待确认。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/BillDelayReceiver.java` | 未接通话单可能带着未完成状态进历史表；非数字 ID 的未接通话单不移表。 | 5 秒延迟是给 vi-as 补写结束原因的窗口。删延迟会把更早的中间状态移走。数字判断删掉之前要确认 ID 生成规则，避免把非法 ID 写进分表。 |
| 5 | vi-as 对“只支持 G.729”的接通话单发 `isPickUP=false`。本服务按未接通移表，不做质检。若合音模块仍发了消息，此时 `bill_new` 已没有这条，会走 3 次延时后写入 recover 日志。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/BillReceiver.java` | G.729 接通通话没有质检，日志里还有合音找不到话单的噪声。 | 按 isPickUP 分支是接通/未接通的分界。删掉 false 分支会让未接通话单也去等合音，`bill_new` 不再移走。 |
| 6 | `getRecording` 不看质检状态。重复的合音消息会重复创建质检任务。回调也没有去重。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java` | vi-merge 和 offline-merge 若都发到能被本服务消费的 topic，同一通话会建两次质检。 | 不去重是缺陷，不是“允许多次质检”的产品规则。修的时候加状态判断，不要删创建质检的主路径。 |
| 7 | `getError` 读 `param.get("id")`，合音消息只有 `callId` 没有 `id`，必然 NPE。NPE 被 Receiver 捕获。失败计数在 NPE 之前已经加上。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java` | 合音失败分支多一条异常栈，失败原因没有按 callId 记下来。 | 失败分支是 `code!=0` 的处理入口。删掉会让合音失败没有任何业务日志。应改成读 callId。 |
| 8 | 回调查不到话单也返回成功，qa-server 不会重推。移表之后的重复回调是正常情况；数据库抖动导致的查不到会丢结果。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/controller/ApplicationController.java` | 质检结果在数据库抖动时永久丢失。 | 查不到就成功是为了让“已经移表”的重复回调停下来，避免 qa-server 无限重推。改成失败返回之前要区分“已移表”和“数据库异常”。 |
| 9 | 回调先改数据库再发 MQ。MQ 失败返回非成功码，qa-server 会重推，状态会被再写一次。若发送实际成功但客户端超时，vi-pusher 会收到两次，客户可能被推两次。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckCallbackService.java` | 推送重复。客户侧要按 listId 或 taskId 幂等，约定待确认。 | 先落库是为了状态不丢。改成先发 MQ 会在进程崩溃时出现“客户已收到但库仍是未推送”。不要删状态更新。 |
| 10 | 质检请求失败不自动重试，只能调重建接口。该接口不传时间时，窗口硬编码在 2026-07-02 到 2026-07-05，调用必须显式传时间。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckRecreateService.java`、`vi-assets/vi-billcenter-master/src/main/java/com/cmic/controller/ApplicationController.java` | 不带时间的重建几乎扫不到当天的失败话单。 | 硬编码窗口是防误扫全表。删掉时间条件会让一次调用扫出大量历史失败单并打向 qa-server。应改默认值为“最近窗口”，保留必传时间。 |
| 11 | 流程 ID、两个 prompt ID、ragTopK、rtType 写死，所有客户共用。不读客户的 script、回调 URL、ability、状态、过期时间和分钟额度。是否由 qa-server 校验，待确认。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java` | 未开通或超额的客户仍会发起质检；换流程必须改代码发版。 | 这些常量是当前专线质检实际走到的流程。删掉会让创建请求缺少 customizedDetectionId，qa-server 直接拒绝。要改成按客户配置，而不是删字段。 |
| 12 | `/vibillcenter/test` 无鉴权，会用写死的企业/客户标识真实调用 qa-server。`/vibillcenter/task/recreate` 同样无鉴权。生产是否有网关，待确认。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/controller/ApplicationController.java` | 能访问端口的人可以制造质检任务或批量重放失败话单。 | 重建接口是第 10 条所说的唯一补偿入口。删掉之前要有替代的运维手段。测试接口可以从生产路由去掉。 |
| 13 | 质检请求以 INFO 打印请求头（含签名材料）和请求体（随路数据里有号码）。回调以 INFO 打印全文，可能含转写、摘要和号码。本文不摘录。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java` | 日志采集保存号码、签名和转写文本。 | 删掉整个请求构造日志会让联调没有现场。应脱敏后保留状态码和话单 ID。 |
| 14 | 移表先 INSERT 历史表再 DELETE `bill_new`，不是一个事务。中途崩溃会两边都有。再次移表会主键冲突。当日分表不存在时 INSERT 失败，话单留在 `bill_new`。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/dao/BillDao.java` | 历史表和在途表重复，或话单永远不移走。 | 先插入再删除是为了避免先删后插失败导致话单消失。倒序或删掉插入都会丢话单。分表要靠建表任务提前存在，不能靠删 INSERT 绕过。 |
| 15 | 回写录音时长失败后仍然发起质检。`file_duration` 等字段可能没落库。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/RecordService.java` | 质检任务有录音 URL，话单上的时长仍是旧值，计费或展示可能不一致。 | 发起质检是主路径。因为更新失败就删掉质检调用，录音已经在存储上却永远不会质检。应把更新失败计入补偿，而不是跳过质检。 |
| 16 | 死代码来自中间号话单中心改造：未接线的话单/录音发送、历史查询、混淆和摘要工具、未使用的服务发现和指标。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/BillReceiver.java` | 不要把这些 topic 当成当前对客推送路径。当前生效的是质检结果 topic。 | 删除前查 `09-decisions/`。正在消费的通话消息和合音消息不能一起删。 |
| 17 | 默认 dev profile 的 manifest 主类名与真实启动类不一致，`java -jar` 会找不到主类。prod profile 由插件探测主类。pom 里 httpclient 依赖声明了两次。 | `vi-assets/vi-billcenter-master/pom.xml` | 用默认 profile 打的瘦 jar 起不来。prod 包不受这条影响。 | 改主类名时要改 manifest，不要为了能启动把 `ViBillcenterApplication` 删掉或改包名。 |
| 18 | 企业/客户等配置缓存是 public static HashMap，reload 时整体替换，没有 volatile。`init` 会先清空再加载，启动有短空窗。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/utils/CustomerTabManager.java` | 极小概率读到旧引用；启动瞬间查不到企业配置。 | 替换引用是为了避免边读边改。改成就地 clear 会让请求读到空表，把话单判成无配置。 |
| 19 | `CommonResult.isSuccess()` 用 `==` 比较字符串，当前没有调用方。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/pojo/vo/CommonResult.java` | 一旦有人调用，字面量以外的 `"0000"` 会被当成失败。 | 成功码 `"0000"` 是和 qa-server 重推约定绑在一起的。不要删常量。比较方法在接入前改成值相等。 |
| 20 | RestTemplate 关闭了主机名校验。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/config/RestTemplateConfig.java` | 到 qa-server 代理的 TLS 不校验主机名，存在被冒充的风险。 | 这是为了适配对端证书。直接删掉客户端会让质检创建全部失败。应收紧校验并确认证书，而不是删 HTTP 客户端。 |
| 21 | RocketMQ 自动配置相关 Bean 被标成基础设施角色，避免被 BeanPostProcessor 过早初始化。 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/config/RocketMQBeanFactoryPostProcessor.java` | 删掉之后启动期会出现过早初始化告警，严重时消费者起不来。 | 这是有意的启动顺序修复。看起来多余，删了会把已经避开的初始化问题带回来。 |
