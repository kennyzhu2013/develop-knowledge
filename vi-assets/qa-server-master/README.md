# qa-server

质检任务网关服务：对外提供 HTTP API 创建与管理质检任务，通过 RabbitMQ 与调度系统交互，消费质检结果并写入 ClickHouse / StarRocks，同时提供 VI（企业开户、号码报备等）管理接口。

## 功能概览

| 模块       | 说明                                                 |
| ---------- | ---------------------------------------------------- |
| HTTP API   | 任务创建、词袋/话术管理、文本质检、专线规则任务等    |
| MQ 消费者  | 质检结果、准实时任务、结果重推（生产环境随进程启动） |
| 定时任务   | 每分钟将 Redis 中的子结果批量刷入 StarRocks          |
| ETCD 监听  | VI 配置变更时刷新本地缓存                            |
| Prometheus | 独立端口暴露请求与任务指标                           |

## 技术栈

- Python 3.14+（见 `requirements.txt`）
- Flask 3 + Gunicorn（`gthread`）
- MySQL / ClickHouse / StarRocks / Redis / MongoDB / RabbitMQ / ETCD

## 项目结构

```
qa-server/
├── app.py                 # Flask 应用入口、Blueprint 注册、定时任务
├── config.py              # 按环境加载 config.yaml / logging.yaml
├── consumers.py           # MQ 消费者与 Prometheus、ETCD 启动
├── gunicorn_conf.py       # Gunicorn 配置
├── models.py              # SQLAlchemy 模型
├── apps/
│   ├── __init__.py        # 全局鉴权、请求/响应钩子
│   ├── views.py           # /v1、/v2、/manage 接口
│   ├── vi/views.py        # /vi 企业开户与号码接口
│   ├── service.py         # 任务创建等业务逻辑
│   ├── result_consumer.py # 质检结果消费
│   ├── task_consumer.py   # 准实时任务消费
│   ├── repush_consumer.py # 结果重推消费
│   └── sub_worker.py      # Redis → StarRocks 定时刷库
├── utils/                 # Redis、MySQL、CK、MQ、ETCD、缓存等
├── config.local/          # 本地开发配置（勿提交敏感信息）
├── config.test/           # 测试环境配置
└── config.prod/           # 生产环境配置
```

## 环境要求

- Python 3.14+
- 可访问的 MySQL、Redis（必选）
- 完整功能还需：RabbitMQ、ClickHouse/StarRocks、MongoDB、ETCD（按接口与部署裁剪）

## 配置

通过环境变量 `FLASK_ENV` 选择配置目录（默认 `local`）：

| FLASK_ENV       | 配置目录        |
| --------------- | --------------- |
| `local`（默认） | `config.local/` |
| `test`          | `config.test/`  |
| `production`    | `config.prod/`  |

每个目录包含：

- `config.yaml` — 数据库、Redis、MQ、Gunicorn、消费者数量等
- `logging.yaml` — 日志格式与输出路径

本地开发可复制 `config.test/config.yaml` 到 `config.local/config.yaml` 后按本机修改。`config.local/` 建议仅保留在本地，不要将含真实密码的文件提交到 Git。

`config.local` 若缺少 Gunicorn 相关项，可补充例如：

```yaml
GUNICORN_BIND: "0.0.0.0:7220"
GUNICORN_WORKERS: 1
GUNICORN_THREADS: 4
```

本地调试可将 `RESULT_CONSUMERS`、`REALTIME_CONSUMERS`、`REPUSH_CONSUMERS` 设为 `0`，避免启动 MQ 消费线程。

## 安装与运行

### 本地

```bash
pip install -r requirements.txt

# 可选：指定环境
set FLASK_ENV=local          # Windows
# export FLASK_ENV=local     # Linux/macOS

# 开发模式（会启动 debug，且不启动 MQ 消费者）
set FLASK_DEBUG=1
python app.py

# 或使用 Gunicorn（与生产一致）
gunicorn -c gunicorn_conf.py app:app
```

日志目录由配置中的 `LOGGING_PATH` 决定，不存在时会自动创建。

### Docker

```bash
docker build -t qa-server .
docker run -e FLASK_ENV=production -p 7220:7220 qa-server
```

生产镜像通过 `gunicorn_conf.py` 读取 `GUNICORN_BIND` 等配置。

## 鉴权与安全

除 `/manage` 外，业务接口均需通过 `apps/__init__.py` 中的 `before_request` 校验。

### 1. IP 白名单

请求源 IP（`X-Forwarded-For` 首段或 `remote_addr`）须在 Redis 集合 `QASERVER:WHITEIP` 中。

### 2. Authorization（EOPAUTH）

请求头示例：

```
Authorization: EOPAUTH appid="<app_key>", timestamp="<unix_seconds>", signature="<SIGNATURE>"
```

- `timestamp`：秒级 Unix 时间戳，与服务端时间差不超过 5 分钟
- `signature`：大写 MD5  
  `MD5(timestamp + app_secret)`（HTTPS 模式不拼接 body）

`app_secret` 由 Redis 缓存，来源于 `qa_subject` 等企业配置。

### 3. 回调 URL 白名单

请求体中的 `taskCallBackUrl`、`subTaskCallBackUrl`（若存在）须在 Redis 集合 `QASERVER:WHITEURL` 中。

### 4. 管理接口 `/manage`

仅允许内网 IP 访问，不做 EOPAUTH 签名校验。例如：

- `POST /manage/info/reload` — 重新加载企业/渠道缓存

## API 一览

### `/v1` — 质检与资源

| 方法 | 路径                 | 说明                   |
| ---- | -------------------- | ---------------------- |
| POST | `/v1/front/create`   | 按原子能力开关创建任务 |
| POST | `/v1/task/create`    | 按规则创建任务         |
| POST | `/v1/task/customize` | 自定义检测任务         |
| POST | `/v1/text/check`     | 文本质检               |
| POST | `/v1/keyword/create` | 创建词袋               |
| POST | `/v1/keyword/update` | 更新词袋               |
| POST | `/v1/script/create`  | 创建话术               |
| POST | `/v1/script/update`  | 更新话术               |

### `/v2` — 专线任务

| 方法 | 路径              | 说明                       |
| ---- | ----------------- | -------------------------- |
| POST | `/v2/task/create` | 按规则 ID 创建专线质检任务 |

### `/vi` — 企业开户（VI）

| 方法 | 路径                          | 说明                          |
| ---- | ----------------------------- | ----------------------------- |
| POST | `/vi/enterprise/create`       | 企业开户                      |
| POST | `/vi/enterprise/modifyScene`  | 修改场景                      |
| POST | `/vi/enterprise/modifyScript` | 修改话术                      |
| POST | `/vi/enterprise/cancel`       | 企业注销                      |
| POST | `/vi/number/addOrDel`         | 号码批量新增/删除（异步回调） |

### `/manage` — 运维（内网）

| 方法 | 路径                  | 说明                     |
| ---- | --------------------- | ------------------------ |
| POST | `/manage/info/reload` | 刷新企业/渠道 Redis 缓存 |

请求体一般为 JSON，`Content-Type: application/json`。各接口字段校验见对应 `views.py` 中的 JSON Schema。

## 响应码

业务 JSON 中 `code` 字段含义（节选）：

| code   | 含义                                   |
| ------ | -------------------------------------- |
| `0000` | 成功                                   |
| `1001` | 重复请求或 requestId 无效（VI 等接口） |
| `1002` | 参数缺失或企业/渠道校验失败            |
| `1003` | 参数值错误                             |
| `9999` | 服务内部错误                           |

HTTP 鉴权失败返回 **403**，无业务 `code` 体。

## 后台任务

进程启动后（非 `debug` 模式）会额外启动：

1. **Prometheus**：`PROMETHEUS_HOST`:`PROMETHEUS_PORT`（如 `0.0.0.0:7225`）
2. **ETCD Watcher**：监听 VI 配置版本键
3. **MQ 消费者**：数量由 `RESULT_CONSUMERS`、`REALTIME_CONSUMERS`、`REPUSH_CONSUMERS` 控制
4. **APScheduler**：每分钟执行 `flush_data_to_db`，将 Redis 子结果写入 StarRocks

多 Gunicorn worker 时，定时任务与消费者可能重复执行；生产环境当前多为 `GUNICORN_WORKERS: 1`，扩容前需评估分布式锁或独立 worker 进程。

## 监控

- Gunicorn 访问/错误日志：`LOGGING_PATH` 下 `gunicorn-access.log`、`gunicorn-error.log`
- 应用日志：见 `logging.yaml` 中 `all` 等 logger 配置
- Prometheus 指标：由 `apps/prometheus.py` 注册，经 `consumers.start_prometheus_app` 暴露

## 常见问题

**启动报配置或连接错误**  
检查 `FLASK_ENV` 对应目录下的 `config.yaml` 中 MySQL、Redis 地址与账号，并确认网络可达。

**接口一律 403**  
确认调用方 IP 与回调 URL 已写入 Redis 白名单，且 `Authorization` 时间戳与签名正确。

**本地不想连 MQ**  
在 `config.local/config.yaml` 将三类 `*_CONSUMERS` 设为 `0`，或使用 `FLASK_DEBUG=1` 启动（`app.debug` 为真时不启动消费者）。

**VI 号码接口返回 taskId 但无回调**  
`/vi/number/addOrDel` 在后台线程异步处理，需配置企业 `numberUrl` 并保证可访问。

## 仓库

GitLab: https://gitlab.tools.cmic.site/ytx/zjh/uniops/qa-server

## 相关文档

- 调度流程 ID：配置项 `PROCESS_COLLECTION`
- 任务上限：`TASK_LIMITATION`（单次任务子项数量限制）
