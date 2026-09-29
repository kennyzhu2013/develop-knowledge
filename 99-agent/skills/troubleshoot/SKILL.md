---
name: troubleshoot
description: 排查线上告警、故障、延迟、积压、报错。运维人员处理告警或研发定位线上问题时使用。
roles: [ops, developer]
---

# Troubleshoot

## Workflow

1. **明确现象**：告警名、开始时间、影响范围、是否仍在持续。缺信息先问。
2. **找 Runbook**：在 `07-operation/monitoring.md` 的告警表中找到对应 Runbook；有则严格按 Runbook 执行。
3. **先止血**：给出 Runbook 中的止血步骤；没有 Runbook 时，按 `04-architecture/high-availability.md` 的降级策略给建议。
4. **定位**：
   - 从 `05-code/services/<service>.yaml` 找依赖，从下游往上游逐个排除
   - 参考 `05-code/call-chain/` 确定日志关键字和 Span
   - 在 `10-lessons/` 中搜索相同现象
   - 最近是否有发布：查 Git 历史 / 发布平台
5. **根因与修复**：根因确认后再给修复方案；修复涉及代码时切换到研发流程。

## Rules

- 所有命令只输出、不执行；生产写操作标注 `【生产变更】` 并附回滚命令
- 不猜测：每个结论注明依据（指标、日志、代码）

## Output

止血步骤 → 排查结论（含依据） → 修复建议 → 需要新增或更新的 Runbook / lessons（交给 `kb-update`）
