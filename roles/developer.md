---
id: role-developer
title: 角色视图 - 研发工程师
domain: context
kind: stable
audience: [developer]
owner: TODO
status: active
last_verified: 2026-09-29
---

# 研发工程师

## 你关心什么

- 需求对应的业务流程、代码在哪、怎么改、改了影响谁
- 接口 / MQ / 表结构契约和兼容性
- 测试怎么写、怎么本地验证

## 默认阅读顺序

1. `00-context/glossary.md`（先对齐术语，避免把“绑定关系”“通话记录”“质检任务”混用）
2. 对应业务域的 `overview.md` 和 `business-flow.md` / `pipeline.md`
3. `05-code/services/<service>.yaml` → 代码仓库中的对应模块
4. 涉及契约时：`06-data/api/`、`06-data/mq/`、`06-data/tables/`
5. `08-development/` 中相关规范

## 常用 Skill

- `modify-api`、`modify-mq`、`modify-database`：改契约的标准流程
- `add-qc-rule`、`add-fraud-pattern`：质检和反诈规则开发
- `impact-analysis`：改公共代码前必做

## Agent 在该角色下的输出要求

- 改动前列出：要改的文件、受影响的上下游、需要补的测试
- 代码风格遵循业务仓库自身的规范，其次是 `08-development/coding.md`
- 改了契约或规则，同一个任务内起草对应知识库更新（`kb-update`）
- 不要为了“顺手”重构与任务无关的代码
