---
id: ops-monitoring
title: 监控与告警
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - ops-deployment
  - ops-emergency
  - arch-middleware
  - runbook-sip-call-failure
  - runbook-record-merge-backlog
  - runbook-bill-backlog
  - runbook-qc-result-delay
  - runbook-push-failure
  - runbook-scheduler-master-failover
  - runbook-traffic-control-misjudge
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/PromExporterService.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/XmodePromExporterService.java
  - vi-assets/vi-media-master/internal/app.go
  - vi-assets/vi-merge-master/internal/metrics/metrics.go
  - vi-assets/offline-merge-master/internal/metrics/metrics.go
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/metrics/BillMetrics.java
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/metrics/PusherMetrics.java
  - vi-assets/qa-server-master/apps/prometheus.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/metrics/MetricsBean.java
  - vi-assets/vi-cront-jobs-master/README.md
---

# 监控与告警

源码和 `config.prod` 里没有告警规则名，也没有看板名称。下表是按现象整理的处置入口，级别按业务影响估算，不是监控平台里的规则。数值阈值除代码里写死的以外都标「待确认」。Topic 以 `06-data/mq/topics.yaml` 为准。部署端口见 `07-operation/deployment.md`。

抓取地址不在仓库中。在实例上只读打开下面写的 HTTP 路径即可，例如 `curl -sS "http://<实例>:5287/metrics"`。日志里可能有号码和转写文本，引用时打码。

## 1. 现象处置入口

| 现象 | 含义 | 级别 | Runbook |
| --- | --- | --- | --- |
| SIP 接入失败 | vi-as UDP 5080 不响应，或新 INVITE 大面积失败 | P0 | `07-operation/runbooks/sip-call-failure.md` |
| 录音合音积压 | 离线合音消费组或实时合音上传堆积，录音出不来 | P1 | `07-operation/runbooks/record-merge-backlog.md` |
| 话单积压 | vi-as 话单消息或 `bill_new` 在途行持续增加 | P1 | `07-operation/runbooks/bill-backlog.md` |
| 质检结果延迟 | 合音已到，质检结果迟迟不回调 | P1 | `07-operation/runbooks/qc-result-delay.md` |
| 推送失败 | 质检结果到了 vi-pusher，客户侧未成功接收 | P2 | `07-operation/runbooks/push-failure.md` |
| scheduler 主节点异常 | 没有或有多个 `masterServer=true` | P1 | `07-operation/runbooks/scheduler-master-failover.md` |
| 话务管控误判 | 该拦未拦，或正常呼叫被拦截 | P1（大面积）/ P2（个别号码） | `07-operation/runbooks/traffic-control-misjudge.md` |

总顺序见 `07-operation/emergency/overview.md`。

## 2. 各服务观测点

### 2.1 vi-as

| 项 | 内容 |
| --- | --- |
| 端口 | SIP UDP 5080；HTTP prod 5287（dev 5668）：`/metrics`、`/ops/status`；JMX exporter 8588 `/metrics`（OS 指标） |
| 业务指标 | `as_invite_arrive_total`、`as_invite_retran_total`、`as_controller_alive_num`、`as_left_leg_num`、`as_right_leg_num`、`as_in_call_num`、`as_bill_send_num`、`as_bill_finishstate`、`call_reject_due_to_too_much`、`sip_message_queue_size`、`rocket_queue_too_much`、`strategy_audit_queue_full`、`strategy_audit_response_queue_full`、`recording_call_retry1/2/3`、`as_http_error_count`、`xdtmf_db_fail_count`、`xdtmf_write_db_log_count`、`xdtmf_db_task_reject_count`、`no_support_code`、`process_timeout`。vod / light_screen / aidou 仍在注册，不作为本链路依据 |
| 日志 | `/var/log/vi-as/`：`all.log`、`signaling.log`（logger `aslog`）、`analyse.log`、`db.log`（logger `dblog`，入库失败 SQL）、`error.log`、`warn.log`、`info.log`、`netty.log` |
| 定时（报告 §10） | 每 10s 导出 SIP UDP 队列和存活 Controller 数；每 60s 清理超过 18 分钟的死会话；优雅下线检查每 5s |
| 配置（报告 §11） | `publishMQThreadPoolSize=16`，`publishMQThreadPoolQueueSize=10000`；`THREAD_POOL_SIZE` prod 128 |
| 积压 | `sip_message_queue_size` 持续上升表示 SIP 队列堆积。`rocket_queue_too_much` 在发布队列长度大于 200 时计数。话单发送失败不重试，看 `bill_fail` 与 `db.log`，不会变成消费组重投 |

`/ops/status` 返回 `{version, session, state}`，state=3 在线，state=2 下线。

### 2.2 vi-media

| 项 | 内容 |
| --- | --- |
| 端口 | 9060：`/metrics`、`/som/service/status`、pprof。RTP UDP 10000–30000 |
| 指标 | namespace `vi-media`：`session_active_size`、`audio_cache_size`、`rtp`（label `rType`）、`zone_layer_download` |
| 日志 | `/var/log/vi-media/all.log` |
| 定时（§10） | 会话扫描每 3 分钟（START/ACTIVE 超时 60s/90s，IN_CALL 14400s）；缓存过期扫描每 10s；索引重写 cron `0 0 3 * * ?`；目录清理 cron `0 0 4 * * ?`（只删 7 天前那一天） |
| 配置（§11） | 会话槽位 `(ims.end-ims.start)/4`，模板 10000/30000 即 5000。`monitor.cpu.enable` 默认 false |
| 积压 | `session_active_size` 接近 5000 时 create 返回 503。实时推流成功时不发合音 MQ。缓存与目录清理失败会占满 `/tmp/media/call`、`/data/media/call` |

### 2.3 vi-merge

| 项 | 内容 |
| --- | --- |
| 端口 | 9033 WebSocket `/v1/merge`；9034 `/metrics`、`/ops/omu/status`、pprof |
| 指标 | namespace `vimerge`：`stream_merge_active_session`、`stream_merge_fragment_count`、`stream_merge_upload_fail_count`、`stream_merge_bill_fail_count`、`stream_merge_ws_fail_count`、`stream_pcmmix_chanfull_count`、`stream_node_notfound_count`、`stream_save_disk_count`、`stream_buffer2file_count`、`stream_merge_upload_time` |
| 日志 | `/var/log/vi-merge/all.log`（含明文号码，引用时打码） |
| 定时（§10） | 无业务定时任务。ETCD 续约间隔 15s、租约 30s |
| 配置（§11） | `maxSession` 2000；上传超时 `outbound.timeout` 10s；合音通道容量 250 |
| 积压 | `stream_merge_active_session` 接近 2000 时新 WS 返回 503。`stream_pcmmix_chanfull_count` 增加表示音频块被丢弃。`stream_node_notfound_count` 表示找不到 vi-mdown |

### 2.4 offline-merge

| 项 | 内容 |
| --- | --- |
| 端口 | 9036 业务；9037 `/metrics`、`/health`、`/ops/omu/status`、`/ops/mq/stop`、`/ops/mq/start`。`/v1/test` 与 `/testmetrics` 会改生产数据或污染指标，排障不要调用 |
| 指标 | namespace `offlinemerge`：`mq_receive_count`、`mq_message_invalid_count`、`merge_fail_count`、`merge_down_fail_count`、`merge_upload_fail_count`、`merge_bill_fail_count`、`merge_ffrun_fail_count`、`merge_file_local_save_count`、`merge_fallback_count`、`merge_starttime_wrong_count` |
| 日志 | `/var/log/offline-merge/all.log` |
| 定时（§10） | 无扫描、无补传。`/data/offlocal/` 只增不减 |
| 配置（§11） | `consumptionThreadCount` 20；消费缓存 `MaxCacheMessageCount=1024`；`uploadFailSaveLocal=true` |
| 积压 | 看消费组 `GID_VI_MERGE_RECORD` 对 `TID_VI_MEDIA_RECORD_MERGE_NORMAL` 的堆积（控制台，命令待确认）。`merge_file_local_save_count` 增加表示上传失败落在本机。JSON 解析失败会直接 ack，堆积不增加但录音丢失 |

### 2.5 vi-billcenter

| 项 | 内容 |
| --- | --- |
| 端口 | 9277 `/vibillcenter/*`；19277 `/manage/info/prometheus` |
| 指标 | 代码登记名：`vi.bill.request.total`（只计未接通）、`vi.record.error.total`、`vi.get.connection.timeout.total`、`vi.inspection.create.total`、`vi.inspection.success.total`、`vi.inspection.failure.total`、`vi.inspection.callback.success.total`、`vi.inspection.callback.failure.total`、`vi.inspection.result.send.total`。抓取页上的名字以该端点实际输出为准（点号是否变成下划线待确认） |
| 日志 | `/var/log/vi-billcenter/`：`all.log`、`warn.log`、`error.log`、`recover.log`（关键字 `record_repush_fail`） |
| 定时（§10） | 无生效定时任务。接通后等合音、合音失败都不自动清理 |
| 配置（§11） | `billConsumerCount` 50、`recordConsumerCount` 40、`delayRecordConsumerCount` 30；Druid `max-active=50`、`max-wait=1000` |
| 积压 | 话单消费组 `GID_VI_ONE_BILLCENTER_BILL`、合音消费组 `GID_VI_ONE_BILLCENTER_RECORD`、延时组 `GID_VI_ONE_BILLCENTER_BILL_PROCESS` 与 `GID_VI_ONE_BILLCENTER_RECORD_DELAY`。Consumer 无条件 ACK，堆积表示处理慢或进程不在，不表示失败重投。在途行看 `vi_bill.bill_new` |

### 2.6 vi-pusher

| 项 | 内容 |
| --- | --- |
| 端口 | 9283；19283 `/manage/info/prometheus` |
| 指标 | `vi.pusher.inspection.push.success.count`（含「未配置推送地址、直接当成功」）、`vi.pusher.inspection.push.error.count`（只在重试耗尽时加）、`vi.pusher.db.connection.timeout.count`。`bill.push.*` / `record.push.*` 属于已停用的话单/录音推送 |
| 日志 | `/var/log/vi-pusher/all.log`、`warn.log`、`error.log`。INFO 会打印完整推送体，引用时打码 |
| 定时（§10） | 无 |
| 配置（§11） | `pusher.inspectionpusher=true` 才消费主 topic；`inspectionConsumerCount` 40、`delayRecordConsumerCount` 30。延时消费不受该开关控制 |
| 积压 | 主组 `GID_VI_ONE_PUSHER_INSPECTION_PUSH`、延时组 `GID_VI_ONE_PUSHER_INSPECTION_DELAY`（固定 60s，共 4 次推送）。中间失败不计 error 指标 |

### 2.7 qa-server

| 项 | 内容 |
| --- | --- |
| 端口 | `GUNICORN_BIND`（README 示例 7220）处理 HTTP；`PROMETHEUS_PORT`（README 示例 7225）导出指标。MQ 消费和 APScheduler 在 Gunicorn master，不在 worker |
| 指标 | namespace `uniops`，导出名 `uniops_<name>`：`pending_async_tasks`、`mq_consumer_alive`、`mq_consume_reconnect_counter`、`created_main_task_counter`、`finished_sub_task_counter`、`callback_error_counter`、`repush_result_counter`、`result_write_error_counter`、`sub_task_model_latency`、`push_result_latency`、`etcd_watch_alive`、`etcd_watch_error_counter` |
| 日志 | `/var/log/qa-server/`：`info.log`、`errors.log`、`all.log`、`realtime.log`、`result.log`、`flush.log`，另加 gunicorn error/access |
| 定时（§10） | 每分钟 `flush_data_to_db`（mid/dcc/vi 三路，max_instances=1）；每 10 分钟 `retry_qa_fail`；worker 每 1s 看缓存版本文件；ETCD watch 停滞检测 720s |
| 配置（§11） | `RESULT_CONSUMERS`、`REALTIME_CONSUMERS`、`REPUSH_CONSUMERS`、`ASYNC_WORKER_THREAD_COUNT`、`prefetch_count`。队列名来自 `from_scheduler_queue`、`repush_queue`、`from_xpusher_queue` |
| 积压 | `uniops_pending_async_tasks` 升高：建任务接口已返回成功，落库和发 MQ 还在线程池里。`uniops_mq_consumer_alive` 不为 1：对应消费线程不在跑。flush 只扫到大约 2 分钟前；Redis 游标 `QASUBRESULT:latest`、`DCCQASUBRESULT:latest`、`VIQASUBRESULT:latest` 比这个更旧，或本轮失败后 list 还在，才是入库滞后。游标 key 被删后，更早的分钟桶不会再入库且无 TTL |

### 2.8 scheduler

| 项 | 内容 |
| --- | --- |
| 端口 | `server.port` 待确认。actuator prometheus 的暴露路径由部署配置决定，待确认 |
| 指标 | `task_create_count`、`subtask_callback_count`、`subtask_callback_result_count`、`job_send_error`、`send_mq_exchange_error`、`model_send_count`、`model_receive_count`、`model_receive_status_count`、`model_receive_timeout_count`、`model_receive_cost_histogram`（计算式有误，数值不能当耗时）、`task_timeout_10_minutes_count`、`task_timeout_6_hours_count`、`task_timeout_12_hours_count`、`task_timeout_24_hours_count`、`mongodb_error_count`、`redis_error_count` |
| 日志 | `/var/log/scheduler/all.log`、`warn.log`、`error.log`。MDC `traceId` 是任务 `transactionId` |
| 定时（§10） | 仅 `masterServer=true` 的实例每小时跑 `pollTasks`：先打超时指标，再对创建超过 24h（priority>2）或 48h、且仍在 3 天窗口内的未回调任务发兜底。`process.timeout`（1–60 分钟）没有代码使用 |
| 配置（§11） | `masterServer` 全局只能有一个 true；`service.instance.id` 0–31 且各实例不同；`spring.rabbitmq.concurrency` / `max-concurrency` / `prefetchCount` 待确认现网值 |
| 积压 | `task_timeout_*` 是 Counter，每小时把当前存量再加一次，看增量，不能当积压 Gauge。`scheduler.task.repeat.queue` 只有 master 在消费，消费者数应为 1。模型响应慢看 `scheduler.<model>.queue` 与对应 `response.queue`（清单在报告 §7，本文不另造队列名）。异常消息 `basicNack(requeue=false)`，队列变空不等于处理成功 |

### 2.9 vi-cront-jobs

| 项 | 内容 |
| --- | --- |
| 端口 | 无 |
| 指标 | 配置项 `UNIOPS_MONITOR` 存在，代码未注册 Prometheus 指标名。不编造告警名 |
| 日志 | `/var/log/vi-cront-jobs/info.log`、`errors.log`、`info-vi-strategy.log` |
| 定时（§10） | 见第 4 节。无 flock，单次超过 1 分钟会与下一次重叠 |
| 配置（§11） | `VI_STRATEGY_REDIS`、`CRONT_ENV`。生产使用 `production` 或不设置；README 示例是 `CRONT_ENV=test`，不要照抄 |
| 积压 | 没有消息队列。重叠的 cron 会重复写明细。Redis key 被当天更新过的策略每分钟覆盖并重置 TTL，表现为管控时间被拉长，不是消费滞后 |

## 3. MQ 积压对照

控制台或管理界面的入口、CLI 均待确认。下表只列代码和 `06-data/mq/topics.yaml` 里的名字。

### 3.1 RocketMQ

vi-billcenter、vi-pusher 的消费者无条件返回 SUCCESS。offline-merge 只有「向外发送账单消息失败」才让本条消息失败重投，重投会把下载和 ffmpeg 再做一遍。

| Topic | 消费组 | 用来判断 |
| --- | --- | --- |
| `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL` | `GID_VI_ONE_BILLCENTER_BILL` | 话单消息是否还在被 vi-billcenter 消化 |
| `TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY` | `GID_VI_ONE_BILLCENTER_BILL_PROCESS` | 未接通且 `finish_state=0` 的 5s 延时移表 |
| `TID_VI_MEDIA_RECORD_MERGE_NORMAL` | `GID_VI_MERGE_RECORD` | offline-merge 离线合音 |
| `TID_VI_ONE_MERGE_MSG_BILL_NORMAL` | `GID_VI_ONE_BILLCENTER_RECORD` | vi-billcenter 等待的合音结果 |
| `TID_VI_ONE_BILLCENTER_RECORD_PUSH_BILLCENTER_DELAY` | `GID_VI_ONE_BILLCENTER_RECORD_DELAY` | 合音先到、话单未到时的 60s×3 重试 |
| `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL` | `GID_VI_ONE_PUSHER_INSPECTION_PUSH` | 质检结果推送。`pusher.inspectionpusher=true` 才消费 |
| `TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY` | `GID_VI_ONE_PUSHER_INSPECTION_DELAY` | 推送 60s 重试 |

`TID_VI_MERGE_MSG_BILL_NORMAL`（vi-merge 生产）和 `TID_VI_MERGE_BILL_RECORD_NORMAL`（offline-merge 生产）在 topics.yaml 里消费组为空。

CONFLICT: `vi-assets/vi-merge-master/config.prod/app.yaml` 与 `vi-assets/offline-merge-master/config.prod/app.yaml` vs `vi-assets/vi-billcenter-master` 的 RecordReceiver：生产侧 topic 是 `TID_VI_MERGE_MSG_BILL_NORMAL`、`TID_VI_MERGE_BILL_RECORD_NORMAL`，vi-billcenter 订阅的是 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`。堆积要两边都看，不能只看消费组。

`TID_VI_AS_EVENT_PUSH_PUSHER_NORMAL` 以及 topics.yaml 中 `status: inactive` 的遗留 topic 不作为积压对象。

### 3.2 RabbitMQ

| 代码中的队列 / 配置项 | 谁消费 | 用来判断 |
| --- | --- | --- |
| `scheduler.task.queue` | scheduler。代码未声明，需预建 | qa-server 已发出的质检任务。qa-server 发到配置项 `to_scheduler_exchange` / `to_scheduler_routing_key`，是否绑到这个队列名待确认 |
| `scheduler.callback.queue` | qa-server 配置项 `from_scheduler_queue` | scheduler 回调是否被 qa-server 取走。配置值是否仍是这个名字待确认 |
| `scheduler.task.repeat.queue` | 仅 `masterServer=true` | 主任务回调去重。消费者数应为 1 |
| `scheduler.task.lb-callback.queue` | 任一实例，并发 2 | master 转发后的主任务回调 |
| `repush_queue` | qa-server `RepushConsumeThread` | 回调 HTTP 失败后的延迟重推 |
| 配置项 `from_xpusher_queue` | qa-server 准实时话单消费 | 实际队列名待确认 |
| `scheduler.<model>.queue` 与 `scheduler.<model>.response.queue` | 模型适配器 / scheduler | 模型请求或回包堆积。具体名单以 scheduler 报告 §7 为准，本文不另造 |

`scheduler.task.callback.queue` 在 topics.yaml 中为 inactive。

## 4. vi-cront-jobs 调度表

来源是仓库 `README.md` 的 crontab。生产不要使用示例里的 `CRONT_ENV=test`。`ck_check.py`、`dataCheck.py`、`dailyRange.py` 不在表内，不要拿来当生产任务。

| 脚本 | cron | 作用 |
| --- | --- | --- |
| viStrategyHighFrequency.py | `* * * * *` | 短时高频，写 `highfreq-` |
| viStrategyLongCall.py | `* * * * *` | 长通话，写 `longcall-`（代码 `USE_MYSQL=True`） |
| viStrategyRemoteRange.py | `* * * * *` | 异地大范围，写 `difarea-` |
| viStrategyBusinessRange.py | `* * * * *` | 超业务范围，写 `bizrange-` |
| viStrategyBusinessThreshold.py | `* * * * *` | 企业外呼门限，写 `threshold-` |
| viStrategyDisabledRedisClear.py | `* * * * *` | 删除 status=0 策略残留的 key |
| viStrategyDifareaHighFreq.py | `* * * * *` | 外省/外市高频 |
| viStrategySilentCallBurst.py | `* * * * *` | 沉默号码突增，写 `inactive-` |
| viStrategyIdleHighFreq.py | `* * * * *` | 闲时高频 |
| viStrategyShortHighfreqBurst.py | `* * * * *` | 短时高频突增 |
| viStrategyShortLongcallBurst.py | `* * * * *` | 短时长通话突增 |
| viStrategySilentDifareaLongcall.py | `* * * * *` | 沉默号码外地长通话 |
| viStrategyOffwork.py | `*/10 * * * *` | 非工作时间，写 `offtime-` |
| viStrategyBillDetail.py | `5 * * * *` | 补录原因码 13、20 的明细 |
| viStrategyActivePhone.py | `0 1 * * *` | 刷新 `active_phone_tab` |
| viStrategyDailyStatistics.py | `0 1 * * *` | 昨日原因码汇总 |

ActivePhone 与 DailyStatistics 依赖仓库中不存在的 `apps.email`。线上是否另有该模块待确认。没有该模块时这两个脚本 ImportError，沉默类策略会用过期的活跃时间。
