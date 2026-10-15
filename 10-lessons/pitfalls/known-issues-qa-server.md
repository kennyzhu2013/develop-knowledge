---
id: lesson-known-issues-qa-server
title: qa-server 已知坑与不要随手删的逻辑
domain: lessons
kind: stable
audience: [developer, ops]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - lessons-overview
  - svc-qa-server
  - module-qa-server
  - lesson-known-issues-vi-billcenter
  - lesson-known-issues-scheduler
  - lesson-doc-code-conflicts
  - lesson-open-questions
source_of_truth:
  - vi-assets/qa-server-master/apps/views.py
  - vi-assets/qa-server-master/apps/service.py
  - vi-assets/qa-server-master/apps/__init__.py
  - vi-assets/qa-server-master/gunicorn_conf.py
---

# qa-server 已知坑与不要随手删的逻辑

核对 `vi-assets/qa-server-master`，2026-10-15。生产 vi-billcenter 调用的是 `/v1/task/customize`，不是文档里的 `/v2/task/create`，见 `10-lessons/pitfalls/doc-code-conflicts.md`。

| # | 现象 | 代码位置 | 影响 | 为什么不能随手删 |
| --- | --- | --- | --- | --- |
| 1 | MQ 消费、定时入库、重试跑在 Gunicorn master 的线程里。worker 的配置缓存靠本地文件版本号同步，master 靠 ETCD watch。 | `vi-assets/qa-server-master/gunicorn_conf.py`、`vi-assets/qa-server-master/utils/cache_sync.py` | master 异常时 HTTP 可能还在，但消费、入库、重试全停。 | 把后台线程挪进 worker 会变成每个 worker 各消费一遍。删 master 钩子会让质检结果没有消费者。 |
| 2 | 创建接口返回成功时，任务还没落库、也还没投 MQ。后续失败只把任务状态置 2，调用方无感知，也没有补偿重投。是否有监控和人工补偿，待确认。 | `vi-assets/qa-server-master/apps/service.py` | vi-billcenter 以为质检已发起，实际任务失败。 | 异步是为了让创建接口不被落库和投递拖住。改成同步要重新定超时。失败标记是唯一可见状态，不能删。 |
| 3 | 签名是时间戳加密钥的摘要，不覆盖请求体。5 分钟内签名可复用到任意 body。IP 白名单通配命中后会把精确 IP 写进集合，只增不减。 | `vi-assets/qa-server-master/apps/__init__.py` | 截获一次签名就能在窗口内改 body。白名单会越积越大。 | 签名校验是所有请求的入口。删掉会让创建和回调失去鉴权。应改成覆盖 body，保留校验。 |
| 4 | 雪花机器位来自环境变量，默认 0。多实例没配置时 task_id 可能冲突。同一实例的多个 worker 也共享机器号，同毫秒有冲突可能。实际 worker 数和是否按实例配置机器号，待确认。 | `vi-assets/qa-server-master/utils/snowFlake.py` | 任务 ID 冲突会导致插入失败或串任务。 | ID 生成是创建主路径。删雪花会让任务没有主键。应保证机器号唯一，而不是换一套不兼容的 ID。 |
| 5 | 入库游标不存在（首次上线或被误删）时从大约 2 分钟前开始。更早的分钟桶永远不会入库，也没有 TTL。 | `vi-assets/qa-server-master/apps/sub_worker.py` | 游标丢失会造成一段结果只留在 Redis，库里没有。 | 从“现在附近”开始是为了避免第一次启动把历史桶全扫进库。删掉起点会在空游标时打满 StarRocks。应把被跳过的桶纳入补偿，保留游标。 |
| 6 | 重推的延迟递进依赖收到消息时 header 仍是原始正数。若延迟插件交付时把延迟改成负数，递进会一直走默认分支，形成无限重推。插件版本待确认。 | `vi-assets/qa-server-master/apps/repush_consumer.py` | 回调失败的任务可能无限重推 vi-billcenter。 | 递进是为了拉长重试间隔并最终停下来。删消费者会让失败回调不再重推，结果更丢。应确认插件行为并加上限。 |
| 7 | 重试投递后若 scheduler 不回结果，记录停在重试中，不再被扫描，也不回调客户。是否有清理，待确认。 | `vi-assets/qa-server-master/apps/retry_worker.py` | 专线失败重试变成悬挂，客户一直等不到最终失败。 | 状态 1 用来防止同一条被两个扫描重复投递。删状态会并发双发。应加超时回扫，保留状态。 |
| 8 | 专线首次失败不马上回调客户。明细先以失败态入库，最多再试若干次才回调。vi-billcenter 需要容忍这段延迟。 | `vi-assets/qa-server-master/apps/result_consumer.py` | 话单中心和客户会觉得质检变慢；过早当失败会和后面的成功回调打架。 | 延迟回调是为了给重试留出成功的机会。删掉会让一次模型失败就通知客户失败，随后重试成功又通知一次。 |
| 9 | 准实时任务在 Redis 里的任务键没有 TTL。 | `vi-assets/qa-server-master/apps/task_consumer.py` | Redis 内存随历史任务增长。 | 这个键是准实时任务的上下文。直接删写入会让结果消费找不到原任务。应加过期时间，保留写入。 |
| 10 | 结果消费即使处理异常也会 ack。只能靠 Mongo 里的失败记录追溯，没有自动重放。 | `vi-assets/qa-server-master/apps/result_consumer.py` | 处理 bug 或瞬时失败会丢这条结果的后续动作。 | 先 ack 是为了不让一条坏消息堵死队列。改成 nack 之前要有死信和幂等。失败集合是补偿线索，不能删。 |
| 11 | Redis 写失败时改成直接写 StarRocks，批量很小、很频繁。 | `vi-assets/qa-server-master/apps/sub_worker.py` | StarRocks 版本数压力上升，导入变慢。 | 直写是 Redis 不可用时的兜底，保证结果还能入库。删兜底会让 Redis 故障期间的质检结果只存在于进程内存。 |
| 12 | customize 在客户不存在时对空字典取 status，返回 500。 | `vi-assets/qa-server-master/apps/views.py` | vi-billcenter 收到 500 而不是业务错误码，不会按“企业不可用”处理。 | 这是创建入口里的账户检查。删检查会让不存在的客户继续建任务。应改成明确错误码。 |
| 13 | customize 的 userData schema 把企业 ID 写成带冒号的键，实际不会校验企业 ID。 | `vi-assets/qa-server-master/apps/views.py` | 缺企业 ID 的请求能通过 schema，后面再失败或写错关联。 | schema 是创建请求的门禁。删整个 schema 会让任意 JSON 进入创建。应改键名，保留校验。 |
| 14 | 号码操作异常被吞，调用方收不到失败回调。 | `vi-assets/qa-server-master/apps/vi/views.py` | 报备或号码变更失败时，对端以为成功。 | 吞异常是为了不让一个号码失败打崩批量接口。应补失败回调，不要删整个号码接口。 |
| 15 | 特定 app 列表在回调时把 content 置空，并走另一套流程。 | `vi-assets/qa-server-master/utils/helpers.py`、`vi-assets/qa-server-master/apps/result_consumer.py`、`vi-assets/qa-server-master/apps/service.py` | 这些应用的客户收不到文本内容；流程 ID 也和默认不同。 | 这是历史客户的兼容分支。删掉会把文本内容推给约定不接收的客户，或改用他们没配的流程。 |
| 16 | 特定客户和特定 extid 的录音 URL 后缀、文件名规则与默认不同。 | `vi-assets/qa-server-master/apps/service.py` | 删掉之后这些客户的录音 URL 404，质检拉不到文件。 | 这是历史存储布局的兼容，按仓库红线不能当无用分支删。改 URL 规则要和存储侧一起改。 |
| 17 | location 为 3 的准实时链路使用单独的文件服务地址，和普通链路不同。 | `vi-assets/qa-server-master/apps/task_consumer.py` | 这个 location 的录音会去另一套文件服务。配错地址就全部 404。 | 分支是多套文件服务的路由。删掉会让该 location 去错误的默认地址拉录音。 |
| 18 | 创建路径把长度为 32 且以 700 开头的 ID 当成另一类话单。雪花解析对长度不小于 32 的 ID 去掉前 14 位。 | `vi-assets/qa-server-master/apps/service.py`、`vi-assets/qa-server-master/utils/snowFlake.py` | 符合这个形状的专线 ID 会被截断或走错话单库，查不到录音。 | 截断是旧话单 ID 的兼容。在确认没有这种旧 ID 之前不要删。新 ID 不要再使用同一形状。 |
