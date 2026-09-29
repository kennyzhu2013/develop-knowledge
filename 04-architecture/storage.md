---
id: arch-storage
title: 存储
domain: architecture
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-overall, data-overview]
---

# 存储

| 数据 | 存储 | 留存期 | 访问方 | 备注 |
| --- | --- | --- | --- | --- |
| 绑定关系 | TODO | TODO | mid-number-service | |
| 话单 | TODO | TODO | 计费、质检、数据平台 | |
| 录音 | TODO：对象存储 / NFS | TODO | call-qc-service、asr-service | 敏感数据 |
| 转写文本 | TODO | TODO | call-qc-service、fraud-agent | 敏感数据 |
| 质检结果 | TODO | TODO | 数据平台 | |

表结构见 `06-data/tables/`。
