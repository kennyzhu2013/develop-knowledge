---
id: ADR-001
title: ADR-001 工程知识库采用 Knowledge as Code，单仓库多角色共用
domain: decisions
kind: historical
audience: [all]
owner: TODO
status: active
adr_status: Accepted
date: 2026-09-29
last_verified: 2026-09-29
related: [ADR-002]
---

# ADR-001 工程知识库采用 Knowledge as Code，单仓库多角色共用

## Status

Accepted

## Context

中间号与语音质检涉及多个服务和多类角色（架构、研发、运维、测试）。知识散落在 Wiki、会议纪要、个人笔记中，Code Agent 无法稳定获取，且各角色维护的文档互相矛盾。

## Decision

- 用一个独立 Git 仓库存放全部工程知识，Markdown 给人读，YAML 给机器读
- 不按角色拆仓库或做权限隔离；通过文档 frontmatter 的 `audience` 与 `roles/*.md` 提供角色视图
- 通过 `knowledge.yaml` + `tools/kb_route.py` 做任务到知识的路由，不在 Phase 1~2 引入向量库
- 所有知识变更走 PR 与 Review，CI 运行 `tools/kb_lint.py`

## Alternatives

- A. 每个角色一套知识库：维护成本高，事实不一致
- B. 全部灌入向量库做 RAG：Agent 无法精确引用、难以 Review、跨工具复用差
- C. 单仓库 + 角色视图 + 结构化路由（选择）

## Consequences

- 优点：唯一事实源；任何 Agent 工具都能直接读文件；变更可审计
- 代价：需要维护 frontmatter 与 YAML 结构；需要 CI 保证质量
