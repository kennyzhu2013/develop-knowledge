---
id: mn-charging-overview
title: 话单与计费 - 总览
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [mn-overview, data-events]
---

# 话单与计费

## 1. 话单流转

```text
通话结束 → 话单生成（TODO：来源） → MQ（TODO：Topic） → 计费 → 对账 → 数据平台
```

话单事件结构见 `06-data/events/events.yaml` 中的 `CallCompleted`。

## 2. 计费规则

| 维度 | 规则 | 代码 / 配置位置 |
| --- | --- | --- |
| 计费单位 | TODO | |
| 号码月租 | TODO | |
| 700 号码 | TODO | |

## 3. 约束

- 话单消息**只能新增字段、不能修改语义或删除字段**，下游包括计费、质检、数据平台（待核实完整清单，见 `05-code/dependency-map.yaml`）。
- TODO：对账差异处理流程
