---
id: data-overview
title: 数据与契约 - 总览
domain: data
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-storage]
---

# 数据与契约

| 目录 | 内容 | 格式 | 事实源 |
| --- | --- | --- | --- |
| `tables/` | 表结构（一表一文件） | YAML，模板 `99-agent/templates/table.yaml` | 数据库 DDL / 迁移脚本 |
| `mq/topics.yaml` | Topic 清单、生产者、消费者 | YAML | 代码中的生产 / 消费配置 |
| `events/events.yaml` | 业务事件及字段 | YAML | 代码中的消息 DTO |
| `api/` | 接口契约 | 优先 OpenAPI 文件；否则 Markdown | 代码 / 网关配置 |

规则：

- 契约变更必须同步更新对应文件，并在 PR 中标明是否向后兼容
- 字段说明写“业务含义”和“取值范围”，不要只写类型
