---
id: qc-pipeline
title: 语音质检 - 准实时流水线
domain: call_qc
kind: stable
audience: [architect, developer, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-overview, qc-media-chain, qc-orchestration, callchain-qc-near-realtime]
source_of_truth:
  - vi-assets/vi-as-master/
  - vi-assets/offline-merge-master/
  - vi-assets/vi-billcenter-master/
  - vi-assets/qa-server-master/
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
---

# 准实时质检流水线

准实时（一期）是通话结束后再质检：合音完成才向 qa-server 建任务。ASR 拉取的是整通录音，不是分片。

```text
xproxy → vi-as → vi-media 录音
      → MediaRecordMergeRequested → offline-merge → OfflineRecordMerged
      → vi-billcenter → qa-server /v1/task/customize
      → scheduler（zhuanxian_qc.xml）→ ASR / 反诈模型
      → qa-server 回调 vi-billcenter → vi-pusher → httpproxy → 客户
```

步骤、代码位置和失败行为见 `05-code/call-chain/qc-near-realtime.md`。媒体细节见 `02-call-qc/media-chain.md`，任务与模型见 `02-call-qc/orchestration.md`。

## 步骤表

| # | 步骤 | 服务 | 输入 | 输出 |
| --- | --- | --- | --- | --- |
| 1 | 7001X 非 15 位被叫接入 | vi-as | SIP INVITE | 授权后的 B2BUA 会话 |
| 2 | 锚定并录音 | vi-media | HTTP create/update/pickup | 分段录音文件 |
| 3 | 通话结束发合音请求 | vi-media | 会话 stop | 事件 `MediaRecordMergeRequested` |
| 4 | ffmpeg 合音并上传 | offline-merge | 分段文件 | 事件 `OfflineRecordMerged`，文件在 vi-mdown / PFS |
| 5 | 关联话单并发起质检 | vi-billcenter | 通话结束消息 + 合音结果 | HTTP `/v1/task/customize` |
| 6 | 编排 ASR 与模型 | qa-server、scheduler | 流程 `zhuanxian_qc.xml` | 风险等级、质检结果 |
| 7 | 回调并推送 | qa-server、vi-billcenter、vi-pusher | 子任务/主任务回调 | 客户侧质检话单 |

合音 topic 名在生产者与 vi-billcenter 订阅之间不一致，见 `02-call-qc/media-chain.md` 第 6 节。未接通或只支持 G.729 时 vi-as 不锚定媒体，后续合音与质检不会发生。
