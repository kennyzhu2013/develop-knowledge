import atexit
import functools
import logging
import threading
from concurrent.futures import ThreadPoolExecutor

from flask import Blueprint, g, jsonify, request
import pypinyin
import requests
from sqlalchemy import delete, func, select

from config import settings
from apps.service import save_scripts, update_scripts, map_scene_codes_to_word_package_ids, clone_qa_rule_for_customer
from models import BizNumAuth, ChannelTab, CompanyTab, CustomerTab, MobileTab, QaEnterprise, QaSubject
from utils.cache import get_info_by_customer_id, get_subject_by_app_id
from utils.etcd import update_etcd
from utils.helpers import request_dedup_guard
from utils.mysql import session_scope
from utils.schema import validate_json
from utils.snowFlake import get_next_id


bp_vi = Blueprint("vi", __name__, url_prefix="/vi")
logger = logging.getLogger("all")
etcd_config = settings["ETCD_CONFIG"]

_num_executor = ThreadPoolExecutor(
    max_workers=settings["ASYNC_WORKER_THREAD_COUNT"],
    thread_name_prefix="vi-num-worker",
)
_shutdown_once = threading.Event()


def _shutdown_num_executor():
    """进程退出时优雅关闭号码操作线程池，等待已提交任务执行完毕。"""
    if _shutdown_once.is_set():
        return
    _shutdown_once.set()
    logger.info("shutting down vi num executor...")
    try:
        _num_executor.shutdown(wait=True, cancel_futures=False)
    except TypeError:
        _num_executor.shutdown(wait=True)
    except Exception:
        logger.exception("shutdown vi num executor failed")
    logger.info("vi num executor shutdown complete")


atexit.register(_shutdown_num_executor)


def _submit_num_operation(data, task_id):
    if _shutdown_once.is_set():
        logger.warning("[%s]-reject num_operation: executor is shutting down", task_id)
        return False
    _num_executor.submit(num_operation, data, task_id)
    return True


def check_customer_info(view):
    """
    对比账户信息是否正确
    """
    @functools.wraps(view)
    def wrapped_view(**kwargs):
        json_data = request.get_json()
        customer_id = json_data.get("enterpriseId")
        app_info = get_info_by_customer_id(customer_id)
        if not app_info:
            return jsonify(code="1002", message="企业不存在")
        if g.app_key != app_info["app_key"]:
            return jsonify(code="1002", message="渠道ID与鉴权信息不一致")
        if app_info["status"] == 3:
            return jsonify(code="1002", message="企业账户已注销")
        return view(**kwargs)
    return wrapped_view


@bp_vi.post("/enterprise/create")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
def create_enterprise():
    schema = {
        "type": "object",
        "required": [
            "requestId", "provinceId", "cityId", "districtId", 
            "enterpriseFullname", "scene", "channelId", "script", "recordChannel"
        ],
        "properties": {
            "requestId": {"type": "string"},
            "provinceId": {"type": "string"},
            "cityId": {"type": "string"},
            "districtId": {"type": "string"},
            "enterpriseFullname": {"type": "string", "minLength": 1},
            "enterpriseNickname": {"type": "string"},  # 可选
            "scene": {
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": ["1", "2", "3", "4", "5", "6", "7", "8", "9"]
                },
                "minItems": 1
            },
            "script": {
                "type": "array",
                "items": {"type": "string"}
            },  # 可选
            "numberList": {
                "type": "array",
                "items": {"type": "string"},
                "minItems": 1
            }, # 可选
            "numberUrl": {"type": "string"}, # 可选
            "recordChannel": {
                "type": "string",
                "enum": ["3", "0", "1", "2"]
            },
            "channelId": {"type": "string", "minLength": 1}
        },
        "additionalProperties": False
    }
    json_data = request.get_json() or {}
    request_id = json_data.get("requestId", "")
    logger.info("[%s]-enterpriseInfo:%s", request_id, json_data)
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1001", message=err_msg)

    reject = request_dedup_guard(request_id, prefix="VI")
    if reject:
        return jsonify(code=reject[0], message=reject[1])
    
    app_id = json_data.get("channelId")
    province_id = json_data.get("provinceId")
    city_id = json_data.get("cityId")
    district_id = json_data.get("districtId")
    fullname = json_data.get("enterpriseFullname")
    nickname = json_data.get("enterpriseNickname")
    nickname = nickname if nickname else fullname
    scene_list = json_data.get("scene")
    script_list = json_data.get("script")
    record_channel = json_data.get("recordChannel")
    num_callback_url = json_data.get("numberUrl")
    try:
        with session_scope("user_qc") as ss:
            subject = ss.execute(select(QaSubject).filter_by(app_id=app_id)).scalars().first()
            # 校验省ID
            mobile = ss.get(MobileTab, province_id)
            if not mobile:
                return jsonify(code="1001", message="provinceId错误")
            # 校验市ID
            enterprise_city = ss.execute(select(QaEnterprise).filter_by(app_id=app_id, region_id=city_id, level="2")).scalars().first()
            if not enterprise_city:
                return jsonify(code="1001", message="cityId错误")
            channel_id = enterprise_city.main_id
            channel = ss.get(ChannelTab, channel_id)
            if not channel:
                return jsonify(code="1002", message="二级移动级未创建")
            if channel.parent_id != mobile.id:
                return jsonify(code="1001", message="省市非父子级关系")
            # 校验区县ID
            enterprise_district = ss.execute(select(QaEnterprise).filter_by(app_id=app_id, region_id=district_id, level="3")).scalars().first()
            if not enterprise_district:
                return jsonify(code="1001", message="districtId错误")
            company_id = enterprise_district.main_id
            company = ss.get(CompanyTab, company_id)
            if not company:
                return jsonify(code="1002", message="接入企业级未创建")
            if company.parent_id != channel.id:
                return jsonify(code="1001", message="市和区县非父子级关系")
            
            if ss.scalar(select(func.count()).select_from(CustomerTab).where(CustomerTab.nickname==nickname)) > 0:
                return jsonify(code="1001", message="该企业账户名称已存在,请使用其他名称")

            need_clone_rule = subject.is_segment != 2 and bool(subject.rule_id)
            word_package_id = ""
            if need_clone_rule:
                word_package_id, missing_scenes = map_scene_codes_to_word_package_ids(scene_list, session=ss)
                if missing_scenes:
                    return jsonify(
                        code="1001",
                        message=f"场景编码未配置词袋映射:{','.join(missing_scenes)}",
                    )

            customer = CustomerTab()
            customer.parent_id = company_id
            customer.app_id = app_id
            customer.name = "".join([item[0] for item in pypinyin.lazy_pinyin(nickname)]).lower() if nickname else ""
            customer.nickname = nickname
            customer.fullname = fullname
            customer.status = 1
            customer.source = 3
            customer.creator = "system"
            customer.num_callback_url = num_callback_url
            customer.qa_type = subject.qa_type
            customer.priority = subject.priority
            customer.is_reuse_asr = subject.is_reuse_asr
            customer.ability = subject.ability
            customer.account_type = subject.account_type
            # recordChannel 0/1/2/3 → qa_direction 3/4/1/2
            customer.qa_direction = {"0": 3, "1": 4, "2": 1, "3": 2}[record_channel]
            customer.scenario_id = ",".join(scene_list)
            if script_list:
                customer.script_id = save_scripts(nickname, script_list, app_id)
            
            customer.is_mid_qc = subject.is_mid_qc
            customer.stream_priority = subject.stream_priority
            customer.is_segment = subject.is_segment

            ss.add(customer)
            ss.flush()
            customer_id = customer.id

            # 非纯话务且 subject 配置了模板规则时，按企业场景/话术克隆 qa_rule
            if need_clone_rule:
                new_rule_id = clone_qa_rule_for_customer(
                    subject.rule_id,
                    app_id=app_id,
                    nickname=nickname,
                    qa_mobile_id=mobile.id,
                    qa_channel_id=channel.id,
                    qa_company_id=company.id,
                    qa_customer_id=customer_id,
                    word_package_id=word_package_id,
                    script_id=customer.script_id,
                )
                if not new_rule_id:
                    raise ValueError(f"模板质检规则不存在:{subject.rule_id}")
                customer.rule_id = new_rule_id
                logger.info(
                    "[%s]-create enterprise rule: customer_id=%s template_rule_id=%s new_rule_id=%s",
                    request_id, customer_id, subject.rule_id, new_rule_id,
                )
    except ValueError as err:
        logger.error("[%s]-create enterprise failed: %s", request_id, err)
        return jsonify(code="1002", message=str(err))
    
    number_list = json_data.get("numberList", [])
    if number_list:
        if len(number_list) >= 10000:
            return jsonify(code="1002", message="号码数量过多(需小于10000)", enterpriseId=customer_id)
        num_data = {
            "enterpriseId": customer_id,
            "numberList": number_list,
            "operateType": "1"
        }
        task_id = str(get_next_id())
        if not _submit_num_operation(num_data, task_id):
            logger.error("[%s]-submit num operation failed: %s", request_id, task_id)
            return jsonify(code="1002", message="号码操作失败，请稍后重试", enterpriseId=customer_id)
        logger.info("[%s]-submit num operation success: %s", request_id, task_id)
        return jsonify(code="0000", message="success", enterpriseId=customer_id, taskId=task_id)
    return jsonify(code="0000", message="success", enterpriseId=customer_id)


@bp_vi.post("/enterprise/modifySceneAndScript")
@check_customer_info
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
def modify_scene_and_script():
    schema = {
        "type": "object",
        "required": ["requestId", "enterpriseId", "scene", "script", "scriptOperateType"],
        "properties": {
            "requestId": {"type": "string"},
            "enterpriseId": {"type": "integer"},
            "scene": {
                "type": "array",
                "items": {
                    "type": "string",
                    "enum": ["1", "2", "3", "4", "5", "6", "7", "8", "9"]
                },
                "minItems": 1
            },
            "script": {
                "type": "array",
                "items": {"type": "string"},
                "minItems": 1
            },
            "scriptOperateType": {
                "type": "string",
                "enum": ["1", "2"]  # 1-新增, 2-删除
            }
        },
        "additionalProperties": False
    }
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    logger.info(f"[{request_id}]-modifySceneAndScript:{json_data}")
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1001", message=err_msg)

    reject = request_dedup_guard(request_id, prefix="VI")
    if reject:
        return jsonify(code=reject[0], message=reject[1])
    
    customer_id = json_data.get("enterpriseId")
    scene = json_data.get("scene")
    script_list = json_data.get("script")
    operate_type = json_data.get("scriptOperateType")
    opt = "1" if operate_type == "1" else "0"
    with session_scope("user_qc") as ss:
        customer = ss.get(CustomerTab, customer_id)
        if not customer:
            return jsonify(code="1001", message="enterpriseId错误")
        # 修改场景
        customer.scenario_id = ",".join(scene)
        # 修改话术
        script_id = customer.script_id
        app_id = customer.app_id
        if not script_id:
            if operate_type == "1":
                customer.script_id = save_scripts(customer.nickname, script_list, app_id)
        else:
            for script in script_list:
                update_result = update_scripts(group_id=script_id, opt=opt, script=script, app_id=app_id)
                logger.info(f"modifyScript result:{update_result}")
    return jsonify(code="0000", message="success")


@bp_vi.post("/enterprise/cancel")
@check_customer_info
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
def cancel_enterprise():
    schema = {
        "type": "object",
        "required": ["requestId", "enterpriseId"],
        "properties": {
            "requestId": {"type": "string"},
            "enterpriseId": {"type": "integer"}
        },
        "additionalProperties": False
    }
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    logger.info(f"[{request_id}]-cancelEnterprise:{json_data}")

    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1001", message=err_msg)
    reject = request_dedup_guard(request_id, prefix="VI")
    if reject:
        return jsonify(code=reject[0], message=reject[1])

    customer_id = json_data.get("enterpriseId")

    with session_scope("user_qc") as ss:
        customer = ss.get(CustomerTab, customer_id)
        if not customer:
            return jsonify(code="1001", message="enterpriseId错误")
        customer.status = 3
    return jsonify(code="0000", message="success")


def push_num_result(url, result, reason, task_id):
    data = {"taskId": task_id, "result": result}
    if reason:
        data["reason"] = reason
    logger.info(f"[{task_id}]-request:{data}")
    resp = requests.post(url=url, json=data, headers={"Content-Type": "application/json"}, timeout=30)
    if resp.status_code == 200:
        resp_json = resp.json()
        logger.info(f"[{task_id}]-response:{resp_json}")
    else:
        logger.info(f"[{task_id}]-statusCode:{resp.status_code}")


@update_etcd(key=etcd_config["number_key"], etcd_list=etcd_config["all_addrs"])
def num_operation(data: dict, task_id: str):
    customer_id = data.get("enterpriseId")
    number_list = data.pop("numberList", [])
    operate_type = data.get("operateType")
    app_info = get_info_by_customer_id(customer_id=customer_id)
    mobile_id = app_info["qa_mobile_id"]
    channel_id = app_info["qa_channel_id"]
    company_id = app_info["qa_company_id"]
    app_id = app_info["app_id"]
    url = app_info["num_callback_url"]
    logger.info(f"[{task_id}]-appId:{app_id}-customerId:{customer_id}-total:{len(number_list)}-requestData:{data}")

    if operate_type == "1":
        with session_scope("user_qc") as ss:
            exist_list = ss.execute(select(BizNumAuth).where(BizNumAuth.num.in_(number_list))).scalars().all()
            if exist_list:
                exist_nums = [item.num for item in exist_list]
                logger.info(f"[{task_id}]-total of existed nums:{len(exist_list)},detail:{exist_nums}")
                push_num_result(url, "0", "存在重复入库号码", task_id)
                return False
            new_list = [BizNumAuth(num, mobile_id, channel_id, company_id, customer_id, 1) for num in number_list]
            ss.add_all(new_list)
            logger.info(f"[{task_id}]-total of added nums:{len(new_list)}")
    else:
        with session_scope("user_qc") as ss:
            total =  ss.scalar(select(func.count()).select_from(BizNumAuth).where(BizNumAuth.num.in_(number_list), BizNumAuth.customer_id==customer_id))
            if total != len(number_list):
                push_num_result(url, "0", "号码信息不对称,请删除多余号码或者错误号码", task_id)
                return
            ss.execute(delete(BizNumAuth).where(BizNumAuth.num.in_(number_list), BizNumAuth.customer_id==customer_id))
            logger.info(f"[{task_id}]-total of deleted nums:{len(number_list)}")
    push_num_result(url, "1", None, task_id)
    return True


@bp_vi.post("/number/addOrDel")
@check_customer_info
def add_or_del_numbers():
    schema = {
        "type": "object",
        "required": ["requestId", "enterpriseId", "numberList", "operateType"],
        "properties": {
            "requestId": {"type": "string"},
            "enterpriseId": {"type": "integer"},
            "numberList": {
                "type": "array",
                "items": {"type": "string"},
                "minItems": 1
            },
            "operateType": {
                "type": "string",
                "enum": ["1", "2"]  # 1-新增, 2-删除
            }
        },
        "additionalProperties": False
    }
    json_data = request.get_json()
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1001", message=err_msg)

    request_id = json_data.get("requestId", "")
    reject = request_dedup_guard(request_id, prefix="VI")
    if reject:
        return jsonify(code=reject[0], message=reject[1])
    
    customer_id = json_data.get("enterpriseId")
    app_info = get_info_by_customer_id(customer_id=customer_id)
    url = app_info["num_callback_url"]
    if not url:
        return jsonify(code="1001", message="未配置回调地址")
    
    number_list = json_data.get("numberList", [])
    if len(number_list) >= 10000:
        return jsonify(code="1001", message="号码数量过多(需小于10000)")
    
    task_id = str(get_next_id())
    if not _submit_num_operation(json_data, task_id):
        return jsonify(code="9999", message="号码操作失败，请稍后重试")
    return jsonify(code="0000", message="success", taskId=task_id)
