import logging
import os
import threading
import time
from contextlib import contextmanager

import redis

logger = logging.getLogger("all")

_RENEW_LOCK_SCRIPT = """
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('expire', KEYS[1], ARGV[2])
else
    return 0
end
"""

_RELEASE_LOCK_SCRIPT = """
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
"""

# 仅在仍持有锁时推进 flush 游标；ARGV[3] 非空则同时删除该分钟 list
_COMMIT_FLUSH_SCRIPT = """
if redis.call('get', KEYS[1]) ~= ARGV[1] then
    return 0
end
redis.call('set', KEYS[2], ARGV[2])
if ARGV[3] ~= '' then
    redis.call('del', ARGV[3])
end
return 1
"""


class Redis(object):
    __pool__ = None

    @classmethod
    def create_pool(cls, settings):
        return redis.ConnectionPool(
            host=settings["HOST"],
            port=settings["PORT"],
            db=settings["DB"],
            password=settings["PASSWORD"],
            max_connections=settings["MAX_CONNECTIONS"],
            socket_connect_timeout=settings["SOCKET_CONNECT_TIMEOUT"],
            socket_timeout=settings["SOCKET_TIMEOUT"],
            retry_on_timeout=settings["RETRY_ON_TIMEOUT"],
            health_check_interval=settings["HEALTH_CHECK_INTERVAL"],
        )

    @classmethod
    def init(cls, settings):
        cls.__pool__ = cls.create_pool(settings)
        logger.info("redis host: {}, port: {}".format(settings["HOST"], settings["PORT"]))

    @classmethod
    def get_connection(cls):
        if cls.__pool__ is None:
            raise Exception("redis pool has not been initiated")
        return redis.Redis(connection_pool=cls.__pool__)

    @classmethod
    def acquire_lock(cls, lock_name, acquire_timeout=10, lock_timeout=180):
        """
        获取分布式锁，成功返回 lock_token，失败返回 None。
        """
        r = cls.get_connection()
        end = time.time() + acquire_timeout
        expire_seconds = int(lock_timeout)
        while time.time() < end:
            lock_token = f"{time.time()}:{os.getpid()}"
            if r.set(lock_name, lock_token, nx=True, ex=expire_seconds):
                r.close()
                return lock_token

            # 清理旧实现遗留的无 TTL 锁，或逻辑过期但未删除的锁
            raw = r.get(lock_name)
            if raw is not None:
                try:
                    if time.time() > float(raw):
                        r.delete(lock_name)
                        continue
                except (ValueError, TypeError):
                    pass
                if r.ttl(lock_name) == -1:
                    r.delete(lock_name)
                    continue

            time.sleep(0.01)
        r.close()
        return None

    @classmethod
    def renew_lock(cls, lock_name, lock_token, lock_timeout=180):
        """
        续期锁，仅持有者可续期。
        """
        r = cls.get_connection()
        try:
            result = r.eval(
                _RENEW_LOCK_SCRIPT, 1, lock_name, lock_token, int(lock_timeout)
            )
            return bool(result)
        finally:
            r.close()

    @classmethod
    def release_lock(cls, lock_name, lock_token):
        """
        释放锁，仅持有者可释放。
        """
        r = cls.get_connection()
        try:
            result = r.eval(_RELEASE_LOCK_SCRIPT, 1, lock_name, lock_token)
            return bool(result)
        finally:
            r.close()

    @classmethod
    def owns_lock(cls, lock_name, lock_token, client=None):
        """当前连接上的锁值是否仍是本轮 token。"""
        close = False
        if client is None:
            client = cls.get_connection()
            close = True
        try:
            raw = client.get(lock_name)
            if raw is None:
                return False
            if isinstance(raw, bytes):
                raw = raw.decode()
            return raw == lock_token
        finally:
            if close:
                client.close()

    @classmethod
    def commit_flush_progress(cls, lock_name, lock_token, latest_key, next_minute,
                              data_key=None, client=None):
        """
        原子：仍持锁才 set latest，并可顺带 del 分钟 list。
        超时释放锁后，后台 flush 线程调用此方法会失败，避免与下一轮抢删 Redis。
        """
        close = False
        if client is None:
            client = cls.get_connection()
            close = True
        try:
            result = client.eval(
                _COMMIT_FLUSH_SCRIPT, 2,
                lock_name, latest_key,
                lock_token, next_minute, data_key or "",
            )
            return bool(result)
        finally:
            if close:
                client.close()

    @classmethod
    @contextmanager
    def lock_renewal(cls, lock_name, lock_token, lock_timeout=180):
        """
        长任务期间后台续期，防止锁过期导致并发入库。
        """
        interval = max(int(lock_timeout) // 3, 10)
        stop_event = threading.Event()

        def renew_loop():
            while not stop_event.wait(interval):
                if not cls.renew_lock(lock_name, lock_token, lock_timeout):
                    logger.warning("failed to renew lock: %s", lock_name)
                    break

        thread = threading.Thread(target=renew_loop, daemon=True)
        thread.start()
        try:
            yield
        finally:
            stop_event.set()
            thread.join(timeout=1)
