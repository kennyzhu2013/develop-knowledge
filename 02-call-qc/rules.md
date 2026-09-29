---
id: qc-rules
title: 语音质检 - 规则体系
domain: call_qc
kind: dynamic
audience: [developer, qa, architect]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-overview, fraud-overview]
source_of_truth: []
---

# 质检规则体系

## 1. 规则存放位置

| 项 | 位置 |
| --- | --- |
| 规则定义（事实源） | TODO：代码仓库路径 / 配置中心 / 数据库表 |
| 规则引擎代码 | TODO |
| 反诈规则（结构化） | `02-call-qc/fraud/rules.yaml` |

> 规则的**事实源在代码或配置中心**。本文件只描述规则体系、分类和编写规范，不复制每条规则的内容。

## 2. 规则分类

| 分类 | ID 前缀 | 说明 |
| --- | --- | --- |
| 涉诈 | `RULE-FRAUD-` | 见 `fraud/` |
| 投诉 | `RULE-CPL-` | TODO |
| 骚扰 | `RULE-HRS-` | TODO |
| 服务规范 | `RULE-SVC-` | TODO |

## 3. 规则结构

TODO：条件类型（关键词、正则、说话人角色、时间窗口、组合逻辑）、输出（风险等级、标签）。

## 4. 风险等级

| 等级 | 含义 | 处置 |
| --- | --- | --- |
| TODO | | |

## 5. 规则变更要求

- 新增或修改规则必须走 `99-agent/skills/add-qc-rule/SKILL.md`
- 每条规则必须有正例、反例样本（脱敏）
- 规则上线需要评估误报率，阈值：TODO
