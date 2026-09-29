---
id: data-api-overview
title: 接口契约索引
domain: data
kind: dynamic
audience: [developer, qa, architect]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [data-overview]
---

# 接口契约索引

优先把各服务导出的 OpenAPI 文件放在本目录（如 `mid-number-service.openapi.yaml`），本文件只做索引。

| 服务 | 契约文件 | 对外 / 对内 | 调用方 |
| --- | --- | --- | --- |
| mid-number-service | TODO | 对外 | 客户平台、软交换 |
| call-qc-service | TODO | 对内 | TODO |
| fraud-agent | TODO | 对内 | call-qc-service |
| asr-service | TODO | 对内 | call-qc-service |

## 通用约定

- 错误码：TODO（统一错误码表位置）
- 版本策略：TODO（URL 版本 / Header 版本）
- 兼容性：只能新增可选字段；删除或改语义需要新版本接口
