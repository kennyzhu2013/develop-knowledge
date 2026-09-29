---
name: modify-database
description: 新增表、修改表结构、加索引、数据迁移。在任何 DDL 或批量数据修改之前使用。
roles: [developer, ops]
---

# Modify Database

## Workflow

1. 读 `08-development/database-change.md` 和 `06-data/tables/<table>.yaml`。
2. 执行 `impact-analysis`，找出所有读写方（`readers` / `writers` + 代码搜索表名）。
3. 评估数据量与锁表风险，选择在线 DDL 方式。
4. 编写迁移脚本（包含回滚脚本），不在对话中直接给出生产执行命令。
5. 字段删除按“两步法”：先停读写并发布，再删除。
6. 更新 `06-data/tables/<table>.yaml`（新表从 `99-agent/templates/table.yaml` 复制）。

## Checklist

- [ ] 迁移脚本与回滚脚本
- [ ] 大表变更已评估耗时
- [ ] 所有读写方已适配
- [ ] 表文档已更新
