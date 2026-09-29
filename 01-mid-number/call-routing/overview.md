---
id: mn-call-routing-overview
title: 呼叫路由 - 总览
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [mn-business-flow]
---

# 呼叫路由

## 1. 路由链路

```text
主叫 → 运营商网络 → TODO：软交换/媒体网关 → 路由查询（绑定关系） → 被叫真实号码
```

## 2. 路由决策

| 场景 | 判定条件 | 行为 |
| --- | --- | --- |
| 正常绑定 | 绑定有效 | 接续至对端 |
| 绑定过期 | TODO | TODO：放音 / 拒接 |
| 风险拦截 | 质检/反诈标记 | TODO |

## 3. 性能与可用性要求

- TODO：路由查询 P99 延迟要求
- TODO：缓存策略、降级策略（缓存失效时如何处理）

## 4. 相关 Runbook

- TODO：`07-operation/runbooks/` 中与接续失败相关的 Runbook
