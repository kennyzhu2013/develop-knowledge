---
id: data-overview
title: 数据与契约 - 总览
domain: data
kind: dynamic
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - arch-storage
  - data-redis-keys
  - data-etcd-keys
  - api-overview
  - mn-traffic-control-overview
  - mn-traffic-control-codes
  - qc-pipeline
  - svc-vi-as
  - svc-vi-billcenter
  - svc-vi-pusher
  - svc-qa-server
  - svc-scheduler
  - svc-vi-cront-jobs
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/dao/BillDao.java
  - vi-assets/qa-server-master/models.py
  - vi-assets/vi-cront-jobs-master/apps/models.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/pojo/entity/Task.java
---

# 数据总览

本页按库说明职责和读写方，并列出表文件。一表一文件，模板是 `99-agent/templates/table.yaml`。`service` 只写主写入方（配置表在本仓库只读时，写主要使用方）。Redis 见 `06-data/redis/keys.yaml`，ETCD 见 `06-data/etcd/keys.yaml`。消息见 `06-data/mq/topics.yaml` 与 `06-data/events/events.yaml`，接口见 `06-data/api/overview.md`。存储分层见 `04-architecture/storage.md`。

事实以 `vi-assets/` 源码为准。下面的 CONFLICT 行是文档与代码不一致处。

## 库职责

| 存储 | 职责 | 写入 | 读取 |
| --- | --- | --- | --- |
| MySQL `vi_bill` | 在途话单、失败话单、按日历史话单 | vi-as 写 `bill_new` / `bill_fail`；vi-billcenter、vi-pusher 写日表并回写质检状态 | vi-billcenter、vi-pusher；vi-cront-jobs 在 `USE_MYSQL` 时读日表 |
| MySQL `user_qc` | 企业、客户、渠道、号码报备、号段、ICSCF、质检主体与 BGE | qa-server 写 `customer_tab`、`biz_num_auth`；其余配置本仓库只读 | vi-as 缓存鉴权、号段、ICSCF；qa-server、vi-cront-jobs |
| MySQL `vi_strategy` | 话务管控策略、活跃号码 | 策略配置本仓库只读；`active_phone_tab` 由 vi-cront-jobs 写 | vi-cront-jobs |
| qa-server 其它 MySQL | 规则与任务、词包话术、鉴权配置、700 下载配置、失败重试 | qa-server | qa-server |
| StarRocks 海山 | 质检明细 `qa_detail`、分片 `qa_segment` | qa-server Stream Load | qa-server；scheduler 只读 `qa_detail` |
| ClickHouse `mid_bussops` | 话务管控命中明细与按日统计 | vi-cront-jobs | vi-cront-jobs |
| MongoDB | 质检任务、流程、模型注册；失败留痕 | scheduler；qa-server 写失败集合 | scheduler；qa-server 读 `scheduler.process` |
| Redis | 去重、规则缓存、分钟桶、策略下发 | qa-server、scheduler、vi-cront-jobs | 同上；策略 key 的读取方是外部 strategy-service |
| ETCD | 配置版本、服务注册、放音文件名 | qa-server 写版本号；vi-media / vi-merge / offline-merge 写注册 | vi-as、vi-billcenter、vi-pusher、vi-media、qa-server |

vi-media、vi-merge、offline-merge 不直接访问上表中的 MySQL。vi-as 的会话 Redis（`AB-{caller}-{callee}`、`max_call_limit_{customerId}`）代码还在，但 `initRedis` 未调用且无 redis 配置，实际不生效。

## 表文件

### vi_bill

| 文件 | id | 表 | 主使用方 |
| --- | --- | --- | --- |
| `06-data/tables/vi_bill.bill_new.yaml` | `table-vi-bill-bill-new` | bill_new | vi-as |
| `06-data/tables/vi_bill.bill_fail.yaml` | `table-vi-bill-bill-fail` | bill_fail | vi-as |
| `06-data/tables/vi_bill.bill_daily.yaml` | `table-vi-bill-bill-daily` | bill_yyyy_MM_dd | vi-billcenter |

日表是说明性文件，不为每一天建文件。表名是 `bill_` 加 `yyyy_MM_dd`（下划线），由 `SimpleDateFormat("yyyy_MM_dd")` 生成。vi-billcenter 与 vi-pusher 在移出在途话单时 INSERT 44 列再 DELETE `bill_new`。模板表 `bill_template` 只存在于话单 DDL，应用不直接读写，因此没有单独的表文件。

`quality_inspection_status`：0 未发起，1 已发起，2 质检成功，3 推送成功（或无 `result_push_url`），-1 发起失败，-2 质检失败，-3 推送失败。vi-as 插入时用默认 0，不写该列。`finish_state` 的 0–24 与 99 以 vi-as `FinishState` 为准，列在 `bill_new` 文件里。

qa-server 查话单走 `apps/service.py` 的 `get_bill_list`：引擎 `hs_bill`（当天/昨天的 `bill_` 日期表）、`mid_bill_history.bill_history`、`dcc_mid_bill` 与 `dcc_mid_bill_history`。日期格式同样是下划线。这些引擎与 MySQL `vi_bill` 日表是否同一份数据，待确认。vi-cront-jobs 在 `USE_MYSQL` 时用引擎 `vi_bill` 读 `vi_bill.bill_%Y_%m_%d`，否则走 CK 客户端。

### user_qc

| 文件 | id | 表 | 主使用方 |
| --- | --- | --- | --- |
| `06-data/tables/user_qc.biz_num_auth.yaml` | `table-user-qc-biz-num-auth` | biz_num_auth | qa-server |
| `06-data/tables/user_qc.company_tab.yaml` | `table-user-qc-company-tab` | company_tab | qa-server |
| `06-data/tables/user_qc.customer_tab.yaml` | `table-user-qc-customer-tab` | customer_tab | qa-server |
| `06-data/tables/user_qc.channel_tab.yaml` | `table-user-qc-channel-tab` | channel_tab | qa-server |
| `06-data/tables/user_qc.mobile_tab.yaml` | `table-user-qc-mobile-tab` | mobile_tab | qa-server |
| `06-data/tables/user_qc.phone_segment_area_code_info.yaml` | `table-user-qc-phone-segment-area-code-info` | phone_segment_area_code_info | vi-as |
| `06-data/tables/user_qc.chinamobile_icscf_ip.yaml` | `table-user-qc-chinamobile-icscf-ip` | chinamobile_icscf_ip | vi-as |
| `06-data/tables/user_qc.enterprise_scene.yaml` | `table-user-qc-enterprise-scene` | enterprise_scene | qa-server |
| `06-data/tables/user_qc.qa_subject.yaml` | `table-user-qc-qa-subject` | qa_subject | qa-server |
| `06-data/tables/user_qc.qa_enterprise.yaml` | `table-user-qc-qa-enterprise` | qa_enterprise | qa-server |
| `06-data/tables/user_qc.qa_strategy_config.yaml` | `table-user-qc-qa-strategy-config` | qa_strategy_config | qa-server |
| `06-data/tables/user_qc.bge_classifier.yaml` | `table-user-qc-bge-classifier` | bge_classifier | qa-server |
| `06-data/tables/user_qc.bge_rule.yaml` | `table-user-qc-bge-rule` | bge_rule | qa-server |
| `06-data/tables/user_qc.bge_rule_item.yaml` | `table-user-qc-bge-rule-item` | bge_rule_item | qa-server |

号段、ICSCF 的主使用方写成 vi-as，因为呼叫路由全量缓存它们；本仓库没有写入。`company_tab.app_secret` 只作列名，文档不记录密钥值。

### vi_strategy

有 DDL 的 6 张策略表各自一文件。脚本还读另外 6 张无 DDL 的策略表和 `active_phone_tab`，合并在 `06-data/tables/vi_strategy.overview.yaml`（`table-vi-strategy-overview`）：

`strategy_difarea_high_frequency`、`strategy_silent_call_burst`、`strategy_idle_high_frequency`、`strategy_short_highfreq_burst`、`strategy_short_longcall_burst`、`strategy_silent_difarea_longcall_burst`、`active_phone_tab`。

| 文件 | id | 表 |
| --- | --- | --- |
| `06-data/tables/vi_strategy.strategy_high_frequency.yaml` | `table-vi-strategy-strategy-high-frequency` | strategy_high_frequency |
| `06-data/tables/vi_strategy.strategy_remote_range.yaml` | `table-vi-strategy-strategy-remote-range` | strategy_remote_range |
| `06-data/tables/vi_strategy.strategy_offwork_call.yaml` | `table-vi-strategy-strategy-offwork-call` | strategy_offwork_call |
| `06-data/tables/vi_strategy.strategy_business_threshold.yaml` | `table-vi-strategy-strategy-business-threshold` | strategy_business_threshold |
| `06-data/tables/vi_strategy.strategy_business_range.yaml` | `table-vi-strategy-strategy-business-range` | strategy_business_range |
| `06-data/tables/vi_strategy.strategy_long_call.yaml` | `table-vi-strategy-strategy-long-call` | strategy_long_call |
| `06-data/tables/vi_strategy.overview.yaml` | `table-vi-strategy-overview` | 上列 7 张次要表 |

`vi-cront-jobs` 的 `apps/models.py` 还声明 `complaint_detail`、`strategy_detail`、`control_frequency_threshold`、`strategy_info`、`complaint_gzh_detail`。策略脚本没有引用它们，不单独建文件，是否遗留待确认。

表字段 `control_command`：0 拦截，1 放通，2 告警。脚本 `_cmd_map` 为 `{0: "0", 1: "2", 2: "1"}`，写入 ClickHouse 明细前做映射。

### qa-server 的 MySQL

默认会话是 `MYSQL_URI`，仓库里没有 `config.yaml`，库名待确认。规则缓存的 SELECT 走 bind `mid_strategy`。文件名前缀 `mysql.` 表示库名未在代码里写死。

| 文件 | id | 库 | 表 |
| --- | --- | --- | --- |
| `06-data/tables/mysql.qa_rule.yaml` | `table-mysql-qa-rule` | MYSQL_URI | qa_rule |
| `06-data/tables/mysql.qa_task.yaml` | `table-mysql-qa-task` | MYSQL_URI | qa_task |
| `06-data/tables/mysql.dcc_qa_rule.yaml` | `table-mysql-dcc-qa-rule` | MYSQL_URI | dcc_qa_rule |
| `06-data/tables/mysql.dcc_qa_task.yaml` | `table-mysql-dcc-qa-task` | MYSQL_URI | dcc_qa_task |
| `06-data/tables/vi_cdr.qa_fail.yaml` | `table-vi-cdr-qa-fail` | vi_cdr | qa_fail |
| `06-data/tables/mid_strategy.qa_word_package.yaml` | `table-mid-strategy-qa-word-package` | mid_strategy | qa_word_package |
| `06-data/tables/mid_strategy.qa_script_group.yaml` | `table-mid-strategy-qa-script-group` | mid_strategy | qa_script_group |
| `06-data/tables/mid_strategy.qa_script.yaml` | `table-mid-strategy-qa-script` | mid_strategy | qa_script |
| `06-data/tables/mid_strategy.qa_word_group.yaml` | `table-mid-strategy-qa-word-group` | mid_strategy | qa_word_group |
| `06-data/tables/mid_strategy.qa_regex.yaml` | `table-mid-strategy-qa-regex` | mid_strategy | qa_regex |
| `06-data/tables/mid_strategy.qa_prompt.yaml` | `table-mid-strategy-qa-prompt` | mid_strategy | qa_prompt |
| `06-data/tables/mid_auth.config_company.yaml` | `table-mid-auth-config-company` | mid_auth | config_company |
| `06-data/tables/mid_auth.auth_channel.yaml` | `table-mid-auth-auth-channel` | mid_auth | auth_channel |
| `06-data/tables/mid_auth.config_customer.yaml` | `table-mid-auth-config-customer` | mid_auth | config_customer |
| `06-data/tables/dcc_conf.file_down_host.yaml` | `table-dcc-conf-file-down-host` | dcc_conf | file_down_host |
| `06-data/tables/dcc_conf.config_user.yaml` | `table-dcc-conf-config-user` | dcc_conf | config_user |
| `06-data/tables/dcc_conf.config_application.yaml` | `table-dcc-conf-config-application` | dcc_conf | config_application |

`qa_fail.retry_status` 以代码常量为准：0 PENDING，1 RETRYING，2 SUCCESS，3 FAILED（`is_done=1`）。唯一键 `(task_id, data_id)`。`qa_task.status` 注释是 0/1，失败路径会写成 2。

vi-cront-jobs 的 `apps/general.py` 还会读 `mid_auth` 的 `auth_mobile`、`config_province`。它们不在 qa-server 的表清单里，没有单独文件。

### StarRocks 海山

| 文件 | id | 引擎 | 表 |
| --- | --- | --- | --- |
| `06-data/tables/starrocks.qa_detail.yaml` | `table-starrocks-qa-detail` | mid_strategy | qa_detail |
| `06-data/tables/starrocks.qa_segment.yaml` | `table-starrocks-qa-segment` | mid_strategy_segment | qa_segment |

普通明细从 Redis `QASUBRESULT` 刷到 `mid_strategy.qa_detail`，分片从 `VIQASUBRESULT` 刷到 `mid_strategy_segment.qa_segment`，700 明细从 `DCCQASUBRESULT` 刷到引擎 `dcc_strategy` 的同名 `qa_detail`（模型 `DccQaDetail`，额外列写在 `qa_detail` 文件里，不另建文件）。失败兜底引擎是 `mid_strategy_hs`、`dcc_strategy_hs`。scheduler 的 JDBC 地址在缺失的 `application.yml` 里，库名待确认；它只读 `qa_detail` 的 ASR 复用列。

### ClickHouse mid_bussops

`06-data/tables/mid_bussops.call_control.yaml`（`table-mid-bussops-call-control`）合并两张 MergeTree：`call_control_detail` 与 `call_control_statistics`。分区 `toYYYYMM`，排序键 `id`。写入方是 vi-cront-jobs。`strategy_name` 在脚本里用到 1–5、8–15。

## MongoDB

scheduler 的任务模型在 MongoDB，集合名不要当成 MySQL 表。库名在缺失的 `spring.data.mongodb.*` 里，待确认。实体在 `vi-assets/scheduler-master/src/main/java/com/cmic/pojo/entity/`，DAO 在同仓库 `dao/`。集合没有代码内 TTL。

| 集合 | 用途 | 代码 |
| --- | --- | --- |
| task | 主任务。status 0 未开始 / 1 推送中 / 2 全部已推送；finish 0 失败 / 1 成功 / 2 部分成功或超时 | `Task.java` |
| sub_task | 子任务与模型结果。status 0 未开始 / 1 推送失败 / 2 推送成功。回调后去掉 asrText、asrInfo、cqAgentDetail | `SubTask.java` |
| process | 流程元数据。status 0 不可用 / 1 可用。XML 正文在本地文件 | `Process.java` |
| his_process | 流程历史，含当时的 XML 全文 | `HisProcess.java` |
| ai_model | dify / http 模型注册 | `AIModel.java` |
| ai_type | 模型类型 | `AIType.java` |
| model_meta | qwen / deepseek 多版本 | `ModelMeta.java` |
| shard_task | 实时流分片，按 callId 累加转写 | `ShardTask.java` |

qa-server 另用两处 Mongo（`vi-assets/qa-server-master/apps/service.py`、`apps/result_consumer.py`、`apps/repush_consumer.py`）：

| 库.集合 | 方向 | 用途 |
| --- | --- | --- |
| scheduler.process | 读 | 按 processId 取 aiModels，展开质检开关 |
| service.process_fail | 写 | 结果处理或回调异常，转写先加密 |
| service.push_fail | 写 | 延迟重推最终失败 |

scheduler 仓库 `src/main/resources/mysql/` 里的旧版 MySQL DDL（`task`、`smart_walnuts_sub_task`、`process`）当前运行时未使用：代码没有对应的 MySQL DataSource。现行模型是上面的 Mongo 集合。这三张旧表不建表文件。

CONFLICT: `vi-assets/scheduler-master/src/main/resources/mysql/sub_task.ddl` vs `SubTask.java`：DDL 把 status 写成 1 推送成功、2 推送失败，代码写入是 1 失败、2 成功。

CONFLICT: `vi-assets/scheduler-master/src/main/resources/mysql/task.ddl` vs `Task.java`：DDL 的 finish 只有 0/1，代码还有 2（部分成功或超时）。

## Redis 与 ETCD

键模式、TTL 和代码路径在 `06-data/redis/keys.yaml`、`06-data/etcd/keys.yaml`。键名用占位符，不写真实号码。策略 value 是 JSON：`priority`、`isMain`、`condition.customerId`、`action.opType`（0 拦截 / 1 放通 / 2 告警）、`action.appendReason`。`offtime-{customerId}` 只 SET、不 EXPIRE；其余策略 key 仅在 `control_valid_second>0` 时 EXPIRE。

Go 注册前缀 `/{clusterName}/micro-registry/{serviceName}/{instance}`，生产配置的 clusterName 为 `vii`。已知服务名：`com.cmic.vi.merge`、`com.cmic.vi.offline`、`com.cmic.vi.mdownload`。vi-media 的 appName 仍是部署占位，待确认。scheduler 与 vi-cront-jobs 不使用 ETCD。

## 文档与代码差异

CONFLICT: `/tmp/vi/docs/话单DDL.txt` vs `BillNewDo.java` 与两处 `BillHistoryDao`：代码读写 `call_in_area_code`、`call_out_area_code`，话单 DDL 没有这两列。

CONFLICT: `/tmp/vi/docs/二期话单DDL.txt` vs vi-as、vi-billcenter、vi-pusher：DDL 增加 `quality_inspection_segment_result`、`quality_inspection_final_result`、`control_action`，三个服务的实体和日表 INSERT 都不含这三列。

CONFLICT: 话单 DDL 的 `append_reason` 注释 1–6 vs vi-as `BillAppendReasonEnum` 1–25 vs 策略 Redis 的 appendReason 5、13、20–32。脚本写 Redis 时用硬编码，不用表里的 `append_reason` 列。

CONFLICT: 策略 DDL 没有 `is_main`、`app_id`，前 6 张表的脚本 SELECT 了这两列。

CONFLICT: `/tmp/vi/analysis/docs.md` 第 6.3 节记策略 7–13 无表无脚本 vs `vi-cront-jobs`：外省/外市高频、沉默突增、闲时高频、短时高频突增、短时长突增、沉默外地长通话已有脚本和表名，设计 DDL 仍只有前 6 张。

CONFLICT: `/tmp/vi/docs/话务管控策略表DDL.txt` 明细注释把 `control_command` 写成 1 告警、2 挂断 vs 策略表与 Redis 的 0/1/2。运行逻辑文档库名 `mid_bussop` vs 代码 `mid_bussops`。

CONFLICT: `qa_rule` / `dcc_qa_rule` 的 ORM 走默认 `MYSQL_URI`，`load_qa_rule` 从 bind `mid_strategy` 读取。

CONFLICT: 号码表 DDL vs `BizNumAuth`：DDL 有 `comment`、`number_status`，`channel_id` 可空且 `num` 唯一；ORM 未映射前两列，`channel_id` 非空。

CONFLICT: qa-server PUT `{ETCD_CONFIG.number_key}`，watch 列表只有 `config_key` 与 `rule_key`。vi-as 刷新号码看的是 `/config/num/version`。两套 key 是否同步，待确认。

## 待确认

- `MYSQL_URI` 的实际库名，以及它和 bind `mid_strategy` 是否同一个库。
- scheduler 的 Mongo 库名、`auto-index-creation`、各集合是否有运维侧 TTL；`shard_task` 是否建了 `callId` 索引。
- scheduler JDBC 的 StarRocks 库名是否就是 Stream Load 引擎 `mid_strategy`。
- qa-server 话单引擎 `hs_bill` / `mid_bill_history` / `dcc_mid_bill` 与 MySQL `vi_bill` 日表的对应关系。
- 策略 Redis 的读取方是否就是 ETCD 服务名 `com.cmic.vi.strategy`。`offtime-{customerId}` 永不过期是否符合预期。
- qa-server 裸键 `{taskId}`（无 TTL）与 scheduler 裸键 `{dataId}`（默认 7 天）是否会撞键。
- `customer_tab.source` 开户写 3，模型注释只有 1 能开、2 后台。`customer_tab.is_segment` 与 `qa_subject.is_segment` 的取值是否同一套枚举。
- 次要策略表没有 DDL，列类型未知。`active_phone_tab` 的清理策略未知。
- `bill_new` 没有应用内清理，接通后等不到合音的行会留下。
- `/config/*` 的写入方不在本仓库。vi-media 注册名、Java xdiscovery 的注册路径未知。
- 旧版 MySQL DDL 是否还有仓库外系统在用。代码侧当前运行时未使用。
