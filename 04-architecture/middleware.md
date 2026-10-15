---
id: arch-middleware
title: 中间件
domain: architecture
kind: stable
audience: [architect, developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related: [arch-overall, data-mq-topics, data-events]
source_of_truth:
  - 06-data/mq/topics.yaml
  - vi-assets/scheduler-master/src/main/java/com/cmic/config/rabbitmq/MQBeanConfig.java
---

# 中间件

| 中间件 | 用途 | 使用方 | 规范 |
| --- | --- | --- | --- |
| RocketMQ 5（namespace `VI`） | 话单、合音、推送 | vi-as、vi-media、vi-merge、offline-merge、vi-billcenter、vi-pusher | Topic 见 `06-data/mq/topics.yaml`。消费者代码无条件返回 SUCCESS，失败不靠 MQ 重投，而靠业务延时消息 |
| RabbitMQ（scheduler 南向） | 质检任务、模型请求/响应、回调 | qa-server、scheduler、ai-models | 北向客户端被注释。exchange 实际是 TopicExchange。任务队列 `scheduler.task.queue` 代码未声明，需预建 |
| Redis | 策略命中、qa-server 白名单与分钟桶、分片中断标记 | vi-cront-jobs、strategy-service、qa-server、scheduler | Key 见 `06-data/redis/keys.yaml` |
| ETCD | 服务注册、配置版本、放音文件列表 | vi-as、vi-media、vi-merge、offline-merge、qa-server | Key 见 `06-data/etcd/keys.yaml` |
| PFS / vi-mdown | 录音与合音文件 | vi-merge、offline-merge | 分片 ZoneC，整通 ZoneM |

不要把两套 MQ 的事件混在一个消费者里。RocketMQ 的合音 topic 名在仓库配置之间不一致，改名要同时改生产、消费和 `06-data/events/events.yaml`。
