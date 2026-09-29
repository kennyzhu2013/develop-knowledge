---
id: role-architect
title: 角色视图 - 技术架构师
domain: context
kind: stable
audience: [architect]
owner: TODO
status: active
last_verified: 2026-09-29
---

# 技术架构师

## 你关心什么

- 系统边界、服务职责划分、依赖方向是否合理
- 非功能性：延迟、吞吐、可用性、容量、成本、合规（号码、录音、出海数据）
- 决策的来龙去脉，以及新方案和已有 ADR 是否冲突

## 默认阅读顺序

1. `00-context/business-overview.md`、`00-context/system-boundary.md`
2. `04-architecture/overall.md`，再按需读 `middleware.md`、`storage.md`、`high-availability.md`、`security.md`
3. `05-code/dependency-map.yaml`（真实依赖以它和代码为准，不以架构图为准）
4. `09-decisions/` 中与主题相关的 ADR
5. `10-lessons/` 中相关故障

## 常用 Skill

- `architecture-review`：评审方案、输出风险和备选方案
- `impact-analysis`：沿依赖图评估改动影响面
- `understand-system`：快速建立全局认知

## Agent 在该角色下的输出要求

- 结论先行，给出 2~3 个备选方案和取舍，不只给一个答案
- 明确引用依据：ADR 编号、依赖图节点、代码路径
- 与已有 ADR 冲突时，建议新增 ADR 并将旧 ADR 标记为 `Superseded`，而不是改写旧 ADR
- 需要画图时使用 Mermaid，节点名与 `knowledge.yaml` 的服务名一致
