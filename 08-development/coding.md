---
id: dev-coding
title: 编码规范
domain: development
kind: stable
audience: [developer]
owner: TODO
status: draft
last_verified: 2026-09-29
---

# 编码规范

> 各业务仓库自身的规范（lint 配置、仓库内 AGENTS.md）优先于本文件。本文件只写跨仓库的共性约定。

- 日志：真实号码必须脱敏（规则见 `04-architecture/security.md`）；每条日志带 `call_id` / `trace_id`
- 异常：外部调用（ASR、模型、MQ）必须有超时和明确的失败处理，不允许吞异常
- 配置：开关、阈值、规则放配置中心，不写死在代码
- 枚举：状态值在代码中集中定义，知识库文档引用代码枚举名
- TODO：各语言的具体规范链接
