---
id: module-vi-as
title: vi-as 代码深度解析（SIP B2BUA）
domain: mid_number
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-as]
source_of_truth: [vi-assets/vi-as-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-as-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-as.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-as（vi-as-master）仓库分析报告

> 分析对象：`/workspace/vi-assets/vi-as-master`（Java，`src/main/java` 下 351 个 `.java` 文件，另有 1 个测试类）。
> 代码引用格式：`相对路径:类.方法`，相对路径以 `src/main/java/` 为根（配置/脚本以仓库根为根）。
> 本报告不包含任何 IP、主机名、口令、AK/SK、连接串、真实号码；号码示例统一使用 `700xxxx0001`、`138xxxx0001` 这类虚构形式。
> 未能从代码完全确认的结论标注"待确认"。

---

## 1. 定位

vi-as 是专线质检链路中的 **SIP 应用服务器（B2BUA）**：位于 xproxy（7001 前缀统一入口）之后，接收 700 号码专线呼叫（以及 GZH 公众号/外呼场景的呼叫）的初始 INVITE，完成以下职责：

1. 依据被叫/主叫识别业务号码（bizNum），在内存缓存（源自 MySQL `user_qc` 库）中查询 **号码 → 企业(company) → 客户(customer) → 渠道(channel)** 的授权与开通状态（即"专线质检配置"）；
2. 可选调用策略服务 `com.cmic.vi.strategy`（HTTP `/strategy`）做话务管控（拦截/放音）；
3. 生成 18 位雪花 billId 作为会话 ID，向 MySQL `vi_bill.bill_new` **插入基础话单**，呼叫结束后回填时间、状态、媒体信息；
4. 通过 HTTP 调用 **vi-media**（`/call/session/create|update|pickup|stop`），改写两侧 SDP 把 RTP 锚定到 vi-media，实现**通话录音**；
5. 呼叫结束、话单更新成功后，通过 **RocketMQ** topic `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL` 通知 **vi-bill（vi-billcenter）**，后续由 vi-bill 驱动推送与质检。

它本身不做 ASR / 质检判定，只负责"信令接续 + 话单 + 录音锚定 + 通知话单中心"。

---

## 2. 技术栈

| 项 | 内容 | 位置 |
| --- | --- | --- |
| 语言/版本 | Java 21（`maven.compiler.source/target`） | `pom.xml` |
| 构建 | Maven，groupId `com.cmic`，artifactId `vi-as`；profile `dev`（默认）打瘦 jar + `lib/` 依赖目录、`config/` 在 classpath；profile `prod` 用 assembly 打 `jar-with-dependencies`，finalName `vi-as` | `pom.xml` |
| SIP 栈 | JAIN-SIP（gov.nist 实现，使用 Netty UDP MessageProcessor，信令日志通过 `NettyUDPMessageChannel.signalLogger` 钩子），`AUTOMATIC_DIALOG_SUPPORT=off` 手工管理 Dialog | `config/sip-stack-call.properties`、`com/cmic/sip/engine/DtmfEngine.java:DtmfEngine.init` |
| 内部依赖 | `ziyan-util`、`xdiscovery`（ETCD 服务发现 `com.discovery.RegistryService`）、`dcc-conf`（`com.dcc.config` DccConfigDb / BlackWhiteService / BaseProdNum）、`sharding-database-table`（`com.common.dao.db` Db/DbManager）、`sdpparser` | `pom.xml` |
| 三方依赖 | druid、mysql-connector 8.0.33、rocketmq-client-java 5.0.8（RocketMQ 5 gRPC 客户端）、jedis 5.1.0、fastjson、jackson、json-lib、log4j2（异步 + disruptor）、lombok、prometheus simpleclient、vnumber-encrypt（携程号码加密）、taobao-sdk、commons-cli、guava | `pom.xml` |
| HTTP 服务端 | JDK 内置 `com.sun.net.httpserver.HttpServer`，5 线程，backlog 3 | `com/cmic/util/MyHTTPServer.java:MyHTTPServer.<init>` |
| HTTP 客户端 | JDK11 `java.net.http.HttpClient`（HTTP/2，异步）+ 自研 `HttpAgent` | `com/cmic/business/query/AsyncJava11HttpClient.java`、`com/cmic/recording/MediaManager.java` |
| 启动入口 | `com.cmic.start.Application.main` | `com/cmic/start/Application.java:Application.main` |
| 监听端口 | SIP UDP `5080`（配置项 `asListen`，dev/prod 相同）；HTTP 管理/回调 `5668`（dev）/ `5287`（prod）（配置项 `httpPort`）；JMX Prometheus exporter `8588` | `config/application-config.xml`、`config.prod/application-config.xml`、`bin/start.sh` |
| 部署脚本 | `bin/start.sh`（dev：`-Xms1g -Xmx1g`）、`bin.prod/start.sh`（prod：`-Xms21g -Xmx21g`），二者都 `-javaagent:jmx_prometheus_javaagent-0.12.0.jar=8588:config.yaml`、ZGC、`-Dlog4j.configurationFile=config/log4j2.xml`、异步 LoggerContextSelector，`java -jar vi-as*.jar`；`config.yaml` 为 JMX exporter 规则 | `bin/`、`bin.prod/` |
| 日志 | log4j2，输出到 `/var/log/vi-as/`，分 all / signaling（logger `aslog`）/ analyse / db（logger `dblog`）/ debug / info / warn / error | `config/log4j2.xml`、`config.prod/log4j2.xml` |

### 2.1 启动参数（commons-cli）

| 参数 | 作用 | 位置 |
| --- | --- | --- |
| `-config` | 指定 application-config.xml 路径 | `com/cmic/start/Application.java:Application.main` |
| `--mediarecordhost` | 强制指定 vi-media 地址（构造 id=9999 的伪节点，绕过 ETCD 发现） | `com/cmic/service/MyRegistryService.java:MyRegistryService.getRecordHost` |
| `--force_config` | 强制配置（待确认具体语义） | `Application.main` |
| `--delayStart` | 延迟启动 | `Application.main` |

### 2.2 启动顺序（`Application.main`）

1. 解析参数 → `AppConfig.init`（读 application-config.xml）；
2. `initSipStack` → `DtmfEngine.init`（**硬编码**读取 `config/sip-stack-call.properties`）；
3. **仅当 `startMode == 0`**：`initDb`（BillDB / UserQcDB）→ `AuthConfigService.init`（加载号码/企业/客户/渠道缓存）→ `initEtcd`（`MyRegistryService.init`）→ `initRocketMQ`（只初始化 `BillRocketMQService`，`EventRocketMQService` 初始化被注释）；
4. `BillIdService.init(location, serverId)`（雪花 ID）；
5. 启动 `MyHTTPServer(httpIp, httpPort)`；
6. `MediaManager.init("http://httpIp:httpPort")`，媒体回调地址为 `.../media`；
7. 注册 `Signal("INT")` → `GracefulDownHandler`；
8. 读取工作目录 `version` 文件记录版本；
9. 每 10s 执行 `auditSipEngine` 导出 SIP UDP 队列长度指标。

`initRedis()`（读 `config/redis.xml`）**存在但从未被调用**。

### 2.3 环境差异（dev = `config/`，prod = `config.prod/`）

| 项 | dev | prod |
| --- | --- | --- |
| application-config.xml | 明文值；`startMode=1`（有话单日志兜底，不连 DB/ETCD/MQ）；`proxyMode=1` | Jinja `{{...}}` 模板占位（location、id、imsip、ip、etcd1..3、slb、proxy_mode 默认 0、vi_as_httpproxy_app_name、startMode） |
| httpPort | 5668 | 5287 |
| billConfigPath / confDbConfigPath | 指向 `mid_bill.yaml` / `dcc_conf.yaml`，**目录中不存在** | `config/vi_bill.yaml`（库 `vi_bill`，单数据源 ds_0，不分片）/ `config/vi_conf.yaml`（库 `user_qc`） |
| rocketmq.xml | **不存在** | 存在（endpoints / ak / sk 占位） |
| sip-stack-call.properties | THREAD_POOL_SIZE=16 | THREAD_POOL_SIZE=128 |
| 其它 | — | 多了未知配置项 `mssSipStackPath`，`AppConfig.init` 仅打印错误日志，不生效 |
| JVM | 1g 堆 | 21g 堆 |

`sip-stack-mrf.properties` 在两套配置中都存在，但源码中**无引用**（遗留）。

---

## 3. 代码结构

| 包（`com.cmic.` 下） | 文件数 | 职责 |
| --- | --- | --- |
| `start` | 1 | `Application` 启动入口 |
| （根包） | 4 | `BillNew`（话单内存模型）、`GracefulDownHandler`（优雅下线）等 |
| `config` | 6 | `AppConfig`（主配置）、`ConstantConf`（ETCD key、服务名、呼叫状态常量）、`RocketMQConfig`、`RedisConfig`、`AbnormalBillAnalyserConfig`（异常话单分析开关） |
| `sip/engine` | 2 | `SipEngine`（抽象 SipListener，请求/响应/Dialog 终止分发、会话数保护、清理）、`DtmfEngine`（具体引擎，号码解析与 Controller 选择） |
| `sip/controller/base` | 3 | `Controller`（会话基类、超时、hook）、`TwoLegController`（双腿绑定）、`ControllerManager`（会话表、定时清理） |
| `sip/leg`、`sip/event`、`sip/controller/event`、`sip/info`、`sip/param`、`sip/header/historyinfo` | 15 | Leg（LeftLeg/RightLeg/SzLeftLeg）、SIP 事件包装、History-Info 解析 |
| `bizcontroller` | 7 | `ControllerAgent`（鉴权后编排：插话单→策略查询→启动具体 Controller）、`ControllerFactory`（反射创建）、`VideoBillHandler` 等 |
| `bizcontroller/controllerImpl` | 6 | `BaseController`（正常录音质检流程）、`B2BWithBillController`（startMode=1，有话单无录音）、`B2BWithoutBillController`（纯转发不出话单）、`ErrorController`（拦截放音后挂断）、`RejectController`（未授权 603）、`NewTwoLegController`（公共父类） |
| `bizcontroller/controllerImpl/vod/**`、`videooutbound`、`earlyvideo` | 36 | 视频插播（VOD）、视频彩铃（VRBT）、视频外呼（OBV）插件体系；**主流程中开关从未被置位，实际不生效** |
| `bizcontroller/callInfo` | 3 | `CallSession`（200/180/181 事件处理、接通时间、最大通话时长任务）、CallRecorder 等 |
| `bizcontroller/feature` | 2 | `CallStateManager`、`StreamManager`（流开始时间） |
| `bizcontroller/event`、`event/**` | 30 | 早期事件/状态机实现（`ControllerMachine`、`BillHandler`、`EarlyMediaHandler` 等），与主流程**并行存在但无外部引用**（遗留） |
| `business` | 3 | `BusinessEvent`（呼叫参数、补全话单、策略查询）、`ViConf`（企业/客户/渠道配置视图） |
| `business/businessparam/**` | 8 | `CallParameter`（opType、放音码、录音参数、最大时长等）、Record 录音参数 |
| `business/query/**` | 11 | `AsyncJava11HttpClient`（策略查询 HTTP 客户端）、IDaaS、阿里/基础绑定查询（遗留） |
| `business/record` | 1 | 录音参数构建 |
| `recording/**` | 18 | `MediaManager`（vi-media HTTP 协议）、`MediaSession`（会话、SDP 改写、seq/回调）、`SdpSession`、媒体回调事件（DTMF_DONE / AUDIO_DONE / VIDEO_DONE / ENHANCE_DONE） |
| `service` | 22 | 配置缓存（`AuthConfigService`、`PhoneSegmentCacheService`、`ChinaMobileIcscfCacheService`）、ETCD（`MyRegistryService`）、DB 异步（`DbAsyncService`）、MQ（`BillRocketMQService`、`EventRocketMQService`、`BillCenterService`、`BussinessMsgService`）、媒体（`MediaService`）、`BillIdService`、`MaxCallDurationService`、`ScheduleService`、Prometheus（`PromExporterService`、`XmodePromExporterService`）、`PlayCodeService`、`TelBRegulator` 等 |
| `dao`、`dao/db` | 19 | `BillNewDo`（bill_new 映射）、`BillNewBuilder`（反射生成 SQL）、`UserQcDao`、`NumDao`、`BaseDB`/`BillDB`/`UserQcDB` 等数据源 |
| `state` | 8 | `PreciseCallStateBase`/`PreciseCallState`（精确话单状态机）、`FinishState`、`FinishType`、`DtmfPreciseCallState` |
| `extension` | 3 | `ExtensionManager`、`StandardExtension`（x-mode 扩展，选择 Controller 类型） |
| `http`、`http/handler`、`http/handlers`、`http/vo` | 20 | HTTP 管理与回调 Handler（/metrics /ops /media /hook /control /changeStartMode 等） |
| `keyaffinity` | 19 | 通用 KeyAffinityExecutor（同 key 串行、不同 key 并行），用于 DB 异步写 |
| `pojo` | 22 | DO/VO：`BizNumAuthDo`、`CompanyDo`、`CustomerDo`、`ChannelDo`、`BillAppendReasonEnum` 等 |
| `cache`、`manager`、`constant` | 10 | 缓存、管理器、媒体常量（`MediaConst`：放音时段/优先级/方向） |
| `signaling` | 2 | `Signaling`（信令日志与缓存，logger `aslog`） |
| `util` | 46 | `SipUtil`（号码/区号/路由）、`SdpUtil`/`SdpHelper`、`PhoneNumUtil`、`SessionLimitUtil`、`AbnormalBillAnalyser`、`AnalyseLog`、`DBLog`、`DevHelp`（运行时开关）、`MyHTTPServer`、`JedisClientPool` 等；另有若干无引用工具（`CaiNiaoUtil`、`XieChengEncryptHelp` 等） |
| `org/example/Main` | 1 | 示例代码，无业务意义 |
| `src/test/java` | 1 | `TestBlackListNumberLength` |

---

## 4. 核心业务流程

### 4.1 初始 INVITE 接入与 Controller 选择

| 步骤 | 做什么 | 代码位置 | 失败处理/重试/超时 |
| --- | --- | --- | --- |
| 1 | OPTIONS：在线（未 pause）回 200，下线不回 | `com/cmic/sip/engine/SipEngine.java:SipEngine.processRequest` | pause 后不回应，使上游摘除节点 |
| 2 | 初始 INVITE：`getNewServerTransaction`；抛 `TransactionAlreadyExistsException` 视为重传，直接忽略 | `SipEngine.processRequest` | 重传计数指标 `as_invite_retran*_total` |
| 3 | 会话数保护：`ControllerManager` 会话数 > `DevHelp.maxSession`（默认 15000，ETCD `/config/max_sessions` 可调）回 `500 cmic session block`，To-tag=`cmic` | `SipEngine.shouldSessionBlock` | 指标 `call_reject_due_to_too_much` |
| 4 | 号码解析：主叫取 From，缺失取 PAI；`GetPhoneNumber` 去 `+`/`86`/`0`，若去区号后以 `7001` 开头则去掉区号；被叫取 Request-URI | `com/cmic/sip/engine/DtmfEngine.java:DtmfEngine.doBuildController`、`com/cmic/util/SipUtil.java:SipUtil.GetPhoneNumber` | — |
| 5 | 业务号码 bizNum：若是 GZH 主叫侧（Route 匹配 `.*\.gzh\.*[a-z]*\.chinamobile\.com.*` 且含 `orig`）**或** 被叫以 `700` 开头 → bizNum = 主叫；否则 bizNum = 被叫；`125838`/`125836` 前缀截为 6 位 | `DtmfEngine.doBuildController` | 待确认：被叫 700 时 bizNum 取主叫的业务含义（代码如此） |
| 6 | `startMode == 2` → 直接 `B2BWithoutBillController`（不出话单） | `DtmfEngine.doBuildController` | — |
| 7 | 查授权 `AuthConfigService.getCallerNumAuthDo(bizNum)`：先精确匹配，再前缀匹配 | `com/cmic/service/AuthConfigService.java:AuthConfigService.getCallerNumAuthDo` | startMode=1 时返回 company/customer = -1 的伪授权 |
| 8 | History-Info 带 `CMIC_MARK` 参数（GZH 实体二次路由）→ `B2BWithoutBillController` | `DtmfEngine.doBuildController` | — |
| 9 | 无授权：来自 GZH 域 → `B2BWithoutBillController`（指标 GZH-FORWARD）；否则 → `RejectController`（指标 700-REJECT） | `DtmfEngine.doBuildController` | RejectController 先回 180 再回 `603 caller no support here!` |
| 10 | 有授权：构造 `BillNew`（id=billId、bizNumber、areaCode、原始/处理后主被叫、`handleStartTime` 截断到秒、`setLeftLegInfo` 计算主叫省/区号） | `DtmfEngine.doBuildController`、`com/cmic/BillNew.java:BillNew.setLeftLegInfo` | — |
| 11 | `ExtensionManager.getExtension(1)` → `StandardExtension`（x-mode）`.buildController` → `ControllerAgent` | `com/cmic/extension/StandardExtension.java` | — |
| 12 | `ControllerManager.add`，记录信令日志，`controller.start` | `SipEngine.processRequest` | start 异常 → `cleanControllerWith500` |

### 4.2 ControllerAgent：配置判定 → 插话单 → 策略查询 → 启动

| 步骤 | 做什么 | 代码位置 | 失败处理/重试/超时 |
| --- | --- | --- | --- |
| 1 | 构建 `ViConf`：按授权的 companyId / customerId 从缓存取企业、客户、渠道（company.parentId） | `com/cmic/business/ViConf.java:ViConf.<init>` | companyId=-1 → `BlankAuth`；缓存缺企业可能 NPE（待确认） |
| 2 | 构建 `CallParameter`，`BusinessEvent.processCallParameter`：opType=1、supportedCodec、录音参数、forwardCallee | `com/cmic/business/BusinessEvent.java:BusinessEvent.processCallParameter` | — |
| 3 | `BusinessEvent.fillBillNew`：补 callee、`setRightLegInfo`、mobileId、channelId、companyId、customerId、appId、recordType=1、accountType | `BusinessEvent.fillBillNew` | — |
| 4 | `DbAsyncService.submitDbInsert` 异步插入 `bill_new` 初始行 | `com/cmic/service/DbAsyncService.java:DbAsyncService.submitDbInsert` | 见 4.6 |
| 5 | `!viConf.isBizStateActive()`（company.status≠1 或 customer.status≠1）→ `ErrorController`，finishState=99(OTHER)，appendReason=15（企业账户无效） | `com/cmic/bizcontroller/ControllerAgent.java:ControllerAgent.start` | — |
| 6 | `startMode != 0` → 立即 `startController` | `ControllerAgent.start` | — |
| 7 | `startMode == 0` → `BusinessEvent.doStrategyQuery`，异步回调中：opType=DISCONNECT → `ErrorController`；设置 appendReason；`startController` | `BusinessEvent.doStrategyQuery`、`ControllerAgent.start` | HTTP 超时 500ms，连接超时 300ms；策略服务未发现或 ETCD `/config/audit_strategy_allow=0` 时跳过直接放行（待确认：失败是否放行，见 §12） |
| 8 | `startController`：`ControllerFactory` 反射创建具体 Controller → `registerPlugins` → `controller.start` | `com/cmic/bizcontroller/ControllerFactory.java`、`ControllerAgent.startController` | — |

`StandardExtension` 映射：startMode=1 时 optype_0→`ErrorController`、optype_1→`B2BWithBillController`；否则 optype_0→`ErrorController`、optype_1→`BaseController`。

### 4.3 如何判断是否需要质检/录音

代码中**没有逐通话的"是否质检"开关**：

- `BusinessEvent.setRecordParams` **硬编码 `needRecord = true`**，并且 `needEarly=false`、`recordSuffix=wav`、`separateLeg=false`、`separateEarly=false`、`reverseLeg=false`；`Record.buildRecordParam` 生成 JSON `{recordSuffix, reverseLeg, separateLeg, isRecord, separateEarly, bindId, ...specialRecordParams}`，作为 `mergeConfig` 传给 vi-media。
- 因此"是否录音/质检"实际由以下条件决定：

| 条件 | 结果 | 代码位置 |
| --- | --- | --- |
| bizNum 在 `biz_num_auth` 中无记录 | Reject（603）或 GZH 纯转发，无话单、无录音 | `DtmfEngine.doBuildController` |
| 企业或客户 status≠1 | ErrorController：放音后挂断，有话单（finish_state=99, append_reason=15） | `ControllerAgent.start`、`ViConf.isBizStateActive` |
| 策略服务返回 opType=0 | ErrorController，finish_state=22（策略拦截） | `com/cmic/util/SessionLimitUtil.java:SessionLimitUtil.auditLimitIntercept` |
| 主叫 SDP 只支持 G729（无 PT 0/8、无 AMR/AMR-WB） | `onlySupportG729=true`，MediaSession inactive，不锚定媒体、不录音；通知 vi-bill 时 `isPickUP=false` | `com/cmic/service/MediaService.java:MediaService.createMediaSession` |
| vi-media 不可用（ETCD 无节点 / create 返回非 200，重试一次仍失败） | BaseController：MediaSession inactive，**呼叫继续但不录音**（待确认）；ErrorController：回 500 `media is not active!` | `MediaService.createMediaSession`、`ErrorController.start` |
| startMode=1 | `B2BWithBillController`，`new MediaSession(false)`，有话单不录音 | `com/cmic/bizcontroller/controllerImpl/B2BWithBillController.java` |
| startMode=2 | `B2BWithoutBillController`，无话单无录音 | `DtmfEngine.doBuildController` |
| 客户字段 `ability`/`ability_status`/`is_reuse_asr` 等 | vi-as 只加载，不参与录音判断；推测由 vi-bill / qa-server 使用（待确认） | `AuthConfigService` |

vi-bill 侧依据 MQ 中的 `isPickUP`（`callDurationS > 0 && !onlySupportG729`）判断是否接通、是否需要后续合音与质检（待确认，需看 vi-billcenter）。

### 4.4 BaseController 正常流程（录音 + 话单）

| 步骤 | 做什么 | 代码位置 | 失败处理/重试/超时 |
| --- | --- | --- | --- |
| 1 | 创建 `SzLeftLeg` 并绑定 | `com/cmic/bizcontroller/controllerImpl/BaseController.java:BaseController.start` | — |
| 2 | `MediaService.createMediaSession`：先临时 session 处理主叫 SDP 判断 G729；从 ETCD 取 vi-media 节点；调用 `/call/session/create`；失败换节点重试一次 | `MediaService.createMediaSession`、`com/cmic/recording/MediaManager.java:MediaManager.createMediaSession` | create 为同步 HTTP；失败 → inactive |
| 3 | `sessionReInit`、`recordInitialRequest`（UA 是否 iOS、是否视频、是否 precondition）、`handleStreamBUpdate` | `NewTwoLegController.sessionReInit` 等 | — |
| 4 | `B2buaHelper.createInviteRequest` + `linkRequest` 生成右腿 INVITE | `B2buaHelper` | — |
| 5 | 路由：被叫 700 → `SipUtil.setNextRoute4Dcc`（按 proxyMode 加代理 Route；带 xsipproxy `internal-as` tag；Via 序号写入 Route）；R-URI/To = `adaptTelB(forwardCallee)` → `tel:+86` + 11 位（国际号码保留）；非 700 → `setNextRoute4Gzh`（Route 取原请求最后一个 Route，即 S-CSCF；proxyMode=1 时加代理） | `SipUtil.setNextRoute4Dcc`、`SipUtil.setNextRoute4Gzh`、`BaseController.adaptTelB` | — |
| 6 | forwardCallee：原被叫 `700` 开头时，若 To 为国际号（`+` 非 `+86` / `00` 非 `0086`）用 To，否则 `pure11(原被叫).substring(5)`，即去掉 5 位前缀（如 `70014` + 11 位真实被叫，待确认前缀含义） | `BusinessEvent.processCallParameter` | — |
| 7 | SDP 改写：主叫 SDP 交给 MediaSession（`processLeftSDPV3`），向被叫发送的 SDP 改为 vi-media 的 IP/端口 | `com/cmic/recording/MediaSession.java:MediaSession.processLeftSDPV3/processRightSDPV3` | 支持编解码：telephone-event、amr、amr-wb、pcma、pcmu（ETCD `/config/supported_codec` 可调） |
| 8 | Contact（视频时带 video 能力）；`callForwardTime = handleStartTime`；创建 RightLeg；`pluginRecordStart`；`sendInitialInvite`（主叫号码改写过时加 `Mid-Orig-Msg` 头，值为原主叫 base64） | `BaseController.start` | — |
| 9 | 响应处理：180/183 标志；`preciseCallState.extractResponseMsgProperties*`；插件；`forwardResponse`（precondition 时设 `P-Early-Media: sendrecv`；180/183/200 设 Contact；带 SDP 时 `inflateSdp` → `MediaSession.commit` 调 `/call/session/update`）；180 + precondition 时早期放音 callerPlayUrl；200 → `processUponPickup` | `BaseController.processResponse` | — |
| 10 | `CallSession.consumeResponse`：200 → `callStartTime`、CALL_STATE_OK、`/call/session/pickup`、若 `maxDuration>0` 启动最大时长任务；180 → `ringTime`；181 → call_state=3 | `com/cmic/bizcontroller/callInfo/CallSession.java:CallSession.process200Invite/process180` | — |
| 11 | 请求处理：BYE → `callEndTime`；re-INVITE → call_state=4；左腿 ACK → call_state=6，并 InCall 放 calledPlayUrl | `BaseController.processRequest` | — |
| 12 | Dialog 终止：`SipEngine.processDialogTerminated` → `recordAndTerminated` → `BaseController.terminated`（`hasStartTerminated` 原子保护）：取消最大时长任务、`handleEndTime`、`/call/session/stop`、`handleEnd` | `SipEngine.processDialogTerminated`、`BaseController.terminated` | — |
| 13 | `handleEnd`：`PreciseCallStateBase.getFinalState` 得 finish_type / finish_state；状态为 21（无法识别）或 0（NEED_ASR）时延迟 300ms 再判；`doParameterFinishState` 覆盖；**msId=0、storeId=0**；`submitDbUpdate` | `BaseController.handleEnd/insertAndMonitor` | 见 §12 |
| 14 | 更新成功后 `BillCenterService.sendMsgToBillCenter` 发 RocketMQ | `DbAsyncService`、`com/cmic/service/BillCenterService.java` | 发送异常 → 写 `bill_fail` |

### 4.5 ErrorController（拦截/账户无效）流程

| 步骤 | 做什么 | 代码位置 |
| --- | --- | --- |
| 1 | `callerPlayUrl == NO_PLAY`：回 180，200ms 后回 `603 currency call overflow!` | `com/cmic/bizcontroller/controllerImpl/ErrorController.java:ErrorController.start` |
| 2 | 否则创建 MediaSession（失败重试一次，仍失败回 `500 media is not active!`） | `ErrorController.start` |
| 3 | 处理主叫 SDP，生成 183 SDP（可靠临时响应，必要时 `Require: precondition`，`P-Early-Media: sendrecv`），`commit` | `ErrorController.start` |
| 4 | 无 precondition：直接回 180（To-tag `cmic`），`/call/media/play` 早期放音 callerPlayUrl（EARLY、LOCAL、CALLER 方向），放音完成回调 → 左腿 BYE 或 487 | `ErrorController.start`、`MediaService.dccPlayMedia` |
| 5 | 有 precondition：等待 UPDATE，回 200（带协商后的 precondition SDP）后同上放音 | `ErrorController.processRequest` |
| 6 | 终止：`/call/session/stop`，计算 finish 状态，`msId = mediaSession.serverId`、`storeId = mediaSession.storeId`，`submitDbUpdate` | `ErrorController.terminated/handleEnd` |

放音码常量见 `com/cmic/service/PlayCodeService.java`（如 `global_100203` 策略拦截提示、`global_100201` 被叫限次、`global_100305` 呼转拦截、`NO_PLAY`），实际放音资源由 vi-media 解析（待确认）。

### 4.6 话单异步入库（DbAsyncService）

| 步骤 | 做什么 | 代码位置 | 失败处理/重试/超时 |
| --- | --- | --- | --- |
| 1 | 每个话单库一个 `KeyAffinityExecutor`（当前只注册 BillDB），并行度 40，队列 100；同 billId 串行，保证 insert 先于 update | `DbAsyncService.<init>` | — |
| 2 | 并发上限 = `floor(100 × dbOperRejectRatio)`（默认 0.7 → 70），ETCD `/config/db_oper_reject_ratio` 可调 | `DbAsyncService.refreshMaxConcurrency` | 超过则拒绝，SQL 文本写 dblog |
| 3 | executor 选择：billId 末位 % dbCount，失败取下一个 | `DbAsyncService` | — |
| 4 | Insert / Update SQL 由 `BillNewBuilder` 反射 `BillNewDo` 字段生成，null 字段跳过 | `com/cmic/dao/BillNewBuilder.java` | — |
| 5 | 执行：`BaseDB.executeCommandTimeout`，查询超时 10s；超时异常 → `DBRetryException`，其他 → `DbException` | `com/cmic/dao/db/BaseDB.java:BaseDB.executeCommandTimeout` | 仅 `DBRetryException` 重试，间隔 500ms，最长 2 分钟 |
| 6 | 最终失败/拒绝：SQL 与参数写入 logger `dblog`（格式 `[billId][xdtmf][dbId][asrLevel][sql][params]`），供人工/脚本补录 | `com/cmic/util/DBLog.java` | startMode=1 时**只写 dblog，不写库** |
| 7 | Update 成功：Prometheus 指标（`as_bill_finishstate` 等）→ `BillCenterService.sendMsgToBillCenter(billId, customerId, isPickUP, dbId)` | `DbAsyncService` | — |

### 4.7 通知 vi-bill（话单中心）

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 组装 JSON `{billId, isPickUP, dbId}` | `BillCenterService.sendMsgToBillCenter` | — |
| 2 | `BillRocketMQService.basicPublish`：topic `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL`、tag `BillPushMsg`、key = billId，发布线程池大小/队列由 `publishMQThreadPoolSize/QueueSize`（16/10000）及 ETCD `/config/publish_rocketmq_thread_pool` 控制 | `com/cmic/service/BillRocketMQService.java` | 异常 → 插入 `bill_fail`（reason、failcount），**无自动重发**（待确认是否由其他服务/脚本扫描补发） |

### 4.8 其它流程

- **最大通话时长**：`CallSession.process200Invite` 在 `CallParameter.maxDuration > 0` 时调用 `MaxCallDurationService.maxCallDurationControl`：到期放 `lastMinVoice`（若有），放完 1 分钟后双向 BYE，finish_state=19（SESSION_TIME_OUT）。**`maxDuration` 在代码中从未被赋值（默认 0），该功能实际不生效**（待确认是否由策略返回扩展）。
- **会话超时**：`Controller.CONTROLLER_OUT_TIME = 18 分钟`；每 15 分钟发 UPDATE 心跳；`ControllerManager` 每 60s 清理死会话（`onCleanOutEvent` → terminated → remove）。
- **优雅下线**：收到 SIGINT → `DtmfEngine.turnOnOff(false)`（停止回 OPTIONS）→ 每 5s 检查，会话数为 0 或第 30 次（约 150s）退出；第 20 次（约 100s）对所有会话发 BYE。`com/cmic/GracefulDownHandler.java:GracefulDownHandler.handle`
- **异常话单分析**：`AbnormalBillAnalyser` 对每个会话按 7 种 case 检测并写 analyse 日志，开关为 ETCD `/config/analyse_abnormal_bill/case1..7_switch`：case1 183(precondition) 后 6s 内主叫未发 UPDATE；case2 发出 INVITE 未收到 18x/200；case3 未收 100/183 直接 180/200；case4 接通后某方 re-INVITE；case5 信令间隔 > 6s；case6 初始 INVITE 的 PAI 与 From 不一致；case7 200 的 PAI 与 To 不一致。`com/cmic/util/AbnormalBillAnalyser.java:AbnormalBillAnalyser.analyse`
- **呼转拦截**：`SipEngine.processResponse` 中 18x 不满足 `isCallForwardAllow` → `doCallForwardBlock`（appendReason=1 呼转拦截，待确认放音码 `global_100305`）。
- **运行模式切换**：HTTP `/changeStartMode` → `DtmfEngine.switchStartMode`；`initStartMode != 0` 时不允许切到 0（因为 DB/ETCD/MQ 未初始化）。

---

## 5. 对外接口（入站）

### 5.1 SIP（UDP 5080）

| 协议 | 方法 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| SIP/UDP | OPTIONS | `SipEngine.processRequest` | — | 200（在线时） | 健康探测，pause 后不回 |
| SIP/UDP | INVITE（初始） | `SipEngine.processRequest` → `DtmfEngine.doBuildController` | From/PAI（主叫）、Request-URI（被叫，700 号码形如 `700xxxx0001` 或带前缀）、Route（GZH 判断）、History-Info（CMIC_MARK）、Via（ICSCF 区号）、P-Access-Network-Info、SDP | 180/183/200/603/500，向被叫侧发新 INVITE（`tel:+86...`） | B2BUA 入口 |
| SIP/UDP | ACK/BYE/CANCEL/PRACK/UPDATE/re-INVITE/INFO | `SipEngine.doRequest` → `controller.processRequest` | Dialog 内请求 | 透传或本地应答 | 由 Leg 管理事务 |
| SIP/UDP | 响应（1xx-6xx） | `SipEngine.processResponse` → `controller.processResponse` | Reason(Q.850)、SDP | 转发给对端腿 | 18x 呼转检查 |

### 5.2 HTTP（`httpPort`：dev 5668 / prod 5287）

所有 Handler 默认回 200；**均无鉴权**。

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | GET `/metrics` | `com/cmic/http/handler` 下 Metrics Handler | — | Prometheus 文本 | 业务指标 |
| HTTP | GET `/ops/status` | `com/cmic/http/handler/OpsHandler.java` | — | `{version, session, state}`（state 3=在线，2=下线） | 运维状态 |
| HTTP | `/ops/pause`、`/ops/resume` | `OpsHandler` | — | — | 暂停/恢复回 OPTIONS，用于摘流量 |
| HTTP | POST `/media` | `com/cmic/http/handler/MediaHandler.java` | `CallBackEvent{sessionId, event, code, message, seqId, data}`，event ∈ DTMF_DONE/AUDIO_DONE/VIDEO_DONE/ENHANCE_DONE | 200 | vi-media 回调；按 seqId 找 hook |
| HTTP | POST `/hook` | http handler | `{tid, cmd}` | — | 对指定会话执行 http hook（调试） |
| HTTP | POST `/forcemediacodec` | http handler | `{forceMediaCodec}` | — | `MediaSession.testSupportedCodec` |
| HTTP | POST `/control` | `com/cmic/http/handlers/ChangeCodeHandler.java` | 任意 `DevHelp` 静态字段名与值 | — | **反射修改任意运行时开关**；forceAsr/forceCodec 60 分钟后自动还原 |
| HTTP | POST `/changeStartMode` | http handlers | `{startMode: 0/1/2}` | `{code:"000000", desc}` | 运行模式切换 |
| HTTP | POST `/triggerSipStackAudit` | http handlers | `{sip_stack_audit_mins ≥ 20}` | — | 触发 SIP 栈审计 |
| HTTP | `/sms_callback` | http handlers | — | — | 仅打日志（遗留） |
| HTTP | `/v1/vod/play`、`/v1/vod/stop`、`/vod/complete`、`/v1/vod/control`、`/obv/complete` 等 | `com/cmic/bizcontroller/controllerImpl/vod/plugin/PluginHttpHandler.java` 等 | — | — | 视频插播/外呼遗留接口，主流程不触发 |

---

## 6. 消息（MQ）

| 方向 | 中间件 | topic / tag | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 生产 | RocketMQ 5（gRPC 客户端，AK/SK 认证；配置项 `endpoints`/`ak`/`sk` 位于 `config.prod/rocketmq.xml`） | `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL` / tag `BillPushMsg` / key=billId | 生产者无 group（待确认 `BaseRocketMQService` 实现） | `billId`（18 位雪花）、`isPickUP`（bool，接通且非仅 G729）、`dbId`（话单库编号） | `com/cmic/service/BillCenterService.java:BillCenterService.sendMsgToBillCenter`、`BillRocketMQService.basicPublish` |
| 生产（未启用） | RocketMQ | `TID_VI_AS_EVENT_PUSH_PUSHER_NORMAL` | — | `StandardEvent`：callId、event、callerNo、calleeNo、secretNo、timeStamp、userId、appId | `com/cmic/service/EventRocketMQService.java`、`BussinessMsgService.sendBillEventMsg`；`Application.initRocketMQ` 中初始化被注释，**不生效** |
| 消费 | — | 无 | — | — | vi-as 不消费任何 MQ |

注意：`BillRocketMQService extends BaseRocketMQService`，但 `BaseRocketMQService` **不在源码中**且无 import（同包 `com.cmic.service`），推测来自某个依赖 jar 或遗漏提交（待确认）。

---

## 7. 依赖调用（出站）

| 目标 | 发现方式 | 协议/路径 | 用途 | 超时/重试 | 代码位置 |
| --- | --- | --- | --- | --- | --- |
| vi-media | ETCD 服务名 `com.cmic.vi.media`（或 `--mediarecordhost`） | POST `/call/session/create` | 创建媒体会话：sessionId(billId)、userId(=appId 或 companyId)、callerNum、calleeNum、mergeConfig（录音参数 JSON 字符串）、startTime(yyyyMMddHHmmss)、recordMode(1)、seqId=1、callbackUrl；返回 `{code:200, data:{ip, audioPort, videoPort, storeFlag}}` | 同步；失败换节点重试 1 次 | `MediaManager.createMediaSession` |
| vi-media | 同上 | POST `/call/session/update` | 提交两侧媒体地址：audioCallerIp/Port、audioCalleeIp/Port、audioCode、audioPayload、audioDtmfPayload、video*、videoObserve=0、seqId | 异步，`postJsonWithRetry`（仅 SocketTimeout 重试，最多 2 轮）；无变化或 IP 非法时不发 | `MediaSession.commit` |
| vi-media | 同上 | POST `/call/session/pickup` | 接通通知 `{sessionId, startTime, seqId}` | 同上 | `CallSession.process200Invite` |
| vi-media | 同上 | POST `/call/session/stop` | 结束通知 `{sessionId, endTime, seqId}` | 同上 | `MediaSession.stop` |
| vi-media | 同上 | `/call/media/play`、`/call/media/dtmf`、`/call/media/stop`、`/call/media/enhance` | 放音（audioSource、audioLoopSource、period Early/InCall、priority Local/Net、direction 1-4）、收号、停止（mark Media/Dtmf）、增强 | 同上；完成后回调 `/media` | `MediaManager`、`MediaService.dccPlayMedia` |
| vi-media（遗留 v1） | 同上 | `/v1/session/bupdate`、`/v1/video/playearly`、`/v1/session/notifyevent`、`/v1/audio/dtmf(/stop)`、`/v1/video/play|stop|control`、`/v1/media/enhance` | 视频/流式业务 | — | `MediaManager` |
| 策略服务 | ETCD `com.cmic.vi.strategy` | POST `/strategy`（HTTP/2） | 话务管控：入参 billId、callerNo、calleeNo、bizNo、companyId、customerId、caller/callee Province/City Id、handleStartTime；出参 opType（0=拦截）、callNoPlayCode、calledPlayCode、appendReason | 请求超时 500ms，连接超时 300ms；`auditStrategyAllow=0` 或无节点时跳过 | `BusinessEvent.doStrategyQuery`、`AsyncJava11HttpClient` |
| xsipproxy / HTTP 代理 | ETCD `com.cmic.xsipproxy`、配置项 `httpProxyName` | SIP Route / HTTP 代理 | 出呼路由（proxyMode=1 内外网代理） | — | `SipUtil.setNextRoute4Dcc/addProxyRoute`、`MyRegistryService.getSipProxyHost` |
| MySQL | 配置文件 | JDBC（druid） | 见 §8 | 查询超时 10s | `com/cmic/dao/**` |
| RocketMQ | `rocketmq.xml` | gRPC | 见 §6 | — | `BillRocketMQService` |
| ETCD | 配置项 `etcd`（prod 三节点模板） | xdiscovery | 服务发现与配置 watch | — | `MyRegistryService` |

vi-as 不直接调用 vi-bill、qa-server、vi-pusher 的 HTTP 接口。

---

## 8. 数据

### 8.1 MySQL

| 库 | 表 | 读/写 | 用途 | 代码位置 |
| --- | --- | --- | --- | --- |
| `vi_bill`（BillDB "billdb"） | `bill_new` | 写（insert + update） | 基础话单 | `com/cmic/dao/BillNewDo.java`、`BillNewBuilder`、`DbAsyncService` |
| `vi_bill` | `bill_fail` | 写 | MQ 发送失败记录：id、company_id、location、reason、failcount、gmt_create | `BillCenterService` |
| `user_qc`（UserQcDB "userQc"） | `biz_num_auth` | 读（全量缓存） | 号码授权：id、num、match_type（1=精确，否则前缀）、company_id、customer_id、create_time、update_time | `com/cmic/dao/UserQcDao.java`、`AuthConfigService.reloadNumConf` |
| `user_qc` | `company_tab` | 读 | 企业：status、parent_id（→渠道）等；含 app_key/app_secret 列（vi-as 未使用，不应外泄） | `AuthConfigService.reloadBizInfo` |
| `user_qc` | `customer_tab` | 读 | 客户：status（1/2/3）、ability、ability_status、app_id、platform_app_id、priority、url（质检复核回调地址）、is_reuse_asr、source、customer_type（1 正式/2 试用/3 联调）、total_minutes、used_minutes、validity、expire_time | `AuthConfigService.reloadBizInfo` |
| `user_qc` | `channel_tab` | 读 | 渠道 | `AuthConfigService.reloadBizInfo` |
| `user_qc` | `chinamobile_icscf_ip` | 读 | `ip, center_area_code, province`：根据 Via 中 ICSCF 地址确定主叫区号 | `ChinaMobileIcscfCacheService.reload` |
| `user_qc` | `phone_segment_area_code_info` | 读（分页 30000） | 号段：province、center_area_code、area_code、operator、number_segment（7 位）、province_id | `PhoneSegmentCacheService.reload` |
| dcc 配置库 | `special_telb_rule`、`caller_prefer_media`、`check_section_no`、`base_prod_num_%03d` | 读 | 遗留中间号配置，主流程未用（待确认） | `com/cmic/dao/NumDao.java` |

#### `bill_new` 字段（来自 `BillNewDo`，snake_case 与列名一一对应；无 DDL 文件）

| 阶段 | 字段 | 含义 |
| --- | --- | --- |
| 插入 | `id` | billId，18 位雪花（location + serverId） |
| 插入 | `location`、`as_id` | 机房位置、AS 实例 serverId |
| 插入 | `mobile_id`、`channel_id`、`company_id`、`customer_id`、`app_id`、`account_type` | 渠道（channel.parentId）、渠道 id（company.parentId）、企业、客户、客户 appId、账户类型（由企业/客户 status 推导） |
| 插入 | `original_caller_number`、`caller_number`、`original_callee_number`、`callee_number`、`biz_number` | 原始/处理后主被叫、业务号码（700 号码） |
| 插入 | `handle_start_time` | AS 收到 INVITE 时间（截断到秒） |
| 插入 | `call_in_province_id`、`call_out_province_id`、`call_in_700_area_code`、`call_in_area_code`、`call_out_area_code` | 主叫/被叫省份、700 号码呼入区号（取原始 R-URI）、主/被叫区号 |
| 更新 | `ms_id`、`pfs_id` | 媒体服务器 id、存储 id（BaseController 恒为 0，ErrorController/NewTwoLegController 取 MediaSession） |
| 更新 | `handle_end_time`、`call_forward_time`、`first_resp_time`、`ring_time`、`call_start_time`、`call_end_time` | 处理结束、转发时间、首响应、振铃、接通、挂机时间 |
| 更新 | `call_duration_s`、`call_duration_m` | 通话时长秒/分钟（向上取整，最小 1） |
| 更新 | `call_state` | 见 §9 CALL_STATE |
| 更新 | `finish_type`、`finish_state`、`append_reason` | 见 §9 |
| 更新 | `record_type`、`q850`、`media_l_direction`、`media_r_direction` | 录音类型（1）、Q.850 原因、左右腿媒体方向 |
| 未写 | `file_duration`、`left_file_length`、`right_file_length`、`is_bill_ok`、`is_record_ok` | 推测由 vi-bill / 合音链路回写（待确认） |

### 8.2 Redis

| key 模式 | 类型 | TTL | 用途 | 代码位置 |
| --- | --- | --- | --- | --- |
| `AB-{caller}-{callee}` | string（INCR） | 设置 TTL | 主被叫对呼叫次数限制 | `SessionLimitUtil` |
| `max_call_limit_{customerId}` | hash（field=billId，value=时间戳） | 2h 过期清理 | 客户并发呼叫限制 | `SessionLimitUtil` |

**实际不生效**：阈值硬编码 -1，且 `Application.initRedis()` 从未调用、`config/redis.xml` 不存在，`JedisClientPool` 未初始化。

### 8.3 ETCD

服务发现（`com/cmic/service/MyRegistryService.java:MyRegistryService.init`，节点名 `vi`）：

| 服务名 | 用途 |
| --- | --- |
| 配置项 `httpProxyName` 的值（默认 `com.cmic.proxy`） | HTTP 代理 |
| `com.cmic.vi.media` | vi-media 录音节点（RECORD_SERVICE_NAME） |
| `com.cmic.xsipproxy` | SIP 出呼代理 |
| `com.cmic.vi.strategy` | 策略服务 |

配置 key（`com/cmic/config/ConstantConf.java`）：

| key | 作用 | 触发 |
| --- | --- | --- |
| `/config/version` | 企业/客户/渠道配置版本 | `AuthConfigService.reloadBizInfo` |
| `/config/num/version` | 号码授权版本 | `AuthConfigService.reloadNumConf` |
| `/config/china_mobile_icscf/version`、`/config/chinamobile_icscf_areacode_version` | ICSCF 区号表版本 | `ChinaMobileIcscfCacheService.reload` |
| `/config/phone_segment/version`、`/config/phone_segment_version` | 号段表版本 | `PhoneSegmentCacheService.reload` |
| `/config/Intercept_international_caller_switch` | 国际主叫拦截开关 | DevHelp |
| `/config/supported_codec` | 支持的编解码 | `DevHelp.supportedCodec` |
| `/config/max_sessions` | 最大会话数 | `DevHelp.maxSession`（缺失时 `parseInt` NPE） |
| `/config/analyse_abnormal_bill/case1..7_switch` | 异常话单分析开关 | `AbnormalBillAnalyserConfig` |
| `/config/callee_limit_num_prefix` | 被叫限制前缀 | DevHelp |
| `/config/audit_strategy_allow` | 是否调用策略服务 | `DevHelp.auditStrategyAllow` |
| `/config/force_to_gd_icscf_city` | 强制走广东 ICSCF 的城市 | DevHelp |
| `/config/publish_rocketmq_thread_pool` | MQ 发布线程池 | DevHelp |
| `/config/zhejiang_jiling_ip` | 浙江吉林(?)IP 列表（待确认） | DevHelp |
| `/config/cert_skipped_by_bind_time` | 按绑定时间跳过实名（待确认） | DevHelp |
| `/config/db_oper_reject_ratio` | DB 并发拒绝比例 | `DbAsyncService.refreshMaxConcurrency` |

### 8.4 MongoDB / 文件存储

- MongoDB：无。
- 录音文件：vi-as 不落盘，录音由 vi-media → vi-codec → vi-merge → vi-mdown → PFS 完成；vi-as 只把 vi-media 返回的 storeId 写入 `pfs_id`（仅部分 Controller）。
- 本地文件：`version` 文件（版本号）；`/var/log/vi-as/` 日志，其中 dblog 是话单入库失败的兜底数据源。

---

## 9. 状态与枚举

### 9.1 FinishState（`com/cmic/state/FinishState.java`，写入 `bill_new.finish_state`）

| 值 | 含义 |
| --- | --- |
| 0 | NEED_ASR（需要 ASR 识别结束原因） |
| 1 | 主叫挂机 |
| 2 | 被叫挂机 |
| 3 | 主叫放弃 |
| 4 | 无应答 |
| 5 | 忙 |
| 6 | 不可及 |
| 7 | 路由失败 |
| 8 | 无绑定 |
| 9 | 绑定查询拦截 |
| 10 | 关机 |
| 11 | 停机 |
| 12 | 拒接 |
| 13 | 空号 |
| 14 | 呼叫转移（秘书台） |
| 15 | 黑名单 |
| 16 | 参数异常 / HTTP_ERROR |
| 17 | 查询超时 |
| 18 | 查询失败 |
| 19 | controller 超时 / 会话超时 |
| 20 | 拨测 |
| 21 | 无法识别 |
| 22 | 策略拦截 |
| 23 | 实名未验证 |
| 24 | industrial id fail |
| 99 | 其他 / 重启 / 释放 |

SIP/Q.850 → FinishState 映射（`FinishState.CAUSE_FINISH_STATE`）：SIP 404→13、486→5、408→4、487→6、600/603→12、503/480→10、481→99；Q.850 21→11、20→10、18→6、4→0、1/2/3/28→13、41→6、34/44→7、17→5、19/102→4、127→6；特例 Q.850 18 + SIP 410 → 11，Q.850 21 + 603 → 忙或拒接。

### 9.2 FinishType（`com/cmic/state/FinishType.java`）

0 系统、1 主叫（LEFT_LEG_STOP）、2 被叫。

### 9.3 精确话单状态机（`com/cmic/state/PreciseCallStateBase.java:PreciseCallStateBase.getFinalState`，按优先级）

1. 系统退出 → 99；
2. 收到 200 但无 ACK → 按 BYE 方 / CANCEL 判定；
3. BYE + 200 → 主叫挂（1）/ 被叫挂（2）；
4. ACK + 200 → 2；
5. controller 超时 → 19 或取消（3）；
6. 181 → 呼叫转移（14）或按原因映射；
7. 180 后：42s 超时 → 无应答（4）；180 后的 UPDATE 或无 SDP 的 183 → 拒接（12）；Q.850 17 / SIP 486 → 忙（5）；CANCEL → 3；否则 NEED_ASR（0）；
8. 无原因 → 0 / 3 / 21；
9. IMS 拒绝映射。

### 9.4 其它常量

| 名称 | 取值 | 位置 |
| --- | --- | --- |
| CALL_STATE（`bill_new.call_state`） | 1=180 振铃、2=200 接通、3=181、4=re-INVITE、5=视频、6=ACK | `ConstantConf` |
| MEDIA_LOGIC_FLAG（recordMode） | 1=common、2=need early、16=not required | `ConstantConf` |
| startMode | 0=正常、1=有话单日志兜底、2=无话单日志兜底 | `AppConfig` |
| proxyMode | 0=无代理、1=内外网代理、2=11 位手机号模拟 700、3=SIPp 压测 | `AppConfig` |
| BillAppendReasonEnum（`append_reason`） | 1–25，如 1 呼转拦截、5 策略拦截默认、8 策略修改（放行但改响应）、9 最大会话数、15 企业账户无效、25 长通话管控 | `com/cmic/pojo/BillAppendReasonEnum.java` |
| customer.status | 1 正常，2/3 其他（待确认） | `AuthConfigService` |
| customer_type | 1 正式、2 试用、3 联调 | `AuthConfigService` |
| biz_num_auth.match_type | 1 精确，其它前缀 | `AuthConfigService` |
| GZH 扩展 id | 7、8、16 | `StandardExtension.isGZHExtId` |
| XSIPPROXY_TAG | `internal-as` | `ConstantConf` |
| 媒体回调事件 | DTMF_DONE、AUDIO_DONE、VIDEO_DONE、ENHANCE_DONE | `com/cmic/recording/mediaevent` |
| HTTP /ops 状态 | 3 在线、2 下线 | `OpsHandler` |

---

## 10. 定时任务

| 任务 | 周期 | 作用 | 位置 |
| --- | --- | --- | --- |
| auditSipEngine | 10s | 导出 SIP UDP 队列长度 | `Application.main` |
| Controller 数量指标 | 10s | `as_controller_alive_num` | `ControllerManager` |
| 死会话清理 | 60s | 超过 18 分钟的会话执行 terminated 并移除 | `ControllerManager` |
| 会话 UPDATE 心跳 | 15 分钟 | 保活 | `Controller` |
| 最大通话时长 | 呼叫级，`maxDuration` 分钟 | 放音 + 1 分钟后释放（实际未启用） | `MaxCallDurationService` |
| ErrorController 延迟释放 | 200ms | NO_PLAY 时回 603 | `ErrorController.start` |
| 状态延迟判定 | 300ms | finish_state 为 0/21 时延迟判定 | `BaseController.handleEnd` |
| forceAsr/forceCodec 还原 | 60 分钟 | `/control` 改动后自动还原 | `ChangeCodeHandler` |
| 优雅下线检查 | 5s | 见 4.8 | `GracefulDownHandler` |
| Redis hash 清理 | 2h 过期 | `max_call_limit_*`（不生效） | `SessionLimitUtil` |

调度线程池：`ScheduleService` 的 `ScheduledThreadPool(20)` + `ThreadPoolExecutor(20, 100, 队列 2000)`。

---

## 11. 关键配置项

| 配置项 | 含义 | 位置 | 环境差异 |
| --- | --- | --- | --- |
| `location` | 机房位置，参与雪花 ID | application-config.xml | prod 模板 |
| `serverId` | AS 实例 id（必须唯一），参与雪花 ID、写 `as_id` | 同上 | prod 模板 |
| `asListen` | SIP 监听地址:5080 | 同上 | 地址 prod 模板，端口一致 |
| `billTrace` | 话单追踪 | 同上 | true |
| `httpIp` / `httpPort` | HTTP 服务地址与端口；也用于媒体回调 URL | 同上 | 5668 / 5287 |
| `billConfigPath` | 话单库配置文件（druid） | 同上 | dev 指向不存在文件；prod `config/vi_bill.yaml` |
| `confDbConfigPath` | 配置库（user_qc）配置文件 | 同上 | dev 不存在；prod `config/vi_conf.yaml` |
| `asStackFilePath` | SIP 栈配置路径（实际被硬编码覆盖） | 同上 | — |
| `etcd`、`etcdNodeName` | ETCD 地址列表、节点名 `vi` | 同上 | prod 三节点模板 |
| `publishMQThreadPoolSize` / `publishMQThreadPoolQueueSize` | MQ 发布线程池 | 同上 | 16 / 10000 |
| `SLBAddress` | SLB 地址（用途待确认，代码中未见核心引用） | 同上 | prod 模板 |
| `proxyMode` | 代理模式 | 同上 | dev 1；prod 默认 0 |
| `httpProxyName` | HTTP 代理服务名 | 同上 | prod 模板 |
| `startMode` | 运行模式 | 同上 | dev 1；prod 模板 |
| `endpoints` / `ak` / `sk` | RocketMQ 接入点与凭据 | `config.prod/rocketmq.xml` | dev 无此文件 |
| druid 数据源（url/用户名/口令） | 话单库与配置库 | `config.prod/vi_bill.yaml`、`config.prod/vi_conf.yaml` | — |
| `THREAD_POOL_SIZE` 等 | JAIN-SIP 栈参数 | `config*/sip-stack-call.properties` | 16 / 128 |
| JVM 堆 | — | `bin*/start.sh` | 1g / 21g |
| DevHelp 运行时开关 | forceAsr、forceCodec、supportedCodec、maxSession、auditStrategyAllow、dbOperRejectRatio 等 | `com/cmic/util/DevHelp.java` | 由 ETCD 与 `/control` 修改 |

---

## 12. 坑与特殊逻辑

1. **录音无配置开关**：`BusinessEvent.setRecordParams` 中 `needRecord=true` 硬编码；客户的 `ability`/`ability_status` 等质检能力字段 vi-as 不使用。
2. **前缀匹配 bug**：`AuthConfigService.reloadNumConf` 重建前缀表时用了 `new TreeMap<>()`（自然序），覆盖了原先按长度降序的 comparator，导致迭代为字典序，**短前缀会先命中，不是最长前缀匹配**；而原 comparator 只比较长度，等长 key 会被视为相等而互相覆盖。两种实现都有问题。缓存 HashMap 引用替换非 volatile，存在可见性问题。
3. **话单 ms_id / pfs_id**：`BaseController.insertAndMonitor` 恒写 0，而 `ErrorController`、`NewTwoLegController.fillBill` 使用 MediaSession 的 serverId/storeId，主流程话单丢失媒体/存储定位信息（下游如何定位录音待确认，可能依据 billId）。
4. **右腿省份字段 bug**：`BillNew.setRightLegInfo` 的固话分支写的是 `callInProvinceId`，应为 `callOutProvinceId`。
5. **ICSCF 缓存 bug**：`ChinaMobileIcscfCacheService` 构建时把 province 设为字面量字符串 `"province"`。
6. **号段缓存**：`PhoneSegmentCacheService.reload` 不清理旧条目；`getAreaCodeFromPhoneSegment` 可能 NPE。
7. **媒体重试 bug**：`MediaManager.postJsonWithRetry` 只在错误消息含 `SocketTimeoutException` 时重发；非超时错误会再次进入循环但不重发，并调用 `failHook` 两次。
8. **vi-media 创建失败**：`MediaService.createMediaSession` 失败后换节点重试，`getRecordHost` 返回 null 时有 NPE 风险；BaseController 下媒体不可用时呼叫照常接续但不录音（待确认）。
9. **策略查询**：`initAuditSendJson` 被调用两次；策略服务超时/异常时的处理（放行还是拦截）需进一步确认，从代码看回调失败分支走默认放行（待确认）。`blackCallerIntercept`（dcc-conf 黑名单）存在但主流程未调用。
10. **MQ 无重发**：发 vi-bill 失败只写 `bill_fail`，代码中无补发逻辑；且写入 `bill_fail.company_id` 的实际是 customerId。
11. **话单入库失败兜底在日志**：DB 超时重试最多 2 分钟，之后 SQL 写入 dblog；需要外部工具回放（待确认是否存在）。startMode=1 时**所有话单只写 dblog**。
12. **BaseRocketMQService 缺失**：源码中无此类，按当前源码无法编译（待确认来自哪个依赖）。
13. **Redis 实际未用**：`initRedis` 未调用、阈值硬编码 -1；`SessionLimitUtil` 中仅 `auditLimitIntercept`（处理策略结果）有效。
14. **HTTP 管理接口无鉴权**：`/control` 可反射修改任意 `DevHelp` 静态字段，`/changeStartMode` 可切换运行模式，`/ops/pause` 可摘流量；应仅在内网暴露。
15. **凭据打印**：`RocketMQConfig.init` 用 `System.out.println` 打印每个配置节点的内容，会把 ak/sk 输出到标准输出（安全风险）。
16. **HTTP Handler 缺陷**：`MediaHandler` 异常时不回响应；`OpsHandler` 用字符长度而非字节长度设置 Content-Length（中文会出错）。
17. **配置坑**：dev 配置中 billConfigPath/confDbConfigPath 指向不存在文件、无 rocketmq.xml，只能以 startMode=1 运行；prod 的 `mssSipStackPath` 是未知配置项；`asStackFilePath` 被 `DtmfEngine.init` 硬编码覆盖；ETCD 缺 `/config/max_sessions` 时 `DevHelp.init` NPE。
18. **企业状态校验**：`ViConf.isBizStateActive` 注释写三级校验，实际只校验企业与客户两级（渠道未校验）。
19. **FinishState 映射死代码**：5xx → 路由失败的分支不可达。
20. **大量遗留代码**：`event/**`、`bizcontroller/event/**`（重复的事件/状态机实现）、vod/vrbt/obv 插件（`needVideoInsert`/`needVideoRing` 从未置位）、`MaxCallDurationService`（`maxDuration` 未赋值）、`EventRocketMQService`、`NumDao` 中 dcc 表、`CaiNiaoUtil`/`XieChengEncryptHelp` 等无引用工具，以及 `NewTwoLegController` 中大段注释代码。删除前需按 AGENTS.md 查 ADR/lessons。
21. **号码处理特殊逻辑**：去区号仅在剩余部分以 `7001` 开头时进行（适配 xproxy 7001 前缀入口）；700 被叫转发时截掉前 5 位（如 `70014` 前缀）得到真实被叫；`125838`/`125836` 截 6 位；国际被叫号码保留原样。改动前需与 xproxy 号码规则核对。
22. **TransactionAlreadyExistsException 静默忽略**：初始 INVITE 重传直接丢弃，依赖 JAIN-SIP 事务层重发响应。
23. **会话数保护回 500**：To-tag 固定为 `cmic`，便于上游识别是 AS 主动拒绝。
24. **测试代码含号码**：`AuditStrategyQuery.main`、`MediaManager.main`、`FinishState` 注释及 `ConfigCache.main` 等含测试号码/地址，不应外传。

---

## 13. 与其它仓库的关系

| 方向 | 仓库/系统 | 契约 | 说明 |
| --- | --- | --- | --- |
| 上游 | xproxy（不在本次源码） | SIP INVITE（UDP 5080），7001 前缀号码；OPTIONS 探活 | xproxy 作为统一入口把专线呼叫送到 AS |
| 上游/下游 | IMS（S-CSCF/I-CSCF）、xsipproxy | SIP Route、Via（ICSCF 区号判定）、History-Info、`Mid-Orig-Msg` 头 | 出呼经 xsipproxy（`internal-as` tag）或直接回 S-CSCF |
| 下游 | vi-media（vi-media-master，Go） | HTTP `/call/session/create|update|pickup|stop`、`/call/media/*`；回调 vi-as `POST /media`；ETCD 服务名 `com.cmic.vi.media`；mergeConfig 录音参数 JSON | 媒体锚定与录音；后续 vi-media → vi-codec → vi-merge → vi-mdown → PFS |
| 下游 | vi-billcenter（vi-bill） | RocketMQ `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL` `{billId, isPickUP, dbId}`；共享表 `vi_bill.bill_new` | vi-bill 依据 billId 查 bill_new，再驱动推送（vi-pusher）与质检（qa-server） |
| 下游（未启用） | vi-pusher | RocketMQ `TID_VI_AS_EVENT_PUSH_PUSHER_NORMAL`（通话事件） | 代码存在但未初始化 |
| 依赖 | 策略服务 `com.cmic.vi.strategy`（推测为 Go 服务，可能与 vi-cront-jobs 的话务管控策略相关，待确认） | HTTP `/strategy` | 话务管控/拦截放音 |
| 共享数据 | 对客质检运营平台 / qa-server / 其它服务 | MySQL `user_qc` 库（biz_num_auth、company_tab、customer_tab、channel_tab、号段、ICSCF 表）；ETCD `/config/version`、`/config/num/version` 等版本 key | 配置写入方修改表后需更新 ETCD 版本 key 才能触发 AS 重载（写入方待确认） |
| 共享数据 | vi-merge / offline-merge / vi-billcenter | `bill_new` 中 file_duration、file_length、is_bill_ok、is_record_ok 等字段 | vi-as 不写，推测由下游回写（待确认） |
| 运维 | 监控系统 | `/metrics`（HTTP 端口）、JMX exporter 8588 | 指标如 `as_invite_arrive_total`、`as_left_leg_num`、`as_bill_finishstate`、`recording_call_retry*`、`call_reject_due_to_too_much` |

---

## 14. 待确认问题清单

1. `BaseRocketMQService` 的来源（依赖 jar 还是缺失提交）、生产者的 group/重试配置。
2. `bill_fail` 记录是否由其他服务或脚本扫描补发 MQ；dblog 中失败 SQL 是否有回放工具。
3. BaseController 主流程 `ms_id`/`pfs_id` 恒为 0，下游（vi-bill / vi-merge）如何定位录音文件（按 billId？）。
4. vi-media 创建失败时 BaseController 是否继续接续呼叫（不录音）是否为预期行为。
5. 策略服务超时/异常时是放行还是拦截；策略服务对应哪个仓库。
6. 被叫为 700 时 bizNum 取主叫号码的业务含义（GZH/外呼场景？）。
7. 700 被叫截掉前 5 位的前缀规则（如 `70014`）与 xproxy 的号码改写约定。
8. `customer_tab.ability`、`ability_status`、`is_reuse_asr`、`url` 等质检能力字段由哪个服务使用（预计 vi-bill / qa-server）。
9. 前缀匹配 TreeMap 问题是否已在线上造成错配；是否存在同长度前缀。
10. `maxDuration`、`needVideoInsert`、`needVideoRing` 等功能是否计划由策略服务下发，还是确认废弃。
11. `EventRocketMQService`（发往 vi-pusher 的通话事件）是否确认废弃。
12. `user_qc` 配置表的写入方，以及谁负责递增 ETCD 版本 key。
13. `/config/zhejiang_jiling_ip`、`/config/cert_skipped_by_bind_time`、`SLBAddress`、`--force_config` 的确切用途。
14. `bill_new` 中 file_duration、left/right_file_length、is_bill_ok、is_record_ok 的写入方。
15. 生产 `startMode` 实际取值（模板 `{{startMode}}`），以及是否存在长期以 startMode=1 运行的节点。
16. HTTP 管理端口是否仅内网可达、是否有网关层鉴权。
