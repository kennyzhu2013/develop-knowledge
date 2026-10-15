---
id: lesson-security-risks
title: 源码中的安全风险
domain: lessons
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - arch-security
  - lesson-known-issues-scheduler
  - lesson-known-issues-vi-as
  - lesson-known-issues-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-vi-pusher
  - lesson-known-issues-qa-server
  - lesson-known-issues-vi-cront-jobs
  - lesson-open-questions
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/CalculateCondition.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/http/handlers/ChangeCodeHandler.java
  - vi-assets/vi-pusher-master/config.prod/application.yml
  - vi-assets/vi-cront-jobs-master/dataCheck.py
---

# 源码中的安全风险

核对日期 2026-10-15。本文只写现象和代码位置。不抄密钥、连接串、内网地址、真实号码。这些接口在源码里没有鉴权，是否被网关挡住，待确认。

## 流程表达式可以执行任意代码

`CalculateCondition`、`TaskCondition`、`RedisCondition` 用 `StandardEvaluationContext` 执行流程 XML 里的表达式，可以调用任意 Java 类型，包括进程运行入口。流程 XML 通过 `POST /admin/process/create` 和 `POST /admin/process/update` 上传，`ProcessController` 上没有鉴权。

| 位置 | 说明 |
| --- | --- |
| `vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/CalculateCondition.java` | 计算条件 |
| `vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/TaskCondition.java` | 任务条件 |
| `vi-assets/scheduler-master/src/main/java/com/cmic/engine/condition/RedisCondition.java` | Redis 条件 |
| `vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java` | 上传与更新流程 |

模型表里的 authentication 会原样放进发往模型队列的消息，`GET /admin/aiModel/http/query` 的查询结果也包含它。见 `vi-assets/scheduler-master/src/main/java/com/cmic/dao/AIModelDaoImpl.java`。不要把查询结果贴到文档或工单。

关键词正则使用调用方传入的表达式，存在灾难性回溯，位置 `vi-assets/scheduler-master/src/main/java/com/cmic/service/ai/KeywordRegexService.java`。

## 未鉴权管理接口

| 服务 | 接口 | 代码位置 | 能做什么 |
| --- | --- | --- | --- |
| scheduler | `/admin/process/create`、`/admin/process/update`、`/admin/aiModel/http/query` | `vi-assets/scheduler-master/src/main/java/com/cmic/controller/ProcessController.java` | 上传可执行的流程 XML；读出模型凭据 |
| vi-as | `/control`、`/changeStartMode`、`/ops/pause` | `vi-assets/vi-as-master/src/main/java/com/cmic/http/handlers/ChangeCodeHandler.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/util/MyHTTPServer.java`、`vi-assets/vi-as-master/src/main/java/com/cmic/http/HTTPServer.java` | 反射修改运行时静态字段、切换启动模式、摘流量 |
| vi-merge | `/ops/config`、`/ops/omu/pause`、pprof | `vi-assets/vi-merge-master/internal/service/route/router.go` | 改配置、暂停、看堆栈 |
| offline-merge | `/v1/test`、`/testmetrics`、`/ops` | `vi-assets/offline-merge-master/internal/service/route/router.go` | 测试接口会真实上传并发合音消息；ops 可停消费或注销 |
| vi-billcenter | `/vibillcenter/test`、`/vibillcenter/task/recreate` | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/controller/ApplicationController.java` | 用写死的企业标识真实调用 qa-server；按时间窗重放失败质检 |

qa-server 的签名不覆盖请求体，5 分钟内同一签名可以换 body 重放。见 `vi-assets/qa-server-master/apps/__init__.py`。

## 配置里的硬编码凭据

只记录现象，不抄值。

| 位置 | 现象 |
| --- | --- |
| `vi-assets/vi-pusher-master/config.prod/application.yml` | 除了业务地址占位，还有注释掉的消息队列账号字段。仓库里留着历史凭据。 |
| `vi-assets/vi-pusher-master/pom.xml` | test profile 带有上传用的主机和凭据，默认打包会尝试上传并远程重启测试机。 |
| `vi-assets/vi-as-master/src/main/java/com/cmic/config/RocketMQConfig.java` | 启动时把每个配置节点的正文打到标准输出，节点内容可以包含访问密钥。 |

这些值应移出仓库并轮换。不要为了“清配置”直接删掉仍被 `@Value` 引用的键，vi-pusher 的 `second-reach-*` 没有默认值，删了起不来。见 `10-lessons/pitfalls/known-issues-vi-pusher.md`。

## 源码中的未脱敏号码

`vi-assets/vi-cront-jobs-master/dataCheck.py` 是调试脚本，源码中存在未脱敏手机号。不要把号码写进文档、日志摘录或工单。

同类问题（本文同样不转录具体值）：

| 位置 | 现象 |
| --- | --- |
| `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java` | 测试入口和注释里有号码或地址 |
| `vi-assets/vi-media-master/test/simulation/` | 测试程序里有地址和类号码字符串 |
| `vi-assets/offline-merge-master/internal/service/route/bizhandler/test_handler.go` | 注释掉的样例报文里有号码 |
| `vi-assets/scheduler-master/src/test/shell/curl.sh` | 测试命令里有地址和由号码组成的文件名 |

vi-merge、offline-merge、vi-billcenter、vi-pusher、scheduler 的 INFO 日志会打印主被叫或整段回调，其中可以有号码和转写。这是日志风险，不是把号码写进本文的理由。
