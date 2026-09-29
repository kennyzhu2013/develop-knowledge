---
id: role-qa
title: 角色视图 - 测试工程师
domain: context
kind: stable
audience: [qa]
owner: TODO
status: active
last_verified: 2026-09-29
---

# 测试工程师

## 你关心什么

- 业务规则的准确边界：什么算命中、什么不算
- 质检 / 反诈规则的误报（false positive）和漏报（false negative）
- 回归范围：改了一个规则或接口，要回归哪些场景

## 默认阅读顺序

1. `00-context/glossary.md`
2. `08-development/testing.md`
3. 对应规则文档：`02-call-qc/rules.md`、`02-call-qc/fraud/`
4. `06-data/api/` 中的接口契约
5. `10-lessons/` 中由测试遗漏导致的故障

## 常用 Skill

- `add-qc-rule`、`add-fraud-pattern` 的 Validation 部分
- `release-check`

## Agent 在该角色下的输出要求

- 用例按「正例 / 反例 / 边界 / 对抗样本」分组
- 样例文本必须脱敏，不使用真实号码和真实通话内容
- 给出每条用例对应的规则 ID 或接口，方便追溯
