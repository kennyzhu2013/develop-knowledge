---
id: api-qa-server-management
title: qa-server 入站 HTTP
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-vi-billcenter-to-qa-server
  - api-reporting-interface
  - api-scheduler-admin
  - svc-qa-server
  - module-qa-server
source_of_truth:
  - vi-assets/qa-server-master/apps/__init__.py
  - vi-assets/qa-server-master/apps/views.py
  - vi-assets/qa-server-master/apps/vi/views.py
  - vi-assets/qa-server-master/apps/strategy.py
  - vi-assets/qa-server-master/apps/service.py
  - vi-assets/qa-server-master/apps/result_consumer.py
---

# qa-server 入站 HTTP

qa-server 提供质检任务、词包话术、行业企业与号码管理。HTTP 端口以部署配置为准（服务索引写 7220）。除 `/manage*` 外都走 EOPAUTH。生产上 vi-billcenter 调用的是 `/v1/task/customize`，契约见 `06-data/api/vi-billcenter-to-qa-server.md`。本文覆盖任务、分片路由、企业管理，以及这些接口带出的回调。

## 1. 鉴权

| 路径 | 规则 | 失败 |
| --- | --- | --- |
| OPTIONS | 放行 | |
| `/manage*` | 只允许内网地址，不验签 | HTTP 403 |
| 其他 | 来源 IP 在 Redis `QASERVER:WHITEIP`（支持两段或三段通配，命中后把精确 IP 写回集合） | HTTP 403 |
| 其他 | `Authorization: EOPAUTH appid=...,timestamp=...,signature=...`。算法名 MD5，输入为 timestamp 与企业密钥拼接，输出大写十六进制。timestamp 为秒，偏差 0 到 300 秒。签名不覆盖 body | HTTP 403 |
| 含回调 URL 的创建请求 | taskCallBackUrl、subTaskCallBackUrl 必须在 Redis `QASERVER:WHITEURL` | HTTP 403 |

代码位置：`vi-assets/qa-server-master/apps/__init__.py:prepare`。

## 2. 任务与配置

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/v1/task/customize` | vi-billcenter | qa-server | customizedDetectionId、urlList 或 textList、userData、channelId、两个回调 URL | 0000 + taskId | `vi-assets/qa-server-master/apps/views.py:create_customized_task` |
| POST | `/v2/task/create` | 专线（设计入口；生产话单中心未用） | qa-server | urlList、userData（customerId、isSegment、duration 必填）、ruleId 可选 | 0000 + taskId；随后可能走分片路由 | 同上 `create_task_by_rule_id_v2` |
| POST | `/v1/task/create` | 内控 | qa-server | ruleId、audioList 或 dccList | 0000 / 1002 | 同上 |
| POST | `/v1/front/create` | 普通 / 700 质检 | qa-server | channelId、callIdList 与 urlList 或 dccList（两类互斥）、两个回调 URL | 0000 / 1002 | 同上 |
| POST | `/v1/text/check` | 文本质检 | qa-server | 文本与 channelId | 0000 / 9999 | 同上 |
| POST | `/v1/keyword/create` | 管理端 | qa-server | 词包名、words。不超过 200 词 | code、wordPackageId | 同上 |
| POST | `/v1/keyword/update` | 管理端 | qa-server | wordPackageId、op（1 加词 / 2 删词 / 3 删包） | code | 同上 |
| POST | `/v1/script/create` | 管理端 | qa-server | 话术组、话术列表 | code、scriptGroupId | 同上 |
| POST | `/v1/script/update` | 管理端 | qa-server | op（1 新增 / 0 删除） | code | 同上 |
| POST | `/manage/info/reload` | 内网 | qa-server | 无 | 0000。重载企业缓存 | 同上 `reload_company_info` |

统一业务码：0000 成功；1001 重复或 requestId 问题；1002 参数空；1003 参数值错误；9999 其他。未知路由与未捕获异常也包装成 9999。创建接口返回 0000 时任务可能尚未落库。

`/v1/front/create` 把以 `700` 开头且长度为 32 的 id 当作 700 话单 id，解析时去掉前 14 位。这不是 15 位的 700 号码。

## 3. 分片路由

只在 `/v2/task/create` 且 qa_type 不是 1 时进入 `resolve_segment_route`。`/v1/task/customize` 不走这里。阈值来自客户表 T1、T2 和抽检比例 p、p1、p2。

| 条件 | 结果 |
| --- | --- |
| isStream 为真（从 userData 包装时，缺省视为真） | realtime |
| is_segment 为 1 且时长 ≤ T1 | 仅当时长落在 0.5·T1 到 T1 之间才按 p1、p2 抽 near_realtime 或 offline，否则 no_ai |
| is_segment 为 1 且 T1 < 时长 ≤ T2 | near_realtime |
| is_segment 为 1 且时长 > T2 | realtime |
| is_segment 不为 1 且时长 ≤ T1 | 中间区间按 p 抽 offline，否则 no_ai |
| is_segment 不为 1 且时长 > T1 | offline |

动作：offline / near_realtime 按应用优先级建任务；realtime 用流式优先级；no_ai 直接写“正常”结果并回调；未知路由 9999。代码：`vi-assets/qa-server-master/apps/strategy.py:resolve_segment_route`。

## 4. 企业管理与号码

这组接口和报备说明书不是同一份契约，字段对比见 `06-data/api/reporting-interface-v1.0.1.md`。

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/vi/enterprise/create` | 专线 / 运营 | qa-server | provinceId、cityId、districtId、enterpriseFullname、scene（1–9）、script、recordChannel（0–3）、channelId；可选 numberList（少于 10000）、numberUrl | 0000 与 enterpriseId；带号码时另给 taskId | `vi-assets/qa-server-master/apps/vi/views.py:create_enterprise` |
| POST | `/vi/enterprise/modifySceneAndScript` | 同上 | qa-server | enterpriseId、scene、script、scriptOperateType | 0000 / 1001 | 同上 |
| POST | `/vi/enterprise/cancel` | 同上 | qa-server | enterpriseId。status 置 3 | 0000 / 1001 | 同上 `cancel_enterprise` |
| POST | `/vi/number/addOrDel` | 同上 | qa-server | enterpriseId、operateType（`"1"` 增 / `"2"` 删）、numberList | 0000 与 taskId；结果异步回调 | 同上 `add_or_del_numbers` |

市公司（level 2）映射到 channel，区县（level 3）映射到 company，新建的是四级 customer。channelId 按质检主体 app_id 校验。

CONFLICT: `vi-assets/qa-server-master/README.md` vs `vi-assets/qa-server-master/apps/vi/views.py`: README 写 `/vi/enterprise/modifyScene` 与 `/vi/enterprise/modifyScript`，代码只有 `/vi/enterprise/modifySceneAndScript`。

## 5. 回调

创建请求里的 URL 由调用方传入，必须先加白。qa-server 出站 POST，超时 5 秒。成功判定为 HTTP 200 且 `code=="0000"`，否则进入延时重推。

| 方向 | 时机 | 关键字段 | 代码位置 |
| --- | --- | --- | --- |
| qa-server → subTaskCallBackUrl | 子任务结果（专线首次失败可能先不回调，最多重试 5 次） | taskId、listId、status、qcResult、riskLevel、userData、duration、transcription | `vi-assets/qa-server-master/apps/result_consumer.py` |
| qa-server → taskCallBackUrl | 主任务结束 | taskId、finishRate、error、data | 同上 `main_result` |
| qa-server → 客户 numberUrl | 号码增删完成 | taskId、result（`"1"` 成功 / `"0"` 失败）、reason。超时 30 秒 | `vi-assets/qa-server-master/apps/vi/views.py:push_num_result` |

vi-billcenter 把前两个 URL 指到本机 `/vibillcenter/subtask/callback` 与 `/vibillcenter/task/callback`。号码回调的 result 编码与报备说明书不一致，见报备接口文档中的 CONFLICT。执行器里未捕获的异常会吞掉号码回调，待确认。

## 6. 待确认

1. `/v2/task/create` 的现网调用方。
2. README 中的 modifyScene、modifyScript 是否已废弃。
3. 号码回调失败（非 200）没有重试，是否可接受。
4. customize 在客户不存在时可能返回 500 / 9999。
