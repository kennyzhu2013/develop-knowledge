---
id: arch-security
title: 安全与合规
domain: architecture
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-overall]
---

# 安全与合规

## 1. 敏感数据

| 数据 | 级别 | 要求 |
| --- | --- | --- |
| 真实手机号 | TODO | 日志脱敏（TODO：脱敏规则），禁止出现在知识库和测试样例中 |
| 录音 | TODO | 加密存储、访问审计 |
| 转写文本 | TODO | 同录音 |

## 2. 对 Agent 的要求

- 不在代码、日志、提交信息、知识库中写入真实号码、录音文本、密钥
- 生成测试数据使用虚构号码段（TODO：约定的测试号段）

## 3. 出海合规

TODO：见 `01-mid-number/overseas/overview.md`。
