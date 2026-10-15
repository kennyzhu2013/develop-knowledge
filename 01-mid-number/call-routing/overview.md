---
id: mn-call-routing-overview
title: 呼叫路由 - 总览
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-business-flow, mn-700-overview, mn-traffic-control-overview, svc-vi-as, module-vi-as]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/sip/engine/SipEngine.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/util/SipUtil.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/bizcontroller/controllerImpl/BaseController.java
---

# 呼叫路由

vi-as 是 JAIN-SIP B2BUA，监听 UDP 5080。HTTP 端口 dev 5668 / prod 5287，用于媒体回调和运维，不用于客户绑定。

## 1. 控制器怎么选

| 条件 | 控制器 | 话单 | 录音 |
| --- | --- | --- | --- |
| startMode=2，或 GZH 二次路由带 CMIC 标记 | B2BWithoutBill | 无 | 无 |
| 无报备且来自 GZH 域 | B2BWithoutBill | 无 | 无 |
| 无报备且非 GZH | RejectController | 无 | 无，先 180 再 603 |
| 账户无效或策略 opType 断开 | ErrorController | 有 | 可能放音 |
| startMode=1 且放通 | B2BWithBill | 有 | 无 |
| 默认放通 | BaseController | 有 | 有（媒体可用且不是纯 G.729） |

会话数默认上限 15000，可由 ETCD `/config/max_sessions` 调整，超过回 `500 cmic session block`。

## 2. 右腿怎么走

| 被叫 | 路由 | 号码 |
| --- | --- | --- |
| 以 700 开头 | `SipUtil.setNextRoute4Dcc`：按 proxyMode 加代理 Route，带 xsipproxy `internal-as` tag | `adaptTelB` 把 11 位补成 `tel:+86` |
| 其它（GZH） | `setNextRoute4Gzh`：Route 取原请求最后一个，即 S-CSCF | 原被叫去掉 5 位前缀 |

主叫 SDP 交给 vi-media，发给被叫的 SDP 换成媒体的 ip 和端口。支持的编码包括 telephone-event、AMR、AMR-WB、PCMA、PCMU，可由 `/config/supported_codec` 调整。主叫只有 G.729 时不锚定媒体。

## 3. 策略对路由的影响

startMode=0 时，控制器启动前同步调用 strategy-service。返回断开则改为放音后挂断，不向被叫发 INVITE。超时、未发现实例、或 ETCD 审计开关关闭时的行为见 `05-code/modules/vi-as.md`，不要在未读代码时改成“失败即拦截”。
