---
id: api-scheduler-admin
title: scheduler 入站 HTTP
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-qa-server-management
  - svc-scheduler
  - module-scheduler
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/TaskController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/AIModelController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/AITypeController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/EventController.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/pojo/dto/ErrorCode.java
---

# scheduler 入站 HTTP

scheduler 的主入口是 RabbitMQ。下面这些 HTTP 是同步建任务和管理接口。代码中没有鉴权拦截器，也没有 IP 白名单。网关是否另做限制，待确认。响应外壳为 `BaseResponse`：`code`、`message`、`data`。

**未鉴权：** 本表每一行都没有应用层鉴权。`/admin/**` 可以创建和热更新流程、读写模型地址。流程 XML 会被加载执行，不要对不可信来源开放。

## 1. 接口

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/v1/front/task/create` | 管理端或联调；主流程不走这里 | scheduler | TaskRequest：requestId、流程 id、data（含 dataId） | 0000，data.transactionId；1002 参数 | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/TaskController.java:audioCreate` |
| POST | `/v1/front/event/create` | 遗留 | scheduler | processId 等 | 0000。标注待下线，取值函数恒返回空 | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/EventController.java:create` |
| POST | `/admin/process/create` | 运维 | scheduler | multipart：xmlFile；表单 requestId、processName、xmlFileName、timeout（1–60）、aiModels | 0000，data.processId。先入库再写本地文件，不广播 | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java:processCreate` |
| GET | `/admin/process/query` | 运维 | scheduler | processId、processName、xmlFileName、status、page、size | 0000，`{processList,count}` | 同上 `processQuery` |
| GET | `/admin/process/history/query` | 运维 | scheduler | processId 必填 | 0000，按 startTime 倒序 | 同上 `hisProcessQuery` |
| POST | `/admin/process/update` | 运维 | scheduler | processId；可选 xmlFile、processName、status（0/1）、timeout、aiModels | 0000。写历史并广播热更新 | 同上 `processUpload` |
| GET | `/admin/process/queryXml/{processId}` | 运维 | scheduler | processId | XML 文本 | 同上 `showXmlFile` |
| POST | `/admin/aiModel/http/create` | 运维 | scheduler | model、desc、aiTypeId、httpUrl、requestParameters、responseParameters、authentication | 0000 | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/AIModelController.java:httpModelCreate` |
| POST | `/admin/aiModel/http/update` | 运维 | scheduler | 同上，按 model 定位 | 0000 | 同上 `httpModelUpdate` |
| GET | `/admin/aiModel/http/query` | 运维 | scheduler | model、aiTypeId、desc、page、size | 0000。结果含 authentication 字段，本文不记录其值，日志也不应打印 | 同上 `httpModelQuery` |
| POST | `/admin/aiModel/http/delete/{model}` | 运维 | scheduler | model | 0000 | 同上 `httpModelDelete` |
| POST | `/admin/aiModel/create` | 运维 | scheduler | model、desc、requestParameters | 0000 | 同上 `modelCreate` |
| POST | `/admin/aiType/create` | 运维 | scheduler | name、nickName、comment | 0000，id 为雪花 | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/AITypeController.java` |
| POST | `/admin/aiType/update/{aiTypeId}` | 运维 | scheduler | nickName、comment | 0000 | 同上 |
| GET | `/admin/aiType/query` | 运维 | scheduler | 无 | AIType 列表 | 同上 |
| POST | `/admin/aiType/delete/{aiTypeId}` | 运维 | scheduler | aiTypeId | 0000 | 同上 |

actuator（含 prometheus）是否暴露、路径是什么，由配置决定，待确认。qwen / deepseek 用的 model_meta 没有管理接口，只能直接写库，待确认。

## 2. 错误码

| code | message |
| --- | --- |
| 0000 | 成功 |
| 1001 | 失败 |
| 1002 | 参数错误 |
| 9998 | 超时 |
| 9999 | 异常 |

定义在 `vi-assets/scheduler-master/src/main/java/com/cmic/pojo/dto/ErrorCode.java`。

## 3. 待确认

1. 生产网关或网络策略是否挡住 `/admin/**`。应用代码本身未鉴权。
2. `/admin/aiModel/http/query` 返回的 authentication 在库里的形态，以及是否已有脱敏。
3. `/v1/front/event/create` 是否可以删除。
4. 监听端口与 actuator 暴露面。
