---
id: module-qa-server
title: qa-server 代码深度解析（质检入口与结果汇聚）
domain: call_qc
kind: dynamic
audience: [developer, architect, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [svc-qa-server]
source_of_truth: [vi-assets/qa-server-master]
---

> 本文由 Agent 逐模块阅读源码生成（2026-10-15，源码快照 `vi-assets/qa-server-master/`），代码位置相对该仓库根目录。
> 结构化事实以 `05-code/services/qa-server.yaml` 为准；标"待确认"的结论需人工核对，核对后把 status 改为 active。

# qa-server 仓库分析报告

> 分析对象：`/workspace/vi-assets/qa-server-master`
> 分析角色：架构师（architect）
> 说明：所有结论均来自代码阅读，引用格式 `相对路径:类.方法`；无法从代码确认的标注“待确认”。报告不含任何密码、Token、AK/SK、连接串、主机地址；配置只写配置项名。

---

## 1. 定位

qa-server 是语音质检的**任务入口与结果汇聚服务**，在整条链路中的位置：

```text
vi-billcenter(vi-bill) ──HTTP(经 SLB, EOPAUTH 签名)──▶ qa-server ──RabbitMQ──▶ scheduler（AI 编排）
                                                          ▲                         │
                                                          └──RabbitMQ(质检结果)─────┘
qa-server ──HTTP 回调(子任务/主任务)──▶ vi-bill
qa-server ──LPUSH 分钟桶──▶ Redis ──每分钟 Stream Load──▶ 海山数据库（StarRocks，*_hs 引擎）
```

职责：

1. 对外提供质检任务创建 HTTP 接口（普通质检 `/v1/front/create`、内控 `/v1/task/create`、定制流程 `/v1/task/customize`、专线 `/v2/task/create`、文本 `/v1/text/check`），以及关键词包/话术/行业企业/号码等配置管理接口。
2. 鉴权（IP 白名单 + EOPAUTH MD5 签名 + 回调 URL 白名单）、去重、限流（单次任务条数上限）。
3. 将任务异步落库（MySQL qa_task / dcc_qa_task）、缓存（Redis）后投递 MQ 给 scheduler。
4. 消费 scheduler 回传的子任务/主任务结果，合并各模型结果，按策略配置补充管控动作，HTTP 回调 vi-bill；失败走延迟队列重推。
5. 结果明细先按分钟写入 Redis List，由定时任务批量 Stream Load 到 StarRocks（qa_detail / qa_segment / dcc qa_detail）。
6. 专线（qa_type=2）质检失败重试（vi_cdr.qa_fail 表 + 10 分钟一次的重试任务）。
7. 准实时质检：消费 xpusher 推送的话单消息并下发 scheduler，结果推回 xpusher。
8. 通过 ETCD 版本号 + 本地文件版本实现多进程/多实例配置缓存同步。

“海山数据库”在代码中对应 `*_hs` 结尾的引擎（`mid_strategy_hs`、`dcc_strategy_hs`、`hs_bill`）以及 `starrocks://` 方言 + Stream Load，推断为 StarRocks（命名“海山”与 StarRocks 的对应关系 待确认）。

## 2. 技术栈

| 项 | 内容 |
| --- | --- |
| 语言 | Python 3.14（`Dockerfile` 第 1 行被注释的基础镜像为 python 3.14.6；当前实际 FROM 为内部 qa-server 1.4.6 基础镜像，其内 Python 版本 待确认） |
| Web 框架 | Flask 3.1.2 + flask-cors；自定义 JSON Provider（`ensure_ascii=False`） |
| WSGI | Gunicorn 23，`worker_class=gthread`，`timeout=60`，`preload_app` 被注释（`gunicorn_conf.py`） |
| 定时 | APScheduler 3.11 `BackgroundScheduler`（`app.py`） |
| MQ | RabbitMQ，pika 1.3.2（`utils/mq.py:MsgQueue`），使用 x-delay 延迟消息（需 delayed-message 插件，待确认） |
| 缓存 | redis-py 7.1（`utils/redis.py`） |
| 关系库 | SQLAlchemy 2.0.45 + PyMySQL，多 bind（`utils/mysql.py`） |
| 分析库 | clickhouse-sqlalchemy、starrocks 1.3.3（`utils/clickhouse.py`），StarRocks Stream Load（`apps/sub_worker.py`） |
| 文档库 | pymongo（失败消息落库、scheduler 流程定义读取） |
| 配置中心 | etcd3 0.12（`utils/etcd.py`），仅用作“版本号”通知 |
| 监控 | prometheus-client（multiprocess 模式，`apps/prometheus.py`、`consumers.py`） |
| 其他 | jsonschema（入参校验）、pycryptodome（AES）、IPy（IP 判断）、pypinyin（企业简称）、urllib3 PoolManager |
| 构建 | `Dockerfile` / `DockerfileTest`：内部基础镜像 → `pip install -r requirements.txt` → 设置 `PROMETHEUS_MULTIPROC_DIR=/tmp/prometheus_multiproc` |
| 启动入口 | `CMD gunicorn -c gunicorn_conf.py app:app`；`app.py` 为 Flask 应用 |
| 端口 | README 示例：HTTP `GUNICORN_BIND` 0.0.0.0:7220；Prometheus 7225（实际值取配置项 `GUNICORN_BIND`、`PROMETHEUS_PORT`） |
| 部署脚本 | 仓库中无 `bin/`、无 k8s/compose 文件；仅 Dockerfile（部署编排 待确认） |
| 环境区分 | 环境变量 `FLASK_ENV`：`production`→`config.prod/`，`test`→`config.test/`，其他→`config.local/`（`config.py`）。仓库内这些目录只有 `logging.yaml`，**`config.yaml` 不在仓库中**，推断由部署时注入（待确认） |
| 日志 | 生产 `/var/log/qa-server/{info,errors,all,realtime,result,flush}.log`；logger：`all`、`realtime`、`result`、`flush`（`config.prod/logging.yaml`） |

## 3. 代码结构表

| 路径 | 职责 |
| --- | --- |
| `app.py` | 创建 Flask app、注册蓝图（bp_v1、bp_manage、bp_vi、bp_v2）、初始化 Redis、加载缓存、定义 APScheduler 任务、`start_background_tasks()` |
| `gunicorn_conf.py` | Gunicorn 配置与钩子：`on_starting` 清理 multiproc 目录；`when_ready` 启动后台任务；`post_fork` 启动缓存版本监听；`child_exit` 清理指标文件 |
| `gunicorn_logger.py` | Gunicorn 日志适配 |
| `config.py` | 按 `FLASK_ENV` 加载 `config.yaml`、`logging.yaml`，创建 `LOGGING_PATH` |
| `consumers.py` | 启动 Prometheus HTTP、ETCD Watcher、三类 MQ 消费线程，注册 atexit 关闭 |
| `models.py` | SQLAlchemy 模型（MySQL 与 CK/StarRocks） |
| `apps/__init__.py` | 蓝图定义、`before_request` 鉴权、`after_request` 日志/计数、统一错误处理 |
| `apps/views.py` | `/v1`、`/v2`、`/manage` 接口 |
| `apps/vi/views.py` | `/vi` 行业语音质检企业/号码管理接口 |
| `apps/service.py` | 任务创建核心逻辑：取话单、拼录音 URL、组装 scheduler 参数、异步落库与投递、无 AI 直写结果 |
| `apps/strategy.py` | 专线分片/抽检路由决策 `resolve_segment_route` |
| `apps/result_consumer.py` | `QaResultConsumeThread`：消费 scheduler 结果、合并、写 Redis、回调 |
| `apps/task_consumer.py` | `QaTaskConsumeThread`：消费 xpusher 准实时话单消息并下发 scheduler |
| `apps/repush_consumer.py` | `RepushConsumeThread`：消费延迟重推队列 |
| `apps/retry_worker.py` | `retry_qa_fail`：专线失败重试（10 分钟一次） |
| `apps/sub_worker.py` | `flush_data_to_db`：Redis 分钟桶 → StarRocks Stream Load（每分钟） |
| `apps/prometheus.py` | 指标定义 |
| `utils/redis.py` | 连接池、分布式锁（SET NX EX + Lua 续期/释放/提交） |
| `utils/mq.py` | `MsgQueue`：发布确认、线程本地 channel、断线重连消费 |
| `utils/etcd.py` | `EtcdWatcherManager`、`update_version`、装饰器 `update_etcd` |
| `utils/cache.py` | 进程内缓存 `CacheState` 与各类 getter（未命中回源数据库） |
| `utils/cache_sync.py` | 基于本地版本文件的多 worker 缓存刷新 |
| `utils/mysql.py` / `utils/clickhouse.py` | 引擎与会话；CK 查询熔断器 |
| `utils/snowFlake.py` | 任务 ID 生成、话单 ID 解析（时间/区域/AS 编号） |
| `utils/helpers.py` | 常量（`QA_INFO_EXPIRATION`、`GZH_LIST`）、`encrypt_id`、内网判断、请求去重 |
| `utils/aes.py` | AES-CBC 加解密工具（密钥来自配置项 `AES_INFO`） |
| `utils/schema.py` | jsonschema 校验、URL 校验 |

## 4. 核心业务流程

### 4.1 进程模型与启动（`gunicorn_conf.py`、`app.py`、`consumers.py`）

| 步骤 | 做什么 | 代码位置 | 失败/备注 |
| --- | --- | --- | --- |
| 1 | master 启动，清空 Prometheus multiproc 目录 | `gunicorn_conf.py:on_starting` | — |
| 2 | 导入 `app.py`：注册蓝图、`Redis.init`、`load_cache()`、`load_qa_rule()` | `app.py` | 未开 preload，worker fork 后各自也会导入 app 并加载缓存 |
| 3 | master `when_ready` 调 `app.start_background_tasks()`：启动 APScheduler，非 debug 时 `consumers.start_consumers_once()` | `gunicorn_conf.py:when_ready`、`app.py:start_background_tasks` | **定时任务与 MQ 消费线程运行在 Gunicorn master 进程**，worker 只处理 HTTP |
| 4 | 启动 Prometheus WSGI server（重试、抓取超时 8s、响应缓存） | `consumers.py` | — |
| 5 | 启动 `EtcdWatcherManager(prefixes=[config_key, rule_key])` | `consumers.py`、`utils/etcd.py` | 断线自动切换节点、补偿全量刷新 |
| 6 | 启动 `RESULT_CONSUMERS` 个 `QaResultConsumeThread`、`REALTIME_CONSUMERS` 个 `QaTaskConsumeThread`、`REPUSH_CONSUMERS` 个 `RepushConsumeThread` | `consumers.py` | atexit 统一关闭 |
| 7 | 每个 worker fork 后启动缓存版本文件监听线程 | `gunicorn_conf.py:post_fork` → `utils/cache_sync.py:start_cache_reload_watcher` | 每 `QA_CACHE_SYNC_INTERVAL`（默认 1s）轮询 |

### 4.2 鉴权（`apps/__init__.py:prepare`，所有请求）

| 步骤 | 做什么 | 失败处理 |
| --- | --- | --- |
| 1 | OPTIONS 直接放行 | — |
| 2 | `/manage*`：仅允许内网 IP（10/8、172.16/12、192.168/16），不验签 | 403 |
| 3 | 其他路径：来源 IP 必须在 Redis SET `QASERVER:WHITEIP`；支持 `a.b.c.*`、`a.b.*` 通配，通配命中后把精确 IP `SADD` 回集合 | 403 |
| 4 | 解析 `Authorization: EOPAUTH appid="..", timestamp="..", signature=".."` | 403 |
| 5 | 时间戳 ≤10 位，且 `0 ≤ now - ts ≤ 300s` | 403 |
| 6 | `get_company_secret(appid)` 取 company_tab.app_secret（缓存），签名 = `MD5(timestamp + secret)` 大写 | 403；**签名不包含 body**，可重放 5 分钟内任意内容 |
| 7 | 请求体中 `taskCallBackUrl`、`subTaskCallBackUrl` 必须在 Redis SET `QASERVER:WHITEURL` | 403 |

### 4.3 质检任务创建（以 vi-bill 实际调用的 `/v1/task/customize` 为主线）

| 步骤 | 做什么 | 代码位置 | 失败/重试/超时 |
| --- | --- | --- | --- |
| 1 | jsonschema 校验（textList/urlList、userData 等） | `apps/views.py`（customize 视图）、`utils/schema.py:validate_json` | 1002/1003 |
| 2 | `check_app_info`：按 channelId 取 subject/app_info，校验 `g.app_key == subject.app_key`；qa_type==1 还要求 status==1、mode_status==1 | `apps/views.py:check_app_info` | 1003 |
| 3 | 请求去重：Redis `SET AIQC_REQUEST_{requestId} NX EX 120` | `utils/helpers.py:request_dedup_guard` | 重复 1001；Redis 异常 fail-closed 返回 9999 |
| 4 | qa_type==2 时 userData 必填，`get_info_by_customer_id(customerId)` 状态必须为 1 | `apps/views.py` | 客户不存在时代码对 `{}` 取 `["status"]` 抛 KeyError → 500/9999 |
| 5 | 读 MongoDB `scheduler.process` `find_one({processId: customizedDetectionId})`，`aiModels` 逗号拆分映射成 is_asr/is_keyword/... 等开关 | `apps/service.py:create_task_by_process` | 流程不存在返回错误码 |
| 6 | 校验条数 ≤ `TASK_LIMITATION`，生成 task_id（`get_next_id`） | `apps/service.py`、`utils/snowFlake.py:get_next_id` | — |
| 7 | 组装子任务缓存 dict 与 scheduler 参数 | `apps/service.py:prepare_sub_data`、`prepare_scheduler_params` | — |
| 8 | **提交线程池异步执行 `_persist_task_and_cache`，接口立即返回 0000 + taskId** | `apps/service.py:_async_executor` | 线程池大小 `ASYNC_WORKER_THREAD_COUNT`；积压由指标 `pending_async_tasks` 反映 |
| 9 | 异步：Pipeline 写 `{task_id}:{data_id}`（TTL 50h） | `apps/service.py:_persist_task_and_cache` | — |
| 10 | 异步：查找/创建 qa_rule（有 rule_id 时仅 qa_type==1 更新库中 data/sub_url/main_url，内存总是更新；无 rule_id 时匹配已有规则或新建 `OT+时间戳`） | 同上 | — |
| 11 | 异步：写 `QARULE:{task_id}`（TTL 50h）；插入 QaTask / DccQaTask（status "0"）并提交 | 同上 | — |
| 12 | 提交后刷新规则缓存；若规则有更新，`notify_cache_reload("rule")` + `update_version(rule_key)` | 同上、`utils/etcd.py:update_version` | — |
| 13 | 发布 scheduler 消息到 `to_scheduler_exchange`/`to_scheduler_routing_key` | `utils/mq.py:MsgQueue.publish` | publisher confirm，失败重试 3 次 |
| 14 | 任一步异常：`_mark_task_failed` 把 qa_task.status 置 "2" | `apps/service.py:_mark_task_failed` | **调用方不会被通知**（接口早已返回成功） |

其他创建入口差异：

- `/v1/front/create` → `create_task_by_ability`：callIdList/urlList 与 dccList 互斥；按 `get_process_id(app_id)` 选流程（`GZH_LIST` 内用 `PROCESS_COLLECTION` 的 `atom_common`，否则 `atom_common_dsz`）；callIdList 需从 CK 查话单（`get_bill_list`）拼录音 URL；business_type DCC=1 否则 0；以 "700" 开头且长度 32 的 ID 视为 DCC（700 号码）话单。
- `/v1/task/create`（内控）：按 ruleId 建任务，无 `check_app_info`，dccList 时取 dcc_qa_rule。
- `/v2/task/create`（专线）→ `create_task_by_rule`：ruleId 缺省取 app_info.rule_id，规则 status 必须 '1' 且 `rule.app_id == channelId`；qa_type==1 时 is_mid_qc==1 用规则优先级、否则用 app 优先级；qa_type!=1 时走 `resolve_segment_route_from_user_data`（见 4.4）。
- `/v1/text/check` → `create_task_of_text`：纯文本任务。

### 4.4 专线分片/抽检路由（`apps/strategy.py:resolve_segment_route`）

输入：通话时长 d、`is_segment`、`is_stream`，阈值来自 customer_tab 的 `call_duration_t1`(T1)、`call_duration_t2`(T2)、`sampling_ratio_p/p1/p2`（0~100）。

| 条件 | 结果 |
| --- | --- |
| is_stream 为真（`_from_user_data` 包装中 isStream 缺省为 True） | realtime（使用 stream_priority） |
| is_segment==1 且 d ≤ T1：d ∈ [0.5·T1, T1] 时随机 r：r<p1→near_realtime，r<p1+p2→offline，否则 no_ai（p1+p2>1 时归一化）；区间外 → no_ai | — |
| is_segment==1 且 T1 < d ≤ T2 | near_realtime |
| is_segment==1 且 d > T2 | realtime |
| is_segment≠1 且 d ≤ T1：区间 [0.5·T1, T1] 内以概率 p → offline，否则 no_ai；区间外 → no_ai | — |
| is_segment≠1 且 d > T1 | offline |

路由动作（`apps/service.py:create_task_by_rule`）：offline→按能力创建（app 优先级）；no_ai→`write_result_to_db`；realtime→stream_priority；near_realtime→app 优先级；未知→9999。

### 4.5 无 AI 直写结果（`apps/service.py:write_result_to_db` / `_persist_result_to_db`）

1. 插入 QaTask，status=1、完成率 100%。
2. 构造 qc_result="1"（正常）的明细，LPUSH 到 `QASUBRESULT:{minute}`；流式写 `VIQASUBRESULT:{minute}`，endFlag 时同时写 `QASUBRESULT:{minute}`。
3. HTTP 回调 sub_url、main_url（`push_http_message`，超时 5s，异常投递 repush 交换机，**不带 x-delay**）。

### 4.6 结果消费与回调（`apps/result_consumer.py:QaResultConsumeThread`）

| 步骤 | 做什么 | 代码位置 | 失败/重试 |
| --- | --- | --- | --- |
| 1 | 消费 `from_scheduler_queue`，手动 ack，`prefetch_count` 来自配置；finally 中 `safe_ack` | `QaResultConsumeThread` | 处理异常也会 ack（消息不重回队列） |
| 2 | type==1 → `sub_result`；否则 → `main_result` | — | — |
| 3 | 取规则：`QARULE:{task_id}` → qa_task → 规则缓存 | `sub_result` | — |
| 4 | 取子任务上下文 `{task_id}:{data_id}`，缺失时按规则重建 | `sub_result` | — |
| 5 | 合并各模型结果字段（asrText→content，GZH 应用 content 置空；matchedWord、summary、emotion、scene、script、vfd、dify、rt*、deepseek、qwen、rag、cqAnti*、bge*、cqAgent* 等），记录模型耗时指标 | `sub_result` | — |
| 6 | rt 检测：svcType "0" 时 firstLabel "4" 视为涉诈（rtDetectionResult=0）；否则 firstLabel "11" 视为一致 | `sub_result` | — |
| 7 | 有 userData 且 qc_result=="0"、risk_level 有值：`get_strategy_config(customer_id, risk_level, process_id, control_type=1)`（user_qc.qa_strategy_config，仅 status==1），向 userData 追加 ctrlAction、audioCode、audioDirection、callId；control_action==2 时清空 audio_code | `sub_result`、`utils/cache.py:get_strategy_config` | — |
| 8 | 时长换算：duration_min=ceil(d/60)，duration_ps=ceil(d/6)；qa_type 2 流式末片使用 `completeRecordUrl` | `sub_result` | — |
| 9 | 策略范围内（qa_type==2 且非流式或末片）执行 `_handle_qa_fail_retry`（见 4.7） | `sub_result` | — |
| 10 | LPUSH 明细到 `DCCQASUBRESULT:{minute}` / `VIQASUBRESULT:{minute}` / `QASUBRESULT:{minute}` | `sub_result` | Redis 失败时直接经 `ck_session_scope` 写 mid_strategy_hs（QaDetail/QaSegment）或 dcc_strategy_hs（DccQaDetail），计数 `result_write_error_counter` |
| 11 | 删除 `{task_id}:{data_id}`（若待重试则改为续期/重写） | `sub_result` | — |
| 12 | risk_level 为 1 或 2 且在策略范围：POST 配置项 `STRATEGY_CONFIG_URL`（taskId、dataId、riskLevel、userData、finishTime、ragResults 等） | `sub_result` | 接收方 待确认 |
| 13 | 若 `rdc.get(task_id)` 存在（准实时任务）且非 DCC：发布到 Realtime_RabbitMq `to_xpusher_exchange`/`to_xpusher_routing_key`（补 callId、companyId、customerId） | `sub_result` | — |
| 14 | 否则 POST 规则 sub_url（vi-bill `/vibillcenter/subtask/callback`），body 中 status "0"，部分成功为 "1" | `push_http_message` | 成功判定：HTTP 200 且 code=="0000"；否则 `push_to_delay_mq`（x-delay 180000） |
| 15 | `main_result`：qa_task status="1"，写 completion_ratio、error_num；POST main_url `{taskId, finishRate, error, data}` | `main_result` | 同上 |
| 16 | 回调过程异常：消息写 MongoDB `service.process_fail`（asrText 先 base64 再 AES 加密） | — | — |

### 4.7 专线失败重试（`apps/result_consumer.py:_handle_qa_fail_retry` + `apps/retry_worker.py:retry_qa_fail`）

| 情形 | 动作 |
| --- | --- |
| 结果成功 | 若存在 qa_fail 记录：retry_status=2、is_done=1；正常写 Redis、回调 |
| 首次失败 | Redis 明细写 status="3"（重试中），插入 vi_cdr.qa_fail；**不回调客户** |
| 中间失败 | retry_status=0，不写 Redis、不回调 |
| retry_count ≥ 5 | retry_status=3、is_done=1，写结果并回调 |

`retry_qa_fail`（每 10 分钟）：

1. 获取分布式锁 `QAFAIL:retry:lock`（600s，带续期）。
2. 以 after_id 游标分批（5000）查 `is_done=0 AND retry_status=0 AND retry_count<5`。
3. 按 task_id 分组，取规则与 `get_info_by_customer_id` 优先级，发布 scheduler（isStream=False、needTaskCallback=False），成功后 retry_status=1、retry_count+1。
4. 无规则/无流程/无 app_info/列表为空：is_done=1、retry_status=3（放弃）。

### 4.8 延迟重推（`apps/repush_consumer.py:RepushConsumeThread`）

- 消费 `repush_queue`；`destination` 为 "mq"（推 xpusher）或 URL。
- x-delay 递进：None→下次 300000；300000→600000；≥600000→-1（最后一次）；其他值（含首发的 180000）→300000。即首次失败后约在 +3min、+5min、+10min 重试，最后一次失败写 MongoDB `service.push_fail`（转写文本加密）。

### 4.9 准实时话单任务（`apps/task_consumer.py:QaTaskConsumeThread`）

1. 消费 Realtime_RabbitMq `from_xpusher_queue`（xpusher 推送的话单）。
2. 取规则：Redis `{task_id}`（**无 TTL**）→ QaTask join QaRule。
3. 拼录音 URL（location 3 使用 `QA_FILE_SERVER` 而非 ZJH_SZ）。
4. 写 `{task_id}:{callid}`（TTL 50h），直接查 MySQL 取关键词与话术。
5. 发布 scheduler，`needTaskCallback: False`。
6. handleStartTime 为空时抛异常（消息仍被 ack）。

### 4.10 结果入库（`apps/sub_worker.py:flush_data_to_db`，每分钟）

| target | 写入引擎 | Redis key 前缀 | 模型 |
| --- | --- | --- | --- |
| mid | mid_strategy | `QASUBRESULT` | QaDetail |
| dcc | dcc_strategy | `DCCQASUBRESULT` | DccQaDetail |
| vi | mid_strategy_segment | `VIQASUBRESULT` | QaSegment |

步骤：

1. 获取锁 `{prefix}:lock`，读取游标 `{prefix}:latest`；从 latest 下一分钟扫到 now−2min（**latest 不存在时从 now−2min 开始**）。
2. 对每个分钟桶 `{prefix}:{yyyyMMddHHmm}` 以 LRANGE 分块（MAX_LENGTH 200000），写 JSON 文件到 `CSV_PATH`。
3. `PUT http://{host}:{port}/api/{db}/{table}/_stream_load`（跟随 307），label=`load_{db}_{table}_{minute}[_{chunk}]` 保证幂等；`max_filter_ratio=0.1`、`timeout=300`。
4. "Label Already Exists" 视为成功；"Publish Timeout" 或客户端超时 → 经 mid_strategy_hs 轮询 `information_schema.loads`（10s × 18 次）。
5. 成功后 Lua `commit_flush_progress`：锁仍持有时原子地设置 latest 并删除 list。
6. 失败保留 key 并终止本轮，下分钟重试。JOB_TIMEOUT 1800s，STREAM_LOAD_TIMEOUT 60s；连接参数来自 `STARROCKS_CLIENT_PARAMS[engine]`。

### 4.11 行业语音质检企业管理（`apps/vi/views.py`）

- `create`：校验省/市/区层级（qa_enterprise：level 2 市→channel，level 3 区→company）与 mobile_tab；企业简称唯一；新建 customer_tab（name 为拼音首字母、source=3，qa_type/priority/能力等复制自 qa_subject，qa_direction 由 recordChannel 映射 `{"0":3,"1":4,"2":1,"3":2}`，scenario_id=场景，script_id 来自 `save_scripts`）。subject.is_segment≠2 且有 rule_id 时：场景映射词包（enterprise_scene）并 `clone_qa_rule_for_customer`。有 numberList（<10000）时异步 `num_operation`。
- `num_operation`（`@update_etcd(number_key)`）：操作 user_qc.biz_num_auth；新增时任一号码已存在则失败，删除要求全部属于该客户；完成后回调 num_callback_url `{taskId, result 1/0, reason}`（超时 30s）。**执行器内异常被吞，不发回调**。

## 5. 对外接口表

| 协议 | 方法+路径 | 处理位置 | 关键入参 | 关键出参 | 说明 |
| --- | --- | --- | --- | --- | --- |
| HTTP | POST `/v1/front/create` | `apps/views.py`（front create 视图）→ `service.create_task_by_ability` | requestId、channelId、callIdList/urlList 或 dccList、taskCallBackUrl、subTaskCallBackUrl、wordPackageId、scripts 等（schema additionalProperties=False） | code、msg、taskId | 普通/700 质检；两类列表互斥 |
| HTTP | POST `/v1/keyword/create` | `apps/views.py` | 词包名、words | code、wordPackageId | mid_strategy.qa_word_package；≤200 词、总长 ≤2048、单词 <8 字；成功后 bump ETCD config_key |
| HTTP | POST `/v1/keyword/update` | `apps/views.py` | wordPackageId、op（1 增加/2 删词/3 删包）、words | code | 同上 |
| HTTP | POST `/v1/script/create` | `apps/views.py` | 话术组、话术列表 | code、scriptGroupId | qa_script_group、qa_script |
| HTTP | POST `/v1/script/update` | `apps/views.py` | op（1 新增/0 删除） | code | 同上 |
| HTTP | POST `/v1/task/create` | `apps/views.py` | ruleId、audioList 或 dccList | code、taskId | 内控任务 |
| HTTP | POST `/v1/task/customize` | `apps/views.py` → `service.create_task_by_process` | customizedDetectionId（流程 ID）、textList/urlList、userData（customerId、companyId、callerNumber、calleeNumber、handleStartTime、asCallBackUrl、isStream、endFlag、segmentId、isSegment 等） | code、taskId | **vi-bill 实际调用的接口**；schema 中存在拼写错误 key `"companyId:"` |
| HTTP | POST `/v1/text/check` | `apps/views.py` → `create_task_of_text` | 文本 | code、taskId | 文本质检 |
| HTTP | POST `/v2/task/create` | `apps/views.py` → `create_task_by_rule` | urlList（minItems 1）、userData（customerId、isSegment、duration 必填）、ruleId 可选 | code、taskId | 专线质检（含分片路由） |
| HTTP | POST `/manage/info/reload` | `apps/views.py` | — | code | 仅内网；`load_company_info` 并 bump ETCD |
| HTTP | POST `/vi/enterprise/create` | `apps/vi/views.py` | provinceId、cityId、districtId、enterpriseFullname、scene[1–9]、script、recordChannel(0–3)、channelId、numberList/numberUrl 可选 | code、enterpriseId | 去重前缀 "VI"；bump config_key |
| HTTP | POST `/vi/enterprise/modifySceneAndScript` | `apps/vi/views.py` | enterpriseId、scene、script | code | README 写作 modifyScene/modifyScript（见 CONFLICT） |
| HTTP | POST `/vi/enterprise/cancel` | `apps/vi/views.py` | enterpriseId | code | customer_tab.status=3 |
| HTTP | POST `/vi/number/addOrDel` | `apps/vi/views.py` → `num_operation` | enterpriseId、operateType（1 增/2 删）、numberList、num_callback_url | code、taskId；异步回调结果 | user_qc.biz_num_auth |
| HTTP | GET Prometheus `/metrics` | `consumers.py` | — | 指标 | 端口取 `PROMETHEUS_PORT` |

统一返回码：`0000` 成功、`1001` 重复/requestId 为空/话单服务不可用、`1002` 参数为空、`1003` 参数值错误、`9999` 其他（未知路由 404、异常 500 也包装为 9999）。鉴权失败统一 HTTP 403。

## 6. 消息表

| 方向 | 中间件 | exchange / routing_key / queue（配置项） | group | 消息关键字段 | 处理位置 |
| --- | --- | --- | --- | --- | --- |
| 出 | RabbitMQ（Scheduler_RabbitMq） | `to_scheduler_exchange` / `to_scheduler_routing_key` | — | requestId(uuid1)、data[{dataId,url,location?,isReverse?,reuse,audioTag?,segmentId?,endFlag?}]、processId、priority、transactionId(int task_id)、isStream、parameter{…}、needTaskCallback(准实时/重试时 False) | `apps/service.py:_persist_task_and_cache`、`apps/task_consumer.py`、`apps/retry_worker.py` |
| 入 | RabbitMQ（Scheduler_RabbitMq） | queue `from_scheduler_queue`，prefetch `prefetch_count` | — | type（1 子任务/其他 主任务）、transactionId、dataId、各模型结果字段、asrText、riskLevel、qcResult 等 | `apps/result_consumer.py:QaResultConsumeThread` |
| 出/入 | RabbitMQ（Scheduler_RabbitMq） | `repush_exchange` / `repush_routing_key` → queue `repush_queue`，header `x-delay` | — | destination（"mq" 或 URL）、body | `push_to_delay_mq` / `apps/repush_consumer.py:RepushConsumeThread` |
| 入 | RabbitMQ（Realtime_RabbitMq） | queue `from_xpusher_queue` | — | 话单字段（callid、handleStartTime、caller/callee、location 等）、task_id | `apps/task_consumer.py:QaTaskConsumeThread` |
| 出 | RabbitMQ（Realtime_RabbitMq） | `to_xpusher_exchange` / `to_xpusher_routing_key` | — | 子任务结果 + callId、companyId、customerId | `apps/result_consumer.py:sub_result` |
| 入 | ETCD watch | key：配置项 `ETCD_CONFIG.config_key`、`rule_key`（精确匹配） | — | 值为时间戳版本号 | `utils/etcd.py:EtcdWatcherManager` |
| 出 | ETCD put | `config_key`、`rule_key`、`number_key` | — | 当前时间戳 | `utils/etcd.py:update_version`；number_key 本服务不监听，推测由 vi-as 监听（待确认） |

MQ 客户端参数（`utils/mq.py:MsgQueue`）：heartbeat 60、socket timeout 10、blocked timeout 15、connection_attempts 3；发布使用线程本地 channel + confirm_delivery，失败重试 3 次；消费断线 3s 退避重连；回调异常 `nack(requeue=False)`（若无死信配置则消息丢弃，待确认）。

## 7. 依赖调用（出站）

| 目标 | 方式 | 用途 | 位置 | 超时/容错 |
| --- | --- | --- | --- | --- |
| vi-bill 子任务回调（规则 sub_url，实为 `/vibillcenter/subtask/callback`） | HTTP POST（urllib3） | 子任务结果 | `apps/result_consumer.py:push_http_message` | 成功=200 且 code "0000"；失败进延迟队列 |
| vi-bill 主任务回调（main_url，`/vibillcenter/task/callback`） | HTTP POST | 主任务完成 | `main_result` | 同上 |
| 配置项 `STRATEGY_CONFIG_URL` | HTTP POST | 中高风险结果推送策略管控 | `sub_result` | 接收方 待确认 |
| num_callback_url | HTTP POST | 号码操作结果 | `apps/vi/views.py:num_operation` | 30s；异常被吞 |
| 文件服务（配置项 ZJH_SOUTH/YSH_SOUTH/ZJH_NORTH/YSH_NORTH/ZJH_SZ/ZJH_ZZ、QA_FILE_SERVER；700 话单取 dcc_conf.file_down_host） | 仅拼 URL，由 scheduler/ASR 下载 | 录音地址 | `apps/service.py:get_server_url`、`build_url`、`build_url_v2` | — |
| ClickHouse / StarRocks 话单库 | SQL | 按 callId 查话单 | `apps/service.py:get_bill_list`、`utils/clickhouse.py:select_in_ck` | 熔断：3 次失败打开 30s，返回 1001 |
| StarRocks FE/BE | HTTP Stream Load | 结果入库 | `apps/sub_worker.py` | 见 4.10 |
| MongoDB | pymongo | 读 scheduler.process；写 service.process_fail / push_fail | `apps/service.py`、`apps/result_consumer.py`、`apps/repush_consumer.py` | — |
| ETCD | gRPC | 版本通知 | `utils/etcd.py` | 多节点 failover |

录音 URL 规则（`apps/service.py`）：

- `get_server_url(location, as_id)`：location 1 → as_id≥35 用 ZJH_SOUTH，否则 YSH_SOUTH；2 → ZJH_NORTH/YSH_NORTH；3 → ZJH_SZ；4 → ZJH_ZZ。
- `build_url`：`{server}/file/1/{encrypt_id(company)}/{encrypt_id(mserverid)}/{nfsid}/{callid}_{caller}_{callee}_{yyyymmddHHMMSS}.{suffix}`；customer 17 使用前缀 "65772_" 且带 midnumber；extid 15/24（阿里）后缀取 record_type；`encrypt_id` 为数字→3 字母映射。
- `build_url_v2`（700）：`/internal/media/{user_id}/{m_server_id}/{callid}/{ts}/{nfs_id}/{callid}_{yyyymmddHHMMSS}.{suffix}`，host 取 dcc_conf `file_down_host`（host_type=2，按 location）。

## 8. 数据

### 8.1 MySQL 表

| 库（bind） | 表 | 读/写 | 用途 |
| --- | --- | --- | --- |
| 默认（MYSQL_URI） | qa_rule、qa_task | 读写 | 质检规则、任务（status：0 进行中、1 完成、2 失败[代码写入，注释未列]） |
| 默认 | dcc_qa_rule、dcc_qa_task | 读写 | 700 规则与任务 |
| vi_cdr | qa_fail | 读写 | 专线失败重试；唯一键 (task_id, data_id)；retry_status 0 待重试/1 已投递/2 成功/3 放弃 |
| mid_strategy | qa_word_package、qa_script_group、qa_script、qa_word_group、qa_regex、qa_prompt | 读写/读 | 词包、话术、词组、正则、提示词 |
| user_qc | mobile_tab、channel_tab、company_tab（app_key/app_secret）、customer_tab、qa_subject、qa_enterprise、biz_num_auth、enterprise_scene、qa_strategy_config、bge_classifier、bge_rule、bge_rule_item | 读写/读 | 客户体系、行业企业、号码报备、策略配置、BGE 分类器 |
| mid_auth | config_company（record_suffix）、auth_channel（mobile_id）、config_customer | 读 | 拼录音 URL |
| dcc_conf | config_user、config_application、file_down_host | 读 | 700 录音 URL |

### 8.2 ClickHouse / StarRocks

| 引擎（CK_BINDS） | 表 | 读/写 | 用途 |
| --- | --- | --- | --- |
| hs_bill | bill_YYYY_MM_DD（当天/昨天） | 读 | 话单 |
| mid_bill_history | bill_history（`global in`） | 读 | 历史话单 |
| dcc_mid_bill / dcc_mid_bill_history | 700 话单 | 读 | DCC 质检 |
| mid_strategy / mid_strategy_segment / dcc_strategy（Stream Load） | qa_detail、qa_segment、dcc qa_detail | 写 | 子任务明细 |
| mid_strategy_hs / dcc_strategy_hs | 同上 | 写（Redis 失败兜底）/读（loads 状态） | 兜底直写、Stream Load 状态查询 |

qa_detail/qa_segment 关键字段（`models.py:QaDetailBase`）：status（1 成功/0 失败/3 重试中）、qa_type（1 普通/2 专线）、qc_result（0 异常/1 正常）、risk_level、ctrl_action、audio_code、audio_direction、control_type、is_segment、segment_id、is_stream、end_flag、rule_id、各模型结果列。

### 8.3 Redis key

| key 模式 | 类型 | TTL | 用途 | 位置 |
| --- | --- | --- | --- | --- |
| `QASERVER:WHITEIP` | SET | 永久 | IP 白名单（支持通配，命中后回写精确 IP） | `apps/__init__.py` |
| `QASERVER:WHITEURL` | SET | 永久 | 回调 URL 白名单 | `apps/__init__.py` |
| `AIQC_REQUEST_{requestId}` / `VI...` 前缀 | String | 120s | 请求去重 | `utils/helpers.py:request_dedup_guard` |
| `{task_id}:{data_id}` | String(JSON) | 50h（`QA_INFO_EXPIRATION`） | 子任务上下文 | `apps/service.py`、`apps/result_consumer.py` |
| `{task_id}:{callid}` | String(JSON) | 50h | 准实时子任务上下文 | `apps/task_consumer.py` |
| `QARULE:{task_id}` | String(JSON) | 50h | 任务规则快照 | `apps/service.py` |
| `{task_id}` | String | **无 TTL** | 准实时任务标识/规则 | `apps/task_consumer.py`、`result_consumer.py`（rdc.get） |
| `QASUBRESULT:{yyyyMMddHHmm}` | List | 入库后删除 | 普通明细分钟桶 | `result_consumer.py`、`sub_worker.py` |
| `VIQASUBRESULT:{minute}` | List | 同上 | 分片（流式）明细 | 同上 |
| `DCCQASUBRESULT:{minute}` | List | 同上 | 700 明细 | 同上 |
| `{PREFIX}:lock` | String | 带过期 | flush 分布式锁 | `apps/sub_worker.py` |
| `{PREFIX}:latest` | String | 永久 | 已入库分钟游标 | `apps/sub_worker.py` |
| `QAFAIL:retry:lock` | String | 600s（续期） | 重试任务锁 | `apps/retry_worker.py` |

### 8.4 ETCD

`config_key`（客户/词包/话术/企业变更）、`rule_key`（规则变更）、`number_key`（号码变更），值为时间戳版本号（`utils/etcd.py`）。

### 8.5 MongoDB

| 库.集合 | 读/写 | 用途 |
| --- | --- | --- |
| scheduler.process | 读 | 流程定义（processId → aiModels） |
| service.process_fail | 写 | 结果处理/回调异常消息（asrText 加密） |
| service.push_fail | 写 | 重推最终失败消息（转写文本加密） |

### 8.6 文件

- `CSV_PATH` 下 Stream Load 临时 JSON 文件。
- `LOGGING_PATH/cache_version.json`（可由 `QA_CACHE_VERSION_FILE` 覆盖）：worker 间缓存版本同步。
- `/tmp/prometheus_multiproc`：多进程指标。

## 9. 状态与枚举

| 枚举 | 取值 | 来源 |
| --- | --- | --- |
| qa_task.status | 0 进行中、1 已完成、2 失败（代码写入，注释缺失） | `models.py`、`apps/service.py:_mark_task_failed` |
| qa_rule.status | 1 正常、2 暂停、3 取消 | `models.py` |
| customer_tab.status | 1 启用、2 停用、3 注销、4 超用量停用 | `models.py` |
| customer_tab.is_segment | 0 离线、1 实时、2 纯话务、3 行业语音质检 | `models.py` |
| customer_tab.qa_type / qa_detail.qa_type | 1 普通、2 专线 | `models.py` |
| qa_detail.status | 1 成功、0 失败、3 重试中 | `models.py` |
| qa_detail.qc_result | 0 异常、1 正常 | `models.py` |
| qa_fail.retry_status | 0 待重试、1 已投递、2 成功、3 放弃 | `models.py`、`apps/retry_worker.py` |
| 分片路由 | offline / near_realtime / realtime / no_ai | `apps/strategy.py` |
| business_type（ID 生成） | 0 普通、1 DCC | `apps/service.py:create_task_by_ability` |
| MQ 结果 type | 1 子任务，其他 主任务 | `apps/result_consumer.py` |
| recordChannel → qa_direction | "0"→3、"1"→4、"2"→1、"3"→2 | `apps/vi/views.py` |
| 号码操作 operateType | 1 新增、2 删除 | `apps/vi/views.py` |
| 词包 update op | 1 增词、2 删词、3 删包 | `apps/views.py` |
| 话术 update op | 1 新增、0 删除 | `apps/views.py` |
| rt 检测 | svcType "0"：firstLabel "4" 涉诈；否则 firstLabel "11" 一致 | `apps/result_consumer.py` |
| ctrl_action | 来自 qa_strategy_config.control_action；2 时清空 audio_code（含义 待确认） | `apps/result_consumer.py` |
| 接口返回码 | 0000/1001/1002/1003/9999 | `apps/views.py` |

## 10. 定时任务

| 任务 | 调度 | 运行位置 | 内容 |
| --- | --- | --- | --- |
| `flush_data_to_db("mid")` | APScheduler cron 每分钟，max_instances=1 | Gunicorn master | QASUBRESULT → StarRocks qa_detail |
| `flush_data_to_db("dcc")` | 同上 | 同上 | DCCQASUBRESULT → dcc qa_detail |
| `flush_data_to_db("vi")` | 同上 | 同上 | VIQASUBRESULT → qa_segment |
| `retry_qa_fail` | cron `minute="*/10"` | 同上 | 专线失败重试 |
| 缓存版本监听 | 每 1s 轮询 | 每个 worker | 本地缓存刷新 |
| ETCD watch supervisor | 常驻 | master | 断线/停滞（720s）检测与重建 |

多实例部署时，flush 与 retry 依赖 Redis 分布式锁互斥。

## 11. 关键配置项（仅列名称，值见部署注入的 `config.<env>/config.yaml`）

| 分类 | 配置项 |
| --- | --- |
| HTTP/进程 | `GUNICORN_BIND`、`GUNICORN_WORKERS`、`GUNICORN_THREADS`、`LOGGING_PATH`、`CSV_PATH` |
| MySQL | `MYSQL_URI`、`MYSQL_BINDS`（mid_strategy、user_qc、mid_auth、dcc_conf、vi_cdr）、`MYSQL_ENGINE_CONFIG` |
| CK/StarRocks | `CK_DATABASE_URI`、`CK_BINDS`、`CK_ENGINE_CONFIG`、`STARROCKS_ENGINE_CONFIG`、`STARROCKS_CLIENT_PARAMS` |
| Redis | `REDIS`（HOST、PORT、DB、PASSWORD、MAX_CONNECTIONS、超时） |
| RabbitMQ | `Scheduler_RabbitMq`（连接信息、to_scheduler_exchange/routing_key、from_scheduler_queue、repush_exchange/routing_key、repush_queue、prefetch_count）、`Realtime_RabbitMq`（from_xpusher_queue、to_xpusher_exchange/routing_key） |
| MongoDB | `MONGODB_URI`、`MONGODB_CONFIG` |
| ETCD | `ETCD_CONFIG`（all_addrs、host、port、config_key、rule_key、number_key） |
| 加密 | `AES_INFO`（AES key/iv，**值不在本报告中**） |
| 业务 | `PROCESS_COLLECTION`、`TASK_LIMITATION`、`ASYNC_WORKER_THREAD_COUNT`、`PORN_ID`、`POLITICS_ID`、`VIOLENCE_ID`、`GAMBLEDRUG_ID`、`STRATEGY_CONFIG_URL` |
| 消费者 | `RESULT_CONSUMERS`、`REALTIME_CONSUMERS`、`REPUSH_CONSUMERS` |
| 监控 | `PROMETHEUS_HOST`、`PROMETHEUS_PORT` |
| 文件服务 | `ZJH_SOUTH`、`YSH_SOUTH`、`ZJH_NORTH`、`YSH_NORTH`、`ZJH_SZ`、`ZJH_ZZ`、`QA_FILE_SERVER` |
| 环境变量 | `FLASK_ENV`、`QA_SERVER_ID`（雪花机器号，默认 0）、`PROMETHEUS_MULTIPROC_DIR`、`QA_CACHE_VERSION_FILE`、`QA_CACHE_SYNC_INTERVAL` |

## 12. 坑与特殊逻辑

1. **后台任务跑在 Gunicorn master**（`gunicorn_conf.py:when_ready`）：MQ 消费、定时入库、重试都在 master 进程线程里；master 内存/GIL 压力与 HTTP worker 隔离，但 master 异常会影响全部后台能力。worker 的配置缓存靠本地文件版本号同步（`utils/cache_sync.py`），master 靠 ETCD watch。
2. **任务创建是异步的**（`apps/service.py:_async_executor`）：接口返回 0000 时尚未落库、未投 MQ；后续失败只把 qa_task.status 置 2，调用方无感知，也没有补偿重投。
3. **签名不覆盖请求体**（`apps/__init__.py:prepare`）：`MD5(timestamp+secret)`，5 分钟窗口内签名可被复用于任意 body；IP 白名单通配命中后会自动 SADD 精确 IP，白名单只增不减。
4. **雪花 ID 机器号**（`utils/snowFlake.py:get_next_id`）：机器位来自环境变量 `QA_SERVER_ID`，默认 0；多实例未配置时 task_id 可能冲突。另外 Gunicorn 多 worker 同实例也共享同一机器号（各 worker 独立序列），同毫秒存在冲突可能（待确认实际 worker 数）。
5. **flush 游标缺失时跳过历史数据**（`apps/sub_worker.py`）：`{PREFIX}:latest` 不存在（首次上线或被误删）时从 now−2min 开始，更早的分钟桶 list 永远不会入库且无 TTL。
6. **重推 x-delay 递进**（`apps/repush_consumer.py`）：依赖收到消息时 header 仍为原始正值；若延迟插件交付时把 x-delay 改为负值（RabbitMQ delayed-message 插件的已知行为，待确认版本），递进逻辑将永远走“其他值→300000”分支，形成无限重推。
7. **retry_status=1 悬挂**（`apps/retry_worker.py`）：重试投递后若 scheduler 不回结果，记录永远停留在 1，不再被扫描，也不回调客户。
8. **专线首次失败不回调客户**（`apps/result_consumer.py:_handle_qa_fail_retry`）：明细先以 status=3 入库，最多 5 次重试后才回调；vi-bill 侧需要容忍延迟。
9. **准实时 `{task_id}` key 无 TTL**（`apps/task_consumer.py`），会长期占用 Redis。
10. **结果消费异常也 ack**（`safe_ack` 在 finally）：处理异常时只能靠 MongoDB `process_fail` 追溯，无自动重放。
11. **Redis 写失败兜底直写 StarRocks**（`sub_result`）：小批量频繁写，可能造成 StarRocks 版本数压力。
12. **customize 接口客户不存在时 500**（`apps/views.py`：`{}["status"]`）。
13. **schema 拼写错误 key `"companyId:"`**（customize userData schema），实际不会校验 companyId。
14. **`num_operation` 异常被吞**（`apps/vi/views.py`），调用方收不到失败回调。
15. **GZH_LIST（"GZ001","GZ002"）应用回调时 content 置空**，并使用不同流程（`PROCESS_COLLECTION.atom_common`）。
16. **customer 17 与 extid 15/24 的录音 URL 特例**（`apps/service.py:build_url`），属于历史兼容逻辑，按 AGENTS.md 红线不得随意删除。
17. **location 3 在准实时链路使用 `QA_FILE_SERVER`**，与普通链路 ZJH_SZ 不同（`apps/task_consumer.py`）。
18. `/v1/front/create` 中 "700" 开头且长度 32 的 ID 视为 DCC 话单（语音比对场景），ID 解析会去掉前 14 位（`utils/snowFlake.py`）。

## 13. 与其它仓库的关系

| 仓库 | 关系 | 证据 |
| --- | --- | --- |
| vi-billcenter-master（vi-bill） | 上游调用方：`QualityCheckService.checkQuality` POST 配置项 `parameters-config.qc-server-create-url`（生产配置指向 **`/v1/task/customize`**），携带硬编码流程 ID（customizedDetectionId）、按场景映射的 wordPackageId、ragTopK=1、deepSeekPromptId、qwenPromptId、rtType "0"、channelId=客户 app_id；回调地址 `/vibillcenter/task/callback`、`/vibillcenter/subtask/callback`；userData 含主被叫、handleStartTime、companyId、customerId，不带 isStream；录音 URL 为 `/internal/media/...` 格式 | vi-billcenter `QualityCheckService`、`config.prod/application.yml` |
| scheduler-master | 下游 AI 编排：消费 to_scheduler 消息、回写 from_scheduler_queue；qa-server 读其 MongoDB `scheduler.process` 流程定义 | `apps/service.py`、`apps/result_consumer.py` |
| vi-pusher-master（xpusher） | 准实时：qa-server 消费 `from_xpusher_queue` 话单、结果推 `to_xpusher_exchange` | `apps/task_consumer.py`、`apps/result_consumer.py` |
| vi-as-master | 可能监听 ETCD `number_key`（号码报备变更），待确认 | `utils/etcd.py`、`apps/vi/views.py` |
| 策略管控服务（`STRATEGY_CONFIG_URL`） | 中高风险结果推送对象，仓库未知，待确认 | `apps/result_consumer.py` |
| vi-cront-jobs-master | 共享 user_qc.customer_tab（status、is_call）、biz_num_auth；无直接调用 | 两仓库 SQL |

## 14. 待确认问题清单

1. `config.prod/config.yaml`、`config.test/config.yaml` 由谁、以何种方式注入（ConfigMap/挂载/镜像构建）？
2. “海山数据库”是否即 StarRocks 集群（`*_hs` 引擎）？Stream Load 目标库与 `mid_strategy_hs` 是否为同一集群？
3. RabbitMQ delayed-message 插件版本与 x-delay 交付后取值，是否会导致重推无限循环？
4. `nack(requeue=False)` 的队列是否配置了死信交换机？
5. `STRATEGY_CONFIG_URL` 的接收服务是哪一个？请求体约定是否有文档？
6. ETCD `number_key` 的监听方是谁（vi-as？）？
7. 多实例部署时是否为每个实例设置了不同的 `QA_SERVER_ID`？
8. qa_task.status=2（失败）是否有监控与人工补偿流程？
9. retry_status=1 长期悬挂的记录是否有清理/超时机制？
10. `ctrl_action`、`audio_code`、`audio_direction` 各取值含义（qa_strategy_config 字典）？
11. `/v2/task/create` 当前是否有调用方？vi-bill 生产走 `/v1/task/customize`，二者分片路由逻辑是否一致（customize 路径不经 `resolve_segment_route`）？
12. README 中的 `/vi/enterprise/modifyScene`、`/modifyScript` 是否已废弃？

CONFLICT: `qa-server-master/README.md` vs `qa-server-master/apps/vi/views.py`: README 列出 `/vi/enterprise/modifyScene` 与 `/vi/enterprise/modifyScript`，代码只有 `/vi/enterprise/modifySceneAndScript`。

CONFLICT: `qa-server-master/models.py`（qa_task.status 注释“1已完成 0进行中”） vs `qa-server-master/apps/service.py:_mark_task_failed`: 代码写入 status="2"（失败），注释未列出。

CONFLICT: 专线方案（`/v2/task/create` 为专线入口） vs `vi-billcenter-master/config.prod/application.yml`: 生产 vi-bill 实际调用 `/v1/task/customize`（待确认这是否为有意设计）。
