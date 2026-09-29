---
name: understand-system
description: 快速建立对中间号与语音质检系统（或其中某个域）的整体认知。在刚接手任务、需要解释系统如何工作、或其它 Skill 要求先理解系统时使用。
roles: [architect, developer, ops, qa]
---

# Understand System

## Workflow

1. 读 `AGENTS.md` 第 1 节确定系统范围，读 `knowledge.yaml` 找到目标域。
2. 读 `00-context/business-overview.md` 与 `00-context/glossary.md`。
3. 读目标域的 `overview.md`，再读其流程文档（`business-flow.md` / `pipeline.md`）。
4. 读 `04-architecture/overall.md`，从 `05-code/dependency-map.yaml` 找出目标域的上下游服务。
5. 对每个相关服务读 `05-code/services/<service>.yaml`，按 `05-code/repositories.yaml` 定位代码，抽查入口类确认文档与代码一致。
6. 扫一眼 `09-decisions/` 和 `10-lessons/` 的标题，读与目标域相关的条目。

## Output

用不超过一页输出：

- 该域的职责和边界（一段话）
- 主链路（Mermaid 或箭头列表，节点使用标准服务名）
- 关键数据对象与契约（表、事件、接口）
- 必须知道的约束（引用 ADR / lessons）
- 发现的 `CONFLICT` 与知识缺口
