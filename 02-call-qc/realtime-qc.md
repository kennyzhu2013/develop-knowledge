---
id: qc-realtime
title: 语音质检 - 实时质检
domain: call_qc
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-overview, ai-asr]
---

# 实时质检

## 1. 链路

```text
通话媒体流 → 音频分片（TODO：分片时长） → 流式 ASR → 增量文本 → 实时规则 / 实时 Agent → 风险事件 → 实时处置（TODO：告警 / 挂断 / 提示）
```

## 2. 关键设计点

| 问题 | 当前做法 | 依据 |
| --- | --- | --- |
| 分片与上下文拼接 | TODO | ADR（TODO 编号） |
| 会话状态存储 | TODO | |
| 端到端延迟预算 | TODO：分片 x ms + ASR y ms + 判定 z ms | |
| 与离线质检结果不一致 | TODO：以哪个为准 | |

## 3. 约束

- TODO：实时链路不可阻塞通话接续；异常时降级为离线质检
