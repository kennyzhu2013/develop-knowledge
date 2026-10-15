import os

from config import settings
# 是否开启debug模式
debug = False
# 访问地址
bind = settings["GUNICORN_BIND"]
# 工作进程数
workers = settings["GUNICORN_WORKERS"]
# 工作线程数
threads = settings["GUNICORN_THREADS"]
# 超时时间
timeout = 60
# 自定义logger
logger_class = "gunicorn_logger.GLogger"
# 输出日志级别
loglevel = "info"
# 存放日志路径
pidfile = os.path.join(settings["LOGGING_PATH"], "gunicorn.pid")
# 存放日志路径
errorlog = os.path.join(settings["LOGGING_PATH"], "gunicorn-error.log")
# 存放日志路径
accesslog = os.path.join(settings["LOGGING_PATH"], "gunicorn-access.log")
# 日志格式
access_log_format = '%(h)s %(u)s "%(r)s" %(s)s %(b)s "%(a)s"'
# gunicorn + apscheduler场景下，解决多worker运行定时任务重复执行的问题
# preload_app = True
# 守护程序设置false，交由supervisor管理
daemon = False
# 每个工作进程的最大并发连接数
worker_connections = 1000
# 工作进程类型
worker_class = "gthread"


def on_starting(server):
    """Master 启动时清空 multiproc 目录，避免历史 .db 导致 /metrics 收集变慢或卡住。"""
    multiproc_dir = os.environ.get("PROMETHEUS_MULTIPROC_DIR", "")
    if not multiproc_dir:
        return
    import shutil
    if os.path.isdir(multiproc_dir):
        shutil.rmtree(multiproc_dir, ignore_errors=True)
    os.makedirs(multiproc_dir, exist_ok=True)


def when_ready(server):
    """Workers fork 完成后再启动后台线程，仅在 master 进程执行一次。"""
    from app import start_background_tasks
    start_background_tasks()


def post_fork(server, worker):
    """每个 worker 启动缓存版本轮询，接收 master ETCD 触发的跨进程重载通知。"""
    from utils.cache_sync import start_cache_reload_watcher
    start_cache_reload_watcher()


def child_exit(server, worker):
    """在 master 中清理已退出 worker 的 live* gauge（官方推荐，不影响 Counter 累计）。"""
    try:
        from apps.prometheus import cleanup_multiproc_files
        cleanup_multiproc_files(pids=[worker.pid])
    except Exception:
        pass
