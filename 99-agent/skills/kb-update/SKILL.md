---
name: kb-update
description: 更新本工程知识库：补充缺失知识、修正与代码冲突的内容、新增 ADR / Runbook / 故障复盘。任务结束发现知识缺口或 CONFLICT 时使用。
roles: [architect, developer, ops, qa]
---

# KB Update

## Workflow

1. 确定知识类型与位置：
   - 稳定知识 → 对应域目录 Markdown
   - 常变事实（地址、版本、依赖、字段） → YAML
   - 决策 → `09-decisions/ADR-NNN-*.md`（旧 ADR 不改写，只标记 `Superseded`）
   - 故障 / 踩坑 → `10-lessons/`
   - 告警处理 → `07-operation/runbooks/`，并在 `monitoring.md` 告警表登记
2. 从 `99-agent/templates/` 复制模板，填写完整 frontmatter，`last_verified` 为当天。
3. 能从代码生成的内容用 `tools/gen_code_index.py` 生成，不要手写。
4. 新增路由关键词：如果这类任务以后会反复出现，在 `knowledge.yaml` 的 `routes` 中补充。
5. 运行 `python tools/kb_lint.py`，零错误后提交。
6. 通过 PR 提交，PR 描述写明：新增 / 修改了什么、依据（代码路径、故障单、会议）、请谁 Review（文档 `owner`）。

## Rules

- 不写入真实号码、录音文本、密钥、内网地址
- 一个 PR 只处理一个主题
- 不确定的内容标 `TODO` 或 `待核实`，不要编造
