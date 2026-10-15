---
id: mn-traffic-control-codes
title: 话务管控 - 编码对照表（append_reason / 管控指令 / 放音码）
domain: mid_number
kind: stable
audience: [developer, qa, ops, architect]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-traffic-control-overview, mn-traffic-control-strategies]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/pojo/BillAppendReasonEnum.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/PlayCodeService.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/util/SessionLimitUtil.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/state/FinishState.java
  - vi-assets/vi-cront-jobs-master/viStrategyDailyStatistics.py
  - 设计文档《话单DDL》《二期话单DDL》《话务管控策略表DDL》《专线质检策略管控服务 redis数据结构》
---

# 话务管控 - 编码对照表

同一个“原因 / 指令”在不同位置有多套编码。**以运行代码为准**：话单 `append_reason` 实际取值 = vi-as `BillAppendReasonEnum` 或 strategy-service 返回值；vi-cront-jobs 写 Redis 的原因码与 vi-as 枚举对齐（5、13、20~25），策略表 `append_reason` 列不生效。

## 1. append_reason 并排对照

列说明：

- **话单 DDL**：《话单DDL》`bill_new`/`bill_template.append_reason` 注释（tinyint，默认 -1）。
- **策略表**：《话务管控策略表DDL》各策略表 `append_reason` 列注释（代码读取但不使用）。
- **Redis / cront-jobs**：《专线质检策略管控服务 redis数据结构》`action.appendReason`，与 `vi-assets/vi-cront-jobs-master/viStrategy*.py` 硬编码值一致。
- **vi-as 枚举**：`vi-assets/vi-as-master/src/main/java/com/cmic/pojo/BillAppendReasonEnum.java`。
- **统计 strategy_name**：`viStrategyDailyStatistics.py:APPEND_REASON_MAP` 映射到明细 / 统计表的策略编号（括号内为 control_dimension）。

| 值 | 话单 DDL | 策略表 | Redis / cront-jobs | vi-as 枚举 | 统计 strategy_name |
| --- | --- | --- | --- | --- | --- |
| -1 | 默认值（未写原因） | — | — | — | — |
| 0 | — | business_range 默认值（未定义） | — | — | — |
| 1 | 呼转拦截通话（注释称“默认值”） | 呼叫频次异常管控 | — | 呼转拦截通话 | — |
| 2 | 主叫黑名单拦截通话 | 异地外呼超限管控 | — | 主叫黑名单拦截通话 | — |
| 3 | 被叫黑名单拦截通话 | 呼叫时间段管控 | — | 被叫黑名单拦截通话 | — |
| 4 | 被叫非白名单拦截通话 | 企业最大呼叫数管控 | — | **主叫**非白名单拦截通话 | — |
| 5 | 呼叫频次异常拦截通话 | 超业务范围-盲呼外呼管控 | 呼叫频次异常管控（highfreq） | 呼叫频次异常管控（opType=0 无原因时的默认值） | 1 |
| 6 | 虚商被叫拦截通话 | 超业务范围-短秒率外呼管控 | — | 虚商被叫拦截通话 | — |
| 7 | — | 超业务范围-接通率外呼管控 | — | 红名单管控 | — |
| 8 | — | 长通话管控 | — | 策略修改（放行但改放音 / 号显） | — |
| 9 | — | — | — | 最大会话数拦截 | — |
| 10 | — | — | — | 浙江机伶拦截 | — |
| 11 | — | — | — | 大网高频拦截 | — |
| 12 | — | — | — | 大网反诈拦截 | — |
| 13 | — | — | 呼叫时间段管控（offtime） | 呼叫时间段拦截 | 3 |
| 14 | — | — | — | 实名校验拦截 | — |
| 15 | — | — | — | 企业账户无效 | — |
| 16 | — | — | — | 非手机号拦截（调用处已注释） | — |
| 17 | — | — | — | 转接查询参数非法拦截 | — |
| 18 | — | — | — | 中间号串打次数过多（调用处已注释） | — |
| 19 | — | — | — | 被叫绿名单拦截通话 | — |
| 20 | — | — | 企业最大呼叫数管控（threshold） | 企业最大呼叫数管控 | 4 |
| 21 | — | — | 异地外呼超限管控（difarea） | 异地外呼超限管控 | 2 |
| 22 | — | — | 超业务范围-盲呼（bizrange，dim 5） | 超业务范围-盲呼外呼管控 | 5（5） |
| 23 | — | — | 超业务范围-短秒率（bizrange，dim 6） | 超业务范围-短秒率外呼管控 | 5（6） |
| 24 | — | — | 超业务范围-接通率（bizrange，dim 7） | 超业务范围-接通率外呼管控 | 5（7） |
| 25 | — | — | 长通话管控（longcall） | 长通话管控 | 8 |
| 26 | — | — | 外省大范围高频外呼（province-highfreq） | **无** | 9 |
| 27 | — | — | 外地市大范围高频外呼（city-highfreq） | **无** | 10 |
| 28 | — | — | 沉默号码话务突增（inactive） | **无** | 11 |
| 29 | — | — | 闲时高频外呼（idle-highfreq） | **无** | 12 |
| 30 | — | — | 短时高频突增（short-highfreq） | **无** | 13 |
| 31 | — | — | 短时长通话突增（short-longcall） | **无** | 14 |
| 32 | — | — | 沉默号码外地长通话突增（inactive-difarea-longcall） | **无** | 15 |

vi-as 写 append_reason 的规则（`SessionLimitUtil.auditLimitIntercept`、`BusinessEvent.doStrategyQuery`、`ControllerAgent.start`）：

| strategy-service 响应 | 话单 append_reason |
| --- | --- |
| opType="0"，appendReason 非空 | `Integer.parseInt(appendReason)`（26~32 也能写入，枚举缺失不影响） |
| opType="0"，appendReason 为空 | 5 |
| opType="1"，appendReason 为空，但返回了放音码 / 号显 / 呼转字段 | 8（策略修改） |
| 其他 opType（含 2 告警），appendReason 非空 | appendReason（**放行也会写原因码**） |
| HTTP 非 200 / 超时 / 异常 / 策略查询被跳过 | 不改（保持 vi-as 其他逻辑设置的值或默认） |
| 企业或客户 status≠1（策略查询之前） | 15，finish_state=99 |

## 2. Redis key 与原因码

| key 模式 | 粒度 | appendReason | 统计 strategy_name | 写入脚本 |
| --- | --- | --- | --- | --- |
| `highfreq-{caller}` | 号码 | 5 | 1 | viStrategyHighFrequency.py |
| `difarea-{caller}` | 号码 | 21 | 2 | viStrategyRemoteRange.py |
| `offtime-{customerId}` | 企业 | 13 | 3 | viStrategyOffwork.py |
| `threshold-{customerId}` | 企业 | 20 | 4 | viStrategyBusinessThreshold.py |
| `bizrange-{caller}` | 号码 | 22/23/24 | 5 | viStrategyBusinessRange.py |
| `longcall-{caller}` | 号码 | 25 | 8 | viStrategyLongCall.py |
| `province-highfreq-{caller}` | 号码 | 26 | 9 | viStrategyDifareaHighFreq.py |
| `city-highfreq-{caller}` | 号码 | 27 | 10 | viStrategyDifareaHighFreq.py |
| `inactive-{caller}` | 号码 | 28 | 11 | viStrategySilentCallBurst.py |
| `idle-highfreq-{caller}` | 号码 | 29 | 12 | viStrategyIdleHighFreq.py |
| `short-highfreq-{caller}` | 号码 | 30 | 13 | viStrategyShortHighfreqBurst.py |
| `short-longcall-{caller}` | 号码 | 31 | 14 | viStrategyShortLongcallBurst.py |
| `inactive-difarea-longcall-{caller}` | 号码 | 32 | 15 | viStrategySilentDifareaLongcall.py |

策略编号 6、7 不存在（超业务范围的维度 5/6/7 复用 strategy_name=5，放在 control_dimension）。

## 3. 管控指令：opType / control_command / control_action

| 位置 | 字段 | 取值 | 来源 |
| --- | --- | --- | --- |
| 策略表 | `control_command` tinyint，默认 2 | 0 拦截、1 放通、2 告警 | 《话务管控策略表DDL》 |
| Redis value | `action.opType`（字符串） | 同策略表原值：0 拦截、1 放通、2 告警 | cront-jobs 各脚本 `str(control_command)` |
| strategy-service 响应 | `opType`（字符串） | vi-as 只判断 `"0"` = 拦截；其余一律放行 | `SessionLimitUtil.auditLimitIntercept` |
| vi-as 内部 | `Optype` 枚举 / `CommonConstant.OPTYPE.DISCONNECT` | 0 CloseOptype → `ErrorController`（放音后挂断）；1 ConnectOptype → `BaseController`（正常接续），startMode=1 时 → `B2BWithBillController` | `vi-assets/vi-as-master/src/main/java/com/cmic/pojo/Optype.java` |
| 明细表 | `call_control_detail.control_command` String | 代码：0 拦截、1 告警、2 放通（映射 `{0:"0",1:"2",2:"1"}`） | `_cmd_map`、`viStrategyBillDetail.py:CONTROL_COMMAND_MAP` |
| 明细表 DDL | 同上 | 文档注释：1 告警、2 挂断 | 《话务管控策略表DDL》 |
| 二期话单 | `bill_new.control_action` tinyint，默认 0 | 0 未设置、1 仅放音、2 仅阻断、3 先放音再阻断（无写入方） | 《二期话单DDL》 |
| qa-server | `ctrlAction`（来自 `user_qc.qa_strategy_config.control_action`） | 2 时清空 audio_code，其余含义待确认 | qa-server `apps/result_consumer.py:sub_result` |
| vi-as 话单 | `finish_state` | 策略拦截 = 22（`STRATEGY_HIJACKING`）；企业无效 = 99（`OTHER`） | `vi-assets/vi-as-master/src/main/java/com/cmic/state/FinishState.java` |

## 4. 放音码（vi-as `PlayCodeService`）

| 常量 | 值 | 文件注释中的提示语 | 管控相关用法 |
| --- | --- | --- | --- |
| `ERROR_NOTICE_URL` / `PLAYCODE_HIJACKING` | `global_100203` | 欢迎使用移动中间号服务，请使用正确的号码呼叫对方 | 主叫黑名单拦截（`BusinessEvent.blackCallerIntercept`）；opType=0 且无放音码时的缺省（`multiPlayCode2Url`，调用处已注释） |
| `CALLEE_LIMIT_TIMES_HIJACKING` | `global_100201` | 您拨打的电话无法接通 | **策略拦截（opType=0）初始放音码**、被叫限次、AB 次数超限 |
| `NO_PLAY` | `NO_PLAY` | 不放音，回 180 后 200ms 以 603 释放 | 最大会话数拦截（append_reason 9） |
| `CALL_FORWARD_HIJACKING` | `global_100305` | 被叫出现违规呼叫转移 | 呼转拦截（调用处已注释） |
| `IDENTITY_INTERCEPT` / `IDENTITY_EARLY` | `global_100307` / `global_100308` | 文件注释未列出 | 实名校验相关 |
| `PLAY_NOTICE_URL` / `FOWARD_NOTICE_URL` / `PLAY_COLLECT_URL` | `global_100000` / `global_100002` / `global_100300` | 正在为您转接 / 转接中 / 请输入四位分机号 | 与管控无关 |
| `TENG_XUN_ERROR_NOTICE_URL` | `/DIDI200` | — | 遗留 |

策略拦截时的放音顺序：`onDisconnect(..., CALLEE_LIMIT_TIMES_HIJACKING, STRATEGY_HIJACKING, SYSTEM_STOP)` 先设 `global_100201`，随后 **无条件** `setCallerPlayUrl(callNoPlayCode)` 覆盖——strategy-service 不返回 `callNoPlayCode` 时，主叫放音地址变成空串（实际表现待确认）。放行时 `callNoPlayCode` / `calledPlayCode` 非空才覆盖主 / 被叫放音。放音码格式：`;` 分隔主叫侧;被叫侧，`,` 分隔多段；`DuDu`、`Mute`、`TTS:*` 为特殊码。`PlayCodeService` 的 URL 转换函数在 `MediaService` 中调用处已注释，放音码原样交给 vi-media 解析（待确认）。

## 5. 不一致之处

CONFLICT: 《话单DDL》`append_reason` 1~6（5=呼叫频次异常拦截通话） vs 《话务管控策略表DDL》`append_reason` 1~8（1=呼叫频次异常管控、5=超业务范围-盲呼）：同一数值含义完全不同。
CONFLICT: 《话务管控策略表DDL》`append_reason` 1~8 vs `vi-assets/vi-cront-jobs-master/viStrategy*.py` 与《专线质检策略管控服务 redis数据结构》（5、13、20~32）：策略表列被读取但不使用，实际原因码硬编码。
CONFLICT: 《话单DDL》`append_reason` 4=被叫非白名单拦截通话 vs `vi-assets/vi-as-master/src/main/java/com/cmic/pojo/BillAppendReasonEnum.java` 4=主叫非白名单拦截通话。
CONFLICT: 《话单DDL》`append_reason` 只到 6 vs `BillAppendReasonEnum` 1~25 与 Redis 文档 26~32：话单 DDL 注释过期。
CONFLICT: 《专线质检策略管控服务 redis数据结构》appendReason 26~32 vs `BillAppendReasonEnum`（只到 25）：vi-as 能写入但枚举无描述，基于枚举的展示 / 报表会缺失。
CONFLICT: 《话务管控策略表DDL》`call_control_detail.control_command` 注释“1-告警 2-挂断” vs `vi-assets/vi-cront-jobs-master/viStrategyBillDetail.py:CONTROL_COMMAND_MAP`（0 拦截、1 告警、2 放通）：2 的含义相反。
CONFLICT: 策略表 / Redis `opType` 2=告警 vs `vi-assets/vi-as-master/src/main/java/com/cmic/util/SessionLimitUtil.java:auditLimitIntercept`：vi-as 不区分告警与放通，只要非 "0" 都放行（告警仅体现在话单 append_reason）。
CONFLICT: 《话务管控策略表DDL》`strategy_name` 注释只列 1、2、3、4、5、8 vs `viStrategyDailyStatistics.py:STRATEGY_NAME_MAP` 1~5、8~15。
CONFLICT: vi-as 分析报告（`global_100203` 为“策略拦截提示”） vs `SessionLimitUtil.auditLimitIntercept`：策略拦截使用 `global_100201`，再被 `callNoPlayCode` 覆盖。
CONFLICT: 《专线质检策略管控服务 redis数据结构》`city-highfreq` 样例 JSON 缺少开头 `{`，且样例 opType 多为 "1"（放通） vs 策略表默认 control_command=2（告警）：样例不可直接作为测试数据。

## 6. 待确认

1. strategy-service 是否原样透传 Redis 中的 `opType` / `appendReason`，还是另有映射；是否返回 `callNoPlayCode`。
2. 话单 `append_reason` 的统一字典由谁维护（建议以 `BillAppendReasonEnum` 为准并补齐 26~32）。
3. 二期 `control_action` 与 `opType` 的关系；qa-server `ctrlAction` 的取值字典。
4. 空放音码时 `ErrorController` 的实际表现。
