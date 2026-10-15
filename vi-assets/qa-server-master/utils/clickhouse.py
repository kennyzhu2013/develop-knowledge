import logging
import threading
import time
from contextlib import contextmanager

from sqlalchemy import text, create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

from config import settings

logger = logging.getLogger("all")

# 连续失败后打开熔断，避免 CK 宕机时每个创建请求仍把 5000/批查完
CK_BREAKER_FAILURE_THRESHOLD = 3
CK_BREAKER_RECOVERY_SECONDS = 30


class ClickHouseUnavailable(Exception):
    """CK 熔断打开或探测失败，调用方应快速失败。"""


class _CircuitBreaker:
    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"

    def __init__(self, name, failure_threshold=CK_BREAKER_FAILURE_THRESHOLD,
                 recovery_seconds=CK_BREAKER_RECOVERY_SECONDS):
        self.name = name
        self.failure_threshold = failure_threshold
        self.recovery_seconds = recovery_seconds
        self._lock = threading.Lock()
        self._failures = 0
        self._state = self.CLOSED
        self._opened_at = 0.0

    def allow(self):
        with self._lock:
            if self._state == self.OPEN:
                if time.monotonic() - self._opened_at >= self.recovery_seconds:
                    self._state = self.HALF_OPEN
                    logger.info("clickhouse circuit half-open, probe engine=%s", self.name)
                    return True
                return False
            if self._state == self.HALF_OPEN:
                return False
            return True

    def success(self):
        with self._lock:
            self._failures = 0
            if self._state != self.CLOSED:
                logger.info("clickhouse circuit closed, engine=%s", self.name)
            self._state = self.CLOSED

    def failure(self):
        with self._lock:
            if self._state == self.HALF_OPEN:
                self._state = self.OPEN
                self._opened_at = time.monotonic()
                logger.warning("clickhouse circuit re-opened, engine=%s", self.name)
                return
            self._failures += 1
            if self._failures >= self.failure_threshold:
                self._state = self.OPEN
                self._opened_at = time.monotonic()
                logger.warning(
                    "clickhouse circuit opened after %s failures, engine=%s",
                    self._failures, self.name,
                )


_breakers = {}
_breakers_lock = threading.Lock()


def _breaker_for(engine):
    key = engine or "default"
    with _breakers_lock:
        breaker = _breakers.get(key)
        if breaker is None:
            breaker = _CircuitBreaker(name=key)
            _breakers[key] = breaker
        return breaker


def _engine_config_for(uri):
    driver = str(uri).split("://", 1)[0].split("+", 1)[0]
    if driver == "starrocks":
        return settings["STARROCKS_ENGINE_CONFIG"]
    return settings["CK_ENGINE_CONFIG"]


DEFAULT_CK_ENGINE = create_engine(settings["CK_DATABASE_URI"], **settings["CK_ENGINE_CONFIG"])
CK_ENGINE_DICT = {k: create_engine(v, **_engine_config_for(v)) for k, v in settings["CK_BINDS"].items()}
DEFAULT_CK_SESSION_MAKER = sessionmaker(bind=DEFAULT_CK_ENGINE, expire_on_commit=False, autoflush=False)
CK_SESSION_MAKER_DICT = {k: sessionmaker(bind=v, expire_on_commit=False, autoflush=False) for k, v in CK_ENGINE_DICT.items()}

# base
class CkBase(DeclarativeBase):
    __table_args__ = {'extend_existing': True}


@contextmanager
def ck_session_scope(engine=None):
    """clickhouse连接器"""
    session_maker = CK_SESSION_MAKER_DICT.get(engine, DEFAULT_CK_SESSION_MAKER)
    session = session_maker()
    try:
        yield session
    except:
        raise
    finally:
        session.close()


def select_in_ck(sql, params=None, engine=None):
    breaker = _breaker_for(engine)
    if not breaker.allow():
        raise ClickHouseUnavailable("clickhouse circuit open: %s" % (engine or "default"))
    if params is None:
        params = {}
    try:
        with ck_session_scope(engine) as ss:
            cursor = ss.execute(text(sql), params)
            fields = list(cursor.keys())
            result_list = [dict(zip(fields, item)) for item in cursor.fetchall()]
        breaker.success()
        return result_list
    except ClickHouseUnavailable:
        raise
    except Exception:
        breaker.failure()
        raise
