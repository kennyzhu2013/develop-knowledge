---
id: mn-charging-overview
title: 话单与计费 - 总览
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-overview, data-events, svc-vi-as, svc-vi-billcenter]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/DbAsyncService.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/BillCenterService.java
  - vi-assets/vi-billcenter-master/
  - 00-context/design-docs/digest.md
---

# 话单与计费

本次仓库实现到“话单落库并通知话单中心”。批价和扣费在外部计费中心（设计文档称 PDC），不在这 9 个仓库里。

## 1. 话单生命周期

| 阶段 | 谁写 | 结果 |
| --- | --- | --- |
| 呼叫建立 | vi-as `DbAsyncService.submitDbInsert` | `vi_bill.bill_new` 初始行，带四级企业 id 和 appId |
| 呼叫结束 | `BaseController.handleEnd` | 计算 finish_type / finish_state。状态 21 或 0 时延迟 300ms 再判 |
| 通知 | `BillCenterService.sendMsgToBillCenter` | 事件 `CallBillCompleted`。失败写入 `bill_fail` |
| 合音关联 | vi-billcenter | 把合音文件时长、录音位置补到质检请求。vi-as 的 `BaseController` 在有媒体会话时写入 serverId/storeId，没有会话时写 0；`B2BWithBillController` 恒写 0 |
| 质检回写 | vi-billcenter 消费 qa-server 回调 | 质检结果再发给 vi-pusher |

finish_state 的优先级在 `PreciseCallStateBase.getFinalState`。策略拦截是 22，账户无效是 99。完整枚举见 `05-code/modules/vi-as.md`。

## 2. 设计文档里的三种批价

计费依据被写成“质检任务耗时”，不是通话时长。是否等于录音时长待确认。三种方案都由对客平台把时长推给 PDC，本仓库没有对应客户端。

| 方案 | 付费 | paymethod | 商品口径 |
| --- | --- | --- | --- |
| 方案一 | 预付费套包 | 1 实时批价 | 不满 1 分钟按 1 分钟 |
| 方案二 | 文档写后付费 | 1 实时批价 | 不满 6 秒按 6 秒 |
| 方案三（江西） | 后付费 | 2 定时批价 | 先按 6 秒取整再汇总当天 |

CONFLICT: 方案二同时写“后付费”和 paymethod=1。方案三的示例总秒数不是 6 的倍数，和“每任务按 6 秒取整”矛盾。

## 3. 不要在 vi-as 里做的事

客户分钟额度、过期时间、`ability` 字段被加载，但不参与是否录音或是否建质检任务。质检侧 vi-billcenter 同样没有扣减额度。
