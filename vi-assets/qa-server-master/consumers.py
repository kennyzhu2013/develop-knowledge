import atexit
import logging
import queue
import threading
import time

from prometheus_client import make_wsgi_app

from config import settings
from utils.etcd import EtcdWatcherManager
from apps.result_consumer import QaResultConsumeThread
from apps.repush_consumer import RepushConsumeThread
from apps.task_consumer import QaTaskConsumeThread


logger = logging.getLogger("all")


_prometheus_server = None
_metrics_running = True
# 略低于常见 scrape_timeout(10s)，超时后回退缓存，避免 Prometheus 先放弃
METRICS_SCRAPE_TIMEOUT = 8
# 无新鲜采集时，允许返回的缓存最大年龄（桥接短暂卡顿，避免面板断点）
METRICS_CACHE_MAX_AGE = 120
# 距上次成功采集不足该秒数则直接复用缓存，避免并发 scrape 叠收集
METRICS_CACHE_FRESH_SECONDS = 5
# 死进程 multiproc 文件清理最小间隔
METRICS_ORPHAN_CLEANUP_INTERVAL = 60

_collect_lock = threading.Lock()
_cache_lock = threading.Lock()
# (body_bytes, status_str, headers_list, monotonic_ts)
_metrics_cache = None
_last_orphan_cleanup_at = 0.0


def _get_metrics_cache():
    with _cache_lock:
        return _metrics_cache


def _set_metrics_cache(body, status, headers):
    global _metrics_cache
    with _cache_lock:
        _metrics_cache = (body, status, list(headers), time.monotonic())


def _respond_cached(start_response, cached, stale=False):
    body, status, headers, _ts = cached
    out_headers = list(headers)
    if stale:
        out_headers.append(("X-Metrics-Cache", "stale"))
    start_response(status, out_headers)
    return [body]


def _maybe_cleanup_orphan_multiproc():
    global _last_orphan_cleanup_at
    now = time.monotonic()
    if now - _last_orphan_cleanup_at < METRICS_ORPHAN_CLEANUP_INTERVAL:
        return
    _last_orphan_cleanup_at = now
    try:
        from apps.prometheus import cleanup_multiproc_files
        cleanup_multiproc_files()
    except Exception:
        logger.exception("清理 multiproc 残留文件失败")


def _with_scrape_timeout(app, scrape_timeout=METRICS_SCRAPE_TIMEOUT):
    """
    /metrics 收集保护：
    1) 单飞锁由收集线程持有到结束，超时返回后也不叠开新的 Multiproc 扫盘
    2) 成功结果短缓存；忙/超时时回退缓存，减少 Prometheus 空窗与面板断点
    """

    def wrapper(environ, start_response):
        cached = _get_metrics_cache()
        if cached is not None and time.monotonic() - cached[3] < METRICS_CACHE_FRESH_SECONDS:
            return _respond_cached(start_response, cached, stale=False)

        if not _collect_lock.acquire(blocking=False):
            cached = _get_metrics_cache()
            if cached is not None and time.monotonic() - cached[3] <= METRICS_CACHE_MAX_AGE:
                return _respond_cached(start_response, cached, stale=True)
            start_response(
                "503 Service Unavailable",
                [("Content-Type", "text/plain; charset=utf-8")],
            )
            return [b"metrics scrape busy\n"]

        # 锁交给收集线程在 finally 中释放（请求超时返回后仍占用，防叠收集）
        result_q = queue.Queue(maxsize=1)

        def run():
            try:
                _maybe_cleanup_orphan_multiproc()
                chunks = []
                started = []

                def capture_start_response(status, headers, exc_info=None):
                    started.append((status, headers, exc_info))

                    def write(data):
                        chunks.append(data)

                    return write

                body_iter = app(environ, capture_start_response)
                try:
                    for chunk in body_iter:
                        chunks.append(chunk)
                finally:
                    if hasattr(body_iter, "close"):
                        body_iter.close()
                if not started:
                    raise RuntimeError("metrics app did not call start_response")
                status, headers, exc_info = started[0]
                body = b"".join(chunks)
                if isinstance(status, str) and status.startswith("2"):
                    _set_metrics_cache(body, status, headers)
                result_q.put(("ok", (status, headers, exc_info), body))
            except Exception as exc:
                result_q.put(("err", exc, None))
            finally:
                _collect_lock.release()

        worker = threading.Thread(target=run, name="metrics-scrape", daemon=True)
        try:
            worker.start()
        except Exception:
            _collect_lock.release()
            raise
        try:
            kind, payload, body = result_q.get(timeout=scrape_timeout)
        except queue.Empty:
            logger.error(
                "Prometheus /metrics 收集超时(%ss)；请检查 PROMETHEUS_MULTIPROC_DIR 文件是否堆积",
                scrape_timeout,
            )
            cached = _get_metrics_cache()
            if cached is not None and time.monotonic() - cached[3] <= METRICS_CACHE_MAX_AGE:
                logger.warning("Prometheus /metrics 超时，返回 %ss 内缓存", METRICS_CACHE_MAX_AGE)
                return _respond_cached(start_response, cached, stale=True)
            start_response(
                "503 Service Unavailable",
                [("Content-Type", "text/plain; charset=utf-8")],
            )
            return [b"metrics scrape timed out\n"]

        if kind == "err":
            logger.error("Prometheus /metrics 收集失败: %s", payload, exc_info=payload)
            cached = _get_metrics_cache()
            if cached is not None and time.monotonic() - cached[3] <= METRICS_CACHE_MAX_AGE:
                return _respond_cached(start_response, cached, stale=True)
            start_response(
                "500 Internal Server Error",
                [("Content-Type", "text/plain; charset=utf-8")],
            )
            return [b"metrics scrape failed\n"]

        status, headers, exc_info = payload
        start_response(status, headers, exc_info)
        return [body]

    return wrapper


def start_prometheus_app():
    global _prometheus_server, _metrics_running
    from werkzeug.serving import WSGIRequestHandler, make_server

    from apps.prometheus import registry

    host = settings["PROMETHEUS_HOST"]
    port = int(settings["PROMETHEUS_PORT"])
    logger.info("暴露监控指标端口: %s:%s", host, port)
    metrics_app = _with_scrape_timeout(make_wsgi_app(registry))

    class MetricsRequestHandler(WSGIRequestHandler):
        timeout = 30  # socket 读超时
        server_version = "metrics"
        sys_version = ""

    # bind + serve 都放进重试循环，端口未释放等启动失败也可自愈
    _metrics_running = True
    while _metrics_running:
        server = None
        try:
            server = make_server(
                host,
                port,
                metrics_app,
                threaded=True,
                request_handler=MetricsRequestHandler,
            )
            _prometheus_server = server
            server.serve_forever()
        except Exception:
            if not _metrics_running:
                break
            logger.exception("Prometheus监控服务异常退出，5秒后重试启动")
            time.sleep(5)
        finally:
            if server is not None:
                try:
                    server.server_close()
                except Exception:
                    pass
                if _prometheus_server is server:
                    _prometheus_server = None
        if not _metrics_running:
            break


worker_list = []
timeout = 15
watcher = None
prometheus_app = None
_shutdown_once = threading.Event()


def start_consumers_once():
    global watcher, prometheus_app
    # 防重复：同进程内只启动一次
    if getattr(start_consumers_once, "_started", False):
        return
    start_consumers_once._started = True
    # 启动额外的线程暴露监控指标
    prometheus_app = threading.Thread(target=start_prometheus_app, daemon=True)
    prometheus_app.name = "prometheus-app"
    prometheus_app.start()

    # 监听ETCD（集群 all_addrs，断连自动 failover）
    etcd_cfg = settings["ETCD_CONFIG"]
    watcher = EtcdWatcherManager(
        prefixes=[etcd_cfg["config_key"], etcd_cfg["rule_key"]],
        endpoints=etcd_cfg.get("all_addrs"),
    )
    watcher.start()

    # 质检结果队列
    for i in range(settings["RESULT_CONSUMERS"]):
        th_result = QaResultConsumeThread()
        th_result.name = f"qa-result-thread-{i}"
        th_result.start()
        worker_list.append(th_result)
    logger.info(f"已启动{settings['RESULT_CONSUMERS']}个质检结果队列消费者")
    # 开始监听准实时任务队列
    for i in range(settings["REALTIME_CONSUMERS"]):
        th_task = QaTaskConsumeThread()
        th_task.name = f"realtime-task-thread-{i}"
        th_task.start()
        worker_list.append(th_task)
    logger.info(f"已启动{settings['REALTIME_CONSUMERS']}个准实时任务队列消费者")
    
    # 质检结果重推队列
    for i in range(settings["REPUSH_CONSUMERS"]):
        repush_thread = RepushConsumeThread()
        repush_thread.name = f"repush-result-thread-{i}"
        repush_thread.start()
        worker_list.append(repush_thread)
    logger.info(f"已启动{settings['REPUSH_CONSUMERS']}个质检结果重推队列消费者")


def _shutdown_all_thread():
    if _shutdown_once.is_set():
        return
    _shutdown_once.set()
    logger.info("准备停止MQ消费线程和ETCD监听线程")
    # 先发停止信号
    for t in worker_list:
        try:
            t.stop()
            logger.info(t.name)
        except Exception:
            logger.exception("停止线程失败: %s", t.name)

    # 再统一join
    for t in worker_list:
        try:
            t.join(timeout=timeout)
            logger.info(t.name)
        except Exception:
            logger.exception("等待线程退出失败: %s", t.name)
    
    if _prometheus_server is not None:
        try:
            global _metrics_running
            _metrics_running = False
            _prometheus_server.shutdown()
        except Exception:
            logger.exception("停止 Prometheus 服务失败")
    if prometheus_app:
        prometheus_app.join(timeout=timeout)
    if watcher:
        watcher.stop()
    logger.info("所有线程停止完毕")

# 进程退出时优雅关闭（不覆盖 Gunicorn 的 SIGTERM 处理，由 atexit 兜底）
atexit.register(_shutdown_all_thread)
