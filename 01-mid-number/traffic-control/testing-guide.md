---
id: mn-traffic-control-testing
title: 话务管控 - 测试关注点
domain: mid_number
kind: stable
audience: [qa, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-traffic-control-overview, mn-traffic-control-strategies, mn-traffic-control-codes]
source_of_truth:
  - vi-assets/vi-cront-jobs-master/
  - 00-context/design-docs/digest.md
---

# 话务管控测试关注点

策略脚本在 vi-cront-jobs，呼叫时的拦截在 vi-as 调用的 strategy-service。测脚本时要同时看 Redis 和话单上的 appendReason，不能只看脚本日志。

## 1. 两类脚本

| 类型 | 脚本 | 关注点 |
| --- | --- | --- |
| 配置类 | 非工作时间、超业务门限 | 不扫话单。改配置后，下一次脚本执行才写 Redis。非工作时间的 crontab 文档写每 10 分钟，运行逻辑写每分钟 |
| 分析类 | 短时高频、异地、超业务范围、长通话 | 每分钟扫话单。命中前最多约 1 分钟仍可呼出。只统计时长大于 0 的通话 |

统计周期只支持 86400（自然日）和 3600（自然小时）。其它值打日志并跳过。异地策略表默认周期 0，会落入“不支持”分支。窗口是自然日/小时，不是滑动窗口，跨零点后计数清空。

## 2. 建议用例

号码用 `700xxxx0001` 这类占位，不要写真实号码。

| 策略 | 正例 | 反例 | 边界 |
| --- | --- | --- | --- |
| 短时高频 | 周期内接通次数达到阈值，Redis 出现对应 key，下一次呼叫被策略拦截 | 次数未达阈值 | 阈值刚好相等；周期切到下一个自然日 |
| 异地 | 不同城市数达到阈值 | 同一城市多次 | `stat_cycle_second=0` 时期望被跳过，并确认这是不是配置错误 |
| 非工作时间 | 落在时间段内且次数超过阈值 | 时段外 | 跨零点 `22:00-08:00` 是否被拆成两段 |
| 超业务门限 | 企业外呼量超过上限 | 未超过 | 上限为 0 |
| 超业务范围 | 盲呼离散度、短秒率或接通率超阈值 | 维度未配置 | 维度 5/6/7 各一条 |
| 长通话 | 单通时长达到阈值 | 未达到 | 阈值为 0 时是否每通都命中 |
| 停用 | 策略 status=0 后清理脚本删掉 Redis | 清理脚本未跑 | 停用后下一通呼叫应放通 |

## 3. 呼叫侧

| 检查 | 期望 |
| --- | --- |
| strategy-service 返回断开 | vi-as 不向被叫发 INVITE，话单 finish_state=22，appendReason 为返回值 |
| 超时或实例不存在 | 按 vi-as 当前代码放行或跳过，不要写成“失败即拦截”，以 `05-code/modules/vi-as.md` 为准 |
| 原因码 | 话单、策略表、Redis 三套编码不一致，断言前先看 `01-mid-number/traffic-control/codes.md` |

## 4. 数据侧

命中明细写入 ClickHouse `mid_bussops.call_control_detail`。日统计在凌晨 1 点。MergeTree 重跑不去重。统计库名在一份文档里少写了 s，以 DDL 的 `mid_bussops` 为准，并在环境里确认库是否存在。

分析 SQL 使用的城市字段与话单 DDL 不一致，见 CONFLICT 记录。用例若依赖城市离散度，先确认脚本实际查询的表和列。
