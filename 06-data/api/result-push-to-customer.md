---
id: api-result-push-to-customer
title: vi-pusher 经 httpproxy 推送质检结果
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-vi-billcenter-to-qa-server
  - svc-vi-pusher
  - svc-vi-billcenter
  - module-vi-pusher
  - module-vi-billcenter
source_of_truth:
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java
  - vi-assets/vi-pusher-master/src/main/java/com/cmic/utils/AuthorizationUtil.java
---

# vi-pusher 经 httpproxy 推送质检结果

生效链路：qa-server 把子任务结果回调给 vi-billcenter，vi-billcenter 原样投入 MQ，vi-pusher 消费后 POST 到 httpproxy 的 `/proxy`。客户真实地址放在请求头里，由 httpproxy 转发。httpproxy 源码不在本次仓库。本文不记录任何回调 URL。

客户地址来自 `user_qc.customer_tab.result_push_url`（四级客户）。为空则视为无需推送，话单质检状态置 3 并移入历史表。`customer_tab.url`（质检复核回调）未被这条链路使用。

## 1. 出站

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/proxy` | vi-pusher | httpproxy，再转发客户 | 头见下表；body 为质检子任务 JSON，不含 retryTimes | 成功：HTTP 2xx 且 JSON `code=="0000"`。否则失败并延时重试 | `vi-assets/vi-pusher-master/src/main/java/com/cmic/inspectionpusher/CommonInspectionPusher.java:pushInspection` |

节点由 ETCD 服务名（配置项 httpproxy-name）选出，再拼接 `/proxy`。`Content-Type: application/json;charset=UTF-8`。本端连接超时 2 秒，读响应 8 秒，取连接 200 毫秒。没有同步重试。

| Header | 值 |
| --- | --- |
| Authorization | `EOPAUTH appid="<三级企业 app_key>",timestamp="<秒>",signature="<32 位大写十六进制>"`。算法名 MD5，输入为 timestamp 与三级企业密钥按此顺序拼接 |
| X-Forwarded-For | `customer_tab.result_push_url`。httpproxy 据此转发，转发语义待确认 |
| X-Session-Id | 话单 id（消息里的 listId） |
| X-Platform-Id | 三级企业 id |
| X-User-Id | 四级客户 id |
| X-App-Id | 四级客户 app_id |
| X-Timeout | `10000`（交给 httpproxy 的毫秒数，长于本端 8 秒读超时） |

## 2. 报文

body 是 qa-server 子任务回调去掉 `retryTimes` 后的 JSON。vi-pusher 不改字段名。常见字段：

| 字段 | 说明 |
| --- | --- |
| taskId / listId | 质检任务 id / 话单 id |
| status | 子任务状态。以 qa-server 为准 |
| qcResult | `"0"` 异常，`"1"` 正常 |
| riskLevel / errorMessage / data | 风险等级、错误、透传 |
| duration | 时长 |
| transcription / matchedWord / summary | 转写与命中信息。日志和文档都不要写入真实通话文本 |
| userData | callerNumber、calleeNumber、handleStartTime（`yyyy-MM-dd HH:mm:ss`）、companyId、customerId。qcResult 为 `"0"` 且有 riskLevel 时，qa-server 还可能放入 ctrlAction、audioCode、audioDirection、callId |

号码示例：`700xxxx0001`、`138xxxx0001`。

客户须返回 HTTP 2xx，且 JSON 含小写字段 `code`，值为 `"0000"`。

## 3. 重试

固定间隔 60 秒，共推送 4 次（首次加 3 次重试）。第 5 次消费时 retryTimes 已大于 3，状态置 -3，移历史表，计数失败。延时消息若发送失败，该条结果丢失。查不到话单则直接丢弃，不重试。

客户收到的 body 不含 retryTimes。

## 4. 入站（非客户推送）

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/vipusher/task/callback` | 调试 | vi-pusher（端口 9283） | 任意 JSON，只读 taskId 打日志 | 固定 `{"code":"0000","message":"回调处理成功"}` | `vi-assets/vi-pusher-master` 的 TestController |

该接口没有鉴权。话单推送、录音推送的旧消费者已停用，不要再接。

## 5. 待确认

1. httpproxy 是否只认 `X-Forwarded-For` 作为目标 URL，以及它自己的鉴权。
2. `X-Timeout` 10 秒与本端 8 秒读超时哪一个先生效。
3. 客户 `code` 非字符串 `"0000"`（例如数字 0）是否应算成功。当前代码要求字符串相等。
