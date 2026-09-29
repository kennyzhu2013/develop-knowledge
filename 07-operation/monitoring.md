---
id: ops-monitoring
title: 监控与告警
domain: operation
kind: dynamic
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-09-29
---

# 监控与告警

## 1. 看板

| 看板 | 平台 | 关注点 |
| --- | --- | --- |
| TODO：中间号总览 | TODO | 绑定 QPS、成功率、路由延迟 |
| TODO：质检总览 | TODO | 任务积压、端到端时延、ASR 失败率 |
| TODO：模型服务 | TODO | 推理延迟、GPU |

## 2. 告警 → Runbook

每条告警必须有 Runbook，`kb_lint.py` 会检查这里引用的 Runbook 是否存在。

| 告警名 | 含义 | 级别 | Runbook |
| --- | --- | --- | --- |
| 质检结果延迟 | 质检端到端时延超过阈值 | P2 | `07-operation/runbooks/qc-result-delay.md` |
| TODO | | | |
