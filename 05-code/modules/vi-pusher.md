---
id: module-vi-pusher
title: vi-pusher 代码深度解析（对客推送）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-pusher]
source_of_truth: [vi-assets/vi-pusher-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-pusher-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-pusher.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-pusher 仓库分析报告

> 仓库：`/workspace/vi-assets/vi-pusher-master`（Java 源码根 `src/main/java/com/cmic/`，下文简写为 `c/`）
> 结论均来自代码；源码之外的佐证会单独注明；无法从代码确认的标"待确认"。敏感信息只写配置项名称。

---

## 1. 定位

vi-pusher 是专线质检链路的**对客推送服务**。它消费 vi-billcenter 投递的**质检结果消息**，按话单 ID（`listId`）查 `bill_new`，再从内存缓存的 `customer_tab.result_push_url` 取客户回调地址，用三级企业密钥生成 `EOPAUTH` 签名，然后经 httpProxy（ETCD 发现，请求 `POST {node}/proxy`，目标地址放在 `X-Forwarded-For` 头里）推送给客户。推送失败通过 RocketMQ 60s 延时消息重试，最多重试 3 次。最终成功、最终失败或客户未配置地址时，更新 `quality_inspection_status`（3 / -3），并把话单从 `bill_new` 移到按天分的历史表 `bill_yyyy_MM_dd`。

仓库里还保留了中间号时代的"话单推送"（`CommonBillPusher`）和"录音推送"（`CommonRecordPusher`）代码，但 MQ 订阅注解已被注释，而且推送 URL 硬编码为空字符串，**实际不生效**（见 §12）。

---

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言 / JDK | Java 21（`maven-compiler-plugin` source/target 21） |
| 框架 | Spring Boot 3.5.7；spring-boot-starter-web（Tomcat 11.0.9）、webflux（引入但未见使用）、jdbc、actuator、aop、validation；`spring-cloud-alibaba-dependencies` 2023.0.3.3（只做 BOM） |
| MQ | `rocketmq-v5-client-spring-boot-starter` 2.3.4 |
| DB | MySQL + Druid + `dynamic-datasource-spring-boot-starter` 4.3.1 + `JdbcTemplate`；`common-dao:sharding-database-table`（只用 `ResultSetConvertor`） |
| 注册中心 | `com.ziyan.common:xdiscovery`（`com.discovery.RegistryService`，基于 ETCD） |
| HTTP 客户端 | 质检推送用 `RestTemplate` + HttpClient5（`c/config/RestTemplateConfig`，与 vi-billcenter 相同）；遗留的话单 / 录音推送用 JDK `java.net.http.HttpClient`（`c/utils/HttpSender`、`HttpClientFactory`，信任所有证书） |
| 其它依赖 | jedis 6.0.0 + commons-pool2（**代码中没有使用 Redis**）、json-lib、poi、fastjson2、log4j2 + disruptor、micrometer-prometheus |
| 构建 | Maven。profile `test`（**默认激活**）：薄 jar + `lib/`，manifest `mainClass=com.cmic.PusherApplication`（**该类不存在**，实际是 `ViPusherApplication`）；带 `wagon-maven-plugin`，在 `package` 阶段通过 SFTP 上传到测试机并远程执行 `restart.sh`，**pom 里写有明文主机和凭据（此处不列出）**。profile `prod`：fat jar，`finalName=vi-pusher-jar-with-dependencies`，打包时排除 `config/` 下的 yml/xml/properties |
| 启动入口 | `c/ViPusherApplication.main`（`@SpringBootApplication @EnableAsync`） |
| 监听端口 | 业务 `server.port=9283`（`tomcat.max-connections=10000`、`max-threads=1000`）；管理端口 `19283`，`/manage/info/prometheus` |
| 部署脚本 | `bin/start.sh` 与 `bin.prod/start.sh` 相同：`java -Dlog4j.configurationFile=config/log4j2.xml -DLog4jContextSelector=...AsyncLoggerContextSelector -jar *.jar` |
| 环境区分 | `config/` 只有 `log4j2.xml`（与 `config.prod/log4j2.xml` 完全相同）；`config.prod/application.yml` 中敏感值为 `{{占位符}}`，但**也有若干外部地址被硬编码**（见 §11、§12）；`src/main/resources/application.properties` 只有 `spring.application.name=vi-pusher` |
| 日志 | `/var/log/vi-pusher/{all,warn,error}.log`，备份目录 `/var/log/vi-pusher/bak` |
| README | 只有一行 `# vi-pusher` |

---

## 3. 代码结构

| 包 / 目录 | 职责 |
| --- | --- |
| `c/ViPusherApplication` | 启动类 |
| `c/inspectionpusher/CommonInspectionPusher` | **核心**：推送质检结果给客户，包含重试、状态更新和移表 |
| `c/rocketmq/InspectionResultReceiver` | 消费 vi-billcenter 的质检结果消息（受开关 `pusher.inspectionpusher` 控制） |
| `c/rocketmq/InspectionDelayReceiver` / `InspectionDelaySender` | 质检推送重试用的延时队列（消费端**不受开关控制**） |
| `c/billpusher/CommonBillPusher`、`BillCommonFun` | 遗留：话单推送（失效） |
| `c/recordpusher/CommonRecordPusher` | 遗留：录音推送（失效） |
| `c/rocketmq/BillReceiver`、`RecordReceiver` | 遗留：`@RocketMQMessageListener` 已注释，不订阅 |
| `c/rocketmq/RecordDelaySender` | 遗留：发 `TID_VI_PUSHER_RECORD_PUSH_PUSHER_DELAY`，仓库内没有消费者 |
| `c/controller/TestController` | `/vipusher/task/callback`，只打日志的测试接口 |
| `c/dao/BillDao`、`BillHistoryDao`、`CommonDbDao` | 数据源 `vi_bill`：`bill_new`、历史分表；`CommonDbDao.checkTableExistence`（没有调用方） |
| `c/dao/MidAuthDao` | 数据源 `user_qc`：`company_tab`、`customer_tab` |
| `c/dao/DccConfigDao` | 数据源 `dcc_conf`：`file_down_host`（`host_type=2`） |
| `c/utils/*Manager`、`ApplicationInit` | 启动时加载企业、客户、下载 host 缓存 |
| `c/utils/Parameters` | 读配置；ETCD 注册 `com.cmic.vi.pusher`，Watch httpProxy 服务和 `/config/version` |
| `c/utils/AuthorizationUtil`、`AuthorizationGenHelp` | `EOPAUTH` 签名（与 vi-billcenter 相同） |
| `c/utils/HttpUtil` | RestTemplate POST |
| `c/utils/HttpSender`、`HttpClientFactory`、`CommonThreadPool` | JDK HttpClient 同步 / 异步发送、按代理节点缓存 client、回调线程池 |
| `c/utils/CommonUtil` | `UnconnectedCauseMap`（finish_state → 未接通原因）、`AliReleaseCauseMap`（未使用）、`generateRequestId` |
| `c/utils/TimeUtil`、`SpringContextHolder`、`MD5Util` | 工具类（基本没有使用） |
| `c/metrics/PusherMetrics` | Prometheus 指标 |
| `c/config/*` | 多数据源、JdbcTemplate、RestTemplate、RocketMQ Bean 角色修正 |
| `c/pojo/*` | `Bill`、`CompanyTab`、`CustomerTab`、`FileDownHost`、`CommonResult`（从 vi-billcenter 复制） |

---

## 4. 核心业务流程

### 4.1 流程 A：质检结果推送给客户（生效）

入口：`InspectionResultReceiver.consume`（首次推送）或 `InspectionDelayReceiver.consume`（重试），两者都进入 `CommonInspectionPusher.pushInspection(params)`。

| 步骤 | 做什么 | 代码位置 | 失败处理 / 重试 / 超时 |
| --- | --- | --- | --- |
| A1 | 解析消息 JSON 得到 Map，取 `listId`（即话单 ID） | `InspectionResultReceiver.consume` L48-51 | 解析异常只打日志；**始终 ACK** |
| A2 | `findBillNewById(listId)`，查不到就 return | `CommonInspectionPusher.pushInspection` L52-56 | **直接丢弃**，不重试、不计数 |
| A3 | 读取 `retryTimes`：没有这个字段时按首次处理（本地变量为 1）；有且 `> 3` 时进入**最终失败**处理：`quality_inspection_status=-3`，移表，计数 `inspection.push.error`，return；否则 `retryTimes+1`，并从 params 删除 `retryTimes`（避免推给客户） | L57-75 | — |
| A4 | 从缓存取 `CompanyTab`（`bill.companyId`）和 `CustomerTab`（`bill.customerId`），任一为 null：状态置 `-3`，`retryTimes` 写回 params，发 **60s** 延时重试 | L76-85 | 计入重试次数 |
| A5 | `customer_tab.result_push_url` 为空：视为**无需推送**，状态置 `3`，移表，计数 success | L88-99 | — |
| A6 | 组装请求头（§7.1），`RegistryService.GetServiceNode(httpproxy-name)` 选一个 httpProxy 节点，`POST {node}/proxy`，请求体为 params（即 qa-server 回调体） | L101-103 | RestTemplate 超时：连接 2s，响应 8s，取连接 200ms |
| A7 | HTTP 2xx 且响应 JSON `code == "0000"`：状态置 `3`，移表，计数 success，return | L104-123 | — |
| A8 | 其它情况（非 2xx、没有 `code` 字段、`code` 不是 0000、任何异常）：状态置 `-3`，`retryTimes` 写回，发 60s 延时消息 `TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY` | L125-137；`InspectionDelaySender.sendInspectionResultDelay` | 延时消息发送失败只打日志，这条结果即丢失 |

**重试时间线**（逐步追踪 `retryTimes` 得出）：

| 时刻 | 消息中的 retryTimes | 动作 |
| --- | --- | --- |
| T0 | 无 | 第 1 次推送，失败后写入 1 |
| T0+60s | 1 | 改为 2，第 2 次推送，失败后写入 2 |
| T0+120s | 2 | 改为 3，第 3 次推送，失败后写入 3 |
| T0+180s | 3 | 改为 4，第 4 次推送，失败后写入 4 |
| T0+240s | 4 | `>3`，最终失败：状态置 `-3` 并移表 |

也就是说**共推送 4 次（首次 + 3 次重试），固定间隔 60s，没有退避**，约 240s 后进入最终失败。客户收到的报文里不包含 `retryTimes`。

**移表**：`billDao.createBillHistory(bill)` 写入 `bill_{yyyy_MM_dd(handle_start_time)}`，写入行数为 1 时 `deleteBillNew`。写入的是内存里的 bill，状态已经设为 3 或 -3。

### 4.2 客户回调地址从哪里来

- 来源：`user_qc.customer_tab.result_push_url`（四级终端客户粒度），由 `MidAuthDao.selectAllCustomerTab` 全量加载到 `CustomerTabManager.customerTabMap`（key 为 `customer_tab.id`）。
- 匹配：`bill_new.customer_id` → `customer_tab.id`。签名密钥用 `bill_new.company_id` → `company_tab.app_key/app_secret`。
- 刷新：ETCD key `/config/version` 收到 PUT 时 reload（`Parameters.parameterInit`）。推测由"对客质检统一运营平台"修改配置后触发，待确认。
- 注意：`customer_tab.url`（DDL 注释为"质检复核回调地址"）**没有使用**。
- 真正发往客户的地址并不在 URL 里，而是放在请求头 `X-Forwarded-For` 中交给 httpProxy 转发。httpProxy 的转发语义不在本次源码中，待确认。

### 4.3 流程 B：遗留话单 / 录音推送（失效，仅说明）

- `BillReceiver`、`RecordReceiver` 的 `@RocketMQMessageListener` 已注释（原订阅 `TID_VI_BILLCENTER_BILL_PUSH_PUSHER_NORMAL` / `TID_DCC_BILLCENTER_RECORD_PUSH_PUSHER_NORMAL`），所以它们只是普通 Bean，不会被 RocketMQ 容器注册（推断，依据 starter 的注解扫描机制）。
- 即使被调用，`CommonBillPusher.pushBill` 和 `CommonRecordPusher.pushRecording` 中的 `String url = "";` 也会让它们**永远走"无需推送，直接归档"分支**，只更新 `is_bill_ok=2` / `is_record_ok=2` 并按条件移表。
- 保留下来的报文格式（中间号话单）：`CallID, InboundCallerNo, OutboundCalleeNo, CallTime, CalloutTime, RingTime, StartTime, ReleaseTime, CallDuration, UnconnectedCause, ReleaseCause, RecordUrl, RequestId`（`CommonBillPusher.createParams`）。
- `BillCommonFun.recordDelaySender` **没有 `@Autowired`**，一旦调用 `checkRecrodDelay` 就会 NPE。

---

## 5. 对外接口（入站）

| 协议 | 方法 + 路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | `POST /vipusher/task/callback`（端口 9283） | `c/controller/TestController.taskCallback` | 任意 JSON（读取 `taskId`） | `CommonResult{code:"0000",message:"回调处理成功"}` | 测试 / 占位接口，只打日志 |
| HTTP | `GET /manage/info/prometheus`（端口 19283） | actuator | — | 指标 | — |

除此之外，服务的主要输入都是 MQ。

---

## 6. 消息

| 方向 | 中间件 | topic | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 消费 | RocketMQ | `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL` | `GID_VI_ONE_PUSHER_INSPECTION_PUSH`（线程数 `inspectionConsumerCount`）；**仅在 `pusher.inspectionpusher=true` 时启用** | qa-server 子任务回调体，由 vi-billcenter 原样转发：`taskId, listId, status, qcResult, riskLevel, errorMessage, data, userData, duration, transcription, matchedWord, summary ...`（字段集合以 qa-server `apps/result_consumer.py` 为准） | `InspectionResultReceiver` |
| 生产 + 消费 | RocketMQ | `TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY`（延时 60s，KEYS=话单 id） | 消费组 `GID_VI_ONE_PUSHER_INSPECTION_DELAY`（线程数 `delayRecordConsumerCount`）；**不受开关控制** | 原回调体 + `retryTimes` | `InspectionDelaySender` / `InspectionDelayReceiver` |
| 生产（死代码） | RocketMQ | `TID_VI_PUSHER_RECORD_PUSH_PUSHER_DELAY` | 仓库内没有消费者 | 纯文本话单 id | `RecordDelaySender.sendBillDelay` |
| 原消费（已注释） | RocketMQ | `TID_VI_BILLCENTER_BILL_PUSH_PUSHER_NORMAL` / `TID_DCC_BILLCENTER_RECORD_PUSH_PUSHER_NORMAL` | `GID_VI_PUSHER_BILL_PUSH` / `GID_DCC_PUSHER_RECORD_PUSH` | `Bill` JSON / 录音参数 | `BillReceiver` / `RecordReceiver`（不生效） |

所有 Consumer 都无条件返回 `ConsumeResult.SUCCESS`。

---

## 7. 依赖调用（出站）

### 7.1 httpProxy → 客户（质检结果推送，生效）

- 地址：`RegistryService.GetServiceNode(parameters-config.httpproxy-name)` 返回的节点，拼接 `+ "/proxy"`，请求 `POST`。节点格式应为 `http://ip:port`，待确认。
- 实现：`HttpUtil.sendPostRequest`（RestTemplate）。`Content-Type: application/json;charset=UTF-8`，`Accept: application/json`。
- 超时：连接 2s，响应 8s，取连接 200ms；连接池 3000，每路由 300（`RestTemplateConfig`）。没有同步重试，重试全靠 §4.1 的 MQ 延时。
- 请求头（`CommonInspectionPusher.createheader`）：

| Header | 值 | 说明 |
| --- | --- | --- |
| `Authorization` | `EOPAUTH appid="<company_tab.app_key>",timestamp="<秒>",signature="<MD5(timestamp+company_tab.app_secret) 大写 hex>"` | 客户可用三级企业密钥验签 |
| `X-Forwarded-For` | `customer_tab.result_push_url` | **客户真实回调地址**，httpProxy 据此转发（待确认） |
| `X-Session-Id` | `listId`（话单 ID） | 链路追踪 |
| `X-Platform-Id` | `company_tab.id` | 三级企业 ID |
| `X-User-Id` | `customer_tab.id` | 四级客户 ID |
| `X-App-Id` | `customer_tab.app_id` | 客户终端 ID |
| `X-Timeout` | `10000` | 交给 httpProxy 的超时（ms），**比本端 8s 响应超时还长**（见 §12） |

**推送给客户的报文格式**：就是 qa-server 子任务回调体（经 vi-billcenter 原样转发），去掉 `retryTimes` 后原样作为 JSON body，本服务**不做任何字段裁剪或映射**。根据 qa-server 代码，可能出现的字段如下（以 qa-server 为准）：

```json
{
  "taskId": "<质检任务ID>",
  "listId": "<话单ID>",
  "status": "1",
  "errorMessage": "",
  "qcResult": "0|1",
  "riskLevel": "<风险等级>",
  "data": "<透传>",
  "duration": 0,
  "userData": {
    "callerNumber": "700xxxx0001",
    "calleeNumber": "<被叫, 示例省略>",
    "handleStartTime": "yyyy-MM-dd HH:mm:ss",
    "companyId": 0,
    "customerId": 0,
    "ctrlAction": "...",
    "audioCode": "...",
    "audioDirection": "...",
    "callId": "<话单ID>"
  },
  "transcription": "<ASR 转写>",
  "matchedWord": "...",
  "summary": "..."
}
```

`userData` 中的号码、companyId、customerId 来自 vi-billcenter 发起质检时传的 `userData`；`ctrlAction`、`audioCode`、`audioDirection`、`callId` 只在 `qcResult=="0"`（异常）且有 `riskLevel` 时由 qa-server 填充。

客户响应格式要求：HTTP 2xx 且 JSON 里有 `code` 字段、值为 `"0000"`（小写 `code`，**与遗留话单推送要求的大写 `Code` 不同**），否则视为失败。

### 7.2 ETCD

- 注册：`com.cmic.vi.pusher` → `server.address:server.port`
- Watch：服务 `{httpproxy-name}`，key `/config/version`

### 7.3 遗留（失效）

`HttpSender.sendPostRequestAsync(host + "/proxy", ...)`（JDK HttpClient，请求超时 10s、连接超时 15s，信任所有证书），用于话单 / 录音推送。

---

## 8. 数据

### 8.1 MySQL

| 数据源 | 表 | 读 / 写 | 用途 |
| --- | --- | --- | --- |
| `vi_bill` | `bill_new` | 读（`SELECT * WHERE id=?`）；写（`UPDATE quality_inspection_status`；`DELETE WHERE id=?`）；遗留代码还会写 `is_bill_ok`、`is_record_ok`、`left/right_file_length`、`file_duration` | 在途话单 |
| `vi_bill` | `bill_yyyy_MM_dd` | 写（INSERT 44 列，与 vi-billcenter 相同） | 历史话单，按 `handle_start_time` 日期分表 |
| `vi_bill` | `SHOW TABLES LIKE 'bill_<date>'` | 读 | `CommonDbDao.checkTableExistence`，没有调用方 |
| `user_qc`（primary） | `company_tab` | 读全表 | `app_key/app_secret` 用于签名，`id` 用于 `X-Platform-Id` |
| `user_qc` | `customer_tab` | 读全表 | **`result_push_url` 即客户回调地址**；`id`、`app_id` 用于请求头 |
| `dcc_conf` | `file_down_host` | 读（`host_type=2`） | 加载到缓存，但质检推送不使用（遗留录音 URL 的 host 也被硬编码为空） |

`bill_new` 字段含义见 vi-billcenter 报告 §8（两仓库的实体和映射完全一致）。本服务关心的字段是 `id`、`company_id`、`customer_id`、`handle_start_time`（决定分表）和 `quality_inspection_status`。

### 8.2 Redis / MongoDB / 文件

- Redis：pom 引入了 jedis，但**代码中没有使用**。
- MongoDB：不使用。
- 文件：只有日志。

---

## 9. 状态与枚举

| 名称 | 取值 | 含义 | 出处 |
| --- | --- | --- | --- |
| `quality_inspection_status` | 3 | 推送质检结果成功，**或客户没有配置 `result_push_url`** | `CommonInspectionPusher` L90、L113 |
| | -3 | 推送质检结果失败。每次失败都会写 -3；超过重试次数后保持 -3 并移表 | L61、L80、L133 |
| `retryTimes` | 无 / 1~4 | 重试计数，`>3` 时结束 | L57-75 |
| 客户响应 `code` | `"0000"` | 成功 | L112 |
| `is_bill_ok` / `is_record_ok`（遗留） | 0 未推送；1 推送成功；2 URL 为空直接归档；-1 推送失败；10 无需推送（DDL） | 遗留话单 / 录音推送 | `CommonBillPusher`、`CommonRecordPusher`、话单DDL.docx |
| `UnconnectedCauseMap`（遗留） | finish_state 到原因码：5→6，7→3，8→2，10→4，11→5，12→7，15→1，其它→99 | 中间号话单的未接通原因 | `c/utils/CommonUtil` |
| `parameters-config.location` | 1 南方节点，2 北方节点，3 联调环境 | — | yml 注释 |

Prometheus 指标（`c/metrics/PusherMetrics`）：

| 指标 | 含义 |
| --- | --- |
| `vi.pusher.inspection.push.success.count` | 质检推送成功（含"无需推送"） |
| `vi.pusher.inspection.push.error.count` | 质检推送**最终失败**（只在重试耗尽时计数，中间失败不计） |
| `vi.pusher.db.connection.timeout.count` | DB 取连接超时 |
| `vi.pusher.db.update.time` | `is_bill_ok` 更新耗时 |
| `vi.pusher.bill.push.*` / `bill.repush.*` / `record.push.*` / `record.repush.*` | 遗留指标 |
| `vi.pusher.unused.historytable.count` | Gauge，启动时固定设为 7，没有实际含义 |

---

## 10. 定时任务

没有。启动类没有 `@EnableScheduling`，也没有 `@Scheduled` 方法。配置里的 `pusher.repusher`、`pusher.billnewclear` 在本仓库代码中没有对应实现（可能是中间号 dcc-pusher 的遗留开关）。

---

## 11. 关键配置项

位于 `config.prod/application.yml`，只写名称和含义。

| 配置项 | 含义 |
| --- | --- |
| `server.port` | 9283 |
| `server.address` | 绑定 IP，同时作为 ETCD 注册地址 |
| `server.tomcat.max-connections` / `max-threads` | 10000 / 1000 |
| `rocketmq.producer.*` / `rocketmq.push-consumer.*`（`endpoints`、`access-key`、`secret-key`） | RocketMQ 接入点和凭据（占位） |
| `spring.datasource.dynamic.primary` | `user_qc` |
| `spring.datasource.dynamic.datasource.{user_qc,dcc_conf,vi_bill}.*` | 三个 MySQL 数据源（与 vi-billcenter 相同）及 Druid 参数 |
| （注释掉的）`spring.rabbitmq.*` | 遗留 RabbitMQ 配置，**注释中有明文地址和凭据**，应删除 |
| `management.server.port` / `address`、`management.endpoints.web.base-path` | 19283，`/manage/info`，只暴露 prometheus |
| `parameters-config.location` | 节点：1 南，2 北，3 联调 |
| `parameters-config.cloud` | 读取但未使用 |
| `parameters-config.etcd` | ETCD 地址（占位） |
| `parameters-config.monitor-callout` / `monitor-billinfo` / `monitor-ringinfo` | 中间号第三方客户监控推送地址，**硬编码外部域名**，代码未读取 |
| `parameters-config.ymm-shortbill-time`、`timeInterval` | 中间号遗留，未读取 |
| `parameters-config.second-reach-prod` / `second-reach-test` / `second-reach-secretary-prod` / `second-reach-secretary-test` | 中间号"二次触达"地址，**硬编码外部 IP / 域名**。`@Value` 必填（缺失时启动失败），但业务不使用 |
| `parameters-config.video-play-test` | 遗留，硬编码外部地址，未读取 |
| `parameters-config.version` | 读取但未使用 |
| `parameters-config.httpproxy-name` | **httpProxy 在 ETCD 的服务名**（占位），质检推送必需 |
| `parameters-config.cluster-name` | ETCD 集群名 `vi` |
| `parameters-config.debug-record-host`、`ecloud-south-record`、`ecloud-north-record` | 遗留录音域名，硬编码外部域名。`debug-record-host` 被读取但未使用，其余两项未读取 |
| `parameters-config.dbnum` | 未读取 |
| `parameters-config.max-concurrency` | 读取但未使用 |
| `parameters-config.internal-platform-list` | **未被读取**，`Parameters.inernalPlatformSet` 永远为空 |
| `parameters-config.billConsumerCount` / `recordConsumerCount` | 遗留 Receiver 的线程数（50 / 30） |
| `parameters-config.inspectionConsumerCount` | 质检结果消费线程数，40 |
| `parameters-config.delayRecordConsumerCount` | 质检延时重试消费线程数，30 |
| `pusher.billpusher` / `pusher.recordpusher` | true。控制遗留 Receiver Bean 是否创建，但其订阅注解已注释，开关实际无效 |
| `pusher.inspectionpusher` | 占位。**为 true 才会消费质检结果主 topic**（`matchIfMissing=false`） |
| `pusher.repusher` / `pusher.billnewclear` | 占位。本仓库代码不使用 |

各环境差异：只能看出通过 `{{占位符}}` 渲染（rocketmqIps、db_*、location、etcd1-3、vi_bill_billproxy_app_name、repusher、billnewclear、inspectionpusher）。dev / test 配置不在仓库中。

---

## 12. 坑与特殊逻辑

1. **开关不对称**：`InspectionResultReceiver` 受 `pusher.inspectionpusher` 控制，`InspectionDelayReceiver` 不受控制。主消费关闭的实例仍会消费重试队列并真实推送客户；开关为 false 的实例也必须能访问 httpProxy 和数据库。
2. **超时错配会导致重复推送**。`X-Timeout: 10000` 让 httpProxy 最多等 10s，而本端 RestTemplate 响应超时只有 8s。客户响应慢（8~10s）时，本端判定失败并在 60s 后重推，但客户可能已经收到并处理了。客户侧必须按 `listId` / `taskId` 做幂等。
3. **"查不到话单就丢弃"**。话单已经移表（例如重复回调、或上一次推送已成功）时直接 return，这是合理的去重；但如果话单 ID 写错或 DB 瞬时异常（异常会被 Receiver 捕获后 ACK），结果会**静默丢失**，没有计数。
4. **`result_push_url` 为空时状态记为 3**（推送成功），和真正推送成功无法区分，运营统计会偏高。
5. **固定 60s 间隔、共 4 次**，没有指数退避。客户长时间故障时，第 5 次投递到达后直接置 `-3` 并移表，没有死信、补推接口或告警（只有 `inspection.push.error.count` 指标）。延时消息本身发送失败时，结果彻底丢失。
6. **中间失败也会写 -3**。在重试期间 `bill_new` 的状态就是 -3，无法区分"重试中"和"最终失败"。只能看 `bill_new` 和历史表：最终失败的会在历史表中，状态仍为 -3。
7. **企业或客户缓存缺失也走重试**（L78-85）。如果是配置漏配，4 分钟后话单会被置 `-3` 并移表。缓存只在 `/config/version` 变更或重启时刷新。
8. **移表不是事务**：先 INSERT 历史表再 DELETE `bill_new`。重复消息并发时，第二次 INSERT 会抛 Duplicate entry，异常被 L129 捕获后置 -3 并发延时重试，下次查不到话单就丢弃。客户可能已经收到两次推送。
9. **日志格式错误**：L130 `log.error("[{}] - push inspection result occurs exception: {}", e)` 少了话单 id 参数，异常信息会错位到第一个占位符，排障时看不到话单 ID。
10. **敏感信息进入日志**：`InspectionResultReceiver` 以 INFO 级别打印完整消息体（可能包含转写文本、号码）；推送响应体也会打印。违反"日志不得写真实号码 / 录音文本"的要求。
11. **对客报文直接透传内部字段**：`userData.companyId`、`customerId`，内部 `taskId`、`data` 等原样发给客户，没有对外契约层，qa-server 回调字段一变客户就受影响。改 qa-server 回调结构前必须评估对客影响。
12. **httpProxy 节点不可用**：`GetServiceNode` 返回 null 时 URL 为 `"null/proxy"`，抛异常后进入重试，没有专门告警。
13. **遗留代码大面积失效**：话单 / 录音推送的 URL 硬编码为 `""`，`BillReceiver` / `RecordReceiver` 的订阅注解被注释，`BillCommonFun.recordDelaySender` 没有注入（会 NPE），`TID_VI_PUSHER_RECORD_PUSH_PUSHER_DELAY` 没有消费者，`internal-platform-list` 没有被读取，jedis / webflux / poi / json-lib 是多余依赖。删除前应查 `09-decisions/`、`10-lessons/` 确认没有回切计划。
14. **配置中硬编码外部地址和明文凭据**：`config.prod/application.yml` 中的 monitor-* / second-reach-* / video-play-test / *-record 等外部地址，注释掉的 RabbitMQ 账号密码，以及 `pom.xml` test profile 中的 SFTP 主机和凭据（**默认 profile 每次 `mvn package` 都会尝试上传并远程重启测试机**）。建议清理并轮换凭据。
15. **`second-reach-*` 是必填配置**（`@Value` 没有默认值）。删掉这些"无用"配置会导致启动失败，需要先删 `Parameters` 中对应的字段。
16. **构建坑**：test profile 的 `mainClass=com.cmic.PusherApplication` 不存在；test profile 还会上传 `target/BillPusher-0.0.1-SNAPSHOT.jar`，与实际 artifact 名 `vi-pusher` 不符，wagon 上传应该会失败，待确认。
17. `HttpClientFactory` / `HttpSender` 信任所有证书；RestTemplate 关闭了主机名校验。
18. `ApplicationInit` 和 `Parameters` 都在 `@PostConstruct` 中初始化，执行顺序不确定。缓存加载在 `ApplicationInit` 中完成，ETCD 初始化在 `Parameters` 中完成，任一失败都会 `System.exit(1)`。

---

## 13. 与其它仓库的关系

| 方向 | 对端 | 契约 |
| --- | --- | --- |
| 上游 | vi-billcenter | MQ `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`，消息体 = qa-server 子任务回调体（KEYS=listId） |
| 间接上游 | qa-server | 回调字段（`taskId,listId,status,qcResult,riskLevel,userData,transcription,...`）原样推给客户，qa-server 改字段会直接影响客户 |
| 下游 | httpProxy（不在本次源码） | `POST {node}/proxy`，目标地址在 `X-Forwarded-For`，超时在 `X-Timeout`，会话 ID 在 `X-Session-Id`；ETCD 服务名为 `httpproxy-name` |
| 下游 | 客户系统 | 接收 JSON，用 `EOPAUTH` 头验签，响应 `{"code":"0000"}` 表示成功 |
| 共享表 | vi-billcenter / vi-AS | `vi_bill.bill_new`（本服务负责质检链路的最终移表和删除）、`bill_yyyy_MM_dd`、`user_qc.company_tab/customer_tab`、`dcc_conf.file_down_host` |
| 配置 | 对客质检统一运营平台 | 维护 `customer_tab.result_push_url` 等配置，并通过 ETCD `/config/version` 触发 reload（推断） |
| 注册 | ETCD | `com.cmic.vi.pusher` |

---

## 14. 待确认问题清单

1. httpProxy 的 `/proxy` 语义：是否按 `X-Forwarded-For` 转发？`X-Timeout` 如何生效？是否会自行重试？（影响重复推送的风险判断）
2. 生产环境 `pusher.inspectionpusher` 在各实例上的取值。是否存在只开重试消费、不开主消费的实例？
3. 客户侧对重复推送的幂等约定是什么（以 `listId` 还是 `taskId` 为准）？是否有对外接口文档（例如 `语音专线质检信息报备接口说明书`）约束推送报文字段？目前代码是原样透传。
4. `result_push_url` 为空时记为 3，这是业务上的有意设计吗？
5. 重试耗尽（-3）的话单是否需要补推接口或告警？目前只有指标。
6. `customer_tab.url`（质检复核回调地址）的用途是什么？目前没有使用。
7. 遗留的话单 / 录音推送是否计划恢复？如不恢复，能否清理相关代码、topic、配置和多余依赖？
8. `config.prod` 和 `pom.xml` 中的明文外部地址和凭据是否已轮换？是否允许从仓库中移除？
9. 二期 `bill_new` 新增的 `quality_inspection_final_result`、`control_action` 等字段，是否需要随推送带给客户或在移表时保留？目前历史表 INSERT 没有包含这些列。
