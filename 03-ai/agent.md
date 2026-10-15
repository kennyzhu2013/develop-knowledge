---
id: ai-agent
title: 质检编排（原 Agent 位）
domain: ai
kind: stable
audience: [architect, developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ai-llm, ai-rag, fraud-overview, ai-model-catalog, qc-orchestration, svc-scheduler]
source_of_truth:
  - vi-assets/scheduler-master/sources/
  - vi-assets/scheduler-master/src/main/java/com/cmic/engine/
---

# 质检编排

早期方案里的 groovy 规则引擎没有被 Java 加载。现行“Agent”就是 scheduler 的流程 XML：按事件顺序调用模型，用 SpEL 分支，用 `process` 动作结束子任务。

## 1. 专线怎么跑

```text
initial → ASR → 脱敏 → 关键词与反诈
       → 条件命中 → Qwen → RAG → 融通 → DeepSeek → 写 riskLevel → 结束
```

实时分片在 dev 的 `zhuanxian_qc_2.xml` 里增加 `shard` 动作和 Redis 条件 `interruptShard`。

## 2. 和知识库反诈模型的关系

`02-call-qc/fraud/` 的 FP / RB / RULE 用来让人和 Agent 对齐“想检测什么”。运行时只认 XML、词包和 `qa_strategy_config`。新增一种诈骗手法时，两处都要改，见 Skill `add-fraud-pattern`。

## 3. 不要随便开的能力

管理接口可以上传 XML 并广播到所有实例。表达式在进程内求值。这不是一个带工具白名单的对话 Agent。
