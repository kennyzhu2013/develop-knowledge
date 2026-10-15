---
id: api-reporting-interface
title: 语音专线质检信息报备接口 v1.0.1
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-qa-server-management
  - svc-qa-server
  - module-qa-server
source_of_truth:
  - vi-assets/qa-server-master/apps/vi/views.py
  - vi-assets/qa-server-master/apps/__init__.py
---

# 语音专线质检信息报备接口 v1.0.1

依据《语音专线质检信息报备接口说明书》v1.0.1（2026-03-25，修订说明为新增 2.5 和 2.6 接口）与设计整理中的报备章节。调用方是语音专线平台，提供方是质检平台。说明书全部 URL 为 `POST url（待定）`，`Content-Type: application/json;charset=UTF-8`。

封面版本号写 V1.0.0，修订记录写到 V1.0.1。2.5 / 2.6 对应哪两个接口，待确认。

## 1. 鉴权

| 项 | 约定 |
| --- | --- |
| 头 | `Authorization: EOPAUTH appid="<appid>",timestamp="<秒>",signature="<32 位大写十六进制>"` |
| appid | 接入方平台唯一 ID |
| timestamp | 1970-01-01 起的秒，与标准时间偏差在过去 5 分钟内 |
| signature | 算法名 MD5。输入为 timestamp 与 appkey 按这个顺序直接拼接，输出 32 位十六进制并转大写。不覆盖请求体 |
| 失败 | HTTP 403 Forbidden |
| 成功 | 返回业务 JSON |

密钥、签名样例不写入本文。

CONFLICT: 报备接口说明书（EOPAUTH，响应只返回 enterpriseId） vs 交互图（请求携带明文密钥，响应返回多级 id）。交互图没有落在本次 9 个仓库源码中，以哪份联调待确认。

## 2. 组织四级字段

一至三级是平台预置字典（只校验，不新建）；四级是本次新建的客户。响应里的客户 Id 是四级 id。

| 层级 | 请求字段 | 含义 |
| --- | --- | --- |
| 一级 | provinceFullname、provinceNickname | 省公司全称、简称 |
| 二级 | cityFullname、cityNickname | 市公司全称、简称 |
| 三级 | districtFullname、districtNickname | 区县公司全称、简称 |
| 四级 | enterpriseFullname、enterpriseNickname；响应 enterpriseId | 客户全称、简称；客户 Id |

`channelId` 在说明书中是质检账户 Id（字符串，示例 `AB001`），不要和库表里整型的二级移动 `channel_id` 混用。

CONFLICT: 报备接口说明书字段表（enterpriseFullname、enterpriseNickname、script、districtFullname、accountManager） vs 同说明书请求示例（customerFullname、customerNickname、scripts）。

## 3. 接口一览

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | 待定（客户创建） | 语音专线平台 | 质检平台 | requestId、四级组织字段、scene、script、accountManager、managerPhone、channelId | 403；0000 / 1001；响应 enterpriseId | 9 仓库无同字段实现，见第 5 节 |
| POST | 待定（场景修改） | 语音专线平台 | 质检平台 | requestId、enterpriseId、scene（修改后企业下全部场景，全量覆盖） | 403；0000；其他值表示失败 | 同上 |
| POST | 待定（话术修改） | 语音专线平台 | 质检平台 | requestId、enterpriseId、script、operateType（新增/删除，示例为 `"2"`，编码未定义） | 同上 | 同上 |
| POST | 待定（客户注销） | 语音专线平台 | 质检平台 | requestId、enterpriseId。注销后该客户下质检账户不能再做质检 | 同上 | 同上 |
| POST | 待定（号码新增/删除） | 说明书写“客户通知语音专线平台”，与其余接口方向相反 | 待确认 | requestId、enterpriseId、numberList、operateType（示例 `"1"`，编码未定义） | 0000；可返回 taskId（长度不超过 32） | 同上 |
| POST | 待定（号码任务结果回调） | 语音专线平台通知客户 | 客户 | taskId、result、reason | 0000；其他值表示失败 | 同上 |

scene 取值：1 催收、2 金融、3 教育、4 房产、5 政务、6 电商、7 企业办公、8 快递、9 医疗。script 在创建时可选，在话术修改时必填。

号码示例只使用 `700xxxx0001`。managerPhone 示例使用 `138xxxx0001`。

## 4. 错误码

说明书没有统一错误码表（“返回值定义”是占位文字）。

| 取值 | 含义 | 适用 |
| --- | --- | --- |
| HTTP 403 | 鉴权未通过 | 全部 |
| 0000 | 成功 | 全部业务响应 |
| 1001 | 失败 | 只在客户创建响应里写明 |
| 其他值 | 失败 | 场景修改、话术修改、注销、号码新增/删除、号码任务回调 |

客户创建的 message 可选；其余接口的 message 必填，长度不超过 128。客户创建成功时 enterpriseId 必填。

result 原文是“全部成功 0-全部失败”，没有写出“全部成功”的编码，也没有部分成功和逐号码失败明细。示例把 result 写成 `"0"`。待确认。

## 5. 本次 9 个仓库里有没有实现

在 vi-as、vi-media、vi-merge、offline-merge、vi-billcenter、vi-pusher、qa-server、scheduler、vi-cront-jobs 中，没有 `provinceFullname`、`accountManager`、`managerPhone` 的服务端实现，也没有说明书里“待定”路径的路由。人工号码报备、对客门户在设计文档中归 ops-platform，该仓库不在本次源码中。

qa-server 有一组路径和字段都不同的相近接口，不能当成说明书契约。详见 `06-data/api/qa-server-management-api.md`。

| 说明书接口 | 9 仓库 | 说明 |
| --- | --- | --- |
| 客户创建 | 未按说明书实现 | qa-server `POST /vi/enterprise/create` 用省市区 id、recordChannel，没有项目经理字段 |
| 场景修改 | 无独立路径 | 与话术合并为 `POST /vi/enterprise/modifySceneAndScript` |
| 话术修改 | 无独立路径 | 同上；代码字段是 scriptOperateType |
| 客户注销 | 语义接近、路径不同 | `POST /vi/enterprise/cancel`，`customer_tab.status=3` |
| 号码新增/删除 | 语义接近、路径不同 | `POST /vi/number/addOrDel`，operateType `"1"` 增、`"2"` 删 |
| 号码任务结果回调 | 无入站实现 | qa-server 向客户 numberUrl 出站 POST `{taskId,result,reason}` |

CONFLICT: 报备接口说明书号码任务 result（成功编码缺失，示例为 `"0"`） vs `vi-assets/qa-server-master/apps/vi/views.py:push_num_result`: 回调 result `"1"` 为成功、`"0"` 为失败。是否同一接口待确认。

## 6. 待确认

1. 六个 URL 的最终路径，以及实现落在 ops-platform 还是 qa-server 的 `/vi`。
2. V1.0.1 新增的 2.5、2.6 各是哪一节。
3. 话术修改、号码增删的 operateType 编码；号码任务 result 的全部成功 / 部分成功编码。
4. 号码接口的调用方向（客户调平台，还是平台调客户）；单次 numberList 上限；能否报备前缀号段（说明书没有 match_type）。
5. requestId 是否做幂等。签名不覆盖 body，5 分钟内可重放，是否接受。
6. 接口报备成功后是否自动刷新 vi-as 内存中的号码表。
