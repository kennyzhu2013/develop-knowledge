---
id: runbook-qc-result-delay
title: Runbook - 质检结果延迟
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - ops-monitoring
  - ops-emergency
  - qc-pipeline
  - svc-vi-billcenter
  - svc-qa-server
  - svc-scheduler
  - runbook-record-merge-backlog
  - runbook-bill-backlog
  - runbook-push-failure
  - runbook-scheduler-master-failover
source_of_truth:
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java
  - vi-assets/qa-server-master/apps/service.py
  - vi-assets/qa-server-master/apps/result_consumer.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/TaskCallbackSchedulerService.java
---

# Runbook：质检结果延迟

链路：合音结果到 vi-billcenter → HTTP 创建 qa-server 任务 → RabbitMQ 到 scheduler → 模型队列 → 回调 qa-server → HTTP 回调 vi-billcenter `/vibillcenter/subtask/callback` → 再交给 vi-pusher。推送见 `07-operation/runbooks/push-failure.md`。流水线背景见 `02-call-qc/pipeline.md`。

呼叫接续不经过 qa-server。源码里没有「切备用 ASR」或「只跑规则」的开关。

## 现象

- 下游说结果晚到。`bill_new.quality_inspection_status` 停在 0 或 1，或长时间为 -1、-2。
- qa-server `uniops_pending_async_tasks` 升高，或 `uniops_mq_consumer_alive` 不为 1。
- `scheduler.task.queue`、模型响应队列或 `repush_queue` 的 ready 数上升。
- scheduler 的 `task_timeout_10_minutes_count` 每小时仍在累加（这是 Counter，不是当前积压值）。

## 影响

质检结果晚到，涉诈和风险处置变慢。专线失败重试最多 5 次、每 10 分钟扫一次，首次失败不回调，这段等待是代码行为。scheduler 的流程超时字段（1–60 分钟）不生效，失败子任务要等 24 小时或 48 小时兜底。

## 止血（先做）

1. 只读看卡在哪一格。`vi_bill`：

```sql
SELECT quality_inspection_status, COUNT(*) AS cnt
FROM bill_new
GROUP BY quality_inspection_status;
```

| 状态 | 含义 | 下一步 |
| --- | --- | --- |
| 0 | 未发起 | 接通则等合音，见 `07-operation/runbooks/record-merge-backlog.md` |
| 1 | qa-server 已接受创建 | 查 qa-server 与 scheduler |
| 2 | 子任务回调已成功 | 若客户没收到，转推送 Runbook |
| -1 | 创建质检失败 | 第 2 步 |
| -2 | 质检处理失败 | 查 qa-server `errors.log` 与模型队列 |
| -3 | 推送失败或仍在重试 | 推送 Runbook。重试期间也会写成 -3 |

2. 状态 -1 的补偿是【生产变更】。必须带时间，默认窗口被写死为 2026-07-02 至 2026-07-05，不传时间会扫到那段历史。同一时刻只能有一个任务，已在跑时返回 `{"result":"fail"}`。

```text
curl -sS -X POST "http://<vi-billcenter>:9277/vibillcenter/task/recreate" \
  -H "Content-Type: application/json" \
  -d '{"startTime":"<yyyy-MM-dd HH:mm:ss>","endTime":"<yyyy-MM-dd HH:mm:ss>"}'
```

接口无鉴权，不要调用 `/vibillcenter/test`。回滚：这个接口没有停止接口；`@PreDestroy` 会在进程退出时打断线程池。已经改成状态 1 的行不会自动改回。停止方式是停 vi-billcenter 进程（会同时丢掉正在 ACK 的其它消息），因此只在确认窗口内的重发必须停掉时使用。

3. qa-server 的消费和定时任务在 Gunicorn master。`uniops_mq_consumer_alive` 为 1 时不要为了「刷新」重启容器。重启会停掉结果消费、flush 和失败重试。worker 可以按部署手册滚动，master 最后动。

4. 模型队列堆积时，处理 ASR / 模型适配器一侧的消费。scheduler 对异常响应 `basicNack(requeue=false)`，消息离开队列后只能等超时兜底，不能靠把队列「消费空」来恢复。没有代码开关可以跳过 ASR。

5. `scheduler.task.repeat.queue` 没有消费者时，主任务回调停。子任务回调（type=1，质检结果主体）不经过 master。主节点处理见 `07-operation/runbooks/scheduler-master-failover.md`。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 合音是否到达 | `GID_VI_ONE_BILLCENTER_RECORD`；`recover.log` 搜 `record_repush_fail` | 状态离开 0 | 60 秒×3 次后只写日志。topic 名不一致见合音 Runbook 的 CONFLICT |
| 2 | 创建质检 | 19277 上的 `vi.inspection.success.total` / `vi.inspection.failure.total` | failure 不持续增加 | 状态 -1。HTTP 超时：连接 2 秒、响应 8 秒，无自动重试 |
| 3 | qa-server 异步 | `uniops_pending_async_tasks`、`/var/log/qa-server/errors.log` | 指标回落 | 接口先返回 `0000`。后续失败只把任务 status 置为 2，调用方无感知 |
| 4 | 任务队列 | RabbitMQ 上 `scheduler.task.queue`（管理界面待确认） | ready 不持续上升 | 队列代码未声明，需预建。qa-server 发到 `to_scheduler_exchange` / `to_scheduler_routing_key`，绑定是否就是这个队列名待确认 |
| 5 | 模型 | `model_send_count` 与 `model_receive_count`、`model_receive_timeout_count`；响应队列 `scheduler.asr.response.queue` 等 | 收发差距不持续拉大 | 耗时直方图 `model_receive_cost_histogram` 的数值不可用。进程 `process.timeout` 不生效 |
| 6 | 回调进入 qa-server | 配置项 `from_scheduler_queue`（topics.yaml 登记 `scheduler.callback.queue`，是否同名待确认）；`uniops_mq_consumer_alive` | 消费线程为 1 | 处理异常也会 ack，只能查 MongoDB `service.process_fail` |
| 7 | 失败重试 | 每 10 分钟的 `retry_qa_fail`；`/var/log/qa-server/result.log` | 重试中的明细 status=3 | 超过 5 次才回调。`retry_status=1` 且 scheduler 不回结果时，记录不再被扫描（悬挂，待确认清理方式） |
| 8 | HTTP 回调 | `uniops_callback_error_counter`、`repush_queue` | 重推次数下降 | 成功标准是 HTTP 200 且 `code` 为 `0000`。延迟大约 3 分钟、5 分钟、10 分钟。最后失败写入 MongoDB `service.push_fail` |
| 9 | 入库滞后 | `flush.log`；Redis 游标 `QASUBRESULT:latest` 等 | 游标停在大约 2 分钟前（任务只扫到该处） | 游标更旧，或失败后 list 仍在：明细入库滞后，不是客户回调。游标丢失时更早的桶不会再入库 |

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

新话单的 `quality_inspection_status` 能从 0/1 走到 2（若客户配置了推送地址，最终到 3 还要看推送 Runbook）。`uniops_pending_async_tasks` 回落，`scheduler.task.queue` 的 ready 不再持续上升。`task_timeout_*` 下一小时的增量不再代表同一批任务被重复累加（该 Counter 每小时把存量再加一次，恢复判断看增量是否只来自新超时，待确认观察窗口）。
