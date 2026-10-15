---
id: mn-700-overview
title: 700 号码 - 总览
domain: mid_number
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-overview, mn-call-routing-overview, mn-number-pool-overview, svc-vi-as]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/DtmfEngine.java
  - 00-context/design-docs/digest.md
---

# 700 号码

这里的 700 号码是专线质检进入 700 大网的方式，不是可申请、可绑定的隐私号池。

## 1. 识别规则

| 规则 | 来源 | 结果 |
| --- | --- | --- |
| 被叫以 7001X 开头且总长不是 15 位 | 《专线质检方案+需求》§2.1.1 | xproxy 送给 vi-as |
| 被叫总长 15 位 | 同上 | 原 700 大网，不进专线质检 |
| 被叫以 700 开头 | `DtmfEngine.doBuildController` | bizNum 取主叫，右腿路由走 DCC/xsipproxy |
| 去掉 5 位前缀得到真实被叫 | `BusinessEvent.processCallParameter` | 例如前缀 `70014` 后接 11 位。X 还有哪些取值待确认 |

文档里同时出现 `70011`（去掉前缀还原被叫）和 `70014`（号码表、ICSCF 的路由码）。两者是否分工待确认。

## 2. 两种接入

| 维度 | 7001 路由（ISBC） | IMS 实体卡签约 |
| --- | --- | --- |
| 进入方式 | 起呼时给被叫加前缀 | 主叫或被叫签约，话路送到中间号 |
| 匹配失败 | 设计为拒绝接续 | 文档自相矛盾：一处放通不质检，一处不接续 |
| 回拨 | 设计为不进平台 | 设计为进入平台并可质检 |

号显在正文和时序图里也不一致：正文写号显主叫，IMS 时序图写号显工作号或被叫。

## 3. 本仓库没有的能力

号码申请、绑定、解绑、回收不在 vi-as 或其它 8 个仓库的接口里。报备由 ops-platform 的报备接口写入 `biz_num_auth`，见 `06-data/api/reporting-interface-v1.0.1.md`。
