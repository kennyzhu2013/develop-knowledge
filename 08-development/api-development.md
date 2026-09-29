---
id: dev-api
title: 接口开发规范
domain: development
kind: stable
audience: [developer, architect]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [data-api-overview]
---

# 接口开发规范

- 新增接口：先写契约（`06-data/api/`），评审后再实现
- 兼容性：只允许新增可选字段；删除字段、修改类型或语义必须新版本
- 幂等：绑定、解绑等写接口必须支持幂等（TODO：幂等键约定）
- 错误码：使用统一错误码表（TODO 位置），不新增含义重复的错误码
- 调用方清单：修改前通过 `impact-analysis` 查 `05-code/services/*.yaml` 与网关日志确认调用方
