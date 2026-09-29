---
id: dev-testing
title: 测试规范
domain: development
kind: stable
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-09-29
---

# 测试规范

| 层级 | 要求 | 工具 |
| --- | --- | --- |
| 单元测试 | 规则、状态机、计费逻辑必须覆盖 | TODO |
| 契约测试 | 接口、MQ 消息结构变更必须有 | TODO |
| 集成测试 | 质检流水线端到端 | TODO |
| 规则评测 | 质检 / 反诈规则上线前跑评测集 | TODO：评测集位置 |

## 测试数据

- 使用约定的测试号段（TODO），禁止使用真实号码
- 通话文本样例使用虚构内容或已脱敏内容
