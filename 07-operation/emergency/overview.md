---
id: ops-emergency
title: 应急预案总览
domain: operation
kind: stable
audience: [ops, architect]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-ha]
---

# 应急预案

| 场景 | 级别 | 预案 | 决策人 |
| --- | --- | --- | --- |
| 呼叫接续大面积失败 | P0 | TODO | TODO |
| 绑定接口不可用 | P0 | TODO | TODO |
| 质检全链路中断 | P1 | TODO：降级为抽检 / 延迟补偿 | TODO |
| 模型服务不可用 | P2 | TODO：仅规则质检 | TODO |

## 通用流程

1. 确认影响 → 2. 通报 → 3. 止血 → 4. 恢复 → 5. 复盘（写入 `10-lessons/incidents/`）
