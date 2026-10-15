---
id: arch-ha
title: 高可用与容灾
domain: architecture
kind: stable
audience: [architect, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-overall, ops-emergency, runbook-scheduler-master-failover]
source_of_truth:
  - vi-assets/vi-media-master/internal/app.go
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/TaskDerepeatConsumer.java
  - vi-assets/vi-as-master/src/main/java/com/cmic/service/MediaService.java
---

# 高可用与容灾

| 服务 | 部署上的事实 | 降级 |
| --- | --- | --- |
| vi-as | ETCD 注册；OPTIONS 在 pause 时不回应，供上游摘除；会话数超过阈值回 500 | 策略服务不可用且开关允许时跳过策略继续呼叫；媒体 create 失败则呼叫继续但不录音 |
| vi-media | ETCD 注册，CPU 超阈值切 Overloaded | 无槽位 503。停机阻塞到会话排空，最长约 4 小时 |
| vi-merge | ETCD 注册，超 `maxSession` 回 503 | 停机不等待合音结束，内存中的分片丢失。上传失败换节点 1 次 |
| offline-merge | 消费线程可配（生产 20）。上传失败可落 `/data/offlocal` | 下载失败不重试，坏腿当静音。只有出站 MQ 失败才重投整条 |
| vi-billcenter | RocketMQ 消费始终 ack | 合音 topic 配错时质检不会开始，呼叫本身不受影响 |
| qa-server | Gunicorn：HTTP 在 worker，MQ 与定时任务在 master | 建任务接口先返回成功。master 挂了则结果停在队列里 |
| scheduler | 多实例消费任务与模型响应 | **全局限一个** `masterServer=true`，否则主任务回调停住。去重 LRU 在进程内，重启即丢 |
| vi-pusher | 消费质检结果 | 失败走延时 topic，经 httpproxy |
| vi-cront-jobs | crontab 每分钟（非工作时间文档写每 10 分钟） | 脚本重叠无 flock。策略最多晚一个周期生效 |

RTO / RPO 没有在代码或设计文档里给出，待确认。应急步骤见 `07-operation/emergency/overview.md`。
