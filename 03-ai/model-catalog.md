---
id: ai-model-catalog
title: AI 模型目录（scheduler 适配器）
domain: ai
kind: dynamic
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-overview, ai-asr, ai-llm, ai-rag, qc-orchestration, svc-scheduler]
source_of_truth:
  - vi-assets/scheduler-master/src/main/java/com/cmic/service/ai/
  - vi-assets/scheduler-master/sources/prod/zhuanxian_qc.xml
  - vi-assets/scheduler-master/sources/dev/zhuanxian_qc_2.xml
---

# AI 模型目录

scheduler 不直连模型 HTTP。每个模型是一个 RabbitMQ 请求/响应适配器，由流程 XML 的 `application` + `op=sendToMQ` 触发。响应码 `0000` 为成功。`jobId` 必须原样返回，格式 `<subTaskId>_<dataId>`。没有按模型的超时，只有主任务 24h/48h 兜底。

队列名以代码常量与 `06-data/mq/topics.yaml` 为准。下表请求路径写 exchange / routingKey，响应只写队列。

## 1. 专线质检实际用到的模型

`zhuanxian_qc.xml`（prod，离线）与 `zhuanxian_qc_2.xml`（dev，分片）会调用：

| 模型 | 枚举名 | 请求 | 响应队列 | 成功时写入 |
| --- | --- | --- | --- | --- |
| ASR | `asr` | `scheduler.asr.exchange` / `topic.asr.key` | `scheduler.asr.response.queue` | `asrStatus, asrText, audioDuration, asrInfo` |
| 优先 ASR | `asr`（`asrPrior==1`） | `scheduler.asr.exchange` / `scheduler.task.fast.asr` | `scheduler.asr.response.fast.queue` | 同上 |
| 脱敏 | `dsz` | `scheduler.desensitization.exchange` / `topic.desensitization.key` | `scheduler.desensitization.response.queue` | `desensitizationStatus`，并**覆盖** `asrText` |
| 关键词反诈 | `keywordAnalysis` / `keywordAnalysisLT` | `scheduler.keyword.exchange` / `topic.keyword.key` | `scheduler.keyword.response.queue` | `keywordAnalysisStatus/Result` 或 LT 对应字段 |
| 本地关键词 | `keyword` | 不走 MQ，`KeywordService.match` | — | 同步 `keyword_resp` |
| 融通 | `rt` | `scheduler.rongtong.queue` / `topic.rongtong.key` | `scheduler.rongtong.response.queue` | `rtStatus, rtFirstLabel` 等 |
| 通义千问 | `qwen` | `scheduler.qwen.exchange` / `topic.qwen.key` | `scheduler.qwen.response.queue` | `qwenStatus, qwenResult`（参数来自 Mongo `model_meta`） |
| RAG | `rag` | `scheduler.rag.exchange` / `topic.rag.key` | `scheduler.rag.response.queue` | `ragStatus, ragResults[{score, metadata}]` |
| DeepSeek | `deepSeek` | `scheduler.deepseek.exchange` / `topic.deepseek.key` | `scheduler.deepseek.response.queue` | `deepSeekStatus, deepSeekResult, deepSeekReason` |

本地匹配还有 `word_group`、`word_regex`、`keyword_rs`，同样不走 MQ。

风险等级由 XML 写回调属性，不由单一模型决定：RAG score≥0.9 → riskLevel 1；融通 firstLabel==4 → 3；DeepSeek result==1 → 2。qa-server 侧把融通 svcType `"0"` 且 firstLabel `"4"` 视为涉诈。

## 2. 其余适配器

这些能力在 scheduler 代码里存在，专线 prod XML 未引用。是否在其它流程启用，以 MongoDB `process` 与部署的 XML 目录为准。

| 模型 | 请求 exchange / routingKey | 响应队列 | 响应事件 |
| --- | --- | --- | --- |
| 九天 ASR `jt_asr` | `scheduler.jiutian-asr.exchange` / `topic.jiutian-asr.key` | `scheduler.jiutian-asr.response.queue` | `asr_resp` |
| 场景 `scenario` | `scheduler.scenario.exchange` / `topic.scenario.key` | `scheduler.scenario.response.queue` | `scenario_resp` |
| 催收 `collection` | `scheduler.scenario.exchange` / `topic.scenario.cuishou.key` | `scheduler.scenario.cuishou.response.queue` | `cs_resp` |
| 声音鉴伪 `vfd` | `scheduler.vfd.exchange` / `topic.vfd.key` | `scheduler.vfd.response.queue` | `vfd_resp` |
| 声纹 `vpr` | `scheduler.vpr.exchange` / `topic.vpr.key` | `scheduler.vpr.response.queue` | `vpr_resp` |
| 声纹 1v1/1vN `vp` | `scheduler.vp.exchange` / `topic.vp.key` | `scheduler.vp.response.queue` | `vp_resp` |
| 身份 `identity` | `scheduler.identity.exchange` / `topic.identity.key` | `scheduler.identity.response.queue` | `identity_resp` |
| 情绪 `emotion` | `scheduler.emotion.exchange` / `topic.emotion.key` | `scheduler.emotion.response.queue` | `emotion_resp` |
| AIGS `aigs` | `scheduler.aigs.exchange` / `topic.aigs.key` | `scheduler.aigs.response.queue` | `aigs_resp` |
| 融通涉诈复核 | `scheduler.rt-fraud-review.exchange` / `topic.rt-fraud-review.key` | `scheduler.rt-fraud-review.response.queue` | `rt_fraud_review_resp` |
| 营销外呼 `marketCall` | `scheduler.market-call.exchange` / `topic.market-call.key` | `scheduler.market-call.response.queue` | `market_call_resp` |
| 成交 `deal` | `scheduler.deal.exchange` / `topic.deal.key` | `scheduler.deal.response.queue` | `deal_resp` |
| BGE `bge` | `scheduler.iterative_anti_model.exchange` / `scheduler.task.iterative_anti_model` | `scheduler.iterative_anti_model.response.queue` | `bge_resp` |
| 重庆反诈 `cqAnti` | `scheduler.chongqing-anti.exchange` / `scheduler.task.chongqing-anti` | `scheduler.chongqing-anti.response.queue` | `cq_anti_resp` |
| 重庆 agent `cqAgent` | `scheduler.cqagent.exchange` / `scheduler.task.cqagent` | `scheduler.cqagent.response.queue` | `cq_agent_resp` |
| DIFY `dify` | `scheduler.dify.exchange` / `topic.dify.key` | `scheduler.dify.response.queue` | `dify_resp` |
| 通用 HTTP `dyHttp` | `scheduler.dyHttp.exchange` / `topic.dyHttp.key` | `scheduler.dyHttp.response.queue` | `<model>_resp` |

## 3. 已失效或声明被注释

`script`、`huaweiQC`、`text_similarity` 的 exchange 声明和响应监听被注释。消息若仍被 XML 投出，收不到响应，子任务只能等主任务超时。`BA`、`TFIDF` 的发送入口不可达。groovy 规则引擎没有 Java 加载点，现行编排是 XML + SpEL。

## 4. 配置面

- Qwen / DeepSeek 的 url 与鉴权存在 MongoDB `model_meta`，没有管理接口。
- DIFY 与通用 HTTP 模型存在 `ai_model`，由 `/admin/aiModel/http/*` 维护。查询接口会返回 `authentication` 字段。
- 这些管理接口在代码中无鉴权。
