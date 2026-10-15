import datetime
import functools
import logging
import os
import queue
import random
import threading
import time
from typing import List, Optional, Sequence, Tuple

import etcd3
import grpc
import six

from config import settings
from apps.prometheus import count_etcd_watch_error, set_etcd_watch_alive
from utils.cache import load_cache, load_qa_rule
from utils.cache_sync import notify_cache_reload


logger = logging.getLogger("all")


def _etcd_log(level, msg, *args):
    """带 thread/pid 的 ETCD 日志，并 flush，避免嵌套线程/多进程写文件时丢行。"""
    prefix = "[tid=%s pid=%s] " % (threading.current_thread().name, os.getpid())
    logger.log(level, prefix + msg, *args)
    for handler in logging.getLogger("all").handlers:
        try:
            handler.flush()
        except Exception:
            pass
    root = logging.getLogger()
    if root is not logger:
        for handler in root.handlers:
            try:
                handler.flush()
            except Exception:
                pass


def _etcd_info(msg, *args):
    _etcd_log(logging.INFO, msg, *args)


def _etcd_error(msg, *args):
    _etcd_log(logging.ERROR, msg, *args)


def _etcd_exception(msg, *args):
    prefix = "[tid=%s pid=%s] " % (threading.current_thread().name, os.getpid())
    logger.exception(prefix + msg, *args)
    for handler in logging.getLogger("all").handlers:
        try:
            handler.flush()
        except Exception:
            pass

# watch 断连恢复后全量补偿；同 key 短时间多次 PUT（历史多节点写入）合并为一次刷新
_WATCH_DEBOUNCE_SECONDS = 0.5
# 仅约束建连 / unary RPC / watch 创建，不约束长连接收事件
_CLIENT_TIMEOUT = 10
# gRPC keepalive：Watch 流半开时默认永不超时，必须显式打开
_GRPC_KEEPALIVE_TIME_MS = 30000
_GRPC_KEEPALIVE_TIMEOUT_MS = 10000
# 含 progress_notify 在内超过该时间无任何响应，视为僵死并重连（etcd 默认 progress 约 10min）
_WATCH_STALL_SECONDS = 720
# 队列轮询间隔，便于响应 stop，并检查 stall
_WATCH_POLL_SECONDS = 5.0

Endpoint = Tuple[str, int]


def _parse_endpoint(addr) -> Optional[Endpoint]:
    """解析 'host:port'；非法则返回 None。"""
    if addr is None:
        return None
    text = str(addr).strip()
    if not text:
        return None
    if ":" not in text:
        return text, 2379
    host, _, port_text = text.rpartition(":")
    host = host.strip()
    if not host:
        return None
    try:
        port = int(port_text)
    except (TypeError, ValueError):
        return None
    return host, port


def _normalize_endpoints(addrs: Optional[Sequence] = None) -> List[Endpoint]:
    """
    解析集群地址列表。
    优先 all_addrs；为空则回退 ETCD_CONFIG.host/port。
    """
    endpoints = []
    seen = set()
    for addr in addrs or []:
        endpoint = _parse_endpoint(addr)
        if not endpoint or endpoint in seen:
            continue
        seen.add(endpoint)
        endpoints.append(endpoint)

    if endpoints:
        return endpoints

    cfg = settings.get("ETCD_CONFIG") or {}
    host = cfg.get("host")
    port = cfg.get("port", 2379)
    if host:
        return [(str(host), int(port))]
    return []


def get_etcd_endpoints(addrs: Optional[Sequence] = None) -> List[Endpoint]:
    """对外暴露：优先传入地址，否则读配置 all_addrs。"""
    if addrs:
        return _normalize_endpoints(addrs)
    cfg = settings.get("ETCD_CONFIG") or {}
    return _normalize_endpoints(cfg.get("all_addrs"))


def _grpc_options():
    # etcd3 要求 iterable of (key, value)；keepalive 用于长连接 Watch 探活
    return (
        ("grpc.keepalive_time_ms", _GRPC_KEEPALIVE_TIME_MS),
        ("grpc.keepalive_timeout_ms", _GRPC_KEEPALIVE_TIMEOUT_MS),
        ("grpc.keepalive_permit_without_calls", 1),
        ("grpc.http2.max_pings_without_data", 0),
        ("grpc.http2.min_time_between_pings_ms", 10000),
    )


def _create_client(host: str, port: int, timeout: float = _CLIENT_TIMEOUT):
    return etcd3.client(
        host=host,
        port=port,
        timeout=timeout,
        grpc_options=_grpc_options(),
    )


def _safe_close_client(client) -> None:
    """
    先停 etcd3 内部 watch 线程，再关 channel。
    直接 channel.close() 时，watcher._run 的 while True 会对已关闭 channel
    再调 Watch()，抛出未捕获的 ValueError: Cannot invoke RPC: Channel closed!
    """
    if client is None:
        return
    watcher = getattr(client, "watcher", None)
    callback_thread = getattr(watcher, "_callback_thread", None) if watcher else None
    if watcher is not None:
        try:
            # 结束当前 request iterator；配合下方 patch，线程应退出 while
            watcher._request_queue.put(None)
        except Exception:
            pass
    try:
        client.close()
    except Exception:
        pass
    if callback_thread is not None and callback_thread.is_alive():
        try:
            callback_thread.join(timeout=1.0)
        except Exception:
            pass


def _patch_etcd3_watcher_run() -> None:
    """
    安装一次：etcd3 0.12 Watcher._run 只捕 grpc.RpcError，
    channel 关闭时的 ValueError 会打满线程 Traceback。改为安全退出。
    """
    watcher_cls = getattr(etcd3.watch, "Watcher", None)
    if watcher_cls is None or getattr(watcher_cls, "_qa_safe_run_patched", False):
        return

    new_request_iter = etcd3.watch._new_request_iter
    safe_callback = etcd3.watch._safe_callback

    def _run(self):
        while True:
            try:
                response_iter = self._watch_stub.Watch(
                    new_request_iter(self._request_queue),
                    credentials=self._credentials,
                    metadata=self._metadata,
                )
            except ValueError as err:
                # Channel closed / Cannot invoke RPC
                logger.debug("[ETCD] watch stub stopped: %s", err)
                return
            except Exception:
                logger.exception("[ETCD] watch stub failed to start")
                return

            try:
                for rs in response_iter:
                    self._handle_response(rs)
            except grpc.RpcError as err:
                with self._lock:
                    if self._new_watch:
                        self._new_watch.err = err
                        self._new_watch_cond.notify_all()
                    callbacks = self._callbacks
                    self._callbacks = {}
                    self._request_queue.put(None)
                    self._request_queue = queue.Queue(maxsize=10)
                for callback in six.itervalues(callbacks):
                    safe_callback(callback, err)
            except ValueError as err:
                logger.debug("[ETCD] watch stream stopped: %s", err)
                return
            except Exception:
                logger.exception("[ETCD] watch stream crashed")
                return

    watcher_cls._run = _run
    watcher_cls._qa_safe_run_patched = True


_patch_etcd3_watcher_run()


class EtcdWatcherManager:
    """
    ETCD 集群 watch（监听线程永不永久退出，仅 stop() 时结束）：
    - all_addrs 轮询 failover
    - gRPC keepalive + progress_notify + stall 检测，避免半开连接静默卡死
    - 断连重连后全量补偿
    - 同 key PUT debounce（watch 线程内延迟执行，不用 Timer 嵌套线程）
    - 事件处理异常不打断 watch
    - 外层 supervised 循环 + supervisor 巡检，线程意外退出立即拉起
    - 全链路日志带 tid/pid，覆盖 etcd3 callback 线程与 watch 线程

    说明：业务版本 key 是永久 PUT，不使用 etcd Lease；所谓「续约」指 Watch/gRPC 保活。
    """

    _SUPERVISOR_INTERVAL = 5.0

    def __init__(
        self,
        prefixes: List[str],
        endpoints: Optional[Sequence] = None,
        host: Optional[str] = None,
        port: Optional[int] = None,
        retry_base: float = 1.0,
        retry_max: float = 20.0,
        jitter: float = 0.5,
        debounce_seconds: float = _WATCH_DEBOUNCE_SECONDS,
        stall_seconds: float = _WATCH_STALL_SECONDS,
    ):
        if endpoints is not None:
            self.endpoints = _normalize_endpoints(endpoints)
        elif host:
            self.endpoints = [(str(host), int(port or 2379))]
        else:
            self.endpoints = get_etcd_endpoints()

        if not self.endpoints:
            raise ValueError("ETCD endpoints empty: set ETCD_CONFIG.all_addrs or host/port")

        self.prefixes = list(prefixes)
        self.retry_base = retry_base
        self.retry_max = retry_max
        self.jitter = jitter
        self.debounce_seconds = debounce_seconds
        self.stall_seconds = stall_seconds

        self._stop_event = threading.Event()
        self._started = False
        self._lock = threading.Lock()
        self._endpoint_idx = 0
        self._endpoint_lock = threading.Lock()

        self._debounce_lock = threading.Lock()
        # key -> due_at(monotonic)；在 watch 线程内轮询触发，避免 Timer 嵌套线程
        self._pending_reloads = {}

        # prefix -> watch thread；supervisor 负责发现死亡并重启
        self._watch_threads = {}
        self._supervisor_thread = None

        self.name = "etcd-watcher-manager"

    def _calc_backoff(self, retry_count: int) -> float:
        delay = min(self.retry_base * (2 ** retry_count), self.retry_max)
        delay += random.uniform(0, self.jitter)
        return delay

    def _pick_endpoint(self) -> Endpoint:
        with self._endpoint_lock:
            if not self.endpoints:
                raise RuntimeError("ETCD endpoints empty")
            endpoint = self.endpoints[self._endpoint_idx % len(self.endpoints)]
            self._endpoint_idx = (self._endpoint_idx + 1) % len(self.endpoints)
            return endpoint

    def _reload_for_key(self, key: str) -> None:
        cfg = settings["ETCD_CONFIG"]
        _etcd_info("[ETCD] reload start key=%s", key)
        if key == cfg["rule_key"]:
            load_qa_rule()
            notify_cache_reload("rule")
        elif key == cfg["config_key"]:
            load_cache()
            notify_cache_reload("config")
        _etcd_info("[ETCD] reload done key=%s", key)

    def _flush_due_reloads(self) -> None:
        """在当前 watch 线程执行到期的 debounce 刷新（无额外 Timer 线程）。"""
        if self._stop_event.is_set():
            return
        now = time.monotonic()
        due_keys = []
        with self._debounce_lock:
            for key, due_at in list(self._pending_reloads.items()):
                if now >= due_at:
                    due_keys.append(key)
                    self._pending_reloads.pop(key, None)
        for key in due_keys:
            _etcd_info("[ETCD] debounce flush key=%s", key)
            try:
                self._reload_for_key(key)
            except Exception:
                _etcd_exception("[ETCD] reload failed key=%s", key)

    def _schedule_reload(self, key: str) -> None:
        """同 key 在 debounce 窗口内多次 PUT，只触发一次刷新（同线程延迟执行）。"""
        if self.debounce_seconds <= 0:
            try:
                self._reload_for_key(key)
            except Exception:
                _etcd_exception("[ETCD] reload failed key=%s", key)
            return

        due_at = time.monotonic() + self.debounce_seconds
        with self._debounce_lock:
            self._pending_reloads[key] = due_at
        _etcd_info(
            "[ETCD] debounce schedule key=%s after=%.2fs",
            key, self.debounce_seconds,
        )

    def _compensate_missed_events(self, prefix: str) -> None:
        """
        watch 断流后重连成功：按 prefix 对应 key 全量补偿一次。
        无法从事件流补齐断连期间 PUT，只能主动 reload。
        """
        cfg = settings["ETCD_CONFIG"]
        try:
            if prefix == cfg.get("rule_key"):
                _etcd_info("[ETCD][%s] reconnect compensate: load_qa_rule", prefix)
                load_qa_rule()
                notify_cache_reload("rule")
            elif prefix == cfg.get("config_key"):
                _etcd_info("[ETCD][%s] reconnect compensate: load_cache", prefix)
                load_cache()
                notify_cache_reload("config")
            else:
                _etcd_info("[ETCD][%s] reconnect compensate: load_cache+rule", prefix)
                load_cache()
                load_qa_rule()
                notify_cache_reload("all")
        except Exception:
            _etcd_exception("[ETCD][%s] reconnect compensate failed", prefix)

    def _handle_event(self, prefix: str, event) -> None:
        key = event.key.decode() if event.key else ""
        event_type = type(event).__name__
        if isinstance(event, etcd3.events.PutEvent):
            val = event.value.decode() if event.value else ""
            _etcd_info("[ETCD][%s] PUT type=%s %s=%s", prefix, event_type, key, val)
            cfg = settings["ETCD_CONFIG"]
            if key in (cfg["rule_key"], cfg["config_key"]):
                self._schedule_reload(key)
            else:
                _etcd_info("[ETCD][%s] PUT ignored key=%s (not config/rule)", prefix, key)
        elif isinstance(event, etcd3.events.DeleteEvent):
            _etcd_info("[ETCD][%s] DELETE type=%s %s", prefix, event_type, key)
        else:
            _etcd_info("[ETCD][%s] OTHER type=%s %s", prefix, event_type, key)

    def _consume_watch_responses(self, client, prefix: str, on_established=None) -> None:
        """
        使用 callback + 本地队列消费 WatchResponse。
        创建 watch 成功后调用 on_established；创建失败（WatchTimedOut 等）不回调、不补偿。
        debounce 在本线程轮询触发，避免 Timer 嵌套线程。
        """
        response_q = queue.Queue()
        last_activity = time.monotonic()

        def _callback(response_or_err):
            # 运行在 etcd3 内部 watch callback 线程
            if isinstance(response_or_err, Exception):
                _etcd_info(
                    "[ETCD][%s] callback-thread error=%s",
                    prefix, type(response_or_err).__name__,
                )
            else:
                n_events = len(getattr(response_or_err, "events", None) or [])
                if n_events > 0:
                    _etcd_info(
                        "[ETCD][%s] callback-thread recv events=%s",
                        prefix, n_events,
                    )
            response_q.put(response_or_err)

        watch_id = client.add_watch_prefix_callback(
            prefix, _callback, progress_notify=True,
        )
        if on_established is not None:
            on_established()
        _etcd_info(
            "[ETCD][%s] watch established id=%s progress_notify=on stall=%ss",
            prefix, watch_id, self.stall_seconds,
        )

        try:
            while not self._stop_event.is_set():
                # 先处理到期 debounce，保证与 PUT 同线程
                self._flush_due_reloads()
                try:
                    item = response_q.get(timeout=_WATCH_POLL_SECONDS)
                except queue.Empty:
                    idle = time.monotonic() - last_activity
                    if idle >= self.stall_seconds:
                        raise ConnectionError(
                            "watch stalled: no response/progress for %.1fs" % idle
                        )
                    continue

                last_activity = time.monotonic()
                if item is None:
                    raise ConnectionError("watch canceled")
                if isinstance(item, Exception):
                    raise item

                events = getattr(item, "events", None) or []
                if events:
                    _etcd_info(
                        "[ETCD][%s] watch-thread handle response events=%s",
                        prefix, len(events),
                    )
                for event in events:
                    if self._stop_event.is_set():
                        break
                    try:
                        self._handle_event(prefix, event)
                    except Exception:
                        _etcd_exception("[ETCD][%s] handle event failed", prefix)
                self._flush_due_reloads()
        finally:
            try:
                client.cancel_watch(watch_id)
            except Exception:
                pass
            try:
                response_q.put_nowait(None)
            except Exception:
                pass

    def _watch_session_loop(self, prefix: str) -> None:
        """单轮会话循环：断连则切节点重连，直到 stop。"""
        retry_count = 0
        # 仅当「曾经建立成功的 watch」中断后，下次连上才全量补偿
        need_compensate = False

        while not self._stop_event.is_set():
            client = None
            host = port = None
            watch_established = False

            def _on_established():
                nonlocal watch_established, retry_count
                watch_established = True
                retry_count = 0
                set_etcd_watch_alive(prefix, True)

            try:
                host, port = self._pick_endpoint()
                client = _create_client(host, port)
                if retry_count == 0:
                    _etcd_info(
                        "[ETCD][%s] watch connecting endpoint=%s:%s (cluster=%s)",
                        prefix, host, port,
                        ",".join("%s:%s" % ep for ep in self.endpoints),
                    )
                else:
                    _etcd_info(
                        "[ETCD][%s] watch reconnecting endpoint=%s:%s retry=%s",
                        prefix, host, port, retry_count,
                    )

                if need_compensate:
                    self._compensate_missed_events(prefix)
                    need_compensate = False

                self._consume_watch_responses(
                    client, prefix, on_established=_on_established,
                )

                if self._stop_event.is_set():
                    break
                raise ConnectionError("watch stream ended")

            except Exception as e:
                phase = "stream" if watch_established else "connect"
                count_etcd_watch_error(prefix, type(e).__name__, phase)
                set_etcd_watch_alive(prefix, False)
                if watch_established:
                    # 已建立后断流：下次连上补偿漏事件
                    need_compensate = True
                    retry_count = 0
                else:
                    # 创建失败（超时/连不上）：不刷全量缓存，加大退避
                    need_compensate = False
                    retry_count += 1

                if self._stop_event.is_set():
                    break
                wait_idx = 0 if watch_established else max(retry_count - 1, 0)
                wait_s = self._calc_backoff(wait_idx)
                next_host, next_port = self.endpoints[self._endpoint_idx % len(self.endpoints)]
                _etcd_info(
                    "[ETCD][%s] watch error=%s endpoint=%s:%s, reconnect in %.1fs via %s:%s",
                    prefix, type(e).__name__, host, port, wait_s, next_host, next_port,
                )
                self._stop_event.wait(wait_s)

            finally:
                _safe_close_client(client)

    def _supervised_watch(self, prefix: str) -> None:
        """
        外层永驻循环：_watch_session_loop 任何未预期退出都会被拉起。
        只有 _stop_event 置位后才真正结束。
        """
        restart_count = 0
        while not self._stop_event.is_set():
            try:
                self._watch_session_loop(prefix)
            except BaseException as e:
                if self._stop_event.is_set():
                    break
                count_etcd_watch_error(prefix, type(e).__name__, "connect")
                set_etcd_watch_alive(prefix, False)
                _etcd_exception("[ETCD][%s] watch session crashed", prefix)

            if self._stop_event.is_set():
                break

            wait_s = self._calc_backoff(restart_count)
            restart_count += 1
            _etcd_error(
                "[ETCD][%s] watch loop exited unexpectedly, restart in %.1fs (restart#%s)",
                prefix, wait_s, restart_count,
            )
            self._stop_event.wait(wait_s)

        _etcd_info("[ETCD][%s] supervised watcher stopped", prefix)

    def _spawn_watch_thread(self, prefix: str) -> None:
        thread = threading.Thread(
            target=self._supervised_watch,
            args=(prefix,),
            name="etcd-watch-%s" % prefix.replace("/", "_"),
            daemon=True,
        )
        self._watch_threads[prefix] = thread
        thread.start()
        _etcd_info("[ETCD][%s] watch thread started tid=%s", prefix, thread.ident)

    def _supervisor_loop(self) -> None:
        """定期检查各 prefix 的 watch 线程是否存活，挂了就重启。"""
        _etcd_info("[ETCD] supervisor started interval=%.1fs", self._SUPERVISOR_INTERVAL)
        while not self._stop_event.is_set():
            if self._stop_event.wait(self._SUPERVISOR_INTERVAL):
                break
            with self._lock:
                if not self._started or self._stop_event.is_set():
                    break
                for prefix in self.prefixes:
                    thread = self._watch_threads.get(prefix)
                    if thread is not None and thread.is_alive():
                        continue
                    count_etcd_watch_error(prefix, "ThreadDead", "connect")
                    set_etcd_watch_alive(prefix, False)
                    _etcd_error(
                        "[ETCD][%s] watch thread dead (alive=%s), respawning",
                        prefix, bool(thread and thread.is_alive()),
                    )
                    try:
                        self._spawn_watch_thread(prefix)
                    except Exception:
                        _etcd_exception("[ETCD][%s] respawn failed", prefix)
        _etcd_info("[ETCD] supervisor stopped")

    def start(self) -> None:
        with self._lock:
            if self._started:
                return
            self._stop_event.clear()
            self._started = True

            for prefix in self.prefixes:
                set_etcd_watch_alive(prefix, False)
                self._spawn_watch_thread(prefix)

            self._supervisor_thread = threading.Thread(
                target=self._supervisor_loop,
                name="etcd-watch-supervisor",
                daemon=True,
            )
            self._supervisor_thread.start()

            _etcd_info(
                "[ETCD] all watchers started endpoints=%s prefixes=%s",
                ",".join("%s:%s" % ep for ep in self.endpoints),
                self.prefixes,
            )

    def stop(self, join_timeout: float = 3.0) -> None:
        with self._lock:
            if not self._started:
                return
            self._started = False
            self._stop_event.set()
            with self._debounce_lock:
                self._pending_reloads.clear()
            threads = list(self._watch_threads.values())
            supervisor = self._supervisor_thread
            self._watch_threads.clear()
            self._supervisor_thread = None

        if supervisor is not None:
            threads.append(supervisor)
        for t in threads:
            try:
                t.join(timeout=join_timeout)
            except Exception:
                pass
        _etcd_info("[ETCD] all watchers stopped")


def update_version(key, etcd_list=None):
    """
    向 ETCD 集群写入版本号。
    集群内只需成功 PUT 任意一个可达节点一次（会复制到其它节点）；
    写满 all_addrs 会导致同一 revision 被 watch 多次、缓存连刷。
    """
    endpoints = get_etcd_endpoints(etcd_list)
    if not endpoints:
        _etcd_error("[ETCD] update_version skipped: empty endpoints key=%s", key)
        return

    update_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    last_err = None
    for host, port in endpoints:
        client = None
        try:
            client = _create_client(host, port)
            client.put(key, update_time)
            _etcd_info(
                "[ETCD] put ok key=%s value=%s endpoint=%s:%s tried=%s",
                key, update_time, host, port,
                ",".join("%s:%s" % ep for ep in endpoints),
            )
            return
        except Exception as err:
            last_err = err
            _etcd_info("[ETCD] put fail endpoint=%s:%s key=%s error=%s", host, port, key, err)
        finally:
            if client is not None:
                try:
                    client.close()
                except Exception:
                    pass

    _etcd_error("[ETCD] put failed on all endpoints key=%s last_error=%s", key, last_err)


def _should_bump_etcd_version(result):
    """仅在业务成功时刷新 ETCD 版本，避免参数校验失败等触发全量 load_cache。"""
    if result is True:
        return True
    if result is False or result is None:
        return False
    if hasattr(result, "get_json"):
        data = result.get_json(silent=True)
        if isinstance(data, dict):
            return data.get("code") == "0000"
    return False


def update_etcd(key, etcd_list=None):
    def decorator(view):
        @functools.wraps(view)
        def wrapped_view(*args, **kwargs):
            result = view(*args, **kwargs)
            if _should_bump_etcd_version(result):
                update_version(key, etcd_list)
            return result
        return wrapped_view
    return decorator
