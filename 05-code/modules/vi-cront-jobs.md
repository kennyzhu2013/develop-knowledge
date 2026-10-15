---
id: module-vi-cront-jobs
title: vi-cront-jobs 代码深度解析（话务管控定时脚本）
domain: mid_number
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-vi-cront-jobs]
source_of_truth: [vi-assets/vi-cront-jobs-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/vi-cront-jobs-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/vi-cront-jobs.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# vi-cront-jobs 仓库分析报告

> 分析对象：`/workspace/vi-assets/vi-cront-jobs-master`
> 分析角色：架构师（architect）
> 说明：所有结论来自代码阅读，引用格式 `相对路径:函数`（脚本为过程式，多数逻辑位于 `if __name__ == '__main__':` 块，记作 `脚本名:main`）；无法从代码确认的标注“待确认”。报告不含密码、Token、密钥、连接串、主机地址、真实号码。

---

## 1. 定位

vi-cront-jobs 是**专线质检的话务管控策略定时脚本集**（README：“专线质检策略定时脚本”）。每个 `viStrategy*.py` 是一个独立的 cron 进程，主要做三件事：

1. **策略判定**：读取 MySQL `vi_strategy` 库中各策略配置表（status=1 生效），按企业（customer）从当天话单表 `vi_bill.bill_YYYY_MM_DD`（StarRocks）统计主叫号码的通话行为，命中阈值的号码/企业写入 Redis 管控 key。
2. **管控明细**：首次命中时写入 ClickHouse `mid_bussops.call_control_detail`；日终汇总到 `mid_bussops.call_control_statistics`。
3. **辅助维护**：策略停用后清理 Redis（DisabledRedisClear）、号码活跃时间维护（ActivePhone）、非工作时间/门限的明细补录（BillDetail）。

本仓库**只写“管控标记”，不执行实际拦截**。实际呼叫时，vi-as 通过 HTTP 调用 `{strategyHost}/strategy`（`vi-as-master/.../AuditStrategyCallBack.java`）获取 `opType`/`appendReason`；该策略管控服务不在当前资产中，推断其读取本仓库写入的 Redis key（待确认）。拦截后话单的 `append_reason` 字段记录原因码，DailyStatistics 据此日统计。

```text
vi_strategy.strategy_*（配置） ─┐
user_qc.customer_tab（企业开关）┼─▶ viStrategy*.py（cron）──SET──▶ Redis（VI_STRATEGY_REDIS）──▶ 策略管控服务(待确认) ◀─HTTP─ vi-as（呼叫接续）
vi_bill.bill_YYYY_MM_DD（话单）─┘            │
                                              └──INSERT──▶ mid_bussops.call_control_detail ──日汇总──▶ call_control_statistics
```

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言 | Python 3（具体版本未在仓库声明，待确认；README 使用 `/home/production/vi-cront-jobs/myenv/bin/python` 虚拟环境） |
| 依赖 | `requirements.txt`（UTF-16 编码）：SQLAlchemy 1.4.54、clickhouse-driver 0.2.10、clickhouse-sqlalchemy 0.2.8、redis 4.6、PyYAML、Jinja2、paramiko、starrocks 1.0.6、pycryptodome、PyMySQL 等 |
| 框架 | 无 Web 框架；每个脚本独立运行，无常驻进程 |
| 构建/部署 | 无 Dockerfile、无部署脚本；按 README 中的 crontab 部署到主机 |
| 入口 | 各 `viStrategy*.py` 的 `__main__`；`ck_check.py`、`dataCheck.py`、`dailyRange.py` 为手工/调试脚本 |
| 端口 | 无监听端口 |
| 环境区分 | 环境变量 `CRONT_ENV`：缺省/`production`→`config/`，`south`→`config.south/`，`north`→`config.north/`，`test`→`config.test/`，其他→`config.local/`（`config.py`）。`config.yaml` 通过 Jinja2 用环境变量渲染。仓库各目录只有 `logging.yaml`，**`config.yaml` 不在仓库中**；`config.south`/`config.north` 目录不存在 |
| 日志 | `/var/log/vi-cront-jobs/{info,errors,info-vi-strategy}.log`，logger `vi_strategy`（`config/logging.yaml`）；DisabledRedisClear 另用 `logging.basicConfig` 与 logger `strategy_redis_daily_clean` |

## 3. 代码结构表

| 路径 | 职责 |
| --- | --- |
| `config.py` | 选择配置目录、Jinja2 渲染 `config.yaml`、加载日志配置、创建 `LOGGING_PATH` |
| `apps/database.py` | SQLAlchemy MySQL 引擎（`SQLALCHEMY_DATABASE_URI` + `SQLALCHEMY_BINDS`：mid_auth、mid_strategy、south_bill、north_bill、vi_strategy、vi_bill、user_qc、mid_bussops 等），`selectForSQL`、`executeForSQL` |
| `apps/clickhouse.py` | `ckSelectForSQL`（SQLAlchemy，`CK_DATABASE_URI`/`CK_BINDS`；vi_bill 引擎按代码注释为 StarRocks）；`ckAddObjects`（clickhouse_driver 原生协议批量写，配置项 `CLICKHOUSE_BUSSOPS`、`CLICKHOUSE_STRATEGY`、`CLICKHOUSE_BUSSOPS_ZYC`、`CLICKHOUSE_YTXDB`、`CLICKHOUSE_BILL_HISTORY`） |
| `apps/general.py` | 企业过滤（`get_call_enabled_customer_ids`、`get_status_enabled_customer_ids`）、`get_customer_app_id_dict`、`is_strategy_updated_today`、主账户策略展开 `resolve_strategies_with_main`、中间号字典、`get_mail`（硬编码内部邮件服务地址，本报告不写出）、`uniops_monitor`（配置项 `UNIOPS_MONITOR`） |
| `apps/timeUtil.py` | 读写“上次执行日期”文件等时间工具 |
| `apps/models.py` | 旧 ORM（complaint_detail、strategy_detail、control_frequency_threshold、strategy_info、complaint_gzh_detail），策略脚本未引用 |
| `apps/aes.py` | AES-CBC 加解密工具类 `AesUtil`（调用方传入 key/iv）；**仓库内无任何引用**，密钥不在本报告中 |
| `apps/sftp.py` | paramiko `SSHConnection`（上传/下载/执行命令），未被引用 |
| `apps/email` | **被 import 但仓库中不存在**（见 §12） |
| `viStrategy*.py`（16 个） | 各策略/维护任务，详见 §4.2 |
| `ck_check.py` | 查询某日 `call_control_statistics` 行数（调试） |
| `dataCheck.py` | 异地大范围策略的数据核对（调试，含硬编码测试号码，本报告不写出） |
| `dailyRange.py` | 按 `call_control_detail` 聚合生成日统计（DailyStatistics 的另一种实现，未进 crontab） |

## 4. 核心业务流程

### 4.1 策略脚本通用流程（适用于 10 个“号码级判定”脚本）

| 步骤 | 做什么 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 取企业白名单：`get_call_enabled_customer_ids()`（user_qc.customer_tab `status=1 AND is_call=1`）；企业级配置同步脚本用 `get_status_enabled_customer_ids()`（仅 `status=1`） | `apps/general.py` | — |
| 2 | 查策略表 `vi_strategy.strategy_xxx WHERE status=1 [AND effect_object=1]` | 各脚本 main | 无策略 `sys.exit()` |
| 3 | 老策略（有 is_main/app_id 字段）调 `resolve_strategies_with_main`：独立策略（is_main=0）优先；主账户策略（is_main=1）按 app_id 展开到没有独立策略的子企业 | `apps/general.py:resolve_strategies_with_main` | — |
| 4 | 逐条校验数值字段（空/非数字跳过并记 error 日志）；统计窗口不支持的跳过 | 各脚本 | 跳过该策略 |
| 5 | 拼 SQL 查当天话单 `vi_bill.bill_{YYYY_MM_DD}`（`ckSelectForSQL`，StarRocks；`USE_MYSQL=True` 时走 MySQL），过滤 `customer_id`、`handle_start_time >= effect_time` | 各脚本 | 查询异常未捕获，脚本退出 |
| 6 | 命中：`redis_key = 前缀 + 主叫号码`；若 key 不存在 **或** 策略今天更新过（`is_strategy_updated_today`），`SET` JSON 值，`control_valid_second>0` 时 `EXPIRE` | 各脚本 | — |
| 7 | 仅 key 原本不存在（首次触发）时生成明细行 | 各脚本 | — |
| 8 | 批量 `INSERT INTO mid_bussops.call_control_detail` | `apps/clickhouse.py:ckAddObjects` | 异常仅记日志（Redis 已写，明细丢失） |

Redis value 通用结构（String，JSON）：

```json
{
  "isMain": 0,
  "priority": 0,
  "condition": {"customerId": "<企业ID>"},
  "action": {"opType": "<策略表 control_command 原值>", "appendReason": "<原因码>"}
}
```

- `isMain` 仅老策略（HighFrequency、LongCall、RemoteRange、BusinessRange、Offwork、BusinessThreshold）写入；新策略不写。
- `opType` 为策略表原值：0 拦截、1 放通、2 告警。
- `appendReason` 在代码中**硬编码**，不使用策略表 `append_reason` 列。

明细表 `call_control_detail` 列：id、mobile_id、channel_id、company_id、customer_id、trigger_time、control_subjects（主叫号码）、strategy_id、strategy_name（策略编号）、control_dimension、control_command（明细口径：0 拦截、1 告警、2 放通，由 `{0:"0",1:"2",2:"1"}` 映射）、threshold_config、actual_value、create_time、update_time。id = `毫秒时间戳 + 4 位序号`（`gen_snowflake_id`）。

### 4.2 每个 viStrategy*.py

#### 4.2.1 viStrategyHighFrequency.py — 短时高频外呼（策略编号 "1"）

- **策略含义**：单个主叫号码在自然日/自然小时内接通次数过多。
- **数据来源**：配置 `vi_strategy.strategy_high_frequency`（frequency_threshold、stat_cycle_second、effect_time、control_valid_second、control_command、is_main、app_id，`status=1 AND effect_object=1`）；企业 `user_qc.customer_tab`（call_enabled）；话单 `vi_bill.bill_YYYY_MM_DD`（StarRocks，`USE_MYSQL=False`）。
- **判定条件与阈值来源**：按主叫分组 `COUNT(*)`，条件 `call_duration_s>0`；`stat_cycle_second=86400` 为当天，`3600` 为当前自然小时，其他值跳过；`cnt >= frequency_threshold` 命中。阈值来自策略表。支持主账户展开。
- **命中后动作**：Redis `SET highfreq-{主叫号码}`，value 含 isMain，appendReason "5"，TTL=control_valid_second（≤0 时永久）；首次命中写 call_control_detail（threshold_config=阈值，actual_value=次数）。是否封禁由 opType 决定（0 拦截/2 告警/1 放通），由下游执行。
- **调度频率**：每分钟（README crontab `* * * * *`）。
- 代码位置：`viStrategyHighFrequency.py:main`。

#### 4.2.2 viStrategyLongCall.py — 长通话管控（策略编号 "8"）

- **策略含义**：单通通话时长超过阈值的主叫号码。
- **数据来源**：`vi_strategy.strategy_long_call`（long_call_threshold、control_valid_second、effect_time、is_main、app_id）；话单 `vi_bill.bill_YYYY_MM_DD`，**`USE_MYSQL=True`，经 MySQL `selectForSQL(engine="vi_bill")` 查询**（与其他脚本不同）。
- **判定条件与阈值来源**：当天（`DATE(handle_start_time)=CURDATE()`）`SELECT DISTINCT mobile_id..caller_number, call_duration_s`，Python 中 `call_duration_s >= long_call_threshold` 命中；支持主账户展开。
- **命中后动作**：Redis `SET longcall-{主叫号码}`，含 isMain，appendReason "25"，TTL=control_valid_second；首次命中写明细。
- **调度频率**：每分钟。
- 代码位置：`viStrategyLongCall.py:main`。

#### 4.2.3 viStrategyOffwork.py — 非工作时间外呼（策略编号 "3"）

- **策略含义**：企业在“宵禁时段”外呼超过次数门限。本脚本只做**配置同步**，实时计数由下游完成（待确认）。
- **数据来源**：`vi_strategy.strategy_offwork_call`（control_time_range、call_threshold、control_command、effect_time、is_main、app_id；仅 `status=1`，无 effect_object 过滤）；企业 `get_status_enabled_customer_ids`。不查话单。
- **判定条件与阈值来源**：effect_time > 当前时间则跳过；其余全部同步。
- **命中后动作**：每次运行**无条件覆盖** `SET offtime-{customer_id}`，value `condition` 含 `{customerId, threshold: call_threshold, curfew: control_time_range}`，appendReason "13"，**无 TTL**；不写明细（明细由 BillDetail 补录）。
- **调度频率**：每 10 分钟（`*/10 * * * *`）。
- 代码位置：`viStrategyOffwork.py:main`。

#### 4.2.4 viStrategyBusinessThreshold.py — 超业务门限 / 企业外呼门限（策略编号 "4"）

- **策略含义**：企业在统计周期内外呼总量上限。本脚本只做**配置同步**。
- **数据来源**：`vi_strategy.strategy_business_threshold`（stat_cycle_second、max_call_limit、control_command、control_valid_second、effect_time、is_main、app_id）；企业 `get_status_enabled_customer_ids`。不查话单。
- **判定条件与阈值来源**：effect_time > 当前时间跳过；key 不存在或今天更新过则写入。
- **命中后动作**：`SET threshold-{customer_id}`，`condition` 含 `{customerId, threshold: max_call_limit}`，appendReason "20"，TTL=control_valid_second（到期后下一分钟重新同步）；**value 中不含 stat_cycle_second**，下游如何得知统计周期 待确认。不写明细（由 BillDetail 补录）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyBusinessThreshold.py:main`。

#### 4.2.5 viStrategyRemoteRange.py — 异地大范围外呼（策略编号 "2"）

- **策略含义**：单个主叫号码在周期内拨打的“外呼地市”数量过多（号码漫游/跨地使用）。
- **数据来源**：`vi_strategy.strategy_remote_range`（area_threshold、stat_cycle_second、control_valid_second、effect_time、is_main、app_id）；话单 StarRocks。
- **判定条件与阈值来源**：按主叫 `COUNT(DISTINCT call_out_area_code)`，`call_duration_s>0`；周期 86400（当天）或 3600（当前小时）；`city_cnt > area_threshold` 命中；支持主账户展开。
- **命中后动作**：`SET difarea-{主叫号码}`，含 isMain，appendReason "21"，TTL=control_valid_second；首次命中写明细（actual_value=地市数）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyRemoteRange.py:main`。

#### 4.2.6 viStrategyBusinessRange.py — 超业务范围（策略编号 "5"，维度 5/6/7）

- **策略含义**：识别疑似“盲呼/群呼”的号码，按三个维度：5 盲呼离散度、6 短秒率、7 接通率。
- **数据来源**：`vi_strategy.strategy_business_range`（dimension、threshold_value(float)、stat_cycle_second、control_valid_second、effect_time、is_main、app_id）；话单表名写作 `bill_YYYY_MM_DD`（**未带 `vi_bill.` 前缀**，依赖引擎默认库）。
- **判定条件与阈值来源**：
  - 子查询 t1：被叫为 11 位且以 '1' 开头，按（主叫，被叫前 9 位）分组统计 `COUNT(DISTINCT RIGHT(被叫,2))` 作为 callee_cnt。
  - 子查询 t2：短秒率 = `0<时长≤3` 的占比，接通率（call_forward_rate）= `finish_state=1` 的占比，**硬编码 `HAVING COUNT(*) > 50`**。
  - 两者 inner join。Python 按维度判定：dim 5 `callee_cnt > threshold_value`（appendReason "22"）；dim 6 `short_rate > threshold_value`（"23"）；dim 7 `call_forward_rate < threshold_value`（"24"）。
  - resolve_with_main 使用 key_func（customer_id, dimension），同一企业可配多个维度。
- **命中后动作**：`SET bizrange-{主叫号码}`（**三个维度共用一个 key**，后写覆盖），含 isMain，TTL=control_valid_second；首次命中写明细，control_dimension=维度。
- **调度频率**：每分钟。
- 代码位置：`viStrategyBusinessRange.py:main`。

#### 4.2.7 viStrategyDifareaHighFreq.py — 外省/外地市大范围高频（策略编号 "9" 省 / "10" 市）

- **策略含义**：号码在报备地之外的省/地市大量外呼。
- **数据来源**：`vi_strategy.strategy_difarea_high_frequency`（effect_area 1 省/2 市、stat_cycle_second、call_threshold、area_ratio_threshold（如 "0.40"）、control_valid_second、effect_time）；话单 StarRocks。无 is_main 处理，仅按 call_enabled 过滤。
- **判定条件与阈值来源**：滑动窗口 `[max(now−stat_cycle_second, 今天 00:00), now]`；省级比较 `biz_report_province_id` 与 `call_out_province_id`，市级比较 `biz_report_area_code` 与 `call_out_area_code`；要求时长>0、报备字段非空；`total_calls > call_threshold AND out_area_pct > area_ratio_threshold×100` 命中。
- **命中后动作**：`SET province-highfreq-{主叫号码}`（appendReason "26"）或 `city-highfreq-{主叫号码}`（"27"），value 无 isMain，TTL=control_valid_second；首次命中写明细（threshold_config=`{call}|{ratio}`，actual_value=`{total}|{pct}`）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyDifareaHighFreq.py:main`。

#### 4.2.8 viStrategySilentCallBurst.py — 沉默号码话务突增（策略编号 "11"）

- **策略含义**：长期不活跃的号码突然大量外呼。
- **数据来源**：`vi_strategy.strategy_silent_call_burst`（stat_cycle_second、backtrack_cycle_day、call_threshold、control_valid_second、effect_time）；沉默号码来自 `vi_strategy.active_phone_tab`（`DATEDIFF(NOW(), active_time) > backtrack_cycle_day`，由 ActivePhone 维护）；话单 StarRocks（`caller_number IN (...)`，每批 500）。
- **判定条件与阈值来源**：窗口 `GREATEST(now−stat_cycle_second, 今天 00:00)`，时长>0；`cnt > call_threshold` 命中。
- **命中后动作**：`SET inactive-{主叫号码}`，appendReason "28"，TTL=control_valid_second；首次命中写明细。
- **调度频率**：每分钟。
- 依赖：ActivePhone 每日 01:00 刷新 active_time；ActivePhone 若因缺失 `apps.email` 无法运行，沉默判定会基于过期数据（见 §12）。
- 代码位置：`viStrategySilentCallBurst.py:main`。

#### 4.2.9 viStrategyIdleHighFreq.py — 闲时高频外呼（策略编号 "12"）

- **策略含义**：当天接通量大且大部分发生在“闲时”时段的号码。
- **数据来源**：`vi_strategy.strategy_idle_high_frequency`（stat_cycle_second、call_threshold、idle_time_range（如 `00:00-08:00,18:00-24:00`）、idle_ratio_threshold、control_valid_second、effect_time，`status=1 AND effect_object=1`）；话单 StarRocks。无 is_main。
- **判定条件与阈值来源**：仅支持 `stat_cycle_second=86400`（当天），其他跳过；`parse_idle_hours` 把时段解析成小时集合（只取小时，分钟被忽略；不支持跨午夜）；SQL 统计 total_calls 与 `HOUR(handle_start_time) IN (闲时小时)` 的 idle_calls（时长>0）；`total_calls > call_threshold AND idle_calls/total_calls > idle_ratio_threshold` 命中（比例按小数比较，**未 ×100**，与 DifareaHighFreq 口径不同）。
- **命中后动作**：`SET idle-highfreq-{主叫号码}`，appendReason "29"，TTL=control_valid_second；首次命中写明细（threshold_config 只记 call_threshold，actual_value 只记 total_calls）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyIdleHighFreq.py:main`、`parse_idle_hours`。

#### 4.2.10 viStrategyShortHighfreqBurst.py — 短时高频突增（策略编号 "13"）

- **策略含义**：短时间窗口内接通量超过阈值，且超过该号码历史日均。
- **数据来源**：`vi_strategy.strategy_short_highfreq_burst`（stat_cycle_second、call_threshold、backtrack_cycle_day、avg_history_call_count、control_valid_second、effect_time）；当天话单 StarRocks；历史话单 `vi_bill_history.bill_history`（StarRocks）。
- **判定条件与阈值来源**：
  - 当前窗口 `[max(now−stat_cycle_second, 今天 00:00), now]`，按主叫 `HAVING COUNT(*) > call_threshold`。
  - 历史均值：`avg_history_call_count > 0` 时直接用配置值；否则从 bill_history 统计 `[now−backtrack_cycle_day, 今天 00:00)` 内每天通话数的平均（仅统计有通话的天）。
  - `total_calls > call_threshold AND total_calls > avg_history` 命中；无历史记录时 avg=0，退化为单阈值判定。
  - 注意：比较的是“当前短窗口通话数”与“历史**日**均”，口径不一致（待确认是否为业务意图）。
- **命中后动作**：`SET short-highfreq-{主叫号码}`，appendReason "30"，TTL=control_valid_second；首次命中写明细（threshold_config 含 call_threshold、backtrack_day、avg_history）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyShortHighfreqBurst.py:main`。

#### 4.2.11 viStrategyShortLongcallBurst.py — 短时长通话突增（策略编号 "14"）

- **策略含义**：短时间窗口内接通量大且其中长通话数多的号码。
- **数据来源**：`vi_strategy.strategy_short_longcall_burst`（stat_cycle_second、short_call_define_second、total_call_threshold、long_call_threshold、control_valid_second、effect_time）；话单 StarRocks。
- **判定条件与阈值来源**：窗口 `[max(now−stat_cycle_second, 今天 00:00), now]`，时长>0；`long_calls = SUM(call_duration_s > short_call_define_second)`；`total_calls > total_call_threshold AND long_calls > long_call_threshold` 命中。字段名 `short_call_define_second` 实际被用作“长通话”秒数下限（命名与用法不符，待确认）。
- **命中后动作**：`SET short-longcall-{主叫号码}`，appendReason "31"，TTL=control_valid_second；首次命中写明细（threshold_config=`total:..,long:..`）。
- **调度频率**：每分钟。
- 代码位置：`viStrategyShortLongcallBurst.py:main`。

#### 4.2.12 viStrategySilentDifareaLongcall.py — 沉默号码外地长通话突增（策略编号 "15"）

- **策略含义**：沉默号码在报备地之外突然大量长通话外呼。
- **数据来源**：`vi_strategy.strategy_silent_difarea_longcall_burst`（effect_area、stat_cycle_second、call_threshold、cross_area_ratio_threshold、backtrack_cycle_day、long_call_def_second、long_call_threshold、control_valid_second、effect_time）；沉默号码 `vi_strategy.active_phone_tab`（`customer_id=…` 且 `DATEDIFF(CURRENT_DATE(), active_time) > backtrack_cycle_day`）；话单 StarRocks（IN 列表每批 200）。
- **判定条件与阈值来源**：窗口同上；effect_area=1 比较省份字段、否则比较地市字段；SQL `HAVING` 同时要求 `COUNT(*) > call_threshold`、`长通话数(时长>long_call_def_second) > long_call_threshold`、`外地占比% > cross_area_ratio_threshold×100`，Python 侧再校验一次。
- **命中后动作**：`SET inactive-difarea-longcall-{主叫号码}`，appendReason "32"，TTL=control_valid_second；首次命中写明细。
- **调度频率**：每分钟。
- 代码位置：`viStrategySilentDifareaLongcall.py:main`。

#### 4.2.13 viStrategyDisabledRedisClear.py — 策略停用清理

- **策略含义**：策略被停用（status=0）后，删除其残留的 Redis 管控 key。
- **数据来源**：各策略表 `status=0` 记录；`STRATEGY_MAPPING`（老策略）：strategy_high_frequency→`highfreq-`（号码型）、strategy_remote_range→`difarea-`（号码型）、strategy_offwork_call→`offtime-`（企业型）、strategy_business_threshold→`threshold-`（企业型）、strategy_business_range→`bizrange-`（号码型）、strategy_long_call→`longcall-`（号码型）；`NEW_STRATEGY_MAPPING`：strategy_difarea_high_frequency→[`province-highfreq-`,`city-highfreq-`]、strategy_silent_call_burst→[`inactive-`]、strategy_idle_high_frequency→[`idle-highfreq-`]、strategy_short_longcall_burst→[`short-longcall-`]、strategy_short_highfreq_burst→[`short-highfreq-`]、strategy_silent_difarea_longcall_burst→[`inactive-difarea-longcall-`]。Redis `SCAN match 前缀* count 1000`；当天话单表（兜底反查）。
- **判定条件**：老策略：企业型 key 从 key 中取 cid、号码型从 value 取 `customerId` 与 `isMain`（缺失按 0）；value 解析失败时按主叫反查当日话单得到 cid。`(isMain==0 且 cid ∈ 停用独立策略集合) 或 (isMain==1 且 cid ∈ 停用主账户策略展开集合)` 时删除。新策略：value 无 isMain，cid ∈ 停用集合即删除。扫描 `inactive-` 时排除 `inactive-difarea-longcall-`。
- **命中后动作**：`DEL key`。不写明细。
- **调度频率**：每分钟。
- 代码位置：`viStrategyDisabledRedisClear.py:main`、`get_disabled_cid_by_table`、`get_disabled_cids_simple`。

#### 4.2.14 viStrategyBillDetail.py — 非工作时间与企业外呼门限明细入库

- **策略含义**：Offwork/BusinessThreshold 只同步配置不写明细，本脚本根据实际被标记的话单补录管控明细。
- **数据来源**：上一个小时的话单中 `append_reason IN (13, 20)` 的记录；配置 `strategy_business_threshold`、`strategy_offwork_call`（`status=1`，按 customer 与 app_id 建索引）；已存在明细 `mid_bussops.call_control_detail`（当天、strategy_name IN ('3','4')）用于去重。
- **判定条件**：去重窗口：门限 `threshold:{cid}:{epoch//cycle*cycle}`；非工作时间 `offwork:{cid}:{时段序号}:{日期}`；同一窗口只写一条。缺配置时使用默认 `DEFAULT_STAT_CYCLE=86400`、`DEFAULT_TIME_RANGE`。
- **命中后动作**：INSERT call_control_detail，strategy_name "4"/"3"，control_subjects 为空，control_command 按映射，threshold_config 来自配置。不写 Redis。
- **调度频率**：每小时第 5 分钟（`5 * * * *`）。
- 代码位置：`viStrategyBillDetail.py:main`。

#### 4.2.15 viStrategyActivePhone.py — 号码活跃更新

- **策略含义**：维护报备号码的最近活跃时间，供“沉默号码”类策略使用。
- **数据来源**：`user_qc.biz_num_auth`（match_type=1）；首次运行（不存在 active_time > online_time 的行）读 `vi_bill_history.bill_history`，否则读昨天的日表；可传参 `YYYY-MM-DD`。
- **判定/动作**：先将号码 upsert 到 `vi_strategy.active_phone_tab`（biz_number、customer_id、online_time，`ON DUPLICATE KEY UPDATE` customer_id、online_time）；再按 biz_number 分批（500）取 `MAX(handle_start_time)`，仅当更大时更新 active_time；结束发邮件（`apps.email.send_email`）。不写 Redis，不封禁。
- **调度频率**：每天 01:00。
- 代码位置：`viStrategyActivePhone.py:main`。

#### 4.2.16 viStrategyDailyStatistics.py — 日统计

- **策略含义**：按昨日话单的拦截原因码汇总各策略触发情况。
- **数据来源**：`vi_bill.bill_{昨日}` 按 append_reason 分组 `COUNT(DISTINCT 主叫)`、`COUNT(*)`。
- **映射**：APPEND_REASON_MAP：5→1、21→2、13→3、20→4、22/23/24→5（维度 5/6/7）、25→8、26→9、27→10、28→11、29→12、30→13、31→14、32→15。
- **动作**：INSERT `mid_bussops.call_control_statistics`（id、mobile/channel/company/customer、stat_time、strategy_name、control_dimension、trigger_phone_num、trigger_count、create/update_time），发送日报邮件。**防重检查被注释**，重跑会重复写入。不写 Redis。
- **调度频率**：每天 01:00。
- 代码位置：`viStrategyDailyStatistics.py:main`。

### 4.3 其他脚本

| 脚本 | 作用 | 是否在 crontab |
| --- | --- | --- |
| `ck_check.py` | 解析日期参数（缺省昨日），查询 `call_control_statistics` 当日行数；定义了 `check()`（防重 + 从 detail 聚合）但 `__main__` 未调用 | 否 |
| `dataCheck.py` | 针对 strategy_remote_range 逐条打印某硬编码测试号码的地市聚合，用于人工核对 | 否 |
| `dailyRange.py` | 从 `call_control_detail` 按企业/策略/维度聚合（`COUNT(DISTINCT control_subjects)`、`COUNT(*)`）写入 `call_control_statistics`，有防重（当日已有数据即退出） | 否 |

## 5. 对外接口表

本仓库不提供 HTTP/RPC 接口。对外“接口”即 Redis key 与 CK 表（见 §8），供策略管控服务/报表使用。

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| — | 无 | — | — | — | 仅命令行参数：`viStrategyActivePhone.py [YYYY-MM-DD]`、`ck_check.py`/`dailyRange.py [YYYY-MM-DD]` |

## 6. 消息表

| 方向 | 中间件 | topic/queue | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| — | 无 | — | — | — | 本仓库不使用 MQ |

## 7. 依赖调用（出站）

| 目标 | 方式 | 用途 | 位置 |
| --- | --- | --- | --- |
| MySQL（vi_strategy、user_qc、mid_auth、mid_bussops 等） | SQLAlchemy | 读策略/企业/号码、写 active_phone_tab；LongCall 读话单 | `apps/database.py` |
| StarRocks（vi_bill、vi_bill_history） | SQLAlchemy（`ckSelectForSQL`） | 读话单 | `apps/clickhouse.py` |
| ClickHouse（mid_bussops） | clickhouse_driver 原生协议 | 写/读 call_control_detail、call_control_statistics | `apps/clickhouse.py:ckAddObjects` |
| Redis（`VI_STRATEGY_REDIS`） | redis-py | 写/删管控 key | 各策略脚本 |
| 邮件服务 | HTTP（`apps/general.py:get_mail`，硬编码内部地址）/ `apps.email.send_email`（缺失） | 日报/失败告警 | DailyStatistics、ActivePhone |
| 统一运维监控 | `apps/general.py:uniops_monitor`（配置项 `UNIOPS_MONITOR`） | 监控上报（调用方 待确认） | — |

## 8. 数据

### 8.1 MySQL

| 库 | 表 | 读/写 | 用途 |
| --- | --- | --- | --- |
| vi_strategy | strategy_high_frequency、strategy_long_call、strategy_offwork_call、strategy_business_threshold、strategy_remote_range、strategy_business_range、strategy_difarea_high_frequency、strategy_silent_call_burst、strategy_idle_high_frequency、strategy_short_highfreq_burst、strategy_short_longcall_burst、strategy_silent_difarea_longcall_burst | 读 | 策略配置（通用列：customer_id、status、effect_object、effect_time、control_command、control_valid_second、append_reason、update_time；老策略另有 is_main、app_id） |
| vi_strategy | active_phone_tab（biz_number、customer_id、online_time、active_time） | 读写 | 号码活跃时间 |
| user_qc | customer_tab（status、is_call、app_id） | 读 | 企业开关、主账户映射 |
| user_qc | biz_num_auth（match_type=1） | 读 | 报备号码 |
| vi_bill（MySQL） | bill_YYYY_MM_DD | 读 | 仅 LongCall（USE_MYSQL=True） |
| mid_auth | 中间号字典表 | 读 | `apps/general.py` 字典函数 |

### 8.2 StarRocks / ClickHouse

| 库.表 | 读/写 | 用途 |
| --- | --- | --- |
| vi_bill.bill_YYYY_MM_DD | 读 | 当天/昨天话单（handle_start_time、call_duration_s、caller_number、callee_number、call_out_area_code、call_out_province_id、biz_report_area_code、biz_report_province_id、finish_state、append_reason 等） |
| vi_bill_history.bill_history | 读 | 历史话单（ShortHighfreqBurst、ActivePhone 首次） |
| mid_bussops.call_control_detail | 写/读 | 管控触发明细 |
| mid_bussops.call_control_statistics | 写/读 | 管控日统计 |

### 8.3 Redis key（库：配置项 `VI_STRATEGY_REDIS`，均为 String/JSON）

| key 模式 | 写入脚本 | 粒度 | TTL | appendReason |
| --- | --- | --- | --- | --- |
| `highfreq-{主叫}` | HighFrequency | 号码 | control_valid_second | 5 |
| `difarea-{主叫}` | RemoteRange | 号码 | 同上 | 21 |
| `offtime-{customer_id}` | Offwork | 企业 | 无 | 13 |
| `threshold-{customer_id}` | BusinessThreshold | 企业 | control_valid_second | 20 |
| `bizrange-{主叫}` | BusinessRange | 号码 | 同上 | 22/23/24 |
| `longcall-{主叫}` | LongCall | 号码 | 同上 | 25 |
| `province-highfreq-{主叫}` / `city-highfreq-{主叫}` | DifareaHighFreq | 号码 | 同上 | 26 / 27 |
| `inactive-{主叫}` | SilentCallBurst | 号码 | 同上 | 28 |
| `idle-highfreq-{主叫}` | IdleHighFreq | 号码 | 同上 | 29 |
| `short-highfreq-{主叫}` | ShortHighfreqBurst | 号码 | 同上 | 30 |
| `short-longcall-{主叫}` | ShortLongcallBurst | 号码 | 同上 | 31 |
| `inactive-difarea-longcall-{主叫}` | SilentDifareaLongcall | 号码 | 同上 | 32 |

示例 key：`highfreq-700xxxx0001`（虚构）。

### 8.4 文件

`apps/timeUtil.py` 读写上次执行日期文件（当前策略脚本是否使用 待确认）；日志目录 `/var/log/vi-cront-jobs/`。

## 9. 状态与枚举

| 枚举 | 取值 | 来源 |
| --- | --- | --- |
| 策略表 status | 1 生效、0 停用 | 各脚本 SQL、DisabledRedisClear |
| effect_object | 1（含义 待确认，推测“企业”） | 各脚本 SQL |
| is_main | 0 独立策略、1 主账户策略 | `apps/general.py:resolve_strategies_with_main` |
| 策略表 control_command / Redis opType | 0 拦截、1 放通、2 告警 | 代码注释 |
| 明细表 control_command | 0 拦截、1 告警、2 放通（`{0:"0",1:"2",2:"1"}`） | 各脚本 `_cmd_map` |
| stat_cycle_second | 86400 自然日、3600 自然小时（老策略）；新策略为滑动秒数窗口（截断到当天 0 点） | 各脚本 |
| effect_area | 1 省、2 市 | DifareaHighFreq、SilentDifareaLongcall |
| BusinessRange dimension | 5 盲呼离散度、6 短秒率、7 接通率 | `viStrategyBusinessRange.py` |
| strategy_name（明细/统计） | 1 短时高频、2 异地大范围、3 非工作时间、4 超业务门限、5 超业务范围、8 长通话、9 外省大范围高频、10 外地市大范围高频、11 沉默号码话务突增、12 闲时高频、13 短时高频突增、14 短时长通话突增、15 沉默号码外地长通话突增 | `viStrategyDailyStatistics.py:STRATEGY_NAME_MAP` |
| append_reason（话单/Redis） | 5、13、20、21、22、23、24、25、26、27、28、29、30、31、32（对应关系见 §8.3） | 各脚本、`APPEND_REASON_MAP` |
| customer_tab | status=1 启用；is_call=1 开启话务质检 | `apps/general.py` |

## 10. 定时任务

来源：`README.md` crontab（每行均为 `export CRONT_ENV=test && /home/production/vi-cront-jobs/myenv/bin/python <脚本>`）。

| 脚本 | cron | README 说明 |
| --- | --- | --- |
| viStrategyBusinessRange.py | `* * * * *` | 超业务范围 |
| viStrategyBusinessThreshold.py | `* * * * *` | 企业外呼门限 |
| viStrategyHighFrequency.py | `* * * * *` | 短时高频 |
| viStrategyLongCall.py | `* * * * *` | 长通话管控 |
| viStrategyOffwork.py | `*/10 * * * *` | 非工作时间管控 |
| viStrategyRemoteRange.py | `* * * * *` | 异地外呼管控 |
| viStrategyDailyStatistics.py | `0 1 * * *` | 日统计 |
| viStrategyDisabledRedisClear.py | `* * * * *` | 策略停用清理 |
| viStrategyBillDetail.py | `5 * * * *` | 非工作时间和企业外呼门限明细入库 |
| viStrategyActivePhone.py | `0 1 * * *` | 号码活跃更新 |
| viStrategyDifareaHighFreq.py | `* * * * *` | 跨区域大范围高频外呼 |
| viStrategySilentCallBurst.py | `* * * * *` | 沉默号码话务突增 |
| viStrategyIdleHighFreq.py | `* * * * *` | 闲时高频外呼 |
| viStrategyShortLongcallBurst.py | `* * * * *` | 短时长通话突增 |
| viStrategyShortHighfreqBurst.py | `* * * * *` | 短时高频突增 |
| viStrategySilentDifareaLongcall.py | `* * * * *` | 沉默号码外地长通话突增 |

`ck_check.py`、`dataCheck.py`、`dailyRange.py` 不在 crontab。crontab 没有加 `flock` 之类的互斥，脚本执行超过 1 分钟时会并发重叠。

## 11. 关键配置项（仅列名称）

| 分类 | 配置项 |
| --- | --- |
| MySQL | `SQLALCHEMY_DATABASE_URI`、`SQLALCHEMY_BINDS`（mid_auth、mid_strategy、south_bill、north_bill、vi_strategy、vi_bill、user_qc、mid_bussops…） |
| StarRocks/CK 查询 | `CK_DATABASE_URI`、`CK_BINDS` |
| CK 原生写 | `CLICKHOUSE_BUSSOPS`、`CLICKHOUSE_STRATEGY`、`CLICKHOUSE_BUSSOPS_ZYC`、`CLICKHOUSE_YTXDB`、`CLICKHOUSE_BILL_HISTORY` |
| Redis | `VI_STRATEGY_REDIS`（HOST、PORT、PASSWORD、DB） |
| 监控/日志 | `UNIOPS_MONITOR`、`LOGGING_PATH` |
| 环境变量 | `CRONT_ENV`；`config.yaml` 中 `{{变量}}` 由环境变量渲染（具体变量名 待确认） |
| 脚本内开关 | `USE_MYSQL`（LongCall 为 True，其余 False）、`SCRIPT_TAG`（定义但未使用） |

## 12. 坑与特殊逻辑

1. **`apps.email` 模块缺失**：`viStrategyDailyStatistics.py`、`viStrategyActivePhone.py` 顶部 `from apps.email import send_email`，仓库中无此模块，按仓库现状运行会 ImportError。ActivePhone 不运行 → `active_phone_tab.active_time` 不更新 → SilentCallBurst、SilentDifareaLongcall 的沉默号码判定失真（是否线上单独部署该模块 待确认）。
2. **README crontab 使用 `CRONT_ENV=test`**，但路径在 `/home/production/`；若照抄到生产会加载 `config.test/`（待确认生产实际 crontab）。
3. **LongCall `USE_MYSQL=True`**：生产走 MySQL 查话单，与其他脚本的 StarRocks 不一致；注释称 True 为“MySQL 测试”，疑似测试开关遗留。且未过滤 `call_duration_s>0`，以 `SELECT DISTINCT` 拉取当天全部话单到 Python，数据量大时有性能风险。
4. **“今天更新过”导致全天续期**：`is_strategy_updated_today` 为真时每分钟都会覆盖 SET 并重置 TTL，命中号码在当天会被持续管控，超出 control_valid_second 语义。
5. **号码型 key 不含企业 ID**：`{前缀}-{主叫}` 被多个企业共用时互相覆盖；DisabledRedisClear 仅按最后写入者的 customerId 判定是否清理。
6. **BusinessRange 三个维度共用 `bizrange-` key**，后命中的维度覆盖 appendReason；SQL 硬编码 `HAVING COUNT(*) > 50`，低于 50 通的号码永不判定；话单表名缺 `vi_bill.` 前缀。
7. **effect_time 为空时崩溃**：Offwork、BusinessThreshold 直接 `effect_time > now`，None 会抛 TypeError，导致当次所有策略都不同步。
8. **`control_valid_second` 为 NULL 时崩溃**：各脚本 `int(strategy.get("control_valid_second", 0))`，列值为 None 时 `int(None)` 抛 TypeError（key 存在但值为 NULL 时 get 的默认值不生效）。
9. **BillDetail `DEFAULT_TIME_RANGE` 使用非 ASCII 连字符**（`22:00‑24:00,00:00‑8:00`），`split('-')` 解析失败（仅在缺配置时触发）。
10. **appendReason 硬编码**，策略表 `append_reason` 列被读取但未使用；改原因码需改代码。
11. **明细 id 可能冲突**：`毫秒时间戳+4 位序号`，`SCRIPT_TAG` 未参与，多个脚本同一分钟并发执行时同一毫秒可能生成相同 id。
12. **明细写入失败不回滚 Redis**：Redis 已写、明细写失败只记日志；下次运行 key 已存在不再写明细，导致统计缺失。
13. **DailyStatistics 防重被注释**，重跑同一天会重复写统计；`dailyRange.py` 是有防重的另一实现，但口径不同（detail 维度 vs 话单 append_reason 维度）。
14. **IdleHighFreq 比例口径**：`idle_ratio_threshold` 按小数比较（0.4），DifareaHighFreq/SilentDifareaLongcall 按 ×100 百分比比较，配置时需注意同为 varchar 小数；IdleHighFreq 闲时时段只精确到小时。
15. **ShortHighfreqBurst 口径**：短窗口次数与历史“日均”比较，日均只统计有通话的天。
16. **ShortLongcallBurst 字段命名**：`short_call_define_second` 被当作长通话下限使用。
17. **DisabledRedisClear 只处理 status=0**：策略行被物理删除时 key 不会清理；同一企业同时存在 status=0 与 status=1 记录时，key 可能被反复删除又重建（待确认是否存在此类数据）。
18. **无并发保护**：cron 无锁，单次执行超过 1 分钟时会与下一次重叠，重复写明细的风险增加。
19. **SQL 字符串拼接**：customer_id、阈值等直接 f-string 拼接（数据来自内部配置表，风险较低）；SilentDifareaLongcall 对号码做了单引号转义。
20. **`dataCheck.py` 硬编码真实格式号码**，属于调试代码遗留，违反“代码中不得写入真实手机号”的红线，建议删除或脱敏（本报告不写出该号码）。

## 13. 与其它仓库的关系

| 仓库/服务 | 关系 | 证据 |
| --- | --- | --- |
| vi-billcenter / vi-pusher（话单） | 话单 `vi_bill.bill_YYYY_MM_DD`、`vi_bill_history.bill_history` 的生产方（Bill 实体含 append_reason 等字段），本仓库只读 | `vi-billcenter-master/.../BillDao.java`、`vi-pusher-master/.../BillDao.java` |
| vi-as-master | 呼叫接续时请求 `{strategyHost}/strategy` 获取 opType/appendReason，并写入话单 append_reason；是本仓库管控结果的最终执行方 | `vi-as-master/src/main/java/com/cmic/business/query/httpcallback/AuditStrategyCallBack.java`、`BusinessEvent.java` |
| 策略管控服务（strategyHost） | 推断读取本仓库写入的 Redis key 并实时计数 offtime/threshold（不在资产中，待确认；参考资产文档《专线质检策略管控服务 redis数据结构》） | — |
| qa-server-master | 共享 user_qc.customer_tab、biz_num_auth；qa-server 的 `STRATEGY_CONFIG_URL` 可能指向同一策略管控服务（待确认）；无直接调用 | 两仓库 SQL |
| 策略配置管理端 | 写入 vi_strategy.strategy_* 表（未知仓库，待确认） | — |
| 报表/运营平台 | 读 mid_bussops.call_control_detail / call_control_statistics（待确认） | — |

## 14. 待确认问题清单

1. 生产 crontab 的 `CRONT_ENV` 实际取值？`config.south`/`config.north` 是否有部署？
2. `apps/email.py` 是否在线上单独存在？ActivePhone、DailyStatistics 是否正常运行？
3. LongCall 的 `USE_MYSQL=True` 是否为有意设计？
4. 读取 Redis 管控 key 的策略管控服务是哪个仓库？它如何使用 offtime/threshold 的 threshold/curfew 进行实时计数？threshold 的统计周期从哪里获得？
5. 号码型 key 不区分企业是否符合业务预期（一个号码只属于一个企业？）。
6. “策略今天更新过即全天续期”是否为预期行为？
7. effect_object 各取值含义？
8. DailyStatistics 与 dailyRange 哪个是正式日统计口径？
9. `mid_bussops` 是 ClickHouse 还是 StarRocks？（写入用 clickhouse_driver 原生协议，查询 SQL 使用 `toDate` 等 CK 函数，推断为 ClickHouse）
10. 策略表 `append_reason` 列的用途？是否应替代硬编码原因码？
11. ShortHighfreqBurst 中“短窗口通话数 vs 历史日均”的比较口径是否正确？
12. `apps/timeUtil.py`、`apps/sftp.py`、`apps/aes.py`、`apps/models.py` 是否可以清理？

CONFLICT: `vi-cront-jobs-master/README.md`（crontab `CRONT_ENV=test` + 生产路径） vs `vi-cront-jobs-master/config.py`: 生产缺省应为 `production` 加载 `config/`，README 示例会加载 `config.test/`。

CONFLICT: `vi-cront-jobs-master/viStrategyLongCall.py`（注释“True=MySQL测试 False=StarRocks正式”） vs 同文件 `USE_MYSQL = True`：代码处于“测试”模式。

CONFLICT: `vi-cront-jobs-master/viStrategy*.py`（策略表读取 `append_reason` 列） vs 同文件 Redis value：appendReason 硬编码，配置列不生效。
