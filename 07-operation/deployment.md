---
id: ops-deployment
title: 专线质检部署手册（9 个服务）
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ops-monitoring, ops-emergency, arch-middleware, runbook-scheduler-master-failover, runbook-traffic-control-misjudge]
source_of_truth:
  - vi-assets/vi-as-master/bin.prod/start.sh
  - vi-assets/vi-as-master/config.prod/application-config.xml
  - vi-assets/vi-billcenter-master/bin.prod/start.sh
  - vi-assets/vi-billcenter-master/config.prod/application.yml
  - vi-assets/vi-pusher-master/config.prod/application.yml
  - vi-assets/qa-server-master/Dockerfile
  - vi-assets/qa-server-master/gunicorn_conf.py
  - vi-assets/scheduler-master/bin.prod/start.sh
  - vi-assets/vi-media-master/bin/start.sh
  - vi-assets/vi-media-master/config.prod/app.toml
  - vi-assets/vi-merge-master/bin/start.sh
  - vi-assets/vi-merge-master/config.prod/app.yaml
  - vi-assets/offline-merge-master/config.prod/app.yaml
  - vi-assets/vi-cront-jobs-master/README.md
---

# 专线质检部署手册

本文按源码中的 `bin*/`、`config*/` 目录和《话务管控上线依赖》整理。地址、账号、密钥一律不写，只写配置项名和去哪里看。监控见 `07-operation/monitoring.md`，应急见 `07-operation/emergency/overview.md`。

## 1. 部署总表

### 1.1 运行时与启停

| 服务 | 语言/运行时 | 构建产物 | 启动 | 停止 / 重启 | JVM / 进程参数 |
| --- | --- | --- | --- | --- | --- |
| vi-as | Java 21，Maven | prod profile 产出 jar-with-dependencies，finalName `vi-as` | `bin.prod/start.sh`：前台 `java ... -jar vi-as*.jar`（脚本里没有 nohup） | **没有 stop 脚本**；优雅停机用 `kill -2 <pid>`（见 §3.1） | `-javaagent:./jmx_prometheus_javaagent-0.12.0.jar=8588:config.yaml -server -Xms21g -Xmx21g -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Dlog4j.configurationFile=config/log4j2.xml -DLog4jContextSelector=AsyncLoggerContextSelector -Djava.library.path=.`；dev `bin/start.sh` 的堆为 1g |
| vi-billcenter | Java 21，Spring Boot 3.5.7 | prod profile 产出 `vi-billcenter-jar-with-dependencies.jar` | `bin.prod/start.sh`：`java -Dlog4j.configurationFile=config/log4j2.xml -DLog4jContextSelector=... -jar *.jar` | 无 stop 脚本（kill 进程，见 §3.4） | **没有设置堆大小**，依赖 JVM 默认值（待确认生产是否另外注入） |
| vi-pusher | Java 21，Spring Boot | jar-with-dependencies | `bin.prod/start.sh`，同 vi-billcenter | 无 stop 脚本 | 同 vi-billcenter，没有设置堆大小 |
| qa-server | Python 3 / Flask / Gunicorn（gthread，timeout 60） | Docker 镜像（`Dockerfile`；测试用 `DockerfileTest`） | 容器 CMD：`rm -rf $PROMETHEUS_MULTIPROC_DIR && mkdir -p ... && exec gunicorn -c gunicorn_conf.py app:app` | 停止或重启容器；gunicorn pid 文件在 `LOGGING_PATH` | Worker 数和线程数由 `GUNICORN_WORKERS`、`GUNICORN_THREADS` 决定；MQ 消费和 APScheduler 运行在 **master 进程** |
| scheduler | Java 21，Spring Boot 3.2 | `scheduler-jar-with-dependencies.jar` | `bin.prod/start.sh`：前台 `java -server -Xms16g -Xmx16g -Xmn4g -XX:+UseParallelGC -classpath ./config -jar scheduler-jar-with-dependencies.jar --logging.config=config/logback.xml` | `bin.prod/stop.sh`、`restart.sh`（有缺陷，见 §4） | prod/dev 堆 16g；`bin.test` 为 4–6g |
| vi-media | Go 1.24 | `build.sh <version>` 产出 `bin/vi-media` | `bin/start.sh`：`GOTRACEBACK=crash nohup ./vi-media >/dev/null 2>&1 &`（不带 `--conf`，读默认 `config/app.toml`） | `bin/stop.sh <端口>`：lsof 找端口进程 → `kill -TERM` → **无限等待**进程退出 | 启动前必须配置 `/proc/sys/net/ipv4/ip_local_reserved_ports`，保留 RTP 端口段 |
| vi-merge | Go 1.24 | `build.sh <version>` 产出 `bin/vi-merge`；同目录放 `ffmpeg` | `bin/start.sh`：先 `chmod 755 ./ffmpeg`，再 `nohup ./vi-merge &` | `bin/stop.sh <端口>`，同 vi-media | 进程内放开 RLIMIT_CORE，RLIMIT_NOFILE=0x7FFFF |
| offline-merge | Go | `build.sh <version>` 产出 `bin/offline-merge` | `bin/start.sh`：`GOTRACEBACK=crash nohup ./offline-merge >/dev/null 2>&1 &`（不 chmod ffmpeg） | `bin/stop.sh <端口>`，同 vi-media | — |
| vi-cront-jobs | Python 3.11+ 脚本（无常驻进程） | 源码目录 + venv | crontab 每分钟或按表执行 `<venv>/bin/python /home/production/vi-cront-jobs/<脚本>` | 注释掉 crontab 行即停 | venv 例 `/data/venv/cront-jobs11`；crontab 没有 flock |

### 1.2 端口、配置、注册与日志

| 服务 | 监听端口与用途 | 配置目录与环境区分 | 配置注入方式 | ETCD 注册名 | 日志目录 |
| --- | --- | --- | --- | --- | --- |
| vi-as | UDP 5080 SIP（`asListen`）；HTTP `httpPort`：prod 5287（写死在 `config.prod/application-config.xml`），dev 5668；8588 JMX exporter（`/metrics`，OS 指标） | `config/`（dev）、`config.prod/`；`bin*/config.yaml` 是 JMX exporter 规则，不是业务配置 | `config.prod/application-config.xml` 中 `{{}}` Jinja 占位：location、serverId、asListen ip、httpIp、etcd×3、SLBAddress、proxyMode、httpProxyName、startMode | 节点根 `etcdNodeName=vi`（注册名 待确认）；发现 `com.cmic.vi.media`、`com.cmic.xsipproxy`、`com.cmic.vi.strategy` 和 httpProxyName 对应服务 | `/var/log/vi-as/`（滚动备份在 `/var/log/vi-as/bak`） |
| vi-billcenter | 9277 业务（`/vibillcenter/*`）；19277 管理（`/manage/info/prometheus`） | 仓库 `config/` 只有 `log4j2.xml`；`config.prod/application.yml` 是模板 | Jinja 占位；部署时需把渲染后的 `config.prod` 内容放到 `config/`（待确认） | `com.cmic.vi.billcenter`（地址取 `server.address:server.port`） | `/var/log/vi-billcenter/`（all/warn/error/recover.log） |
| vi-pusher | 9283 业务；19283 管理（`/manage/info/prometheus`） | 同 vi-billcenter | Jinja 占位；`pusher.inspectionpusher` 等开关在 application.yml | `com.cmic.vi.pusher` | `/var/log/vi-pusher/`（all/warn/error.log） |
| qa-server | `GUNICORN_BIND`（README 示例 7220）HTTP；`PROMETHEUS_PORT`（README 示例 7225）metrics | `FLASK_ENV=production`→`config.prod/`，`test`→`config.test/`，其它→`config.local/`（**仓库中不存在**） | `config.<env>/config.yaml` 不在仓库，部署注入；环境变量 `QA_SERVER_ID`（雪花机器号，必须唯一）、`PROMETHEUS_MULTIPROC_DIR`、`QA_CACHE_VERSION_FILE` | 不注册服务；watch `ETCD_CONFIG` 中的 config_key / rule_key / number_key | `LOGGING_PATH`，按代码为 `/var/log/qa-server/`（info/errors/all/realtime/result/flush.log + gunicorn error/access） |
| scheduler | `server.port`（待确认，application.yml 不在仓库）；actuator prometheus | `config.dev/`、`config.test/`、`config.prod/` 只有 `logback.xml` | `application.yml` 被 gitignore，部署注入；`-classpath ./config` 加载 | 不使用 ETCD（RabbitMQ + Mongo） | `/var/log/scheduler/`（logback，保留 15 天，单文件 200MB） |
| vi-media | 9060 API（`apiAddress`：/call、/media/download、/som/*、/metrics、pprof）；UDP 10000–30000 RTP（约 5000 会话） | 仅 `config.prod/app.toml` | `{{}}` 占位：ip、imsip、appName、id、etcd×3、rocketmq_*、outbound*、cdn*、audioPath、videoPath、idxDir、diskLifeSpan；渲染到 `config/app.toml` | clusterName `vii`，appName 来自模板，前缀 `/vii/micro-registry` | `/var/log/vi-media/all.log` |
| vi-merge | 9033 WS `/v1/merge`；9034 运维（/metrics、/ops/config、/ops/configManifest、/ops/omu/status\|pause\|resume、pprof） | 仅 `config.prod/app.yaml` | `{{}}` 占位，渲染到 `config/app.yaml`（渲染方式 待确认） | `com.cmic.vi.merge`（cluster `vii`），maxSession 2000 | `/var/log/vi-merge/all.log` |
| offline-merge | 9036 业务（含危险的 `/v1/test`）；9037 内部（/metrics、/health、/ops/config、/ops/omu/status\|pause\|resume\|stop、/ops/mq/stop\|start、pprof） | 仅 `config.prod/app.yaml` | 同 vi-merge | `com.cmic.vi.offline` | `/var/log/offline-merge/all.log` |
| vi-cront-jobs | 无端口 | `CRONT_ENV=production` 或不设→`config/`，`test`→`config.test/`，其它→`config.local/`；南/北区目录不存在 | `config.yaml` 由 Jinja2 用环境变量渲染，不在仓库（变量名 待确认） | 不注册 | `/var/log/vi-cront-jobs/`（info.log、errors.log、info-vi-strategy.log） |

### 1.3 必须全局唯一或只能有一个的配置

| 服务 | 配置项 | 要求 | 出错后果 |
| --- | --- | --- | --- |
| vi-as | `serverId` | 每实例不同 | 话单 / 会话 ID 冲突（待确认具体表现） |
| vi-merge / offline-merge / vi-media | `serverId` / `appId`（`{{id}}`） | 每实例不同 | ETCD 注册节点互相覆盖 |
| qa-server | 环境变量 `QA_SERVER_ID` | 每实例不同，默认 0 | task_id 雪花 ID 冲突 |
| scheduler | `service.instance.id` | 0–31，每实例不同 | Snowflake 冲突；流程热更新队列 `scheduler.process.update.<id>` 冲突 |
| scheduler | `masterServer=true` | **有且只有一个实例** | 没有 master：主任务永不回调、超时兜底不运行；多个 master：主任务回调去重失效 |
| scheduler | `xml.path` | 多实例共享同一存储（推测为 `sources/`，待确认） | 上传的流程 XML 只落在一台机器，其它实例加载旧版本或 NPE |

## 2. 启动依赖顺序

源码里没有编排脚本。下表按“先下游、后上游；消费者先于生产者”的调用关系推导，顺序本身属于建议（待确认现网实际做法）。

| 顺序 | 组件 | 依据 / 启动前检查 |
| --- | --- | --- |
| 0 | 基础设施：ETCD、MySQL（vi_bill、user_qc、vi_strategy、vi_cdr 等）、Redis、RocketMQ、RabbitMQ、MongoDB、StarRocks/ClickHouse | vi-billcenter 任一初始化失败直接 `System.exit(1)`；vi-as 在 startMode=0 时依次初始化 DB → AuthConfigService → ETCD → RocketMQ |
| 1 | ETCD 配置 key 就绪 | vi-as 启动时 ETCD 缺 `/config/max_sessions` 会 NPE（`vi-assets/vi-as-master/src/main/java/com/cmic/sip/MyRegistryService.java`）；当日话单分表 `bill_YYYY_MM_DD` 已由存储过程建好 |
| 2 | 外部服务：ai-models、asr、vi-mdown、httpproxy、strategy-service、xproxy | scheduler 依赖模型队列；合音依赖 vi-mdown；pusher 依赖 httpproxy；vi-as 依赖 strategy-service（可降级，见应急） |
| 3 | scheduler（**先启 master 实例**） | RabbitMQ 队列 `scheduler.task.queue` 代码未声明，需预建（待确认）；StarRocks、华为质检参数缺失时启动失败 |
| 4 | qa-server | 依赖 scheduler 队列、MongoDB `scheduler.process`、Redis 白名单 `QASERVER:WHITEIP` / `QASERVER:WHITEURL` |
| 5 | vi-pusher | 必须先于 vi-billcenter，否则回调后的推送消息只能堆积在 RocketMQ |
| 6 | vi-billcenter | 依赖 qa-server（`qc-server-create-url`）；它自身的 `server.address` 会被拼进 `subTaskCallBackUrl`，地址必须能被 qa-server 直连，并且在 `QASERVER:WHITEURL` 中 |
| 7 | offline-merge、vi-merge | 依赖 vi-mdown 与 RocketMQ；vi-merge 工作目录必须有权限为 0755/0777 的 `ffmpeg` |
| 8 | vi-media | 依赖 vi-merge（实时）、RocketMQ（离线合音消息）、ip_local_reserved_ports |
| 9 | vi-as | 最后启动；通过 ETCD 发现 vi-media、xproxy、strategy-service。上线后先 `GET /ops/status` 确认 state=3，再放量 |
| 10 | vi-cront-jobs crontab | 依赖 vi_strategy 库、ClickHouse `mid_bussops`、Redis `VI_STRATEGY_REDIS`；可在任意时刻开启 |

## 3. 优雅停机

### 3.1 vi-as（`GracefulDownHandler`）

| 步骤 | 动作 | 说明 |
| --- | --- | --- |
| 1 | `curl -X POST http://<vi-as>:5287/ops/pause`（示意） | 返回 `{"res":"success"}`；停止响应 xproxy 的 OPTIONS 探测，xproxy 不再分配新呼叫 |
| 2 | `curl http://<vi-as>:5287/ops/status` | 返回 `{version, session, state}`，state=2 表示已下线；观察 session 下降 |
| 3 | `kill -2 <pid>`（SIGINT） | 处理器调用 `turnOnOff(false)`，每 5s 检查一次；session=0 时退出 |
| 4 | 等待 | 第 20 次检查（约 100s）时对所有剩余会话发 BYE；第 30 次（约 150s）强制退出 |
| 回滚 | `POST /ops/resume` | 恢复响应 OPTIONS（state=3） |

不要用 `kill -9`：在途通话直接中断，话单的 finish 更新和 MQ 发送都会丢失。

### 3.2 vi-media / vi-merge / offline-merge

| 服务 | 先下线 | 停止行为 | 注意 |
| --- | --- | --- | --- |
| vi-media | `/som/service/pause`（摘 ETCD，不再分配会话），`/som/service/status` 观察 | `stop.sh 9060` → SIGTERM → registry.Close → 等待所有会话结束 | IN_CALL 超时为 14400s，最长可能等 **4 小时**；stop.sh 会无限等待 |
| vi-merge | `/ops/omu/pause`（9034） | `stop.sh 9033` → SIGTERM | 停止时**不等待**在途会话，只逐个通知媒体，会丢失未完成的实时合音 |
| offline-merge | `/ops/mq/stop` 停止消费，再 `/ops/omu/pause` | `stop.sh 9036` → SIGTERM | 先停 MQ 消费，避免处理中的消息丢失 |

### 3.3 scheduler / qa-server / vi-cront-jobs

- scheduler：`bin.prod/stop.sh` 用 grep `$DEPLOY_DIR/config` 找进程，可能找不到（见 §4）。`masterServer=true` 全局只能有一个。先停旧 master、确认进程退出，再启动已改成 `masterServer=true` 的备用实例。两台同时为 true 会使主任务去重失效。步骤见 `07-operation/runbooks/scheduler-master-failover.md`。
- qa-server：停容器会同时停掉 master 中的 MQ 消费线程和定时任务；flush 与 retry 有 Redis 锁，多实例时可以逐台滚动。
- vi-cront-jobs：注释掉 crontab 行即可；正在执行的脚本会跑完。

### 3.4 vi-billcenter / vi-pusher

没有停机钩子和脚本。所有 Consumer 都无条件 ACK，停止进程时，已拉取但未处理完的消息会丢失。建议先摘 SLB（vi-billcenter 是 qa-server 建任务回调的直连地址，摘除前确认没有在途回调），再 kill（不要用 -9）。

## 4. 常见部署坑

| # | 坑 | 位置 | 规避 |
| --- | --- | --- | --- |
| 1 | vi-as dev 配置指向不存在的 yaml，没有 rocketmq.xml，只能以 startMode=1 运行 | `vi-assets/vi-as-master/config/application-config.xml` | 联调使用 startMode=1；生产只用 `config.prod/` |
| 2 | vi-as 的 `mssSipStackPath` 是无效 key | `config.prod/application-config.xml` | 不要依赖它来切换 SIP 栈配置 |
| 3 | vi-as `sip-stack-call.properties` THREAD_POOL_SIZE：prod 128，dev 16 | `config*/sip-stack-call.properties` | 压测时使用 prod 值 |
| 4 | vi-as `RocketMQConfig` 把 ak/sk 打印到 stdout | `vi-assets/vi-as-master/src/main/java/.../RocketMQConfig` | start.sh 前台运行时，stdout 不要重定向到共享日志 |
| 5 | vi-billcenter dev profile 的 mainClass `com.cmic.BillCenterApplication` 不存在 | `vi-assets/vi-billcenter-master/pom.xml` | 只用 prod profile 打包 |
| 6 | vi-billcenter / vi-pusher 的 `config/` 只有 log4j2，缺少 application.yml 时启动失败 | 仓库 `config/` | 发布时渲染 `config.prod/application.yml` 并放到运行目录（待确认放置位置） |
| 7 | vi-pusher 的 test profile 是默认 profile，会执行 wagon SFTP 上传和重启 | `vi-assets/vi-pusher-master/pom.xml` | 打包时显式指定 `-P prod` |
| 8 | vi-pusher 的 `second-reach-*` 配置项必填 | `config.prod/application.yml` | 缺失时启动失败 |
| 9 | qa-server `FLASK_ENV` 未设置时加载不存在的 `config.local/` | `vi-assets/qa-server-master/config.py` | 容器必须设置 `FLASK_ENV=production` |
| 10 | qa-server 未设置 `QA_SERVER_ID` 时默认 0，多实例 ID 冲突 | `utils/snowFlake.py:get_next_id` | 每实例设置不同值 |
| 11 | scheduler `bin.test` 脚本 shebang 写成 `#!/bin.test/bash`；`restart.sh` 调 `./stop.sh ./start.sh` 依赖当前目录；dev 包没有 bin | `vi-assets/scheduler-master/bin.test/` | 用 `bash stop.sh` 显式执行；在 bin 目录下执行 restart |
| 12 | scheduler `stop.sh` 用 `$DEPLOY_DIR/config` 匹配进程，而启动参数是 `-classpath ./config`（相对路径），可能 grep 不到 | `bin.prod/stop.sh` | 停止后用 `ps` 确认进程已退出 |
| 13 | scheduler 流程禁用（status=0）在重启前不生效 | `ProcessService.findEnableByProcessId` | 禁用流程后需要滚动重启 |
| 14 | vi-media 未设置 ip_local_reserved_ports 时启动失败或与系统临时端口冲突 | vi-media 启动检查 | 主机初始化时保留 10000–30000 |
| 15 | vi-media 录音清理：凌晨 04:00 删除 7 天前的目录，03:00 重写索引；内存 `/tmp/media/call` 保留 10 分钟后转存磁盘 `/data/media/call` | vi-media cron | 磁盘规划按 `diskLifeSpan` 和 7 天量估算 |
| 16 | vi-merge 要求 `./ffmpeg` 权限**恰好**是 0755 或 0777（0775、0750 都会 panic）；`ffmpeg.staticPath` 不生效 | `internal/util/runtime_permissions.go:CanExecute` | 用 start.sh 启动（会 chmod 755） |
| 17 | offline-merge start.sh 不做 chmod；ffmpeg 查找顺序为 `./ffmpeg` → `ffmpeg.staticPath` → PATH | `internal/pkg/ff/runsh.go` | 部署后检查 ffmpeg 可执行 |
| 18 | offline-merge 临时目录 `/tmp/mergeCall_<j>`、`/data/offline`，以及上传失败落盘目录 `/data/offlocal/`，都没有清理 | offline-merge | 配置主机级清理任务，并监控磁盘 |
| 19 | vi-cront-jobs README crontab 写的是 `CRONT_ENV=test`，路径却在 `/home/production/` | `vi-assets/vi-cront-jobs-master/README.md` | 生产 crontab 不要照抄，使用 `production` 或不设置 |
| 20 | vi-cront-jobs 缺少 `apps.email` 模块，ActivePhone、DailyStatistics 会 ImportError | `viStrategyActivePhone.py` | 确认线上是否单独部署该模块（待确认） |
| 21 | vi-cront-jobs 没有 flock，单次执行超过 1 分钟时与下一次重叠 | crontab | 建议 `flock -n <锁文件>` 包一层（变更需评审） |
| 22 | 调试接口暴露：vi-billcenter `/vibillcenter/test` 会用固定企业真实调用 qa-server；offline-merge `/v1/test` | 各 Controller | 在网关 / SLB 层屏蔽 |

CONFLICT: 《话务管控上线依赖》 vs `vi-assets/vi-cront-jobs-master/README.md`: 上线依赖文档的 venv 为 `/data/venv/cront-jobs11` 且只列 8 个脚本，README crontab 使用 `/home/production/vi-cront-jobs/myenv` 与 `CRONT_ENV=test`，列 16 个脚本。

CONFLICT: vi-media 注册 cluster `vii` vs vi-as `etcdNodeName=vi`: vi-as 发现 `com.cmic.vi.media` 是否跨集群前缀可达，待确认。

## 5. 话务管控（vi-cront-jobs）上线步骤

依据《话务管控上线依赖》：

1. 在质检 MySQL 建库 `vi_strategy` 和策略表（DDL 见设计文档《话务管控策略表DDL》）。【生产变更】DDL 需走变更流程，回滚方案是 DROP 新建的空表。
2. 确认 ClickHouse `mid_bussops.call_control_detail`、`call_control_statistics` 已存在（上线依赖文档未列出，待确认）。
3. 安装 Python 3.11+，执行 `python3 -m venv /data/venv/cront-jobs11`，再 `pip install -r requirements.txt`。
4. 渲染 `config/config.yaml`（MySQL / ClickHouse / Redis `VI_STRATEGY_REDIS`）。
5. 配置 crontab，调度表见 `07-operation/monitoring.md` 第 4 节。
6. 验证：`/var/log/vi-cront-jobs/errors.log` 没有新增错误，Redis 中出现预期前缀的 key（见 `07-operation/runbooks/traffic-control-misjudge.md`）。
