---
id: mn-number-pool-overview
title: 号码报备、号段与 ICSCF
domain: mid_number
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [mn-overview, mn-700-overview, svc-vi-as, svc-qa-server]
source_of_truth:
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/AuthConfigService.java
  - vi-assets/qa-server-master/apps/vi/views.py
---

# 号码报备、号段与 ICSCF

本目录名沿用“号码池”，内容是专线质检的号码授权数据。没有空闲、已分配、冷却这组状态机。

## 1. 报备号码

`user_qc.biz_num_auth` 是客户业务号码清单。vi-as 在 `AuthConfigService.getCallerNumAuthDo` 里先精确匹配，再前缀匹配。匹配结果给出企业、客户、区号，用来填话单和选控制器。

qa-server 的企业管理接口可以增删这张表：新增时任一号码已存在则整批失败；删除要求号码都属于该客户。完成后回调报备任务结果。执行器里的异常会被吞掉，回调不一定发出。

设计上的唯一约束是号码全局唯一，不是“同一企业内唯一”。前缀是否保留区号、是否支持 `**` 通配，文档和代码不一致，待确认。

## 2. 号段与 ICSCF

| 表 | 作用 |
| --- | --- |
| `user_qc.phone_segment_area_code_info` | 号段到区号，供话单填写主叫归属 |
| `user_qc.chinamobile_icscf_ip` | 实体卡签约时，按 70014 前缀路由码找 ICSCF 地址 |

这两张表由运营登记，不由呼叫动态分配。字段以 `06-data/tables/` 下对应文件为准。

## 3. 刷新

vi-as 把报备和企业信息放进内存，靠 ETCD 版本 key 触发刷新，而不是每通电话回源查询。CONFLICT: 交互图写成每通电话请求对客平台查三张表。以 vi-as 缓存为准。
