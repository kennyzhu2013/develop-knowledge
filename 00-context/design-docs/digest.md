---
id: context-design-docs-digest
title: 专线质检设计文档整理（15 份 docx 汇编）
domain: context
kind: historical
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
source_of_truth: [vi-assets/专线质检方案+需求.docx, vi-assets/专线质检-媒体实现详细设计接口.docx, vi-assets/语音专线质检信息报备接口说明书_v1.0.1_20260325.docx]
---

> 本文是 `vi-assets/*.docx` 设计文档的结构化整理（含流程图与时序图的文字化），示例中的号码、地址、密钥已替换为占位符。
> 设计文档的事实优先级低于代码；与代码冲突的条目见 `10-lessons/pitfalls/doc-code-conflicts.md`。

# 专线质检（vi）设计文档整理报告

> 来源：`/workspace/vi-assets/*.docx`（15 份，已忽略 `~$` 临时文件），文本版 `/tmp/vi/docs/*.txt`，图片 `/tmp/vi/img/<文档名>/`。
> 本报告只整理文档事实；文档自身不清楚、或文档之间不一致之处，标注为“待确认”，并在第 6 节集中列出。
> 安全处理：文档样例中出现的手机号、固话号、内网 IP、密钥样例一律不转录，用 `1xxxxxxxx01`、`700xxxx0001`、`<内网地址>:端口` 这类占位符代替。

## 0. 文档清单

| # | 文档 | 内容类型 | 图片 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 专线质检方案+需求 | 需求 + 方案 + 原型 | 8 张（2 张时序图、6 张原型/低保真） | 主文档 |
| 2 | 专线质检各模块交互逻辑图 | 纯图 | 1 张长图（开户/能力同步/子账户/质检任务/计费 5 个流程） | 文本为空 |
| 3 | 一期-准实时质检 | 纯图 | 1 张（一期流程图） | 文本只有标题 |
| 4 | 二期-实时分片质检 | 纯图 | 1 张（二期流程图） | 文本只有标题 |
| 5 | 媒体 | 纯图 | 4 张（3 种媒体处理模式、媒体主循环流程、信令/媒体时序、媒体缓存架构） | 文本为空 |
| 6 | 专线质检-媒体实现详细设计接口 | 接口/消息 | 无 | 2 个 MQ、1 个 WS、2 个 HTTP 接口，另有 1 个只给出链接 |
| 7 | 语音专线质检信息报备接口说明书_v1.0.1_20260325 | 对外接口 | 无 | 鉴权 + 6 个业务接口 |
| 8 | 话单DDL | DDL | 无 | `vi_bill.bill_new`、`vi_bill.bill_template` |
| 9 | 二期话单DDL | DDL（ALTER） | 无 | 新增 3 个质检/管控字段 |
| 10 | 号码表DDL | DDL | 无 | `user_qc.biz_num_auth` |
| 11 | 号段登记表DDL | DDL | 无 | `user_qc.phone_segment_area_code_info`，附初始化数据 sql（26.41 MB，未随附） |
| 12 | ICSCF信息登记表DDL | DDL | 无 | `user_qc.chinamobile_icscf_ip`，附初始化数据 sql（66.21 KB，未随附） |
| 13 | 话务管控策略表DDL | DDL | 无 | MySQL `vi_strategy` 6 张策略表 + ClickHouse `mid_bussops` 明细/统计 2 张表 |
| 14 | 专线质检策略管控服务 redis数据结构 | Redis | 无 | 13 类 key |
| 15 | 话务管控策略运行逻辑（测试关注） | 运行逻辑 | 无 | cront-jobs 脚本逻辑 + 话务模型 SQL 片段 |
| 16 | 话务管控上线依赖 | 上线文档 | 1 张图片目录存在（文本中无图片引用） | 建库建表 SQL + Python 环境 + crontab |

> 说明：目录 `/tmp/vi/img/话务管控上线依赖/` 存在，但本次图片清单中该目录下没有图片文件（`find` 结果为空），待确认原 docx 是否有图。

---

## 1. 业务背景与需求

### 1.1 专线质检是什么

- **目标**（方案+需求 §1）：把“语音专线”的通话接入质检平台，实现对通话的**实时质检**与**通话管控**，提升服务质量与合规性。三个核心目标：
  - 实时质检：对通话内容实时分析；
  - 智能管控：根据质检结果对通话进行干预（接续 / 放音 / 插播 / 阻断 / 挂断）；
  - 多场景覆盖：同时支持**专线用户**和**固话（IMS 实体卡）用户**接入。
- **本质**：复用 700 平台（中间号大网）的话路能力，把业务方的呼叫“绕”进 700 平台，平台恢复原始主被叫后正常接续，同时对通话录音并送质检；质检结果（风险等级、正常/异常）回推给业务方，后续还可回推管控指令给 AS 干预通话。

### 1.2 服务对象

- **业务方（客户）**：使用语音专线 / IMS 固话外呼的企业（典型是催收、金融、教育、房产、政务、电商、企业办公、快递、医疗 9 类场景，见报备接口 `scene` 枚举）。
- **客户组织层级**（四级企业，贯穿所有表）：
  | 层级 | 字段 | 含义（文档原文） | 例 |
  | --- | --- | --- | --- |
  | 一级 | `mobile_id` | 一级移动 id（省公司） | 某省移动 |
  | 二级 | `channel_id` | 二级移动 id（市公司） | 某市移动 |
  | 三级 | `company_id` | 三级企业 id / 三级移动 id（区县公司 / 接入企业） | 某区县分公司 / “鸿运通” |
  | 四级 | `customer_id` | 四级终端客户 id（终端企业） | “美团”类终端客户 |
- **质检账户**：在“对客质检平台”主账户表中，以 `app_id`（如 `AB001`，报备接口中叫 `channelId`/“质检账户Id”）标识专线质检客户主体；主体由能力开放平台（能开）开户。
- **内部使用者**：对客质检门户（企业用户按层级查看自己的数据）、对客质检后台管理网站（运营人员全量查看、维护号码报备表）。

### 1.3 700 号码与 7001 前缀

| 概念 | 文档描述 | 来源 |
| --- | --- | --- |
| 700 号码 | 700 平台（中间号大网）承载的号码；普通 700 号码**总长度 15 位** | 方案+需求 §3.1.2 |
| 7001X 前缀 | 业务方在起呼时给被叫统一加 `7001X` 前缀：原始 `A -> B` 变为 `A -> 70011B -> B` | 方案+需求 §2.1.1 |
| 识别规则 | xproxy 识别“被叫以 7001X 开头且**总长度 ≠ 15 位**” → 专线质检链路，路由到 xdtmf（vi-AS）；总长度 = 15 位 → 按原 700 大网流程分发 | §2.1.1、§3.1 |
| 号码还原 | xdtmf 去掉 `70011` 前缀得到原始被叫（例：`70011` + `1xxxxxxxxxx` → `1xxxxxxxxxx`） | §2.1.1 |
| 70014 前缀路由码 | 号码表 DDL、ICSCF DDL 提到“70014 前缀路由码的业务触发方式”；ICSCF 表用于获取触发 700 基础平台的 ICSCF 地址 | 号码表DDL、ICSCF DDL |
| 业务号码（biz_number） | 70014 前缀方式 → 报备的主叫号码；工作号主叫签约 → 主叫号码；被叫签约 → 被叫号码 | 号码表DDL |

> 待确认：`7001X` 的 X 取值集合（文档中出现 `70011` 与 `70014` 两种，含义是否不同、是否还有其他取值）。

### 1.4 两种接入方案

| 维度 | 方案一：7001 路由链路（ISBC） | 方案二：实体卡签约（IMS） |
| --- | --- | --- |
| 接入方式 | 起呼时被叫加 7001X 前缀，经 700 大网路由进入平台 | 对 IMS 固话做主叫/被叫签约（复用工作号主/被叫签约点），话路送至中间号 |
| xproxy 识别 | 被叫 7001X 开头且总长度 ≠ 15 位 | 主/被叫为工作号签约方式且**非手机号** |
| 号码匹配 | 主叫与号码报备表匹配 | 固话主叫/被叫与号码报备表匹配；回拨时原主叫作为被叫也要匹配 |
| 匹配失败 | **拒绝接续**（时序图：“拨打号码为空号，流程结束”） | **放通通话，但不进入质检**（§2.2.1、§3.2；但 §2.2.2 写“不进行接续”，矛盾，见第 6 节） |
| 号显 | 号显为主叫号码 | 文本：号显为主叫号码；时序图：主叫流程“接听、号显工作号”、回拨流程“接听、号显被叫” |
| 回拨 | 回拨不进入平台、不质检 | 回拨（被叫流程）进入平台，匹配成功则质检；时序图回拨流程中 xdtmf 还多一步“5）推送通话话单” |

### 1.5 一期（准实时质检）与二期（实时分片质检）的区别

依据：方案+需求时序图中的【一期需求】/【二期需求】色块、一期流程图、二期流程图、二期话单 DDL。

| 维度 | 一期：准实时质检（通话后质检） | 二期：实时分片质检 |
| --- | --- | --- |
| 质检时机 | 通话结束、合音完成后发起“离线/通话后质检” | 通话中按分片发起实时质检 |
| ASR 取音 | ASR 拉取**完整通话录音** | ASR 拉取**分片录音** |
| xdtmf 读取的质检参数 | 1）号码归属企业 ID【一期】 | 2）质检配置：实时质检 or 通话后【二期】；3）回调地址【二期】；4）分片控制：是否分片录音【二期】 |
| 媒体组件（图中命名） | xproxy（7001 前缀统一入口）→ vi-AS → dcc-media(XCache) → dcc-merge → dcc-mdown → PFS | xproxy（7001 前缀/实体卡，前置 SLB 分发）→ vi-AS → vi-media(Xcache) →WS(rtp 流、质检配置、事件)→ vi-codec →WS(pcm、质检配置)→ vi-merge →上传→ vi-mdown(Xcache) → PFS；新增 offline-merge（消费分段合音消息，HTTP 上传文件到 vi-mdown）、cdn（从 PFS 获取放音文件，向 vi-media 提供放音文件） |
| 话单/推送 | vi-bill 收通话消息、合音结果消息，查库；vi-bill → vi-pusher → 客户 | 同一期，另 vi-pusher **走网关推送**经 httpProxy 到客户；vi-bill 新增“HTTP 质检结果通知”回 vi-AS（为管控） |
| 质检链路 | vi-bill →HTTP（代理 SLB）→ qa-server →MQ 通知质检→ scheduler → ASR / anti-fraud；scheduler →MQ 质检结果回调→ qa-server →质检回调→ vi-bill；qa-server 入库海山数据库；scheduler 写 MongoDB（图中写作 “MangoDB”） | 同一期，但 qa-server 先**缓存写入 Redis，再定时入库海山数据库** |
| 管控 | 无 | （待定）qa_server 生成管控意见：接续/放音/挂断 → xdtmf 生成管控指令 → 700 大网执行；话单新增 `control_action`（1-仅放音 2-仅阻断 3-先放音再阻断） |
| 话单字段 | `bill_new` 基础字段 | 新增 `quality_inspection_segment_result`、`quality_inspection_final_result`、`control_action` |
| 录音分片 | 无 | 媒体按 `initialSegments`（首片秒数）+ `segments`（后续每片秒数）分片；每个分片有唯一话单 id、录音 id、录音 url（§2.1.1 “录音分片控制（待定）”） |
| 中间件 | ETCD、RocketMQ（AS/媒体侧）、RabbitMQ（qa-server/scheduler 侧）、MySQL（话单库）、专线质检配置库、海山数据库、MongoDB | 一期全部 + Redis（qa-server 缓存） |
| 对客平台 | 查询质检配置库、海山数据库 | 增加“听录音”（经 vi-mdown 从 PFS 取） |

> 命名对照（同一组件在不同文档中的名字）：
> | 职责 | 需求文档 | 一期图 | 二期图 / 时序图 | 本次源码仓库 |
> | --- | --- | --- | --- | --- |
> | 质检 AS（号码匹配、话单） | xdtmf、7001 专线 AS“归属查询” | vi-AS | vi-AS、“AS（CT+IT）/(billcenter)” | vi-as-master、vi-billcenter-master |
> | 录音/媒体 | xmedia、vi-media | dcc-media(XCache) | vi-media(Xcache)、stream-media | vi-media-master |
> | 实时编解码 | vi-mstream | — | vi-codec、codec | 不在源码中 |
> | 合音 | vi-merge、stream-merge | dcc-merge | vi-merge、offline-merge | vi-merge-master、offline-merge-master |
> | 文件上传/下载 | — | dcc-mdown | vi-mdown(Xcache) | 不在源码中 |
> | 话单中心 | xdtmf 的话单中心 | vi-bill | vi-bill | vi-billcenter-master |

### 1.6 功能需求（方案+需求 §3，逐条）

#### 1.6.1 xproxy：通话分发控制模块（一期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 1. 700 呼叫识别 | 识别被叫号码为 700 开头且位数不等于 15 位的呼叫，触发 700 质检专线流程 | 仅 7001 路由链路方案触发 |
| 2. 工作号签约识别 | 识别主/被叫为工作号签约方式且非手机号的呼叫，触发实体卡签约专线流程 | 仅实体卡签约方案触发 |
| 3. 非质检呼叫分发 | 将非质检呼叫按原有大网流程正常分发（号码总长度为 15 时） | 不干预 700 普通通话 |
| 4. 质检呼叫路由 | 将触发质检的呼叫精准路由至 xdtmf 模块 | 保证话路不中断 |

#### 1.6.2 xdtmf：企业与质检参数管理模块（一期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 1. 企业归属查询 | 接收质检呼叫后，通过号码报备表匹配主叫号码是否在表中；匹配上还需读取主叫对应的企业信息（`customer_id`） | 支持精准匹配、前缀匹配 |
| 2. 通话接续控制 | 根据归属查询结果向大网返回接续/拒绝指令：7001 链路未匹配 → 拒绝接续；实体卡未匹配 → 仅放通，不进入质检 | 原文备注：“通话：7001 报备号码接续，非报备号码拒绝；通话：实体卡无论是否报备号码都需接续；质检：报备号码接续，非报备号码拒绝”（最后一句语义不清） |
| 3. 质检参数传递 | 将通话信息（话单 ID、企业 ID、主叫、被叫）传给 xmedia 模块 | 按约定格式封装 |
| 4. 通话话单入库 | 封装通话话单 | 参考 700 话单表结构 |
| 5. 质检回调接收（后期完成） | xdtmf 提供回调地址，接收 qa_server 返回的质检处理意见 | 处理意见即实时质检的管控建议 |
| 6. 生成管控指令（后期完成） | 接续、放音、插播、阻断等指令 | — |
| 7. 话单推送（后期完成） | 将通话话单推送给业务方（客户） | — |

实现约定（与开发沟通结论）：号码报备表拆成两份载入内存——精确号码、前缀号段；**先精确匹配，未命中再前缀匹配**；内存数据通过 **etcd 刷新**（对客质检平台提供“etcd 更新”按钮人工触发）。

#### 1.6.3 vi-media：录音与音频分片模块（二期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 1. 录音 | 对接通话话路，实现通话全程实时录音 | 仅对质检前录音进行缓冲 |
| 2. 实时音频传输 | 质检前缓冲录音推送；实时音频帧和补帧传输 | 需重新定义协议；音频帧采用存储格式，非传输格式 |

（“录音”“分片”详细说明为空。）

#### 1.6.4 vi-mstream：音频实时流模块（二期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 实时解码 | 实时解码音频帧或音频片段，支持多采样率、多音频格式 | — |
| 实时流协议 | 服务发现；按标准 20ms 间隔实时推送解码音频帧、分片配置 | — |

（“实时编解码”详细说明为空。）

#### 1.6.5 vi-merge（二期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 实时合音 | 将解码后的分片音频流实时合成为完整通话音频 | — |
| PFS 存储 | 将合音后的完整音频与分片音频存储至 PFS | 存储时长 ≥ 2 个月 |
| 质检发起 | 根据 xdtmf 传递的质检参数向 qa_server 发起实时质检请求 | 打包话单 id、企业 id、主叫、被叫、质检能力等 |
| ws 实时流处理 | （空） | — |

（“合音 / PFS 存储 / 质检发起 / 质检结果透传”详细说明为空。）

#### 1.6.6 qa_server

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 质检结果回调地址 | 支持多个回调地址，回调结果给 xdtmf 和客户，提供质检处置意见 | 一般用于实时质检需通话干预场景 |
| 分发质检任务 | 接收 stream-merge 提交的质检任务参数，分发给调度系统 | 接收话单 id、主叫、被叫 |
| 质检任务参数更新 | 质检参数新增 userdata（主被叫等话单信息） | — |
| 质检结果参数更新 | 质检结果包含 userdata | — |
| 质检话单入库 + 推送 | 1. 质检话单入库；2. 通过企业回调地址推送质检话单；3. 通过 xdtmf 回调地址推送质检结果 | — |

细则：企业表新增“xdtmf 回调地址”字段（与企业客户回调地址都记录在对客质检平台企业表中）；qa_server 收到媒体模块的质检请求后创建 <质检任务参数> + <质检任务流> 发给调度系统，质检任务流由我方提前为客户配置；userdata = 话单 id、企业 id、主叫、被叫；质检子任务完成后生成质检话单（带 userdata）入库，分别推送给客户和 xdtmf。

#### 1.6.7 调度系统（scheduler）

| 功能点 | 需求描述 |
| --- | --- |
| 质检任务参数更新 | 质检参数新增 userdata |
| 质检结果参数更新 | 质检结果包含 userdata；任务完成回传结果时带上 userdata |

#### 1.6.8 对客质检平台（一期）

| 功能点 | 需求描述 | 备注 |
| --- | --- | --- |
| 通话话单查询 | 查询通话话单 | 话单 id 作为串联标识 |
| 质检话单查询 | 查询质检话单：话单 id、质检任务 id、主叫、被叫、企业 id、质检结果 | 话单 id 作为串联标识 |
| 号码报备功能 | 新增号码报备表，供 AS 做号码匹配 | — |
| 企业管理 & 开户迭代 | 质检账户类型、质检模式、分片控制、质检能力等 | 详见外链“企业管理迭代 | 知识库管理”（未随附） |

**(a) 通话话单查询**（入口：对客质检系统 → 通话管理 → 话单管理）

筛选区：

| 筛选项 | 交互类型 | 说明 |
| --- | --- | --- |
| 话单 ID | 输入框 | 精准匹配 |
| 终端客户 ID | 输入框 | 精准匹配 |
| 一/二/三/四级企业 | 层级下拉（支持复选） | 按当前用户所在企业层级：自身及以上层级为单一默认值，以下层级可复选/多选/全选 |
| 呼叫状态 | 下拉多选 | 空号/被叫忙/接通/挂机等，默认全部（原型中还出现“主叫放弃”） |
| 时间范围 | 日期范围选择器 | 默认近 30 天，最大不超过 180 天；超限提示“查询时间范围不能超过半年，请重新选择”；快捷项：今天/近 3 天/近 7 天/近 30 天 |
| 通话时长 | 下拉 + 输入框 | `>` / `<` / 区间（秒） |

操作：搜索（实时刷新）；导出 Excel（有权限才可点，无权限置灰并悬停提示“暂无导出权限”；含全部分页、最多 1 万条；文件名规则见第 6 节）。

列表区：

| 字段 | 数据规则 | 说明 |
| --- | --- | --- |
| 话单 ID | 系统唯一标识 | 完整显示，支持点击复制 |
| 时间 | 通话开始时间 | `YYYY-MM-DD HH:MM:SS` |
| 主/被叫号码 | 主被叫号码 | 脱敏显示，中间 4 位掩码 `*` |
| 企业 | 接入企业名称 | / |
| 省份/城市 | 主叫归属地 | 文本 |
| 公司 | 接入企业名称 | 如“鸿运通” |
| 终端客户 | 客户品牌名称 | 如“美团” |
| 终端客户 ID | 客户系统 ID | 完整显示 |
| 呼叫状态 | 通话结果 | 带颜色标签 |
| 通话时长 | 秒 | 完整显示 |
| 质检记录 | 质检明细入口 | 仅“接通”时可点“查看”，携带话单 ID 跳转质检明细；其他状态不显示 |

分页：每页固定 20 条。表结构“基本沿用 700 话单表结构”（见第 4 节）。

**(b) 质检话单查询（质检明细）**

- 迭代：对客质检门户 → 质检管理 → 质检明细；记录 = 现有质检话单表 + userdata（话单 id、企业 id、主叫、被叫）；若为专线质检账户，表单增加话单 id、主叫、被叫。企业层级按用户所在层级可选可查。
- 新增：对客质检后台管理网站 → 通话管理 → 质检明细；一~四级企业全部可选，全量展示。
- 低保真原型（图）筛选项：质检状态、质检能力、AI 质检结果、人工复核结果、日期范围、任务 ID、录音 ID、话单 id；列：话单 id、一级移动、二级移动、接入企业、终端客户、任务创建时间、任务 id、录音时长、质检状态、AI 质检结果、复核员、人工复核结果、详情（查看/编辑）。原型说明：“1、话单 id 可以通过 userdata 提取；2、不再展示透传数据、录音 id、质检能力、复核员四个字段”。

**(c) 号码报备（后台管理网站 → 通话管理 → 号码报备）**

- 功能：增删改查；“上传”=添加入库，“下线”=删除出库；保留一~四级企业信息；“号码状态”字段备用，暂不关联逻辑。
- 原型（图）：查询框“查询号码”“终端企业 id”；按钮“+添加号码”“+批量添加”“etcd 更新”“下线号码”“导出”；列：报备号码、一级企业、二级企业、三级企业、四级企业、四级企业 id、匹配方式、创建时间、更新时间、操作人、备注、操作（编辑/删除）。
- 单条添加弹窗字段：*匹配方式、*号码、*一级企业、*二级企业、*三级企业、*四级企业、备注。
- 批量添加 / 批量下线：xlsx 文件，三列、无标题：报备号码 / 匹配方式 / 终端企业 id。
- 结果提示：上传失败按行弹窗告知（例：“第 5 行，上传失败，号码与企业信息不符”）；下线失败同理（“第 10 行，下线失败，号码不在库”）。原型中的提示文案：
  - 批量添加：导入成功！请更新 etcd / 导入失败！同一企业已存在相同号码 / 导入失败！文件格式出错，请重新检查 / 导入失败！网络异常 / 导入失败！其他
  - 批量下线：操作成功！号码已下线，请更新 etcd / 操作失败！文件格式出错，请重新检查 / 操作失败！网络异常 / 操作失败！其他
- 需求中的号码报备表样例：

| 字段名 | 非空 | 说明 |
| --- | --- | --- |
| num | Y | 质检报备号码（专线/固话） |
| customer_id | Y | 四级企业唯一标识 ID |
| match_type | Y | 匹配方式：1-精准匹配，2-前缀匹配 |
| create_time | Y | 创建时间 yyyy-dd-mm HH:MM:SS |
| update_time | Y | 更新时间 yyyy-dd-mm HH:MM:SS |
| operator | N | 操作人（更新人、录入人） |
| notes | N | 备注说明 |

（与正式 DDL 的差异见第 4.3 节与第 6 节。）

### 1.7 非功能需求（文档中散落的约束汇总）

需求文档没有独立的“非功能需求”章节，以下为从各文档中摘出的约束：

| 类别 | 约束 | 来源 |
| --- | --- | --- |
| 存储时长 | 合音完整音频与分片音频在 PFS 存储 ≥ 2 个月 | 方案+需求 §3.5 |
| 实时性 | 实时流按标准 20ms 间隔推送解码音频帧 | §3.4 |
| 话路可靠性 | 质检呼叫路由“保证话路不中断”；非质检呼叫不干预 | §3.1 |
| 音频格式 | 采样率仅 8000 / 16000；PCM 格式 S16LE（默认）/ALAW/ULAW；录音 wav/mp3/amr | 媒体接口 §3、§1 |
| 查询性能/范围 | 查询跨度 ≤ 180 天，默认 30 天；导出 ≤ 1 万条；每页 20 条 | §3.8.2.1 |
| 隐私 | 主被叫号码中间 4 位掩码；导出受权限控制 | §3.8.2.1 |
| 安全 | 报备接口 EOPAUTH 鉴权（appid+timestamp+MD5 签名），时间戳偏差 5 分钟内，失败返回 403；质检回调 URL 需加白；能开开户携带 ip 白名单 | 报备接口 §2.1、媒体接口 §4、交互图 |
| 号码数据刷新 | 号码报备表载入内存，人工点击“etcd 更新”刷新 | §3.2.2 |
| 录音完整性 | 媒体图注：“由于开启实时分片时只做格式转换，并不保存录音数据，即可能中间部分丢失”（文档自己提出“是否需保证录音完整性？”的问题，未给结论） | 媒体图 2 |
| 容量估算 | 媒体图右侧算式：5×60=300 → 300×50=15000 → 15000×160=2,400,000 → ×2=4,800,000 → ×2000=9,600,000,000 → ×2=19,200,000,000 → ÷1024÷1024÷1024≈17.88。推测含义：缓冲 5 分钟、每秒 50 帧（20ms）、每帧 160 字节（8k G.711）、双腿、2000 路并发、再 ×2（待确认：16bit 或双份缓存），合计约 17.9 GB 内存 | 媒体图 2（含义为推测，待确认） |

缺失：并发/容量目标（除上述算式外）、端到端时延（准实时/实时质检结果多久返回）、可用性/容灾、话单与质检话单的保留周期、MQ 消息可靠性（重试/幂等）等均无定义。

---

## 2. 模块交互（依据《专线质检各模块交互逻辑图》及两张方案时序图）

交互图共 5 个泳道：①能开-主体账户开户；②能开-能力可用性同步；③质检客户子账户创建；④质检任务运转；⑤计费批价（3 个方案）。

### 2.1 能开-主体账户开户流程

参与方：客户 → 能力开放平台（能开）→ 对客质检平台（写主账户表）。

| 步骤 | 方向 | 内容 / 关键字段 |
| --- | --- | --- |
| 1 | 客户 → 能开 | 填表单（纯文本）：质检模式=专线质检；计费模式=实时批价\定时批价；开户主体=一级移动（必填）；企业全称（必填）；企业简称（必填） |
| 2 | 能开 → 对客质检平台（发起） | `pattem`: 2（专线质检）；`Fullname`；`Nickname`；`paymethod`: 2（定时批价）；`ip`（ip 白名单）；`openlevel`: 1（开户主体） |
| 3 | 对客质检平台 写主账户表 | `nickname`、`fullname`、`app_id`（专线质检客户主体唯一标识，如 AB001）、`pattem`: 2、`main_accountlevel`: 1、`secretid`、`secretkey`、`paymethod`: 2、`ability`: 不可用 |
| 4 | 对客质检平台 → 能开（响应） | `app_id`（专线质检账户 id）、`secretid`（鉴权 id）、`secretkey`（鉴权密钥） |

要点：开户后 `ability` 初始为“不可用”，需等第 2.2 节能力同步后才可用。接口路径、方法图中未给出（待确认）。字段名 `pattem` 疑为 `pattern` 拼写（待确认实际字段名）。

### 2.2 能开-能力可用性同步流程

参与方：能开 → 对客质检平台。

| 步骤 | 方向 | 关键字段 |
| --- | --- | --- |
| 1 | 能开 → 对客质检平台 | `appId`（应用 id & 计费 id，一串长数字）、`serviceCode`: `newCall_101`（业务线 id）、`app_id`: AB001（专线质检账户 id）、`isAvailable`: 可用 |
| 2 | 对客质检平台 更新主账户表 | `app_id`: AB001、`ability`: 可用、`appId`: <计费 id> |

注意：同一张主账户表里同时存在 `app_id`（质检账户 id，字符串如 AB001）和 `appId`（能开应用 id/计费 id），仅大小写/下划线不同，极易混淆。

### 2.3 质检客户子账户创建流程（客户/企业开户）

参与方：客户（语音专线平台）→ 对客质检平台。对应报备接口说明书 “语音专线质检客户创建接口”。

| 步骤 | 方向 | 关键字段 |
| --- | --- | --- |
| 1 | 客户 → 对客质检平台（发起） | 鉴权：`secretid`、`secretkey`；业务：`provinceFullname`、`provinceNickname`、`cityFullname`、`cityNickname`、`districFullname`、`districNickname`、`enterpriseFullname`、`enterpriseNickname`、`scene`（如 ["1","2","8"]）、`scripts`（话术文本数组）、`accountManger`、`managerPhone`、`channelid`（= app_id） |
| 2 | 平台 校验主账户表 | 校验 `app_id` 是否存在、鉴权（`secretid`/`secretkey`）是否合法 |
| 3 | 平台 校验一级企业表 | 字典校验是否准确 |
| 4 | 平台 校验二级企业表 | 字典校验：`cityFullname`、`cityNickname`、`channel_id`、`mobile_id` |
| 5 | 平台 校验三级企业表 | 字典校验：`districFullname`、`districNickname`、`channel_id`、`company_id`、`state`: 启用 |
| 6 | 平台 写/校验四级企业表 | 校验是否存在；写入 `enterpriseFullname`、`enterpriseNickname`、`customer_id`（新生成）、`app_id`、`scene`、`scripts`（**存为话术 id 数组**）、`accountManger`、`managerPhone`、`state`: 启用 |
| 7 | 平台 → 客户（响应） | `customer_id`、`mobile_id`、`channel_id`、`company_id` |

要点：一~三级是“字典”（平台预置，只校验不新建），四级是新建；响应返回的是四级 id 及其上级 id。与报备接口说明书的差异（字段名、鉴权方式、响应字段）见第 6 节。

### 2.4 号码/专线报备

交互图没有单独画号码报备泳道；由以下两处构成：

1. **对客质检后台人工维护**（方案+需求 §3.8.2.3）：运营在“号码报备”页面单条添加 / 批量 xlsx 上传 / 下线 → 写 `user_qc.biz_num_auth` → 点击“etcd 更新”→ AS（xdtmf）通过 etcd 通知刷新内存中的精确号码集合与前缀号段集合。
2. **接口报备**（报备接口说明书 §“语音专线号码新增/删除接口” + “号码任务结果回调接口”）：
   | 步骤 | 方向（按说明书文字） | 关键字段 |
   | --- | --- | --- |
   | 1 | 调用方 → 平台：号码新增/删除 | `requestId`、`enterpriseId`、`numberList`、`operateType`（新增/删除） |
   | 2 | 平台 → 调用方：同步响应 | `code`、`message`、`taskId`（异步任务 id） |
   | 3 | 平台 → 调用方：号码任务结果回调 | `taskId`、`result`（全部成功 / 0-全部失败）、`reason` |
   | 4 | 调用方 → 平台：回调响应 | `code`、`message` |

   推测平台在步骤 2~3 之间把号码写入 `biz_num_auth` 并刷新 etcd（文档未写，待确认）。接口没有 `match_type` 字段，无法通过接口报备前缀号段（待确认）。

### 2.5 通话与质检（质检任务运转流程）

参与方：Xproxy、AS（CT+IT，含 billcenter）、对客质检平台（报备号码表/四级企业表/主账户表）、700 大网、qa_server、调度系统、数据库、客户。

| 步骤 | 方向 | 内容 / 关键字段 |
| --- | --- | --- |
| 1 | Xproxy → AS | 非 15 位 700 号码，路由进 AS |
| 2 | AS → 对客质检平台（查报备号码表） | `callnumber`（主叫号码） |
| 3 | 平台 → AS | `message`: 匹配成功；`customer_id` |
| 4 | AS → 平台（查四级企业表） | `customer_id` |
| 5 | 平台 → AS | `customer_id`、`app_id`、`state`: 启用 |
| 6 | AS → 平台（查主账户表） | `app_id` |
| 7 | 平台 → AS | `app_id`、`ability`: 可用 |
| 8 | AS → 700 大网 | 接续放通 |
| 9 | AS(billcenter) → qa_server（发起质检） | `userdata`: { `call_id`（话单 id）, `callnumber`, `callednumber`, `customer_id`, `app_id` }；`AS 回调地址` |
| 10 | qa_server → 调度系统 | 质检任务 + xml（任务流配置） |
| 11 | 调度系统 → qa_server | 质检结果 + 风险等级 |
| 12 | qa_server → 数据库 → 对客质检平台 | 质检话单；“质检结果明细”：`call_id`、`callnumber`、`callednumber`、`customer_id`、`app_id`、`scene`、`scripts`（命中话术 id）、风险等级（高/中/低）、`result`（正常/异常） |
| 13 | qa_server → AS（根据 AS 回调地址异步回调） | `企业回调地址`；`userdata`{同上}；`qa_result`: 正常\异常 |
| 14 | AS → 客户 | 根据企业回调地址推送给客户 |

结合方案时序图（7001 链路主叫流程）补充的通话侧步骤：

| # | 参与方 | 动作 |
| --- | --- | --- |
| 1 | 业务方 | 为被叫添加 7001X 前缀（总长度非 15 位）并发起呼叫 |
| 2 | 700 大网路由 | 接收呼叫，按 7001 前缀分发 |
| 3 | 700 业务平台 xproxy | 识别总长度非 15 位被叫，路由至 xdtmf |
| 4 | 7001 专线 AS xdtmf | 去掉 70011 前缀拆出原始被叫；匹配报备号码表 |
| 5a | xdtmf → 大网 | 匹配失败：返回拒绝接续指令 → 业务方听到“拨打号码为空号”，流程结束 |
| 5b | xdtmf → 大网 | 匹配成功：返回正常接续指令、透传主叫；读取质检参数（归属企业 ID【一期】、实时 or 通话后【二期】、回调地址【二期】、是否分片【二期】） |
| 6 | 大网 → 被叫 | 呼叫、振铃；被叫号显主叫、接听 |
| 7 | xdtmf → 媒体（stream-media/codec/stream-merge） | 打包主叫、被叫、企业 id、话单 id；stream-media 启动实时双声道录音并按分片参数拆分；codec 实时编解码（MP3）；stream-merge 实时合音；录音存储至 PFS |
| 8 | 媒体 → qa-server | 携带话单 ID/企业 ID/主叫/被叫发起质检请求 |
| 9a【二期】 | qa-server → scheduler → ASR/模型 | 根据质检配置发起实时质检；ASR 拉取分片录音；调用质检模型；质检结果回调；（待定）qa-server → xdtmf 生成管控意见：接续/放音/挂断 → xdtmf 生成管控指令 → 大网执行 |
| 9b【一期】 | qa-server → scheduler → ASR/模型 | 根据质检配置发起离线（通话后）质检；ASR 拉取完整通话录音；调用模型；结果回调 |
| 10 | 大网 → xdtmf | 结束通话（IMS 图中标注“通话结束状态”） |
| 11 | xdtmf | 生成话单 → 保存通话话单（MySQL） |
| 12 | qa-server | 生成质检话单 → 保存质检话单（MySQL、海山） |
| 13 | qa-server → 业务方 | 根据回调地址推送质检话单 |
| 14 | 对客质检平台 | 查询并展示质检话单、通话话单 |

IMS 实体卡时序图差异：识别条件为“工作号签约 + 固话主叫/被叫”；匹配失败 → “通话放通不走质检流程”；匹配成功 → “通话放通且进入质检流程”；xdtmf 传给媒体的是“质检配置参数 + 回调地址”；回拨流程中 xdtmf 多一步“推送通话话单”，并“根据回调地址向业务方（客户）推送通话话单”与“推送质检话单”。

媒体内部时序（媒体图 3，信令 ↔ 媒体 ↔ 实时流编解码 ↔ 合音）：

| # | 消息 | 说明 |
| --- | --- | --- |
| 1 | 主叫 → 信令 → 被叫：Invite | 起呼 |
| 2 | 信令 → 媒体：`create` | 创建会话 |
| 3 | 主叫/被叫 ↔ 信令：Update | 媒体协商 |
| 4 | 信令 → 媒体：`update` | 媒体更新 |
| 5 | 信令 → 媒体：`inspect` | 下发质检配置（对应媒体接口 §5 `/call//media/inspect`） |
| 6 | 被叫 → 信令 → 主叫：200-Invite；ACK | 应答 |
| 7 | 信令 → 媒体：`pickup` | 会话接通 |
| 8 | 主被叫 → 媒体：Audio-RTP | 双向音频 |
| Alt-实时 | 媒体 → 实时流编解码：`livestream` 开启实时流；音频大分片；音频帧 → 实时解码 → 合音：`merge`；音频裸流 PCM | 实时链路 |
| Alt-离线 | 媒体本地录音；合音 → 媒体：`download` 下载录音 | 离线链路 |

媒体处理三种模式（媒体图 1）：
1. 离线：RTP → 格式转换 → 帧缓存 → 落盘 + RocketMQ 通知；
2. 实时：RTP → 格式转换 → 帧 → 转码（codec）；
3. 实时转离线：先实时推转码，转码链路断开后转为落盘 + RocketMQ。

媒体主循环（媒体图 2）：语音 RTP 读取 → 语音帧统计 → 是否已开启实时流？是：推送语音帧；否：是否达到开启分片阈值？是：开启实时流（建立连接 → 推送缓存的多段编码录音 → 失败则切换文件模式/切换持久化；成功则推送当前缓存的录音，并切换非持久化）；否：语音单向隐藏。录音子流程：是否内存模式？→ 格式转换 → 语音帧写入 → 是否文件模式 → 是否达到指定容量 → 数据落盘。图注：“内存模式仅写入程序内存缓冲区中，当切换为非持久化后，则仅作为格式转换器，将传输格式转成文件存储格式”；“缓存容量由开启分片的阈值决定”。

媒体缓存架构（媒体图 4）：媒体（RTP → 格式转换 → 内存缓存帧）→ 加入缓存 → 二级缓存（Tmp 内存 → 磁盘）；媒体 → RocketMQ 推送合音通知；合音订阅 RocketMQ → 拉取录音 → 离线双腿文件合并；媒体 → 实时流编解码（语音帧推送 → 编解码核心 → 语音原始裸流 PCM 推送）→ 合音（实时流双腿内存合并）；合音 → 下载服务（上传合音）→ 加入缓存 → 二级缓存（Tmp 内存 → 远程存储），支持缓存查询。

### 2.6 计费批价流程（3 个方案）

| 维度 | 方案一 | 方案二 | 方案三（江西） |
| --- | --- | --- | --- |
| 付费方式 | 预付费，购买分钟数套包 → 能开 | 后付费，购买分钟数套包 → 能开 | 后付费，购买分钟数套包 → 能开 |
| 平台侧配置 | 主账户表 `app_id`、`paymethod`: 1（实时批价） | 主企业表 `app_id`、`paymethod`: 1 | 主账户表 `app_id`、`paymethod`: 2（定时批价） |
| 推送内容 | 对客质检平台 → 计费中心（PDC）：以秒为单位推送质检时长话单（`appId`、任务耗时 89s） | 同方案一（`appId`、任务耗时 89s） | 第一步：以秒为单位计算单个任务质检时长，不满 6s 按 6s 计（例：任务 10s → 12s）；第二步：计算当天总计费秒数（例：124s）；第三步：将当天总计费秒数推送给 PDC |
| PDC 商品 | 上架实时批价商品：分钟计费型产商品（不满一分钟按一分钟计） | 6s 计费型产商品（不满 6s 按 6s 计） | 分钟计费型产商品（不满一分钟按一分钟计） |
| 批价结果 | 计费分钟扣减 2min | 计费秒数扣减 90s | 计费分钟扣减 3min |

关键字段：`appId`（能开应用 id & 计费 id）、`paymethod`（1-实时批价，2-定时批价）。计费依据是“质检任务耗时”，不是通话时长（待确认是否即录音时长）。

---

## 3. 接口说明

### 3.1 语音专线质检信息报备接口说明书（v1.0.1）

文档信息：产品名“语音专线质检”，归属云通信事业部，密级“保密”；V1.0.0 2026-03-24 初版；V1.0.1 2026-03-25 “新增 2.5 和 2.6 接口”。目的：规范质检能力需求方（语音专线平台）与提供方（质检平台）之间的企业信息、业务场景、话术配置同步规则。所有接口地址均为 `POST url（待定）`，`Content-Type: application/json;charset=UTF-8`。

#### 3.1.1 鉴权

| HTTP 消息头 | 必选/可选 | 数据类型 | 描述 |
| --- | --- | --- | --- |
| Authorization | M | String | 鉴权信息，含 appid、timestamp、signature，用逗号分隔。格式：`Authorization: EOPAUTH appid="<appid>",timestamp="<秒级时间戳>",signature="<32 位大写 MD5>"` |

| 消息参数 | 必选/可选 | 数据类型 | 描述 |
| --- | --- | --- | --- |
| appid | M | String | 接入方平台唯一 ID，每次请求传递，用于身份识别 |
| timestamp | M | String | 1970-1-1 起的秒数，与标准时间偏差在过去 5 分钟之内 |
| signature | M | String | `MD5($timestamp$appkey)`，32 位十六进制大写 |

响应：鉴权不通过 → HTTP `403 Forbidden`；通过 → 返回业务结果。

#### 3.1.2 语音专线质检客户创建接口

功能：语音专线平台创建需要质检的新客户；收到请求后将客户信息、场景信息、话术信息入库。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | provinceFullname | String | 是 | 省公司全称 |
| 3 | provinceNickname | String | 是 | 省公司简称 |
| 4 | cityFullname | String | 是 | 市公司全称 |
| 5 | cityNickname | String | 是 | 市公司简称 |
| 6 | districtFullname | String | 是 | 区县公司全称 |
| 7 | districtNickname | String | 是 | 区县公司简称 |
| 8 | enterpriseFullname | String | 是 | 客户全称 |
| 9 | enterpriseNickname | String | 是 | 客户简称 |
| 10 | scene | List<String> | 是 | 报备场景 id：1-催收服务 2-金融服务 3-教育服务 4-房产服务 5-政务服务 6-电商服务 7-企业办公 8-快递服务 9-医疗服务 |
| 11 | script | List<String> | 否 | 报备话术 |
| 12 | accountManager | String | 是 | 项目经理 |
| 13 | managerPhone | String | 是 | 项目经理手机号码 |
| 14 | channelId | String | 是 | 质检账户 Id |

请求示例（字段名按原文，值脱敏）：`{"requestId":"...","provinceFullname":"...","provinceNickname":"...","cityFullname":"...","cityNickname":"...","districtFullname":"...","districtNickname":"...","customerFullname":"XXXXXX","customerNickname":"XXXX","scene":["1","2",...],"scripts":["...","..."],"accountManager":"XXX","managerPhone":"1xxxxxxxx01","channelId":"AB001"}`（注意示例字段名与字段表不一致，见第 6 节）

响应：

| 参数 | 类型 | 长度 | 是否必传 | 说明 |
| --- | --- | --- | --- | --- |
| code | String | 4 | 是 | 状态码：0000 成功，1001 失败 |
| message | String | <=128 | 否 | 返回结果描述 |
| enterpriseId | Int | — | 是 | 客户 Id |

示例：`{"code":"0000","message":"成功","enterpriseId":12}`

#### 3.1.3 语音专线质检场景修改接口

功能：对存量客户的报备场景增删改（需同步修改后企业下**全部**场景，即全量覆盖）。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | enterpriseId | Int | 是 | 客户 Id |
| 3 | scene | List<String> | 是 | 场景（修改后企业下所有场景），枚举同上 1~9 |

示例：`{"requestId":"...","enterpriseId":123,"scene":["1","2"]}`

响应：

| 序号 | 参数 | 类型 | 长度 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- | --- |
| 1 | code | String | 4 | 是 | 0000 成功，其他值都代表推送失败 |
| 2 | message | String | <=128 | 是 | 返回结果描述 |

#### 3.1.4 语音专线质检话术修改接口

功能：对存量客户的报备话术增删改（增量方式，按 operateType）。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | enterpriseId | Int | 是 | 客户 Id |
| 3 | script | List<String> | 是 | 话术 |
| 4 | operateType | String | 是 | 操作类别：新增、删除（取值编码未定义，示例为 "2"） |

示例：`{"requestId":"...","enterpriseId":123,"script":["...","..."],"operateType":"2"}`

响应：同 3.1.3（code、message）。

#### 3.1.5 语音专线客户注销接口

功能：注销客户，将客户状态置为注销态，客户下的质检账户无法再执行质检任务。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | enterpriseId | Int | 是 | 客户 ID |

示例：`{"requestId":"...","enterpriseId":123}`

响应：同 3.1.3（code、message）。

#### 3.1.6 语音专线号码新增/删除接口

功能：“客户调用此接口通知语音专线平台新增/删除专线号码”。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | enterpriseId | Int | 是 | 客户 ID |
| 3 | numberList | List | 是 | 号码 |
| 4 | operateType | String | 是 | 操作类别：新增、删除（示例为 "1"） |

示例：`{"requestId":"...","enterpriseId":123,"numberList":["0xxxxxxxxx","..."],"operateType":"1"}`

响应：

| 序号 | 参数 | 类型 | 长度 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- | --- |
| 1 | code | String | 4 | 是 | 0000 成功，其他值都代表推送失败 |
| 2 | message | String | <=128 | 是 | 返回结果描述 |
| 3 | taskId | String | <=32 | 否 | 任务 Id |

示例：`{"code":"0000","message":"成功","taskId":"12345678"}`

#### 3.1.7 语音专线号码任务结果回调接口

功能：语音专线平台通过该接口通知客户号码任务结果。`POST url（待定）`

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | taskId | String | 是 | 任务 Id |
| 2 | result | String | 是 | 操作结果：“全部成功 0-全部失败”（原文缺少“全部成功”的编码） |
| 3 | reason | String | 否 | 失败原因 |

示例：`{"taskId":"12345678","result":"0","reason":"..."}`

响应：同 3.1.3（code、message）。

#### 3.1.8 错误码 / 返回值

| 取值 | 含义 | 适用 |
| --- | --- | --- |
| HTTP 403 | 鉴权未通过 | 全部接口 |
| `0000` | 成功 | 全部接口 |
| `1001` | 失败 | 客户创建接口（明确列出） |
| 其他值 | 推送失败 | 场景修改、话术修改、注销、号码新增/删除、号码任务回调 |

“返回值定义”章节内容为模板占位 `[详细描述接口统一返回值说明]`，术语表、缩略语表、附录均为空 → **没有统一错误码表**。

### 3.2 专线质检-媒体实现详细设计接口

#### 3.2.1 媒体通知离线合音消息（MQ）

- 交互方式：MQ 消息（RocketMQ，依据媒体图 1/4）
- Topic：`TID_VI_MEDIA_RECORD_MERGE_NORMAL`
- GroupName：`GID_VI_MERGE_RECORD`
- 生产方：媒体（vi-media）；消费方：合音（offline-merge / vi-merge，依据二期图“分段合音消息”）

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | sessionId | string | 是 | 会话唯一标识符，关联本次通话/媒体会话全生命周期数据 |
| 2 | callerNum | string | 是 | 主叫号码（SIP URI 或 E.164 格式） |
| 3 | calleeNum | string | 是 | 被叫号码（格式需与 callerNum 一致） |
| 4 | userId | string | 是 | 企业用户 ID，用于权限校验和归属识别（平台分配的企业唯一标识，非手机号） |
| 5 | msId | string | 是 | 媒体服务实例 ID，定位处理该会话的媒体服务器节点 |
| 6 | address | string | 是 | 媒体应用部署地址 |
| 7 | version | string | 是 | 媒体应用版本号（如 v2.3.1），用于兼容性控制和灰度 |
| 8 | volume | string | 否 | 存储卷标识（如 vol-001），指定媒体文件落盘逻辑分区 |
| 9 | path | string | 是 | 相对存储子路径 |
| 10 | callerAudioFile | string | 否 | 主叫侧音频文件名列表，逗号分隔，空字符串表示无文件 |
| 11 | calleeAudioFile | string | 否 | 被叫侧音频文件名列表，逗号分隔 |
| 12 | callerVideoFile | string | 否 | 主叫侧视频文件名列表，逗号分隔 |
| 13 | calleeVideoFile | string | 否 | 被叫侧视频文件名列表，逗号分隔 |
| 14 | earlyAudioFile | string | 否 | 早期媒体音频文件名列表，逗号分隔 |
| 15 | options | string | 否 | 合音/混音配置参数（JSON 字符串） |
| 16 | startTime | int64 | 是 | 会话起始时间戳（文字写毫秒级 Unix 时间戳），表示初始信令接收时刻 |
| 17 | callerAudioRtpCount | int | 否 | 主叫侧总音频 RTP 包发送量（含振铃阶段） |
| 18 | calleeAudioRtpCount | int | 否 | 被叫侧总音频 RTP 包发送量（含振铃阶段） |
| 19 | callerAudioInCallRtpCount | int | 否 | 主叫侧通话中（接通后）音频 RTP 包发送量 |
| 20 | calleeAudioInCallRtpCount | int | 否 | 被叫侧通话中（接通后）音频 RTP 包发送量 |
| 21 | callerAudioRecordCount | int | 否 | 主叫侧实际录制音频帧数 |
| 22 | calleeAudioRecordCount | int | 否 | 被叫侧实际录制音频帧数 |
| 23 | callerVideoRtpCount | int | 否 | 主叫侧视频 RTP 包发送量 |
| 24 | calleeVideoRtpCount | int | 否 | 被叫侧视频 RTP 包发送量 |
| 25 | callerVideoRecordCount | int | 否 | 主叫侧录制视频帧数 |
| 26 | calleeVideoRecordCount | int | 否 | 被叫侧录制视频帧数 |
| 27 | pcmFormat | string | 否 | PCM 编码格式（pcm_s16le、pcm_alaw、pcm_mulaw），默认 pcm_s16le |
| 28 | pcmSample | int | 否 | PCM 采样率（Hz），常见 8000、16000 |
| 29 | config | string | 否 | 用户自定义配置（JSON 字符串），透传扩展字段 |

样例中的嵌套结构（值脱敏）：

- `path` 形如 `<userId>/<yyyy-MM-dd>/<秒级时间戳>`；音频文件名形如 `Caller_<sessionId>_<yyyyMMddHHmmss>.amr`、`Callee_<sessionId>_<yyyyMMddHHmmss>.amr`；`address` 为 `<内网地址>:9060`。
- `options` JSON：

| 键 | 样例值 | 推测含义 |
| --- | --- | --- |
| recordSuffix | "mp3" | 合音输出格式 |
| reverseLeg | false | 是否反转左右声道 |
| separateLeg | false | 是否分腿输出 |
| isRecord | true | 是否录音 |
| separateEarly | false | 是否单独输出早期媒体 |
| bindId | "" | 绑定 id |

- `config` JSON（AS 下发的质检/分片配置）：

| 键 | 样例值 | 推测含义 |
| --- | --- | --- |
| callerNum / calleeNum | 号码 | 主被叫 |
| initialSegments | 30 | 首片时长（秒） |
| segments | 20 | 后续分片时长（秒） |
| mode | "RAW" | 音频模式 |
| period | "InCall" | 录音时段（通话中） |
| platformId | "843" | 平台/客户 id |
| sampleRate | 8000 | 采样率 |
| secretNum | 号码 | 业务号码 |
| sessionId | 同上 | 会话 id |
| source | 3 | 音频源：1 主叫 2 被叫 3 混合 |
| startTime | "yyyy-MM-dd HH:mm:ss" | 起始时间 |
| url | "INSPECT" | 推测为下游类型标识 |
| userData | JSON 字符串 | 见下 |
| userId | "843" | 客户 id |

- `userData` JSON（AS 透传，贯穿合音 → 话单 → 质检）：

| 键 | 含义 |
| --- | --- |
| callerNumber | 主叫 |
| calleeNumber | 被叫 |
| bizNumber | 业务号码 |
| handleStartTime | AS 开始处理时间 |
| companyId | 三级企业 id |
| customerId | 四级客户 id |
| asCallBackUrl | AS 质检结果回调地址，形如 `http://<内网地址>:5061/vi/resultCallBack` |

#### 3.2.2 合音结果推送给话单的消息（MQ）

- 交互方式：MQ 消息
- Topic：`TID_VI_MERGE_BILL_RECORD_NORMAL`
- GroupName：（空，待确认）
- 生产方：合音（vi-merge / offline-merge）；消费方：话单（vi-bill / vi-billcenter）

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | sessionId | string | 是 | 会话唯一 ID，关联同一通话的所有分片及质检结果 |
| 2 | segmentId | uint32 | 是 | 分片序号（从 0 或 1 开始递增）；相同 sessionId 下按 segmentId 顺序拼接 |
| 3 | callerNum | string | 是 | 主叫号码（E.164），不可为空字符串 |
| 4 | calleeNum | string | 是 | 被叫号码（E.164），不可为空字符串 |
| 5 | userId | string | 是 | 企业用户 ID（非手机号），用于权限控制、计费归属和数据隔离 |
| 6 | fileNode | string | 是 | 文件完整访问地址，支持 file:// 或 http(s)://，必须含协议头和完整路径 |
| 7 | version | string | 否 | 接口版本号；未传则用服务端最新兼容版本 |
| 8 | endFlag | bool | 否 | 是否为最后一个分片（true 触发合并、质检、计费等终态处理；false/缺失视为中间分片） |
| 9 | duration | float32 | 否 | 当前分片音频时长（秒），两位小数；未提供时服务端解析文件头计算 |
| 10 | path | string | 是 | 相对路径（不含文件名），以 / 开头、结尾不带 / |
| 11 | fileName | string | 是 | 文件名（不含路径和扩展名） |
| 12 | volume | string | 是 | PFS 根挂载点标识（PFS root 名） |
| 13 | suffix | string | 是 | 扩展名（小写不含点），如 wav、mp3；决定解码器与质检能力（mp3 可能不支持实时流质检） |
| 14 | isStream | bool | 否 | 是否实时流式上传分片（true 边录边传；false/空表示完整文件） |
| 15 | userData | string | 否 | AS 透传的原始业务数据（JSON 字符串） |

样例要点：`SegmentID: 0`、`EndFlag: true`、`Duration: 9.171`、`Volume: /nfsdisk/store`、`FileName: <sessionId>_<yyyyMMddHHmmss>.mp3`、`Suffix: mp3`、`IsStream: false`、`FileNode: <内网地址>:9301`、`UserData` 为数组形式。样例键名为大驼峰且不是合法 JSON（见第 6 节）。

#### 3.2.3 实时合音接收消息接口（WebSocket）

- 地址：`ws://{host}:{port}/v1/merge`
- 调用方：vi-codec（二期图“WS 传输 pcm、质检配置”）；服务方：vi-merge

Header：

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | X-Session-Id | string | 是 | 会话 ID |
| 2 | X-Params | string | 是 | 业务参数信息（JSON） |
| 3 | X-Notice | string | 否 | 异常通知回调地址 |

X-Params：

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | sessionId | string | 是 | 会话唯一 ID；omitempty 仅表示序列化时为空省略，仍强制校验非空 |
| 2 | callerNum | string | 是 | 主叫号码（E.164）；`binding:"required"` 强制非空 |
| 3 | calleeNum | string | 是 | 被叫号码（原文写“E.1601 格式”）；与 callerNum 共同构成通话标识 |
| 4 | userId | string | 是 | 企业用户 ID，用于鉴权、计费和数据隔离 |
| 5 | sampleRate | int | 是 | 采样率，仅允许 8000 或 16000，其他值拒绝 |
| 6 | fmt | string | 否 | PCM 格式：S16LE（默认）/ALAW/ULAW |
| 7 | userData | string | 否 | 透传业务数据（JSON 字符串） |
| 8 | initialSegments | int | 是 | 初始分片时长（秒），如 30 表示首片为前 30 秒 |
| 9 | segments | int | 是 | 后续分片固定时长（秒）；“若未传，服务端使用默认值（如 60）” |
| 10 | startTime | string | 是 | 信令起始时间：ISO8601 或毫秒级 Unix 时间戳字符串 |
| 11 | source | uint8 | 否 | 音频源：1 仅主叫；2 仅被叫；3 主被叫混合；其余报错 |

响应（WS 握手完成、参数校验后返回，通知上游可以开始发流）：

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | code | string | 是 | 0000 正常，其他值表示错误 |
| 2 | message | string | 是 | 返回描述 |

#### 3.2.4 话单模块向 QA 发送质检请求（HTTP）

- 方法/路径：`POST /v2/task/create`（质检任务创建接口，质检规则）
- 调用方：话单模块（vi-bill，经代理 SLB）；服务方：qa-server
- 说明：业务平台创建语音质检任务，质检平台根据录音下载 URL 列表去业务平台下载录音并完成质检。

请求：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | requestId | String | 是 | 唯一识别一次任务创建请求 |
| 2 | callIdList | String | 否 | 仅通话在平台上进行时有效；callId 以英文逗号分隔；只有一个 callId 时只触发子任务结果回调；与 urlList 必传其一 |
| 3 | urlList | List<File> | 否 | 待质检录音下载链接列表，每个 File 一个 id 对应一个 url；与 callIdList 必传其一 |
| 4 | data | String | 否 | 透传数据 |
| 5 | channelId | String | 是 | 渠道 ID，由语音质检平台分配 |
| 6 | ruleId | int | 否 | 质检规则 Id |
| 7 | taskCallBackUrl | String | 是 | 质检结果回调接口 URL（需要加白） |
| 8 | subTaskCallBackUrl | String | 是 | 子任务执行结果回调接口 URL（需要加白） |

File 结构：

| 序号 | 字段名 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | id | String | 是 | 录音 id |
| 2 | url | String | 是 | 文件下载 url |
| 3 | callerNumber | String | 否 | 主叫 |
| 4 | calleeNumber | String | 否 | 被叫 |
| 5 | handleStartTime | String | 否 | 通话时间 `%Y-%m-%d %H:%M:%S` |
| 6 | remark1 | String | 否 | 自定义数据 |
| 7 | remark2 | String | 否 | 自定义数据 |
| 8 | remark3 | String | 否 | 自定义数据 |
| 9 | remark4 | String | 否 | 自定义数据 |

响应：

| 参数 | 类型 | 长度 | 是否必传 | 说明 |
| --- | --- | --- | --- | --- |
| code | String | 4 | 是 | 0000 成功，1001 失败 |
| message | String | <=128 | 否 | 返回结果描述 |
| taskId | String | — | 是 | 唯一标识一次任务的 id |

#### 3.2.5 AS 通知媒体录音服务的接口（HTTP）

- 方法/路径：`POST /call//media/inspect`（原文含双斜杠）
- 调用方：vi-AS；服务方：vi-media（对应媒体时序图中信令 → 媒体的 `inspect-质检配置`）

字段表（原文）：

| 序号 | 名称 | 类型 | 是否必传 | 参数说明 |
| --- | --- | --- | --- | --- |
| 1 | sessionId | string | 是 | 会话唯一 ID；必须与上游 HeaderParams 或分片消息中的 sessionId 一致，否则拒绝 |
| 2 | config | string | 是 | 质检配置 JSON 字符串（如 rules、sensitivity）；服务端按 schema 校验，非法 JSON 或缺失必选 rule 报错 |
| 3 | frames | int | 是 | 启动质检的帧数阈值（单位：秒），必须 > 0（如 5 表示累计 5 秒后开始实时质检），避免短语音/噪声误触发 |
| 4 | direction | int | 是 | 质检音频方向：1 仅主叫；2 仅被叫；3 主被叫混合（需提前混音）；其余返回 400 |
| 5 | seqId | uint8 | 是 | 请求序列号，单会话内单调递增（从 0 或 1 开始），用于去重和时序控制；跳号或重复将拒绝 |

使用示例中出现、但字段表未定义的键：`userId`、`callerNum`、`calleeNum`、`mergeConfig`（与 3.2.1 的 options 同结构）、`startTime`（`yyyy-MM-dd HH:mm:ss`）、`recordMode`（样例 1）、`callbackUrl`（形如 `http://<内网地址>:5061/media`）；示例中**没有** `config`、`frames`。

返回：

| 字段 | 类型 | 值示例 | 说明 |
| --- | --- | --- | --- |
| code | int | 200 | HTTP 状态码语义映射：200 表示业务处理成功 |
| data | string | "" | 业务数据载体，此处为空 |
| message | string | "success" | 可读性提示 |

#### 3.2.6 质检业务系统接口

原文只有一行“AI 质检平台接口文档”（外链），内容未随附 → 缺失（待补充：qa-server ↔ scheduler 的 MQ、质检结果回调 `taskCallBackUrl`/`subTaskCallBackUrl` 报文、qa-server → AS 的 `/vi/resultCallBack` 报文）。

#### 3.2.7 图中出现但无接口文档的交互（汇总）

| 交互 | 方式 | 文档中的线索 | 状态 |
| --- | --- | --- | --- |
| 信令 → 媒体 `create` / `update` / `pickup` | HTTP（推测，与 `/call//media/inspect` 同组） | 媒体图 3 | 无定义 |
| 媒体 → codec `livestream`、音频大分片、音频帧 | WS（二期图“WS 传输 rtp 流、质检配置、事件”） | 媒体图 3、二期图 | 无定义 |
| 合音 → 媒体 `download` 下载录音 | HTTP（推测） | 媒体图 3/4 | 无定义 |
| vi-merge / offline-merge → vi-mdown 上传文件 | HTTP | 二期图 | 无定义 |
| vi-AS → vi-bill 通话消息 | MQ（推测 RocketMQ） | 一期/二期图 | 无定义 |
| vi-bill → vi-pusher 推送消息 | MQ（推测） | 一期/二期图 | 无定义 |
| vi-bill → vi-AS HTTP 质检结果通知 | HTTP | 二期图 | 无定义（样例中 `asCallBackUrl` = `/vi/resultCallBack`，端口 5061） |
| qa-server ↔ scheduler 通知质检 / 结果回调 | MQ（RabbitMQ，推测） | 一期/二期图 | 无定义 |
| scheduler → ASR / anti-fraud | MQ 通知 | 一期/二期图 | 无定义 |
| AS → 对客质检平台 号码/企业/主账户查询 | 交互图画为请求-响应 | 交互图 | 无定义（与“内存 + etcd 刷新”方案冲突，见第 6 节） |
| 能开 ↔ 对客质检平台 开户、能力同步 | 未说明 | 交互图 | 无定义 |
| 对客质检平台 → 计费中心 PDC | 未说明 | 交互图 | 无定义 |

---

## 4. 数据结构

### 4.1 通话话单表 `vi_bill.bill_new` / `vi_bill.bill_template`

说明：“AS 和话单模块访问”；“需要创建自动建表存储过程，按天分表”。`bill_template` 与 `bill_new` 字段完全一致（推测 `bill_template` 为按天分表的模板，`bill_new` 为当前写入表或视图，待确认）。引擎 InnoDB，utf8mb4 / utf8mb4_0900_ai_ci，主键 `id`。

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| id | varchar(32) | NOT NULL，PK | 话单 id |
| mobile_id | int | NOT NULL DEFAULT 0 | 一级移动 id |
| channel_id | int | NOT NULL DEFAULT 0 | 二级移动 id |
| company_id | int | NOT NULL DEFAULT 0 | 三级企业 id |
| customer_id | int | NOT NULL DEFAULT 0 | 四级终端客户 id |
| app_id | varchar(50) | NOT NULL | 四级终端客户对应的 app_id |
| account_type | tinyint | NOT NULL | 账户类型：1-测试账户 2-联调账户 3-生产账户 |
| location | tinyint | NOT NULL DEFAULT -1 | 呼叫触发节点（枚举未定义） |
| as_id | int | NOT NULL DEFAULT 0 | AS 节点 id |
| ms_id | int | NOT NULL DEFAULT 0 | MS 节点 id |
| pfs_id | tinyint | NOT NULL DEFAULT 0 | pfs 的 id |
| original_caller_number | varchar(50) | NOT NULL DEFAULT '' | 原始主叫号码 |
| caller_number | varchar(50) | NOT NULL DEFAULT '' | 主叫号码 |
| original_callee_number | varchar(50) | NOT NULL DEFAULT '' | 原始被叫号码（推测为带 7001X 前缀的被叫） |
| callee_number | varchar(50) | NOT NULL DEFAULT '' | 被叫号码 |
| biz_number | varchar(50) | NOT NULL DEFAULT '' | 关联四级企业配置的业务号码 |
| call_in_province_id | tinyint | NOT NULL DEFAULT 0 | 呼叫触发省份 id |
| call_out_province_id | tinyint | NOT NULL DEFAULT 0 | 被叫省份 id |
| call_in_700_area_code | varchar(10) | NOT NULL DEFAULT '' | 700 号码区号 |
| handle_start_time | datetime | DEFAULT NULL | AS 开始处理时间 |
| handle_end_time | datetime | DEFAULT NULL | AS 结束处理时间 |
| call_forward_time | datetime | DEFAULT NULL | 接续被叫时间 |
| ring_time | datetime | DEFAULT NULL | 平台收到被叫振铃时间 |
| call_start_time | datetime | DEFAULT NULL | 通话开始时间 |
| call_end_time | datetime | DEFAULT NULL | 通话结束时间 |
| call_duration_s | smallint | NOT NULL DEFAULT 0 | 通话时长（秒） |
| call_duration_m | tinyint | NOT NULL DEFAULT 0 | 通话时长（分） |
| file_duration | smallint | NOT NULL DEFAULT 0 | 录音时长（秒） |
| left_file_length | int unsigned | NOT NULL DEFAULT 0 | 左腿录音 |
| right_file_length | int unsigned | NOT NULL DEFAULT 0 | 右腿录音 |
| first_resp_time | datetime | DEFAULT NULL | 平台收到被叫首次有效响应的时间 |
| q850 | smallint unsigned | NOT NULL DEFAULT 0 | 平台收到被叫响应中 Q.850 cause 的值 |
| media_l_direction | tinyint | NOT NULL DEFAULT 0 | audio 协商，主叫最后协商的 media direction：1-none 2-sendrecv 3-sendonly 4-recvonly 5-inactive 6-invalid |
| media_r_direction | tinyint | NOT NULL DEFAULT 0 | audio 协商，被叫最后协商的 media direction：同上 |
| call_state | tinyint | NOT NULL DEFAULT 0 | 信令协商阶段：1-180 2-200inv 3-181 4-reinvite 5-视频 6-ack |
| finish_type | tinyint | NOT NULL DEFAULT 0 | 呼叫结束发起方：0-平台 1-主叫 2-被叫 |
| finish_state | tinyint | NOT NULL DEFAULT 0 | 话单状态（枚举未定义） |
| append_reason | tinyint | NOT NULL DEFAULT -1 | 话单状态附加原因：1-呼转拦截通话（默认值）2-主叫黑名单拦截通话 3-被叫黑名单拦截通话 4-被叫非白名单拦截通话 5-呼叫频次异常拦截通话 6-虚商被叫拦截通话 |
| is_bill_ok | tinyint | NOT NULL DEFAULT 0 | 话单是否推送成功：0-未推送 1-推送成功 10-无需推送 |
| is_record_ok | tinyint | NOT NULL DEFAULT 0 | 录音是否推送成功：0-未推送 1-推送成功 10-无需推送 |
| record_type | tinyint | NOT NULL DEFAULT 0 | 录音文件格式：1-wav 2-mp3 |
| quality_inspection_status | tinyint | NOT NULL DEFAULT 0 | 质检状态：0 未发起；1 已发起；2 质检成功；3 推送质检结果成功；-1 发起质检失败；-2 质检处理失败；-3 推送质检结果失败 |

### 4.2 二期话单变更（`ALTER TABLE vi_bill.bill_new` 与 `vi_bill.bill_template`）

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| quality_inspection_segment_result | TINYINT | NOT NULL DEFAULT 1 | 质检分片结果：0-异常，1-正常 |
| quality_inspection_final_result | TINYINT | NOT NULL DEFAULT 1 | 质检最终结果：0-异常，1-正常 |
| control_action | TINYINT | NOT NULL DEFAULT 0 | 质检管控类型：0-未设置，1-仅放音，2-仅阻断，3-先放音再阻断 |

### 4.3 号码报备表 `user_qc.biz_num_auth`（表注释：客户业务号码报备清单）

说明：70014 前缀路由码方式 → 业务号码为报备的主叫号码；工作号主叫签约 → 主叫号码；被叫签约 → 被叫号码。

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| id | int | NOT NULL AUTO_INCREMENT，PK | 主键 id |
| num | varchar(50) | NOT NULL，UNIQUE KEY `caller_num_auth_uk_num` | 报备主叫号码或号段 |
| match_type | tinyint unsigned | NOT NULL DEFAULT 1 | 匹配类型：1-精确匹配，2-前缀匹配（去除 86、0、+ 等） |
| customer_id | int | NOT NULL | 客户终端 id，关联 customer_tab |
| create_time | datetime | DEFAULT CURRENT_TIMESTAMP | 记录创建时间 |
| update_time | datetime | DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 记录更新时间 |
| company_id | int | NOT NULL | 三级移动 id，公司 id，关联 company_tab |
| mobile_id | int | NOT NULL | 一级移动 id |
| channel_id | int | DEFAULT NULL | 二级移动 id |
| comment | text | — | 备注 |
| operator | varchar(255) | NOT NULL | 操作人 |
| number_status | tinyint | DEFAULT NULL | 号码状态（备用，当前不关联逻辑） |

### 4.4 号段登记表 `user_qc.phone_segment_area_code_info`

说明：呼叫标记辅助信息，用于识别号码对应的号段归属地。附初始化数据 `phone_segment_area_code_info_202605081600.sql`（26.41 MB）。

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| id | bigint | NOT NULL AUTO_INCREMENT，PK | 主键 |
| province | varchar(50) | NOT NULL | 省/直辖市 |
| city | varchar(50) | NOT NULL | 所辖城市 |
| area_code | varchar(10) | NOT NULL | 长途区号 |
| center_area_code | varchar(10) | NOT NULL | 注释同为“长途区号”（推测为省会区号，对照 ICSCF 表，待确认） |
| operator | varchar(4) | DEFAULT '0' | 运营商（枚举未定义） |
| number_segment | varchar(11) | NOT NULL | 号码（号段，如 7 位号段前缀） |
| province_id | tinyint | DEFAULT NULL | 省/直辖市 ID |

### 4.5 ICSCF 信息登记表 `user_qc.chinamobile_icscf_ip`

说明：适用于 70014 前缀路由码的业务触发方式，用于获取触发 700 基础平台的 ICSCF 地址。附初始化数据 `chinamobile_icscf_ip_202605081558.sql`（66.21 KB）。

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| id | bigint | NOT NULL AUTO_INCREMENT，PK | 主键 |
| province | varchar(20) | NOT NULL | 省份 |
| cscf_name | varchar(60) | NOT NULL | cscf 名称 |
| ip | varchar(50) | NOT NULL | ip 地址；ipv4/ipv6 |
| contact_name | varchar(50) | NOT NULL | 联系人 |
| phone | varchar(50) | NOT NULL | 电话 |
| center_area_code | varchar(5) | DEFAULT NULL | 省会区号 |

### 4.6 话务管控策略表（MySQL 库 `vi_strategy`）

建库：`CREATE DATABASE IF NOT EXISTS vi_strategy DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`。6 张表公共字段：

| 字段名 | 类型 | 约束/默认 | 含义 |
| --- | --- | --- | --- |
| id | int | NOT NULL AUTO_INCREMENT，PK | 策略 ID |
| strategy_name | varchar(100) | NOT NULL DEFAULT '' | 策略名称 |
| mobile_id | int | NOT NULL DEFAULT 0 | 一级移动 id |
| channel_id | int | NOT NULL DEFAULT 0 | 二级移动 id |
| company_id | int | NOT NULL DEFAULT 0 | 三级企业 id |
| customer_id | int | NOT NULL DEFAULT 0 | 四级企业 id |
| effect_object | tinyint | NOT NULL（默认值随表） | 策略生效对象：1-主叫号码 2-企业 |
| control_command | tinyint | NOT NULL DEFAULT 2 | 话务管控指令：0-拦截 1-放通 2-告警 |
| append_reason | tinyint | NOT NULL（默认值随表） | 话单状态附加原因：1-呼叫频次异常管控 2-异地外呼超限管控 3-呼叫时间段管控 4-企业最大呼叫数管控 5-超业务范围-盲呼外呼管控 6-超业务范围-短秒率外呼管控 7-超业务范围-接通率外呼管控 8-长通话管控 |
| status | tinyint | NOT NULL DEFAULT 1 | 策略状态：1-生效 0-停用 |
| effect_time | datetime | DEFAULT NULL | 生效时间 |
| create_time | datetime | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | datetime | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

各表特有字段与默认值（逐表完整列出）：

**(1) `strategy_high_frequency` 短时高频外呼管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 1 | 策略生效对象：1-主叫号码 |
| stat_cycle_second | int | 86400 | 统计时间周期（秒），默认 24 小时 |
| frequency_threshold | int | 300 | 高频阈值（次），默认 300 次/24 小时 |
| control_valid_second | int | 0 | 管控有效时长（秒） |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| append_reason | tinyint | 1 | 1-呼叫频次异常管控 |

**(2) `strategy_remote_range` 异地大范围外呼管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 1 | 策略生效对象：1-主叫号码 |
| stat_cycle_second | int | 0 | 统计时间周期（秒） |
| area_threshold | int | 100 | 城市离散度阈值（个） |
| caller_home_city | varchar(100) | '' | 报备主叫归属城市 |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| control_valid_second | int | 0 | 管控有效时长（秒） |
| append_reason | tinyint | 2 | 2-异地外呼超限管控 |

**(3) `strategy_offwork_call` 非工作时间外呼管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 2 | 1-主叫号码 2-企业 |
| control_time_range | varchar(255) | '22:00-08:00' | 管控时间范围，多个时间段英文逗号分隔 |
| call_threshold | int | 0 | 外呼阈值（次），默认 0 |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| append_reason | tinyint | 3 | 3-呼叫时间段管控 |

（本表**没有** `stat_cycle_second`、`control_valid_second`。）

**(4) `strategy_business_threshold` 超业务门限外呼管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 2 | 1-主叫号码 2-企业 |
| stat_cycle_second | int | 86400 | 统计时间周期（秒） |
| max_call_limit | bigint | 200000000 | 企业最大呼出上限 |
| control_valid_second | int | 0 | 管控有效时长（秒） |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| append_reason | tinyint | 4 | 4-企业最大呼叫数管控 |

**(5) `strategy_business_range` 超业务范围外呼管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 1 | 1-主叫号码 2-企业 |
| dimension | tinyint | 0 | 策略维度：5-盲呼离散度 6-短秒率 7-接通率 |
| stat_cycle_second | int | 86400 | 统计时间周期（秒） |
| threshold_value | varchar(32) | '0' | 阈值，支持数字/小数 |
| control_valid_second | int | 0 | 管控有效时长（秒） |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| append_reason | tinyint | 0 | 5/6/7（随 dimension），默认 0 未定义 |

**(6) `strategy_long_call` 长通话管控策略表**

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| effect_object | tinyint | 1 | 1-主叫号码 2-企业 |
| long_call_threshold | int | 0 | 长通话阈值（秒），如 300 |
| control_valid_second | int | 0 | 管控有效时长（秒） |
| control_command | tinyint | 2 | 0-拦截 1-放通 2-告警 |
| append_reason | tinyint | 8 | 8-长通话管控 |

（本表**没有** `stat_cycle_second`。）

两份文档的 DDL 比对结果：《话务管控策略表DDL》与《话务管控上线依赖》中 6 张表字段、类型、默认值、注释**完全一致**，差异仅为上线依赖带了 `AUTO_INCREMENT` 起始值（business_range=13、business_threshold=7、high_frequency=6、long_call=7、offwork_call=6、remote_range=6），说明是从已有环境导出的，导出环境中已有测试数据。

### 4.7 话务管控明细/统计表（ClickHouse 库 `mid_bussops`）

**(1) `mid_bussops.call_control_detail` 话务管控明细表**（ENGINE = MergeTree，PARTITION BY toYYYYMM(trigger_time)，ORDER BY id，index_granularity = 8192）

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| id | UInt64 | 0 | 管控 id |
| mobile_id | Int32 | 0 | 一级移动 id |
| channel_id | Int32 | 0 | 二级移动 id |
| company_id | Int32 | 0 | 三级移动 id |
| customer_id | Int32 | 0 | 四级终端客户 id |
| trigger_time | DateTime | — | 触发时间 |
| control_subjects | String | '' | 管控对象 |
| strategy_id | UInt32 | 0 | 策略 id |
| strategy_name | String | '' | 策略名称：1-短时高频 2-异地大范围 3-非工作时间 4-超业务门限 5-超业务范围 8-长通话管控 |
| control_dimension | String | '' | 超业务范围管控维度：5-盲呼 6-短秒率 7-接通率 |
| control_command | String | '' | 管控指令：1-告警 2-挂断 |
| threshold_config | String | '' | 配置阈值 |
| actual_value | String | '' | 实际值 |
| create_time | DateTime | — | 创建时间 |
| update_time | DateTime | — | 更新时间 |

**(2) `mid_bussops.call_control_statistics` 话务管控统计表**（ENGINE = MergeTree，PARTITION BY toYYYYMM(stat_time)，ORDER BY id，index_granularity = 8192）

| 字段名 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| id | UInt64 | 0 | 统计 id |
| mobile_id | Int32 | 0 | 一级移动 id |
| channel_id | Int32 | 0 | 二级移动 id |
| company_id | Int32 | 0 | 三级移动 id |
| customer_id | Int32 | 0 | 四级终端客户 id |
| stat_time | DateTime | — | 统计时间 |
| strategy_name | String | '' | 策略名称：1-短时高频 2-异地大范围 3-非工作时间 4-超业务门限 5-超业务范围 8-长通话管控 |
| control_dimension | String | '' | 超业务范围管控维度：5-盲呼 6-短秒率 7-接通率 |
| trigger_phone_num | UInt32 | 0 | 触发号码数 |
| trigger_count | Int32 | 0 | 触发次数 |
| create_time | DateTime | — | 创建时间 |
| update_time | DateTime | — | 更新时间 |

### 4.8 Redis 数据结构（专线质检策略管控服务）

公共 value 结构（JSON 字符串，推测 Redis 类型为 String，文档未写明类型，待确认）：

```json
{
  "priority": 0,
  "condition": { "customerId": "<四级企业id>", "threshold": <可选>, "curfew": "<可选>" },
  "action": { "opType": "<0-拦截 1-放通 2-告警>", "appendReason": "<附加原因码>" }
}
```

`appendReason` 全量枚举（Redis 文档）：5-呼叫频次异常管控；13-呼叫时间段管控；20-企业最大呼叫数管控；21-异地外呼超限管控；22-超业务范围-盲呼外呼管控；23-超业务范围-短秒率外呼管控；24-超业务范围-接通率外呼管控；25-长通话管控；26-外省大范围高频外呼管控；27-外地市大范围高频外呼管控；28-沉默号码话务突增管控；29-闲时高频外呼管控；30-短时高频突增管控；31-短时长通话突增管控；32-沉默号码外地长通话突增管控。

| # | 策略 | Key 模式 | 类型 | condition 字段 | action（样例） | TTL | 写入方 / 用途 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 短时高频外呼（strategy_high_frequency） | `highfreq-{caller}` | String(JSON)，待确认 | customerId | opType "1"，appendReason "5" | 命中后设置超时（推测 = control_valid_second，待确认） | viStrategyHighFrequency.py；AS 呼叫时按主叫查询并执行管控 |
| 2 | 异地大范围外呼 | `difarea-{caller}` | 同上 | customerId | opType "1"，appendReason "21" | 同上 | viStrategyRemoteRange.py |
| 3 | 非工作时间外呼 | `offtime-{customerId}` | 同上 | customerId、threshold（样例 0）、curfew（管控时间段，样例 "22:00-24:00,11:00-12:00"） | opType "1"，appendReason "13" | 文档未说明（配置类，推测不过期或随停用清理） | viStrategyOffwork.py，读配置直接写 |
| 4 | 超业务门限外呼 | `threshold-{customerId}` | 同上 | customerId、threshold（样例 200000000） | opType "1"，appendReason "20" | 文档未说明（同上） | viStrategyBusinessThreshold.py，读配置直接写 |
| 5 | 超业务范围外呼 | `bizrange-{caller}` | 同上 | customerId | opType "1"，appendReason "22"（22 盲呼 / 23 短秒率 / 24 接通率） | 命中后设置超时 | viStrategyBusinessRange.py |
| 6 | 长通话 | `longcall-{caller}` | 同上 | customerId | opType "1"，appendReason "25" | 命中后设置超时 | viStrategyLongCall.py |
| 7 | 外省大范围高频外呼 | `province-highfreq-{caller}` | 同上 | customerId | opType "1"，appendReason "26" | 未说明 | 无对应策略表/脚本（待确认） |
| 8 | 外市大范围高频外呼 | `city-highfreq-{caller}` | 同上 | customerId | opType "0"（拦截），appendReason "27" | 未说明 | 同上（样例 JSON 缺少开头 `{`） |
| 9 | 沉默号码话务突增 | `inactive-{caller}` | 同上 | customerId | opType "1"，appendReason "28" | 未说明 | 同上 |
| 10 | 闲时高频外呼 | `idle-highfreq-{caller}` | 同上 | customerId | opType "1"，appendReason "29" | 未说明 | 同上 |
| 11 | 短时高频突增 | `short-highfreq-{caller}` | 同上 | customerId | opType "1"，appendReason "30" | 未说明 | 同上 |
| 12 | 短时长通话突增 | `short-longcall-{caller}` | 同上 | customerId | opType "1"，appendReason "31" | 未说明 | 同上 |
| 13 | 沉默号码外地长通话突增 | `inactive-difarea-longcall-{caller}` | 同上 | customerId | opType "1"，appendReason "32" | 未说明 | 同上 |

---

## 5. 话务管控

### 5.1 策略表含义

需求编号：“20260601-话务管控模块需求”（需求原文未随附）。目前 6 个策略，各对应一张 `vi_strategy` 配置表，外加 ClickHouse 明细表和统计表：

| 策略 | 配置表 | 生效对象 | 判定口径 | 关键阈值字段 | 是否需话务分析 | Redis key |
| --- | --- | --- | --- | --- | --- | --- |
| 短时高频外呼 | strategy_high_frequency | 主叫号码 | 统计周期内主叫外呼次数 ≥ 阈值 | stat_cycle_second(86400)、frequency_threshold(300) | 是 | highfreq-{caller} |
| 异地大范围外呼 | strategy_remote_range | 主叫号码 | 统计周期内主叫呼出的不同城市数（城市离散度）≥ 阈值；参考报备主叫归属城市 | stat_cycle_second(0)、area_threshold(100)、caller_home_city | 是 | difarea-{caller} |
| 非工作时间外呼 | strategy_offwork_call | 企业 | 管控时间段内外呼（次数 > call_threshold） | control_time_range('22:00-08:00')、call_threshold(0) | 否（读配置写 Redis，由 AS 实时判断时间段） | offtime-{customerId} |
| 超业务门限外呼 | strategy_business_threshold | 企业 | 统计周期内企业外呼总量 > 上限 | stat_cycle_second(86400)、max_call_limit(200000000) | 否（读配置写 Redis） | threshold-{customerId} |
| 超业务范围外呼 | strategy_business_range | 主叫号码 | 按维度：5-盲呼离散度 / 6-短秒率 / 7-接通率 超阈值 | dimension、stat_cycle_second(86400)、threshold_value | 是 | bizrange-{caller} |
| 长通话 | strategy_long_call | 主叫号码 | 通话时长 ≥ 长通话阈值 | long_call_threshold(0) | 是 | longcall-{caller} |

管控指令（策略表 `control_command`）：0-拦截、1-放通、2-告警，默认 2-告警；`control_valid_second` 为命中后管控持续时长；`status` 1 生效 / 0 停用；`effect_time` 生效时间。

### 5.2 策略运行逻辑（测试关注点）

| 项 | 逻辑（文档原文整理） | 测试关注点 |
| --- | --- | --- |
| 部署单元 | 每个策略一个脚本，仓库 cront-jobs（本次源码为 vi-cront-jobs-master） | 6 个策略脚本 + 统计脚本 + 停用清理脚本 |
| 配置类策略 | 非工作时间外呼、超业务门限外呼：不需要话务分析；每分钟（上线依赖中非工作时间为每 10 分钟）读策略配置表 → 写 Redis | 配置修改后多久生效；停用后 Redis 是否删除（依赖 viStrategyDisabledRedisClear.py）；跨零点时间段 `22:00-08:00` 的判断 |
| 分析类策略 | 短时高频、异地大范围、超业务范围、长通话：每分钟读取具体企业的策略配置 → 按话单 SQL 筛选分析 → 命中策略后写 Redis 并设置超时时间 → 命中数据写管控明细表 `mid_bussops.call_control_detail`（ClickHouse） | 命中后 TTL 是否等于 `control_valid_second`（为 0 时的行为）；同一号码重复命中是否重复写明细；分钟级延迟（命中前最多 1 分钟仍可外呼） |
| 统计 | `viStrategyDailyStatistics.py` 每天凌晨 1 点统计昨天 6 个策略的管控明细 → 写 `mid_bussop.call_control_statistics`（原文少一个 s，ClickHouse） | 统计日期边界；重跑幂等（MergeTree 无去重） |
| 话务模型 | 支持按自然日、自然小时筛选话单，按策略表 `stat_cycle_second` 选择：86400 → `to_date(handle_start_time) = to_date(now()) AND handle_start_time <= now()`；3600 → `to_start_of_hour(handle_start_time) = to_start_of_hour(now()) AND handle_start_time <= now()`；其他值 → 日志告警“不支持的统计周期”并跳过 | 只支持 86400 / 3600；**异地大范围表 `stat_cycle_second` 默认 0 会被直接跳过**；自然日/小时窗口而非滑动窗口，整点/零点后计数清零 |
| 城市离散度 SQL 示例 | `SELECT mobile_id, channel_id, company_id, customer_id, app_id, caller_number, COUNT(DISTINCT call_out_area_code) AS city_cnt FROM {table} WHERE <time_condition> AND call_duration_s > 0 AND customer_id = {customer_id} GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number` | 只统计接通（时长>0）的通话；`call_out_area_code` 字段在话单 DDL 中不存在（见第 6 节）；`{table}` 为哪张表（ClickHouse 话单表？）未说明；SQL 用 f-string 拼接 customer_id |
| Redis 结构 | 见第 4.8 节 | key 是否带企业维度（按 caller 的 key 不含 customerId） |

### 5.3 上线依赖与上线步骤（《话务管控上线依赖》）

依赖项：

| 类别 | 依赖 | 说明 |
| --- | --- | --- |
| 数据库 | 质检使用的 MySQL 新建库 `vi_strategy` 与 6 张策略配置表 | DDL 见第 4.6 节 |
| 数据库 | ClickHouse `mid_bussops.call_control_detail`、`call_control_statistics` | **上线依赖文档未列出**，只在《话务管控策略表DDL》中出现（待确认是否已存在） |
| 缓存 | Redis（策略结果） | 上线依赖文档未列出 Redis 实例/库号/配置项 |
| 运行环境 | Python 3.11 及以上 | — |
| 运行环境 | 虚拟环境，例如 `/data/venv/cront-jobs11` | `python3 -m venv /data/venv/cront-jobs11`；`source /data/venv/cront-jobs11/bin/activate`；`pip install -r requirements.txt` |
| 部署路径 | 脚本位于 `/home/production/vi-cront-jobs/` | — |
| 话单数据 | 分析类策略依赖话单表可查询（SQL 用 ClickHouse 函数，推测话单同步到 ClickHouse） | 未说明 |

上线步骤（文档顺序）：
1. 在质检 MySQL 执行 `CREATE DATABASE IF NOT EXISTS vi_strategy ...; USE vi_strategy;` 并建 6 张表。
2. 安装 Python 3.11+，创建并激活虚拟环境，`pip install -r requirements.txt`。
3. 配置 crontab：

| cron 表达式 | 脚本 | 说明 |
| --- | --- | --- |
| `* * * * *` | viStrategyBusinessRange.py | 每分钟 - 超业务范围 |
| `* * * * *` | viStrategyBusinessThreshold.py | 每分钟 - 超业务门限企业阈值 |
| `* * * * *` | viStrategyHighFrequency.py | 每分钟 - 短时高频 |
| `* * * * *` | viStrategyLongCall.py | 每分钟 - 长通话管控 |
| `*/10 * * * *` | viStrategyOffwork.py | 每 10 分钟 - 非工作时间管控 |
| `* * * * *` | viStrategyRemoteRange.py | 每分钟 - 异地外呼管控 |
| `0 1 * * *` | viStrategyDailyStatistics.py | 每天凌晨 1 点 - 日统计 |
| `* * * * *` | viStrategyDisabledRedisClear.py | 每分钟 - 策略停用清理 |

（均以 `/data/venv/cront-jobs11/bin/python /home/production/vi-cront-jobs/<脚本>` 方式执行。）

缺失：配置文件（MySQL/ClickHouse/Redis 连接）如何下发、日志路径、并发保护（每分钟任务执行超过 1 分钟时是否会重叠，crontab 无 flock）、回滚步骤、上线验证步骤。

---

## 6. 矛盾、缺失与待确认点

### 6.1 跨文档矛盾

| # | 矛盾点 | 文档 A | 文档 B | 影响 |
| --- | --- | --- | --- | --- |
| 1 | 实体卡方案匹配失败时是否接续 | 方案+需求 §2.2.1、§3.2：无论是否匹配都要放通 | §2.2.2 主叫/被叫流程：“若为非报备号码，则不进行接续” | 实体卡签约固话误拦截风险 |
| 2 | 谁向 qa_server 发起质检 | 方案+需求 §3.5、§3.6：vi-merge / stream-merge / 媒体模块发起 | 一期/二期图、媒体接口 §4、交互图：vi-bill（billcenter）通过 HTTP 经 SLB 调 `/v2/task/create` | 实现归属不清 |
| 3 | AS 获取号码/企业信息的方式 | 方案+需求 §3.2.2：号码报备表载入 AS 内存，etcd 人工刷新 | 交互图：AS 每通呼叫依次请求对客质检平台（报备号码表 → 四级企业表 → 主账户表） | 呼叫时延、平台可用性依赖 |
| 4 | 号显 | 方案+需求文本：实体卡号显为主叫号码 | IMS 时序图：主叫流程“号显工作号”，回拨流程“号显被叫” | — |
| 5 | 客户创建接口字段名 | 报备接口字段表：enterpriseFullname/enterpriseNickname、script、districtFullname、accountManager | 同文档请求示例：customerFullname/customerNickname、scripts；交互图：districFullname、accountManger、channelid | 联调时字段对不上 |
| 6 | 客户创建接口鉴权与响应 | 报备接口：EOPAUTH（appid + MD5(timestamp+appkey)），响应只返回 enterpriseId | 交互图：请求带 secretid/secretkey（明文传 secretkey），响应返回 customer_id、mobile_id、channel_id、company_id | 安全与契约不一致 |
| 7 | `channel` 一词两义 | 报备接口 `channelId` = 质检账户 Id（如 AB001，= app_id）；`/v2/task/create` `channelId` = 质检平台分配的渠道 ID | 各 DDL `channel_id` = 二级移动 id（int） | 极易混用 |
| 8 | 附加原因码三套编码 | 话单 `append_reason`：1~6（5=呼叫频次异常拦截通话） | 策略表 `append_reason`：1~8（1=呼叫频次异常管控）；Redis `appendReason`：5、13、20~32 | 话单落库时用哪套？报表口径不一致 |
| 9 | 管控指令编码 | 策略表 / Redis：0-拦截 1-放通 2-告警 | ClickHouse 明细表 `control_command`：1-告警 2-挂断；二期话单 `control_action`：1-仅放音 2-仅阻断 3-先放音再阻断；时序图：接续/放音/挂断；需求：接续/放音/插播/阻断 | 同一概念 4 套编码 |
| 10 | 非工作时间脚本频率 | 运行逻辑：每分钟执行一次 | 上线依赖 crontab：`*/10 * * * *` | 配置生效延迟最长 10 分钟 |
| 11 | 统计库名 | 策略表 DDL：`mid_bussops` | 运行逻辑：`mid_bussop.call_control_statistics` | 拼写错误 |
| 12 | 话务模型 SQL 字段 | 运行逻辑 SQL：`call_out_area_code` | 话单 DDL：无此字段，只有 `call_out_province_id`、`call_in_700_area_code` | SQL 无法在 DDL 所述表上执行；话单分析表结构缺失 |
| 13 | 非工作时间时间段样例 | 策略表默认 `22:00-08:00`（跨零点） | Redis 样例 `curfew: "22:00-24:00,11:00-12:00"`；redis 字段名 `curfew`/`threshold` ≠ 表字段 `control_time_range`/`call_threshold` | 跨零点格式是否需要拆成两段 |
| 14 | 号码报备表字段 | 需求样例：notes、operator 可空；无 company_id/mobile_id/channel_id | DDL：comment、operator NOT NULL，新增 company_id、mobile_id、channel_id(可空)、number_status | — |
| 15 | 号码唯一性 | 原型提示“同一企业已存在相同号码”（暗示按企业唯一） | DDL：`UNIQUE KEY (num)` 全局唯一 | 同一号码不能被两个企业报备，提示文案也不准确 |
| 16 | 前缀匹配规则 | DDL：前缀匹配“去除 86、0、+ 等” | 需求前缀示例 `020876543**` 带区号 0 且用 `**` 通配 | 前缀是否保留 0、是否支持通配符 |
| 17 | 质检明细原型 | 原型说明：“不再展示透传数据、录音 id、质检能力、复核员” | 同一原型的列中仍有“复核员”，筛选项仍有“录音 ID”“质检能力” | — |
| 18 | 计费方案 2 的付费方式 | 方案二标注“后付费”，`paymethod`: 1（实时批价） | 开户流程中 `paymethod` 2 = 定时批价、方案三“后付费 + paymethod 2” | 后付费与实时批价的组合是否成立 |
| 19 | 计费方案三算术 | 每个任务不满 6s 按 6s 计（计费秒数都是 6 的倍数） | 当天总计费秒数例 124s（不是 6 的倍数） | 示例自相矛盾 |
| 20 | 版本号 | 报备接口文件名/修订记录 v1.0.1 | 封面表格“文档版本号 V1.0.0” | — |
| 21 | 一期组件命名 | 一期图：dcc-media / dcc-merge / dcc-mdown | 二期图与源码：vi-media / vi-merge / vi-mdown | 一期是否复用中间号 dcc 组件 |
| 22 | 交互图样例 customer_id | 质检任务流程中发起时 customer_id = 126 | qa_server 回调样例 customer_id = 12 | 样例笔误 |

### 6.2 文档内部错误 / 不严谨（接口契约）

| # | 位置 | 问题 |
| --- | --- | --- |
| 1 | 媒体接口 §1 `startTime` | 说明为毫秒级时间戳，样例为 10 位秒级；`config.startTime` 又是 `yyyy-MM-dd HH:mm:ss` 字符串；WS 接口要求 ISO8601 或毫秒字符串——同一字段 3 种格式 |
| 2 | 媒体接口 §1 `userId` | 消息体 `userId` 为企业编码样例（字母+数字），`config.userId` / `platformId` 为数字 customerId，二者含义不一致 |
| 3 | 媒体接口 §2 样例 | 键名大驼峰（SegmentID、CallerNum…）与字段表小驼峰不一致；不是合法 JSON（无引号、空值、IP 被空格打断）；`FileName` 带扩展名而字段说明“不含扩展名”；`Path` 未以 `/` 开头；`UserData` 是数组而字段类型为 string；Group 为空 |
| 4 | 媒体接口 §2 `segmentId` | “从 0 或 1 开始”未定，消费方无法判断是否丢片 |
| 5 | 媒体接口 §3 | `segments` 标记必传却又写“若未传使用默认值 60”；`calleeNum` 写成“E.1601 格式”；`fmt` 枚举与 §1 `pcmFormat`（pcm_s16le 等）命名不一致 |
| 6 | 媒体接口 §4 `/v2/task/create` | `taskCallBackUrl`、`subTaskCallBackUrl` 必传但样例缺失；`callIdList` 说明英文逗号、样例用中文全角逗号；`ruleId` 样例与“质检任务流由我方提前配置”关系未说明 |
| 7 | 媒体接口 §5 `/call//media/inspect` | 路径双斜杠；字段表（config、frames、direction、seqId）与样例（userId、callerNum、calleeNum、mergeConfig、startTime、recordMode、callbackUrl）大面积不一致；`seqId` 为 uint8，最多 256 次请求后溢出；`frames` 叫“帧数”却单位为秒；返回 `code` 为 int 200，而其他接口为字符串 "0000" |
| 8 | 媒体接口 §6 | 只有外链，无内容 |
| 9 | 报备接口 话术修改 / 号码新增删除 | `operateType` 只写“新增 删除”，未给编码，样例分别为 "2" 和 "1" |
| 10 | 报备接口 号码任务结果回调 | `result` 描述残缺（“全部成功0-全部失败”），未覆盖部分成功；无逐号码失败明细 |
| 11 | 报备接口 号码新增/删除 | 无 `match_type`，无法报备前缀号段；`numberList` 元素类型未定义；单次数量上限未定义；描述“客户调用此接口通知语音专线平台”，与其他接口“语音专线平台通过该接口…”调用方向相反，调用方是谁待确认 |
| 12 | 报备接口 鉴权 | 签名仅 `MD5(timestamp + appkey)`，不覆盖请求体，5 分钟内可被重放/篡改；MD5 强度弱；`requestId` 是否做幂等未说明 |
| 13 | 报备接口 通用 | 所有 URL 为“待定”；“返回值定义”、术语、缩略语、附录均为空；无统一错误码表（仅 0000/1001/“其他值”） |
| 14 | 报备接口 版本说明 | “V1.0.1 新增 2.5 和 2.6 接口”——按章节顺序 2.5/2.6 可能是“客户注销”与“号码新增/删除”，也可能是“号码新增/删除”与“号码任务结果回调”，待确认 |
| 15 | 方案+需求 号码报备表样例 | 时间格式写成 `yyyy-dd-mm`（应为 yyyy-mm-dd） |
| 16 | 方案+需求 导出文件名 | 规则写“前时间-后时间-一级企业-二级企业-三级企业-四级企业-通话统计.xlsx”（连字符、四级全列），示例用下划线且省略上级、出现“所有终端/所有公司”占位，规则不自洽 |
| 17 | 方案+需求 列表区 | “企业”与“公司”都写“接入企业名称”，重复 |
| 18 | 交互图 主账户表 | 字段 `pattem` 疑为 `pattern` 拼写 |

### 6.3 与常识 / 工程风险相关的问题

| # | 问题 | 说明 |
| --- | --- | --- |
| 1 | “总长度 ≠ 15 位”判据会误判 | 普通 700 号码 15 位；7001X（5 位）+ 10 位被叫 = 15 位。400/800 号码正好 10 位，带 7001X 前缀后会被当作普通 700 呼叫走大网，不进入质检（待确认 10 位被叫场景是否存在、是否需改为前缀+白名单判定） |
| 2 | `call_duration_m tinyint` | 有符号 tinyint 最大 127 分钟，超长通话溢出；`call_duration_s smallint` 最大 32767 秒（约 9.1 小时），`file_duration` 同 |
| 3 | 按天分表 + ALTER | 二期 ALTER 只改 `bill_new`、`bill_template`；若按天分表已预建（存储过程提前建表），已建出的日表缺新字段（待确认建表时点） |
| 4 | 二期结果字段默认“正常” | `quality_inspection_segment_result`/`final_result` 默认 1（正常），“未质检/质检失败”与“质检正常”无法区分 |
| 5 | 管控 Redis key 不含企业维度 | `highfreq-{caller}` 等按主叫号码建 key；`biz_num_auth.num` 全局唯一缓解了冲突，但若号码转报备给另一企业，旧 key 在 TTL 内仍生效 |
| 6 | `control_valid_second` 默认 0 | 分析类策略命中后“设置超时时间”，0 时是永不过期还是立刻过期，未定义 |
| 7 | 异地大范围 `stat_cycle_second` 默认 0 | 话务模型只支持 86400/3600，默认配置会被跳过，策略静默失效 |
| 8 | 自然日/自然小时窗口 | “短时高频 300 次/24 小时”实际是“自然日内 300 次”，零点清零，与“24 小时”字面不符 |
| 9 | 管控延迟 | 分析类脚本每分钟一次（非工作时间每 10 分钟），命中前存在分钟级窗口；cron 无防重入，跑超 1 分钟会叠加 |
| 10 | Redis 策略 7~13 无来源 | 外省/外市大范围高频、沉默号码突增、闲时高频、短时高频突增、短时长通话突增、沉默号码外地长通话突增 7 类 key 有定义，但无策略表、无脚本、无 crontab、无运行逻辑说明（待确认是否为后续需求或由其他服务写入） |
| 11 | Redis 样例全部 opType=1（放通） | 只有 city-highfreq 为 0（拦截），且其 JSON 缺 `{`；策略表默认 2（告警）在 Redis 样例中未出现 |
| 12 | 号段表注释重复 | `area_code` 与 `center_area_code` 注释都是“长途区号”；ICSCF 表 `center_area_code varchar(5)` 而号段表为 `varchar(10)`，关联时长度不一致 |
| 13 | ICSCF 表含个人信息 | `contact_name`、`phone` 为联系人姓名电话，初始化数据随文档流转，需按个人信息管理 |
| 14 | 号码报备 etcd 人工刷新 | 上传/下线后需人工点“etcd 更新”才生效，接口报备（3.1.6）是否自动刷新未说明；漏点会导致新号码被拒接（7001 方案） |
| 15 | 录音完整性 | 媒体图明确“开启实时分片时只做格式转换，并不保存录音数据，可能中间部分丢失”，与 PFS 存储完整音频 ≥2 个月的需求冲突，未给结论 |
| 16 | 计费依据 | 计费基于“质检任务耗时”（89s 等），不是通话时长或录音时长；任务耗时受系统处理速度影响，作为计费依据需确认 |
| 17 | 敏感信息 | 交互图在开户响应中明文返回 `secretkey`，子账户创建请求中明文携带 `secretkey`；样例文档中出现内网 IP 与号码样例（本报告已脱敏） |

### 6.4 缺失文档 / 待补充

| # | 缺失内容 | 线索 |
| --- | --- | --- |
| 1 | “企业管理迭代 \| 知识库管理”（企业表：账户类型、质检模式、分片控制、质检能力、xdtmf 回调地址、企业回调地址） | 方案+需求 §3.8.2.4 外链 |
| 2 | “AI 质检平台接口文档”（qa-server 对外接口、回调报文） | 媒体接口 §6 外链 |
| 3 | “20260601-话务管控模块需求” | 运行逻辑文档引用 |
| 4 | 主账户表、一~四级企业表（customer_tab、company_tab 等）、质检话单表、专线质检配置库的 DDL | 交互图、号码表 DDL 引用 |
| 5 | vi-AS ↔ vi-bill 通话消息、vi-bill → vi-pusher 推送消息、qa-server ↔ scheduler MQ 的 topic/字段 | 一期/二期图 |
| 6 | AS 管控指令接口（`/vi/resultCallBack` 报文）、AS → 700 大网管控指令格式 | 方案+需求“待定” |
| 7 | 媒体 create/update/pickup/livestream/merge/download 接口 | 媒体时序图 |
| 8 | 话单推送给客户的报文格式（vi-pusher → httpProxy → 客户） | 一期/二期图 |
| 9 | 能开 ↔ 对客质检平台、对客质检平台 → PDC 的接口 | 交互图 |
| 10 | 话务分析用的话单表（含 `call_out_area_code`）及其同步方式 | 运行逻辑 SQL |
| 11 | 初始化数据 sql（号段 26.41 MB、ICSCF 66.21 KB） | DDL 文档附件 |
| 12 | 呼叫状态（空号/被叫忙/接通/挂机/主叫放弃）与 `finish_state`/`q850` 的映射；`location`、`finish_state`、`operator` 枚举 | 方案+需求 §3.8.2.1、话单 DDL |
| 13 | 一期/二期上线范围与时间边界（需求表格标注“一期”“二期”“后期完成”“待定”混用） | 方案+需求 §3 |
