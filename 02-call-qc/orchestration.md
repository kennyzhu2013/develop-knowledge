---
id: qc-orchestration
title: 语音质检 - 任务编排
domain: call_qc
kind: stable
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [qc-pipeline, qc-realtime, ai-model-catalog, svc-qa-server, svc-scheduler, svc-vi-billcenter]
source_of_truth:
  - vi-assets/qa-server-master/apps/service.py
  - vi-assets/qa-server-master/apps/strategy.py
  - vi-assets/scheduler-master/src/main/java/com/cmic/
---

# 质检任务编排

话单中心 vi-billcenter 在合音结果到达后，经 SLB 调 qa-server 建任务。qa-server 把任务投到 RabbitMQ，scheduler 按流程 XML 调模型适配器，结果再回调 qa-server，由 qa-server 回调 vi-billcenter，最后 vi-pusher 推给客户。

## 1. 谁发起任务

CONFLICT: 方案文档写媒体/vi-merge 发起质检，并写接口 `/v2/task/create`。代码里 vi-billcenter 的 `qc-server-create-url` 请求体带 `customizedDetectionId`，对应 qa-server `POST /v1/task/customize`（`apps/views.py`）。`/v2/task/create` 存在，但是按规则建任务并做分片路由，不是话单中心这条主路径。

`customizedDetectionId`、`deepSeekPromptId`、`qwenPromptId`、`ragTopK`、`rtType` 在 vi-billcenter 里写死，所有客户共用同一个流程。客户是否开通、是否过期、分钟额度不在 vi-billcenter 校验。

## 2. qa-server 建任务（`/v1/task/customize`）

鉴权（`apps/__init__.py:prepare`）：来源 IP 在 Redis `QASERVER:WHITEIP`；`Authorization: EOPAUTH`；签名为 `MD5(timestamp + secret)` 大写，**不包含 body**，5 分钟内可重放；回调 URL 必须在 `QASERVER:WHITEURL`。

| 步骤 | 行为 | 失败 |
| --- | --- | --- |
| 校验 | jsonschema、账户、`requestId` 去重（Redis NX，120s） | 1001 / 1002 / 1003 / 9999 |
| 读流程 | MongoDB `scheduler.process`，按 `aiModels` 拆开关 | 流程不存在则错误码 |
| 立即返回 | 线程池异步落库和发 MQ，接口先回 `0000` 与 taskId | 异步失败只把任务标失败，**不再通知调用方** |
| 发调度 | `to_scheduler_exchange`，publisher confirm，失败重试 3 次 | — |

`/v2/task/create` 另走 `resolve_segment_route`（`apps/strategy.py`），按通话时长 T1/T2 和抽检比例得到 realtime / near_realtime / offline / no_ai。`no_ai` 直接写“正常”结果并回调，不进 scheduler。

## 3. scheduler 执行

主入口消费 `scheduler.task.queue`（`TaskConsumer`）。校验失败的回调发送代码被注释，消息 ack 后静默丢弃。子任务写入 MongoDB `sub_task`，主任务写入 `task`。Spring 事件是同步的，`initial` 里的模型投递都在消费线程里串行完成。

模型请求/响应都走 RabbitMQ，scheduler 自己不发 HTTP。`jobId = <subTaskId>_<dataId>`，响应必须原样带回。路由失败的消息没有 ReturnCallback，会静默丢失。模型清单见 `03-ai/model-catalog.md`。

子任务结束由流程 XML 的 `process op=success|fail|stop` 决定 `finish=1|0|2`，回调 type=1 发到 `scheduler.callback.queue`。全部子任务回调后，发 `TaskMqBo` 到 `scheduler.task.repeat.queue`。**只有 `masterServer=true` 的实例消费该队列**；没有 master 时主任务永不回调。全局部署必须恰好一个 master。

超时兜底按小时扫描：priority > 2 的任务创建超过 24 小时兜底，其余要满 48 小时。流程表上的 `process.timeout`（1–60 分钟）没有代码使用。

实时分片（`isStream=true`）用 MongoDB `shard_task` 把同一 callId 的分片串行起来，ASR 文本累加。高风险后写 Redis `interruptShard=1`，后续分片直接结束。抢发送权的 CAS 已注释，存在重复发送竞态。

## 4. 专线流程 XML

| 文件 | 位置 | 链路 |
| --- | --- | --- |
| `zhuanxian_qc.xml` | `sources/prod/` | 离线专线：ASR（文本超过 45 字才继续）→ 脱敏 → 本地关键词与高阈值关键词反诈并行；命中则 Qwen → RAG（score≥0.9 记高风险 1），否则融通（firstLabel==4 记低风险 3）→ DeepSeek（result==1 记中风险 2） |
| `zhuanxian_qc_2.xml` | `sources/dev/` | 二期分片：低阈值关键词反诈 → Qwen → RAG → 融通与高阈值关键词反诈汇合 → DeepSeek，支持 `interruptShard` |

三个环境目录没有同名文件。生产实际加载哪个目录取决于部署的 `xml.path` 与 `env`，待确认。

条件表达式是 SpEL。`CalculateCondition` 把 `${attr}` 换成 `#attr` 后交给 Spring 求值。管理接口可以上传 XML。这是远程代码执行面，见 `10-lessons/pitfalls/security-risks.md`。

## 5. 结果回到客户

qa-server 消费 `from_scheduler_queue`（处理异常也会 ack）：

1. type=1 合并模型字段；有 userData 且异常时，按 `qa_strategy_config` 往 userData 追加管控字段。
2. 明细先 LPUSH 到 Redis 分钟桶（`QASUBRESULT` / `VIQASUBRESULT` / `DCCQASUBRESULT`），每分钟 stream load 进海山（StarRocks）。
3. 子任务 HTTP 回调 vi-billcenter `/vibillcenter/subtask/callback`。成功标准是 HTTP 200 且 code=`0000`，否则进延迟重推（约 3 分钟、5 分钟、10 分钟）。
4. 主任务回调 type=2，写完成率。

专线失败重试：首次失败不回调客户，写入 `vi_cdr.qa_fail`；每 10 分钟重投 scheduler，最多 5 次。

vi-billcenter 收到回调后再发 RocketMQ，vi-pusher 经 httpproxy `/proxy` 推到客户回调地址。

## 6. 状态（scheduler 子任务）

| 字段 | 取值 |
| --- | --- |
| 推送 status | 1 推送失败，2 已推送 |
| finish | 1 成功，0 失败，2 部分成功 |
| callback | 1 已回调 |
| 模型 status | 0 未执行，1 失败或超时，2 成功 |

回调完成后会从 MongoDB 删掉 `asrText` / `asrInfo`。转写全文不再留在 scheduler。
