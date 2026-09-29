---
name: release-check
description: 发布、灰度、回滚前的检查清单。研发提交发布、运维执行发布或测试确认上线时使用。
roles: [ops, developer, qa]
---

# Release Check

## Workflow

1. 读 `08-development/release.md` 和 `07-operation/deployment.md`。
2. 汇总本次发布的变更：代码 diff、配置、DDL、MQ、规则、模型版本。
3. 对每类变更逐项检查下面的清单。
4. 输出发布单草稿：变更内容、影响范围、灰度计划、观察指标、回滚步骤。

## Checklist

- [ ] 测试通过（单元 / 契约 / 集成 / 规则评测）
- [ ] 契约变更已通知下游，且向后兼容
- [ ] DDL 已在发布前单独执行或已确认顺序
- [ ] 配置 / 开关已在各环境准备好
- [ ] 灰度比例与观察指标已明确
- [ ] 回滚步骤可执行，且已确认数据兼容（回滚后旧版本能读新数据）
- [ ] 监控看板与告警覆盖新功能
- [ ] 知识库已更新（服务 YAML、契约、Runbook、模型版本表）
