---
id: module-vi-billcenter
title: vi-billcenter 代码深度解析（话单中心）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-billcenter]
source_of_truth: [vi-assets/vi-billcenter-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-billcenter-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-billcenter.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-billcenter（设计图中的 vi-bill）仓库分析报告

> 仓库：`/workspace/vi-assets/vi-billcenter-master`（下文路径均相对该目录，Java 源码根为 `src/main/java/com/cmic/`，简写为 `c/`）
> 结论均来自代码；源码之外（其它仓库 / docx）的佐证会单独注明；推断或无法从代码确认的内容标"待确认"。
> 敏感信息（连接串、AK/SK、密码、主机地址）一律只写配置项名称。

---

## 1. 定位

vi-billcenter 是专线质检链路中的"话单中心"，负责编排质检流程，但本身不负责生成话单。基础话单由 vi-AS 写入 MySQL `vi_bill.bill_new`，vi-billcenter 基于这张表做三件事：

1. 消费 vi-AS 的**通话结束消息**。未接通的话单直接移到按天分的历史表；接通的话单什么也不做，等合音结果。
2. 消费 vi-merge / offline-merge 的**合音结果消息**。收到后回写录音长度和时长，再经代理 SLB 用 HTTP 调 qa-server 创建质检任务，并把发起结果写进 `quality_inspection_status`。
3. 对外提供**质检子任务回调接口**。qa-server 回调后，更新质检状态，并把回调报文原样投递到 RocketMQ，交给 vi-pusher 推送给客户。

另外提供一个手工触发的"发起质检失败重建"接口。设计图里 vi-bill 直接推消息给 vi-pusher；在代码中，**实际生效的只有质检结果这一条消息**（`TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`）。话单推送和录音推送的发送器（`BillSender`、`RecordSender`）还在，但没有任何调用方（见 §12）。

---

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言 / JDK | Java 21（`pom.xml` `java.version=21`） |
| 框架 | Spring Boot 3.5.7（parent），spring-boot-starter-web（显式指定 Tomcat 11.0.9 组件）、spring-boot-starter-jdbc、actuator、aop |
| MQ | `rocketmq-v5-client-spring-boot-starter` 2.3.4（RocketMQ 5.x gRPC 客户端，PushConsumer + `RocketMQClientTemplate`） |
| DB | MySQL（`mysql-connector-j`）、Druid 连接池（`druid-spring-boot-3-starter` 1.2.25）、`dynamic-datasource-spring-boot-starter` 4.3.1 多数据源（`@DS` 切换）、`JdbcTemplate` 手写 SQL；引入了内部包 `common-dao:sharding-database-table:1.0.8`，只用到其中的 `ResultSetConvertor` |
| 注册中心 | `com.ziyan.common:xdiscovery:1.1.19`（`com.discovery.RegistryService`，基于 jetcd 0.8.5 的 ETCD 服务注册 / 发现 / Key Watch） |
| HTTP 客户端 | `RestTemplate` + Apache HttpClient 5.5（`c/config/RestTemplateConfig`） |
| JSON | fastjson2 2.0.57 |
| 日志 | log4j2 + disruptor（异步 Logger） |
| 监控 | micrometer-registry-prometheus 1.15.5 |
| 构建 | Maven（带 `mvnw`）。`maven.test.skip=true`。两个 profile：`dev`（默认激活，`maven-jar-plugin` 生成薄 jar + `lib/` 依赖目录，manifest 的 `mainClass=com.cmic.BillCenterApplication`，**该类在源码中不存在**，见 §12）和 `prod`（`spring-boot-maven-plugin` 打 fat jar，`finalName=vi-billcenter-jar-with-dependencies`） |
| 启动入口 | `c/ViBillcenterApplication.main`（`@SpringBootApplication @EnableScheduling`） |
| 监听端口 | 业务 HTTP `server.port=9277`；管理端口 `management.server.port=19277`，只暴露 Prometheus：`/manage/info/prometheus` |
| 部署脚本 | `bin/start.sh` 与 `bin.prod/start.sh` 内容相同：`java -Dlog4j.configurationFile=config/log4j2.xml -DLog4jContextSelector=...AsyncLoggerContextSelector -jar *.jar` |
| 环境区分 | `config/` 只有 `log4j2.xml`（projectName=`vibillcenter`），**没有 application.yml**；`config.prod/` 有 `application.yml`（全部敏感值为 `{{xxx}}` 模板占位符，推测由部署工具渲染，待确认）和 `log4j2.xml`（projectName=`vi-billcenter`）。`src/main/resources` 不存在。运行时通过工作目录下的 `config/application.yml` 加载（Spring Boot 默认外部配置路径），所以部署时应该是把 `config.prod/*` 拷到 `config/`（待确认） |
| 日志路径 | `/var/log/vi-billcenter/{all,warn,error,recover}.log`，备份目录 `/var/log/vi-billcenter/bak`。`recover.log` 由 logger `recoverlog` 专写（`c/utils/RecoverLogUtil`） |
| README | 只有一行标题 `# vi-billcenter`，没有任何说明 |

---

## 3. 代码结构

| 包 / 目录 | 职责 |
| --- | --- |
| `c/ViBillcenterApplication` | 启动类 |
| `c/controller/ApplicationController` | HTTP 入口：质检主任务回调、子任务回调、失败重建、测试接口（`/vibillcenter/*`） |
| `c/service/BillService` | 处理 vi-AS 通话结束消息（区分接通 / 未接通） |
| `c/service/RecordService` | 处理合音结果消息：回写录音信息，发起质检 |
| `c/service/QualityCheckService` | 组装并调用 qa-server 创建质检任务（URL、鉴权头、请求体） |
| `c/service/QualityCheckCallbackService` | 处理 qa-server 子任务回调：更新状态，发 MQ 给 vi-pusher |
| `c/service/QualityCheckRecreateService` | 批量重建"发起质检失败（-1）"的话单 |
| `c/service/BillCommonFun` | `createHistory`：`bill_new` → `bill_yyyy_MM_dd` 移表 |
| `c/rocketmq/*Receiver` | 4 个 RocketMQ PushConsumer（通话消息、通话延时消息、合音消息、合音延时消息） |
| `c/rocketmq/*Sender` | 生产者：通话延时、合音延时、质检结果（生效）；话单推送、录音推送（无调用方，死代码） |
| `c/dao/BillDao`、`BillHistoryDao` | 数据源 `vi_bill`：`bill_new` 增删改查、历史分表写入 |
| `c/dao/MidAuthDao` | 数据源 `user_qc`：`company_tab`、`customer_tab`、`enterprise_scene` 全量读取 |
| `c/dao/DccConfigDao` | 数据源 `dcc_conf`：`file_down_host`（`host_type=2`） |
| `c/utils/*Manager` | 内存缓存：`CompanyTabManager`、`CustomerTabManager`、`EnterpriseSceneManager`、`FileDownHostManager`（静态 HashMap，整体替换式 reload） |
| `c/utils/Parameters` | 读取 `parameters-config.*`；`@PostConstruct` 初始化缓存、注册 ETCD、监听 `/config/version` |
| `c/utils/AuthorizationUtil`、`AuthorizationGenHelp` | 生成 `EOPAUTH` 鉴权头 |
| `c/utils/HttpUtil` | RestTemplate POST 封装 |
| `c/utils/CommonUtil` | `generateRequestId`（UUID 去横线） |
| `c/utils/Obfuscate`、`MD5Util` | 混淆 / MD5 工具，没有业务调用方 |
| `c/utils/RecoverLogUtil` | 写 `recover.log`（合音消息重投失败记录） |
| `c/config/*` | 多数据源、JdbcTemplate、RestTemplate（连接池 / 超时）、RocketMQ Bean 角色修正 |
| `c/metrics/BillMetrics` | Prometheus 计数器 |
| `c/schedule/BillNewCreateTaskFailClear` | **整个文件被注释**，是原定时重建任务，现由 HTTP 接口代替 |
| `c/pojo/entity`、`dto`、`vo` | `Bill`、`CompanyTab`、`CustomerTab`、`EnterpriseScene`、`FileDownHost`；`TaskUrlDto{id,url}`；`CommonResult{code,data,message}` |

---

## 4. 核心业务流程

### 4.1 话单如何组装：通话消息与合音结果的关联

**关联键**：vi-AS 话单 ID 是 `bill_new.id`（varchar(32)）。它在不同消息里叫不同的字段名：

- vi-AS 消息里叫 `billId`
- 合音消息里叫 `callId`
- 质检请求 `urlList[].id`
- 质检回调里叫 `listId`

vi-billcenter **不在内存中合并两条消息**，所有关联都通过以这个 ID 查 `bill_new` 完成。话单主体是 vi-AS 写入的那一行；vi-billcenter 只回写三类字段：

- 录音长度和时长：`left_file_length`、`right_file_length`、`file_duration`
- `quality_inspection_status`
- 移表时把整行复制到历史表

是否接通由 vi-AS 在消息里的 `isPickUP` 字段决定。佐证见 vi-as 仓库 `DbAsyncService`：`isPickUP = callDurationS > 0 && !onlySupportG729`，而且 vi-AS 只在 DB update 成功后才发这条消息。

### 4.2 流程 A：通话结束消息（vi-AS → vi-billcenter）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| A1 | 消费 `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL`，JSON 转 Map | `c/rocketmq/BillReceiver.consume` | 任何异常只打日志；**始终返回 `ConsumeResult.SUCCESS`**，不依赖 MQ 重投 |
| A2 | `isPickUP=true`：接通，直接 return，等合音消息触发质检 | `c/service/BillService.checkBill` L36-39 | 不设等待超时，也不做兜底（见 §12） |
| A3 | 未接通：计数 `vi.bill.request.total`，`findBillNewById` | 同上 L41-58 | DB 异常：打日志后 return（消息丢失）；查不到：打日志后 return |
| A4 | `finish_state == 0`：注释写的是"as 异步更新数据库，收到消息时可能还没更新"，于是发 **5 秒**延时消息 `TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY` | 同上 L62-63；`c/rocketmq/BillDelaySender.sendDelayBill` | 发送失败只打日志 |
| A5 | `finish_state != 0`：未接通，不需要质检，`createHistory` 移表 | `c/service/BillCommonFun.createHistory` | 插入历史表的行数不是 1：打日志，不删 `bill_new` |
| A6 | 延时消息到达：`billId` 必须是纯数字（`StringUtils.isNumeric`），再查 `bill_new`，**不再检查 finish_state，直接移表** | `c/rocketmq/BillDelayReceiver.consume` L64-71 | 查不到：return SUCCESS。只等一次，不循环 |

### 4.3 流程 B：合音结果消息（vi-merge / offline-merge → vi-billcenter）→ 发起质检

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| B1 | 消费 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`，按 `code` 分支：`0` 表示合音成功，`1` 表示合音失败 | `c/rocketmq/RecordReceiver.consume` | 异常只打日志，始终返回 SUCCESS |
| B2 | `code=1`：只计数 `vi.record.error.total` 并打日志，不落库、不发起质检 | `c/service/RecordService.getError` | 读取的是 `param.get("id")`，但合音消息没有 `id` 字段，会抛 NPE（见 §12） |
| B3 | `code=0`：取 `callId`，为空则丢弃；`findBillNewById` | `RecordService.getRecording` L32-47 | DB 异常：打日志后 return，消息丢失（代码里有 `//todo:推延时队列`） |
| B4 | **查不到话单**（合音结果比 vi-AS 写库先到）：消息体里的 `recordReceiveTimes` 加 1，发 **60 秒**延时消息 `TID_VI_ONE_BILLCENTER_RECORD_PUSH_BILLCENTER_DELAY` | 同上 L56-72；`c/rocketmq/RecordDelaySender` | `recordReceiveTimes >= 3` 后不再重投，写 `recover.log`，格式为 `[record_repush_fail] [<billId>]`。等待策略：60s × 3 次，最长约 180s |
| B5 | 延时消息到达：只处理 `code==0`，重新进入 `getRecording` | `c/rocketmq/RecordDelayReceiver.consume` | 同 B3/B4 |
| B6 | 查到话单：把消息里的 `leftFileLength`、`rightFileLength`、`fileDuration` 写到对象上，然后 `updateBillNewRecord` | 同上 L75-94 | 更新异常只打日志，**照样继续发起质检** |
| B7 | 计数 `vi.inspection.create.total`；`QualityCheckService.checkQuality(bill)`，成功则 `quality_inspection_status=1`，失败则 `-1`，再 `updateBillNewInspectionStatus` | 同上 L95-108 | 失败**不自动重试**，只能通过 §4.5 的人工重建接口补发 |

**`checkQuality` 细节**（`c/service/QualityCheckService`）：

1. 从缓存取 `CompanyTab`（按 `bill.companyId`）和 `CustomerTab`（按 `bill.customerId`），任一为 null 直接返回 false。
2. 生成请求头：`Content-Type: application/json`，`Authorization: EOPAUTH appid="<company_tab.app_key>",timestamp="<秒级时间戳>",signature="<MD5(timestamp + company_tab.app_secret) 大写>"`（`c/utils/AuthorizationUtil.getAuthorizationHeader`）。
3. 组装请求体，见 §7.1。
4. `POST parameters-config.qc-server-create-url`。HTTP 2xx 且响应体 `code == "0000"` 才算成功，其它情况都算失败。
5. 超时设置：响应 8s，连接 2s，从连接池取连接 200ms（`RestTemplateConfig`）。没有重试。

### 4.4 流程 C：质检回调（qa-server → vi-billcenter）→ 投递给 vi-pusher

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| C1 | `POST /vibillcenter/subtask/callback` 收到子任务结果；参数为空返回 `9999` | `ApplicationController.subtaskCallback` | 计数 `vi.inspection.callback.failure.total` |
| C2 | 按 `listId` 查 `bill_new`；**查不到也返回成功（`0000`）**，只打日志 | `QualityCheckCallbackService.processSubtackCallback` L23-27 | qa-server 不会因此重推 |
| C3 | `status == "1"` 时 `quality_inspection_status=2`，否则 `-2`；更新 DB | 同上 L28-33 | — |
| C4 | 回调 Map **原样**发同步普通消息 `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`，KEYS=listId | `c/rocketmq/InspectionResultSender.send` | 生产者 `max-attempts: 3`；异常包装成 RuntimeException，接口返回 `9999`，qa-server 会按自己的延时队列重推（佐证：qa-server `apps/result_consumer.py` 中的 `push_http_message`，响应不是 `code=="0000"` 就进入 `push_to_delay_mq`） |
| C5 | 返回 `{"code":"0000","message":"回调处理成功"}` | — | 计数 `vi.inspection.callback.success.total` |

`POST /vibillcenter/task/callback`（主任务回调）只打日志，返回成功，不做任何业务处理。

> 注意：vi-billcenter 收到质检回调后**不移表**。话单从 `bill_new` 移到历史表的动作由 vi-pusher 在推送成功、推送最终失败，或客户未配置推送地址时完成（见 vi-pusher 报告）。

### 4.5 流程 D：人工重建"发起质检失败"的话单

| 步骤 | 做什么 | 代码位置 | 说明 |
| --- | --- | --- | --- |
| D1 | `POST /vibillcenter/task/recreate`，body 可带 `startTime`、`endTime`（格式 `yyyy-MM-dd HH:mm:ss`）和 `companyId` | `ApplicationController.recreateTask` | 已有任务在跑时返回 `{"result":"fail"}`（`AtomicBoolean` 保证同一时间只跑一个） |
| D2 | 异步线程池 `EXECUTOR`（CPU×2）中执行 `failBillReCreate` | `QualityCheckRecreateService.recreatingTask` | 接口立即返回 `{"result":"success"}` |
| D3 | 游标分页：`SELECT * FROM bill_new WHERE quality_inspection_status=-1 AND handle_start_time BETWEEN ? AND ? [AND company_id=?] [AND id>?] ORDER BY id LIMIT 1000` | `BillDao.getCreateTaskFailBillNew` | 不传时间时默认是硬编码的 `2026-07-02 00:00:00` ~ `2026-07-05 00:00:00` |
| D4 | 每批用 8 线程（`BILL_PROCESSOR`）并发 `checkQuality`；成功的改为 `1`，失败的保持 `-1` | `failBillReCreate` | 批内异常不计入成功 / 失败；批级异常直接 break |
| D5 | 优雅停机：`@PreDestroy` 先等 30s，再 `shutdownNow` | `destroy` | — |

### 4.6 启动流程

`c/utils/Parameters.applicationInit`（`@PostConstruct`）依次执行：

1. 加载四个缓存
2. `RegistryService.Init(cluster-name, etcd)`
3. 把自身注册为 `com.cmic.vi.billcenter`，地址为 `server.address:server.port`
4. `WatchService(httpproxy-name)`（本服务并不用它，见 §12）
5. `WatchEtcdKey("/config/version")`：收到 PUT 事件就 reload 四个缓存

任何一步异常都会 `System.exit(1)`。

---

## 5. 对外接口（入站）

前缀均为 `/vibillcenter`，端口 9277。

| 协议 | 方法 + 路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | `POST /vibillcenter/subtask/callback` | `ApplicationController.subtaskCallback` | 示例：`{taskId, listId(=话单id), status("1" 成功 / "0" 失败), qcResult, riskLevel, errorMessage, data, userData{...,ctrlAction,audioCode,audioDirection,callId}, duration, transcription, matchedWord, summary ...}`。字段集合以 qa-server 为准，本服务只读取 `listId` 和 `status` | `CommonResult{code:"0000"/"9999", message}` | **质检子任务回调**。URL 在发起质检时用 `http://{server.address}:{server.port}/vibillcenter/subtask/callback` 拼出，作为 `subTaskCallBackUrl` 传给 qa-server，即回调直连本实例，不经过 SLB |
| HTTP | `POST /vibillcenter/task/callback` | `ApplicationController.taskCallback` | `{taskId, finishRate}` | `CommonResult` | 主任务回调，只打日志 |
| HTTP | `POST /vibillcenter/task/recreate` | `ApplicationController.recreateTask` | `{startTime?, endTime?, companyId?}` | `{"result":"success"\|"fail"}` | 人工重建发起质检失败（-1）的话单；没有鉴权 |
| HTTP | `POST /vibillcenter/test` | `ApplicationController.test` | `{id}` | `CommonResult` | **调试接口**：用硬编码的 companyId=15、customerId=30 构造一个假话单，并真实调用 qa-server。生产环境也暴露，没有鉴权（见 §12） |
| HTTP | `GET /manage/info/prometheus`（端口 19277） | actuator | — | Prometheus 指标 | 只暴露 prometheus 一个端点 |

所有业务接口都没有鉴权或签名校验。

---

## 6. 消息

均为 RocketMQ 5.x（`rocketmq-v5-client-spring-boot-starter`）。tag 均为 `*`；所有 Consumer 都**无条件返回 SUCCESS**，失败处理依赖业务自己发延时消息。

| 方向 | 中间件 | topic | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 消费 | RocketMQ | `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL` | `GID_VI_ONE_BILLCENTER_BILL`（线程数 `billConsumerCount`） | `billId`、`isPickUP`、`dbId`（`dbId` 本服务不使用） | `c/rocketmq/BillReceiver` → `BillService.checkBill` |
| 生产 + 消费 | RocketMQ | `TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY`（延时 5s，KEYS=billId） | 消费组 `GID_VI_ONE_BILLCENTER_BILL_PROCESS`（线程数 `billConsumerCount`） | 原通话消息体 | 生产：`BillDelaySender`；消费：`BillDelayReceiver` |
| 消费 | RocketMQ | `TID_VI_ONE_MERGE_MSG_BILL_NORMAL` | `GID_VI_ONE_BILLCENTER_RECORD`（线程数 `recordConsumerCount`） | `code`（0 成功 / 1 失败）、`callId`、`fileName`、`leftFileLength`、`rightFileLength`、`fileDuration`、`serverId`、`separateEarly`、`fullRecord` 等。字段定义参考 offline-merge `internal/pkg/broker/pub/dcc_producer.go:DccBillMessage`；本服务只用 `code`、`callId`、三个长度 / 时长字段 | `RecordReceiver` → `RecordService` |
| 生产 + 消费 | RocketMQ | `TID_VI_ONE_BILLCENTER_RECORD_PUSH_BILLCENTER_DELAY`（延时 60s，KEYS=callId） | 消费组 `GID_VI_ONE_BILLCENTER_RECORD_DELAY`（线程数 `delayRecordConsumerCount`） | 原合音消息 + `recordReceiveTimes` | `RecordDelaySender` / `RecordDelayReceiver` |
| 生产 | RocketMQ | `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`（同步普通消息，KEYS=listId） | 消费方：vi-pusher `GID_VI_ONE_PUSHER_INSPECTION_PUSH` | qa-server 子任务回调体，原样转发 | `InspectionResultSender.send` |
| 生产（死代码） | RocketMQ | `TID_VI_BILLCENTER_BILL_PUSH_PUSHER_NORMAL` | — | 整个 `Bill` JSON | `BillSender.send`，没有调用方 |
| 生产（死代码） | RocketMQ | `TID_VI_BILLCENTER_RECORD_PUSH_PUSHER_NORMAL` | — | `billId,fileName,msId,separateEarly,fullRecord,bindId` | `RecordSender.send`，唯一调用点在 `RecordService` 中被注释 |

生产者配置：`rocketmq.producer.max-attempts=3`，`request-timeout=5`（秒，单位待确认）。

**CONFLICT（合音 topic 名不一致，待确认）**：本服务订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`，但其它仓库的生产配置里写的是：

- vi-merge `config.prod/app.yaml`：`rocketMq.topic = TID_VI_MERGE_MSG_BILL_NORMAL`（另配 `namespace: VI`）
- offline-merge `config.prod/app.yaml`：`viTopic = TID_VI_MERGE_BILL_RECORD_NORMAL`，`dccTopic = TID_DCC_MERGE_MSG_BILL_NORMAL`

三处都和本服务订阅的名字不同。需要确认是部署时被覆盖、RocketMQ namespace 映射，还是确实不一致。

---

## 7. 依赖调用（出站）

### 7.1 qa-server：创建质检任务（经代理 SLB）

- URL：配置项 `parameters-config.qc-server-create-url`（位于 `config.prod/application.yml`，值是模板占位）。请求体带 `customizedDetectionId`，对应 qa-server 的 `POST /v1/task/customize`（`apps/views.py:create_customized_task`），因此推断是这个接口，**待确认**。
- 方法：POST JSON；请求头 `Authorization: EOPAUTH appid=...,timestamp=...,signature=...`（用三级企业 `company_tab.app_key/app_secret` 签名）。
- 超时：连接 2s，响应 8s，取连接 200ms；连接池总数 3000，每路由 300；TLS 1.2/1.3，`NoopHostnameVerifier`（不校验主机名）。
- 成功判定：HTTP 2xx 且 `code=="0000"`。

请求体（`QualityCheckService.createParams`）：

| 字段 | 取值来源 | 说明 |
| --- | --- | --- |
| `requestId` | `CommonUtil.generateRequestId()` | 32 位 UUID |
| `urlList` | `[{id: bill.id, url: recordUrl}]` | 一次请求只放一通话单 |
| `customizedDetectionId` | **硬编码** `pjeu1p52049743508159467520` | qa-server / scheduler 定制流程 ID，对应 MongoDB `scheduler.process.processId`（佐证见 qa-server `service.py`）；旧值保留在注释里 |
| `wordPackageId` | `customer_tab.scenario_id` 按逗号拆开，映射到 `enterprise_scene.word_package_id`，再逗号拼接 | 场景对应的词包 |
| `taskCallBackUrl` | `http://{server.address}:{server.port}/vibillcenter/task/callback` | 主任务回调 |
| `subTaskCallBackUrl` | `http://{server.address}:{server.port}/vibillcenter/subtask/callback` | 子任务回调 |
| `userData` | `{callerNumber?, calleeNumber?, handleStartTime("yyyy-MM-dd HH:mm:ss"), companyId, customerId}` | 透传字段，会出现在回调里 |
| `channelId` | `customer_tab.app_id` | 四级客户终端 ID |
| `ragTopK` | 硬编码 `1` | — |
| `deepSeekPromptId` | 硬编码 `"38"` | — |
| `qwenPromptId` | 硬编码 `"39"` | — |
| `rtType` | 硬编码 `"0"` | 推测 0 表示离线 / 准实时，待确认 |

录音 URL（`createRecordUrl`）：

```text
{file_down_host.host(location=parameters-config.location, host_type=2)}/internal/media/{bill.appId}/{bill.msId}/{bill.id}/{handleStartTime 秒级时间戳}/{bill.pfsId}/{bill.id}_{handleStartTime yyyyMMddHHmmss}.{mp3|wav}
```

`record_type==1` 用 `.wav`，其它情况用 `.mp3`。URL 是按话单字段拼出来的，**没有使用合音消息里的 `fileName`**。host 应该指向 vi-mdown（Xcache）内网下载地址，待确认。

### 7.2 ETCD（xdiscovery）

- 服务注册：`com.cmic.vi.billcenter` → `server.address:server.port`，集群名为 `parameters-config.cluster-name`。
- 服务发现：Watch `parameters-config.httpproxy-name`，但没有任何代码调用 `GetServiceNode`。
- 配置变更：Watch key `/config/version`（PUT 时 reload 缓存）。ETCD 里实际的 key 前缀格式由 xdiscovery 库决定，待确认。

---

## 8. 数据

### 8.1 MySQL

| 数据源（`@DS`） | 表 | 读 / 写 | 用途 |
| --- | --- | --- | --- |
| `vi_bill` | `bill_new` | 读（`SELECT * WHERE id=?`；重建时按状态和时间分页）、写（`UPDATE left_file_length,right_file_length,file_duration`；`UPDATE quality_inspection_status`；`DELETE WHERE id=?`） | 在途话单，由 vi-AS 插入 |
| `vi_bill` | `bill_yyyy_MM_dd` | 写（INSERT 全部 44 列）；`findBillFromHistory` 有读方法但没有调用 | 按 `handle_start_time` 的日期分表，表结构来自 `bill_template`；建表靠存储过程（话单DDL.docx："需要创建自动建表存储过程，按天分表"） |
| `user_qc`（primary） | `company_tab` | 读全表 | 三级企业，取 `app_key/app_secret` 做 EOPAUTH 签名 |
| `user_qc` | `customer_tab` | 读全表 | 四级客户，用 `app_id`（作为 channelId）和 `scenario_id`（场景）；`result_push_url` 由 vi-pusher 使用 |
| `user_qc` | `enterprise_scene` | 读全表 | `id → word_package_id` |
| `dcc_conf` | `file_down_host` | 读（`host_type = 2`） | `location → host`，录音内网下载域名 |

`bill_new` 关键字段（来源：`c/dao/BillDao.billMap` 映射，以及 `/workspace/vi-assets/话单DDL.docx`）：

| 字段 | 含义 |
| --- | --- |
| `id` varchar(32) | 话单 ID，即 callId / listId |
| `mobile_id` / `channel_id` / `company_id` / `customer_id` | 一级移动 / 二级移动 / 三级企业 / 四级终端客户 |
| `app_id` | 四级客户的 app_id |
| `account_type` | 1 测试，2 联调，3 生产 |
| `location` | 呼叫触发节点（南 / 北） |
| `as_id` / `ms_id` / `pfs_id` | AS、MS、PFS 节点 ID |
| `caller_number` / `callee_number` / `original_*` / `biz_number` | 主叫、被叫、原始号码、业务号码（敏感） |
| `handle_start_time` / `handle_end_time` | AS 处理起止时间；起始时间决定历史分表和录音路径 |
| `call_forward_time` / `ring_time` / `call_start_time` / `call_end_time` | 接续、振铃、通话起止时间 |
| `call_duration_s` / `call_duration_m` | 通话时长（秒 / 分） |
| `file_duration` / `left_file_length` / `right_file_length` | 录音时长、左右腿录音长度，由本服务回写 |
| `q850`、`media_l_direction`、`media_r_direction`、`call_state` | 信令信息 |
| `finish_type` | 结束发起方：0 平台，1 主叫，2 被叫 |
| `finish_state` | 话单状态，见 §9 |
| `append_reason` | 附加原因 |
| `is_bill_ok` / `is_record_ok` | 话单 / 录音推送状态（vi-pusher 使用） |
| `record_type` | 1 wav，2 mp3 |
| `quality_inspection_status` | 质检状态，见 §9 |
| `call_out_area_code` / `call_in_area_code` | 区号 |

**CONFLICT**：`/workspace/vi-assets/二期话单DDL.docx` 给 `bill_new` / `bill_template` 新增了 `quality_inspection_segment_result`、`quality_inspection_final_result`、`control_action` 三列，但本仓库代码既不读也不写，历史表 INSERT 也没带这三列，迁移时会取默认值。二期实时分片质检的结果落库应该不在本服务，待确认。

### 8.2 Redis / MongoDB / 文件

- Redis：不使用。
- MongoDB：不使用。
- 文件存储：不直接读写 PFS，只拼录音下载 URL（§7.1）。
- 本地文件只有日志，其中 `recover.log` 记录合音消息重投失败的话单 ID。

### 8.3 ETCD key

- `/config/version`：Watch，变更时 reload 缓存
- 服务注册：`com.cmic.vi.billcenter`
- 服务发现：Watch `{httpproxy-name}`

---

## 9. 状态与枚举

| 名称 | 取值 | 含义 | 出处 |
| --- | --- | --- | --- |
| `quality_inspection_status` | 0 | 未发起 | DDL 注释 |
| | 1 | 已发起（qa-server 返回 0000） | `RecordService` L99、`QualityCheckRecreateService` |
| | 2 | 质检成功（回调 `status=="1"`） | `QualityCheckCallbackService` L29 |
| | 3 | 推送质检结果成功 | vi-pusher 写入 |
| | -1 | 发起质检失败 | `RecordService` L102 |
| | -2 | 质检处理失败（回调 `status!="1"`） | `QualityCheckCallbackService` L31 |
| | -3 | 推送质检结果失败 | vi-pusher 写入 |
| 回调 `status` | "1" / 其它 | qa-server 子任务成功 / 失败。qa-server 中部分成功也按成功处理（`result_consumer.py` 中 `status if status=="0" else "1"`） | — |
| 合音消息 `code` | 0 / 1 | 合音成功 / 失败 | `RecordReceiver` |
| `finish_state` | 0 = `NEED_ASR`；1 主叫 BYE；2 被叫 BYE；3 主叫 CANCEL；5 忙；7 路由失败；8 未绑定；10 关机；11 停机；12 拒接；15 黑名单拦截；…；99 其它 | 本服务只区分 `==0` 和 `!=0`。完整枚举在 vi-as `state/FinishState.java` | `BillService` L62 |
| `record_type` | 1 | wav（其它值按 mp3 处理） | `QualityCheckService.createRecordUrl` |
| `CommonResult.code` | `0000` / `9999` | 成功 / 失败 | `c/pojo/vo/CommonResult` |
| `RecordSender` 常量 | `X_CAINIAO=18`，`EXT_ALI=15` | 中间号时代遗留，没有使用 | `c/rocketmq/RecordSender` |
| `parameters-config.location` | 1 南方节点，2 北方节点 | 决定录音 host | `config.prod/application.yml` 注释 |

Prometheus 指标（`c/metrics/BillMetrics`）：

| 指标 | 含义 |
| --- | --- |
| `vi.bill.request.total` | 未接通话单请求数 |
| `vi.bill.create.total` | 话单推送数（死代码路径） |
| `vi.asr.request.total` / `vi.asr.error.total` | 遗留，未使用 |
| `vi.record.total` | 录音推送数（死代码路径） |
| `vi.record.error.total` | 合音失败数 |
| `vi.get.connection.timeout.total` | 取 DB 连接超时数（按异常信息里含 "wait millis" 判断） |
| `vi.inspection.create.total` / `.success.total` / `.failure.total` | 发起质检的请求数 / 成功数 / 失败数 |
| `vi.inspection.callback.success.total` / `.failure.total` | 质检回调处理成功 / 失败数 |
| `vi.inspection.result.send.total` | 质检结果投递 MQ 数 |

---

## 10. 定时任务

- `@EnableScheduling` 已开，但**没有生效的 `@Scheduled` 方法**。
- `c/schedule/BillNewCreateTaskFailClear` 整个文件被 `/* */` 注释。原来计划每天 3 点（或每分钟）重建 `-1` 话单，开关为 `pusher.billnewclear`，现由 `POST /vibillcenter/task/recreate` 代替。
- 在途话单的超时清理（例如接通后一直没收到合音、合音失败的话单）**本仓库没有实现**。另外检查了 vi-cront-jobs、qa-server、scheduler，也没有找到操作 `bill_new` 的逻辑（待确认是否有 DBA 脚本）。

---

## 11. 关键配置项

位于 `config.prod/application.yml`。敏感值只写名称；dev / test 环境的 application.yml 不在仓库中，各环境差异只能看出由 `{{占位符}}` 渲染。

| 配置项 | 含义 |
| --- | --- |
| `server.port` | 业务端口 9277 |
| `server.address` | 绑定 IP。同时用于拼质检回调 URL 和 ETCD 注册地址，**必须是 qa-server 能直接访问的地址** |
| `rocketmq.producer.endpoints` / `access-key` / `secret-key` | 生产者接入点和凭据（占位） |
| `rocketmq.producer.max-attempts` | 发送重试次数，3 |
| `rocketmq.producer.request-timeout` | 发送超时，5 |
| `rocketmq.push-consumer.endpoints` / `access-key` / `secret-key` | 消费者接入点和凭据（占位） |
| `spring.jackson.time-zone` | GMT+8 |
| `spring.datasource.dynamic.primary` | 默认数据源 `user_qc` |
| `spring.datasource.dynamic.datasource.user_qc.*` | 用户 / 企业配置库（`company_tab`、`customer_tab`、`enterprise_scene`），即设计图中的"专线质检配置库" |
| `spring.datasource.dynamic.datasource.dcc_conf.*` | DCC 配置库（`file_down_host`） |
| `spring.datasource.dynamic.datasource.vi_bill.*` | 话单库（`bill_new`、`bill_yyyy_MM_dd`） |
| 各数据源 `druid.*` | `initial-size=1`、`min-idle=5`、`max-active=50`、`max-wait=1000ms`、`validation-query=SELECT 1`、`test-while-idle=true` |
| `management.server.port` / `address` | 管理端口 19277 |
| `management.endpoints.web.base-path` | `/manage/info`，只暴露 prometheus |
| `parameters-config.location` | 节点：1 南，2 北。用于选录音下载 host |
| `parameters-config.etcd` | ETCD 集群地址列表（占位） |
| `parameters-config.version` | 读取但未使用 |
| `parameters-config.media-name` | `com.cmic.media`，代码未读取 |
| `parameters-config.httpproxy-name` | httpProxy 在 ETCD 中的服务名（占位），本服务只 Watch 不使用 |
| `parameters-config.qc-server-create-url` | qa-server（经代理 SLB）创建质检任务的 URL（占位） |
| `parameters-config.cluster-name` | ETCD 集群名 `vi` |
| `parameters-config.billConsumerCount` | 通话消息和通话延时消息的消费线程数，50 |
| `parameters-config.recordConsumerCount` | 合音消息消费线程数，40 |
| `parameters-config.delayRecordConsumerCount` | 合音延时消息消费线程数，30 |
| `pusher.billnewclear` | 被注释的定时类的开关，yml 里没有配置 |

---

## 12. 坑与特殊逻辑

1. **所有 Consumer 都无条件 ACK**，返回 `ConsumeResult.SUCCESS`。DB 取连接超时、DB 异常、质检请求失败等情况都不会触发 RocketMQ 重投；`RecordService` 里明确写着 `//todo:推延时队列` 却没实现，这类消息直接丢失。
2. **接通话单没有合音等待超时和兜底**。`isPickUP=true` 时只等合音消息，合音一直不到或 `code=1` 合音失败时，话单永远停在 `bill_new`，`quality_inspection_status=0`，不会发起质检也不会移表。仓库内没有清理任务。
3. **合音先于话单到达**时有 60s × 3 次的延时重试，之后只写 `recover.log`，需要人工根据日志补偿（待确认是否有运维流程）。
4. **未接通话单的"等一下"逻辑名不副实**。`finish_state==0` 时延时 5s，但 `BillDelayReceiver` 到期后不重新判断，直接移表。如果 5s 后 vi-AS 仍没更新，话单会带着 `finish_state=0` 进入历史表。`BillDelayReceiver` 还要求 `billId` 是纯数字（`StringUtils.isNumeric`），而 `id` 是 varchar(32)，非数字 ID 会被静默跳过，这类话单永远留在 `bill_new`（待确认 ID 生成规则）。
5. **G.729 接通话单不做质检**。vi-AS 对"只支持 G729"的接通话单发 `isPickUP=false`，本服务把它当未接通处理，直接移表。如果合音模块仍发了合音消息，此时 `bill_new` 已查不到，会走 3 次延时重试，最终写进 `recover.log`，形成噪声。
6. **没有幂等**。`getRecording` 不检查 `quality_inspection_status`，重复的合音消息（例如 vi-merge 和 offline-merge 都发）会重复创建质检任务。回调也没有去重。
7. **`getError` 必然 NPE**。它读的是 `param.get("id").toString()`，但合音消息只有 `callId`，没有 `id`（参考 offline-merge `DccBillMessage`）。NPE 被 Receiver 捕获，合音失败计数在 NPE 之前已经加过，所以只是日志里多一条异常栈。
8. **回调查不到话单也返回成功**，qa-server 不会重推。话单已被 vi-pusher 移表后再收到重复回调属于正常情况，但如果是因为 DB 抖动而查不到，结果会丢失。
9. **回调处理顺序**：先改 DB 状态再发 MQ。MQ 发送失败会返回 `9999`，触发 qa-server 重推；重推时会重复改状态，结果幂等。如果 MQ 实际发送成功但随后超时报错，会造成 vi-pusher 重复推送给客户。
10. **质检请求失败不自动重试**，只能调 `/task/recreate`。该接口默认时间窗硬编码为 2026-07-02 ~ 2026-07-05，调用时必须显式传时间。
11. **硬编码的业务参数**：`customizedDetectionId`、`deepSeekPromptId=38`、`qwenPromptId=39`、`ragTopK=1`、`rtType=0` 写死在代码里，所有客户共用同一个定制流程，没有按客户配置。`customer_tab.script_id`、`url`（质检复核回调地址）、`ability`、`status`、`expire_time`、`total_minutes`、`used_minutes` 都没有参与判断，也就是说**不校验客户是否开通质检、是否过期、额度是否用尽**（待确认是否由 qa-server 校验）。
12. **`/vibillcenter/test` 调试接口在生产环境暴露**，没有鉴权，会用固定的 companyId=15、customerId=30 真实调用 qa-server。`/task/recreate` 同样没有鉴权。
13. **日志包含敏感信息**。`QualityCheckService` 以 INFO 级别打印完整的请求头（包含 Authorization 签名）和请求体（`userData` 中有主被叫号码）。`subtaskCallback` 以 INFO 级别打印完整回调，可能包含 `transcription` 转写文本、`summary`、号码。这违反"日志不得写真实号码 / 录音文本"的要求，建议脱敏。
14. **移表不是事务**。`createHistory` 先 INSERT 历史表再 DELETE `bill_new`，两步之间崩溃会导致两边都有数据，下次再移表会报 Duplicate entry。当日分表不存在（存储过程没建）时 INSERT 失败，话单留在 `bill_new`。
15. **`updateBillNewRecord` 失败后照样发起质检**，`file_duration` 等字段可能没落库。
16. **死代码**：`BillSender`、`RecordSender`（及对应的 `TID_VI_BILLCENTER_*_PUSH_PUSHER_NORMAL` topic）、`BillHistoryDao.findBillFromHistory`、`Obfuscate`、`MD5Util`、`CommonUtil.processUserId` 等、`WatchService(httpproxy-name)`、`media-name`、`version`、`asr*` 指标。这是从中间号 dcc-billcenter 改造来的痕迹。
17. **构建坑**：`dev` profile（默认激活）的 manifest `mainClass=com.cmic.BillCenterApplication`，而实际类是 `com.cmic.ViBillcenterApplication`，按 dev 打出的薄 jar 用 `java -jar` 启动会找不到主类。`prod` profile 由 spring-boot 插件自动探测主类，不受影响。另外 `pom.xml` 里 `httpclient5:5.5` 重复声明了两次。
18. **缓存线程安全**：缓存是 `public static HashMap`，reload 时整体替换引用。没有 `volatile`，可见性靠 JVM 时序保证（实际风险低）。`init()` 会先清空再加载，启动期间有短暂的空窗。
19. **`CommonResult.isSuccess()` 用 `==` 比较字符串**，目前没有调用方。
20. **RestTemplate 关闭了主机名校验**（`NoopHostnameVerifier`）。
21. `RocketMQBeanFactoryPostProcessor` 把 RocketMQ 自动配置相关 Bean 标记为 `ROLE_INFRASTRUCTURE`，目的是避免它们被 BeanPostProcessor 过早初始化时产生告警。这是有意为之，不要删。

---

## 13. 与其它仓库的关系

| 方向 | 对端 | 契约 |
| --- | --- | --- |
| 上游 | vi-AS（`vi-as-master`） | MQ `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL`，字段 `{billId,isPickUP,dbId}`（vi-as `service/BillCenterService.sendMsgToBillCenter`）。表 `vi_bill.bill_new` 由 vi-AS 插入和更新，本服务读取并改写部分字段 |
| 上游 | vi-merge / offline-merge | MQ 合音结果（本服务订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`，与对端配置的 topic 名不一致，见 §6 CONFLICT），字段 `{code,callId,leftFileLength,rightFileLength,fileDuration,...}` |
| 下游 | qa-server（经代理 SLB） | HTTP 创建质检任务（`qc-server-create-url`，推测为 `/v1/task/customize`），EOPAUTH 鉴权，请求体见 §7.1 |
| 上游（回调） | qa-server | HTTP `POST /vibillcenter/subtask/callback`、`/vibillcenter/task/callback`。qa-server 在响应不是 `code=="0000"` 时走延时队列重推 |
| 下游 | vi-pusher | MQ `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`，原样转发回调体。vi-pusher 推送后更新 `quality_inspection_status=3/-3` 并移表 |
| 共享表 | vi-pusher | `bill_new`、`bill_yyyy_MM_dd`、`company_tab`、`customer_tab`、`file_down_host`。两边的 DAO 和实体是复制粘贴的，改字段要两边同步 |
| 间接 | vi-mdown（Xcache）/ PFS | 录音 URL `/internal/media/...` 由 qa-server / ASR 按 URL 拉取（设计图中 ASR 拉取文件） |
| 配置 | 对客质检统一运营平台 | 写 `user_qc` 配置库，并通过 ETCD `/config/version` 通知各服务 reload（推断，待确认） |
| 注册 | ETCD | 服务名 `com.cmic.vi.billcenter` |

---

## 14. 待确认问题清单

1. 合音 topic 名不一致：本服务订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`，vi-merge 配置 `TID_VI_MERGE_MSG_BILL_NORMAL`（namespace VI），offline-merge 配置 `TID_VI_MERGE_BILL_RECORD_NORMAL` / `TID_DCC_MERGE_MSG_BILL_NORMAL`。哪个是生产实际值？
2. `qc-server-create-url` 实际指向 qa-server 的哪个接口（`/v1/task/customize`？），代理 SLB 的超时是多少？
3. 硬编码的 `customizedDetectionId`、`deepSeekPromptId`、`qwenPromptId` 是否应该按客户或场景配置？`rtType="0"` 的含义是什么？
4. 接通后一直没收到合音、或合音失败的 `bill_new` 记录由谁清理和补偿？`recover.log` 有没有对应的人工补偿流程？
5. `bill_new.id` 是否一定是纯数字？如果不是，`BillDelayReceiver` 会静默跳过。
6. 二期新增的 `quality_inspection_segment_result`、`quality_inspection_final_result`、`control_action` 由哪个服务写入？本服务和 vi-pusher 都没有处理。
7. dev / test 环境的 `application.yml` 放在哪里？`{{占位符}}` 由什么工具渲染？
8. 是否需要校验客户的质检能力开通状态、有效期、分钟额度（`customer_tab.ability/status/expire_time/total_minutes`）？还是由 qa-server 负责？
9. `file_down_host`（`host_type=2`）对应 vi-mdown 的哪个内网入口？qa-server / ASR 是否能直接访问？
10. `/vibillcenter/test` 和 `/vibillcenter/task/recreate` 在生产环境是否有网关层鉴权或访问控制？
11. 质检回调直连实例 IP（`server.address`）。多实例或容器化部署时，回调地址是否可达、是否稳定？
