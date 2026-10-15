---
id: lesson-known-issues-vi-as
title: vi-as 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-as
  - module-vi-as
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/AuthConfigService.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/util/MyHTTPServer.java
---

# vi-as 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-as-master`，2026-10-15。管理接口见 `10-lessons/pitfalls/security-risks.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 录音没有配置开关。`setRecordParams` 把 `needRecord` 固定为 true，不读客户 `ability` / `ability_status`。 | `vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java` | 质检能力关闭的客户仍会走录音参数。能力字段由谁消费待确认。 | 这是当前“默认录音”的生效点。删掉赋值会让录音参数为空，通话侧直接失去录音，并不是按客户开关关闭。 |
| 2 | 前缀表重建用自然序 `new TreeMap<>()`，覆盖了字段上按长度降序的 comparator。短前缀会先命中，不是最长前缀。原 comparator 只比长度，等长 key 会互相覆盖。缓存 HashMap 整体替换且非 volatile。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/AuthConfigService.java` | 号段/前缀可能配错企业。是否已在线上错配、是否存在等长前缀，待确认。 | 两种实现都有缺陷。删掉重建逻辑会停掉 ETCD 版本触发的号码重载，新报备号码不生效。应改比较器，而不是删刷新。 |
| 3 | 主流程 `BaseController.insertAndMonitor` 在 `mediaSession == null` 时 `ms_id`/`storeId` 写 0，有会话时写媒体 serverId/storeId。`B2BWithBillController.insertAndMonitor` 恒写 0。`ErrorController`、`NewTwoLegController` 使用媒体会话上的 id。 | `vi-assets/vi-as-master/src/main/java/com/cmic/bizcontroller/controllerImpl/BaseController.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/bizcontroller/controllerImpl/B2BWithBillController.java` | B2B 话单和媒体创建失败的话单丢失媒体/存储定位。下游是否只靠 billId 找录音，待确认。 | 0 是“没有媒体会话”的占位，不是多余字段。改成不写或删列，历史查询和补偿会对不上。 |
| 4 | 固话右腿把省份写进 `callInProvinceId`，同方法手机分支写的是 `callOutProvinceId`。 | `vi-assets/vi-as-master/src/main/java/com/cmic/BillNew.java` | 固话被叫省份可能记到主叫省份字段，报表口径偏。 | 这是写错字段，不是死赋值。删掉整段固话分支会让固话失去区号和类型，比写错更差。 |
| 5 | ICSCF 缓存构建时把 province 设成字面量 `"province"`。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/ChinaMobileIcscfCacheService.java` | 依赖省份字段的 ICSCF 选择会失真。 | 缓存本身是出呼选路的数据源。删缓存会让 ICSCF 查询每次打库或直接失败。应改赋值来源。 |
| 6 | 号段 `reload` 不清理旧条目；`getAreaCodeFromPhoneSegment` 可能空指针。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/PhoneSegmentCacheService.java` | 号段变更后旧映射残留；缺号段时呼叫线程可能 NPE。 | 重载是配置生效路径。删重载等于号段永远停在启动快照。NPE 要补空判断，不要删查询。 |
| 7 | `postJsonWithRetry` 只在错误信息含 `SocketTimeoutException` 时重发。其它错误不重发，但循环里仍会调用 `failHook`，最多两次。 | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` | 非超时的媒体失败不会重试，失败钩子可能执行两次（停媒体、计数被加两次）。 | 超时重发是媒体抖动时的唯一自动补偿。删掉重试后，短暂超时会直接丢 pickup/stop/update。修钩子次数时应保留超时重发。 |
| 8 | `createMediaSession` 失败后换节点再试。`getRecordHost` 返回 null 时有 NPE 风险。主流程媒体不可用时呼叫仍接续、不录音。是否为预期，待确认。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/MediaService.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` | 媒体故障时通话还能通，但没有录音和后续质检素材。 | 换节点是媒体单点故障的兜底。删掉会把一台 vi-media 异常放大成整机无法建媒体会话。 |
| 9 | `initAuditSendJson` 在策略查询路径上会组两次包。策略超时/异常从回调失败分支看是默认放行，待确认。`blackCallerIntercept` 存在，主流程未调用。 | `vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java` | 策略服务不可用时呼叫可能放行（待确认）。黑名单方法目前不生效。 | 失败放行是接通优先的兜底，删掉失败分支会让策略超时直接打断呼叫。黑名单方法删除前要确认没有外部反射或即将启用的配置。 |
| 10 | 发给 vi-billcenter 的 MQ 失败只写 `bill_fail`，源码里没有补发。写入 `bill_fail.company_id` 的值实际是 customerId。是否有外部扫描补发，待确认。 | `vi-assets/vi-as-master/src/main/java/com/cmic/dao/BillDao.java` | 话单中心收不到通话结束通知，质检链路停在话单插入之后。补偿任务若按 company_id 过滤会找错。 | `bill_fail` 是唯一的失败落点。删插入会让 MQ 失败完全无痕，补发也无法做。 |
| 11 | 话单入库超时最多重试约 2 分钟，之后 SQL 进 dblog，需要外部回放（工具是否存在待确认）。`startMode=1` 时跳过中间件，话单只走 dblog，不发 MQ。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/DbAsyncService.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/start/Application.java` | 数据库抖动后话单只留在日志里。startMode=1 的节点不会通知话单中心。生产 startMode 实际值待确认。 | dblog 是入库失败的最后兜底。删日志落盘等于数据库超时即丢话单。startMode 分支是降级运行开关，不能当废弃配置删。 |
| 12 | `BillRocketMQService`、`EventRocketMQService` 继承 `BaseRocketMQService`，本仓库没有这个类。按当前源码无法单独编译。来自哪个依赖，待确认。 | `vi-assets/vi-as-master/src/main/java/com/cmic/service/BillRocketMQService.java` | 构建依赖未入库的基类；生产者 group/重试配置也待确认。 | 删继承改成空实现会让发话单中心的 MQ 消失。应补齐基类或依赖，而不是删发送服务。 |
| 13 | `Application.main` 不调用 `initRedis`。`SessionLimitUtil` 里与本次呼叫相关的生效路径是 `auditLimitIntercept`（处理策略结果）。阈值相关逻辑有硬编码 -1。 | `vi-assets/vi-as-master/src/main/java/com/cmic/start/Application.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/util/SessionLimitUtil.java` | 不要假设 AS 用 Redis 做会话限流。策略拦截仍走 `auditLimitIntercept`。 | 删 `auditLimitIntercept` 会把策略服务返回的拦截/放音结果丢掉，呼叫不再执行管控。Redis 初始化方法可能被测试或后续模式调用，删除前要全仓库搜引用。 |
| 14 | `/control` 可反射改 `DevHelp` 静态字段，`/changeStartMode` 切换运行模式，`/ops/pause` 可摘流量。源码无鉴权。 | `vi-assets/vi-as-master/src/main/java/com/cmic/util/MyHTTPServer.java` | 能访问该端口的调用方可以改运行时开关、摘流量。是否仅内网、是否有网关鉴权，待确认。 | 这些是运维摘流量和切 startMode 的入口。删接口前要确认现网排障是否还在用。加固应加鉴权，而不是在没有替代手段时直接删。 |
| 15 | `RocketMQConfig.init` 用标准输出打印每个配置节点的正文，节点里可以带访问密钥。 | `vi-assets/vi-as-master/src/main/java/com/cmic/config/RocketMQConfig.java` | 进程标准输出、采集到的启动日志会带上凭据。不要把打印内容抄进文档或工单。 | 删掉整个 `init` 会让 MQ 配置不加载。应停止打印节点正文，保留解析。 |
| 16 | `MediaHandler` 异常时可能不回响应。`OpsHandler` 用字符长度而不是字节长度设置 Content-Length，中文响应会截断。 | `vi-assets/vi-as-master/src/main/java/com/cmic/http/handler/MediaHandler.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/http/handler/OpsHandler.java` | vi-media 回调可能一直等到自己的超时；运维接口中文说明不可读。 | 两个 Handler 是媒体回调和运维口的入口。删 Handler 会让 `/media` 或运维请求没有处理函数。 |
| 17 | dev 配置的库路径指向缺失文件且没有 rocketmq 配置，只能 startMode=1。prod 的 `mssSipStackPath` 在代码中未见消费。`asStackFilePath` 会被 `DtmfEngine.init` 覆盖。ETCD 缺少 `/config/max_sessions` 时 `DevHelp.init` 会 NPE。 | `vi-assets/vi-as-master/src/main/java/com/cmic/start/Application.java` | 本地按 dev 配置启动会绕过 MQ。生产缺 ETCD 键时进程起不来。 | ETCD 键是会话上限的来源，删读取会让保护逻辑使用未初始化值。dev 配置缺失不要用“删配置项”掩盖，否则 prod 模板被一起改掉。 |
| 18 | `isBizStateActive` 注释写三级校验，实现只校验企业与客户，不校验渠道。 | `vi-assets/vi-as-master/src/main/java/com/cmic/business/ViConf.java` | 渠道停用不会单独拦截呼叫。 | 这是接续前的账户判断。删方法会让 `ControllerAgent` 失去企业/客户状态检查，停用客户仍能继续通话。 |
| 19 | FinishState 里 5xx 映射到路由失败的分支不可达。 | `vi-assets/vi-as-master/src/main/java/com/cmic/state/FinishState.java` | 路由失败不会走这条映射，话单结束原因可能落在别的状态。 | 状态映射被话单和监控使用。删整个类或大段 case 会改变 `finish_state` 口径。不可达分支应在确认没有任何反射/未来 SIP 码会进入之后再删。 |
| 20 | 遗留：`com/cmic/event` 与 `com/cmic/bizcontroller/event` 两套事件机、vod/vrbt/obv 插件（视频插播和视频彩铃开关未见置位）、最大通话时长服务（时长未见赋值）、发往 pusher 的事件 MQ、号码 DAO 里的旧表访问，以及两腿控制器里的大段注释。 | `vi-assets/vi-as-master/src/main/java/com/cmic/event/event/SipEvent.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/bizcontroller/event/SipEvent.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/service/EventRocketMQService.java` | 这些路径当前主流程不走。是否计划由策略下发视频/时长，或事件 MQ 已废弃，待确认。 | 删除前要查 `09-decisions/` 和本目录。`EventRocketMQService` 仍继承发送基类，删掉等于关闭通往 vi-pusher 的通话事件（当前未初始化，但是回切点）。 |
| 21 | 去区号只在剩余部分以 `7001` 开头时做（配合 xproxy 的 7001 前缀入口）。700 被叫转发时截掉前 5 位路由前缀得到真实被叫。`125838` / `125836` 截 6 位。国际被叫保留原样。 | `vi-assets/vi-as-master/src/main/java/com/cmic/util/SipUtil.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/DtmfEngine.java` | 改错一位，被叫会把路由前缀当成号码送出，或把真实 700 号码截短。与 xproxy 的改写约定待确认。 | 这是号码规范化，不是调试残留。删前缀处理会让 7001 入口的被叫无法出局。 |
| 22 | 初始 INVITE 重传触发 `TransactionAlreadyExistsException` 时直接丢弃，依赖 SIP 栈重发已经生成的响应。 | `vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/SipEngine.java` | 若改成再次建事务，会把重传 INVITE 当成新呼叫，话单和媒体会话重复。 | 忽略该异常是重传去重。删掉 catch 会让重传变成未处理异常或第二通呼叫。 |
| 23 | 会话数打满时回 500，To-tag 固定为 `cmic`，方便上游识别是 AS 主动拒绝。 | `vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/SipEngine.java` | 过载时呼叫被拒，上游可以据此与下游失败区分。 | 固定 tag 是过载信号，不是写死的测试数据。删掉保护会让会话无上限增长。 |
| 24 | `AuditStrategyQuery`、`MediaManager`、`FinishState` 注释和 `ConfigCache` 等测试入口含号码或地址。不应外传，本文不转录。 | `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` | 源码扩散会带出未脱敏样例。 | 删 `main` 不影响线上进程，但要和真正的业务方法分开删。不要为了“清理样例”整文件删除 `MediaManager`。 |
