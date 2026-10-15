---
id: runbook-push-failure
title: Runbook - 推送失败
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - ops-monitoring
  - ops-emergency
  - svc-vi-pusher
  - svc-vi-billcenter
  - runbook-qc-result-delay
source_of_truth:
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/rocketmq/InspectionResultReceiver.java
  - vi-assets/vi-pusher-master/config.prod/application.yml
---

# Runbook：推送失败

生效路径：vi-billcenter 把质检回调原样发到 `TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL`，消费组 `GID_VI_ONE_PUSHER_INSPECTION_PUSH`。失败后 60 秒延时 topic `TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY`，组 `GID_VI_ONE_PUSHER_INSPECTION_DELAY`。

vi-pusher 通过 ETCD 找到 httpproxy，`POST {节点}/proxy`。客户地址在请求头 `X-Forwarded-For`（值来自 `customer_tab.result_push_url`），超时在 `X-Timeout: 10000`。httpproxy 是否按这两个头转发，待确认。

遗留话单/录音推送的订阅注解已注释，`bill.push.*` 指标不代表本次质检推送。

## 现象

- 客户未收到结果，而 `bill_new.quality_inspection_status` 为 -3，或历史表里该行已是 -3。
- `vi.pusher.inspection.push.error.count` 增加。这个计数只在第 5 次投递、判定最终失败时加，前 4 次失败不加。
- 主消费组堆积，或延时组一直有新消息。

重试时间：首次、+60 秒、+120 秒、+180 秒各推一次，+240 秒的消息发现次数已大于 3，写 -3 并移表。中间每一次失败也会把 `bill_new` 写成 -3，所以 -3 同时表示「还在重试」和「已经最终失败」。看行还在不在 `bill_new`：在则仍可能重试，已进当日历史表则已结束。

## 影响

客户收不到质检结果。计算本身可能早已成功（状态曾经是 2）。`result_push_url` 为空时状态直接记 3 并移表，success 指标包含这些「无需推送」。本端 8 秒超时、转发给客户的超时 10 秒，客户慢响应时可能已经处理过，60 秒后仍会再推一次。客户侧要用话单 ID（`listId`）做幂等。

## 止血（先做）

1. 只读确认开关和指标。

```text
curl -sS "http://<vi-pusher>:19283/manage/info/prometheus"
```

`pusher.inspectionpusher` 不为 true 的实例不消费主 topic，但仍消费延时组并会真实调用 httpproxy。生产上是否存在「只开延时、不开主消费」的实例待确认。不要靠把开关改成 false 来停止重试，延时消费者停不掉。

2. 看两个消费组的堆积（RocketMQ 控制台，命令待确认）。主组无人消费时，按 `07-operation/deployment.md` 检查 `pusher.inspectionpusher` 和进程。进程在处理中被 kill 时，消息已经 ACK，不会重投。

3. httpproxy 节点为空时，代码会请求 `null/proxy` 然后进入重试。在 ETCD 上查看配置项 `parameters-config.httpproxy-name` 对应的服务是否有节点（读命令待确认）。先恢复 httpproxy，不要并行做手工 HTTP 重放。

4. 重试未结束（行仍在 `bill_new`）时，不要另写脚本再推一次。8 秒到 10 秒之间的慢响应会造成客户收到两次。

5. 最终失败（已在历史表且状态 -3）后，仓库没有补推接口。补偿方式待确认。不要把日志里的报文体再 POST 给客户：日志含转写和号码。

6. 企业或客户缓存缺失也会走满 4 次然后移表。缓存只在 ETCD `/config/version` 发生 PUT 或进程重启时重新加载。改了 `result_push_url` 却没有更新这个 key 时，推送仍用旧地址。更新版本 key 是【生产变更】，写入方式待确认，回滚是再 PUT 一次以重新加载正确配置。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 主消费 | 组 `GID_VI_ONE_PUSHER_INSPECTION_PUSH`，线程配置 40 | 有消费者且差值不持续上升 | 查 `inspectionpusher`。false 时主消息停在组上，延时组仍在工作 |
| 2 | 延时消费 | 组 `GID_VI_ONE_PUSHER_INSPECTION_DELAY`，线程配置 30 | 失败后约 60 秒有一次重试 | 延时消息本身发送失败时，结果不再重试，只在日志里 |
| 3 | 指标 | success / error 计数 | error 不增加 | success 含空 URL。空 URL 与真正推送成功无法用状态 3 区分 |
| 4 | httpproxy | ETCD 服务名来自 `httpproxy-name` | `GetServiceNode` 能返回节点 | 为空则失败重试。`/var/log/vi-pusher/error.log` |
| 5 | 客户响应 | 日志中的响应体（打码后看 `code`） | HTTP 2xx 且 JSON `code` 为 `0000` | 大写 `Code` 不算成功。非 2xx、超时、异常都重试 |
| 6 | 话单还在不在 | `SELECT id, quality_inspection_status FROM bill_new WHERE id='<话单ID>'` | 重试中仍在 `bill_new` | 查不到则已移表或从未写入。查不到时消费者直接 return，没有计数 |
| 7 | 配置缓存 | `customer_tab.result_push_url` 与 `/config/version` | 变更后各实例已 reload | 未 reload 时用旧 URL，4 分钟后 -3 移表 |
| 8 | 重复推送 | 客户是否已按 `listId` 受理 | 幂等 | 移表不是事务。并发重复可能让客户收到两次，第二次 INSERT 历史表失败后再走延时，随后查不到话单被丢弃 |

日志格式有一处少了话单 ID 参数，异常栈会占掉第一个占位符，排障时不要假设每一行都有话单 ID。

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

新结果的 error 计数不再增加。配置了推送地址的话单在 4 次之内变为状态 3，并出现在 `handle_start_time` 对应日期的历史表。主消费组有消费者。空 URL 的话单仍会显示成功，抽查时要排除这类客户。
