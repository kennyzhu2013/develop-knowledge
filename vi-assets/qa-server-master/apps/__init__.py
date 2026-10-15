import datetime
import hashlib
import logging
import math
import traceback

from flask import make_response, request, g, Response, jsonify
from werkzeug.exceptions import HTTPException

from app import app
from apps.prometheus import count_request, count_request_error, count_response_code
from utils.cache import get_company_secret
from utils.helpers import is_intranet_network
from utils.redis import Redis

WHITE_IP = "QASERVER:WHITEIP"
WHITE_URL = "QASERVER:WHITEURL"
logger = logging.getLogger("all")

_AUTH_TIMESTAMP_MAX_SKEW = 300


def _forbidden_response():
    response = make_response("403 Forbidden")
    response.status_code = 403
    return response


def _get_client_ip():
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        ip = forwarded.split(",")[0].strip()
        if ip:
            return ip
    return (request.remote_addr or "").strip() or "0.0.0.0"


def _parse_authorization(header):
    """
    解析 EOPAUTH 鉴权头。格式: EOPAUTH appid="...", timestamp="...", signature="..."
    解析失败返回 None。
    """
    if not header:
        return None
    parts = [p.strip() for p in header.split(",")]
    if len(parts) != 3:
        return None
    try:
        scheme_part = parts[0]
        if not scheme_part.upper().startswith("EOPAUTH"):
            return None
        if "=" not in scheme_part:
            return None
        app_key = scheme_part.split("=", 1)[1].strip().strip('"')
        if "=" not in parts[1] or "=" not in parts[2]:
            return None
        timestamp = parts[1].split("=", 1)[1].strip().strip('"')
        signature = parts[2].split("=", 1)[1].strip().strip('"')
    except (IndexError, AttributeError):
        return None
    if not (app_key and timestamp and signature):
        return None
    return app_key, timestamp, signature


def _is_timestamp_in_window(timestamp):
    if not timestamp.isdigit() or len(timestamp) > 10:
        return False
    try:
        request_ts = int(timestamp)
    except ValueError:
        return False
    now_ts = math.ceil(datetime.datetime.now().timestamp())
    time_delta = now_ts - request_ts
    return 0 <= time_delta <= _AUTH_TIMESTAMP_MAX_SKEW


def _compute_signature(timestamp, secret):
    md5 = hashlib.md5()
    md5.update(timestamp.encode("utf-8"))
    md5.update(secret.encode("utf-8"))
    return md5.hexdigest().upper()


def _get_task_callback_urls():
    """从 JSON 请求体读取任务回调地址；非 JSON 或解析失败时返回 (None, None)。"""
    if request.method not in ("POST", "PUT", "PATCH"):
        return None, None
    content_type = (request.content_type or "").split(";", 1)[0].strip().lower()
    if content_type and content_type != "application/json":
        return None, None
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return None, None
    sub_url = data.get("subTaskCallBackUrl")
    main_url = data.get("taskCallBackUrl")
    if not isinstance(sub_url, str) or not sub_url:
        sub_url = None
    if not isinstance(main_url, str) or not main_url:
        main_url = None
    return sub_url, main_url


def is_white_ip(ip):
    """
    查看请求ip是否已加白名单
    :param ip:
    :return:
    """
    with Redis.get_connection() as client:
        if client.sismember(WHITE_IP, ip):
            return True
        ip_segments = ip.split(".")
        if len(ip_segments) < 3:
            return False
        ip3 = "{}.{}.{}.*".format(ip_segments[0], ip_segments[1], ip_segments[2])
        if client.sismember(WHITE_IP, ip3):
            client.sadd(WHITE_IP, ip)
            return True
        ip2 = "{}.{}.*".format(ip_segments[0], ip_segments[1])
        if client.sismember(WHITE_IP, ip2):
            client.sadd(WHITE_IP, ip)
            return True
        return False


def is_white_url(url):
    """
    查看请求ip是否已加白名单
    :param url:
    :return:
    """
    with Redis.get_connection() as client:
        if client.sismember(WHITE_URL, url):
            return True
        return False


@app.before_request
def prepare():
    if request.method == "OPTIONS":
        return None

    request_ip = _get_client_ip()
    if request.path.startswith("/manage"):
        if not is_intranet_network(request_ip):
            logger.info("manage forbidden outside internal network: %s", request_ip)
            return _forbidden_response()
        return None

    if not is_white_ip(request_ip):
        logger.info("IP forbidden: %s", request_ip)
        return _forbidden_response()

    auth = _parse_authorization(request.headers.get("Authorization"))
    if not auth:
        logger.info("invalid or missing Authorization header from %s", request_ip)
        return _forbidden_response()

    app_key, timestamp, signature = auth
    if not _is_timestamp_in_window(timestamp):
        logger.info("timestamp out of window for app_key=%s", app_key)
        return _forbidden_response()

    secret = get_company_secret(app_key=app_key)
    if not secret:
        logger.info("no company info for app_key=%s", app_key)
        return _forbidden_response()

    g.app_key = app_key
    # HTTPS 模式: MD5($timestamp$secret)，不计算 http body
    expected_signature = _compute_signature(timestamp, secret)
    if signature != expected_signature:
        logger.info("signature mismatch for app_key=%s, expected_signature=%s, signature=%s", app_key, expected_signature, signature)
        return _forbidden_response()

    sub_url, main_url = _get_task_callback_urls()
    if sub_url and not is_white_url(sub_url):
        logger.info("subTaskCallBackUrl forbidden: %s", sub_url)
        return _forbidden_response()
    if main_url and not is_white_url(main_url):
        logger.info("taskCallBackUrl forbidden: %s", main_url)
        return _forbidden_response()
    return None


@app.after_request
def finish(response: Response):
    app_info = g.app_info if hasattr(g, "app_info") else {}
    app_id = app_info.get("app_id", "")
    task_id = ""
    request_id = ""
    if request.method == "POST":
        payload = request.get_json(silent=True) or {}
        request_id = payload.get("requestId", "") if isinstance(payload, dict) else ""
    if response.status_code == 200:
        response_data = response.get_json(silent=True)
        task_id = response_data.get("taskId", "") if isinstance(response_data, dict) else ""
        if isinstance(response_data, dict) and "code" in response_data:
            count_response_code(response_data.get("code"))
        logger.info(f"[{task_id}]-requestId:{request_id}-app_id:{app_id}-responseData:{response_data}")
    else:
        logger.info(f"[{request_id}]-app_id:{app_id}-responseCode:{response.status_code}")
    # 多进程启动，记录每次收到请求的端口
    port = request.server[1]
    server = request.server[0]
    instance = f"{server}:{port}"
    count_request(status_code=response.status_code, instance=instance)
    return response


@app.errorhandler(Exception)
def handle_exception(e):
    app_info = g.app_info if hasattr(g, "app_info") else {}
    app_id = app_info.get("app_id", "")
    count_request_error(app_id=app_id)
    logger.error(f"app_id:{app_id}-requestError:{traceback.format_exc()}")
    # 404：路由未匹配，返回 404 响应
    if isinstance(e, HTTPException) and e.code == 404:
        return jsonify(code="9999", message="404 Not Found"), 404
    # 其余异常统一返回 500 错误响应
    return jsonify(code="9999", message="处理出错"), 500
