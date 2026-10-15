---
id: api-vi-billcenter-to-qa-server
title: vi-billcenter 调用 qa-server 创建质检任务
domain: data
kind: dynamic
audience: [developer, qa]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - api-overview
  - api-qa-server-management
  - api-result-push-to-customer
  - svc-vi-billcenter
  - svc-qa-server
  - module-vi-billcenter
  - module-qa-server
source_of_truth:
  - vi-assets/vi-billcenter-master/config.prod/application.yml
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java
  - vi-assets/vi-billcenter-master/src/main/java/com/cmic/utils/AuthorizationUtil.java
  - vi-assets/qa-server-master/apps/views.py
  - vi-assets/qa-server-master/apps/__init__.py
  - vi-assets/qa-server-master/apps/service.py
---

# vi-billcenter 调用 qa-server 创建质检任务

合音结果到达后，vi-billcenter 向配置项 `parameters-config.qc-server-create-url` 发 POST JSON。生产模板该 URL 的路径是 `/v1/task/customize`。请求经过接入层（分析报告称 SLB），源码不写死主机名。

## 1. 路径冲突

CONFLICT: 专线质检-媒体实现详细设计接口写 `POST /v2/task/create` vs `vi-assets/vi-billcenter-master/config.prod/application.yml`（`qc-server-create-url` 指向 `/v1/task/customize`）以及 `vi-assets/qa-server-master/apps/views.py:create_customized_task`: 生产走定制流程接口。`/v2/task/create` 在 qa-server 中存在，但是另一套按规则建任务并做分片路由的入口，本次 vi-billcenter 源码不调用它。

## 2. 生产调用

| 方法 | 路径 | 调用方 | 被调方 | 关键字段 | 错误码 | 代码位置 |
| --- | --- | --- | --- | --- | --- | --- |
| POST | `/v1/task/customize` | vi-billcenter | qa-server | requestId、urlList、customizedDetectionId、wordPackageId、taskCallBackUrl、subTaskCallBackUrl、userData、channelId、ragTopK、deepSeekPromptId、qwenPromptId、rtType | HTTP 403；业务 0000 / 1001 / 1002 / 1003 / 9999 | `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java:checkQuality`；`vi-assets/qa-server-master/apps/views.py:create_customized_task` |

| 项 | 约定 |
| --- | --- |
| 鉴权 | `Authorization: EOPAUTH appid="<三级企业 app_key>",timestamp="<秒>",signature="<32 位大写十六进制>"`。算法名 MD5，输入为 timestamp 与三级企业密钥按此顺序拼接。不覆盖 body |
| 时间窗 | qa-server 要求 timestamp 不超过 10 位，且 `0 ≤ now - ts ≤ 300` 秒 |
| 白名单 | 来源 IP 须在 Redis `QASERVER:WHITEIP`；两个回调 URL 须在 `QASERVER:WHITEURL` |
| 超时 | 连接 2 秒，读响应 8 秒，取连接 200 毫秒 |
| 成功 | HTTP 2xx 且 body `code=="0000"`。接口先返回 taskId，落库和投递 scheduler 在线程池里异步完成 |
| 回调 | taskCallBackUrl、subTaskCallBackUrl 指向本实例 `/vibillcenter/task/callback`、`/vibillcenter/subtask/callback`，不经 SLB |

channelId 取四级客户 `customer_tab.app_id`，不是二级移动 channel_id。customizedDetectionId 在 `QualityCheckService.createParams` 中写死为流程 id `pjeu1p52049743508159467520`。ragTopK 为 1，deepSeekPromptId 为 `"38"`，qwenPromptId 为 `"39"`，rtType 为 `"0"`（0 表示离线 / 准实时，待确认）。

urlList 每次一条：`{id: 话单 id, url: 录音地址}`。主被叫不在 File 里，而在 userData。

| userData 字段 | 取值 |
| --- | --- |
| callerNumber / calleeNumber | 有值才放入。示例 `700xxxx0001`、`138xxxx0001` |
| handleStartTime | `yyyy-MM-dd HH:mm:ss` |
| companyId / customerId | 三级企业 id、四级客户 id |

qa-server customize 的 userData schema 把 companyId 写成了带冒号的键名，因此不会按整数校验 companyId。待确认是否影响联调。

## 3. startTime 单位冲突

CONFLICT: 专线质检-媒体实现详细设计接口将 startTime 写成毫秒级 Unix 时间戳（同文档样例却是 10 位秒；送质检的 File.handleStartTime 又是 `%Y-%m-%d %H:%M:%S`） vs `vi-assets/vi-billcenter-master/src/main/java/com/cmic/service/QualityCheckService.java:createRecordUrl`: 录音 URL 路径中的时间戳来自 `getEpochSecond()`，单位是秒。同文件 `createUserData` 把 userData.handleStartTime 格式化为 `yyyy-MM-dd HH:mm:ss`。`vi-assets/qa-server-master/apps/views.py` 的 customize schema 只声明 handleStartTime 为 string，不校验单位。

录音 URL 形态（host 来自下载节点配置，本文不写具体地址）：

```text
{host}/internal/media/{appId}/{msId}/{billId}/{秒级时间戳}/{pfsId}/{billId}_{yyyyMMddHHmmss}.{mp3|wav}
```

`record_type==1` 用 wav，其余用 mp3。路径按话单字段拼接，不用合音消息里的 fileName。

## 4. 错误码（qa-server）

| 码 | 含义 |
| --- | --- |
| HTTP 403 | 鉴权、IP 或回调 URL 白名单失败 |
| 0000 | 受理成功（异步失败不一定再通知调用方） |
| 1001 | requestId 重复，或 Redis 去重异常之外的重复请求 |
| 1002 | 参数缺失或回调 URL 格式错误 |
| 1003 | 参数值错误，含 channelId 与签名 appid 不匹配 |
| 9999 | 其他失败；未捕获异常也包装为 9999 |

去重键 `AIQC_REQUEST_{requestId}`，120 秒。Redis 异常时 fail-closed 返回 9999。

## 5. 待确认

1. `/v2/task/create` 是否仍有别的调用方。customize 不走分片路由。
2. rtType `"0"` 的正式含义。
3. 录音 host 是否就是 vi-mdown 内网下载地址。
4. 异步落库失败后，调用方只看到 0000，补偿是否只靠 `/vibillcenter/task/recreate`。
