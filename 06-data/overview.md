---
id: data-overview
title: 数据与契约 - 总览
domain: data
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-storage, data-events, data-mq-topics, data-redis-keys, data-etcd-keys]
source_of_truth:
  - 06-data/tables/
  - vi-assets/
---

# 数据与契约

| 目录 | 内容 |
| --- | --- |
| `tables/` | 一表一文件。话单主表是 `tables/vi_bill.bill_new.yaml` |
| `mq/topics.yaml` | RocketMQ Topic 与 RabbitMQ 队列 |
| `events/events.yaml` | 事件名，服务 YAML 必须引用这里的名字 |
| `api/` | HTTP 与 WebSocket 契约 |
| `redis/keys.yaml` | 话务管控、qa-server、scheduler 的 key |
| `etcd/keys.yaml` | 配置版本与服务注册前缀 |

## 库

| 库 | 谁用 | 放什么 |
| --- | --- | --- |
| MySQL `vi_bill` | vi-as 写，vi-billcenter、vi-pusher 读写 | 在途话单 `bill_new`，失败 `bill_fail`，按日历史表 |
| MySQL `user_qc` | vi-as 读，qa-server 读写 | 号码报备、四级企业、号段、ICSCF、质检策略 |
| MySQL `vi_strategy` | vi-cront-jobs | 话务管控策略配置 |
| MySQL 质检库 | qa-server | `qa_task`、`qa_rule`、词包、话术。库名以部署配置为准 |
| StarRocks 海山 | qa-server 写，scheduler 读 `qa_detail` | 质检明细 `qa_detail`、`qa_segment` |
| ClickHouse `mid_bussops` | vi-cront-jobs | 管控明细与日统计 |
| MongoDB | scheduler | `task`、`sub_task`、`process`、`shard_task`、`model_meta`、`ai_model`。旧 MySQL DDL 当前不用 |
| Redis | vi-cront-jobs、qa-server、scheduler | 见 `redis/keys.yaml` |
| ETCD | 各服务 | 见 `etcd/keys.yaml` |

契约变更要同步对应文件，并在 PR 里写明是否兼容。字段说明写业务含义。不要把密码、内网地址和真实号码写进这些文件。
