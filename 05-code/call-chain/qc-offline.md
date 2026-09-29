---
id: callchain-qc-offline
title: 调用链 - 离线质检
domain: code
kind: dynamic
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-pipeline, svc-call-qc-service]
source_of_truth: []
---

# 调用链：离线质检

> 填写指引：从入口到出口，逐跳写到“类.方法”级别。优先用 IDE / 链路追踪导出，再人工校对。

| # | 服务 | 入口（类.方法 / Topic） | 调用 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | call-qc-service | TODO：CallCompleted 消费者 | 创建 qc_task | |
| 2 | call-qc-service | TODO | asr-service 离线转写 | 超时：TODO |
| 3 | call-qc-service | TODO | 规则引擎 | |
| 4 | call-qc-service | TODO | fraud-agent 涉诈判定 | |
| 5 | call-qc-service | TODO | 发送 QCCompleted / FraudDetected | |

链路追踪中的 Span 名：TODO
