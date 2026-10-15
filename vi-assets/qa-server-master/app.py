import datetime
import logging

from apscheduler.events import EVENT_JOB_ERROR, EVENT_JOB_MAX_INSTANCES
from apscheduler.schedulers.background import BackgroundScheduler
from flask import Flask
from flask.json.provider import DefaultJSONProvider
from flask_cors import CORS

from config import settings
from utils.cache import load_cache, load_qa_rule
from utils.helpers import JsonEncoder
from utils.clickhouse import CK_ENGINE_DICT
from utils.mysql import MYSQL_ENGINE_DICT
from utils.redis import Redis


class JsonProvider(DefaultJSONProvider):
    default = staticmethod(JsonEncoder.default)
    # 返回中文原文
    ensure_ascii = False


class FlaskApp(Flask):
    # 返回json格式转换
    json_provider_class = JsonProvider


app = FlaskApp(__name__)
app.config.update(settings)
app.app_context().push()
# 跨域处理
CORS(app)
# session有效期1天
app.config["PERMANENT_SESSION_LIFETIME"] = datetime.timedelta(days=1)

# 注册blueprint
from apps.views import bp_v1, bp_manage, bp_v2
from apps.vi.views import bp_vi
blueprints = [bp_v1, bp_manage, bp_vi, bp_v2]
for blueprint in blueprints:
    app.register_blueprint(blueprint)
# 打印url映射
url_list = []
for r in app.url_map.iter_rules():
    app.logger.info("%s ----> %s %s", r.rule, r.endpoint, r.methods)

# 初始化Redis连接
Redis.init(settings["REDIS"])
app.logger.info("length of mysql datasource: %d", len(MYSQL_ENGINE_DICT)+1)
app.logger.info("length of clickhouse datasource: %d", len(CK_ENGINE_DICT)+1)
# 加载缓存
load_cache()
# 更新质检规则
load_qa_rule()

from apps.sub_worker import flush_data_to_db
from apps.retry_worker import retry_qa_fail

flush_logger = logging.getLogger("flush")


def _on_flush_scheduler_event(event):
    if event.code == EVENT_JOB_MAX_INSTANCES:
        flush_logger.warning("job %s skipped: previous instance still running", event.job_id)
    elif event.code == EVENT_JOB_ERROR:
        flush_logger.error("job %s failed: %s", event.job_id, event.exception)


# 定时任务
scheduler = BackgroundScheduler()
scheduler.add_listener(_on_flush_scheduler_event, EVENT_JOB_MAX_INSTANCES | EVENT_JOB_ERROR)
# 每分钟定时把 redis 中的子结果写入 StarRocks
scheduler.add_job(
    flush_data_to_db, trigger="cron", minute="*", args=("mid",),
    id="flush_qa_subresult", max_instances=1, replace_existing=True,
)
scheduler.add_job(
    flush_data_to_db, trigger="cron", minute="*", args=("dcc",),
    id="flush_dcc_subresult", max_instances=1, replace_existing=True,
)
scheduler.add_job(
    flush_data_to_db, trigger="cron", minute="*", args=("vi",),
    id="flush_vi_subresult", max_instances=1, replace_existing=True,
)
# 每整 10 分钟扫描 qa_fail 重投调度
scheduler.add_job(
    retry_qa_fail, trigger="cron", minute="*/10",
    id="retry_qa_fail", max_instances=1, replace_existing=True,
)


def start_background_tasks():
    """在 Gunicorn fork worker 完成后由 when_ready 调用，避免 preload 阶段启线程导致 fork 不安全。"""
    if getattr(start_background_tasks, "_started", False):
        return
    start_background_tasks._started = True
    scheduler.start()
    if not app.debug:
        from consumers import start_consumers_once
        start_consumers_once()
