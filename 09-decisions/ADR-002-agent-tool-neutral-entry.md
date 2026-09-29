---
id: ADR-002
title: ADR-002 以 AGENTS.md 作为唯一入口，其它 Agent 工具入口由脚本生成
domain: decisions
kind: historical
audience: [all]
owner: TODO
status: active
adr_status: Accepted
date: 2026-09-29
last_verified: 2026-09-29
related: [ADR-001]
---

# ADR-002 以 AGENTS.md 作为唯一入口，其它 Agent 工具入口由脚本生成

## Status

Accepted

## Context

团队成员使用不同 Agent 工具，各工具读取的入口文件不同：Claude Code 读 `CLAUDE.md` 与 `.claude/skills/`，Gemini CLI / Qwen Code 读 `GEMINI.md` / `QWEN.md`，Copilot 读 `.github/copilot-instructions.md`，Trae 读 `.trae/rules/`，Cursor / Codex / OpenCode 原生读 `AGENTS.md`，Codex 与 Cursor 从 `.agents/skills/` 加载 Skills。

## Decision

- `AGENTS.md` 与 `99-agent/skills/` 是唯一维护点
- 其它入口文件由 `tools/sync_adapters.py` 生成，内容只是指向 `AGENTS.md` 的指针；Skills 复制到 `.claude/skills/` 与 `.agents/skills/`
- Cursor 同时读取这两个 Skills 目录，可能看到同名 Skill 各出现一次，内容相同，不影响使用
- 不使用符号链接，因为 Windows 环境下 Git 默认不创建符号链接

## Consequences

- 新增一种 Agent 工具，只需在 `sync_adapters.py` 增加一个目标
- 生成文件被提交到仓库，CI 通过 `sync_adapters.py --check` 保证未被手改
