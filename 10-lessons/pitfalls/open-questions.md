---
id: lesson-open-questions
title: 待确认问题
domain: lessons
kind: stable
audience: [architect, developer, ops, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-known-issues-vi-as
  - lesson-known-issues-vi-media
  - lesson-known-issues-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-vi-pusher
  - lesson-known-issues-qa-server
  - lesson-known-issues-scheduler
  - lesson-known-issues-vi-cront-jobs
source_of_truth:
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java
  - vi-assets/vi-media-master/internal/dto/session_create.go
  - vi-assets/vi-billcenter-master/config.prod/application.yml
---

# 待确认问题

来源是各仓库分析报告第 14 节，2026-10-15 按主题去重。同一问题保留全部来源。没有新的线上证据之前，保持待确认。

## 高优先

| # | 问题 | 来源 |
| --- | --- | --- |
| 1 | 合音 topic 生产实际用哪一个：vi-merge 配置 `TID_VI_MERGE_MSG_BILL_NORMAL`，设计文档和 offline-merge 配置 `TID_VI_MERGE_BILL_RECORD_NORMAL`，另有 `TID_DCC_MERGE_MSG_BILL_NORMAL`，vi-billcenter 只订阅 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`。谁消费实时分片消息？ | vi-merge §14.1，offline-merge §14.2，vi-billcenter §14.1，vi-media §14.16 |
| 2 | vi-as 当前线上 create 是否传 direction？不传的话 vi-media 如何没有返回 400？ | vi-media §14.1 |
| 3 | 生产创建质检走 `/v1/task/customize`，文档写 `/v2/task/create`。customize 不做分片路由。这是不是有意设计？代理超时是多少？ | qa-server §14.11，vi-billcenter §14.2 |
| 4 | scheduler 是否保证有且只有一个 `masterServer=true`？没有 master 时主任务不回调，多个 master 时去重失效。故障如何切换？ | scheduler §14.3 |
| 5 | 接通后一直没有合音，或合音失败的 `bill_new` 由谁清理？recover 日志有没有人工补偿？ | vi-billcenter §14.4，vi-as §14.2 |
| 6 | 管理端口（scheduler `/admin`、vi-as `/control` 与 `/ops`、vi-merge 与 offline-merge 的 ops、vi-billcenter 的 test/recreate）在生产是否只有内网，有没有网关鉴权？ | vi-as §14.16，vi-billcenter §14.10，offline-merge §14.10，scheduler §12 与 §14.1 |
| 7 | startTime 线上契约是毫秒、unix 秒，还是 `yyyy-MM-dd HH:mm:ss`？三处代码三种解析。 | vi-merge §14.2，vi-media §14.3 |

## 录音与合音

| # | 问题 | 来源 |
| --- | --- | --- |
| 1 | 主流程媒体会话为空或 B2B 话单 `ms_id` 为 0 时，下游如何定位录音？ | vi-as §14.3 |
| 2 | `file_duration`、左右文件长度、`is_bill_ok`、`is_record_ok` 由谁回写？ | vi-as §14.14 |
| 3 | vi-media 创建失败时呼叫继续接续且不录音，是否为预期？ | vi-as §14.4 |
| 4 | 谁调用 `/call/media/inspect`？本次 vi-as 源码里没有这个调用。 | vi-media §14.2 |
| 5 | pickup 用 UTC 解析 callTime 是否已知？CALLING 时间戳和 4 小时超时是否已受影响？ | vi-media §14.3 |
| 6 | vi-codec 的 TERMINATION 是否应该触发离线合音回退？ | vi-media §14.4，vi-merge §14.8 |
| 7 | 写超时 30ms 和静默丢帧是否为有意取舍？要不要丢帧指标？ | vi-media §14.5 |
| 8 | 实时推流成功后不发合音 MQ，完整文件由谁归档？ | vi-media §14.6，vi-merge §14.3 |
| 9 | MQ 里视频文件名、volume、early 文件恒为空，offline-merge 是否依赖？视频字段是否已废弃？ | vi-media §14.7，offline-merge §14.11 |
| 10 | `maxVideoCapacity` 是否本意是 MB？ | vi-media §14.8 |
| 11 | TTS 该用 outbound 集群名还是本服务集群？ | vi-media §14.9 |
| 12 | vi-codec 回调 `/call/media/inspect/notice` 时 `X-Sequence-Id` 填什么？ | vi-media §14.10 |
| 13 | `/call/media/stream` 和 inbound 拉流是否还有启用计划？ | vi-media §14.11 |
| 14 | AMR 按最大帧长切块，vi-codec 能否处理跨块的帧？ | vi-media §14.12 |
| 15 | `diskLifeSpan` 与按天删 7 天前目录，哪个先生效？ | vi-media §14.13 |
| 16 | vi-as 认为仅 G.729 不支持，vi-media 支持 g729，要不要统一？ | vi-media §14.14 |
| 17 | cdn 预热失败会不会阻断 vi-media 启动？ | vi-media §14.15 |
| 18 | 最后一片为空时不发结束标识，下游是否会一直等？ | vi-merge §14.1 的连带现象，见 known-issues |
| 19 | 整通录音上传后 vi-merge 不发 MQ，是否完全由 offline-merge 的整通消息替代？两条链路会不会对同一会话都跑、在存储里重名？ | vi-merge §14.3、§14.4，offline-merge §14.8 |
| 20 | vi-merge 路径第一级是 userId，vi-media/offline-merge 第一级是 appId，两份整通会不会落到不同目录或互相覆盖？ | vi-merge §14.4 |
| 21 | vi-mdown 对 ZoneC/ZoneM 的保存时长和默认 zone？`file_down_host` 对应哪个入口，qa-server 能否直接访问？ | vi-merge §14.5，vi-billcenter §14.9，offline-merge §14.8 |
| 22 | 心跳关闭后，静音期不发帧会不会被 60 秒读超时拆掉？上游帧长在 16k 时是否为 640 字节？ | vi-merge §14.6、§14.9 |
| 23 | 本地 `/data` 与 `/data/offlocal` 的兜底录音由谁补传、多久清理？临时目录有没有外部清理？ | vi-merge §14.7，offline-merge §14.3、§14.9 |
| 24 | `source=1` 不产出合音，线上会不会出现？ | offline-merge §14.1 |
| 25 | 合音 MQ 最大重投次数、死信，以及下游是否按会话去重？ | offline-merge §14.4 |
| 26 | 生产是否还有 dccMerge？`callId` 为空、JSON 名 `bindIdm` 对下游有没有影响？ | offline-merge §14.5 |
| 27 | vi-media 实际传入的 pcmFormat 是什么，会不会触发大小写不匹配？ | offline-merge §14.6，vi-merge §14.2 |
| 28 | 16k 输入降到 8k 是否有意（ASR 要不要 16k）？ | offline-merge §14.7 |

## 质检、推送与编排

| # | 问题 | 来源 |
| --- | --- | --- |
| 1 | 写死的流程 ID、两个 prompt ID、`rtType=0` 是否应按客户配置？含义是什么？ | vi-billcenter §14.3 |
| 2 | `bill_new.id` 是否一定是纯数字？不是的话延时移表会静默跳过。 | vi-billcenter §14.5 |
| 3 | 二期分段结果、最终结果、管控动作由谁写入？推送和历史表要不要带上？ | vi-billcenter §14.6，vi-pusher §14.9 |
| 4 | 客户质检能力、有效期、分钟额度由谁校验？ | vi-billcenter §14.8，vi-as §14.8 |
| 5 | 质检回调直连实例地址。多实例或容器里这个地址是否稳定可达？ | vi-billcenter §14.11 |
| 6 | dev/test 的 `application.yml` 放在哪，占位符由什么渲染？ | vi-billcenter §14.7，scheduler §14.1 |
| 7 | httpProxy 的 `/proxy` 是否按转发头把请求送到客户？超时头是否生效？代理会不会自己重试？ | vi-pusher §14.1 |
| 8 | 各实例 `pusher.inspectionpusher` 的取值？有没有只开重试、不开主消费的实例？ | vi-pusher §14.2 |
| 9 | 客户幂等键是 listId 还是 taskId？有没有对外字段文档？空回调地址记成推送成功是不是有意？ | vi-pusher §14.3、§14.4 |
| 10 | 重试耗尽（状态 -3）要不要补推或告警？目前只有指标。 | vi-pusher §14.5 |
| 11 | `customer_tab.url` 的用途？代码没有使用。 | vi-pusher §14.6，vi-as §14.8 |
| 12 | 遗留的话单/录音推送是否还要恢复？ | vi-pusher §14.7，vi-as §14.11 |
| 13 | 配置和构建脚本里的明文凭据是否已轮换、能否移出仓库？ | vi-pusher §14.8 |
| 14 | qa-server 配置文件由谁注入？ | qa-server §14.1 |
| 15 | “海山数据库”是不是 StarRocks？`qa_detail` 的写入方是不是 qa-server？ | qa-server §14.2，scheduler §14.7 |
| 16 | 延迟插件交付后 x-delay 会不会变成负数，导致无限重推？nack 的队列有没有死信？ | qa-server §14.3、§14.4，scheduler §14.4 |
| 17 | `STRATEGY_CONFIG_URL` 指向哪个服务？请求体有没有文档？ | qa-server §14.5，vi-cront-jobs §14.4，vi-as §14.5 |
| 18 | ETCD 号码版本键的监听方是不是 vi-as？`user_qc` 的写入方是谁？ | qa-server §14.6，vi-as §14.12 |
| 19 | 每个 qa-server 实例是否配置了不同的机器号？ | qa-server §14.7 |
| 20 | 任务状态 2（失败）和重试状态一直为 1 的记录，有没有监控、补偿或超时？ | qa-server §14.8、§14.9 |
| 21 | `ctrl_action`、`audio_code`、`audio_direction` 的字典含义？ | qa-server §14.10 |
| 22 | README 里的 modifyScene、modifyScript 是否已废弃？ | qa-server §14.12 |
| 23 | `xml.path` 在多实例上是不是共享存储？ | scheduler §14.2 |
| 24 | 任务队列由谁声明，有没有优先级和死信？ | scheduler §14.4 |
| 25 | script、huawei、文本相似度队列是否还在？依赖它们的流程是否已下线？ | scheduler §14.5 |
| 26 | qa-server 消费回调依赖哪些字段？`needSubTaskCallback` 是否由 qa-server 自己过滤？ | scheduler §14.6 |
| 27 | 流程 timeout（1–60 分钟）原计划在哪里生效？现在只有 24/48 小时兜底，是否符合实时 SLA？ | scheduler §14.8 |
| 28 | 实时分片版专线流程是否已上线？prod 目录只有离线版。XML 里中风险结果值和重复条件是不是笔误？ | scheduler §14.9、§14.13 |
| 29 | qwen/deepseek 的 `model_meta` 如何维护？没有管理接口。 | scheduler §14.10 |
| 30 | Mongo 各集合有没有 TTL？分片任务有没有 callId 索引？ | scheduler §14.11 |
| 31 | scheduler 的 start/stop 由谁托管？stop 能否找到进程？ | scheduler §14.12 |
| 32 | MySQL 的 task、sub_task、process 是否还有系统在用？ | scheduler §14.14 |

## 话务管控

| # | 问题 | 来源 |
| --- | --- | --- |
| 1 | 生产 crontab 的环境变量实际是什么？南北配置目录有没有部署？ | vi-cront-jobs §14.1 |
| 2 | 邮件模块是否在线上单独存在？活跃号码和日统计是否在跑？ | vi-cront-jobs §14.2 |
| 3 | 长通话走 MySQL 是否有意？ | vi-cront-jobs §14.3 |
| 4 | 策略服务如何用 Redis 做实时计数？阈值的统计周期从哪来？ | vi-cront-jobs §14.4，vi-as §14.5 |
| 5 | 号码型键不区分企业，是否符合“一号一企业”？ | vi-cront-jobs §14.5 |
| 6 | “策略今天更新过就全天续期”是否为预期？ | vi-cront-jobs §14.6 |
| 7 | effect_object 各取值的含义？ | vi-cront-jobs §14.7 |
| 8 | 日统计与另一套按明细的统计，哪一个是正式口径？ | vi-cront-jobs §14.8 |
| 9 | `mid_bussops` 是 ClickHouse 还是 StarRocks？ | vi-cront-jobs §14.9 |
| 10 | 策略表 `append_reason` 列是否应该替代硬编码原因码？ | vi-cront-jobs §14.10 |
| 11 | 短窗口次数对比历史日均的口径是否正确？ | vi-cront-jobs §14.11 |
| 12 | 未引用的 time、sftp、aes、models 模块能否删除？ | vi-cront-jobs §14.12 |

## vi-as 其余项

| # | 问题 | 来源 |
| --- | --- | --- |
| 1 | `BaseRocketMQService` 来自依赖还是缺失提交？生产者的 group 和重试是什么？ | vi-as §14.1 |
| 2 | 策略超时是放行还是拦截？ | vi-as §14.5 |
| 3 | 被叫为 700 时业务号码取主叫，业务含义是什么？ | vi-as §14.6 |
| 4 | 700 被叫截掉前 5 位与 xproxy 的改写约定是什么？ | vi-as §14.7 |
| 5 | 前缀 TreeMap 是否已经造成错配？有没有等长前缀？ | vi-as §14.9 |
| 6 | 最大通话时长、视频插播、视频彩铃是计划由策略下发，还是已废弃？ | vi-as §14.10 |
| 7 | 若干 ETCD 键、负载均衡地址和强制配置开关的用途？ | vi-as §14.13 |
| 8 | 生产 startMode 实际值？有没有长期以跳过中间件的模式运行的节点？ | vi-as §14.15 |
