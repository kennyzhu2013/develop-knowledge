---
name: add-fraud-pattern
description: 新增诈骗模式、风险行为或话术链路，并把它们接入检测规则 / 反诈 Agent。
roles: [developer, qa, architect]
---

# Add Fraud Pattern

## Workflow

1. 读 `02-call-qc/fraud/overview.md`，理解 FP / RB / RULE 三层模型。
2. 在 `risk-behaviors.yaml` 中查找可复用的风险行为；确实没有再新增 `RB-*`。
3. 在 `fraud-patterns.yaml` 中新增 `FP-*`：描述、风险等级、`risk_behaviors`、`conversation_flow`（按顺序引用 RB）。
4. 判断检测方式：规则可覆盖 → 走 `add-qc-rule`；需要语义推理 → 更新 fraud-agent 的 Prompt / RAG 案例（见 `03-ai/agent.md`、`03-ai/rag.md`）。
5. 在 `rules.yaml` 中登记规则并回填 `patterns[].rules`。
6. 准备脱敏案例，放到 `02-call-qc/fraud/cases/`（如需要）。
7. 运行 `python tools/kb_lint.py`，确保所有 ID 引用有效。

## Validation

- [ ] 话术链路每一步都有可观测的信号（`signals`）
- [ ] 与相似模式的区分点已写明
- [ ] 正例 / 反例样本已准备
- [ ] 评测集上误报率可接受
