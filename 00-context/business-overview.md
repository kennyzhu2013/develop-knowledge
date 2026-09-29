---
id: ctx-business-overview
title: 业务总览
domain: context
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-09-29
related: [ctx-glossary, ctx-system-boundary, arch-overall]
---

# 业务总览

> 填写指引：用 1 页讲清楚“这套系统为谁、解决什么问题、主链路是什么”。细节放到各域 overview。

## 1. 中间号

为通话双方提供隐私号码（中间号），实现号码隐藏、通话管控、话单留痕。主要子业务：

| 子业务 | 一句话说明 | 文档 |
| --- | --- | --- |
| 号码绑定 | 真实号码与中间号建立绑定关系（AXB / AXN 等模式，按实际填写） | `01-mid-number/overview.md` |
| 700 号码 | TODO：700 号段业务定位与差异 | `01-mid-number/700/overview.md` |
| 出海 | TODO：面向海外的中间号能力 | `01-mid-number/overseas/overview.md` |
| 呼叫路由 | 来电根据绑定关系路由到真实号码 | `01-mid-number/call-routing/overview.md` |
| 号码池 | 中间号资源的分配、回收、冷却 | `01-mid-number/number-pool/overview.md` |
| 计费 | 话单生成与计费结算 | `01-mid-number/charging/overview.md` |

## 2. 语音质检

对通话录音 / 实时语音进行转写和分析，识别涉诈、投诉、骚扰等风险。

主链路（待核实）：

```text
通话结束 / 实时语音流 → 录音获取 → ASR 转写 → 质检（规则 + 模型 + Agent） → 风险事件 → 处置 / 数据平台
```

详见 `02-call-qc/overview.md`、`02-call-qc/pipeline.md`。

## 3. 两套系统的连接点

| 连接点 | 方向 | 载体 | 文档 |
| --- | --- | --- | --- |
| 通话完成事件 | 中间号 → 质检 | MQ（TODO：Topic 名） | `06-data/events/events.yaml` |
| 录音文件 | 中间号 → 质检 | TODO：对象存储 / NFS | `04-architecture/storage.md` |
| 风险处置 | 质检 → 中间号 | TODO：解绑 / 拦截接口 | `06-data/api/` |

## 4. 关键指标

| 指标 | 目标 | 说明 |
| --- | --- | --- |
| TODO：绑定接口 P99 | TODO | |
| TODO：离线质检端到端时延 | TODO | |
| TODO：实时质检首次风险发现时延 | TODO | |
