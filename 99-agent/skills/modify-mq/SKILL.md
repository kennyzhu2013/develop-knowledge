---
name: modify-mq
description: 新增或修改 MQ Topic、业务事件结构、生产者或消费者逻辑（如 CallCompleted、QCCompleted、FraudDetected）。
roles: [developer, architect]
---

# Modify MQ / Event

## Workflow

1. 读 `06-data/events/events.yaml` 和 `06-data/mq/topics.yaml`。
2. 执行 `impact-analysis`，重点确认知识库之外的消费者（数据平台、计费等）。
3. 结构变更规则：只新增字段；字段语义变更视为新事件或新版本。
4. 消费者改动：确认幂等（重复消费）、顺序性要求、失败重试与死信处理。
5. 更新 `events.yaml`、`topics.yaml` 以及生产 / 消费服务的 `05-code/services/*.yaml`。
6. 运行 `python tools/kb_lint.py`；需要时运行 `python tools/kb_lint.py --write-deps` 刷新依赖图。

## Checklist

- [ ] 向后兼容，老消费者可以忽略新字段
- [ ] 消费幂等
- [ ] 顺序性要求已确认（是否按 `call_id` 分区）
- [ ] 积压 / 失败有告警和 Runbook
- [ ] 知识库事件与服务文件已同步
