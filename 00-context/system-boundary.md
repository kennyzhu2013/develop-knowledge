---
id: ctx-system-boundary
title: 系统边界与外部依赖
domain: context
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [arch-overall]
---

# 系统边界与外部依赖

## 1. 本知识库覆盖的系统

以 `knowledge.yaml` 的 `services` 列表为准；每个服务详情见 `05-code/services/`。

## 2. 外部系统（不在本知识库维护，只记录接口）

| 外部系统 | 我们如何使用 | 接口/协议 | 负责团队 | 文档 |
| --- | --- | --- | --- | --- |
| TODO：运营商交换 / 软交换 | 呼叫接续 | SIP（待核实） | TODO | |
| TODO：客户业务平台 | 调用绑定接口 | HTTP | TODO | `06-data/api/` |
| TODO：数据平台 | 消费质检结果 | MQ | TODO | `06-data/mq/topics.yaml` |
| TODO：反诈处置平台 | 接收风险事件 | TODO | TODO | |

## 3. 边界规则

- 外部系统的行为以对方接口文档为准；本库只记录“我们依赖的子集”和已知坑。
- 外部接口变更需要在 `10-lessons/` 或 ADR 中留痕。
