"""
跨 gunicorn worker 的缓存同步。

Master（ETCD watcher）在本地 load_* 后调用 notify_cache_reload；
各 worker 在 post_fork 启动轮询线程，发现版本变化后按 kind 全量重载。

不把 notify 放进 load_cache 内部，避免 worker 重载时再次 notify 形成循环。
"""
import json
import logging
import os
import threading
import time

from config import settings

logger = logging.getLogger("all")

_VERSION_FILE = os.environ.get(
    "QA_CACHE_VERSION_FILE",
    os.path.join(settings["LOGGING_PATH"], "cache_version.json"),
)
_POLL_INTERVAL = float(os.environ.get("QA_CACHE_SYNC_INTERVAL", "1.0"))

_started = False
_start_lock = threading.Lock()
_last_version = None
_stop_event = threading.Event()


def notify_cache_reload(kind: str = "all") -> None:
    """
    通知其它进程重载缓存。
    :param kind: "config" | "rule" | "all"
    """
    if kind not in ("config", "rule", "all"):
        kind = "all"
    payload = {"version": time.time_ns(), "kind": kind, "ts": time.time()}
    directory = os.path.dirname(_VERSION_FILE) or "."
    os.makedirs(directory, exist_ok=True)
    # pid + 线程 + 纳秒，避免同进程多线程并发 notify 抢同一个 tmp
    tmp_path = f"{_VERSION_FILE}.{os.getpid()}.{threading.get_ident()}.{payload['version']}.tmp"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(payload, f)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_path, _VERSION_FILE)
        logger.info("cache sync notify kind=%s version=%s file=%s", kind, payload["version"], _VERSION_FILE)
    except Exception:
        logger.exception("cache sync notify failed")
        try:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
        except OSError:
            pass


def _read_payload():
    try:
        with open(_VERSION_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return None
    except (json.JSONDecodeError, OSError) as err:
        logger.warning("cache sync read version file failed: %s", err)
        return None


def _apply(kind: str) -> None:
    from utils.cache import load_cache, load_qa_rule

    if kind in ("config", "all"):
        load_cache()
    if kind in ("rule", "all"):
        load_qa_rule()


def start_cache_reload_watcher() -> None:
    """在 worker 进程中启动版本轮询线程（幂等）。"""
    global _started, _last_version
    with _start_lock:
        if _started:
            return
        _started = True

    current = _read_payload()
    _last_version = current.get("version") if current else None

    def _loop():
        global _last_version
        logger.info(
            "cache sync watcher started, file=%s interval=%ss last_version=%s",
            _VERSION_FILE, _POLL_INTERVAL, _last_version,
        )
        while not _stop_event.is_set():
            if _stop_event.wait(_POLL_INTERVAL):
                break
            payload = _read_payload()
            if not payload:
                continue
            version = payload.get("version")
            if version is None or version == _last_version:
                continue
            kind = payload.get("kind", "all")
            try:
                logger.info("cache sync apply kind=%s version=%s pid=%s", kind, version, os.getpid())
                _apply(kind)
                _last_version = version
            except Exception:
                logger.exception("cache sync apply failed kind=%s version=%s", kind, version)

    thread = threading.Thread(target=_loop, name="cache-sync-watcher", daemon=True)
    thread.start()


def stop_cache_reload_watcher() -> None:
    _stop_event.set()
