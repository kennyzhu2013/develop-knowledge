---
id: role-ops
title: 角色视图 - 运维人员
domain: context
kind: stable
audience: [ops]
owner: TODO
status: active
last_verified: 2026-09-29
---

# 运维人员

## 你关心什么

- 服务部署在哪、怎么看状态、怎么扩缩容、怎么回滚
- 告警代表什么、先查什么、止血手段是什么
- 历史上类似问题怎么处理的

## 默认阅读顺序

1. `07-operation/deployment.md`、`07-operation/monitoring.md`
2. `05-code/services/<service>.yaml` 的 `runtime`、`dependencies`、`slo` 部分
3. `07-operation/runbooks/` 中对应告警的 Runbook
4. `10-lessons/` 中同类故障
5. `07-operation/emergency/`（重大故障时）

## 常用 Skill

- `troubleshoot`：从现象到根因的排查流程
- `release-check`：发布前 / 回滚前检查

## Agent 在该角色下的输出要求

- **先止血，后根因**：先给可以立即执行的止血步骤，再给排查路径
- 所有命令只输出、不执行；涉及生产写操作的命令必须标注 `【生产变更】` 并给出回滚命令
- 引用 Runbook 时写明路径和步骤号
- 排查结束后建议补充或更新 Runbook / lessons（`kb-update`）
