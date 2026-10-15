import functools
import logging

from flask import Blueprint, request, jsonify, g
from sqlalchemy import and_, select, func, delete

from models import QaWorkPackage
from apps.service import create_task_by_rule, get_process_id, create_task_by_ability, create_task_by_process, \
    create_task_of_text, save_scripts, update_scripts
from config import settings
from utils.cache import get_info_by_customer_id, get_subject_by_app_id, load_company_info, get_info_by_app_id, set_keyword, \
    delete_keyword, get_qa_rule_by_id, get_dcc_qa_rule_by_id, rule_to_task_params
from utils.etcd import update_etcd
from utils.helpers import request_dedup_guard
from utils.mysql import session_scope
from utils.schema import validate_json, is_valid_url

bp_v1 = Blueprint("v1", __name__, url_prefix="/v1")
bp_manage = Blueprint("manage", __name__, url_prefix="/manage")
bp_v2 = Blueprint("v2", __name__, url_prefix="/v2")
logger = logging.getLogger("all")
etcd_config = settings["ETCD_CONFIG"]


"""
0000 成功
1001 重复请求或requestID为空
1002 接口参数为空
1003 接口参数值错误
9999 其他
"""


def check_app_info(view):
    """
    对比账户信息是否正确
    """
    @functools.wraps(view)
    def wrapped_view(**kwargs):
        json_data = request.get_json()
        app_id = json_data.get("channelId")
        subject = get_subject_by_app_id(app_id=app_id)
        app_info = get_info_by_app_id(app_id=app_id)
        if not app_info or not subject:
            return jsonify(code="1002", message="企业不存在")
        if g.app_key != subject["app_key"]:
            return jsonify(code="1002", message="渠道ID与鉴权信息不一致")
        if app_info["qa_type"] == 1 and app_info["status"] != 1:
            # 语音质检主账户和子账户一对一关系，可用status判断，
            # 专线质检是一对多，不能直接用status判断，需要根据customerId判断
            return jsonify(code="1002", message="企业账户不可用")
        if app_info["qa_type"] == 1 and app_info["mode_status"] != 1:
            # 语音质检账户需判断能开状态开关
            return jsonify(code="1002", message="能力状态为关闭")
        g.app_info = app_info
        g.subject = subject
        return view(**kwargs)
    return wrapped_view


@bp_v1.post("/front/create")
@check_app_info
def create_task():
    """
    根据原子能力开关创建质检任务
    :return:
    """
    schema = {
        "type": "object",
        "properties": {
            "requestId": {"type": "string", "minLength": 1},
            "callIdList": {"type": "string"},
            "urlList": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "url": {"type": "string", "minLength": 1},
                        "callerNumber": {"type": "string"},
                        "calleeNumber": {"type": "string"},
                        "handleStartTime": {"type": "string"},
                        "remark1": {"type": "string"},
                        "remark2": {"type": "string"},
                        "remark3": {"type": "string"},
                        "remark4": {"type": "string"},
                    },
                    "required": ["id", "url"],
                    "additionalProperties": False  # 禁止额外字段
                }
            },
            "dccList": {
                "type": "array",
                "items": {
                    "type": "string"
                }
            },
            "transcription": {"type": "string", "enum": ["0", "1"]},
            "transcriptionType": {"type": "string", "enum": ["0", "1", "2"]},
            "asrLanguage": {"type": "string"},
            "asrEmotionTag": {"type": "string"},
            "wordMatching": {"type": "string", "enum": ["0", "1"]},
            "wordPackageId": {"type": "string"},
            "summary": {"type": "string", "enum": ["0", "1"]},
            "emotionAnalysis": {"type": "string", "enum": ["0", "1"]},
            "emotionChannel": {"type": "string", "enum": ["0", "1", "2"]},
            "sceneAnalysis": {"type": "string", "enum": ["0", "1"]},
            "sceneConsistency": {"type": "string"},
            "scriptConsistency": {"type": "string", "enum": ["0", "1"]},
            "scriptId": {"type": "string"},
            "forgeryDetection": {"type": "string", "enum": ["0", "1"]},
            "sceneRecognition": {"type": "string", "enum": ["0", "1"]},
            "fraudDetection": {"type": "string", "enum": ["0", "1"]},
            "fraudDetectionScene": {"type": "string"},
            "taskCallBackUrl": {"type": "string", "minLength": 1},
            "subTaskCallBackUrl": {"type": "string", "minLength": 1},
            "data": {"type": "string"},
            "channelId": {"type": "string", "minLength": 1},
            "pornAnalysis": {"type": "string", "enum": ["0", "1"]},
            "politicsAnalysis": {"type": "string", "enum": ["0", "1"]},
            "violenceAnalysis": {"type": "string", "enum": ["0", "1"]},
            "gambleDrugAnalysis": {"type": "string", "enum": ["0", "1"]},
            "regexWordMatching": {"type": "string", "enum": ["0", "1"]},
            "regexId": {"type": "string"},
            "dealClass": {"type": "string", "enum": ["0", "1"]},
            "bgeClassifier": {"type": "string", "enum": ["0", "1"]},
            "classifiers": {"type": "string"},
            "bgeRuleId": {"type": "string"},
            "ragSearch": {"type": "string", "enum": ["0", "1"]},
            "ragTopK": {"type": "integer"},
            "deepSeekAnalysis": {"type": "string", "enum": ["0", "1"]},
            "deepSeekPromptId": {"type": "string"},
            "qwenInference": {"type": "string", "enum": ["0", "1"]},
            "qwenPromptId": {"type": "string"},
            "fusionFraudSceneCheck": {"type": "string", "enum": ["0", "1"]},
            "rtScene": {"type": "string"},
            "rtType": {"type": "string", "enum": ["0", "1", "2"]},
            "voiceComparison": {"type": "string", "enum": ["0", "1"]},
            "voicePrintDb": {"type": "string"},
            "voiceAnalysis": {"type": "string", "enum": ["0", "1"]},
            "vpChannel": {"type": "string", "enum": ["0", "1"]},
            "vpTopn": {"type": "string"},
            "identityComparison": {"type": "string", "enum": ["0", "1"]},
            "identityPreset": {"type": "string"},
            "wordAnalysis": {"type": "string", "enum": ["0", "1"]},
            "wordGroupId": {"type": "string"},
            "aigsAnalysis": {"type": "string", "enum": ["0", "1"]},
            "collectionModel": {"type": "string", "enum": ["0", "1"]},
            "marketingCall": {"type": "string", "enum": ["0", "1"]},
            "difyIntegration": {"type": "string", "enum": ["0", "1"]},
            "customizedParam": {"type": "object"},
            "cqFraudCheck": {"type": "string", "enum": ["0", "1"]},
            "cqAgentReview": {"type": "string", "enum": ["0", "1"]},
            "cqAgentUseTools": {"type": "integer", "enum": [0, 1]},
            "cqAgentUseFast": {"type": "integer", "enum": [0, 1]},
        },
        "required": [
            "requestId", "taskCallBackUrl", "subTaskCallBackUrl", "channelId"
        ],
        "additionalProperties": False  # 禁止额外字段
    }
    json_data = request.get_json()
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1002", message=err_msg, taskId="")
    if not is_valid_url(json_data["taskCallBackUrl"]) or not is_valid_url(json_data["subTaskCallBackUrl"]):
        return jsonify(code="1002", message="回调地址格式错误", taskId="")
    request_id = json_data.get("requestId", "")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], taskId="")

    id_list = json_data.get("callIdList", "")
    url_list = json_data.get("urlList", [])
    dcc_list = json_data.get("dccList", [])
    if not id_list and not url_list and not dcc_list:
        return jsonify(code="1002", message="录音数据为空", taskId="")
    if (id_list or url_list) and dcc_list:
        return jsonify(code="1002", message="700质检与其他质检无法同时进行", taskId="")

    is_asr = json_data.get("transcription", "0")
    asr_type = json_data.get("transcriptionType", "")
    if is_asr == "1":
        if not asr_type:
            return jsonify(code="1002", message="语音转写类型为空", taskId="")
        if asr_type not in ("0", "1", "2"):
            return jsonify(code="1003", message="语音转写类型错误", taskId="")
    is_word_match = json_data.get("wordMatching", "0")
    word_package_id = json_data.get("wordPackageId", "")
    if is_word_match == "1" and not word_package_id:
        return jsonify(code="1002", message="词袋ID为空", taskId="")

    is_scene_analyse = json_data.get("sceneAnalysis", "0")
    expected_scene = json_data.get("sceneConsistency", "")
    if is_scene_analyse == "1" and not expected_scene:
        return jsonify(code="1002", message="预期场景为空", taskId="")

    is_script_compare = json_data.get("scriptConsistency", "0")
    script_id = json_data.get("scriptId", "")
    if is_script_compare == "1" and not script_id:
        return jsonify(code="1002", message="话术ID为空", taskId="")

    is_bge_classifier = json_data.get("bgeClassifier", "0")
    classifiers = json_data.get("classifiers", "")
    bge_rule_id = json_data.get("bgeRuleId", "")
    if is_bge_classifier == "1" and not classifiers:
        return jsonify(code="1002", message="BGE模型为空", taskId="")
    if is_bge_classifier == "1" and not bge_rule_id:
        return jsonify(code="1002", message="BGE规则ID为空", taskId="")

    app_id = json_data.get("channelId")

    # 根据能力开关获取流程ID
    process_id = get_process_id(app_id)
    if not process_id:
        return jsonify(code="9999", message="无对应流程", taskId="")

    code, message, task_id = create_task_by_ability(json_data, process_id)
    return jsonify(code=code, message=message, taskId=task_id)


@bp_v1.post("/keyword/create")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
@check_app_info
def create_word_package():
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    if not request_id:
        return jsonify(code="1001", message="requestId为空", wordPackageId="")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], wordPackageId="")
    words = json_data.get("words")
    if not words:
        return jsonify(code="1002", message="词袋内容为空", wordPackageId="")
    word_list = words.split(",")
    if len(words) > 2048 or len(word_list) > 200:
        return jsonify(code="1002", message="词袋内容过长", wordPackageId="")
    for word in word_list:
        if len(word) >= 8:
            return jsonify(code="1002", message="关键词长度过长", wordPackageId="")

    app_id = json_data.get("channelId")
    name = json_data.get("name", "")

    # 词袋入库
    with session_scope("mid_strategy") as ss:
        word_package = QaWorkPackage()
        # 设置企业ID
        word_package.app_id = app_id
        word_package.words = words
        word_package.name = name
        ss.add(word_package)
        ss.flush()
        package_id = str(word_package.id)

        data = {"id": package_id, "words": words, "app_id": app_id, "name": name}
        set_keyword(package_id, data)
    return jsonify(code="0000", message="success", wordPackageId=package_id)


@bp_v1.post("/keyword/update")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
@check_app_info
def update_word_package():
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    if not request_id:
        return jsonify(code="1001", message="requestId为空")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1])
    words = json_data.get("wordList", "")
    # if not words:
    #     return jsonify(code="1002", message="词袋内容为空")
    opt = json_data.get("operation")
    if not opt:
        return jsonify(code="1002", message="操作类型为空")
    if opt not in ("1", "2", "3"):
        return jsonify(code="1003", message="操作类型值错误")
    package_id = json_data.get("wordPackageId")
    if not package_id:
        return jsonify(code="1002", message="词袋ID为空")

    app_id = json_data.get("channelId")

    # 词袋修改
    with session_scope("mid_strategy") as ss:
        word_package = ss.execute(select(QaWorkPackage).filter_by(id=package_id, app_id=app_id)).scalars().first()
        if not word_package:
            return jsonify(code="1002", message="渠道与词袋ID不符")
        if opt == "1":
            word_list = words.split(",")
            exist_len = len(word_package.words.split(",")) if word_package.words else 0
            if words and exist_len + len(word_list) > 200:
                return jsonify(code="1003", message="词袋内容过长")
            for word in word_list:
                if len(word) >= 8:
                    return jsonify(code="1002", message="关键词长度过长")
            # 新增关键词
            if exist_len:
                word_package.words = word_package.words + "," + words if words else word_package.words
            else:
                word_package.words = words
            data = {"id": package_id, "words": word_package.words, "app_id": app_id, "name": word_package.name}
            set_keyword(package_id, data)
        elif opt == "3":
            # 删除词袋
            ss.delete(word_package)
            delete_keyword(package_id)
        else:
            # 删除关键词
            exist_words = word_package.words
            word_list = exist_words.split(",")
            todo_list = words.split(",")

            new_words = []
            for word in word_list:
                if word in todo_list:
                    continue
                new_words.append(word)
            word_package.words = ",".join(new_words)
            data = {"id": package_id, "words": word_package.words, "app_id": app_id, "name": word_package.name}
            set_keyword(package_id, data)
    return jsonify(code="0000", message="success")


@bp_v1.post("/script/create")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
@check_app_info
def create_script():
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    if not request_id:
        return jsonify(code="1001", message="requestId为空", scriptId="")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], scriptId="")
    script_list = json_data.get("scriptList")
    if not script_list:
        return jsonify(code="1002", message="话术脚本为空", scriptId="")

    app_id = json_data.get("channelId")
    name = json_data.get("name", "")
    group_id = save_scripts(name, script_list, app_id)
    return jsonify(code="0000", message="success", scriptId=str(group_id))


@bp_v1.post("/script/update")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
@check_app_info
def update_script():
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    if not request_id:
        return jsonify(code="1001", message="requestId为空")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1])
    opt = json_data.get("operation")
    if not opt:
        return jsonify(code="1002", message="操作类型为空")
    if opt not in ("1", "0"):
        return jsonify(code="1003", message="操作类型值错误")
    script = json_data.get("script")
    if not script:
        return jsonify(code="1002", message="话术脚本为空")
    group_id = json_data.get("scriptId")
    if not group_id:
        return jsonify(code="1002", message="话术组ID为空")

    app_id = json_data.get("channelId")
    result = update_scripts(group_id, opt, script, app_id)
    return jsonify(result)


@bp_v1.post("/task/create")
def create_task_by_rule_id():
    """
    根据已有的质检规则创建任务(内控)
    :return:
    """
    json_data = request.get_json()
    request_id = json_data.get("requestId", "")
    if not request_id:
        return jsonify(code="1001", message="requestId为空", taskId="")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], taskId="")

    id_list = json_data.get("callIdList", "")
    url_list = json_data.get("urlList", [])
    audio_list = json_data.pop("audioList", [])
    dcc_list = json_data.pop("dccList", [])
    if not id_list and not url_list and not audio_list and not dcc_list:
        return jsonify(code="1002", message="录音数据为空", taskId="")
    if (id_list or url_list) and dcc_list:
        return jsonify(code="1002", message="700质检与其他质检无法同时进行", taskId="")

    rule_id = json_data.get("ruleId")

    if dcc_list:
        rule = get_dcc_qa_rule_by_id(rule_id)
    else:
        rule = get_qa_rule_by_id(rule_id)
    if not rule:
        return jsonify(code="1002", message="ruleId错误", taskId="")
    process_id = rule["process_id"]
    data = rule_to_task_params(rule)
    data["callIdList"] = id_list
    data["urlList"] = url_list
    data["audioList"] = audio_list
    data["dccList"] = dcc_list

    app_info = get_info_by_app_id(app_id=rule["app_id"])
    subject = get_subject_by_app_id(app_id=rule["app_id"])
    if not app_info or not subject:
        return jsonify(code="1002", message="企业不存在", taskId="")
    if app_info["status"] != 1:
        return jsonify(code="1002", message="企业账户不可用", taskId="")
    g.app_info = app_info
    g.subject = subject
    priority = rule["priority"]
    code, message, task_id = create_task_by_ability(data, process_id, priority)
    logger.info(f"[{task_id}]-requestId:{request_id}-ruleId:{rule_id}-priority:{priority}")
    return jsonify(code=code, message=message, taskId=task_id)


@bp_v1.post("/task/customize")
@check_app_info
def create_customized_task():
    """
    根据定制化流程ID创建任务
    :return:
    """
    schema = {
        "type": "object",
        "properties": {
            "requestId": {"type": "string", "minLength": 1},
            "callIdList": {"type": "string"},
            "urlList": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "url": {"type": "string", "minLength": 1},
                        "callerNumber": {"type": "string"},
                        "calleeNumber": {"type": "string"},
                        "handleStartTime": {"type": "string"},
                        "remark1": {"type": "string"},
                        "remark2": {"type": "string"},
                        "remark3": {"type": "string"},
                        "remark4": {"type": "string"},
                    },
                    "required": ["id", "url"],
                    "additionalProperties": False  # 禁止额外字段
                }
            },
            "textList": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "text": {"type": "string", "minLength": 1},
                        "remark1": {"type": "string"},
                        "remark2": {"type": "string"},
                        "remark3": {"type": "string"},
                        "remark4": {"type": "string"},
                    },
                    "required": ["id", "text"],
                    "additionalProperties": False  # 禁止额外字段
                }
            },
            "dccList": {
                "type": "array",
                "items": {
                    "type": "string"
                }
            },
            "transcription": {"type": "string", "enum": ["0", "1"]},
            "transcriptionType": {"type": "string", "enum": ["0", "1", "2"]},
            "customizedDetectionId": {"type": "string"},
            "customizedParam": {"type": "object"},
            "taskCallBackUrl": {"type": "string", "minLength": 1},
            "subTaskCallBackUrl": {"type": "string", "minLength": 1},
            "data": {"type": "string"},
            "channelId": {"type": "string", "minLength": 1},
            "wordPackageId": {"type": "string"},
            "emotionChannel": {"type": "string", "enum": ["0", "1", "2"]},
            "sceneConsistency": {"type": "string"},
            "scriptId": {"type": "string"},
            "fraudDetectionScene": {"type": "string"},
            "pornAnalysis": {"type": "string", "enum": ["0", "1"]},
            "politicsAnalysis": {"type": "string", "enum": ["0", "1"]},
            "violenceAnalysis": {"type": "string", "enum": ["0", "1"]},
            "gambleDrugAnalysis": {"type": "string", "enum": ["0", "1"]},
            "regexId": {"type": "string"},
            "ragTopK": {"type": "integer"},
            "deepSeekPromptId": {"type": "string"},
            "qwenPromptId": {"type": "string"},
            "rtScene": {"type": "string"},
            "rtType": {"type": "string", "enum": ["0", "1", "2"]},
            "voicePrintDb": {"type": "string"},
            "vpChannel": {"type": "string", "enum": ["0", "1"]},
            "vpTopn": {"type": "string"},
            "identityPreset": {"type": "string"},
            "wordGroupId": {"type": "string"},
            "classifiers": {"type": "string"},
            "bgeRuleId": {"type": "string"},
            "cqAgentReview": {"type": "string", "enum": ["0", "1"]},
            "cqAgentUseTools": {"type": "integer", "enum": [0, 1]},
            "cqAgentUseFast": {"type": "integer", "enum": [0, 1]},
            "userData": {
                    "type": "object",
                    "properties": {
                        "callerNumber": {"type": "string"},
                        "calleeNumber": {"type": "string"},
                        "handleStartTime": {"type": "string"},
                        "companyId:": {"type": "integer"},
                        "customerId": {"type": "integer"},
                        "asCallBackUrl": {"type": "string"},
                        "isStream": {"type": "boolean"},
                        "endFlag": {"type": "boolean"},
                        "segmentId": {"type": "integer"},
                        "isSegment": {"type": "integer"},
                    }
                }
        },
        "required": [
            "requestId", "taskCallBackUrl", "subTaskCallBackUrl", "channelId", "customizedDetectionId"
        ],
        "additionalProperties": False  # 禁止额外字段
    }
    json_data = request.get_json()
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1002", message=err_msg, taskId="")
    if not is_valid_url(json_data["taskCallBackUrl"]) or not is_valid_url(json_data["subTaskCallBackUrl"]):
        return jsonify(code="1002", message="回调地址格式错误", taskId="")
    request_id = json_data.get("requestId", "")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], taskId="")

    id_list = json_data.get("callIdList", "")
    url_list = json_data.get("urlList", [])
    text_list = json_data.get("textList", [])
    dcc_list = json_data.get("dccList", [])
    if not id_list and not url_list and not text_list and not dcc_list:
        return jsonify(code="1002", message="录音数据为空", taskId="")
    if (id_list or url_list) and dcc_list:
        return jsonify(code="1002", message="700质检与其他质检无法同时进行", taskId="")

    is_asr = json_data.get("transcription", "0")
    asr_type = json_data.get("transcriptionType", "")
    if is_asr == "1":
        if not asr_type:
            return jsonify(code="1002", message="语音转写类型为空", taskId="")
        if asr_type not in ("0", "1", "2"):
            return jsonify(code="1003", message="语音转写类型错误", taskId="")
    
    user_data = json_data.get("userData", {})
    app_info = g.app_info
    if app_info["qa_type"] == 2 and not user_data:
        return jsonify(code="1002", message="实时质检需要传入userData", taskId="")
    if app_info["qa_type"] == 2 and user_data:
        customer_id = user_data["customerId"]
        app_info = get_info_by_customer_id(customer_id)
        if app_info["status"] != 1:
            return jsonify(code="1002", message="企业账户不可用")
        g.app_info = app_info

    # 优先级取配置值
    code, message, task_id = create_task_by_process(json_data)
    return jsonify(code=code, message=message, taskId=task_id)


@bp_v1.post("/text/check")
@check_app_info
def create_text_task():
    """
    根据定制化流程ID创建任务
    :return:
    """
    schema = {
        "type": "object",
        "properties": {
            "requestId": {"type": "string", "minLength": 1},
            "textList": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "groups": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "callId": {"type": "string", "minLength": 1},
                                    "text": {"type": "string", "minLength": 1}
                                },
                                "required": ["callId", "text"],
                                "additionalProperties": False  # 禁止额外字段
                            }
                        },
                        "remark1": {"type": "string"},
                        "remark2": {"type": "string"},
                        "remark3": {"type": "string"},
                        "remark4": {"type": "string"},
                    },
                    "required": ["id", "groups"],
                    "additionalProperties": False  # 禁止额外字段
                }
            },
            "contentSimilarityAnalyse": {"type": "string", "enum": ["0", "1"]},
            "textLength": {"type": "string"},
            "headTail": {"type": "string"},
            "textThreshold": {"type": "string"},
            "taskCallBackUrl": {"type": "string", "minLength": 1},
            "subTaskCallBackUrl": {"type": "string", "minLength": 1},
            "data": {"type": "string"},
            "channelId": {"type": "string", "minLength": 1},
        },
        "required": [
            "requestId", "taskCallBackUrl", "subTaskCallBackUrl", "channelId", "textList"
        ],
        "additionalProperties": False  # 禁止额外字段
    }
    json_data = request.get_json()
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1002", message=err_msg, taskId="")
    if not is_valid_url(json_data["taskCallBackUrl"]) or not is_valid_url(json_data["subTaskCallBackUrl"]):
        return jsonify(code="1002", message="回调地址格式错误", taskId="")
    request_id = json_data.get("requestId", "")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], taskId="")

    app_id = json_data.get("channelId")
    # 根据能力开关获取流程ID
    process_id = get_process_id(app_id)
    if not process_id:
        return jsonify(code="9999", message="无对应流程", taskId="")

    code, message, task_id = create_task_of_text(json_data, process_id)
    return jsonify(code=code, message=message, taskId=task_id)


@bp_manage.post("/info/reload")
@update_etcd(key=etcd_config["config_key"], etcd_list=etcd_config["all_addrs"])
def reload_company_info():
    load_company_info()
    return jsonify(code="0000", message="success")



@bp_v2.post("/task/create")
@check_app_info
def create_task_by_rule_id_v2():
    """
    创建专线质检任务
    """
    schema = {
        "type": "object",
        "properties": {
            "requestId": {"type": "string", "minLength": 1},
            "callIdList": {"type": "string"},
            "urlList": {
                "type": "array",
                "minItems": 1,
                "items": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "string", "minLength": 1},
                        "url": {"type": "string", "minLength": 1},
                        "callerNumber": {"type": "string"},
                        "calleeNumber": {"type": "string"},
                        "handleStartTime": {"type": "string"},
                        "remark1": {"type": "string"},
                        "remark2": {"type": "string"},
                        "remark3": {"type": "string"},
                        "remark4": {"type": "string"},
                    },
                    "required": ["id", "url"],
                    "additionalProperties": False,
                },
            },
            "taskCallBackUrl": {"type": "string", "minLength": 1},
            "subTaskCallBackUrl": {"type": "string", "minLength": 1},
            "data": {"type": "string"},
            "ruleId": {"type": "integer"},
            "channelId": {"type": "string", "minLength": 1},
            "userData": {
                "type": "object",
                "properties": {
                    "callerNumber": {"type": "string"},
                    "calleeNumber": {"type": "string"},
                    "handleStartTime": {"type": "string"},
                    "companyId": {"type": "integer"},
                    "customerId": {"type": "integer"},
                    "asCallBackUrl": {"type": "string"},
                    "isStream": {"type": "boolean"},
                    "endFlag": {"type": "boolean"},
                    "segmentId": {"type": "integer"},
                    "duration": {"type": "integer"},
                    "isSegment": {"type": "integer"}
                },
                "required": ["customerId", "isSegment"],
            },
        },
        "required": [
            "requestId",
            "taskCallBackUrl",
            "subTaskCallBackUrl",
            "channelId"
        ],
        "additionalProperties": False,
    }
    json_data = request.get_json()
    err_msg = validate_json(json_data, schema)
    if err_msg:
        return jsonify(code="1002", message=err_msg, taskId="")
    if not is_valid_url(json_data["taskCallBackUrl"]) or not is_valid_url(json_data["subTaskCallBackUrl"]):
        return jsonify(code="1002", message="回调地址格式错误", taskId="")
    request_id = json_data.get("requestId", "")
    reject = request_dedup_guard(request_id)
    if reject:
        return jsonify(code=reject[0], message=reject[1], taskId="")
    
    id_list = json_data.get("callIdList", "")
    url_list = json_data.get("urlList", [])
    if not id_list and not url_list:
        return jsonify(code="1002", message="录音数据为空", taskId="")

    user_data = json_data.get("userData", {})
    app_id = json_data["channelId"]
    app_info = g.app_info
    if app_info["qa_type"] == 2 and not user_data:
        return jsonify(code="1002", message="实时质检需要传入userData", taskId="")
    if app_info["qa_type"] == 2 and user_data:
        customer_id = user_data["customerId"]
        app_info = get_info_by_customer_id(customer_id)
        if app_info["status"] != 1:
            return jsonify(code="1002", message="企业账户不可用")
        g.app_info = app_info

    rule_id = json_data.get("ruleId")
    rule_id = rule_id if rule_id else app_info.get("rule_id")
    if not rule_id:
        return jsonify(code="1002", message="质检规则ID为空", taskId="")

    rule = get_qa_rule_by_id(rule_id)
    if not rule:
        return jsonify(code="1002", message="无指定规则ID", taskId="")
    if rule["status"] != '1':
        return jsonify(code="1002", message="该质检规则已禁用，请选择其他规则", taskId="")
    if rule["app_id"] != app_id:
        return jsonify(code="1002", message="渠道ID与规则不匹配", taskId="")

    process_id = rule["process_id"]
    priority = rule["priority"]
    data = rule_to_task_params(rule)

    data["data"] = json_data.get("data", "")
    data["subTaskCallBackUrl"] = json_data["subTaskCallBackUrl"]
    data["taskCallBackUrl"] = json_data["taskCallBackUrl"]
    data["urlList"] = url_list
    data["callIdList"] = id_list
    data["userData"] = user_data

    code, message, task_id = create_task_by_rule(data, process_id, priority)
    logger.info(f"[{task_id}]-requestId:{request_id}-ruleId:{rule_id}-userData:{user_data}")
    return jsonify(code=code, message=message, taskId=task_id)
