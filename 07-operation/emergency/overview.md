---
id: ops-emergency
title: 应急预案总览
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - arch-ha
  - ops-monitoring
  - ops-deployment
  - runbook-sip-call-failure
  - runbook-record-merge-backlog
  - runbook-bill-backlog
  - runbook-qc-result-delay
  - runbook-push-failure
  - runbook-scheduler-master-failover
  - runbook-traffic-control-misjudge
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/SipEngine.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/BillService.java
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/TaskDerepeatConsumer.java
---

# 应急预案

先止血，再查根因。命令写在对应 Runbook 里，由人在故障实例上执行。本文不提供会改生产数据的脚本。值班决策人知识库未记录，待确认。

观测入口见 `07-operation/monitoring.md`，启停见 `07-operation/deployment.md`。

CONFLICT: `04-architecture/high-availability.md` vs 本文：高可用文档仍使用 mid-number-service、call-qc-service 等占位名。现行服务是 vi-as、vi-media、vi-merge、offline-merge、vi-billcenter、vi-pusher、qa-server、scheduler、vi-cront-jobs。源码里没有「只跑规则、跳过模型」或「临时抽检」开关。

## 通用顺序

1. 用只读接口和指标确认影响面（哪些实例、从什么时间开始、呼叫是否还通）。
2. 通报。决策人待确认。
3. 按下面该类型的顺序止血。涉及摘流、改 ETCD、改 `masterServer`、删 Redis key、补建质检任务时，按 Runbook 标注的【生产变更】执行，并先记下回滚动作。
4. 恢复后用 Runbook「恢复确认」里的信号看，不凭单一成功日志结单。
5. 复盘写入 `10-lessons/incidents/`，索引见 `10-lessons/overview.md`。

日志和话单可能含号码、转写。通报和复盘里打码。

## 1. SIP 接入失败

级别 P0。呼叫接不进来。详细命令见 `07-operation/runbooks/sip-call-failure.md`。

1. 只读看各 vi-as：UDP 5080 是否在听，`GET /ops/status`（prod 端口 5287）的 state 与 session，以及 `as_invite_arrive_total`、`sip_message_queue_size`。
2. 只有个别实例异常：对该实例摘流（`/ops/pause`），让 xproxy 不再把新呼叫分过来。其余实例保持 state=3。
3. `strategy_audit_queue_full` 升高或策略查询把呼叫打成拦截：按话务管控 Runbook 临时关掉策略查询（ETCD `/config/audit_strategy_allow`）。这会同时放通管控，需要人工决定。
4. 媒体创建失败但 SIP 仍在接续：呼叫可以继续，录音问题转入第 2 类。不要为了录音停掉 vi-as。
5. 全部实例都不响应 OPTIONS：按 `07-operation/deployment.md` 第 2 节从 ETCD、进程、UDP 5080 查起。恢复时先确认 state=3，再放量。

## 2. 录音合音积压

级别 P1。新呼叫仍可接通，质检拿不到录音。详细步骤见 `07-operation/runbooks/record-merge-backlog.md`。

1. 先分开两条链路：offline-merge 看 `GID_VI_MERGE_RECORD` 和 9037 `/metrics`；vi-merge 看 9034 的 `stream_merge_active_session` 与上传失败计数。
2. 磁盘满时先腾出系统临时空间。`/data/offlocal/` 是上传失败的兜底文件，确认补传方式之前不要删。
3. 消费进程不在时按部署手册拉起 offline-merge。积压期间不要先调 `/ops/mq/stop`，那是停机步骤。
4. 实时推流已经成功时，vi-media 按代码不发离线合音 MQ。这不是消费组故障，见 Runbook。
5. 合音消息到了但话单中心订阅的 topic 对不上：同时看第 3 类，不要只扩 offline-merge。

## 3. 话单积压

级别 P1。呼叫接续不依赖这条 MQ。详细步骤见 `07-operation/runbooks/bill-backlog.md`。

1. 分开「MQ 消费慢」和「接通话单在等合音」。后者 `isPickUP=true` 时 vi-billcenter 直接返回，行留在 `bill_new`，应走第 2 类。
2. 看 `GID_VI_ONE_BILLCENTER_BILL` 堆积和 `vi.get.connection.timeout.total`。进程在、只是慢时，不要 kill 进程：消费者无条件 ACK，已拉未处理的消息会丢。
3. vi-as 侧 `xdtmf_write_db_log_count`、`rocket_queue_too_much` 升高时，查 `db.log` 和 `bill_fail`。发送失败没有自动补发，回放工具待确认，不要手写补发脚本。
4. 未接通且 `finish_state=0` 的 5 秒延时走 `GID_VI_ONE_BILLCENTER_BILL_PROCESS`。当日历史分表不存在时移表失败，行留在 `bill_new`。

## 4. 质检结果延迟

级别 P1。呼叫已经结束，结果晚到。详细步骤见 `07-operation/runbooks/qc-result-delay.md`。先用 `bill_new.quality_inspection_status` 判断卡在哪一段，再动对应组件。

1. 状态停在 0：合音未到或未发起质检，先做第 2 类、第 3 类。
2. 状态为 -1：发起质检失败。补偿只有 `/vibillcenter/task/recreate`，必须带时间窗，同一时刻只能跑一个。见 Runbook。
3. `uniops_pending_async_tasks` 升高：qa-server 已经向调用方返回成功，任务还在异步线程池。先看线程池和 master 是否存活，不要先重启 worker。
4. `scheduler.task.queue` 或模型响应队列堆积：扩消费或处理模型侧。失败消息会被 nack 丢掉，清空队列不等于恢复。没有 master 时主任务回调停，见第 6 类；子任务回调不依赖 master。
5. 状态已是 2，客户仍没收到：转入第 5 类。

## 5. 推送失败

级别 P2。质检计算可能已经完成。详细步骤见 `07-operation/runbooks/push-failure.md`。

1. 确认 `pusher.inspectionpusher=true` 的实例在消费 `GID_VI_ONE_PUSHER_INSPECTION_PUSH`。延时组不受这个开关控制，仍会推送。
2. 看 httpproxy 是否能被发现。节点为空时请求会失败并进入 60 秒重试，共 4 次。
3. 重试未耗尽时不要另做一次手工推送。本端等待 8 秒，转发给客户的超时是 10 秒，客户可能已经收到。
4. `result_push_url` 为空会被记成推送成功。success 指标上升不足以证明客户收到了。
5. 重试耗尽后状态 -3 并移入历史表。仓库里没有补推接口，补偿方式待确认。

## 6. scheduler 主节点

级别 P1。子任务回调仍可由任意实例发送；主任务汇总和每小时超时扫描只在 master 上。详细步骤见 `07-operation/runbooks/scheduler-master-failover.md`。

1. 先确认现在有几份 `masterServer=true`。全局只能有一个。
2. 一个都没有：`scheduler.task.repeat.queue` 无人消费。按 Runbook 先停掉旧进程，再启动唯一的新 master。禁止两台同时为 true。
3. 多于一个：主任务可能重复回调。停掉多余实例，只留一个 true。
4. 切换窗口内主任务回调暂停。窗口结束后看 repeat 队列消费者数是否为 1。

## 7. 话务管控误判

大面积误拦截按 P1，个别号码按 P2。质检主链路不依赖这条策略。详细步骤见 `07-operation/runbooks/traffic-control-misjudge.md`。策略含义见 `01-mid-number/traffic-control/overview.md`。

1. 大面积误拦截：临时把 ETCD `/config/audit_strategy_allow` 设为 0，vi-as 跳过策略查询。这会放通全部管控。
2. 个别号码：核对 Redis 前缀和 `append_reason`，再决定是否删除这一条 key。下一分钟脚本仍可能按策略写回。
3. 该拦未拦：看 crontab 是否在跑、`errors.log` 是否有 ImportError 或查询失败。策略服务超时后的默认行为按代码是放行（待确认），先看 vi-as 是否拿到了响应。
4. 恢复策略查询前，用占位号码抽查 Redis 与新话单原因码一致。
