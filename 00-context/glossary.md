---
id: ctx-glossary
title: 术语表
domain: context
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
---

# 术语表

> 规则：代码、文档、Agent 回复中统一使用「标准名」一列；「别名」只用于检索。新增术语按字母/拼音顺序插入。

| 标准名 | 英文 / 代码标识 | 别名 | 定义 |
| --- | --- | --- | --- |
| 中间号 | `middle_number` (TODO 核对) | 隐私号、虚拟号、小号 | 为通话双方分配的、隐藏真实号码的号码 |
| 绑定关系 | `binding` | 映射关系 | 真实号码与中间号之间的有效期内对应关系 |
| AXB | `AXB` | | TODO：A、B 双方通过中间号 X 互通的绑定模式 |
| AXN | `AXN` | | TODO |
| 700 号码 | TODO | | TODO |
| 话单 | `cdr` | 通话记录 | 一次通话的结构化记录 |
| 质检任务 | `qc_task` | | 对一通通话发起的一次质检处理单元 |
| 质检规则 | `qc_rule` | | 判定某类风险的规则，唯一 ID 形如 `RULE-xxx` |
| 诈骗模式 | `fraud_pattern` | 诈骗类型 | 一类诈骗手法，ID 形如 `FP-xxx`，见 `02-call-qc/fraud/fraud-patterns.yaml` |
| 风险行为 | `risk_behavior` | | 可在对话中观测到的风险动作，ID 形如 `RB-xxx` |
| 实时质检 | `realtime_qc` | 流式质检 | 通话进行中对语音分片进行质检 |
| ASR | `asr` | 语音转写 | 自动语音识别 |
| TODO | | | |
