---
id: runbook-qc-result-delay
title: Runbook - 质检结果延迟
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [qc-pipeline, svc-call-qc-service]
---

# Runbook：质检结果延迟

## 现象

告警「质检结果延迟」触发；下游反馈质检结果迟迟未产出。

## 影响

质检结果延迟产出，涉诈风险处置变慢；**不影响呼叫接续**（待核实）。

## 止血（先做）

1. 确认影响范围：看质检总览看板中的任务积压量和时延曲线。
2. 如果是 `CallCompleted` 消费积压：TODO（扩容消费者 / 临时开启抽检的开关名）。
3. 如果是 ASR 失败率上升：TODO（切换备用 ASR / 降级为仅规则质检）。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | MQ 消费积压 | TODO | 积压 < TODO | 看第 2 步 |
| 2 | call-qc-service 实例健康 | TODO | | 重启 / 扩容 |
| 3 | asr-service 延迟与错误率 | TODO | | 见 `03-ai/asr.md` |
| 4 | fraud-agent / 模型服务延迟 | TODO | | 见 `03-ai/model-serving.md` |
| 5 | 录音获取失败 | TODO：日志关键字 | | 检查存储 |

## 历史案例

- TODO：`10-lessons/` 中的相关复盘

## 恢复确认

积压回落、时延恢复到阈值以下并持续 TODO 分钟。
