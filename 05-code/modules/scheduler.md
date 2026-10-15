---
id: module-scheduler
title: scheduler 代码深度解析（质检编排与模型调度）
domain: ai
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-scheduler]
source_of_truth: [vi-assets/scheduler-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/scheduler-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/scheduler.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# scheduler-master 分析报告（质检调度服务）

> 分析对象：`/workspace/vi-assets/scheduler-master`（Java，277 个 `.java` 源文件，约 1.65 万行；另含 `sources/` 下 78 个流程 XML）
> 结论均来自代码；无法从仓库确认的标注“待确认”。所有路径相对于仓库根目录。
> 仓库内**没有** `application.yml`（被 `.gitignore` 忽略：`src/main/resources/application.yml`），配置项名称均由 `@Value` / `@ConfigurationProperties` 反推，取值与各环境差异无法从仓库得到。

---

## 1. 定位

scheduler 是专线质检链路里的**质检编排与 AI 模型调度中心**。上游 qa-server 把一批待质检的通话（一个 Task，含若干子任务 SubTask，每个对应一通录音 / 一段文本 / 实时流的一个分片）以 RabbitMQ 消息投递到 `scheduler.task.queue`。scheduler 按 `processId` 找到对应的**流程 XML**（事件—条件—动作 DSL），把每个子任务依次投递给 ASR、脱敏、关键词/反诈、通义千问、RAG、融通、DeepSeek、声纹、鉴伪等模型（模型侧都是 MQ 适配器，scheduler 本身不发 HTTP）。模型结果写入 MongoDB `sub_task.responseResults`，再由 XML 条件决定下一步。子任务结束后发子任务回调消息（type=1），全部子任务回调完或超时后发主任务回调消息（type=2）。两类回调都发到 `scheduler.callback.exchange` 交给 qa-server。设计图里的 “anti-fraud” 在代码中并不是单独一个服务，而是一组反诈模型队列：`keyword`（关键词反诈 / keywordAnalysis）、`rongtong`、`rt-fraud-review`、`iterative_anti_model`（BGE）、`chongqing-anti`、`cqagent`、`deepseek`、`qwen`、`rag` 等。

---

## 2. 技术栈

| 项 | 内容 | 代码位置 |
| --- | --- | --- |
| 语言/JDK | Java 21 | `pom.xml` `<java.version>21` |
| 框架 | Spring Boot 3.2.12（spring-framework 覆盖为 6.2.3，tomcat 10.1.35） | `pom.xml` |
| MQ | spring-boot-starter-amqp（RabbitMQ），手工 ack，优先级队列 | `config/rabbitmq/*` |
| 存储 | spring-boot-starter-data-mongodb（主存储）；spring-boot-starter-data-redis；HikariCP 连接 StarRocks（MySQL 协议，只读 `qa_detail`）；clickhouse-jdbc 依赖存在但配置类已注释；mysql-connector 8.0.11 依赖存在但**运行时无 MySQL 数据源** | `config/StarRocksDataSourceConfig.java`、`config/ClickHouseDataSourceConfig.java`（`//@Configuration`） |
| 表达式/脚本 | Spring SpEL（流程条件）；groovy 3.0.9 依赖 + 3 个 groovy 脚本（**未被任何 Java 代码加载**） | `engine/condition/*`、`src/main/resources/groovy/*` |
| XML 解析 | dom4j 1.6.1 | `config/xml/Dom4jUtil.java` |
| 其它 | hutool 5.7.20（Snowflake、JSON）、guava 31.1（RateLimiter）、lombok、aspectj、micrometer-prometheus + 原生 prometheus `CollectorRegistry` | `pom.xml` |
| 构建 | Maven；`spring-boot-maven-plugin` 生成 `scheduler-jar-with-dependencies.jar`；`maven-assembly-plugin` 按 profile 打 `tar.gz`；打包跳过测试 | `pom.xml`、`config/assembly.xml` |
| 启动入口 | `src/main/java/com/cmic/SchedulerApplication.java:SchedulerApplication.main`（`@EnableScheduling @EnableAsync @EnableMongoAuditing`） | |
| 监听端口 | 仓库内未定义（`server.port` 在缺失的 application.yml 中，待确认）。`src/test/shell/curl.sh` 使用过 6666 端口调用旧接口（历史值，待确认） | |
| 部署脚本 | `bin/`、`bin.test/`、`bin.prod/` 各有 `start.sh` / `stop.sh` / `restart.sh` | 见下 |

### 2.1 部署脚本与环境区分

- profile：`dev`（默认激活）/ `test` / `prod`，属性 `env`（`pom.xml <profiles>`）。
- `config/assembly.xml` 打包内容：`bin.${env}` → 包根目录；`config.${env}` → `config/`；**整个 `sources/`（dev/test/prod 三个子目录全部）** → `sources/`；`target/*.jar`。
  - 注意：dev profile 会去找 `bin.dev` 目录，但仓库里只有 `bin/`、`bin.test/`、`bin.prod/`，**dev 包里没有启动脚本**（待确认是否有意为之）。
- `start.sh`：
  - `bin/start.sh`、`bin.prod/start.sh`：`java -server -Xms16g -Xmx16g -Xmn4g -XX:+UseParallelGC -classpath ./config -jar scheduler-jar-with-dependencies.jar --logging.config=config/logback.xml`
  - `bin.test/start.sh`：堆 `-Xms4g -Xmx6g -Xmn1g`，其余相同。
  - 脚本里没有 `nohup` / `&`，进程在前台运行（待确认外层如何托管）。
- `stop.sh`：用 `ps -ef | grep java | grep "$CONF_DIR"` 找进程，但 `start.sh` 的命令行里只有相对路径 `./config`，不含绝对路径 `$DEPLOY_DIR/config`，**可能匹配不到进程**（待确认）。shebang 写成了 `#!/bin.test/bash`（三个目录的脚本都这样，路径非法，只能靠 `sh xxx.sh` 方式调用）。
- `bin.test/restart.sh` 调用的是 `./bin/stop.sh`、`./bin/start.sh`；打包后 `bin.test` 的内容被平铺在包根目录，并不存在 `bin/` 子目录，该脚本大概率无法使用。
- logback（`config.{dev,test,prod}/logback.xml`）：

| 项 | dev | test | prod |
| --- | --- | --- | --- |
| LOG_HOME | `logs` | `/var/log/scheduler` | `/var/log/scheduler` |
| maxHistory | 7 | 15 | 15 |
| maxFileSize | 1MB | 200MB | 200MB |
| INFO appender | 有 | 无 | 无 |

  `src/main/resources/logback.xml` 是 jar 内默认值（`logs`、7 天、100MB）。日志 pattern 带 MDC `traceId`（`util/TraceIdUtil`，取值为 `transactionId-jobId`）。
- 流程 XML 的环境区分：`sources/dev|test|prod` 是**流程定义目录**，不是 Spring 配置。上传、读取时的路径是 `${xml.path}/${spring.profiles.active}/<xmlFileName>.xml`（`service/ProcessService.java:ProcessService.upload / queryXmlFile / update`）。但启动和重载时**递归扫描整个 `${xml.path}`、按文件名取第一个匹配**（`service/XmlEventLoaderService.java:getFiles`、`util/CommonUtil.findFileByName`），不区分环境目录（见 §12）。

---

## 3. 代码结构（全部包）

`src/main/java/com/cmic/` 下的包：

| 包 / 目录 | 文件数 | 职责 |
| --- | --- | --- |
| `(根)` | 1 | `SchedulerApplication` 启动类 |
| `component` | 2 | `ParamExceptionHandler`（参数校验异常 → 400）；`PropertyPlaceholders`（把 XML 表达式里的 `${x}` 替换成 SpEL 变量 `#x`，取值来自 subTask.responseResults 或 task.attributes） |
| `config` | 10 | 应用配置：`ApplicationProperties`（`xml.path`、`rateLimiter.*`、`common.atomicProcessId`）、线程池 `AsyncTaskConfig`、`EventConfig`（Spring 事件**同步**广播）、Redis 序列化、StarRocks 数据源、ClickHouse（停用）、Mongo 监听器、Snowflake 属性（未使用）、`FixedSizeLinkedHashMap`（LRU）、JDBC 监控代理 |
| `config/metrics` | 4 | Prometheus 指标定义（`MetricsBean`）、业务埋点（`CommonMetrics`）、DB/Redis/Mongo 错误埋点（`DBMetrics`，含 RedisUtil AOP）、任务创建/回调耗时切面（`PrometheusAspect`） |
| `config/rabbitmq` | 20 | 全部 MQ 拓扑常量与动态声明（`MQBeanConfig`、`DynamicBeanRegistry`）、南向连接工厂与 RabbitTemplate（`SouthRabbitMqConfig/ProducerConfig/ConsumerConfig`）、北向（整体已停用：`NorthRabbitMqConfig`、`NorthMessageConsumer` 的注解被注释）、消费者：任务创建 `TaskConsumer`、模型响应 `SouthMessageConsumer`→`MessageConsumer`、主任务回调去重 `TaskDerepeatConsumer`（仅 master）、主任务回调执行 `TaskLBCallbackConsumer`、旧主任务回调 `TaskCallbackConsumer`、流程热更新广播 `ProcessSender`/`ProcessConsumer`；消费限流 `MQConsumerRateLimiter`；发送并发 `MqSendProperties`；confirm/return 回调（已停用） |
| `config/xml` | 14 | 流程 XML 模型（`EventXml`、`ActionXml`、`ConditionXml`、`IfElseXml`、`ImportXml`、`ParamXml` 等）、解析器 `Dom4jUtil`、执行器 `XmlService`（事件 → 动作/条件）；`XmlLoader` 已停用（`//@Configuration`） |
| `controller` | 5 | HTTP：任务创建 `TaskController`、流程管理 `ProcessController`、AI 模型管理 `AIModelController`、AI 类型管理 `AITypeController`、`EventController`（TODO 下线） |
| `dao` | 14 | MongoDB Repository 与 MongoTemplate 实现（task / sub_task / process / his_process / ai_model / ai_type / model_meta / shard_task），以及 StarRocks `qa_detail` 只读 DAO `QaDetailDaoImpl` |
| `engine` | 2 | `XmlEventPublisher`（发布 Spring 事件）、`XmlEventListener`（监听事件 → 调 `XmlService.handlerEvent`） |
| `engine/action` | 40 | XML `<action application=...>` 的实现，`ActionFactory` 按 application 名分发（共 38 种，未知名走 `NullAction`） |
| `engine/condition` | 5 | XML `<if application=...>` 条件实现：`CalculateCondition`（默认，`#calculate.xxx()` 工具函数）、`TaskCondition`（`#task`/`#job` 实体）、`RedisCondition`（`#call.xxx` 读 Redis） |
| `engine/event` | 4 | 事件类型：`MqEntityEvent`（主用，携带 Task+SubTask 实体）、`MqEvent`（事件名常量 + 仅 id 的旧事件）、`HttpEvent`、`EventOrigin` |
| `engine/strategy` | 2 | 回调属性覆盖策略：`RiskLevelOverrideStrategy`（riskLevel 只能由低风险升级为高风险） |
| `pojo/bo` | 9 | `AIModelEnum`（模型名、状态键、结果键、原子开关）、`Attribute`（responseResults 键名字典）、`TaskMqBo`（主任务回调内部消息）等；`JobRedisBo`、`TaskRedisBo`、`JobResponseBo` 为旧版遗留，未使用 |
| `pojo/dto` | 19 | 入参出参：`TaskRequest`（任务创建消息体）、`BaseResponse`（统一响应 / 模型响应外壳）、`ErrorCode`、`CallbackJobRequest`、`CallbackRequest`、`CallbackModelRequest`、`ProcessRequest` 等；`TaskSmartWalnutsRequest`、`RequestFile`、`ASRTextRequest`、`RequestExtParameter` 为旧版遗留 |
| `pojo/dto/ai` | 49 | 各模型 MQ 请求/响应 DTO（见 §7） |
| `pojo/entity` | 9 | Mongo 实体：`Task`、`SubTask`、`Process`、`HisProcess`、`AIModel`、`AIType`、`ModelMeta`、`ShardTask`；StarRocks 行 `QaDetail` |
| `service` | 12 | 回调 `CallbackService`、子任务结束 `JobService`、主任务超时扫描 `TaskCallbackSchedulerService`（仅 master）、MQ 发送 `MqService`、流程管理 `ProcessService`、XML 加载缓存 `XmlEventLoaderService`、Redis 封装 `RedisService`、回调属性 `AttributeService`、本地关键词匹配 `KeywordService`、AI 模型元数据 `AIModelService`/`ModelMetaService`/`AITypeService` |
| `service/ai` | 32 | 每个模型一个 Service（实现 `IMqService.sendToMq / handlerMessage`），`MqServiceFactory` 把响应队列映射到 Service 和 `AIModelEnum` |
| `service/task` | 4 | `TaskService`（建任务、主任务回调）、`SubTaskService`（子任务读写、模型状态打点）、`ProcessJobService`（逐个子任务触发 `initial` 事件、ASR 结果复用）、`ShardTaskService`（实时流分片串行化） |
| `util` | 10 | `SnowflakeUtils`（id 生成，workerId 取 `service.instance.id`）、`ModelUtil`（`jobId_dataId` 拼接/拆分）、`TransformUtil`、`RedisUtil`、`JacksonUtil`、`CommonUtil`、`TraceIdUtil` 等 |

资源与其它目录：

| 目录 | 内容 |
| --- | --- |
| `src/main/resources/mysql/` | `task.ddl`、`sub_task.ddl`、`process.sql`（**旧版 MySQL 模型，运行时未使用**，见 §8.1） |
| `src/main/resources/groovy/` | `Qc4Condition.groovy`、`Qc4Condition2.groovy`、`Qc4Event.groovy`（原型代码，未被加载） |
| `config/groovy/rules` | YAML 片段 `rule1 → Qc4Event.groovy condition/action`（未被加载） |
| `sources/{dev,test,prod}/` | 流程 XML：dev 61 个、test 3 个、prod 14 个 |
| `src/test/` | JUnit 样例与 `shell/`（rabbitmqadmin 模拟 ASR/场景回包、旧接口 curl） |

---

## 4. 核心业务流程

### 4.1 任务创建（MQ 主入口）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| 1 | 消费 `scheduler.task.queue`，反序列化为 `TaskRequest`，MDC traceId = transactionId | `config/rabbitmq/TaskConsumer.java:TaskConsumer.consumerTask` | 任何异常都 `basicNack(requeue=false)` 丢弃消息（队列未声明 DLX，待确认 broker 侧是否配置） |
| 2 | 校验：requestId 非空；priority 匹配 `^(10\|[0-9])$`；processId 非空且流程启用；transactionId 非空；data 非空且 < 100000 条；requestId 不重复；每条 data 都有非空 `dataId` | `service/task/TaskService.java:TaskService.validTask` | 校验失败调 `CallbackService.callbackTaskError`，但该方法发送那一行被注释，**实际什么也不发**，消息 ack 后静默丢弃 |
| 3 | 生成 taskId（Snowflake）；先对每条 data 生成 SubTask（jobId 也是 Snowflake，`attributes`=原 data map，`location` 默认 1），`insertAll` 到 `sub_task` | `TaskService.storeJob`、`util/TransformUtil.transformRequestToSubTask` | 无事务（`@Transactional` 对 Mongo 无效，无 MongoTransactionManager） |
| 4 | 保存 Task 到 `task`（`attributes`=`parameter`、`jobCount`=data.size、`stream`=isStream） | `TaskService.storeTask`、`TransformUtil.transformRequestToTask` | requestId 并发重复时 `requestId` 唯一索引抛异常 → nack 丢弃，**已写入的子任务成为孤儿** |
| 5 | ASR 结果复用：筛选 `attributes.reuse==1` 的子任务 → Redis 过滤出 key 存在的 dataId → StarRocks `qa_detail` 按 data_id 批量查（每批 1000）→ 命中的把 asrStatus/asrText/audioDuration/asrInfo 预写入 responseResults | `service/task/ProcessJobService.java:updateIfReuseAsrResult` | 异常仅打日志，不影响建任务；StarRocks 查询超时 30s，某批失败就提前返回已查到的部分 |
| 6 | 逐个子任务：若 data 中有 `text`，把它当作 `asrText` 写入；发布 `initial` 事件 → 同步执行流程 XML 的 `initial` 事件 | `ProcessJobService.processJob` | 单个子任务异常：`job_send_error` +1，`status=1`（推送失败），**不重试**（代码 TODO）；成功 `status=2` |
| 7 | 主任务 `status=2`（子任务全部已推送） | `TaskService.createMqTask` | 中间不会写 `status=1`（推送中） |

说明：
- Spring 事件广播器 `applicationEventMulticaster` 的 `taskExecutor=null`（`config/EventConfig.java`），所以**事件全部同步执行**，`initial` 事件里所有模型投递都在 TaskConsumer 线程内串行完成；10 万条子任务会长时间占住这个消费线程。
- HTTP 入口 `POST v1/front/task/create` 走 `TaskService.createTask`，逻辑相同，但异常时返回 `1001`，已写数据不回滚。

### 4.2 模型调度与响应（通用模式）

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| 1 | XML 动作 `application=xxx op=sendToMQ` → `ActionFactory` → `XxxAction` → `XxxService.sendToMq` | `engine/action/*`、`service/ai/*` | 动作抛出的异常一路向上，在 initial 阶段由 `ProcessJobService` 捕获；在响应阶段由 `MessageConsumer` 捕获并 nack |
| 2 | 组装请求：`jobId = <subTaskId>_<dataId>`（`util/ModelUtil.buildModelJobId`），文本类模型取 `responseResults.asrText`，音频类模型取 `subTask.attributes.url` | 各 `XxxService.sendToMq` | — |
| 3 | `beforeSendModel`：在 `responseResults.<model小写>` 写 `{name,startTime,status=0}`；指标 `model_send_count` | `service/task/SubTaskService.java:beforeSendModel`、`dao/SubTaskDaoImpl.updateSubTaskModel` | — |
| 4 | 发送：`southRabbitTemplate.convertAndSend(exchange, routingKey, msg)`，消息 priority=task.priority，删除 `__TypeId__` 头；全局公平信号量控制并发（默认 16） | `service/MqService.java:sendPriorityMessage` | 未开启 publisher confirm / return；`mandatory=true` 但没有 ReturnCallback → 路由不到的消息**静默丢失**；`acquireTimeoutMs` 配置项未被使用（`semaphore.acquire()` 会无限等待） |
| 5 | 模型适配器回包到 `scheduler.<model>.response.queue`，消息体为 `BaseResponse{code,message,data{jobId,...}}` | `config/rabbitmq/SouthMessageConsumer.java` → `MessageConsumer.consumer` | — |
| 6 | 按队列名取 guava RateLimiter（`rateLimiter.rate` 每秒许可数）限流 | `MQConsumerRateLimiter.getRateLimiter` | 阻塞等待 |
| 7 | 从 `data.jobId` 截取 `_` 前的部分作为 subTaskId；加载 SubTask、Task | `MessageConsumer.consumer`、`ModelUtil.getOriginJobId` | jobId 为空直接 ack 丢弃；子任务不存在时抛 NPE → nack 丢弃 |
| 8 | 幂等：子任务已回调（`callback==1`）→ ack，计 `model_receive_timeout_count`；`responseResults.<该模型statusKey>` 已有值 → ack 丢弃 | `MessageConsumer.consumer` | 判断与写入之间不是原子操作（见 §12） |
| 9 | `XxxService.handlerMessage`：code==`0000` 时写 `xxxStatus=true` 加结果字段，否则 `xxxStatus=false`；`afterResponseModel` 写 `responseResults.<model>.{endTime,code,message,status(2成功/1失败)}`（findAndModify 返回新文档）；指标 `model_receive_*` | `SubTaskService.afterResponseModel` | — |
| 10 | 发布 `<model>_resp` 事件 → 执行流程 XML 中同名事件 | `XmlEventPublisher.mqEventPublish` | 事件内异常 → nack(requeue=false) 丢弃，子任务只能等主任务超时兜底 |

### 4.3 子任务结束与子任务回调（type=1）

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | XML `process op=success/fail/stop` → finish=1/0/2 | `engine/action/ProcessAction.java` | `fail` 可带 `<attribute name="model">`、`<attribute name="errorMessage">`，追加写入 `errorMessage`（JSON，key=模型） |
| 2 | 主任务已回调（内存中 `task.callback==1`）或子任务已回调则跳过 | `service/JobService.java:jobFinished` | 判断依据是本次消费开始时加载的实体快照 |
| 3 | 构造子任务回调 JSON：`type=1,dataId,status=finish,errorMessage,transactionId,models[...]`；再加上各模型结果字段（`AIModelEnum.getAttributes`）、Redis `attrs_callback<jobId>` 里的回调属性（默认补 `review="0"`）、`modelStatus{model:status}`；finish==0 时去掉 `qcResult`、`riskLevel` | `service/CallbackService.java:callbackJob(Task,SubTask)` | **整个方法 try/catch 只打 `e.getMessage()`**，发送失败或 NPE 时静默 |
| 4 | 模型列表：普通流程取 `process.aiModels` 按逗号拆分；原子流程（processId 在配置 `common.atomicProcessId` 中）取 task.attributes 里值为 `"1"` 的开关，映射成 `AIModelEnum` 名 | `CallbackService.callbackJob`、`AIModelEnum.getModelsByKeys` | aiModels 里出现枚举外的名字时 `AIModelEnum.getAttributes` 会 NPE → 回调丢失 |
| 5 | 每个模型的状态：responseResults 里没有该模型 → status=0 未执行；有 startTime 无 endTime → status=1、code=`9998` 超时；有 endTime → 用存储的 status/code/message | `CallbackService.getModelInfos` | — |
| 6 | 发送到 `scheduler.callback.exchange` / `topic.callback.key`；指标 `subtask_callback_result_count{status,qcResult,tag}` | `MqService.sendToCallback`、`CommonMetrics.statJobFinsh` | — |
| 7 | 写 `sub_task.callback=1, finish=x`（**在发送之后**，无论发送是否成功都会写） | `CallbackService.callbackJob(Task,SubTask,int,int)` | — |
| 8 | 统计已回调子任务数；等于 `task.jobCount` 时发送 `TaskMqBo{taskId,finish=1}` 到 `scheduler.task.repeat.queue` | `JobService.checkCallbackCount` | 两个子任务并发结束时可能都看到计数相等 → 发两次，依赖 master 内存去重 |
| 9 | 清理：`$unset responseResults.asrText / asrInfo / cqAgentDetail`；删除 Redis `attrs_callback<jobId>` | `JobService.doAfter`、`SubTaskDaoImpl.removeSubTaskAsr` | 回调完成后 MongoDB 不再保留转写全文 |

### 4.4 主任务回调（type=2）

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | master 实例消费 `scheduler.task.repeat.queue`，用进程内 LRU（1000 条）对 taskId 去重，再转发到 `scheduler.task.lb-callback.queue`（保留消息优先级） | `config/rabbitmq/TaskDerepeatConsumer.java` | 只有 `masterServer=true` 的实例才有这个消费者；**没有 master 时主任务永远不会回调**；去重状态重启即丢 |
| 2 | 任一实例消费 lb-callback：`task.finish=TaskMqBo.finish`；若 `needTaskCallback` 则先写 `task.callback=1` 再执行 `callbackTask` | `config/rabbitmq/TaskLBCallbackConsumer.java`、`TaskService.handlerMqTaskCallback(TaskMqBo)`、`doTaskCallback` | `doTaskCallback` 吞掉所有异常 |
| 3 | 遍历子任务：未回调的按超时补发子任务回调（`callback=1,finish=0`）；失败数 = 未回调数 + finish==0 的数量；`finishRate = (jobCount-fail)/jobCount*100` | `CallbackService.callbackTask`、`timeoutCallbackJob` | — |
| 4 | 发送 `{type:2, finishRate, failCount, total, transactionId}` 到回调 exchange | `CallbackService.callbackTask` | — |

### 4.5 超时兜底（定时任务）

见 §10。要点：每小时扫描一次 3 天内、`needTaskCallback=true`、`callback=0`、创建超过 24 小时的任务；priority > 2 的立即兜底，其它任务要等到满 48 小时；发送 `TaskMqBo{finish=2}` 到 repeat 队列，走 §4.4。**流程表里的 `process.timeout`（1–60 分钟）没有任何代码使用**。

### 4.6 实时流分片（`isStream=true`）

一通实时通话被拆成多个子任务（每个子任务 `attributes` 带 `segmentId`、`endFlag`，`dataId`=callId）。目标是同一 callId 的分片按顺序串行处理、ASR 文本累加。

| 步骤 | 做什么 | 代码位置 |
| --- | --- | --- |
| 1 | `initial` 中 `shard op=sendShard`：upsert `shard_task{callId}.sharkInfoMap.<segmentId>={subTaskId,endFlag,received=true,finish=false}`；segmentId==0 或前一片 `finish==true` 才发布 `shard_send_resp` | `service/task/ShardTaskService.java:sendShard / receiveShard`、`dao/ShardTaskDaoImpl.saveShard / preShardFinished` |
| 2 | `shard_send_resp` → ASR → 脱敏 | XML `sources/dev/zhuanxian_qc_2.xml` |
| 3 | `shard op=updateAsrText`：`shard_task.asrText += 本片 asrText`，`asrDuration` 累加，并把累加后的全文回写到当前子任务的 `asrText` / `audioDuration` | `ShardTaskService.updateAsrText` |
| 4 | 结束时 `shard op=finishShard`：本片 `finish=true`，下一片若已到达则对下一片的子任务发布 `shard_send_resp` | `ShardTaskService.finishShard / finishShardAndNext` |
| 5 | 中断：命中高风险后 `attribute type=2` 把 `interruptShard=1` 写入 Redis `attrs_callId<callId>`，后续分片通过 `redisCondition` 的 `#call.interruptShard=='1'` 直接结束 | `service/AttributeService.setValue`、`engine/condition/RedisCondition` |

CAS 抢发送权的代码（`tryMarkProcess`）已被注释，存在重复发送的竞态（见 §12）。

### 4.7 流程定义（Process）与 XML 编排

**流程元数据**存在 MongoDB `process`：`processId`（`p` + 6 位随机字符 + snowflake）、`processName`、`xmlFileName`（唯一）、`timeout`、`aiModels`、`status`、`requestId`（唯一）。**流程内容**是本地文件 `${xml.path}/${env}/<xmlFileName>.xml`。

加载与缓存：
- 启动时：`ProcessService.init` 把 `status=1` 的流程放进内存 `processMap`；`XmlEventLoaderService.init` 先加载 `${xml.path}export` 目录下的可复用 event/action（仓库中没有该目录），再为每个流程解析 XML，放进 `serviceEventsMap`。
- 运行时：`getEventXmls(processId)` 未命中时懒加载。
- 更新：`POST admin/process/update` → 当前版本（含 XML 文本）存入 `his_process` → 保存 process → 若上传了新文件则写本地并重载 → 向 fanout `scheduler.process.update.exchange` 广播 processId → 每个实例的 `scheduler.process.update.<service.instance.id>` 队列收到后重新读库并重新解析本地文件（`ProcessConsumer`）。

XML DSL（`config/xml/Dom4jUtil`、`config/xml/XmlService`）：

```text
<events>
  <event name="事件名">                       ← 事件名：initial / <model>_resp / 自定义（经 event.sendToEvent 触发）
    <action application="X" op="Y" type=".." data="..">   ← 顺序执行
       <attribute name="k">v</attribute>      ← 动作参数（如 model、text、kbType、deepSeekPrompt、qwenModelType）
    </action>
    <condition>                               ← 子元素按顺序求值，标签名无所谓（if / else-if / else 都由 doIf 解析）
       <if expression="SpEL" application="calculate|taskCondition|redisCondition"> actions </if>
       <else> actions </else>                 ← 没有 expression 的分支视为 else
    </condition>
    <import type="action" name="..."/>        ← 引用 export 目录中的 action
  </event>
  <import type="event" name="..."/>
</events>
```

- 默认条件 `CalculateCondition`：表达式里的 `${attr}` 先替换成 `#attr`，变量取自 `subTask.responseResults[attr]`；`${task.x}` 取 `task.attributes.x`，`${job.x}` 取 responseResults.x。提供 `#calculate.isTrue/isFalse/notNull/gt/gte/lt/lte/et/noteq/length/words/allEquals/allNotEquals`。
- `taskCondition`：直接暴露 `#task`（Task 实体）和 `#job`（SubTask 实体）。
- `redisCondition`：`#call.<field>` 从 Redis hash `attrs_callId<dataId>` 读字段。
- 一个 event 里可以放多个 `<condition>`，依次求值（例如 `zhuanxian_qc_2.xml` 的 `desensitization_resp`）。

**动作清单**（`engine/action/ActionFactory.action`）：

| application | op | 实现 | 说明 |
| --- | --- | --- | --- |
| `asr` | sendToMq | `ASRService.sendToMq` | `asrPrior==1` 走 fast ASR 队列；支持复用 |
| `jt_asr` | sendToMq | `JiutianASRService` | 九天 ASR |
| `desensitization` | sendToMq | `DSZService` | 脱敏，回包后**覆盖 `asrText`** |
| `keyword` | match | `KeywordService.match` | 本地关键词包匹配（`task.attributes.wordPackage`），同步产生 `keyword_resp` |
| `keywordAnalysis` / `keywordAnalysisLT` | sendToMq | `KeywordAnalysisService.sendToMq(event, "1"/"0")` | 关键词反诈模型（高 / 低阈值），共用 keyword 队列 |
| `word_group` | match | `KeywordGroupService.match` | 本地关键词组合（`wordGroup`） |
| `word_regex` | match | `KeywordRegexService.match` | 本地正则（`wordRegex`，由任务方提供正则） |
| `keyword_rs` | match | `KeywordRiskShieldService.match` | 本地高危禁词（`wordRisk`）；**只支持 op=match** |
| `scenario` / `script` / `vfd` / `vpr` / `vp` / `identity` / `emotion` / `aigs` / `collection` / `text_sml` / `rt_qc` / `rt_fraud_review` / `market_call` / `deal` / `bge` / `cq_anti` / `cq_agent` / `huawei_qc` | sendToMq | 对应 Service | 见 §7 |
| `rag` | sendToMq | `RAGService.sendToMq(event,text,kbType)` | `<attribute name="text">` 指定用哪个结果字段当查询文本（默认 asrText），`kbType` 可选 |
| `qwen` / `deepseek` | sendToMq | `QwenService` / `DeepSeekService` | 从 Mongo `model_meta` 取默认版本（或 XML 指定 `version`）的 url/鉴权/参数 |
| `dify` | sendToMq | `DIFYService` + `HttpModelService.getRequest` | 从 Mongo `ai_model` 取模型；`<attribute name="model">` 必填 |
| `http_model` | sendToMq | `HttpModelService` | 通用 HTTP 模型适配（dyHttp 队列），回包事件名为 `<model>_resp` |
| `atom_process` | atom_initial / atom_handler / check_progress | `AtomCommonAction` | 原子能力流程：按 task.attributes 里的 30 个开关分发，所有开关对应结果都到齐后 finish：全成功=1，全失败=0，其它=2 |
| `shard` | sendShard / finishShard / updateAsrText | `ShardTaskService` | 实时分片 |
| `attribute` | set | `AttributeService.setValue` | `type="1"`：写回调属性（Redis `attrs_callback<jobId>`，riskLevel 只升不降）；`type="2"`：写通话属性（Redis `attrs_callId<callId>`）；`type="model" data=<model>`：写 `responseResults.<model>.<k>`（如 `modelQcResult`） |
| `event` | sendToEvent | `EventAction` | 同步触发同一流程里的另一个事件 |
| `process` | success / fail / stop | `ProcessAction` | 结束子任务，finish=1 / 0 / 2 |
| 其它（如 `tfidf`） | — | `NullAction` | 只打日志（`sources/dev/tfidf.xml` 即属此类，已失效） |

### 4.8 流程 XML 清单（`sources/`）

prod（14 个）：

| 文件 | 编排链路 | 备注 |
| --- | --- | --- |
| `asr_only.xml` | ASR → 成功/失败 | |
| `asr.xml` | ASR → 脱敏 → 结束 | |
| `vfd.xml` | 鉴伪 → 结束 | |
| `asr_keyword.xml` | ASR → 脱敏 → 本地关键词 | |
| `asr_keywordAnalysis.xml` | ASR → 脱敏 → 关键词反诈 | |
| `asr_rt.xml` | ASR（文本 ≤45 字判失败）→ 脱敏 → 融通 | |
| `asr_scenario.xml` / `jt_asr_scenario.xml` | (九天)ASR → 脱敏 → 场景 | |
| `asr_scenario_keywordAnalysis.xml` | ASR → 场景，`scenarioConsistency==0` 时再做关键词反诈 | 脱敏分支被注释 |
| `dify_cmic_internal_inspection.xml` | ASR → DIFY(`cmic_internal_inspection`) | |
| `asr_qwen_rag_mul_prompt_ds.xml` | ASR → 脱敏 → Qwen(`qwen3-4b`) → RAG → 按 `ragResults[0].metadata.note` 选择多种诈骗类型专用 `deepSeekPrompt` → DeepSeek | 每种诈骗类型一段很长的中文提示词（反诈规则知识写在流程 XML 里） |
| `hubeixiaogan.xml` | ASR → 脱敏 → 正则/关键词/关键词反诈 → DeepSeek → end_event 汇总 | 地市定制 |
| `jiangxishangtong.xml` | ASR → 脱敏 → 融通 + 关键词反诈（`rt_ka_event` 汇合）→ Qwen → RAG → DeepSeek | 定制 |
| `zhuanxian_qc.xml` | **专线质检（离线）**：ASR（>45 字）→ 脱敏 → 本地关键词 + 关键词反诈（高阈值）并行；命中 → Qwen → RAG（score≥0.9 判高风险 riskLevel=1）→ 否则融通（firstLabel==4 判低风险 3）→ DeepSeek（result==1 判中风险 2）→ `end_event` 根据已有结果判断是否 success | 设置回调属性 `modelQcResult`、`riskLevel` |

test（3 个）：`asr_keyword_scenario.xml`、`condition_test.xml`、`voice_print.xml`（vpr）。

dev（61 个）：覆盖全部能力的组合，包括 `atom_common*.xml`（原子能力）、`shard.xml`（分片调试）、`zhuanxian_qc_2.xml`（**专线质检二期：支持实时流分片**，低阈值关键词反诈 → Qwen → RAG → 融通 + 高阈值关键词反诈汇合（`rt_kah_resp`）→ DeepSeek，使用 taskCondition / redisCondition 和 `interruptShard` 中断），以及 cq_anti、cq_agent、bge、deal、market_call、emotion、identity、aigs、vp 1v1/1vn、huawei、dify、dy_http 等。三个环境目录之间没有同名文件。

### 4.9 groovy 脚本的作用

- `src/main/resources/groovy/Qc4Condition.groovy`：`execute(params)` 返回 `qc1Status && qc2Status && qc3Status`。
- `Qc4Condition2.groovy` / `Qc4Event.groovy`：通过 `applicationContext.getBean("redisService")` 读 `getSubTaskItem("qcNStatus")`，三项都为真时调 `mqAction.sendToQc4(...)`。
- `config/groovy/rules`：`rule1 → Qc4Event.groovy, condition: condition, action: action`。
- **现状**：全仓库没有任何 Java 代码加载 groovy 或读取 `config/groovy/rules`；脚本引用的 `RedisService.getSubTaskItem` 和 bean `mqAction` 也都不存在。这是早期“groovy 规则引擎”方案的原型，已被 XML + SpEL 取代，`groovy` 依赖仍留在 pom 中。

---

## 5. 对外接口（入站 HTTP）

所有接口均无鉴权、鉴权拦截器或 IP 白名单（代码中未发现，待确认网关侧是否有）。响应外壳为 `BaseResponse{code,message,data}`。

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | POST `/v1/front/task/create` | `controller/TaskController.audioCreate` → `TaskService.createTask` | JSON `TaskRequest`（同 §6 消息体） | `data.transactionId` | 同步建任务的入口（主流程走 MQ）；校验 requestId、流程启用、dataId |
| HTTP | POST `/v1/front/event/create` | `controller/EventController.create` | `{processId,...}` | 0000 | 发布 `http_event`，标注 TODO 下线；`PropertyPlaceholders.getHttpValue` 恒返回 null |
| HTTP | POST `/admin/process/create` | `controller/ProcessController.processCreate` | multipart：`xmlFile`，表单 `requestId`、`processName`、`xmlFileName`、`timeout`(1–60)、`aiModels` | `data.processId` | 先存库（status=1）再写本地文件；不广播（其它实例靠懒加载） |
| HTTP | GET `/admin/process/query` | `ProcessController.processQuery` | processId、processName(正则模糊)、xmlFileName、status、page、size | `{processList,count}` | |
| HTTP | GET `/admin/process/history/query` | `ProcessController.hisProcessQuery` | processId(必填) 等 | `{hisProcessList,count}` | 按 startTime 倒序 |
| HTTP | POST `/admin/process/update` | `ProcessController.processUpload` → `ProcessService.update` | processId、可选 xmlFile、processName、status(0/1)、timeout(1–60)、aiModels | 0000 | 写 his_process，广播热更新 |
| HTTP | GET `/admin/process/queryXml/{processId}` | `ProcessController.showXmlFile` | processId | XML 文本 | |
| HTTP | POST `/admin/aiModel/http/create` | `controller/AIModelController.httpModelCreate` | `HttpModelRequest{model,desc,aiTypeId,httpUrl,requestParameters,responseParameters,authentication}` | 0000 | 写 `ai_model` |
| HTTP | POST `/admin/aiModel/http/update` | `AIModelController.httpModelUpdate` | 同上（按 model 定位） | 0000 | |
| HTTP | GET `/admin/aiModel/http/query` | `AIModelController.httpModelQuery` | model、aiTypeId、desc、page、size | `{aiModels,count}` | **返回结果包含 `authentication` 字段**（`dao/AIModelDaoImpl` 的 project 中包含它） |
| HTTP | POST `/admin/aiModel/http/delete/{model}` | `AIModelController.httpModelDelete` | model | 0000 | |
| HTTP | POST `/admin/aiModel/create` | `AIModelController.modelCreate` | `ModelRequest{model,desc,requestParameters}` | 0000 | |
| HTTP | POST `/admin/aiType/create` | `controller/AITypeController` | `AiTypeRequest{name,nickName,comment}` | 0000 | id 为 snowflake |
| HTTP | POST `/admin/aiType/update/{aiTypeId}` | 同上 | nickName、comment | 0000 | |
| HTTP | GET `/admin/aiType/query` | 同上 | — | AIType 列表 | |
| HTTP | POST `/admin/aiType/delete/{aiTypeId}` | 同上 | — | 0000 | |
| HTTP | actuator（含 prometheus） | spring-boot-starter-actuator | — | — | 暴露端点与路径由配置决定（待确认） |

`model_meta`（qwen / deepseek 用）没有管理接口，只能直接写库（待确认）。

---

## 6. 消息（RabbitMQ）

### 6.1 连接与通用约定

- 只启用“南向”RabbitMQ：配置前缀 `spring.rabbitmq.south.*`（host / port / username / password / virtualHost / listener.acknowledgeMode）。北向配置类和消费者的注解都被注释，`MqService.getRabbitTemplate` 恒返回 south 的 template。
- 消费者：`southFactory`，并发数为 `spring.rabbitmq.concurrency` / `max-concurrency`，prefetch 为 `spring.rabbitmq.prefetchCount`，ack 模式取配置（代码按手工 ack 编写，配置应为 `manual`，待确认），心跳 300s，channel 缓存 25。
- 生产者：`southProducerConnectionFactory`（`@Primary`），confirm=NONE，returns=false，连接超时 3000ms；Jackson JSON 转换器。
- 队列声明：`DynamicBeanRegistry.registerQueue` → durable、`x-max-priority=10`；exchange 全部是 **TopicExchange**（方法名叫 `registerDirectExchange`，实际创建的是 topic）。
- 消息优先级 = `task.priority`（0–10）。
- 模型请求的 `jobId` = `<subTaskId>_<dataId>`；响应必须原样带回，scheduler 取 `_` 前的部分定位子任务。

### 6.2 入站（消费）

| 方向 | 中间件 | queue（exchange / routingKey） | group/并发 | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 消费 | RabbitMQ | `scheduler.task.queue`（**代码中未声明**，需由上游或运维预建，待确认） | southFactory | `TaskRequest`：`requestId`、`priority`("0"–"10" 字符串)、`processId`、`needTaskCallback`、`needSubTaskCallback`（存储但未使用）、`parameter`(Map→task.attributes)、`data`(List<Map>，每项必须有 `dataId`，常见 `url`、`text`、`location`、`reuse`、`isReverse`、`segmentId`、`endFlag`、`audioTag`)、`transactionId`(Long)、`isStream` | `TaskConsumer.consumerTask` |
| 消费 | RabbitMQ | 模型响应队列（见 §7 表，共 26 个） | southFactory，按队列限流 | `BaseResponse{code,message,data{jobId,...模型字段}}`，code=`0000` 表示成功 | `SouthMessageConsumer` → `MessageConsumer.consumer` |
| 消费 | RabbitMQ | `scheduler.task.repeat.queue`（exchange `scheduler.callback.exchange`，routingKey=队列名） | 仅 master | `TaskMqBo{taskId,finish}` | `TaskDerepeatConsumer` |
| 消费 | RabbitMQ | `scheduler.task.lb-callback.queue`（exchange `scheduler.callback.exchange`，routingKey=队列名） | concurrency=2 | `TaskMqBo{taskId,finish}` | `TaskLBCallbackConsumer` |
| 消费 | RabbitMQ | `scheduler.task.callback.queue`（代码中未声明） | concurrency=2 | 纯文本 taskId | `TaskCallbackConsumer`（调用已 `@Deprecated` 的方法，固定 finish=1，旧路径） |
| 消费 | RabbitMQ | `scheduler.process.update.<service.instance.id>`（fanout `scheduler.process.update.exchange`） | southFactory | processId 字符串（可能带首尾双引号） | `ProcessConsumer.consumerProcessUpdate` |

### 6.3 出站（生产）

| 方向 | 中间件 | exchange / routingKey | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- |
| 生产 | RabbitMQ | `scheduler.callback.exchange` / `topic.callback.key` → 队列 `scheduler.callback.queue`（绑定 `topic.callback.*`） | **子任务回调 type=1**：`type,dataId,status(0失败/1成功/2部分成功),errorMessage(JSON字符串，key=模型),transactionId,code,models[{name,code,message,status(0未执行/1失败/2成功),startTime,endTime,modelQcResult,riskLevel}],modelStatus{model:status}`，加上各模型结果字段（`asrText`、`audioDuration`、`keywordAnalysisResult`、`ragResults`、`rtFirstLabel`、`deepSeekResult`…）和 XML 写入的回调属性（`qcResult`、`modelQcResult`、`riskLevel`、`review` 等）；finish=0 时去掉 qcResult/riskLevel | `CallbackService.callbackJob` |
| 生产 | RabbitMQ | 同上 | **主任务回调 type=2**：`type,finishRate,failCount,total,transactionId` | `CallbackService.callbackTask` |
| 生产 | RabbitMQ | `scheduler.callback.exchange` / `scheduler.task.repeat.queue` | `TaskMqBo{taskId, finish}`（1=全部回调完，2=超时兜底） | `JobService.checkCallbackCount`、`TaskCallbackSchedulerService.doCallbackTask` |
| 生产 | RabbitMQ | `scheduler.callback.exchange` / `scheduler.task.lb-callback.queue` | `TaskMqBo` | `TaskDerepeatConsumer` → `MqService.sendToTaskCallback` |
| 生产 | RabbitMQ | fanout `scheduler.process.update.exchange` | processId | `ProcessSender.broadcastProcessUpdateMessage` |
| 生产 | RabbitMQ | 各模型请求 exchange / routingKey（见 §7） | 各模型请求 DTO | `MqService.sendToXxx` |

`scheduler.callback.queue` 由 qa-server 消费（与设计图“MQ 质检结果回调”一致；消费端代码不在本仓库，待在 qa-server 中核对）。

---

## 7. 依赖调用（出站）：AI 模型 MQ 协议

scheduler **不直接发 HTTP**（仓库内没有 HttpClient / RestTemplate 调用；`DyHttpRequest` 只是把 url 和鉴权信息带给 MQ 适配器，由“scheduler adapter”代发）。所有模型都通过 RabbitMQ 请求/响应交互。超时没有按单个模型设置，只有 §10 的主任务级兜底（24h/48h）。

| 模型（AIModelEnum.name） | 请求 exchange / routingKey → queue | 响应 queue | 请求体 | 成功时写入 responseResults 的字段 | 响应事件 |
| --- | --- | --- | --- | --- | --- |
| ASR `asr` | `scheduler.asr.exchange` / `topic.asr.key` → `scheduler.asr.queue` | `scheduler.asr.response.queue` | `ASRRequest{jobId,fileUrl(=data.url),type(=transcriptionType，默认"0"：0左/1右/2双声道),language(默认zh),emotionTag}` | `asrStatus,asrText,audioDuration,asrInfo{sentences,channels,timestamps}`；reuse==1 时写 Redis dataId 键 | `asr_resp` |
| 优先 ASR（`asrPrior==1`） | `scheduler.asr.exchange` / `scheduler.task.fast.asr` → `scheduler.asr.fast.queue` | `scheduler.asr.response.fast.queue` | 同 ASR | 同 ASR | `asr_resp` |
| 九天 ASR（记作 `asr`） | `scheduler.jiutian-asr.exchange` / `topic.jiutian-asr.key` → `scheduler.jiutian-asr.queue` | `scheduler.jiutian-asr.response.queue` | `{jobId,fileUrl}` | 同 ASR | `asr_resp` |
| 脱敏 `dsz` | `scheduler.desensitization.exchange` / `topic.desensitization.key` | `scheduler.desensitization.response.queue` | `{jobId,text}` | `desensitizationStatus`，**`asrText` 被替换为脱敏文本** | `desensitization_resp` |
| 关键词反诈 `keywordAnalysis` / `keywordAnalysisLT` | `scheduler.keyword.exchange` / `topic.keyword.key` → `scheduler.keyword.queue` | `scheduler.keyword.response.queue` | `TextRequest{jobId,text,scene(=fraudDetectionScene，LT 强制"0"),isReverseLeg(=data.isReverse),threshLevel("1"高/"0"低)}` | `keywordAnalysisStatus/Result`（LT：`keywordAnalysisLTStatus/Result`），按响应里的 `threshLevel` 区分 | `keyword_analysis_resp` / `keyword_analysis_lt_resp` |
| 场景 `scenario` | `scheduler.scenario.exchange` / `topic.scenario.key` | `scheduler.scenario.response.queue` | `{jobId,text,preset}` | `scenarioStatus,scenarioResult,scenarioConsistency,scenarioScore` | `scenario_resp` |
| 催收 `collection` | `scheduler.scenario.exchange` / `topic.scenario.cuishou.key` → `scheduler.scenario.cuishou.queue` | `scheduler.scenario.cuishou.response.queue` | TextRequest | `cmic_internal_collection_status/result,collectionScore` | `cs_resp` |
| 声音鉴伪 `vfd` | `scheduler.vfd.exchange` / `topic.vfd.key` | `scheduler.vfd.response.queue` | `{jobId,fileUrl}` | `vfdStatus,vfdResult,vfdScore`，**同时覆盖 `audioDuration`**（向上取整秒） | `vfd_resp` |
| 声纹比对 `vpr` | `scheduler.vpr.exchange` / `topic.vpr.key` | `scheduler.vpr.response.queue` | `{jobId,groups,callId,fileUrl}` | `vprStatus,vprResult,differentPairs,differentIds,vprError` | `vpr_resp` |
| 声纹 1v1/1vN `vp` | `scheduler.vp.exchange` / `topic.vp.key` | `scheduler.vp.response.queue` | `VPRequest{jobId,type,url,channel,audioTagDb,audioTag,topN}` | `vpStatus,vpScore,vpTopKTags` | `vp_resp` |
| 身份比对 `identity` | `scheduler.identity.exchange` / `topic.identity.key` | `scheduler.identity.response.queue` | `{jobId,text,preset(=identityPreset),isReverseLeg}` | `identityStatus,identityConsistency,identityScore` | `identity_resp` |
| 情绪 `emotion` | `scheduler.emotion.exchange` / `topic.emotion.key` | `scheduler.emotion.response.queue` | `{jobId,fileUrl,setChannel(=emotionChannel),texts,channels,timestamps}` | `emotionStatus,emotionResult` | `emotion_resp` |
| AIGS `aigs` | `scheduler.aigs.exchange` / `topic.aigs.key` | `scheduler.aigs.response.queue` | `{jobId,fileUrl}` | `aigsStatus,aigsContent,aigsReason,aigsConfidence,aigsIsFraud` | `aigs_resp` |
| 融通反诈+场景 `rt` | `scheduler.rongtong.queue`（exchange 名与队列同名）/ `topic.rongtong.key` → `scheduler.rongtong.queue` | `scheduler.rongtong.response.queue` | `RongtongRequest{jobId,svcType,contentText,remark(=rtPreset)}` | `rtStatus,rtResultCode,rtAbilityType,rtFirstLabel,rtSecondLabel,rtThirdLabel,svcType` | `rt_resp` |
| 融通涉诈复核 `rtFraudReview` | `scheduler.rt-fraud-review.exchange` / `topic.rt-fraud-review.key` | `scheduler.rt-fraud-review.response.queue` | RongtongRequest，写死 `svcType="1"`、`remark="19"` | `rtFraudReviewStatus/ResultCode/AbilityType/First/Second/ThirdLabel` | `rt_fraud_review_resp` |
| 营销外呼 `marketCall` | `scheduler.market-call.exchange` / `topic.market-call.key` | `scheduler.market-call.response.queue` | `{jobId,text}` | `marketCallStatus,marketCallResult(prediction)` | `market_call_resp` |
| 成交分类 `deal` | `scheduler.deal.exchange` / `topic.deal.key` | `scheduler.deal.response.queue` | `{jobId,text}` | `dealStatus,dealLabel,dealNeedReview` | `deal_resp` |
| RAG `rag` | `scheduler.rag.exchange` / `topic.rag.key` | `scheduler.rag.response.queue` | `{jobId,text,topK(=ragTopK),kbType}` | `ragStatus,ragResults[{id,text,score,metadata{note…}}],ragCount` | `rag_resp` |
| BGE 多分类器 `bge` | `scheduler.iterative_anti_model.exchange` / `scheduler.task.iterative_anti_model` | `scheduler.iterative_anti_model.response.queue` | `{jobId,text,classifiers,rules[{name,priority,expression,output_label,is_default}]}` | `bgeStatus,bgeRuleName,bgeExpression,bgeOutputLabel,bgePriority` | `bge_resp` |
| 重庆行业卡反诈 `cqAnti` | `scheduler.chongqing-anti.exchange` / `scheduler.task.chongqing-anti` | `scheduler.chongqing-anti.response.queue` | `{jobId,text}` | `cqAntiStatus,cqAntiFilterType,cqAntiRiskLevel,cqAntiResult` | `cq_anti_resp` |
| 重庆行业卡 agent 复核 `cqAgent` | `scheduler.cqagent.exchange` / `scheduler.task.cqagent` | `scheduler.cqagent.response.queue` | `{jobId,text,useTools,useFast}` | `cqAgentStatus,cqAgentIsViolation,cqAgentIsFraud,cqAgentRiskLevel,cqAgentSceneCategory,cqAgentSceneSubtype,cqAgentConfidence,cqAgentSource,cqAgentDetail{explanation,detectedFeatures,evidenceQuotes,analysisThought}` | `cq_agent_resp` |
| DeepSeek `deepSeek` | `scheduler.deepseek.exchange` / `topic.deepseek.key` | `scheduler.deepseek.response.queue` | `DyHttpRequest{jobId,model,version,url,requestParameters(含 text、prompt 等),responseParameters,authorization,type}`（取自 `model_meta`） | `deepSeekStatus,deepSeekResult,deepSeekReason,deepSeekExtraData` | `deepseek_resp` |
| 通义千问 `qwen` | `scheduler.qwen.exchange` / `topic.qwen.key` | `scheduler.qwen.response.queue` | DyHttpRequest（取自 `model_meta`） | `qwenStatus,qwenResult` | `qwen_resp` |
| DIFY `dify` | `scheduler.dify.exchange` / `topic.dify.key` | `scheduler.dify.response.queue` | DyHttpRequest（取自 `ai_model`，自定义参数来自 `task.attributes.difyParam`） | `difyStatus,difyResult` | `dify_resp` |
| 通用 HTTP 模型 `dyHttp` | `scheduler.dyHttp.exchange` / `topic.dyHttp.key` | `scheduler.dyHttp.response.queue` | DyHttpRequest（参数来自 `task.attributes.httpParam`） | `<model>_status`、`<model>_<key>` | `<model>_resp` |
| 话术 `script` | `scheduler.script.exchange` / `topic.script.key` | `scheduler.script.response.queue` | `{jobId,text,threshold(默认0.85),scriptInfos…}` | `scriptStatus,scriptsIds,scriptResult` | `script_resp` |
| 文本相似度 `text_similarity` | `scheduler.cs_qc.exchange` / `topic.cs_qc.key` | `scheduler.cs_qc.response.queue` | TextSmlRequest | `content_similarity_qc_status/result,contentSimilarityScore` | `text_sml_resp` |
| 华为质检 `huaweiQC` | `scheduler.huawei.exchange` / `topic.huawei.key` | `scheduler.huawei.response.queue` | `HuaweiRequest{checkType,appKey(配置 huawei.qc.appKey),serviceType,sessionId,recordUrl/recordText,industryCategory,industrySegmentation}` | `huaweiQCStatus/Result/Detail/Consistency` | `huawei_qc_resp` |

已失效或部分失效的能力：
- `script`、`huawei`、`text_similarity`（cs_qc）：exchange/队列声明代码被注释（`MQBeanConfig.configureMq`），对应响应队列的 `@RabbitListener` 也被注释。投递目标 exchange 若不存在，消息会丢失或导致 channel 报错（待确认 broker 侧是否预建）；即使能发出去也收不到响应，相关子任务只能等超时兜底。
- `BA`（行为分析）、`TFIDF`：声明与发送入口都已注释或不可达。
- 本地匹配类（`keyword`、`word_group`、`word_regex`、`keyword_rs`）不走 MQ，在当前线程内同步完成并立即发布响应事件。

指标：`model_send_count{model,process,priority}`、`model_receive_count{model,process}`、`model_receive_status_count{model,status}`、`model_receive_code_count{model,code}`、`model_receive_cost_histogram{model}`（有 bug，见 §12）、`model_receive_timeout_count`、`model_abnormal_count{abnormal=zero_duration}`。

---

## 8. 数据

### 8.1 MySQL DDL（`src/main/resources/mysql/`）——旧版模型，当前运行时未使用

当前代码中没有 MySQL 的 DataSource、JdbcTemplate 或 JPA 实体对应这三张表（唯一的 JDBC 数据源是 StarRocks，只读 `qa_detail`）。这三份 DDL 对应旧版接口 `v1/front/smart/create`（见 `src/test/shell/curl.sh` 以及遗留 DTO `TaskSmartWalnutsRequest{urlList}`），带有按 URL 回调的字段。现行模型已迁到 MongoDB 的同名集合（§8.2）。下面列出 DDL 的全部字段：

**`task.ddl` → 表 `task`**

| 字段 | 类型 | 约束/默认 | 含义（DDL 注释） |
| --- | --- | --- | --- |
| `id` | bigint(20) | PK，NOT NULL | 主键 id |
| `create_time` | datetime | NOT NULL，默认 CURRENT_TIMESTAMP | 创建时间 |
| `extra_parameters` | varchar(255) | NULL | 其他参数 |
| `finish` | tinyint(4) | NOT NULL | 0-失败，1-成功 |
| `location` | tinyint(4) | NOT NULL | 位置：1 南，2 北；默认南方 |
| `priority` | tinyint(4) | NOT NULL | task 优先级：0,10 |
| `process_id` | varchar(255) | NULL | 流程规则 id |
| `request_id` | varchar(255) | NULL，唯一键 `UK_au9ck3rs4g8f2mplbdqve6djm` | client 请求 id |
| `status` | tinyint(4) | NOT NULL | 0-未开始，1-推送中，2-子任务全部已推送完成 |
| `subtask_callback_url` | varchar(255) | NULL | 子任务回调接口的 url |
| `task_callback_url` | varchar(255) | NULL | 总任务回调接口的 url |
| `update_time` | datetime | NOT NULL，默认 CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**`sub_task.ddl` → 表 `smart_walnuts_sub_task`**（注意表名不是 `sub_task`）

| 字段 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| `id` | bigint | PK | 主键 id |
| `create_time` | datetime | NULL | 创建时间 |
| `error_message` | varchar(255) | NULL | 错误信息 |
| `file_id` | varchar(255) | NULL | 客户提供的文件 id |
| `file_url` | varchar(255) | NULL | 录音文件下载 url |
| `finish` | tinyint | NOT NULL | 0-失败，1-成功 |
| `status` | tinyint | NOT NULL | 子任务推送 MQ 状态 **0-未开始 1-推送成功 2-推送失败** |
| `task_id` | bigint | NOT NULL | 关联主任务 id |
| `update_time` | datetime | NOT NULL，默认 CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| `location` | tinyint | NOT NULL，默认 1 | 1-南方，2-北方 |

**`process.sql` → 表 `process`**

| 字段 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| `id` | bigint(20) | PK AUTO_INCREMENT | |
| `create_time` | datetime(6) | NULL | |
| `process_id` | varchar(255) | NULL | 流程 id |
| `update_time` | datetime(6) | NULL | |
| `xml_file_name` | varchar(255) | NULL | 流程 XML 文件名 |
| `timeout` | int(11) | 默认 0 | 超时时间 |
| `ai_models` | varchar(255) | NULL | AI 模型，用 `,` 隔开 |

CONFLICT: `src/main/resources/mysql/sub_task.ddl` vs `src/main/java/com/cmic/pojo/entity/SubTask.java` 和 `service/task/ProcessJobService.processJob`：DDL 中 status 为“1-推送成功 2-推送失败”，代码实体注释与实际写入值是“1-推送失败 2-推送成功”（发送成功写 2、异常写 1）。
CONFLICT: `src/main/resources/mysql/task.ddl` vs `pojo/entity/Task.java`：DDL 的 finish 只有 0/1，代码中 finish 有 0 失败 / 1 成功 / 2 部分成功或超时；DDL 中的 callback url 字段在现行代码中不存在（改为 MQ 回调）。

### 8.2 MongoDB 集合（主存储）

库名在配置 `spring.data.mongodb.*`（缺失的 application.yml 中，待确认）。

| 集合 | 读/写 | 实体 | 关键字段 | 索引 | 用途 |
| --- | --- | --- | --- | --- | --- |
| `task` | 读写 | `pojo/entity/Task` | `_id`(Long snowflake)、`requestId`、`priority`(0–10)、`processId`、`status`(0未开始/1推送中/2全部已推送)、`finish`(0失败/1成功/2部分成功或超时)、`attributes`(=TaskRequest.parameter)、`createTime`、`updateTime`、`jobCount`、`needTaskCallback`、`needSubTaskCallback`、`transactionId`、`callback`(0/1)、`stream` | `requestId` 唯一 | 主任务 |
| `sub_task` | 读写 | `SubTask` | `_id`(long snowflake，即 jobId)、`taskId`、`status`(0未开始/1推送失败/2推送成功)、`finish`(0/1/2)、`location`(1南/2北)、`errorMessage`(JSON 字符串)、`dataId`、`attributes`(原 data 项)、`createTime`、`updateTime`、`callback`(0/1)、`responseResults`（模型结果，扁平 key 见 §7；另有 `<model小写>` 子文档 `{name,code,message,status,startTime,endTime,modelQcResult,riskLevel}`） | `{taskId,status}`、`{taskId,callback}` | 子任务与结果；回调后 unset `asrText`/`asrInfo`/`cqAgentDetail` |
| `process` | 读写 | `Process` | `requestId`(唯一)、`processId`、`processName`、`xmlFileName`(唯一)、`timeout`(默认10，未使用)、`aiModels`、`status`(0不可用/1可用)、`createTime`、`updateTime` | `requestId`、`xmlFileName` 唯一 | 流程元数据 |
| `his_process` | 写/查询 | `HisProcess` | 上述字段 + `xmlText`（当时的 XML 全文）、`startTime`、`endTime` | `processId`、`processName`、`xmlFileName` | 流程历史版本 |
| `ai_model` | 读写 | `AIModel` | `model`(唯一)、`desc`、`requestParameters`、`responseParameters`、`httpUrl`、`type`(=ai_type.name)、`authentication`(Map，敏感)、时间 | `model` 唯一 | dify / http_model 的模型注册表 |
| `ai_type` | 读写 | `AIType` | `_id`(Long)、`name`(唯一)、`nickName`、`comment` | `name` 唯一 | 模型类型 |
| `model_meta` | 读 | `ModelMeta` | `model`、`version`、`isDefault`、`httpUrl`、`authentication`、`requestParameters`、`responseParameters`、`type`、`maxConcurrency`(未使用) | `{model,version}` 唯一 | qwen / deepseek 的多版本模型注册表 |
| `shard_task` | 读写 | `ShardTask` | `callId`、`asrText`(累加)、`asrDuration`、`interruptFlag`(未使用)、`sharkInfoMap{segmentId:{subTaskId,segmentId,endFlag,received,finish,asr}}` | 无显式索引（按 callId 查询，待确认库里是否建了索引） | 实时流分片状态 |

自动建索引依赖 `spring.data.mongodb.auto-index-creation`（待确认）。所有集合都没有 TTL，也没有清理逻辑（待确认运维侧是否定期清理）。

### 8.3 StarRocks（只读）

| 库/表 | 读写 | 字段 | 用途 | 位置 |
| --- | --- | --- | --- | --- |
| （库名在配置中）`qa_detail` | 读 | `id, data_id, url, content, duration, asr_info, create_time` | ASR 结果复用：按 `data_id IN (...)` 批量查询（`findByUrl` 未被使用） | `dao/QaDetailDaoImpl` |

数据源配置：`spring.data.starRocks.url/username/password/driver-class-name`；Hikari 最大连接 10，最小空闲 2，连接超时 30s，空闲超时 10min，查询超时 30s。日志里写的是 “ck error”（从 ClickHouse 迁移过来的遗留文案）。该表应由 qa-server 写入“海山数据库”（待确认）。

### 8.4 Redis

| key 模式 | 类型 | TTL | 读写方 | 用途 |
| --- | --- | --- | --- | --- |
| `<dataId>`（**无前缀**的裸 dataId） | string `"1"` | `task.attributes.expire` 天，默认 7 天 | 写：`ASRService.handlerMessage`（reuse==1 且 ASR 成功）；读：`RedisService.filterDataId`（MGET） | 标记该 dataId 的 ASR 结果已入 `qa_detail`、可复用 |
| `attrs_callback<jobId>` | hash | 3 天（每次 hmset 重置） | 写：XML `attribute type="1"`（`AttributeService.setAttributeJobCallback`，riskLevel 只升不降）；读：`CallbackService.getCallbackAttributes`；删：`JobService.doAfter` | 子任务回调附加属性（qcResult、modelQcResult、riskLevel…） |
| `attrs_callId<callId>` | hash | 1 天 | 写：XML `attribute type="2"`；读：`RedisCondition`（`#call.xxx`） | 同一通话跨分片共享的属性，例如 `interruptShard` |
| `processedUrl:<url>` | string | 默认 7 天 | `RedisService.setProcessUrl / existProcessedUrl` | **未被调用**（遗留） |

Redis key/value 都用 StringRedisSerializer；所有 RedisUtil 方法的异常都会计入 `redis_error_count`。`deleteAttributeJobCallback`、`getAttributeJobCallback` 没有 try/catch，Redis 故障时会让回调流程抛错（`callbackJob` 外层吞异常 → 回调丢失）。

### 8.5 ETCD / 文件存储

- ETCD：本仓库未使用。
- 本地文件：流程 XML 位于 `${xml.path}/${spring.profiles.active}/<xmlFileName>.xml`（上传和读取），启动/重载时递归扫描 `${xml.path}`；`${xml.path}export/` 用于可复用片段（仓库中不存在）。打包时 `sources/` 被放进包里，推测 `xml.path=sources/`（`XmlLoader.main` 中写的就是 `sources/`，待确认）。
- PFS：本服务不直接访问，只把录音 URL（`data.url`）透传给 ASR / VFD / VP / AIGS / 情绪模型。

---

## 9. 状态与枚举

| 名称 | 取值 | 含义 | 位置 |
| --- | --- | --- | --- |
| `Task.status` | 0 / 1 / 2 | 未开始 / 推送中（代码从不写入）/ 子任务全部已推送 | `pojo/entity/Task`、`TaskService.createMqTask` |
| `Task.finish` | 0 / 1 / 2 | 失败 / 成功（全部子任务都已回调）/ 部分成功（超时兜底时写 2） | `TaskService.doTaskCallback` |
| `Task.callback`、`SubTask.callback` | 0 / 1 | 未回调 / 已回调 | |
| `SubTask.status` | 0 / 1 / 2 | 未开始 / 推送失败 / 推送成功（与 DDL 相反） | `ProcessJobService.processJob` |
| `SubTask.finish` | 0 / 1 / 2 | 失败（process fail、超时、原子能力全失败）/ 成功 / 部分成功（process stop、原子能力部分成功） | `ProcessAction`、`AtomCommonAction.checkProcess` |
| `location` | 1 / 2 | 南 / 北（北向已停用，实际都走南向 MQ） | `TransformUtil` |
| `priority` | 0–10 | 任务优先级，也是 MQ 消息优先级（队列 `x-max-priority=10`）；超时策略：>2 满 24h 兜底，≤2 满 48h 兜底 | `TaskService.validTask`、`TaskCallbackSchedulerService.shouldBatchTaskCallback` |
| `ErrorCode` | `0000` 成功 / `1001` 失败 / `1002` 参数错误 / `9998` 超时 / `9999` 异常 | 接口响应码与模型 code | `pojo/dto/ErrorCode` |
| `CallbackModelRequest.status` | 0 / 1 / 2 | 未执行 / 执行失败（含超时）/ 执行成功 | |
| `modelQcResult` | "0" / "1" | 质检结果 异常 / 正常（XML 写入） | `CallbackModelRequest` 注释 |
| `riskLevel` | "1" / "2" / "3" | 高 / 中 / 低风险；覆盖权重 1→10、2→5、3→1，低风险不能覆盖高风险 | `engine/strategy/RiskLevelOverrideStrategy` |
| `qcResult` | "0" / "1" | 专线质检二期流程写入的总结论（XML 约定） | `sources/dev/zhuanxian_qc_2.xml` |
| `CallbackRequest/CallbackJobRequest.type` | 1 / 2 | 子任务回调 / 主任务回调 | |
| `TaskMqBo.finish` | 1 / 2 | 正常收齐 / 超时兜底 | `JobService`、`TaskCallbackSchedulerService` |
| `KeywordAnalysisService.threshLevel` | "0" / "1" | 低阈值（LT）/ 高阈值 | |
| `VPService.TYPE_1V1 / TYPE_1VN` | 常量（具体值在 VPService 中） | 声纹 1:1 比对 / 1:N 检索 | |
| `transcriptionType` | "0" / "1" / "2" | 左声道 / 右声道 / 双声道转写（默认 0） | `RequestExtParameter` 注释、`ASRService` |
| `Process.status` | 0 / 1 | 不可用 / 可用 | |
| `MqEvent` 事件名 | `initial`、`asr_resp`、`desensitization_resp`、`keyword_resp`、`keyword_analysis_resp`、`keyword_analysis_lt_resp`、`scenario_resp`、`vpr_resp`、`vfd_resp`、`vp_resp`、`dify_resp`、`http_model_resp`、`huawei_qc_resp`、`rt_resp`、`rt_fraud_review_resp`、`cs_resp`、`text_sml_resp`、`keyword_rs_resp`、`identity_resp`、`emotion_resp`、`aigs_resp`、`deepseek_resp`、`word_group_resp`、`qwen_resp`、`market_call_resp`、`word_regex_resp`、`deal_resp`、`rag_resp`、`bge_resp`、`cq_anti_resp`、`cq_agent_resp`、`shard_send_resp`、`<model>_resp` | 流程 XML 中的事件名 | `engine/event/MqEvent` |
| 原子能力开关（task.attributes 值为 `"1"` 时开启） | `transcription`、`forgeryDetection`、`voiceComparison`、`voiceAnalysis`、`wordMatching`、`sceneAnalysis`、`sceneRecognition`、`scriptConsistency`、`fraudDetection`、`fraudDetectionLT`、`emotionAnalysis`、`regexWordMatching`、`identityComparison`、`aigsAnalysis`、`deepSeekAnalysis`、`wordAnalysis`、`qwenInference`、`fusionFraudSceneCheck`、`fusionFraudReview`、`collectionModel`、`contentSimilarityAnalyse`、`marketingCall`、`forbiddenWordCheck`、`difyIntegration`、`dealClass`、`ragSearch`、`bgeClassifier`、`cqFraudCheck`、`cqAgentReview` | 对应 `AIModelEnum.atomicSwitch` | `engine/action/AtomCommonAction` |

### 9.1 任务状态机

```text
Task:
  (MQ 收到 TaskRequest)
     │ 校验失败 → 丢弃（不回调）
     ▼
  status=0 → [逐个子任务 initial] → status=2
     │
     ├─ 全部子任务 callback=1 → TaskMqBo(finish=1) → master 去重 → finish=1, callback=1 → 主任务回调 type=2
     └─ 超时（>24h 且 priority>2，或 >48h；只扫描 3 天内的任务）
            → TaskMqBo(finish=2) → finish=2, callback=1 → 未回调子任务补发回调(finish=0) → 主任务回调 type=2

SubTask:
  status: 0 ──initial 成功──▶ 2
           └─initial 异常──▶ 1（不重试）
  responseResults.<model>.status: 0(已发送) ──回包 0000──▶ 2
                                            └─回包非 0000──▶ 1
                                   有 startTime 无 endTime 且已回调 → 回调中报超时(9998)
  finish/callback: 0/0 ──process success──▶ 1/1
                       ──process fail─────▶ 0/1
                       ──process stop─────▶ 2/1
                       ──主任务超时兜底────▶ 0/1
  已回调后到达的模型响应：ack 丢弃，计入 model_receive_timeout_count
```

---

## 10. 定时任务

| 任务 | 周期 | 条件 | 动作 | 位置 |
| --- | --- | --- | --- | --- |
| `TaskCallbackSchedulerService.pollTasks` | `@Scheduled(fixedRate = 1h)`；只在 `masterServer=true` 的实例上运行 | — | 先 `statTasks`，再 `callbackTasks` | `service/TaskCallbackSchedulerService.java` |
| └ `statTasks` | 同上 | 实时任务：3 天内且创建超过 10 分钟仍未回调，按 processId 计数；离线任务（stream=false）：创建超过 6h / 12h / 24h 仍未回调，统计未回调子任务数 | 只更新指标 `task_timeout_10_minutes_count`、`task_timeout_6/12/24_hours_count`（Counter 每小时累加，并非 Gauge，见 §12） | |
| └ `callbackTasks` | 同上 | `needTaskCallback=true`、`callback=0`、`createTime` 在 [now-3d, now-24h]，且 `priority>2` 或已超过 48h | 发送 `TaskMqBo{finish=2}` 到 repeat 队列（每小时重复发，依赖 master 内存 LRU 去重） | |

注释中写的“实时（1h）超时回调”在代码里没有实现；实时任务也只按上面 24h/48h 的规则兜底。

---

## 11. 关键配置项（application.yml 缺失，按代码反推；不含敏感值）

| 配置项 | 含义 | 位置 | 环境差异 |
| --- | --- | --- | --- |
| `spring.profiles.active` | 环境名，决定流程 XML 的子目录 `${xml.path}/<env>/` | `ProcessService.env` | dev / test / prod（待确认） |
| `server.port` | HTTP 端口 | Spring Boot | 待确认 |
| `service.instance.id` | 实例 id：Snowflake workerId（hutool 限制 0–31，必须全局唯一）+ 流程热更新队列后缀 | `SnowflakeUtils`、`ProcessSender` | 每个实例必须不同 |
| `masterServer` | `true` 时启用超时扫描与主任务回调去重 | `TaskCallbackSchedulerService`、`TaskDerepeatConsumer` | 只能有且仅有一个实例为 true |
| `xml.path` | 流程 XML 根目录 | `ApplicationProperties.Xml` | 推测为 `sources/`，待确认 |
| `rateLimiter.rate` / `rateLimiter.rateInterval` | 每个响应队列每秒处理许可数 / （rateInterval 未使用） | `MessageConsumer` | 待确认 |
| `common.atomicProcessId` | 原子能力流程 processId 列表（回调时按开关推导模型） | `CallbackService.callbackJob` | 待确认 |
| `spring.rabbitmq.south.host/port/username/password/virtualHost` | RabbitMQ 连接（敏感，不列值） | `SouthRabbitMqConfig` | 各环境不同 |
| `spring.rabbitmq.south.listener.acknowledgeMode` | ack 模式，应为 `manual` | `SouthRabbitMqConsumerConfig` | 待确认 |
| `spring.rabbitmq.south.publisherConfirms/publisherReturns` | 绑定到属性但生产者工厂里写死为关闭 | `RabbitMqConfig` | 实际不生效 |
| `spring.rabbitmq.concurrency` / `max-concurrency` / `prefetchCount` | 消费并发与预取 | `SouthRabbitMqConsumerConfig` | 待确认 |
| `spring.rabbitmq.send.maxConcurrency`（默认 16）/ `acquireTimeoutMs`（默认 2000，未使用） | 全局发送信号量 | `MqSendProperties` | |
| `spring.data.mongodb.*` | MongoDB 连接（敏感） | Spring Boot | 各环境不同 |
| `spring.data.redis.*` / `spring.redis.*` | Redis 连接（敏感） | Spring Boot | 各环境不同 |
| `spring.data.starRocks.url/username/password/driver-class-name` | StarRocks（敏感） | `StarRocksDataSourceConfig` | 各环境不同；缺失时启动失败 |
| `spring.data.clickhouse.*` | ClickHouse（配置类已停用） | `ClickHouseDataSourceConfig` | 无 |
| `huawei.qc.appKey` / `huawei.qc.serviceType` | 华为质检参数（appKey 敏感） | `HuaweiService` | 缺失时启动失败 |
| `sf.*` | Snowflake 参数 | `SnowFlakeProperties` | 未被使用（实际用 hutool） |
| JVM 堆 | dev/prod 16g，test 4–6g | `bin*/start.sh` | 见 §2.1 |
| 日志 | 目录 / 保留 / 单文件大小 | `config.*/logback.xml` | 见 §2.1 |
| `sources/<env>/` | 流程定义 | `sources/` | dev 61 / test 3 / prod 14 个；同一能力在三个环境的流程不同，例如专线质检 prod 为离线版 `zhuanxian_qc.xml`，dev 有实时分片版 `zhuanxian_qc_2.xml` |

---

## 12. 坑与特殊逻辑

### 12.1 安全

1. **SpEL 注入 / 远程代码执行风险**：`CalculateCondition`、`TaskCondition`、`RedisCondition` 都用 `StandardEvaluationContext` 执行流程 XML 中的表达式，可以调用 `T(java.lang.Runtime)` 等任意类型。流程 XML 可通过 `POST /admin/process/create|update` 上传，而这些接口没有任何鉴权（`controller/ProcessController`）。建议改用 `SimpleEvaluationContext` 或白名单，并给 admin 接口加鉴权。
2. **模型凭据泄露面**：`ai_model.authentication`、`model_meta.authentication` 会被原样放进发往 dify / dyHttp / qwen / deepseek 队列的消息（`DyHttpRequest.authorization`），而 `GET /admin/aiModel/http/query` 会返回 authentication（`dao/AIModelDaoImpl.findByCriteria` 的 project 包含它）。
3. 本地正则匹配（`KeywordRegexService`）使用任务方传入的正则，存在 ReDoS 风险。
4. `src/test/shell/curl.sh` 含疑似真实内网地址和疑似真实号码组成的录音文件名，建议脱敏（本报告未转录这些值）。
5. 子任务回调日志会在去掉 `asrText` 后打印整个 paramJson，其中仍包含 `asrInfo` 等可能带通话内容的字段（`CallbackService.callbackJob`）；`SubTask.toString` 会打印 attributes 和 responseResults（`ProcessJobService` 的 info 日志中有调用）。

### 12.2 正确性 / 潜在 bug

6. **没有 master 时主任务永不回调；多个 master 时去重失效**：子任务全部完成后，主任务回调必须经过 master 才有的 `TaskDerepeatConsumer`。去重用的是进程内 1000 条 LRU，重启即丢，也不能跨实例去重。
7. **建任务校验失败时静默丢弃**：`CallbackService.callbackTaskError` 的发送语句被注释，上游拿不到失败原因；只有日志。
8. **回调先发送、后落库，且吞异常**：`CallbackService.callbackJob(Task,SubTask,int,int)` 先调 `callbackJob(task, subTask)`（内部 try/catch 只记 `e.getMessage()`），再无条件写 `callback=1`。发送失败、`process.aiModels` 中出现 `AIModelEnum` 之外的模型名（`AIModelEnum.getAttributes` 会 NPE）、流程被禁用导致 `findEnableByProcessId` 返回 null，都会让子任务被标记为已回调但实际没有发出回调消息。
9. **`process.timeout` 没有生效**：字段只做存储和校验（1–60 分钟），真正的超时只有 24h/48h 的兜底；并且只扫描 3 天内的任务，master 长时间不可用时更早的任务永远不会回调。
10. **流程禁用不生效**：`ProcessService.findEnableByProcessId` 命中内存 `processMap` 时不再检查 status；`ProcessConsumer` 重载时也不管 status 一律放入缓存。把流程 status 改为 0 后，直到重启前仍可用。`processMap`、`serviceEventsMap` 都是非线程安全的 `HashMap`，却会被 MQ 消费线程和热更新线程并发读写。
11. **多实例下 XML 文件只落在接收请求的那台机器**：`ProcessService.upload` 写本地磁盘，广播后其它实例从各自本地磁盘重载（`XmlEventLoaderService.reloadEventByProcessId`）。除非 `xml.path` 是共享存储（待确认），其它实例会加载旧版本或找不到文件；找不到时 `serviceEventsMap` 中为 null，`XmlService.matchEventXml` 会 NPE。
12. **XML 查找不区分环境目录**：启动/重载时递归扫描整个 `${xml.path}`，按文件名取第一个匹配（`CommonUtil.findFileByName`）。目前 dev/test/prod 三个目录没有同名文件；一旦出现同名文件，prod 可能加载到 dev 的流程。打包时三套流程都会进入 prod 包。
13. **实时分片重复发送竞态**：CAS 的 `tryMarkProcess` 被注释（`ShardTaskService.receiveShard`、`finishShardAndNext`）。第 N 片到达与第 N-1 片完成同时发生时，两条路径都可能为第 N 片发布 `shard_send_resp`，导致重复 ASR。`ShardTaskDaoImpl.appendAsrText`（读后写）、`updateAsrText`（基于读取值拼接）也都不是原子操作。
14. **模型响应幂等非原子**：`MessageConsumer.consumer` 先读 `responseResults.<status>` 再处理，两条重复响应并发时都会通过检查。并行分支汇合（如 `zhuanxian_qc_2.xml` 中 `rt_resp` 与 `keyword_analysis_resp` 都触发 `rt_kah_resp`）如果同时到达，两个线程都可能看到两边结果齐全，从而重复发送 DeepSeek、重复结束子任务。`JobService.jobFinished` 依据的是内存快照中的 callback，不能防止并发重复回调。dyHttp 响应的状态键是 `<model>_status`，而去重检查用的是 `httpModelStatus`，因此 dyHttp **没有去重**。
15. **失败消息直接丢弃**：所有消费者异常都 `basicNack(requeue=false)`，队列没有声明 DLX（待确认 broker policy），子任务只能等 24/48h 兜底。`ProcessConsumer` 在流程不存在时 `return` 但不 ack，手工 ack 模式下这条消息会一直处于 unacked 状态，直到连接断开。
16. **发送可靠性**：publisher confirm 关闭，`mandatory=true` 但没有 ReturnCallback，路由不到的消息静默丢失；`semaphore.acquire()` 无超时（`acquireTimeoutMs` 未使用）。线程中断时 finally 仍会 release，导致许可数被“多还”一个。
17. **建任务非原子**：先写子任务再写主任务；requestId 并发重复时主任务因唯一索引写入失败，但子任务已落库成为孤儿。`@Transactional` 对 MongoDB 无效。HTTP `createTask` 的错误日志用 `e.toString()` 作为第 3 个占位参数，异常栈被丢掉。
18. **结果字段被覆盖**：脱敏回包会把 `asrText` 替换为脱敏文本（原文不保留）；VFD 回包会用鉴伪时长覆盖 ASR 的 `audioDuration`。流程中如果同时有 ASR 和 VFD，最终回调的时长取决于哪个后到。
19. **`CalculateCondition.allNotEquals` 实现与 `allEquals` 完全相同**（都是“全部相等返回 true”），XML 中使用时语义相反。
20. **指标 bug**：`CommonMetrics.receiveModel` 中 `costSeconds = endTime - startTime / 1000.0`（运算符优先级错误），`model_receive_cost_histogram` 的数值没有意义。`TaskDaoImpl.countTaskByProcessId` 忽略 `stream` 参数，写死为 `stream=true`。`task_timeout_*` 是 Counter，每小时把当前存量再累加一次，只能看增量趋势。
21. `zhuanxian_qc_2.xml` 的 `end_event` 中 `isFalse(${keywordAnalysisLTStatus})` 重复写了两次，疑似原意是其中一个为 `keywordAnalysisStatus`（待确认）。`zhuanxian_qc.xml` 的 `deepseek_resp` 在判定中风险（riskLevel=2）的分支里写的是 `modelQcResult=1`，与其它命中分支写 `0` 不一致（待确认）。
22. 失效的能力：`dev/tfidf.xml` 使用了不存在的 `tfidf` 动作；`dev/asr_keyword_rs.xml` 使用 `keyword_rs op=sendToMQ`，但该动作只支持 `match`，因此是空操作，子任务会挂起到超时；script / huawei / text_sml 的队列声明和监听都已注释（§7）。
23. `TaskController` 与 `validTask` 的 priority 校验不一致：注解是 `([0-9]|10)`，MQ 路径用正则 `^(10|[0-9])$`，两者等价；但 priority 是 String，MQ 消息里如果 priority 为 null，`validTask` 会 NPE → nack 丢弃。
24. `MessageConsumer` 中 `channel.basicAck(tag, multiple=true)`：所有消费者都用 multiple=true 确认，同一 channel 上 prefetch>1 时会把前面尚未处理完的消息一起确认（待确认是否会导致处理中的消息在宕机时丢失）。
25. `start.sh`/`stop.sh` 的问题见 §2.1（非法 shebang、grep 不到进程、`bin.test/restart.sh` 路径错误、dev 包没有 bin）。

### 12.3 看起来奇怪但有原因的逻辑（不要随意删除）

- 回调前 `$unset asrText/asrInfo/cqAgentDetail`：控制 Mongo 体积，同时避免长期保留通话文本（`JobService.doAfter`）。
- 子任务已回调后再到达的模型响应直接 ack，只计超时指标：防止超时兜底之后的迟到结果覆盖已回调的结论。
- `RiskLevelOverrideStrategy`：多个分支都会写 riskLevel，只允许风险升级，保证最终风险等级取最高。
- 主任务回调先到 repeat 队列由 master 去重，再到 lb-callback 队列由任一实例执行：在多实例并发收齐子任务时只回调一次，同时分摊回调负载。
- `jobId = subTaskId_dataId`：模型侧可以直接看到业务 dataId 便于排查，scheduler 只取 `_` 前的部分。
- exchange `scheduler.rongtong.queue` 与队列同名，催收复用 `scheduler.scenario.exchange`：属于历史命名，模型适配器侧依赖这些名字，改名会破坏契约。

### 12.4 TODO / 遗留

- `ProcessJobService.processJob`：“失败记录状态，后续看情况是否需要重试”。
- 各消费者：“TODO 处理异常消息”。
- `EventController`：“TODO 下线”；`PropertyPlaceholders.getHttpValue` 为 TODO，恒返回 null。
- 北向 MQ、ClickHouse、groovy 规则、`XmlLoader`、`JobRedisBo`/`TaskRedisBo` 等旧 Redis 模型、`processedUrl:` 键、MySQL DDL 都是遗留代码。

---

## 13. 与其它仓库的关系

| 方向 | 对方 | 契约 | 说明 |
| --- | --- | --- | --- |
| 上游 | qa-server（Python） | RabbitMQ `scheduler.task.queue`，消息体 `TaskRequest`（§6.2） | qa-server 收到 vi-bill 的 HTTP 质检请求后，按流程 processId 组装任务；需在 qa-server 中核对 `parameter` 和 `data` 的字段约定（`url`、`reuse`、`expire`、`isStream`、`segmentId`、`endFlag` 等） |
| 下游（回调） | qa-server | RabbitMQ `scheduler.callback.exchange` / `topic.callback.key` → `scheduler.callback.queue`；type=1 子任务结果、type=2 主任务汇总（§6.3） | qa-server 再回调 vi-bill；`riskLevel`、`modelQcResult`、`qcResult`、`review` 等字段由流程 XML 决定 |
| 下游 | ASR / 九天 ASR / fast ASR | `scheduler.asr.*`、`scheduler.jiutian-asr.*`、`scheduler.asr.fast.queue`（§7） | ASR 通过 `fileUrl` 从 vi-mdown / PFS 拉取录音 |
| 下游 | anti-fraud 系列模型适配器 | keyword、rongtong、rt-fraud-review、iterative_anti_model、chongqing-anti、cqagent、deepseek、qwen、rag、dify、dyHttp 等队列（§7） | 均不在本次源码中 |
| 下游 | 其它 AI 能力 | desensitization、scenario、vfd、vpr、vp、identity、emotion、aigs、market-call、deal、cuishou 等队列 | |
| 读 | StarRocks `qa_detail`（推测为海山数据库，由 qa-server 写入，待确认） | SQL 只读 | ASR 结果复用 |
| 共享 | Redis | `<dataId>` 复用标记（无前缀，可能与其它系统的 key 冲突，待确认是否独立 db） | |
| 无关系 | vi-as、vi-media、vi-merge、vi-billcenter、vi-pusher、offline-merge、vi-cront-jobs | 无直接接口 | 间接通过 qa-server 和录音 URL 关联 |

---

## 14. 待确认问题清单

1. 各环境的 `application.yml` 内容：`server.port`、`xml.path`、`masterServer`、`service.instance.id`、`rateLimiter.rate`、`common.atomicProcessId`、ack 模式、并发与 prefetch、Mongo 库名。
2. `xml.path` 在多实例部署时是否是共享存储？否则流程上传/更新只会在单台机器上生效（§12.11）。
3. 生产环境是否保证有且只有一个 `masterServer=true` 的实例？master 故障时如何切换？
4. `scheduler.task.queue`、`scheduler.task.callback.queue` 由谁声明，参数是什么（是否带 `x-max-priority`、DLX）？broker 是否配置了死信 policy？
5. script / huawei / cs_qc 的 exchange 和队列是否仍在 broker 上存在？依赖这些能力的流程（dev 中的 `asr_script.xml`、`asr_huawei_*.xml`、`text_sml.xml`，以及原子流程中的话术、文本相似度开关）是否已下线？
6. qa-server 消费 `scheduler.callback.queue` 时依赖哪些字段？`needSubTaskCallback` 在 scheduler 中未使用，是否由 qa-server 自行过滤？
7. `qa_detail` 所在的 StarRocks 库是否就是设计图中的“海山数据库”？写入方是不是 qa-server？
8. 流程 `timeout`（1–60 分钟）原计划在哪里生效？现在只有 24h/48h 兜底，这是否符合实时质检的 SLA？
9. `zhuanxian_qc_2.xml`（实时分片版专线质检）是否已上线？prod 目录中只有离线版 `zhuanxian_qc.xml`。
10. `model_meta`（qwen / deepseek 多版本）如何维护？没有管理接口。
11. MongoDB 各集合是否有 TTL 或归档策略？`shard_task` 是否建了 `callId` 索引？
12. `start.sh` 在实际部署中如何托管（systemd / supervisor / 容器）？`stop.sh` 能否找到进程？
13. `zhuanxian_qc.xml` 中 DeepSeek 判中风险分支写 `modelQcResult=1`、`zhuanxian_qc_2.xml` 中重复的 `keywordAnalysisLTStatus`，是笔误还是有意为之？
14. MySQL DDL（`task`、`smart_walnuts_sub_task`、`process`）是否还有任何系统在使用？如果没有，建议删除或标注为历史版本，避免与 MongoDB 模型的状态码定义冲突。
