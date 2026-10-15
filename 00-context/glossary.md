---
id: ctx-glossary
title: 术语表
domain: context
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [ctx-business-overview]
---

# 术语表

代码、文档、Agent 回复使用「标准名」。别名只用于检索。

| 标准名 | 英文 / 代码标识 | 别名 | 定义 |
| --- | --- | --- | --- |
| 专线质检 | vi | 语音专线质检 | 把专线或 IMS 固话呼叫接入 700 平台并做录音质检的系统 |
| 700 号码 | 700 | | 700 平台上的号码。普通 700 号码总长 15 位，走原大网 |
| 7001X 前缀 | 7001X | 70011、70014 | 业务方加在被叫前的前缀。总长不是 15 位时进入专线质检。X 的取值集合待确认 |
| 业务号码 | `biz_number` / bizNum | | 用来查报备表的号码。被叫以 700 开头时代码取主叫，否则取被叫 |
| 号码报备 | `biz_num_auth` | 号码表 | 允许进入专线质检的号码清单，不是 AXB 绑定关系 |
| 四级企业 | `mobile_id` / `channel_id` / `company_id` / `customer_id` | | 省、市、接入企业、终端客户 |
| 质检账户 | `app_id` | channelId（报备接口） | 能开侧主体。不要和表字段 `channel_id` 混用 |
| 话单 | `bill_new` | CDR | 一次呼叫在 `vi_bill.bill_new` 的记录 |
| 合音 | merge | 混音 | 把主叫、被叫录音合成双声道或单声道文件 |
| 准实时质检 | near realtime | 一期、通话后质检 | 挂机并合音完成后发起 |
| 实时分片质检 | `isStream` | 二期 | 通话中按片上传并发起质检，失败可回退离线 |
| 质检任务 | task / sub_task | | qa-server 的一次请求，scheduler 再拆成子任务 |
| 流程 | `processId` | XML | scheduler 的编排文件，专线离线为 `zhuanxian_qc.xml` |
| 话务管控 | strategy | | 接通前的拦截、放音、告警。由 vi-cront-jobs 写 Redis，strategy-service 在呼叫时返回 opType |
| ASR | `asr` | 语音转写 | 由 scheduler 经 RabbitMQ 调用的外部适配器，不是本仓库里的 asr-service |
| 海山 | StarRocks | | qa-server 质检明细的分析库 |
| vi-bill | vi-billcenter | 话单中心 | 设计图上的名字。本库服务名是 vi-billcenter |
| xdtmf | vi-as | | 需求文档里专线 AS 的旧名 |

本次资产里没有 AXB / AXN 绑定实现。不要把号码报备写成绑定关系。
