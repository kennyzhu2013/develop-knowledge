---
id: runbook-record-merge-backlog
title: Runbook - 录音合音积压
domain: operation
kind: stable
audience: [ops, developer]
owner: TODO
status: draft
last_verified: 2026-10-15
related:
  - ops-monitoring
  - ops-emergency
  - ops-deployment
  - svc-vi-media
  - svc-vi-merge
  - svc-offline-merge
  - svc-vi-billcenter
  - runbook-bill-backlog
  - runbook-qc-result-delay
source_of_truth:
  - vi-assets/vi-media-master/internal/proxy/record_audio.go
  - vi-assets/offline-merge-master/internal/pkg/broker/receive/mq_consumer.go
  - vi-assets/vi-merge-master/internal/metrics/metrics.go
  - vi-assets/offline-merge-master/config.prod/app.yaml
---

# Runbook：录音合音积压

两条链路不要混在一个消费组里看：

- 通话中：vi-codec 经 WebSocket 把 PCM 送到 vi-merge（9033），分片消息发到 `TID_VI_MERGE_MSG_BILL_NORMAL`。
- 通话结束：vi-media 发 `TID_VI_MEDIA_RECORD_MERGE_NORMAL`，offline-merge 用消费组 `GID_VI_MERGE_RECORD` 合成整通，再发 `TID_VI_MERGE_BILL_RECORD_NORMAL`。

实时推流成功时，vi-media 不发离线合音 MQ（`record_audio.go` 的 Cancel：`needVI && !enableVI` 则跳过）。`fallback=true` 才表示实时流失败后改走离线。

CONFLICT: `vi-assets/vi-merge-master/config.prod/app.yaml` 与 `vi-assets/offline-merge-master/config.prod/app.yaml` vs vi-billcenter RecordReceiver：合音生产 topic 与 vi-billcenter 订阅的 `TID_VI_ONE_MERGE_MSG_BILL_NORMAL` 不一致。消费组堆积为零也可能是消息发到了另一个 topic。

## 现象

- `GID_VI_MERGE_RECORD` 堆积上升，或 offline-merge 的 `offlinemerge_mq_receive_count` 不增加而 vi-media 仍在产生录音。
- vi-merge `vimerge_stream_merge_active_session` 接近 2000，或 `vimerge_stream_merge_upload_fail_count`、`vimerge_stream_pcmmix_chanfull_count` 增加。
- 接通话单停在 `bill_new`，`quality_inspection_status=0`。
- 磁盘告警：`/tmp/mergeCall_*`、`/data/offline`、`/data/offlocal`、`/tmp/media/call`、`/data/media/call`。

## 影响

质检任务发不出去。呼叫接续不依赖合音完成。vi-merge 停机不等待在途会话，实时分片会丢。offline-merge 上传失败仍会 ack，并可能发出「合音完成」消息，下游以为文件已经在 vi-mdown 上。

## 止血（先做）

1. 只读分开链路。offline-merge：

```text
curl -sS "http://<offline-merge>:9037/metrics"
curl -sS "http://<offline-merge>:9037/ops/omu/status"
```

vi-merge：

```text
curl -sS "http://<vi-merge>:9034/metrics"
curl -sS "http://<vi-merge>:9034/ops/omu/status"
```

vi-media：

```text
curl -sS "http://<vi-media>:9060/som/service/status"
curl -sS "http://<vi-media>:9060/metrics"
```

RocketMQ 控制台看 `GID_VI_MERGE_RECORD`（命令待确认）。同时看生产 topic `TID_VI_MERGE_MSG_BILL_NORMAL`、`TID_VI_MERGE_BILL_RECORD_NORMAL` 是否有人消费。

2. 磁盘满时先清理系统临时目录里已确认无主的文件。`/data/offlocal/` 是 `uploadFailSaveLocal=true` 时的兜底，代码没有补传任务。谁来补传待确认。确认之前不要删除这些文件，也不要写补传脚本。

3. offline-merge 进程不在时，按 `07-operation/deployment.md` 拉起。积压时不要先调用 `/ops/mq/stop`，那是停机前停消费的步骤。`/v1/test` 会真实下载、上传并发 MQ，排障不要调用。`/testmetrics` 会写测试指标。

4. vi-merge 的 `stream_node_notfound_count` 升高：ETCD 上 `com.cmic.vi.mdownload`（集群 `vii`）没有 UP 节点。先恢复 vi-mdown，而不是加大合音进程。

5. vi-media 会话停不下来时，IN_CALL 超时是 14400 秒。`stop.sh` 会一直等。不要对在途会话发强制停止来「清积压」。

## 排查路径

| # | 检查 | 方法 | 正常 | 异常时 |
| --- | --- | --- | --- | --- |
| 1 | 离线消费组 | 控制台看 `GID_VI_MERGE_RECORD` / `TID_VI_MEDIA_RECORD_MERGE_NORMAL` | 差值不持续上升（阈值待确认） | 看第 2、3 行。线程数配置 `consumptionThreadCount` 为 20，热更新接口不存在 |
| 2 | 下载 | 指标 `merge_down_fail_count` | 不持续增加 | 回源是 POST `http://<消息 address>/media/download/<文件名>`，失败不重试。address 来自 vi-media 的 apiAddress |
| 3 | 上传 | `merge_upload_fail_count`、`merge_file_local_save_count` | 不增加 | 文件可能在 `/data/offlocal/`。消息里没有上传成功字段 |
| 4 | 重投放大 | `merge_bill_fail_count` | 偶发 | 只有发账单 MQ 失败才重投，重投会整段重做，磁盘和 vi-mdown 都会被再写一次 |
| 5 | 实时合音 | `stream_merge_active_session`、`stream_pcmmix_chanfull_count`、`stream_merge_upload_fail_count` | 会话低于 2000，通道未满 | 通道容量 250，满则丢块。上传超时 10 秒，大文件 20 秒 |
| 6 | 实时不发离线 MQ | vi-media 日志与消息里的 `fallback` | 实时成功则没有离线消息 | 这是代码路径，不是消费组挂了。fallback 才应出现离线消息 |
| 7 | 下游接得住 | `GID_VI_ONE_BILLCENTER_RECORD` 与两个生产 topic | 消费组名见 `06-data/mq/topics.yaml` | topic 不一致时见文首 CONFLICT，并转 `07-operation/runbooks/bill-backlog.md` |
| 8 | ffmpeg | vi-merge 工作目录 `./ffmpeg` 权限恰好 0755 或 0777 | 进程已在跑 | 权限不对会在启动时失败，表现为没有新合音而不是变慢 |
| 9 | 媒体磁盘 | `df` 查看 `/tmp/media/call`、`/data/media/call` | 未满 | 清理 cron 只删「7 天前那一天」。停机错过的日期目录会留下 |

## 历史案例

`10-lessons/overview.md` 与 `10-lessons/incidents/` 中尚无本现象的已归档案例。

## 恢复确认

`GID_VI_MERGE_RECORD` 差值回落。`merge_down_fail_count` 与上传失败计数不再持续增加。新的接通话单能离开「只等合音」的状态（见 `07-operation/runbooks/qc-result-delay.md` 的状态表）。`/data/offlocal/` 的新增停止。恢复时延阈值待确认。
