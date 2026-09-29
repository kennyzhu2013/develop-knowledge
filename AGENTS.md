# AGENTS.md — 中间号与语音质检工程知识库

> 本文件是所有 Code Agent（Claude Code、Cursor、Codex、Gemini CLI、Qwen Code、Trae、OpenCode、Copilot 等）的统一入口。
> `CLAUDE.md`、`GEMINI.md`、`QWEN.md`、`.github/copilot-instructions.md`、`.trae/rules/` 都只是指向本文件的指针，`.claude/skills/` 与 `.agents/skills/` 是 `99-agent/skills/` 的镜像，均由 `tools/sync_adapters.py` 生成，请只修改本文件和 `99-agent/skills/`。

## 1. 系统范围

- **中间号**：隐私号绑定/解绑、呼叫路由、号码池、700 号码、中间号出海、话单与计费
- **语音质检**：录音获取、ASR 转写、离线质检、实时质检、语义质检、涉诈识别、投诉判定、骚扰识别、风险分级
- **AI 能力**：ASR、通话大模型、RAG、质检/反诈 Agent、模型服务
- **支撑体系**：中间件（MQ、缓存、存储）、数据平台、部署、监控、应急

系统之间的关系见 `04-architecture/overall.md`，术语见 `00-context/glossary.md`。

## 2. 开始任何任务前

1. **确认角色**：用户是架构师 / 研发 / 运维 / 测试中的哪一类。用户没说时，根据任务推断（设计评审→architect，改代码→developer，告警/故障/部署→ops，用例/验证→qa），并在回复开头说明你按哪个角色工作。然后阅读 `roles/<role>.md`。
2. **召回知识**：运行 `python tools/kb_route.py "<任务描述>" --role <role>`，只读取它列出的文件；无法运行脚本时，手动查 `knowledge.yaml` 的 `routes`。
3. **加载 Skill**：如果路由结果推荐了 Skill，阅读 `99-agent/skills/<skill>/SKILL.md` 并按其流程执行。
4. **定位代码**：通过 `05-code/services/<service>.yaml` 找到仓库和模块，再去代码中核实。

不要一次读完整个知识库。

## 3. 事实优先级

```text
运行中的代码 / 配置 > 自动生成的代码索引(05-code) > 结构化 YAML > 架构文档 / ADR > 设计文档 > 会议纪要 > Agent 推测
```

知识库与代码不一致时：

- 以代码为准完成当前任务，**不要**自行判断哪边是“对的”业务意图；
- 在回复中输出 `CONFLICT: <文档路径> vs <代码路径>: <差异>`；
- 如果用户同意，起草一个修正知识库的改动（走 PR）。

## 4. 变更红线（所有角色）

禁止：

- 未理解业务流程就重构
- 修改公共接口 / MQ 消息结构 / 数据库字段，而不检查所有上下游（用 `impact-analysis` Skill）
- 修改质检规则、涉诈规则而不同步更新 `02-call-qc/` 下的规则文档和样例
- 删除看起来“奇怪”的重试、兜底、兼容逻辑而不查 `09-decisions/` 和 `10-lessons/`
- 在代码、日志、文档中写入真实手机号、通话录音文本、密钥、Token
- 在生产环境执行任何写操作；运维类任务只输出命令和步骤，由人执行

## 5. 不确定时

按顺序：搜知识库 → 搜代码 → 查 Git 历史 / blame → 查 ADR 和 lessons → 再问用户。
提问时说明你已经查过什么、还缺什么。

## 6. 维护知识库

- 完成任务后，如果发现知识缺失或过期，按 `99-agent/skills/kb-update/SKILL.md` 起草更新。
- 新文档从 `99-agent/templates/` 复制模板，必须带 frontmatter。
- 提交前运行 `python tools/kb_lint.py`，必须零错误。
- 知识库改动只能通过 PR 合入，由对应 `owner` Review。

## 7. 目录速查

| 目录 | 内容 | 典型读者 |
| --- | --- | --- |
| `00-context/` | 业务总览、术语、系统边界 | 所有人 |
| `01-mid-number/` | 中间号各子业务 | 研发、架构 |
| `02-call-qc/` | 质检流水线与各类识别（`fraud/` 为结构化反诈知识） | 研发、架构、测试 |
| `03-ai/` | ASR、LLM、RAG、Agent、模型服务 | 研发、架构 |
| `04-architecture/` | 总体架构、中间件、存储、高可用、安全 | 架构 |
| `05-code/` | 仓库、服务、依赖、调用链（自动生成优先） | 研发 |
| `06-data/` | 表、Topic、事件、API 契约 | 研发、测试 |
| `07-operation/` | 部署、监控、Runbook、应急 | 运维 |
| `08-development/` | 编码、测试、发布规范 | 研发、测试 |
| `09-decisions/` | ADR | 架构、研发 |
| `10-lessons/` | 故障复盘与踩坑 | 所有人 |
| `99-agent/` | Skills 与模板 | Agent |
