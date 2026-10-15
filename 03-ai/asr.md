---
id: ai-asr
title: ASR 语音转写
domain: ai
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-overview, ai-model-catalog, qc-realtime, svc-scheduler]
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/ai/
---

# ASR 语音转写

ASR 服务本身不在本次源码里。scheduler 把录音 URL 放进 RabbitMQ，适配器转写后把文本写回。

| 项 | 准实时 | 实时分片 |
| --- | --- | --- |
| 音频 | 合音后的整通文件 URL | 每个分片的 URL |
| 队列 | `scheduler.asr.queue`；`asrPrior==1` 走 fast 队列 | 同一套队列，由 `shard_task` 保证同一通话按序 |
| 请求 | `jobId, fileUrl, type`（0 左 / 1 右 / 2 双声道，默认 0），语言默认 zh | 同左 |
| 写回 | `asrText, audioDuration, asrInfo` | 文本累加到 `shard_task.asrText` 再回写当前子任务 |
| 复用 | 子任务 `reuse==1` 且 Redis 有 dataId 时，从海山 `qa_detail` 预填 | 分片流程未单独描述复用 |

专线 XML 要求转写超过 45 字才继续，否则子任务失败。脱敏模型成功后会**替换** `asrText`。回调发送后，MongoDB 里的转写全文被删除。

九天 ASR 走另一套队列，但写回字段和事件名仍是 `asr_resp`。声道 type 与合音“左主叫、右被叫”是否一致，取决于建任务时传入的 type，待确认。
