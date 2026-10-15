---
id: runbook-scheduler-master-failover
title: Runbook - scheduler 主节点切换
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - ops-monitoring
  - ops-emergency
  - ops-deployment
  - svc-scheduler
  - svc-qa-server
  - runbook-qc-result-delay
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/TaskDerepeatConsumer.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/TaskCallbackSchedulerService.java
---

# Runbook：scheduler 主节点切换

**全局只能有一个 `masterServer=true`。** 配置在部署注入的 `application.yml`（仓库中没有这份文件）。代码里没有 HTTP 接口报告自己是不是 master。

只有这一台会：

- 消费 `scheduler.task.repeat.queue`（`TaskDerepeatConsumer`），用进程内 1000 条 LRU 按 taskId 去重，再转发到 `scheduler.task.lb-callback.queue`。
- 每小时执行 `TaskCallbackSchedulerService.pollTasks`：打 `task_timeout_*` 指标，并对 3 天窗口内、创建超过 24 小时（priority>2）或 48 小时仍未回调的任务做兜底。

没有 master：主任务回调永远不发出，超时兜底不运行，repeat 队列无人消费。

两个及以上 master：LRU 不能跨进程去重，主任务回调会发多次。重启后 LRU 清空，已去重过的 taskId 也可能再回调一次。

子任务回调（type=1）由处理该子任务的任意实例直接发到 `scheduler.callback.exchange`，不经过 master。质检结果主体仍可回调。vi-billcenter 的主任务回调接口只打日志。

`service.instance.id` 必须在 0–31 且每个实例不同，和 master 标记是两项独立约束。新 master 不能沿用旧实例的 id，否则流程热更新队列 `scheduler.process.update.<id>` 冲突。

## 现象

- RabbitMQ 上 `scheduler.task.repeat.queue` 的消费者数量不是 1（管理界面入口待确认）。
- 主任务完成率迟迟不到 qa-server，子任务结果仍在更新。
- `task_timeout_10_minutes_count` 等指标超过一小时没有任何新增量，同时仍有创建很久的未回调任务。
- 客户或质检侧看到同一任务的主回调出现两次。

## 影响

主任务汇总和 24/48 小时兜底暂停或重复。超过 3 天仍未回调的任务，即使 master 恢复也不会被这轮扫描捞起。切换窗口内主任务回调暂停，这是只保留一个 master 的代价。

## 止血（先做）

1. 只读清点。在每一台 scheduler 的部署配置里看 `masterServer`（文件路径待确认，不要猜主机名）。在 RabbitMQ 上看 `scheduler.task.repeat.queue` 的消费者个数：

| 消费者数 | 含义 |
| --- | --- |
| 0 | 当前没有 master 在跑 |
| 1 | 与「全局只有一个 true」一致，再核对配置确实只有一份 true |
| 大于 1 | 有多个 master 在消费，去重已经失效 |

同时用 `ps` 看 `scheduler-jar-with-dependencies.jar` 的进程数。`bin.prod/stop.sh` 用 `$DEPLOY_DIR/config` 匹配进程，启动参数是相对路径 `./config`，可能 grep 不到。停完必须再用 `ps` 确认。

2. 从 0 恢复成 1 个，或从多个收成 1 个。顺序固定，避免出现第二个 true。【生产变更】

   1. 选定唯一的备用实例。把它的 `masterServer` 改成 `true`，`service.instance.id` 保持该实例自己的值。先不要启动。
   2. 停掉正在跑的旧 master，以及其它任何 `masterServer=true` 的进程：`bash bin.prod/stop.sh`，然后 `ps` 确认没有残留 Java 进程。
   3. 再确认配置文件里只剩即将启动的这一份是 `true`。
   4. 只启动这一台。

   回滚：停掉新进程；把原 master 的配置改回 `true`（备用改回 `false`）；`ps` 确认没有第二个进程；再启动原来的那一台。

3. 不要为了缩短空窗而先启动新 master 再停旧的。那一段时间内全局有两个 `masterServer=true`。

4. 切换完成后不要立刻再重启这台 master，否则 LRU 清空，短时间内可能对同一 taskId 再发一次主回调。qa-server 对主回调失败会进 `repush_queue`。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 配置份数 | 各实例 `application.yml` 的 `masterServer` | 恰好一个 true | 改配置后未重启的进程仍按启动时的值运行，以进程启动配置为准 |
| 2 | repeat 队列 | `scheduler.task.repeat.queue` 消费者数 | 1 | 0：主回调停止。大于 1：重复回调 |
| 3 | 转发队列 | `scheduler.task.lb-callback.queue` | 有消费者（代码并发 2），ready 不持续堆积 | master 不转发时这个队列不会变多，积压在 repeat |
| 4 | 日志 | `/var/log/scheduler/all.log` 按类名 `TaskDerepeatConsumer`、`TaskCallbackSchedulerService` 检索 | 只有一台有这些类的工作日志 | 两台同时出现：仍然是多 master |
| 5 | 超时指标 | `task_timeout_10_minutes_count` 及 6/12/24 小时 | master 起来后下一小时有扫描 | 指标是 Counter，每小时把存量再加一次，不能当成「当前超时任务数」 |
| 6 | 实例 id | `service.instance.id` 与队列 `scheduler.process.update.<id>` | 0–31 且互不相同 | 冲突时流程热更新会串到别的实例 |
| 7 | 子任务是否还在走 | qa-server `uniops_finished_sub_task_counter`、`from_scheduler_queue` | 子任务回调仍在增加 | 子任务也停了就不是 master 问题，转 `07-operation/runbooks/qc-result-delay.md` |

`process.timeout`（1–60 分钟）没有被定时任务读取。实时任务也按 24/48 小时兜底，注释里的「1 小时超时回调」没有实现。

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

只有一个 scheduler 进程的配置是 `masterServer=true`，并且 `ps` 里没有第二个带着该配置的进程。`scheduler.task.repeat.queue` 消费者数为 1，ready 开始下降。下一小时 `task_timeout_*` 有新的增量或日志里出现 `pollTasks` 所在类。空窗期内积压的主回调会在新 master 上补发，补发造成的重复以 qa-server 侧幂等为准（主回调在 vi-billcenter 只记日志）。
