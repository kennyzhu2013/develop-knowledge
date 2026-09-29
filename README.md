# 中间号 + 语音质检 工程知识库（Agent Engineering KB）

这是一套 **给 Code Agent 使用、同时给人读** 的工程知识库，覆盖中间号（含 700 号码、出海、呼叫路由、号码池、计费）与语音质检（实时/离线质检、涉诈、投诉、骚扰识别、ASR/大模型/RAG）整套系统。

设计基于 [`建议.md`](./建议.md)，并补齐了两件原方案没展开的事：

1. **多角色共用**：技术架构师、研发工程师、运维人员、测试工程师用 *同一个仓库*，通过「角色视图」加载各自需要的上下文，而不是每个角色维护一套。
2. **多 Agent 工具通用**：Claude Code / Cursor / Codex / Gemini CLI / Qwen Code / Trae / OpenCode / Copilot 都能自动读到同一份入口规则，不绑定某个厂商。

---

## 1. 核心设计（一句话版）

> **一个 Git 仓库 = 唯一事实源；`AGENTS.md` = 宪法；`knowledge.yaml` = 路由表；`roles/` = 角色视图；`99-agent/skills/` = 标准作业流程；`tools/` = 路由 / 校验 / 自动索引；CI = 知识质量门禁。**

```text
                    ┌────────── 技术架构师 / 研发 / 运维 / 测试 ──────────┐
                    │        （任意 Agent 工具，任意 IDE，无权限区分）        │
                    └───────────────────────┬───────────────────────────────┘
                                            │ 自动读取
               AGENTS.md  ←  CLAUDE.md / GEMINI.md / QWEN.md / copilot-instructions / .trae（都只是指针）
                                            │
                                   knowledge.yaml（域 + 角色 + 路由关键词）
                                            │
                     tools/kb_route.py "任务描述" --role developer
                                            │ 输出：本次任务该读的最小文件集 + 推荐 Skill
        ┌──────────────┬──────────────┬─────┴────────┬──────────────┬──────────────┐
    业务知识        架构/ADR        代码索引(YAML)   数据/MQ/API     运维 Runbook    经验/故障
   01~03 目录       04 / 09          05 目录          06 目录         07 目录         10 目录
                                            │
                                  99-agent/skills/*/SKILL.md
                                            │
                              Code / Test / Review / Deploy
                                            │
                    代码变更 → tools/gen_code_index.py → PR → 人工 Review → Merge
```

## 2. 为什么这样设计

| 问题 | 做法 |
| --- | --- |
| 不同 Agent 工具读取的入口文件名不同 | 只维护 `AGENTS.md`，其它入口文件都是一行指针，由 `tools/sync_adapters.py` 生成 |
| 不同角色关注点不同，但不想分仓库 | 文档 frontmatter 标 `audience`，`roles/*.md` 定义每个角色的阅读顺序和红线，路由器按角色裁剪 |
| Agent 一次读太多、上下文爆炸 | `knowledge.yaml` 定义关键词 → 最小知识集，`kb_route.py` 按任务召回，而不是全量加载 |
| 文档和代码对不上 | 代码知识（服务、接口、MQ、依赖）由 `gen_code_index.py` 从代码生成；冲突时按 `AGENTS.md` 的优先级处理并标 `CONFLICT` |
| 知识腐烂 | 每篇文档有 `owner` 和 `last_verified`，`kb_lint.py` 在 CI 里检查引用、格式、过期时间 |
| “为什么这么设计”丢失 | `09-decisions/` 放 ADR，`10-lessons/` 放故障与踩坑，永不删除，只能 `Superseded` |

不先上向量库：Phase 1~2 用 Markdown + YAML + 关键词路由就够用，而且所有 Agent 都能直接读文件。等文档量上来再加向量检索或 MCP 服务，也只是换掉路由这一层（见第 6 节）。

## 3. 目录

```text
.
├── AGENTS.md                  # 所有 Agent 的统一入口（系统宪法）
├── knowledge.yaml             # 总索引：域、角色、路由规则、服务清单
├── roles/                     # 角色视图：architect / developer / ops / qa
├── 00-context/                # 全局：业务总览、术语表、系统边界
├── 01-mid-number/             # 中间号：700 / 出海 / 呼叫路由 / 号码池 / 计费
├── 02-call-qc/                # 语音质检：流水线、实时质检、涉诈（fraud/ 结构化）…
├── 03-ai/                     # ASR / LLM / RAG / Agent / 模型服务
├── 04-architecture/           # 总体架构、中间件、存储、高可用、安全
├── 05-code/                   # 代码知识：仓库、服务（人工 YAML）、index/（代码自动生成）、依赖图、调用链
├── 06-data/                   # 数据库表、MQ Topic、事件、API 契约
├── 07-operation/              # 部署、监控告警、Runbook、应急预案
├── 08-development/            # 编码、测试、接口、数据库变更、发布规范
├── 09-decisions/              # ADR（为什么）
├── 10-lessons/                # 故障复盘、踩坑、性能问题
├── 99-agent/
│   ├── skills/                # 标准作业流程（SKILL.md，兼容 Claude/Cursor/Codex Skills 格式）
│   └── templates/             # 各类文档模板
├── tools/                     # kb_route / kb_lint / gen_code_index / sync_adapters
│
│   以下为 tools/sync_adapters.py 生成，勿手改：
├── CLAUDE.md  GEMINI.md  QWEN.md   # 各工具入口，内容只是 @AGENTS.md
├── .github/copilot-instructions.md
├── .trae/rules/project_rules.md
├── .claude/skills/            # Skills 镜像：Claude Code、VS Code Copilot
└── .agents/skills/            # Skills 镜像：Codex、Cursor
```

| Agent 工具 | 读取的入口 | 读取的 Skills |
| --- | --- | --- |
| Claude Code | `CLAUDE.md` → `AGENTS.md` | `.claude/skills/` |
| Cursor | `AGENTS.md`（原生） | `.agents/skills/`、`.claude/skills/` |
| Codex / OpenCode | `AGENTS.md`（原生） | `.agents/skills/` |
| Gemini CLI / Qwen Code | `GEMINI.md` / `QWEN.md` → `AGENTS.md` | 按 `AGENTS.md` 指引读 `99-agent/skills/` |
| GitHub Copilot | `.github/copilot-instructions.md` | `.claude/skills/` |
| Trae | `.trae/rules/project_rules.md` | 按 `AGENTS.md` 指引读 `99-agent/skills/` |

新增一种工具，只需在 `tools/sync_adapters.py` 的 `ENTRY_FILES` / `SKILL_MIRRORS` 中加一项。

## 4. 各角色怎么用

所有人都 clone 同一个仓库，不做权限区分。区别只在于告诉 Agent “我是谁”：

| 角色 | 对 Agent 说 | Agent 会优先加载 |
| --- | --- | --- |
| 技术架构师 | “我是架构师，评估实时质检改为流式 ASR 的影响” | `roles/architect.md` → 架构、ADR、依赖图 → `architecture-review` / `impact-analysis` |
| 研发工程师 | “我是研发，给 700 号码申请接口加一个字段” | `roles/developer.md` → 业务流程、服务 YAML、API/DB 契约、开发规范 → `modify-api` |
| 运维人员 | “我是运维，质检结果延迟告警了” | `roles/ops.md` → 服务运行信息、监控、Runbook、历史故障 → `troubleshoot` |
| 测试工程师 | “我是测试，给新涉诈规则设计用例” | `roles/qa.md` → 业务规则、样例、测试规范 → `add-qc-rule` 的验证部分 |

也可以显式调用路由器，任何 Agent 都能跑：

```bash
python tools/kb_route.py "修改700号码的号码申请接口" --role developer
python tools/kb_route.py "质检结果延迟告警" --role ops --format json
```

## 5. 怎么接到代码仓库里

三种方式任选，推荐 A + B：

- **A. 多根工作区**（最简单）：知识库与各业务代码仓库放在同级目录，在 IDE / Agent 中同时打开。`05-code/repositories.yaml` 里写好每个仓库的相对路径。
- **B. Git submodule**：在每个业务仓库中 `git submodule add <kb-repo> .kb`，并在业务仓库根目录的 `AGENTS.md` 里加一行 “先阅读 `.kb/AGENTS.md`”。这样只打开业务仓库的人也能用。
- **C. 只读 MCP / HTTP 服务**（Phase 3 可选）：把 `kb_route.py` 包装成 MCP 工具（`route_task`、`read_doc`、`list_services`），给不方便 clone 的场景用。知识仍以 Git 为准。

## 6. 落地节奏

| 阶段 | 目标 | 要做的事 |
| --- | --- | --- |
| Phase 1 知识资产化 | 能读 | 填 `00-context`、`01`、`02`、`04` 的 overview；每个服务一份 `05-code/services/*.yaml`；核心表、Topic、接口契约；Top 10 历史故障；补 5~10 篇关键 ADR |
| Phase 2 Agent 可用 | 能用 | 完善 `knowledge.yaml` 路由；打磨 10 个核心 Skill；`gen_code_index.py` 接入各仓库；CI 开启 `kb_lint.py`；验收标准：新 Agent 30 分钟内建立系统认知 |
| Phase 3 自动演进 | 能长 | 业务仓库合并后自动跑索引并向知识库提 PR；故障复盘自动生成 `10-lessons` 草稿；需要时增加向量检索 / MCP，但只替换路由层 |

**知识更新规则**：Agent 可以起草，必须走 PR + 人工 Review，不允许直接改 `main`。

## 7. 常用命令

```bash
pip install -r tools/requirements.txt

python tools/kb_route.py "任务描述" --role developer      # 召回该读的文件
python tools/kb_lint.py                                     # 知识库校验（CI 同款）
python tools/kb_lint.py --write-deps                        # 按 services/*.yaml 重新生成依赖图
python tools/gen_code_index.py --repo ../call-qc --service call-qc-service           # 扫描代码，输出索引与 DRIFT
python tools/gen_code_index.py --repo ../call-qc --service call-qc-service --write   # 写入 05-code/index/
python tools/sync_adapters.py                               # 修改 AGENTS.md 或 Skills 后重新生成各工具入口
```

`kb_lint.py` 检查的内容：frontmatter 必填字段与取值、文档 id 唯一与 `related` 引用、正文中引用的路径是否存在、`knowledge.yaml` 中的路径 / Skill / 服务、服务 YAML 引用的仓库 / 事件 / Runbook、反诈 FP / RB / RULE 之间的 ID 引用、架构图节点是否为已登记服务、依赖图是否过期、疑似手机号和密钥、`last_verified` 是否过期。

`gen_code_index.py` 可以接入业务仓库的 CI：合并到主干后运行 `--write` 并向本知识库自动提 PR；加 `--fail-on-drift` 可以在代码与知识库契约不一致时阻断。

## 8. 写文档的规则

- 每篇 `.md` 必须有 frontmatter（见 `99-agent/templates/doc.md`）：`id`、`title`、`domain`、`kind`（stable / dynamic / historical）、`audience`、`owner`、`last_verified`。
- 会变的东西（地址、版本、依赖、Topic、字段）写 YAML，不写在正文里。
- 一篇文档只讲一件事，控制在 300 行以内；超出就拆分，在 overview 里做索引。
- 代码能说明的事实不要手写，写 `source_of_truth` 指向代码路径。
- 不写密码、Token、客户号码、真实通话内容；样例数据必须脱敏。
