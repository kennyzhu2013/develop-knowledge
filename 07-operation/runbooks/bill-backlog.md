---
id: runbook-bill-backlog
title: Runbook - 话单积压
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
  - svc-vi-as
  - svc-vi-billcenter
  - runbook-record-merge-backlog
  - runbook-qc-result-delay
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/BillCenterService.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/BillService.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/BillReceiver.java
---

# Runbook：话单积压

话单消息：vi-as 生产 `TID_VI_ONE_AS_BILL_PUSH_BILLCENTER_NORMAL`（tag `BillPushMsg`，key=billId），字段 `billId`、`isPickUP`、`dbId`。消费组 `GID_VI_ONE_BILLCENTER_BILL`，线程数 `billConsumerCount`（模板 50）。

`isPickUP=true`（接通且不是仅 G.729）时，vi-billcenter 直接返回，不移表，等合音消息。这类行变多是合音问题，用 `07-operation/runbooks/record-merge-backlog.md`。

## 现象

- 消费组 `GID_VI_ONE_BILLCENTER_BILL` 或延时组 `GID_VI_ONE_BILLCENTER_BILL_PROCESS` 差值持续上升。
- `vi_bill.bill_new` 行数上升，同时 vi-as 的 `rocket_queue_too_much`、`xdtmf_write_db_log_count`、`xdtmf_db_fail_count` 增加。
- `/var/log/vi-as/db.log` 出现入库 SQL。`vi_bill.bill_fail` 新增行。

## 影响

未接通话单不能按时进入按日历史表。呼叫接续不读这个消费组。消费者无条件 ACK：进程在消息处理中被杀掉时，这条话单不会被 MQ 重投。

G.729 接通被 vi-as 标成 `isPickUP=false`，会按未接通移表，不会发起质检。

## 止血（先做）

1. 只读区分三类积压。

```text
curl -sS "http://<vi-billcenter>:19277/manage/info/prometheus"
```

看 `vi.bill.request.total`（只计未接通路径）和 `vi.get.connection.timeout.total`。RocketMQ 控制台看两个消费组（命令待确认）。

在 `vi_bill` 上只读：

```sql
SELECT quality_inspection_status, COUNT(*) AS cnt
FROM bill_new
GROUP BY quality_inspection_status;
```

状态 0 且通话已接通：等合音，不是本消费组的失败。

2. 消费进程在、只是慢：不要重启 vi-billcenter。已拉取的消息 ACK 后无法重投。Druid `max-active=50`、`max-wait=1000` 毫秒，连接超时会让这条消息在打日志后被 ACK 掉。

3. vi-as 发 MQ 失败只插入 `bill_fail`，仓库里没有补发任务。入库失败写 `/var/log/vi-as/db.log`（logger `dblog`），重试最多约 2 分钟。回放工具是否存在待确认。不要根据日志手写 INSERT 脚本。

4. 若实例被配成 startMode=1，所有话单只写 db.log，不入库、不发 MQ。改回 startMode=0 属于【生产变更】：`POST http://<vi-as>:5287/changeStartMode`，体为 `{"startMode":0}`。该接口无鉴权。回滚是再设回变更前的模式。先用配置和日志确认当前模式，不要凭空切换。

5. 当日分表 `bill_YYYY_MM_DD` 不存在时，移表 INSERT 失败，行留在 `bill_new`。建表依赖存储过程。补建表是【生产变更】，回滚方案待确认，不要在本文执行 DDL。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 话单消费组 | `GID_VI_ONE_BILLCENTER_BILL` | 差值稳定（阈值待确认） | 看进程与 DB 连接指标。线程数是配置项，改了要发布 |
| 2 | 5 秒延时 | `TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY`，组 `GID_VI_ONE_BILLCENTER_BILL_PROCESS` | 未接通且 finish_state=0 的话单约 5 秒后被移表 | 延时消费者不再看 finish_state，到期直接移表。billId 不是纯数字时会被静默跳过，行一直留在 `bill_new` |
| 3 | vi-as 发布队列 | 指标 `rocket_queue_too_much`、`as_bill_send_num` | 队列长度未持续超过 200 | 发布池配置 16 线程、队列 10000。超过后计数，失败写入 `bill_fail` |
| 4 | 入库 | `xdtmf_db_fail_count`、`xdtmf_db_task_reject_count`、`db.log` | 失败不持续增加 | DB 拒绝比例来自 ETCD `/config/db_oper_reject_ratio`。startMode=1 时全部只写日志 |
| 5 | 库连接 | `vi.get.connection.timeout.total` | 不增加 | 超时的消息已经 ACK，需要按 `bill_fail` / db.log 人工补偿（工具待确认） |
| 6 | 接通行 | `bill_new` 里状态 0、有通话时长的行 | 合音到达后会离开 | 没有合音超时任务。见合音 Runbook。合音先到话单会 60 秒重试 3 次，失败写入 `recover.log` 的 `record_repush_fail` |
| 7 | 历史表 | 当日 `bill_YYYY_MM_DD` 是否存在（只读 `SHOW TABLES`） | 表在 | 不在则移表失败。不要手工 DROP |

`vi.bill.request.total` 不含接通话单。接通量要看 vi-as 的 `as_invite_ok_num` / `as_in_call_num`，不要用这个计数判断话单消费是否健康。

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

`GID_VI_ONE_BILLCENTER_BILL` 与延时组的差值不再上升。新的未接通话单能进入当日历史表。`xdtmf_write_db_log_count` 不再持续增加。仍停在 `bill_new` 且等待合音的接通话单，用合音和质检 Runbook 的标准，不算本故障未恢复。
