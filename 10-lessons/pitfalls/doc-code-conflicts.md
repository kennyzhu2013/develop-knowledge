---
id: lesson-doc-code-conflicts
title: 文档与代码冲突
domain: lessons
kind: stable
audience: [architect, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - context-design-docs-digest
  - lesson-known-issues-vi-as
  - lesson-known-issues-vi-media
  - lesson-known-issues-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-qa-server
  - lesson-known-issues-scheduler
  - lesson-known-issues-vi-cront-jobs
  - lesson-open-questions
source_of_truth:
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java
  - vi-assets/vi-media-master/internal/dto/session_create.go
  - vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java
  - vi-assets/vi-billcenter-master/config.prod/application.yml
  - vi-assets/scheduler-master/src/main/resources/mysql/sub_task.ddl
---

# 文档与代码冲突

核对日期 2026-10-15。完成当前判断时以代码为准，同时保留下面的 CONFLICT，不要自行改文档去圆场。纯文档对文档、且没有源码落点的矛盾（报备字段名、计费示例、号显文案等）不写进本篇，避免编造代码路径。设计文档在 `vi-assets/` 下的 docx。

## 必须先知道的五条

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx` 与生产者配置 `vi-assets/vi-merge-master/config.prod/app.yaml`（`TID_VI_MERGE_MSG_BILL_NORMAL`）、`vi-assets/offline-merge-master/config.prod/app.yaml`（`TID_VI_MERGE_BILL_RECORD_NORMAL` / `TID_DCC_MERGE_MSG_BILL_NORMAL`） vs `vi-assets/vi-billcenter-master/src/main/java/com/cmic/rocketmq/RecordReceiver.java`: 文档与两个生产者的合音 topic 都不是 vi-billcenter 订阅的 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL`。生产实际打通的是哪一个名字，待确认。

CONFLICT: `vi-assets/vi-as-master/src/main/java/com/cmic/recording/MediaManager.java`（create 参数无 direction） vs `vi-assets/vi-media-master/internal/dto/session_create.go`: vi-media 的 create 把 direction 标成必填，vi-as 不传。按两边当前源码应返回 400。线上 DTO 是否不同，待确认。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（专线入口写 `/v2/task/create`） vs `vi-assets/vi-billcenter-master/config.prod/application.yml`: 生产 `qc-server-create-url` 指向 `/v1/task/customize`。

CONFLICT: `vi-assets/qa-server-master/apps/views.py`（`/v2/task/create` 走 `create_task_by_rule`，内部会做分片路由） vs 同文件 `/v1/task/customize`（走 `create_task_by_process`，不经过 `resolve_segment_route`）: 生产若只走 customize，文档里的分片路由不会执行。是否有意，待确认。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（startTime 说明为毫秒，样例又有 10 位秒和 `yyyy-MM-dd HH:mm:ss`） vs `vi-assets/vi-media-master/internal/proxy/session.go` 与 `vi-assets/vi-media-master/internal/pkg/broker/broker.go`: create 按本地时区解析日期时间字符串，合音消息里的 startTime 是 unix 秒。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（startTime 为 ISO8601 或毫秒） vs `vi-assets/vi-merge-master/internal/util/times.go`: 只解析 `yyyy-MM-dd HH:mm:ss`。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（startTime 为毫秒） vs `vi-assets/offline-merge-master/internal/processor/parser_processer.go`: 10 位按秒、13 位按毫秒，两种都接受。

CONFLICT: `vi-assets/scheduler-master/src/main/resources/mysql/sub_task.ddl`（status：1 推送成功，2 推送失败） vs `vi-assets/scheduler-master/src/main/java/com/cmic/pojo/entity/SubTask.java` 与 `vi-assets/scheduler-master/src/main/java/com/cmic/service/task/ProcessJobService.java`: 实体注释和写入相反，发送成功写 2，异常写 1。该 DDL 不是当前 Mongo 运行时模型。

## 合音与媒体契约

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（合音消息含 pcmFormat、pcmSample） vs `vi-assets/vi-media-master/internal/pkg/broker/broker.go`: 消息结构体没有这两个字段。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（`/call/media/inspect` 的示例请求体） vs `vi-assets/vi-media-master/internal/dto/media_inspect.go`: 示例实际是 create 的字段，代码体是 sessionId、config、frames、direction、seqId。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（segments 默认 60） vs `vi-assets/vi-merge-master/internal/models/bmessage.go`: 小于 1 时默认 30。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（fmt 为 S16LE/ALAW/ULAW） vs `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`: 只认小写 s16le/pcma/pcmu。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（sampleRate 仅 8000/16000） vs `vi-assets/vi-merge-master/internal/models/bmessage.go`: 只校验 8000 的倍数，0 也能通过。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（fileName 不含扩展名，duration 为当前分片时长，fileNode 带协议头） vs `vi-assets/vi-merge-master/internal/processor/ac2_processor.go`: 文件名带扩展名，duration 为累计时长，fileNode 为 host:port。

CONFLICT: `vi-assets/专线质检-媒体实现详细设计接口.docx`（pcmFormat 形如 pcm_s16le） vs `vi-assets/offline-merge-master/internal/audioformat/detector.go`: 按大写 S16LE/F32LE 比较，和媒体侧小写、带 pcm_ 前缀对不上。

CONFLICT: `vi-assets/vi-media-master/internal/proxy/session.go` 注释（IN_CALL 超过 3 小时） vs 同文件常量 14400 秒: 实际强制结束是 4 小时。

## 质检任务与话单

CONFLICT: `vi-assets/专线质检方案+需求.docx`（由合音或媒体模块向 qa-server 发起质检） vs `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java`: 发起方是 vi-billcenter，经配置的 HTTP 地址调用 qa-server。

CONFLICT: `vi-assets/二期话单DDL.docx`（`bill_new` 增加分段结果、最终结果、管控动作三列） vs `vi-assets/vi-billcenter-master/src/main/java/com/cmic/dao/BillDao.java`: 本仓库源码不读不写这三列，历史表插入也不会带上它们。

CONFLICT: `vi-assets/qa-server-master/README.md`（列出修改场景、修改话术两个路径） vs `vi-assets/qa-server-master/apps/vi/views.py`: 代码只有合并后的 modifySceneAndScript。

CONFLICT: `vi-assets/qa-server-master/models.py`（质检任务 status 注释为 1 已完成、0 进行中） vs `vi-assets/qa-server-master/apps/service.py`: 失败路径写入 status 2，注释没有这一档。

CONFLICT: `vi-assets/scheduler-master/src/main/resources/mysql/task.ddl`（finish 只有 0 失败 / 1 成功，且有回调 URL 字段） vs `vi-assets/scheduler-master/src/main/java/com/cmic/pojo/entity/Task.java`: 注释含 2 部分成功；现行回调走 MQ，不使用 DDL 里的回调 URL。该表不是当前运行时存储。

## 话务管控与配置加载

CONFLICT: `vi-assets/专线质检各模块交互逻辑图.docx`（AS 每通呼叫向运营平台要号码和企业） vs `vi-assets/vi-as-master/src/main/java/com/cmic/service/AuthConfigService.java`: 号码和企业配置在进程内全量加载，由 ETCD 版本键触发重载，不是每通呼叫现查平台。

CONFLICT: `vi-assets/话务管控策略表DDL.docx`（策略表有 append_reason 列） vs `vi-assets/vi-cront-jobs-master/viStrategyOffwork.py`: 脚本读取该列但不写入 Redis，原因码用代码里的常量。

CONFLICT: `vi-assets/vi-cront-jobs-master/README.md`（示例把 CRONT_ENV 设为 test，路径却在生产目录） vs `vi-assets/vi-cront-jobs-master/config.py`: 缺省环境才是 production。照抄示例会加载测试配置。

CONFLICT: `vi-assets/vi-cront-jobs-master/viStrategyLongCall.py` 注释（true 表示 MySQL 测试，false 表示 StarRocks 正式） vs 同文件 `USE_MYSQL = True`: 代码处于注释所称的测试数据源。
