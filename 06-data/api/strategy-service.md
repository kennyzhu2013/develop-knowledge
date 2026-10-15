---
id: api-strategy-service
title: vi-as 查询话务管控策略服务
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - svc-vi-as
  - module-vi-as
  - svc-vi-cront-jobs
  - module-vi-cront-jobs
  - mn-traffic-control-overview
  - mn-traffic-control-codes
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/business/query/AuditStrategyQuery.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/util/SessionLimitUtil.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/pojo/AuditResponseEntity.java
---

# vi-as 查询话务管控策略服务

strategy-service（注册名 `com.cmic.vi.strategy`）的源码不在本次 9 个仓库。本文只记录 vi-as 发出的 HTTP 契约，以及 vi-cront-jobs 写入、设计文档描述的 Redis 结果如何被这次查询使用。策略服务内部如何读 Redis，待确认。

发现方式：ETCD 取一个节点，路径拼在节点地址后面。节点为空，或运行开关 `auditStrategyAllow` 为 0 时，vi-as 跳过查询并继续接续。

## 1. 调用

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/strategy` | vi-as | strategy-service | 见下表。HTTP/2，JSON | 调用方只认 HTTP 200；非 200 不拦截 | `vi-assets/vi-as-master/src/main/java/com/cmic/business/BusinessEvent.java:doStrategyQuery`；`vi-assets/vi-as-master/src/main/java/com/cmic/business/query/AuditStrategyQuery.java:query` |

| 项 | 调用方行为 |
| --- | --- |
| 协议 | `HttpClient.Version.HTTP_2` |
| 请求超时 | `AuditStrategyQuery` 默认 500 毫秒 |
| 连接超时 | 分析报告记为 300 毫秒，本次未在客户端构造处逐行复核，待确认 |
| 失败 | 超时、异常、HTTP 非 200：打日志，不设置管控结果，呼叫继续。是否故意放通，待确认 |
| 鉴权 | 调用处传入的 header 为空。策略服务是否另有网关鉴权，待确认 |

## 2. 请求字段

`BusinessEvent.initAuditSendJson` 实际发送：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| billId | 话单 id | 字符串 |
| callerNo | 主叫 | 示例 `700xxxx0001` |
| calleeNo | 被叫 | 示例 `138xxxx0001` |
| bizNo | 业务号码 | |
| companyId | 三级企业 id | 字符串 |
| customerId | 四级客户 id | 字符串 |
| callerProvinceId | 主叫省份 id | |
| calleeProvinceId | 被叫省份 id | |
| callerCityId | 主叫区号 | |
| calleeCityId | 被叫区号 | |
| handleStartTime | 处理开始时间 | `yyyy-MM-dd HH:mm:ss` |

## 3. 响应字段

HTTP 200 时 vi-as 读取字符串字段。没有业务 code 字段；策略服务的错误体格式不在本次源码中。

| 字段 | vi-as 行为 |
| --- | --- |
| opType | `"0"` 拒绝接续（FinishState 为策略拦截）。其他取值不在这里挂断 |
| appendReason | 有值则写入话单附加原因。opType 为 `"0"` 且本字段为空时，vi-as 改写为 5（呼叫频次异常拦截） |
| callNoPlayCode | opType 为 `"0"` 时作为主叫放音码 |
| calledPlayCode | 非空时作为被叫放音修正；空表示不修正 |

设计文档里策略表 `control_command` 为 0 拦截、1 放通、2 告警。vi-as 只对 opType `"0"` 做拦截分支，1 和 2 的差异待确认。

设计文档 Redis `action.appendReason` 示例（是否与本接口返回值同一套，待确认）：

| 码 | 含义 |
| --- | --- |
| 5 | 呼叫频次异常 |
| 13 | 呼叫时间段 |
| 20 | 企业最大呼叫数 |
| 21 | 异地外呼超限 |
| 22 / 23 / 24 | 超业务范围：盲呼 / 短秒率 / 接通率 |
| 25 | 长通话 |
| 26–32 | 外省高频、外市高频、沉默突增、闲时高频、短时高频突增、短时长突增、沉默号码外地长通话突增 |

话单 `append_reason`、策略表 `append_reason`、Redis `appendReason` 在设计文档里是三套编码。本接口以 vi-as 原样落库的字符串为准，映射表见话务管控原因码文档。

## 4. 和 vi-cront-jobs 的关系

vi-cront-jobs 不提供 HTTP。它按策略脚本把命中结果写入 Redis（如 `highfreq-{caller}`、`offtime-{customerId}`），供策略服务在 `/strategy` 里判定。key 的值结构以话务管控文档为准。strategy-service 是否直接读这些 key，待确认。

## 5. 待确认

1. 策略服务的业务错误码、鉴权，以及非 200 时放通是否为设计。
2. opType 1 与 2 在服务端的区别；HTTP 返回的 appendReason 用哪套码。
3. 连接超时 300 毫秒是否仍生效。
4. 测试代码里的查询地址不能当生产地址，本文不记录。
