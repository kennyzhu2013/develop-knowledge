---
id: runbook-traffic-control-misjudge
title: Runbook - 话务管控误判
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
  - mn-traffic-control-overview
  - mn-traffic-control-codes
  - mn-traffic-control-strategies
  - svc-vi-as
  - svc-vi-cront-jobs
  - runbook-sip-call-failure
source_of_truth:
  - vi-assets/vi-cront-jobs-master/viStrategyHighFrequency.py
  - vi-assets/vi-cront-jobs-master/viStrategyDisabledRedisClear.py
  - vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java
---

# Runbook：话务管控误判

判定脚本在 vi-cront-jobs，结果写到 Redis `VI_STRATEGY_REDIS`。呼叫时 vi-as 向 strategy-service（ETCD 服务名 `com.cmic.vi.strategy`）`POST /strategy`，超时 500 毫秒、连接 300 毫秒。`auditStrategyAllow=0` 或 ETCD 没有节点时，vi-as 跳过查询。查询失败后的分支按代码走默认放行（待确认）。

strategy-service 不在本次源码里。它如何用 `offtime-`、`threshold-` 做实时计数，待确认。原因码对照见 `01-mid-number/traffic-control/codes.md`，脚本清单见 `01-mid-number/traffic-control/strategies.yaml`。

Redis value 是 JSON，号码型 key 不含企业 ID。`opType` 取策略表 `control_command`：0 拦截、1 放通、2 告警。`appendReason` 在脚本里写死，策略表同名列不生效。

| 前缀 | 对象 | 脚本 | appendReason |
| --- | --- | --- | --- |
| `highfreq-` | 主叫号码 | HighFrequency | 5 |
| `difarea-` | 主叫号码 | RemoteRange | 21 |
| `offtime-` | 客户 ID | Offwork | 13 |
| `threshold-` | 客户 ID | BusinessThreshold | 20 |
| `bizrange-` | 主叫号码 | BusinessRange | 22 / 23 / 24，三个维度共用一个 key，后写覆盖 |
| `longcall-` | 主叫号码 | LongCall | 25 |
| `province-highfreq-` | 主叫号码 | DifareaHighFreq | 26 |
| `city-highfreq-` | 主叫号码 | DifareaHighFreq | 27 |
| `inactive-` | 主叫号码 | SilentCallBurst | 28 |
| `idle-highfreq-` | 主叫号码 | IdleHighFreq | 29 |
| `short-highfreq-` | 主叫号码 | ShortHighfreqBurst | 30 |
| `short-longcall-` | 主叫号码 | ShortLongcallBurst | 31 |
| `inactive-difarea-longcall-` | 主叫号码 | SilentDifareaLongcall | 32 |

调度表见 `07-operation/monitoring.md` 第 4 节。

## 现象

- 正常呼叫被拦截或放音后挂断，话单 `finish_state=22` 或 `append_reason` 为上表中的码。
- 明显超出策略的号码没有被拦截，Redis 里没有对应 key。
- 管控持续时间比 `control_valid_second` 长：策略当天被更新过时，脚本每分钟 SET 并重置 TTL。
- `/var/log/vi-cront-jobs/errors.log` 出现 ImportError、SQL 错误或写入失败。

## 影响

误拦截会拒绝新呼叫，质检主链路仍可对已接通的通话工作。误放通时违规外呼继续。大面积修改策略查询会影响全部呼叫，按 P1 处理，见 `07-operation/emergency/overview.md`。

## 止血（先做）

1. 大面积误拦截。【生产变更】把 ETCD `/config/audit_strategy_allow` 改为 `0`，vi-as 不再调用策略。先读出原值。写入命令待确认。回滚：写回原值。这会放通全部管控，直到改回。

2. 个别号码。只读查看 key（连接方式待确认，不要把口令写进命令行）。号码使用现场值，示例占位 `138xxxx0001`：

```text
GET highfreq-138xxxx0001
```

确认前缀和 value 里的 `opType`、`appendReason`、`condition.customerId` 之后，若确定是残留误写，【生产变更】删除这一个 key。回滚不需要手工 SET：下一分钟对应脚本如果仍然判定命中，会把 key 写回来。要让它不再写回，先把策略行停用（`status=0`，走变更流程），等 `viStrategyDisabledRedisClear.py` 清理。物理删除策略行时，这个清理脚本不会删 key。

3. 该拦未拦。看 crontab 是否还在，以及 `errors.log`。不要重启 vi-as：策略是每次呼叫时现查的。脚本没有 flock，执行超过 1 分钟会重叠，可能重复写明细，但不是「漏拦」的直接原因。

4. ActivePhone 若因缺少 `apps.email` 无法运行，沉默类（`inactive-`、`inactive-difarea-longcall-`）会用过期的活跃时间。线上是否另外部署该模块待确认。不要为了补数据去跑仓库里的 `dataCheck.py`（调试脚本，且不在 crontab）。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | vi-as 是否查询 | `strategy_audit_queue_full`；ETCD `/config/audit_strategy_allow`；服务名 `com.cmic.vi.strategy` | 有节点且允许查询 | 值为 0 或无节点：跳过，表现为放通 |
| 2 | 策略响应 | vi-as `/var/log/vi-as/error.log` 中策略调用失败 | 500 毫秒内返回 opType | 失败分支默认放行（待确认）。这不是 cront 没跑 |
| 3 | key 是否存在 | 按上表 GET。企业型 key 是 `offtime-<客户ID>`、`threshold-<客户ID>` | value 中 opType 与策略表 control_command 一致 | 号码型 key 多企业互相覆盖。`bizrange-` 三个维度后写覆盖 |
| 4 | TTL | key 的剩余生存时间 | 与 `control_valid_second` 同量级 | 策略 `update_time` 在今天：每分钟重置 TTL，当天会一直续上 |
| 5 | 原因码 | 话单 `append_reason` 与 `01-mid-number/traffic-control/codes.md` | 与上表一致 | 改原因码要改脚本，改表字段不会生效 |
| 6 | 长通话数据源 | LongCall 的 `USE_MYSQL=True`，查的是 MySQL `vi_bill`，其它脚本走 StarRocks | 与现网选择一致 | 两边话单进度不同时，长通话会和其他策略结论不一致 |
| 7 | 停用清理 | `DisabledRedisClear` 每分钟 SCAN | status=0 的 key 被删除 | 同一企业同时有 status=0 和 status=1 时，key 可能被删掉又被下一分钟写回（待确认是否有这种数据） |
| 8 | 非工作时间 / 门限 | `offtime-`、`threshold-` 只同步配置，不在脚本里计数 | 配置 key 存在 | 实时计数在 strategy-service（待确认）。`threshold-` 的 value 不含统计周期 |
| 9 | cron | `07-operation/monitoring.md` 第 4 节；`errors.log` | 每分钟任务有新日志 | README 的 `CRONT_ENV=test` 不要用于生产。`effect_time` 或 `control_valid_second` 为 NULL 时脚本会抛错并整次退出 |
| 10 | 明细 | ClickHouse `mid_bussops.call_control_detail`（是否就是 CK 待确认） | 首次命中有明细 | Redis 已写、明细插入失败时只记日志，下次 key 已存在就不再补明细 |

话单上的 `append_reason` 13 和 20 由 `viStrategyBillDetail.py` 在每小时第 5 分钟补明细，脚本本身不写 Redis。

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

若改过 `/config/audit_strategy_allow`，值已按决策写回，vi-as 能再次发现 `com.cmic.vi.strategy`。抽查号码（记录里写 `138xxxx0001` 这种占位，不要写真实号码）的 Redis value 与策略表一致，新话单的 `append_reason` 与 `opType` 一致。`errors.log` 没有新的整次退出。误拦截停止后，被误伤的呼叫可以重新接通。
