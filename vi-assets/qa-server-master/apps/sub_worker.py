import datetime
import json
import logging
import os
import threading
import time
import traceback

import requests

from apps.prometheus import count_result_write_error
from config import settings
from models import QaDetail, DccQaDetail, QaSegment
from utils.clickhouse import select_in_ck
from utils.redis import Redis

logger = logging.getLogger("flush")
# 单次批量处理数据上限
MAX_LENGTH = 200000
LOCK_TIMEOUT = 600
# 单次 flush 任务最大执行时间（秒），超时后释放锁以便 scheduler 继续调度
JOB_TIMEOUT = 1800
STREAM_LOAD_TIMEOUT = 60
# 客户端超时后轮询 StarRocks label 状态
LOAD_STATE_QUERY_TIMEOUT = 30
LOAD_STATE_POLL_INTERVAL = 10
LOAD_STATE_POLL_MAX = 18

_LOAD_SUCCESS_STATES = frozenset({"FINISHED", "FINISH"})
_LOAD_FAIL_STATES = frozenset({"CANCELLED"})

# flush 目标：mid=中间号 qa_detail，dcc=DCC qa_detail，vi=分片 qa_segment
_FLUSH_TARGETS = {
    "mid": {
        "engine": "mid_strategy",
        "key_prefix": "QASUBRESULT",
        "last_time": "QASUBRESULT:latest",
        "lock_name": "QASUBRESULT:lock",
        "model": QaDetail,
        "is_dcc": False,
        "file_prefix": "",
    },
    "dcc": {
        "engine": "dcc_strategy",
        "key_prefix": "DCCQASUBRESULT",
        "last_time": "DCCQASUBRESULT:latest",
        "lock_name": "DCCQASUBRESULT:lock",
        "model": DccQaDetail,
        "is_dcc": True,
        "file_prefix": "DCC",
    },
    "vi": {
        "engine": "mid_strategy_segment",
        "key_prefix": "VIQASUBRESULT",
        "last_time": "VIQASUBRESULT:latest",
        "lock_name": "VIQASUBRESULT:lock",
        "model": QaSegment,
        "is_dcc": False,
        "file_prefix": "VI",
    },
}


# DCC qa_detail 非空列：URL/文本质检无话单维度时补默认值，避免 stream load 过滤
_DCC_NOT_NULL_DEFAULTS = {
    "dcc_mobile_id": "",
    "dcc_channel_id": "",
    "dcc_app_id": "",
    "dcc_user_id": 0,
    "dcc_platform_id": 0,
    "dcc_scene": 0,
    "is_media_channel_reversed": 0,
}


def _normalize_row_for_stream_load(row, is_dcc):
    """入库前清洗：空串时间→null；DCC 非空列缺省补默认值。"""
    if row.get("call_time") == "":
        row["call_time"] = None
    if row.get("user_data") is None:
        row["user_data"] = ""
    if is_dcc:
        for key, default in _DCC_NOT_NULL_DEFAULTS.items():
            if row.get(key) is None:
                row[key] = default
    return row


def _build_stream_load_label(database, table, minute, chunk=None):
    # label 只与「分钟 + 分片下标」绑定，保证重试时同一批数据用同一 label，
    # 借助 StarRocks 的 Label Already Exists 去重，实现 exactly-once。
    if chunk is None:
        return f"load_{database}_{table}_{minute}"
    return f"load_{database}_{table}_{minute}_{chunk}"


def _is_label_already_used(result):
    """
    同 label 重试时，部分 StarRocks 版本返回 Status=Label Already Exists；
    另一些版本返回 Status=Fail 且 Message 含 already been used（见 starrocks#57982）。
    """
    status = result.get("Status") or ""
    if status in ("Label Already Exists", "LABEL_ALREADY_EXISTS"):
        return True
    message = (result.get("Message") or "").lower()
    return "has already been used" in message or "label already exists" in message


def _query_load_state(label):
    sql = "select * from information_schema.loads where LABEL = :label"
    result_list = select_in_ck(sql, params={"label": label}, engine="mid_strategy_hs")
    if not result_list:
        return None
    # 同 label 可能有多条（历史 CANCELLED + 成功 FINISHED），优先成功态
    for row in result_list:
        if (row.get("STATE") or "").upper() in _LOAD_SUCCESS_STATES:
            return row
    return result_list[0]


def _poll_load_state(label, engine):
    for attempt in range(1, LOAD_STATE_POLL_MAX + 1):
        loads_log = _query_load_state(label)
        state = ((loads_log.get("STATE") if loads_log else None) or "").upper()
        if state in _LOAD_SUCCESS_STATES:
            logger.info("[%s] label %s finished with state=%s", engine, label, state)
            return True
        if state in _LOAD_FAIL_STATES:
            err_msg = loads_log.get("ERROR_MSG")
            logger.warning("[%s] label %s failed, error_msg=%s", engine, label, err_msg)
            return False
        logger.info(
            "[%s] label %s state=%s, poll %s/%s",
            engine, label, state or None, attempt, LOAD_STATE_POLL_MAX,
        )
        time.sleep(LOAD_STATE_POLL_INTERVAL)
    logger.warning("[%s] label %s poll timeout", engine, label)
    return False


def _unlink_quietly(file_path):
    if not file_path:
        return
    try:
        os.remove(file_path)
    except FileNotFoundError:
        pass
    except OSError:
        logger.warning("failed to remove temp file: %s", file_path, exc_info=True)


def _handle_stream_load_result(result, label, engine):
    status = result.get("Status")
    if status == "Success" or _is_label_already_used(result):
        logger.info(
            "[%s] import success! label=%s Status=%s NumberLoadedRows=%s Message=%s",
            engine, label, status, result.get("NumberLoadedRows"), result.get("Message"),
        )
        return True
    if status == "Publish Timeout":
        logger.info("[%s] stream load status=%s, verifying label=%s", engine, status, label)
        return _poll_load_state(label, engine)
    logger.info(
        "[%s] import fail! label=%s Message=%s ErrorURL=%s",
        engine, label, result.get("Message"), result.get("ErrorURL"),
    )
    return False


def _stream_load_put(session, url, headers, file_content):
    response = session.put(
        url=url, headers=headers, data=file_content,
        timeout=STREAM_LOAD_TIMEOUT, allow_redirects=False,
    )
    if response.status_code == 307:
        redirect_url = response.headers.get("Location")
        if not redirect_url:
            return None, "fail to get redirect url"
        logger.info("stream load redirect to: %s", redirect_url)
        response = session.put(
            url=redirect_url, headers=headers, data=file_content,
            timeout=STREAM_LOAD_TIMEOUT,
        )
    return response, None


def import_data_via_client(filename, data_list, engine="mid_strategy", minute=None, chunk=None):
    csv_dir = settings["CSV_PATH"]
    os.makedirs(csv_dir, exist_ok=True)
    stem = os.path.splitext(os.path.basename(filename))[0]
    file_path = os.path.join(
        csv_dir, f"{stem}_{os.getpid()}_{threading.get_ident()}.json",
    )
    try:
        with open(file_path, "w", encoding="utf-8") as f:
            json.dump(data_list, f, ensure_ascii=False, indent=2)
        logger.info(f"[{engine}] succeed in saving file:{file_path}")

        client_config = settings["STARROCKS_CLIENT_PARAMS"].get(engine)
        host = client_config["host"]
        user = client_config["user"]
        password = client_config["password"]
        port = client_config["port"]
        database = client_config["database"]
        table = client_config["table"]
        url = f"http://{host}:{port}/api/{database}/{table}/_stream_load"
        label = _build_stream_load_label(database, table, minute, chunk)
        headers = {
            "label": label,
            "format": "json",
            "strip_outer_array": "true",
            "max_filter_ratio": "0.1",
            "timeout": "300",
            "Expect": "100-continue",
            "ignore_json_size": "true"
        }

        session = requests.Session()
        session.auth = (user, password)

        with open(file_path, "rb") as f:
            file_content = f.read()

        try:
            response, redirect_err = _stream_load_put(session, url, headers, file_content)
            if redirect_err:
                logger.info(f"[{engine}] {redirect_err}, label={label}")
                return False
            if response.status_code == 200:
                return _handle_stream_load_result(
                    response.json(), label, engine,
                )
            logger.info(
                "[%s] http request fail! label=%s StatusCode=%s ResponseText=%s",
                engine, label, response.status_code, response.text[:500],
            )
            return False
        except requests.exceptions.Timeout as err:
            logger.warning("[%s] stream load read timeout, verifying label=%s: %s", engine, label, err)
            return _poll_load_state(label, engine)
        except requests.exceptions.RequestException as err:
            logger.warning("[%s] stream load request error, verifying label=%s: %s", engine, label, err)
            return _poll_load_state(label, engine)
    finally:
        _unlink_quietly(file_path)


def _flush_data_to_db_impl(target, client, rds, lock_name, lock_token):
    cfg = _FLUSH_TARGETS[target]
    end_time = datetime.datetime.now() - datetime.timedelta(minutes=2)
    last_time = cfg["last_time"]
    engine = cfg["engine"]
    key_prefix = cfg["key_prefix"]
    DetailModel = cfg["model"]
    is_dcc = cfg["is_dcc"]
    file_prefix = cfg["file_prefix"]

    with client.pipeline() as pipe:
        pipe.get(last_time)
        lt = pipe.execute()
    current_time = datetime.datetime.strptime(lt[0].decode(), "%Y%m%d%H%M") if lt[0] else end_time
    logger.info("[%s] scan from %s to %s", engine, current_time.strftime("%Y%m%d%H%M"), end_time.strftime("%Y%m%d%H%M"))

    def _commit_minute(next_minute, data_key=None, action=""):
        ok = rds.commit_flush_progress(
            lock_name, lock_token, last_time, next_minute,
            data_key=data_key, client=client,
        )
        if not ok:
            logger.error(
                "[%s] lost lock before %s, skip redis mutation",
                engine, action or "commit",
            )
        return ok

    # 整个 flush 期间持续续期锁，避免长任务锁过期导致并发入库
    with rds.lock_renewal(lock_name, lock_token, LOCK_TIMEOUT):
        while current_time <= end_time:
            opt_time = current_time.strftime("%Y%m%d%H%M")
            key = f"{key_prefix}:{opt_time}"
            next_minute = (current_time + datetime.timedelta(minutes=1)).strftime("%Y%m%d%H%M")

            if not rds.owns_lock(lock_name, lock_token, client=client):
                logger.error("[%s] lost lock before scanning %s, stop", engine, key)
                break

            total = client.llen(key) or 0
            logger.info(f"[{key}]-todoLength:{total}")

            if not total:
                # 该分钟无数据，直接推进游标，不阻塞后续分钟
                if not _commit_minute(next_minute, action=f"advance empty {opt_time}"):
                    break
                current_time += datetime.timedelta(minutes=1)
                continue

            chunk_count = (total + MAX_LENGTH - 1) // MAX_LENGTH
            import_success = True
            for idx in range(chunk_count):
                if not rds.owns_lock(lock_name, lock_token, client=client):
                    logger.error(
                        "[%s] lost lock before import %s chunk %s, stop",
                        engine, opt_time, idx,
                    )
                    import_success = False
                    break
                start = idx * MAX_LENGTH
                end = start + MAX_LENGTH - 1
                data_list = client.lrange(key, start, end)
                new_list = []
                for item in data_list:
                    detail = json.loads(item.decode("utf-8"))
                    detail_model = DetailModel()
                    detail_model.update_attr(detail)
                    new_list.append(_normalize_row_for_stream_load(detail_model.to_dict(), is_dcc))
                data_list = None
                chunk_label = opt_time if chunk_count == 1 else f"{opt_time}_{idx}"
                filename = f"{file_prefix}{chunk_label}.json"
                ok = import_data_via_client(
                    filename, new_list, engine=engine,
                    minute=opt_time, chunk=None if chunk_count == 1 else idx,
                )
                new_list = None
                if not ok:
                    import_success = False
                    break

            if import_success:
                if not _commit_minute(
                    next_minute, data_key=key, action=f"delete and advance {opt_time}",
                ):
                    break
                logger.info(
                    "[%s] imported %s (%d rows), advanced latest to %s",
                    engine, opt_time, total, next_minute,
                )
                current_time += datetime.timedelta(minutes=1)
            else:
                # 超时未确认或真正失败：保留 key 与游标，停止后续分钟处理。
                # 下一轮重试用同一 label；若 Message 含 already been used / Label Already Exists，
                # 视为已成功并推进游标，避免重复入库。
                logger.warning(
                    "[%s] import not confirmed for %s, keep key and stop to avoid mixing minutes",
                    engine, opt_time,
                )
                break


def flush_data_to_db(target="mid"):
    """
    每分钟把 redis 中的子结果数据加载到 StarRocks。
    target: mid | dcc | vi
    """
    # 兼容旧调用 flush_data_to_db(True/False)
    if target is True:
        target = "dcc"
    elif target is False:
        target = "mid"
    if target not in _FLUSH_TARGETS:
        logger.error("unknown flush target: %s", target)
        return

    cfg = _FLUSH_TARGETS[target]
    engine = cfg["engine"]
    lock_name = cfg["lock_name"]
    t0 = time.time()
    logger.info("[%s] flush job triggered, pid=%s", engine, os.getpid())

    rds = Redis()
    lock_token = rds.acquire_lock(lock_name, acquire_timeout=3, lock_timeout=LOCK_TIMEOUT)
    if not lock_token:
        logger.info("[%s] lock not acquired (%s), skip", engine, lock_name)
        return

    logger.info("[%s] lock acquired: %s", engine, lock_name)
    client = rds.get_connection()
    work_error = []

    def _run():
        try:
            _flush_data_to_db_impl(target, client, rds, lock_name, lock_token)
        except Exception as err:
            work_error.append(err)
            logger.info(traceback.format_exc())
            logger.error("[%s] flush error: %s", engine, err)
        finally:
            try:
                client.close()
            except Exception:
                pass

    worker = threading.Thread(target=_run, name=f"flush-{engine}-worker", daemon=True)
    worker.start()
    worker.join(timeout=JOB_TIMEOUT)

    timed_out = worker.is_alive()
    if timed_out:
        logger.error(
            "[%s] job timeout after %ss, releasing lock; worker must not mutate redis after lock loss",
            engine, JOB_TIMEOUT,
        )
        count_result_write_error()
    elif work_error:
        count_result_write_error()

    released = rds.release_lock(lock_name, lock_token)
    logger.info("[%s] lock released=%s, elapsed=%.1fs", engine, released, time.time() - t0)
