---
id: arch-storage
title: 存储
domain: architecture
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-overall, data-overview]
source_of_truth:
  - vi-assets/vi-as-master/
  - vi-assets/vi-billcenter-master/
  - vi-assets/qa-server-master/apps/sub_worker.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/dao/
---

# 存储

| 数据 | 存储 | 访问方 | 备注 |
| --- | --- | --- | --- |
| 通话话单 | MySQL `vi_bill.bill_new`，失败写 `bill_fail`，另有按日表 | vi-as 写，vi-billcenter 读 | 字段见 `06-data/tables/vi_bill.bill_new.yaml` |
| 号码与企业 | MySQL `user_qc`（报备、企业、号段、ICSCF） | vi-as 读，qa-server 读写报备 | 无传统“绑定关系”表 |
| 话务策略 | MySQL `vi_strategy`，明细在 ClickHouse `mid_bussops` | vi-cront-jobs | 见 `01-mid-number/traffic-control/strategies.yaml` |
| 质检任务与规则 | qa-server 使用的 MySQL | qa-server | 任务状态与海山明细分开 |
| 质检明细 | Redis 分钟桶，再 stream load 到 StarRocks（海山）`qa_detail` / `qa_segment` | qa-server 写，ops-platform 读 | 转写文本属于敏感数据 |
| 调度状态 | MongoDB `task`、`sub_task`、`process`、`shard_task` | scheduler | 回调后删除转写全文。旧 MySQL DDL 当前运行时不用 |
| 模型元数据 | MongoDB `model_meta`、`ai_model` | scheduler | 含鉴权材料，查询接口会返回 |
| 分段录音 | vi-media 本地内存卷再迁磁盘 | offline-merge 回源下载 | 短生命期，不是最终存档 |
| 合音文件 | vi-mdown / PFS | vi-merge、offline-merge 写，ASR 拉取 | 分片 WAV；整通可能是 WAV 或 MP3 |

留存周期除媒体内存卷约 10 分钟、磁盘卷由 `diskLifeSpan` 决定外，库表留存在代码中没有统一策略，待确认。
