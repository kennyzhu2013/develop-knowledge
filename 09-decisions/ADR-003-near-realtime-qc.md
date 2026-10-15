---
id: ADR-003
title: ADR-003 采用通话后准实时质检作为一期
domain: decisions
kind: historical
audience: [all]
owner: TODO
status: active
adr_status: Accepted
date: 2026-10-15
last_verified: 2026-10-15
related: [qc-pipeline, callchain-qc-near-realtime, ADR-004]
source_of_truth:
  - 00-context/design-docs/digest.md
  - vi-assets/offline-merge-master/
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
---

# ADR-003 采用通话后准实时质检作为一期

## Status

Accepted。依据《一期-准实时质检》与现行 prod 流程 `zhuanxian_qc.xml`。本 ADR 是对已落地实现的追记，不是新的待决提案。

## Context

专线呼叫经 7001X 前缀进入 vi-as。一期需要把通话录音送去 ASR 和反诈模型，并把结果推给客户。通话中的分片、编解码和管控指令尚未作为上线范围。

媒体侧已能把 RTP 落成分段文件，并用 RocketMQ 通知合音。质检调度侧已有 RabbitMQ 上的流程 XML。

## Decision

1. 质检在通话结束、合音完成之后发起。
2. vi-media 发 `MediaRecordMergeRequested`，offline-merge 用 ffmpeg 合成整通录音并上传 vi-mdown。
3. vi-billcenter 关联话单与合音结果，调用 qa-server `POST /v1/task/customize`。
4. scheduler 使用 prod 目录的 `zhuanxian_qc.xml`：ASR → 脱敏 → 关键词/反诈 → Qwen → RAG → 融通 → DeepSeek。
5. 结果经 qa-server 回调 vi-billcenter，再由 vi-pusher 推客户。
6. 接通前的拦截仍由话务管控（vi-cront-jobs + strategy-service）负责，不依赖本次质检结果。

## Alternatives

- 通话中实时分片：依赖 vi-codec 与分片串行，一期不作为主路径。见 ADR-004。
- 媒体服务直接调 qa-server：设计文档有这种写法，代码是 vi-billcenter 发起。

## Consequences

- 客户在挂机之后才收到内容质检结果，通话中不能按语义挂断。
- 合音 topic 名在生产者与 vi-billcenter 之间不一致，上线前必须核对配置，否则任务不会创建。
- 整通录音先落本地再上传，磁盘与 ffmpeg 时长成为容量瓶颈。
