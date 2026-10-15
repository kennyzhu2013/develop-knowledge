---
id: lesson-known-issues-vi-cront-jobs
title: vi-cront-jobs 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-vi-cront-jobs
  - module-vi-cront-jobs
  - mn-traffic-control-overview
  - mn-traffic-control-codes
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-cront-jobs-master/config.py
  - vi-assets/vi-cront-jobs-master/apps/general.py
  - vi-assets/vi-cront-jobs-master/viStrategyLongCall.py
  - vi-assets/vi-cront-jobs-master/dataCheck.py
---

# vi-cront-jobs 已知坑与不要随手删的逻辑

核对 `vi-assets/vi-cront-jobs-master`，2026-10-15。这些脚本把话务管控结果写入 Redis，vi-as 呼叫时向策略服务查询。策略服务仓库不在本次源码里。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | 日统计和活跃号码脚本引用 `apps.email`，仓库里没有这个模块。按仓库直接跑会导入失败。活跃号码不跑，沉默号码策略用的活跃时间不更新。线上是否另外部署了该模块，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyDailyStatistics.py`、`vi-assets/vi-cront-jobs-master/viStrategyActivePhone.py` | 日统计邮件和沉默号码判定失真。 | 导入是这两个脚本的启动依赖。删导入会让脚本在“发邮件”处直接失败，判定逻辑也跑不到。应补模块或改成可选导入，不要删活跃时间更新。 |
| 2 | README 的 crontab 示例把环境变量设成 test，同时路径写在生产目录下。配置加载缺省环境是 production，才会加载正式配置目录。照抄 README 会加载测试配置。生产实际取值待确认。 | `vi-assets/vi-cront-jobs-master/README.md`、`vi-assets/vi-cront-jobs-master/config.py` | 生产任务连到测试库或测试 Redis，管控键写错环境。 | 环境分支是隔离配置的方式。删分支会让所有环境共用一套配置。应改示例，不要删 production 分支。 |
| 3 | 长通话脚本把 `USE_MYSQL` 设为 true。注释写 true 是 MySQL 测试、false 是 StarRocks 正式。生产因此走 MySQL 查话单，且没有按通话时长大于 0 过滤，会把当天话单拉进进程。是否有意，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyLongCall.py` | 和其它脚本的数据源不一致；数据量大时内存和数据库压力高。 | 开关是数据源选择。删 MySQL 分支会让这个脚本立刻改走 StarRocks，口径和性能一起变。应先确认线上意图再改布尔值。 |
| 4 | 策略“今天更新过”时，每分钟都重新 SET 并刷新 TTL。命中号码当天会被持续管控，超过 `control_valid_second` 的字面含义。是否预期，待确认。 | `vi-assets/vi-cront-jobs-master/apps/general.py` | 白天改过策略后，号码管控到当天结束，而不是配置的秒数。 | 刷新是为了让刚改的策略马上覆盖旧键。删判断会让旧 TTL 在策略更新后仍然生效。应改成按配置秒数设置一次，保留“更新后重写”的能力。 |
| 5 | 号码型 Redis 键只有前缀和主叫，没有企业 ID。多个企业共用时互相覆盖。清理任务只按最后写入者的客户判断是否删除。一个号码是否只属于一个企业，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyHighFrequency.py`、`vi-assets/vi-cront-jobs-master/viStrategyDisabledRedisClear.py` | A 企业的管控会被 B 企业的结果覆盖，或被错误清理。 | 键格式是策略服务读取的契约。改成带企业 ID 必须和读取方一起发。不要只在写入侧删掉号码键。 |
| 6 | 经营范围三个维度共用一个键前缀，后命中的维度覆盖原因码。SQL 把次数硬编码成大于 50，低于 50 通的号码永不判定。话单表名没有库前缀。 | `vi-assets/vi-cront-jobs-master/viStrategyBusinessRange.py` | 原因码只反映最后一次维度。小流量号码不受这条策略管控。缺库前缀时 SQL 可能打到错误库。 | 50 是当前生效阈值，不是注释。删条件会让所有打过电话的号码进入判定。键前缀是读取契约，不能单独改。 |
| 7 | 非工作时间和业务阈值在 `effect_time` 为空时直接和当前时间比较，抛类型错误，当次所有策略都不同步。 | `vi-assets/vi-cront-jobs-master/viStrategyOffwork.py`、`vi-assets/vi-cront-jobs-master/viStrategyBusinessThreshold.py` | 一条空生效时间会让这次 cron 的企业级策略全部不写 Redis。 | 生效时间比较是“未到生效点不写入”的门禁。删比较会让未来才生效的策略立刻管控。应把空值当成未生效并继续下一条。 |
| 8 | `control_valid_second` 为 NULL 时，`int(strategy.get(..., 0))` 仍得到 None，因为键存在。`int(None)` 抛错。 | `vi-assets/vi-cront-jobs-master/viStrategyHighFrequency.py` | 一条空时长会让该脚本本次退出，后面的号码都不写。 | 时长决定键的 TTL。删掉 int 转换会把非法值写进 expire。应把 NULL 当成 0 再继续循环。 |
| 9 | 话单明细的默认时间段用了非 ASCII 连字符。只有缺配置时才走这个默认值，按普通连字符拆分会失败。 | `vi-assets/vi-cront-jobs-master/viStrategyBillDetail.py` | 缺配置时明细脚本崩溃，当次不写统计。 | 默认值是配置缺失时的兜底。删默认值会让缺配置直接无时间窗。应改成普通连字符。 |
| 10 | 原因码在代码里硬编码。策略表的 `append_reason` 列读出来了但没有用。改原因码必须改代码。列的设计用途待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyOffwork.py` | 运营在表里改原因码不会改变 Redis 和话单上的原因。 | 硬编码值是 vi-as 和报表正在用的口径。改成读列之前要和话单、策略服务对齐，不能先把硬编码删掉导致原因码变空。 |
| 11 | 明细 ID 用毫秒时间戳加 4 位序号，没有脚本名。多个脚本同一毫秒可能生成相同 ID。 | `vi-assets/vi-cront-jobs-master/viStrategyBillDetail.py` | 明细插入主键冲突，统计缺行。 | ID 是明细主键。删生成逻辑会让插入没有主键。应把脚本名算进去。 |
| 12 | Redis 已经写成功、明细写失败时只记日志。下次运行键已存在，不再补写明细。 | `vi-assets/vi-cront-jobs-master/viStrategyHighFrequency.py` | 号码已被管控，但统计表没有这条命中。 | 先写 Redis 是为了管控先生效。倒过来会让统计有了、呼叫还不拦截。应在键已存在时仍补明细，不要删 Redis 写入。 |
| 13 | 日统计的防重被注释掉，重跑同一天会重复写统计。另有一个按明细维度防重的实现，口径不同（明细维度对话单原因维度）。哪一个是正式口径，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyDailyStatistics.py` | 补跑日统计会把次数加双倍。 | 被注释的防重是防重复的半成品。不要把插入统计的主路径删掉。恢复防重前要先定口径。 |
| 14 | 闲时高频的比例按小数比（例如 0.4），另两个策略按乘 100 的百分比比。闲时时段只精确到小时。 | `vi-assets/vi-cront-jobs-master/viStrategyIdleHighFreq.py`、`vi-assets/vi-cront-jobs-master/viStrategyDifareaHighFreq.py` | 同一套配置习惯会把 0.4 和 40 用错，策略要么全命中要么全不命中。 | 两套比较都在线上生效。删掉乘 100 会让已经按百分比填写的策略失效。改口径要和配置界面一起改。 |
| 15 | 短时高频突增用短窗口次数和历史“日均”比，日均只统计有通话的天。口径是否正确，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyShortHighfreqBurst.py` | 沉默天后突然呼叫更容易超过日均，可能误管控。 | 日均是这条策略的阈值来源。删比较会让短窗口一有通话就命中，或完全不命中。应先确认口径再改公式。 |
| 16 | 字段名像“短通话定义秒数”，代码把它当长通话下限使用。 | `vi-assets/vi-cront-jobs-master/viStrategyShortLongcallBurst.py` | 按字段名把值配小，会把普通通话当成“长通话突增”。 | 这个字段是现网阈值。改语义必须改配置数据和读取方。不要只改比较方向。 |
| 17 | 清理任务只处理 status=0。策略行被物理删除时键不会清。同一企业同时有停用和生效行时，键可能被反复删除又重建。是否存在这种数据，待确认。 | `vi-assets/vi-cront-jobs-master/viStrategyDisabledRedisClear.py` | 已删除的策略仍然管控，直到 TTL。或者生效策略被停用行清掉。 | 清理是停用策略后的唯一回收手段。删脚本会让停用后的号码一直管控到 TTL，而有的键会被“今天更新”反复续期（见第 4 条）。 |
| 18 | cron 没有锁。单次执行超过 1 分钟会和下一次重叠，重复写明细的风险变大。 | `vi-assets/vi-cront-jobs-master/viStrategyHighFrequency.py` | 同一分钟两轮写入，明细重复或键被覆盖两次。 | 脚本主体是管控写入。加锁可以，不要用“删 cron”来避免重叠，否则策略不再更新。 |
| 19 | customer_id 和阈值用字符串拼进 SQL。数据来自内部配置表。沉默异地长通话对号码做了引号转义，其它脚本不一定有。 | `vi-assets/vi-cront-jobs-master/viStrategySilentDifareaLongcall.py` | 配置表若被写入特殊字符，SQL 会被改变。号码转义是防注入的一小段，不能当多余删掉。 | 查询是策略判定的数据来源。改成参数绑定时应保留过滤条件。不要为了“去掉拼接”把查询删成空跑。 |
| 20 | `dataCheck.py` 是调试脚本，源码中存在未脱敏手机号。本文不写出号码。 | `vi-assets/vi-cront-jobs-master/dataCheck.py` | 仓库和日志扩散会带出真实格式号码，违反源码不得写真实号码的要求。 | 可以删或脱敏这个调试脚本。不要把它和正式策略脚本的判定逻辑一起删。 |
