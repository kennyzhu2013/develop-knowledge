---
id: runbook-sip-call-failure
title: Runbook - SIP 接入失败
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
  - svc-vi-media
  - runbook-traffic-control-misjudge
  - runbook-record-merge-backlog
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/SipEngine.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/http/handler/OpsHandler.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/XmodePromExporterService.java
---

# Runbook：SIP 接入失败

组件：vi-as（UDP 5080、HTTP 5287）、xproxy 的 OPTIONS 探测、ETCD 中的 xsipproxy / vi-media / strategy-service。vi-as 不消费 MQ。

## 现象

- xproxy 的 OPTIONS 探测得不到 200，或新的 INVITE 大面积失败。
- `as_invite_arrive_total` 掉下去，`sip_message_queue_size` 持续上升。
- `/ops/status` 的 state=2，或进程已经不在。
- `call_reject_due_to_too_much` 上升：会话数保护，响应 500，To-tag 为 `cmic`。

## 影响

新呼叫不能接续。已经 pause 的实例不再接新呼叫，在途会话仍留到结束或优雅停机。录音失败不一定是 SIP 故障：媒体创建失败时呼叫仍可能接续（待确认）。

## 止血（先做）

1. 只读确认范围。对每个 vi-as 执行：

```text
curl -sS "http://<vi-as>:5287/ops/status"
```

state=3 为在线，state=2 为下线。同时看 `session`。业务指标：

```text
curl -sS "http://<vi-as>:5287/metrics"
```

JMX OS 指标在 8588 的 `/metrics`。在主机上确认 UDP 5080 处于监听（`ss -ulnp` 或现网同等命令，待确认是否安装 ss）。

2. 只有个别实例异常时，【生产变更】摘掉该实例的新呼叫：

```text
curl -sS -X POST "http://<vi-as>:5287/ops/pause"
```

期望 `{"res":"success"}`，随后 status 的 state=2。回滚：

```text
curl -sS -X POST "http://<vi-as>:5287/ops/resume"
```

state 回到 3。其余健康实例不要一起 pause。

3. 策略查询把呼叫打失败或 `strategy_audit_queue_full` 持续增加时，【生产变更】把 ETCD `/config/audit_strategy_allow` 改为 `0`（无节点或该值为 0 时 vi-as 跳过策略）。写入命令待确认，先读出原值。回滚是写回原值。这会放通话务管控，见 `07-operation/runbooks/traffic-control-misjudge.md`。

4. `call_reject_due_to_too_much` 升高时，先看 ETCD `/config/max_sessions` 和 `as_controller_alive_num`，不要先重启。缺这个 key 时 vi-as 启动会 NPE。

5. 需要停进程时用 SIGINT（`kill -2 <pid>`），按 `07-operation/deployment.md` 第 3.1 节等会话下降。`kill -9` 会丢掉在途话单的 finish 更新和 MQ 发送。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 进程与 5080 | 主机上查看 UDP 5080；HTTP 5287 `/ops/status` | 端口在听，state=3 | 按部署手册拉起。启动前 ETCD 需有 `/config/max_sessions` |
| 2 | SIP 队列 | 指标 `sip_message_queue_size`（每 10s 导出） | 不持续攀升 | 先 pause，等队列下降后再停进程 |
| 3 | 信令日志 | `/var/log/vi-as/signaling.log`、`error.log` | 无成片 5xx | 对照 `as_bill_finishstate`：7 路由失败，22 策略拦截 |
| 4 | 出呼代理 | ETCD 服务名 `com.cmic.xsipproxy`（读命令待确认） | 有可用节点 | proxyMode 与路由见部署配置，本文不写地址 |
| 5 | 媒体 | 指标 `as_http_error_count`、`recording_call_retry1/2/3`；ETCD `com.cmic.vi.media` | 创建会话成功 | 呼叫可能仍接通但不录音，转 `07-operation/runbooks/record-merge-backlog.md` |
| 6 | 策略 | `strategy_audit_queue_full`、`strategy_audit_response_queue_full` | 500ms 内返回 | 超时/异常分支按代码走默认放行（待确认）。误拦截走话务管控 Runbook |
| 7 | 话单副作用 | `rocket_queue_too_much`、`xdtmf_db_fail_count`、`/var/log/vi-as/db.log` | 话单仍在写 | 转入 `07-operation/runbooks/bill-backlog.md`。startMode=1 时只写 db.log |

会话数打满时的 500 与下游 IMS 的 500 不同：前者 To-tag 为 `cmic`。

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

摘流实例已 resume，state=3。`as_invite_arrive_total` 随呼叫恢复。`sip_message_queue_size` 不再持续上升。若改过 `/config/audit_strategy_allow`，确认已写回原值，或留下书面的放通窗口。观察时长待确认。
