import copy
import dataclasses
import datetime
import hashlib
import decimal
import json
import logging
import typing as t
import uuid

import IPy
import redis

from utils.redis import Redis

QA_INFO_EXPIRATION = 50*3600
GZH_LIST = ["GZ001", "GZ002"]
logger = logging.getLogger("all")


def md5(txt):
    """md5"""
    m = hashlib.md5()
    m.update(txt.encode("utf-8"))
    return m.hexdigest()


def encrypt_id(company_id):
    mapper = ["acd", "bfg", "cjk", "dop", "eqz",
              "fyt", "gmx", "hvn", "jpw", "kri"]
    id_list = list(str(company_id))
    res = ""
    for n in id_list:
        res = res + mapper[int(n)]
    return res


def is_intranet_network(ip: str) -> bool:
    """
    判断ip是否为内网地址
    以下IP段的地址都是内网IP地址。
        （1）10.0.0.0 到 10.255.255.255
        （2）172.16.0.0 到172.31.255.255
        （3）192.168.0.0 到192.168.255.255
    """
    if ip in IPy.IP("10.0.0.0/8"):
        return True
    elif ip in IPy.IP("172.16.0.0/12"):
        return True
    elif ip in IPy.IP("192.168.0.0/16"):
        return True
    else:
        return False


def _default(o: t.Any) -> t.Any:
    if hasattr(o, "__table__"):
        ret = copy.deepcopy(o.__dict__)
        ret.pop("_sa_instance_state")
        return ret
    if isinstance(o, datetime.datetime):
        # 格式化时间
        return o.strftime("%Y-%m-%d %H:%M:%S")
    if isinstance(o, datetime.date):
        # 格式化日期
        return o.strftime("%Y-%m-%d")
    if isinstance(o, (decimal.Decimal, uuid.UUID)):
        return str(o)
    if isinstance(o, bytes):
        # 格式化字节数据
        return o.decode("utf-8")
    if dataclasses and dataclasses.is_dataclass(o):
        return dataclasses.asdict(o)
    if hasattr(o, "__html__"):
        return str(o.__html__())
    raise TypeError(f"Object of type {type(o).__name__} is not JSON serializable")


class JsonEncoder(json.JSONEncoder):
    default = staticmethod(_default)
    # 返回中文原文
    ensure_ascii = False


REQUEST_DEDUP_TTL = 120


def request_dedup_guard(request_id, with_delete=False, prefix="AIQC_REQUEST_", ttl=REQUEST_DEDUP_TTL):
    """
    请求去重：同一 requestId 在 TTL 内仅允许通过一次（SET NX 原子写入）。

    Returns:
        None: 非重复请求，可继续处理
        (code, message): 应拒绝请求（1001 重复 / 9999 Redis 不可用，fail-closed）
    """
    if not request_id:
        return None

    request_key = prefix + request_id
    cache = Redis.get_connection()
    try:
        if with_delete:
            if cache.get(request_key):
                cache.delete(request_key)
                return "1001", "重复请求"
            if cache.set(request_key, request_id, ex=ttl, nx=True):
                return None
            return "1001", "重复请求"

        if cache.set(request_key, request_id, ex=ttl, nx=True):
            return None
        return "1001", "重复请求"
    except (ConnectionError, TimeoutError, redis.RedisError) as err:
        logger.error("request dedup redis error, key=%s: %s", request_key, err, exc_info=True)
        return "9999", "服务暂不可用，请稍后重试"
    finally:
        cache.close()
