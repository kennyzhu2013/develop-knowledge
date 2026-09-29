---
name: add-qc-rule
description: 新增或修改语音质检规则（投诉、骚扰、服务规范、涉诈规则等）。测试工程师可只使用其中的 Validation 部分设计用例。
roles: [developer, qa]
---

# Add QC Rule

## Preconditions

必须确认：质检类型、输入数据（转写文本 / 通话特征 / 说话人）、规则来源（业务方需求或案例）、输出（标签、风险等级）、生效范围（离线 / 实时）。缺任何一项先问用户。

## Workflow

1. 读 `02-call-qc/overview.md`、`02-call-qc/rules.md`、`02-call-qc/pipeline.md`。
2. 按 `rules.md` 找到规则事实源（代码 / 配置中心）和规则引擎代码。
3. 检查现有规则是否已覆盖或与新规则冲突（同类 ID 前缀下搜索关键词）。
4. 按规则 ID 规范编号，实现规则。
5. 写样本：正例、反例、边界、对抗样本（全部脱敏）。
6. 跑评测集，记录误报率 / 召回率。
7. 涉诈规则同步更新 `02-call-qc/fraud/rules.yaml`，并确认 `detects` 引用的 `RB-*` 存在。
8. 运行 `python tools/kb_lint.py`。

## Validation

- [ ] false positive：正常通话样本不命中
- [ ] false negative：已知风险样本命中
- [ ] 与现有规则无冲突或重复
- [ ] 性能：单条规则耗时在预算内（TODO：预算）
- [ ] 向后兼容：不影响已有标签的下游使用
- [ ] 实时质检场景下，增量文本也能正确判定（如适用）
