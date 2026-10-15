---
id: mn-business-flow
title: 中间号 - 核心业务流程
domain: mid_number
kind: stable
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-overview, mn-call-routing-overview, mn-700-overview, callchain-qc-near-realtime]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/DtmfEngine.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/bizcontroller/controllerImpl/BaseController.java
---

# 专线呼叫主流程

本次资产没有“绑定”接口。呼叫能否进来，取决于号码是否已报备，以及 strategy-service 是否要求拦截。

## 1. 一通外呼

```text
业务方 INVITE（被叫带 7001X，总长不是 15 位）
  → xproxy → vi-as
  → 查 biz_num_auth
  → 写 bill_new 初始行
  → 查 strategy-service
  → 创建 vi-media 会话并改写 SDP
  → 向真实被叫发 INVITE
  → 应答后 pickup，挂机后 stop
  → 更新话单并发 CallBillCompleted
```

| # | 步骤 | 代码 | 结果 |
| --- | --- | --- | --- |
| 1 | 解析号码 | `DtmfEngine.doBuildController` | 去掉 `+` / `86` / `0`；被叫 700 开头时 bizNum 取主叫 |
| 2 | 查报备 | `AuthConfigService.getCallerNumAuthDo` | 先精确再前缀。无记录则 603，GZH 域则只转发 |
| 3 | 账户状态 | `ViConf.isBizStateActive` | status≠1：放音后挂断，finish_state=99，append_reason=15 |
| 4 | 策略 | `BusinessEvent.doStrategyQuery` | opType 断开则 ErrorController，finish_state=22 |
| 5 | 媒体与接续 | `BaseController.start` | 被叫 700 走 `setNextRoute4Dcc`，否则走 GZH 的 S-CSCF |
| 6 | 结束 | `BaseController.terminated` | stop 媒体、算 finish_state、更新话单、通知 vi-billcenter |

startMode=1 有话单不录音；startMode=2 无话单无录音。质检从合音结果开始，不在本流程内，见 `05-code/call-chain/qc-near-realtime.md`。

## 2. 匹配失败时是否接续

CONFLICT: 7001 路由方案的设计是匹配失败拒绝接续。实体卡方案在 §2.2.1 写“放通但不质检”，§2.2.2 写“不进行接续”。代码路径取决于是否判为 GZH 域，见 `05-code/modules/vi-as.md`。
