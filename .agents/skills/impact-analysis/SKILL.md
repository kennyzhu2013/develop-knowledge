---
name: impact-analysis
description: 评估一次改动（接口、MQ 消息、表结构、规则、公共代码、配置）会影响哪些服务、数据、测试和运行。在修改任何对外契约或公共逻辑之前使用。
roles: [architect, developer, qa]
---

# Impact Analysis

## Preconditions

明确改动对象：是接口、事件/MQ、表、规则、配置还是公共代码，以及改动类型（新增 / 修改 / 删除）。

## Workflow

1. **找到改动对象的所有者**：在 `05-code/services/*.yaml` 中定位它属于哪个服务。
2. **沿依赖图展开**：
   - 接口：搜索所有 `dependencies.services` 包含该服务的服务；再在代码中搜索接口路径确认调用方；外部调用方查 `00-context/system-boundary.md`。
   - 事件 / MQ：在 `05-code/services/*.yaml` 中查 `events.consume` 包含该事件的服务；查 `06-data/mq/topics.yaml` 的 `consumer_groups`（可能有知识库外的消费者，如数据平台）。
   - 表：查 `06-data/tables/<table>.yaml` 的 `readers` / `writers`，再在代码中搜索表名。
   - 规则：查 `02-call-qc/fraud/*.yaml` 中引用该规则的模式与行为。
3. **在代码中核实**：知识库里的依赖只是起点，必须用代码搜索确认；不一致时输出 `CONFLICT`。
4. **查历史**：在 `09-decisions/` 和 `10-lessons/` 中搜索改动对象的名字。
5. **评估兼容性**：是否向后兼容；不兼容时给出迁移步骤（双写、版本共存、灰度）。

## Output

| 影响对象 | 类型 | 影响 | 需要的动作 | 依据 |
| --- | --- | --- | --- | --- |

并给出：风险等级（高 / 中 / 低）、需要通知的下游、需要补的测试、需要更新的知识库文件。
