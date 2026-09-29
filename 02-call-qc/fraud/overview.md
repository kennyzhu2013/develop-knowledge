---
id: fraud-overview
title: 涉诈识别 - 总览
domain: call_qc
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-rules, ai-agent, ai-rag, svc-fraud-agent]
---

# 涉诈识别

## 1. 知识模型

反诈知识采用结构化建模，三层之间通过 ID 引用：

```text
诈骗模式 (FP-*)  ──包含──>  风险行为 (RB-*)  ──被检测于──>  规则 (RULE-FRAUD-*)
      │                                                         │
      └──────── 话术链路 conversation_flow（有序的 RB 序列） ─────┘
```

| 文件 | 内容 | 事实源 |
| --- | --- | --- |
| `fraud-patterns.yaml` | 诈骗模式与话术链路 | 本知识库 |
| `risk-behaviors.yaml` | 风险行为定义 | 本知识库 |
| `rules.yaml` | 规则索引（ID、覆盖的风险行为、代码位置） | 代码 / 配置中心，本文件只做索引 |

`tools/kb_lint.py` 会检查三者之间的 ID 引用是否存在。

## 2. 判定链路

TODO：规则初筛 → fraud-agent（RAG 检索相似案例 + LLM 推理） → 风险分级 → 处置。见 `03-ai/agent.md`。

## 3. 新增诈骗模式

按 `99-agent/skills/add-fraud-pattern/SKILL.md` 执行。

## 4. 数据要求

- 所有样例话术必须脱敏，禁止出现真实号码、姓名、账号
