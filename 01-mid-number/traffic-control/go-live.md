---
id: mn-traffic-control-go-live
title: 话务管控 - 上线依赖
domain: mid_number
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-traffic-control-overview, mn-traffic-control-testing, ops-deployment]
source_of_truth:
  - vi-assets/vi-cront-jobs-master/
  - 00-context/design-docs/digest.md
---

# 话务管控上线依赖

以下步骤来自《话务管控上线依赖》和 vi-cront-jobs 脚本。由人在目标环境执行。本文不写连接串、密码和主机名。

## 1. 依赖

| 类别 | 内容 | 缺口 |
| --- | --- | --- |
| MySQL | 库 `vi_strategy` 与 6 张策略配置表 | DDL 在设计文档，不在应用启动时自动建 |
| ClickHouse | `mid_bussops.call_control_detail`、`call_control_statistics` | 上线依赖原文没列这两张表，需要单独确认已建 |
| Redis | 策略结果 key | 原文未写库号。key 形如 `highfreq-{caller}`，见 `01-mid-number/traffic-control/strategies.yaml` |
| 运行时 | Python 3.11+，虚拟环境，`pip install -r requirements.txt` | 文档示例路径是 `/data/venv/cront-jobs11` 与 `/home/production/vi-cront-jobs/` |
| 话单 | 分析类脚本要能查到话单 | 使用 ClickHouse 函数。话单同步任务不在本仓库 |

strategy-service 与 vi-as 的 HTTP 地址、ETCD 开关 `/config/audit_strategy_allow` 也必须先可用，否则脚本写出的 Redis 不会作用到呼叫。

## 2. 文档中的 crontab

| 表达式 | 脚本 | 作用 |
| --- | --- | --- |
| `* * * * *` | viStrategyBusinessRange.py | 超业务范围 |
| `* * * * *` | viStrategyBusinessThreshold.py | 超业务门限 |
| `* * * * *` | viStrategyHighFrequency.py | 短时高频 |
| `* * * * *` | viStrategyLongCall.py | 长通话 |
| `*/10 * * * *` | viStrategyOffwork.py | 非工作时间 |
| `* * * * *` | viStrategyRemoteRange.py | 异地 |
| `0 1 * * *` | viStrategyDailyStatistics.py | 日统计 |
| `* * * * *` | viStrategyDisabledRedisClear.py | 停用后清理 Redis |

原文以虚拟环境里的 python 解释器调用脚本。crontab 没有 flock。脚本若跑过 1 分钟会重叠，上线前要确认脚本是否自带锁。

CONFLICT: 非工作时间在运行逻辑里写每分钟，在 crontab 里写每 10 分钟。

## 3. 上线顺序

1. 建库建表，写入一条 status=0 的策略，确认脚本启动不报错。
2. 配好 MySQL、ClickHouse、Redis，先不启用 crontab。
3. 手工跑停用清理和一条分析脚本，确认日志里的周期校验符合预期。
4. 把一条策略改为生效，打一通测试呼叫，核对 Redis、话单 appendReason、是否拦截。
5. 再挂 crontab。日统计放到次日核对。

## 4. 回滚

1. 将策略 status 置为停用。
2. 跑清理脚本，确认对应 Redis key 消失。
3. 从 crontab 去掉脚本。
4. vi-as 侧若需立刻停止查询，调整 ETCD 审计开关（具体值以 `05-code/modules/vi-as.md` 为准），使呼叫不再等待策略服务。

配置文件如何下发、日志目录、上线后的对账报表，设计文档没有写，待确认。
