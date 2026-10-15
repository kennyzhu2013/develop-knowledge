"""
质检失败重试：每 10 分钟扫描 qa_fail，按批重试, 每批5000条。
"""
import datetime
import json
import logging
import uuid
from collections import defaultdict

from apps.result_consumer import (
    QA_FAIL_ENGINE,
    QA_FAIL_MAX_RETRY,
    QA_FAIL_STATUS_FAILED,
    QA_FAIL_STATUS_PENDING,
    QA_FAIL_STATUS_RETRYING,
)
from apps.service import prepare_scheduler_params, task_mq, task_mq_config
from models import QaFail
from utils.cache import get_info_by_customer_id, get_qa_rule_by_id, rule_to_task_params
from utils.mysql import session_scope
from utils.redis import Redis

logger = logging.getLogger("all")

BATCH_SIZE = 5000
LOCK_NAME = "QAFAIL:retry:lock"
LOCK_TIMEOUT = 600

# _publish_task_retry 返回值
PUBLISH_OK = "ok"
PUBLISH_ABANDON = "abandon"  # 永久无法投递，标记 is_done 以免堵队列
PUBLISH_SKIP = "skip"  # 本轮跳过，下轮再试


def _parse_user_data(raw):
    if not raw:
        return {}
    if isinstance(raw, dict):
        return raw
    try:
        data = json.loads(raw)
        return data if isinstance(data, dict) else {}
    except (TypeError, ValueError, json.JSONDecodeError):
        return {}


def _fetch_retry_batch(limit=BATCH_SIZE, after_id=0):
    """读取待重试记录：回调已到位(status=0)且未达重试上限；after_id 用于跳过本轮已扫过的 id。"""
    with session_scope(QA_FAIL_ENGINE) as ss:
        q = ss.query(QaFail).filter(
            QaFail.is_done == 0,
            QaFail.retry_status == QA_FAIL_STATUS_PENDING,
            QaFail.retry_count < QA_FAIL_MAX_RETRY,
        )
        if after_id:
            q = q.filter(QaFail.id > after_id)
        rows = q.order_by(QaFail.id.asc()).limit(limit).all()
        # 脱离 session，避免后续长逻辑占连接
        return [row.to_dict() for row in rows]


def _mark_retry_submitted(ids):
    """发起重试成功后：retry_status=1，retry_count+1，更新 retry_time。"""
    if not ids:
        return
    now = datetime.datetime.now()
    with session_scope(QA_FAIL_ENGINE) as ss:
        rows = ss.query(QaFail).filter(QaFail.id.in_(ids)).all()
        for row in rows:
            if row.retry_status != QA_FAIL_STATUS_PENDING:
                continue
            row.retry_status = QA_FAIL_STATUS_RETRYING
            row.retry_count = int(row.retry_count or 0) + 1
            row.retry_time = now
            row.update_time = now


def _mark_retry_abandoned(ids):
    """永久无法投递：is_done=1，retry_status=3，不再扫描。"""
    if not ids:
        return
    now = datetime.datetime.now()
    with session_scope(QA_FAIL_ENGINE) as ss:
        rows = ss.query(QaFail).filter(QaFail.id.in_(ids)).all()
        for row in rows:
            if row.is_done:
                continue
            row.is_done = 1
            row.retry_status = QA_FAIL_STATUS_FAILED
            row.update_time = now


def _get_reuse_asr(qa_customer_id):
    """根据 qa_customer_id 取企业配置 is_reuse_asr，对应调度字段 reuse。"""
    if qa_customer_id is None or qa_customer_id == "":
        return 0
    app_info = get_info_by_customer_id(qa_customer_id)
    if not app_info:
        return 0
    return app_info.get("is_reuse_asr", 0) or 0


def _build_sub_payload(row):
    sub = {
        "dataId": row.get("data_id"),
        "url": row.get("url") or "",
        "reuse": _get_reuse_asr(row.get("qa_customer_id")),
    }
    user_data = _parse_user_data(row.get("user_data"))
    if user_data.get("segmentId") is not None:
        sub["segmentId"] = user_data.get("segmentId")
    if user_data.get("endFlag") is not None:
        sub["endFlag"] = user_data.get("endFlag")
    return sub, user_data


def _row_ids(rows):
    return [item["id"] for item in rows if item.get("id") is not None]


def _publish_task_retry(task_id, rows):
    """
    同一 task_id 下多条子结果合并为一次投递。
    返回 PUBLISH_OK / PUBLISH_ABANDON / PUBLISH_SKIP。
    """
    if not rows:
        return PUBLISH_ABANDON

    rule_id = rows[0].get("rule_id")
    rule = get_qa_rule_by_id(rule_id) if rule_id else None
    if not rule:
        logger.info("[qaFailRetry] [%s]-abandon: rule not found rule_id=%s count=%s", task_id, rule_id, len(rows))
        return PUBLISH_ABANDON

    process_id = rule.get("process_id") or ""
    if not process_id:
        logger.info("[qaFailRetry] [%s]-abandon: empty process_id rule_id=%s", task_id, rule_id)
        return PUBLISH_ABANDON

    qa_customer_id = rows[0].get("qa_customer_id")
    app_info = get_info_by_customer_id(qa_customer_id) if qa_customer_id not in (None, "") else {}
    if not app_info:
        logger.info(
            "[qaFailRetry] [%s]-abandon: app_info not found qa_customer_id=%s",
            task_id, qa_customer_id,
        )
        return PUBLISH_ABANDON
    priority = app_info["priority"]

    result_list = []
    sample_user_data = {}
    for row in rows:
        sub, user_data = _build_sub_payload(row)
        if not sub.get("dataId"):
            continue
        result_list.append(sub)
        if user_data and not sample_user_data:
            sample_user_data = user_data

    if not result_list:
        logger.info("[qaFailRetry] [%s]-abandon: empty result list", task_id)
        return PUBLISH_ABANDON

    json_data = rule_to_task_params(rule)
    if sample_user_data:
        json_data["userData"] = sample_user_data
    parameter, err = prepare_scheduler_params(json_data.copy())
    if err:
        logger.info("[qaFailRetry] [%s]-skip: prepare scheduler params err=%s", task_id, err)
        return PUBLISH_SKIP

    # userData.isStream=True 时，调度参数 isStream 仍传 False（重试按非实时）
    is_stream = False
    request_data = {
        "requestId": uuid.uuid1().hex,
        "data": result_list,
        "processId": process_id,
        "priority": priority,
        "transactionId": int(task_id),
        "isStream": is_stream,
        "needTaskCallback": False,
    }
    if parameter:
        request_data["parameter"] = parameter

    task_mq.publish_message(
        exchange=task_mq_config["to_scheduler_exchange"],
        routing_key=task_mq_config["to_scheduler_routing_key"],
        msg=json.dumps(request_data),
    )
    log_body = {k: v for k, v in request_data.items() if k != "data"}
    logger.info("[qaFailRetry] [%s]-dataCount:%s sendToMq:%s", task_id, len(result_list), log_body)
    return PUBLISH_OK


def _process_batch(rows):
    grouped = defaultdict(list)
    for row in rows:
        task_id = str(row.get("task_id") or "")
        if not task_id:
            continue
        grouped[task_id].append(row)

    success_ids = []
    abandon_ids = []
    for task_id, items in grouped.items():
        try:
            result = _publish_task_retry(task_id, items)
        except Exception:
            logger.exception("[qaFailRetry] [%s]-publish failed, count=%s", task_id, len(items))
            continue
        ids = _row_ids(items)
        if result == PUBLISH_OK:
            success_ids.extend(ids)
        elif result == PUBLISH_ABANDON:
            abandon_ids.extend(ids)

    if success_ids:
        _mark_retry_submitted(success_ids)
    if abandon_ids:
        _mark_retry_abandoned(abandon_ids)
    return len(success_ids), len(abandon_ids)


def retry_qa_fail():
    """
    定时入口：分批读取 qa_fail 并重投调度。
    用 after_id 游标扫完全表，避免批内全失败时反复读同一批堵死后续。
    """
    rds = Redis()
    lock_token = rds.acquire_lock(LOCK_NAME, acquire_timeout=3, lock_timeout=LOCK_TIMEOUT)
    if not lock_token:
        logger.info("[qaFailRetry] lock not acquired, skip")
        return
    logger.info("[qaFailRetry] lock acquired, max_retry=%s batch=%s", QA_FAIL_MAX_RETRY, BATCH_SIZE)

    client = rds.get_connection()
    total_published = 0
    total_abandoned = 0
    batches = 0
    after_id = 0
    try:
        with rds.lock_renewal(LOCK_NAME, lock_token, LOCK_TIMEOUT):
            while True:
                if not rds.owns_lock(LOCK_NAME, lock_token, client=client):
                    logger.error("[qaFailRetry] lost lock, stop")
                    break
                rows = _fetch_retry_batch(BATCH_SIZE, after_id=after_id)
                if not rows:
                    break
                batches += 1
                after_id = max(r["id"] for r in rows if r.get("id") is not None)
                published, abandoned = _process_batch(rows)
                total_published += published
                total_abandoned += abandoned
                logger.info(
                    "[qaFailRetry] batch=%s fetched=%s published=%s abandoned=%s after_id=%s",
                    batches, len(rows), published, abandoned, after_id,
                )
    except Exception:
        logger.exception("[qaFailRetry] job failed")
    finally:
        released = rds.release_lock(LOCK_NAME, lock_token)
        try:
            client.close()
        except Exception:
            pass
        logger.info(
            "[qaFailRetry] done batches=%s published=%s abandoned=%s lock_released=%s",
            batches, total_published, total_abandoned, released,
        )
