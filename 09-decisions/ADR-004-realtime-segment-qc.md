---
id: ADR-004
title: ADR-004 二期用实时分片质检并保留离线回退
domain: decisions
kind: historical
audience: [all]
owner: TODO
status: active
adr_status: Accepted
date: 2026-10-15
last_verified: 2026-10-15
related: [ADR-003, qc-realtime, callchain-qc-realtime, qc-media-chain]
source_of_truth:
  - vi-assets/vi-media-master/internal/proxy/stream_outbound.go
  - vi-assets/vi-merge-master/processor/ac2_processor.go
  - vi-assets/scheduler-master/sources/dev/zhuanxian_qc_2.xml
---

# ADR-004 二期用实时分片质检并保留离线回退

## Status

Accepted，作为二期目标架构的追记。分片编排 XML 目前在 `sources/dev/`，不在 prod 目录。生产是否已经切到该 XML 待确认。

## Context

一期只能在挂机后质检。二期要求通话中按片分析，并在高风险时尽快结束后续分片。实时链路上多了 vi-codec 和 vi-merge，任一环节失败都不能把这通录音丢掉。

## Decision

1. vi-as 通过 `/call/media/inspect` 打开实时质检。vi-media 在录满 `frames` 秒后，用 WebSocket 把帧推给 vi-codec。
2. vi-codec 把 PCM 推到 vi-merge `/v1/merge`。vi-merge 按 `initialSegments` 与 `segments` 切 WAV，上传后发 `StreamSegmentMerged`。
3. scheduler 用 `shard_task` 让同一 callId 的分片串行，ASR 文本累加。高风险写 `interruptShard`，后续分片不再跑模型。
4. 推流建连失败时 vi-media 置 `fallback=true`，会话结束仍发离线合音消息，回到 ADR-003 的 offline-merge 路径。
5. 推流成功且未回退时，不再发离线合音消息，避免同一通话两条合音结果。
6. 整通录音仍上传，但不单独发 MQ。

## Alternatives

- 只做实时、失败即放弃录音：会丢失质检输入，未采用。
- 实时与离线始终双写：推流成功时不发离线消息，避免重复质检。
- 在 vi-as 内直接执行质检回推的挂断：设计文档标为待定，本次代码未做。

## Consequences

- 队列满时帧静默丢弃，且不会触发 fallback，分片可能缺音频。
- vi-merge 停机不等待会话，进行中的合音丢失。
- 分片 `duration` 是累计值。下游若当成单片时长，计费和展示会错。
- dev 与 prod 的 XML 不同名。部署时要明确 `xml.path` 指向的环境。
