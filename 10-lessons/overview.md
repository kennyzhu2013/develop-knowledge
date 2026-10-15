---
id: lessons-overview
title: 经验与故障索引
domain: lessons
kind: stable
audience: [all]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lesson-known-issues-vi-as
  - lesson-known-issues-vi-media
  - lesson-known-issues-vi-merge
  - lesson-known-issues-offline-merge
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-vi-pusher
  - lesson-known-issues-qa-server
  - lesson-known-issues-scheduler
  - lesson-known-issues-vi-cront-jobs
  - lesson-doc-code-conflicts
  - lesson-security-risks
  - lesson-open-questions
  - arch-security
source_of_truth:
  - vi-assets/vi-as-master/
  - vi-assets/vi-media-master/
  - vi-assets/vi-merge-master/
  - vi-assets/offline-merge-master/
  - vi-assets/vi-billcenter-master/
  - vi-assets/vi-pusher-master/
  - vi-assets/qa-server-master/
  - vi-assets/scheduler-master/
  - vi-assets/vi-cront-jobs-master/
---

# 经验与故障索引

本目录记录源码里已经能对上的坑、兜底和未决问题。核对日期 2026-10-15。标「待确认」的结论还没有用线上配置证实。

删重试、兜底、兼容分支之前，先读对应服务的 known-issues，并查 `09-decisions/`。看起来像笔误的状态码、Topic 名，以 `10-lessons/pitfalls/doc-code-conflicts.md` 里的代码侧为准完成本次判断，同时保留 CONFLICT 行，不要自行改文档去“对齐”未经确认的业务意图。

## 目录

| 路径 | id | 内容 | 读者 |
| --- | --- | --- | --- |
| `10-lessons/pitfalls/known-issues-vi-as.md` | lesson-known-issues-vi-as | vi-as 接入、话单、媒体重试、号码规则 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-vi-media.md` | lesson-known-issues-vi-media | 会话、推流、合音回退、端口与清理 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-vi-merge.md` | lesson-known-issues-vi-merge | 实时分片合音、上传乱序、结束标识 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-offline-merge.md` | lesson-known-issues-offline-merge | 离线合音配对、上传失败仍发消息 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-vi-billcenter.md` | lesson-known-issues-vi-billcenter | 话单消费无重投、合音等待、质检发起 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-vi-pusher.md` | lesson-known-issues-vi-pusher | 对客推送超时、重试、移表 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-qa-server.md` | lesson-known-issues-qa-server | 任务创建异步、签名、重试悬挂 | 研发、运维 |
| `10-lessons/pitfalls/known-issues-scheduler.md` | lesson-known-issues-scheduler | 编排、回调、超时兜底、不要删的去重 | 研发、运维、架构 |
| `10-lessons/pitfalls/known-issues-vi-cront-jobs.md` | lesson-known-issues-vi-cront-jobs | 话务管控脚本口径、Redis 续期、崩溃点 | 研发、运维 |
| `10-lessons/pitfalls/doc-code-conflicts.md` | lesson-doc-code-conflicts | 设计文档或 DDL 与源码不一致 | 架构、研发 |
| `10-lessons/pitfalls/security-risks.md` | lesson-security-risks | 表达式执行、未鉴权管理接口、凭据与未脱敏号码 | 架构、研发、运维 |
| `10-lessons/pitfalls/open-questions.md` | lesson-open-questions | 各仓库分析报告待确认项（已去重） | 所有人 |
| `10-lessons/incidents/` | — | 线上故障复盘。模板 `99-agent/templates/incident.md`。目前没有复盘正文 | 所有人 |

## 先看这些

| 主题 | 去哪一篇 |
| --- | --- |
| 合音消息对不上、质检没发起 | `10-lessons/pitfalls/doc-code-conflicts.md` 与 `10-lessons/pitfalls/known-issues-vi-billcenter.md` |
| 改管理接口、配置、流程 XML | `10-lessons/pitfalls/security-risks.md` |
| 删看起来多余的分支之前 | 上表里该服务的 known-issues |
| 还没人证实的线上行为 | `10-lessons/pitfalls/open-questions.md` |
