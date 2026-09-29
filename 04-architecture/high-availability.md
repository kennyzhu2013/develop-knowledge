---
id: arch-ha
title: 高可用与容灾
domain: architecture
kind: stable
audience: [architect, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-overall, ops-emergency]
---

# 高可用与容灾

| 服务 | 部署模式 | 单点 | 降级策略 | RTO / RPO |
| --- | --- | --- | --- | --- |
| mid-number-service | TODO | | TODO：缓存失效时的路由兜底 | |
| call-qc-service | TODO | | TODO：积压时的限流 / 抽检 | |
| asr-service | TODO | | | |
| fraud-agent | TODO | | TODO：模型不可用时仅走规则 | |

应急预案见 `07-operation/emergency/overview.md`。
