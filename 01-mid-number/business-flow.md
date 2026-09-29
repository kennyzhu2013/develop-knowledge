---
id: mn-business-flow
title: 中间号 - 核心业务流程
domain: mid_number
kind: stable
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [mn-overview]
---

# 中间号核心业务流程

> 填写指引：每个流程一张时序图 + 一个步骤表。步骤表的“代码位置”一列由研发填写，指向具体类/方法。

## 1. 绑定

```mermaid
sequenceDiagram
    participant C as 客户平台
    participant M as mid-number-service
    participant P as 号码池
    participant DB as 绑定库
    C->>M: 绑定请求（A 号码、B 号码、有效期）
    M->>P: 申请中间号 X
    P-->>M: X
    M->>DB: 写绑定关系
    M-->>C: 返回 X
```

| 步骤 | 说明 | 代码位置 | 失败处理 |
| --- | --- | --- | --- |
| 1 | 参数校验 | TODO | 返回错误码（见 `06-data/api/`） |
| 2 | 申请中间号 | TODO | TODO |
| 3 | 写绑定关系 | TODO | TODO |

## 2. 呼叫接续

TODO：来电 → 查绑定 → 路由到真实号码 → 话单。见 `call-routing/overview.md`。

## 3. 解绑 / 到期回收

TODO

## 4. 话单与计费

TODO：见 `charging/overview.md`。
