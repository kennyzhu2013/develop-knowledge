---
id: dev-db-change
title: 数据库变更规范
domain: development
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
---

# 数据库变更规范

- 所有 DDL 走迁移脚本（TODO：工具），不手工执行
- 大表加字段 / 加索引使用在线 DDL 工具（TODO），并评估锁表时间
- 字段删除分两步：先停止读写并发布，观察一个版本周期后再删除
- 变更后同步更新 `06-data/tables/<table>.yaml`
