---
id: mn-number-pool-overview
title: 号码池 - 总览
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [mn-overview]
---

# 号码池

## 1. 号码状态机

```mermaid
stateDiagram-v2
    [*] --> 空闲
    空闲 --> 已分配: 绑定
    已分配 --> 冷却: 解绑/到期
    冷却 --> 空闲: 冷却期结束
    空闲 --> 停用: 下线
    冷却 --> 停用: 下线
```

> 以上为通用示意，按实际状态和代码中的枚举值修正，并在下表写明代码中的枚举名。

| 状态 | 代码枚举 | 说明 |
| --- | --- | --- |
| 空闲 | TODO | |
| 已分配 | TODO | |
| 冷却 | TODO | 冷却时长：TODO |
| 停用 | TODO | |

## 2. 分配策略

TODO：按地域 / 客户 / 号段分配的规则，并发分配如何避免同号重复分配。

## 3. 约束与坑

- TODO
