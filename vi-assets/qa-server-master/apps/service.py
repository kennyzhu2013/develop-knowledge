import atexit
import datetime
import json
import logging
import math
import re
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

from flask import g
from pymongo import MongoClient
from sqlalchemy import and_, delete, func, select, update
import urllib3

from apps.strategy import SEGMENT_ROUTE_NEAR_REALTIME, SEGMENT_ROUTE_NO_AI, SEGMENT_ROUTE_OFFLINE, SEGMENT_ROUTE_REALTIME, resolve_segment_route_from_user_data
from models import QaRule, QaScript, QaScriptGroup, QaTask, DccQaRule, DccQaTask, EnterpriseScene
from apps.prometheus import count_created_sub_task, count_created_main_task, create_task_latency, \
    pending_async_tasks
from config import settings
from utils.cache import (
    get_dcc_qa_rule_by_id, get_keyword_list, get_prompt_by_id, get_qa_rule_by_id, get_script_list_by_group, get_word_group_list, get_regex_list,
    load_scripts, set_script_group, set_qa_rule, set_dcc_qa_rule, get_bge_classifier_by_name, get_bge_rule_by_id,
    get_bge_rule_items_by_rule_id, build_bge_scheduler_rules,
)
from utils.cache_sync import notify_cache_reload
from utils.clickhouse import ClickHouseUnavailable, select_in_ck
from utils.etcd import update_version
from utils.helpers import encrypt_id, QA_INFO_EXPIRATION, GZH_LIST, JsonEncoder
from utils.mq import MsgQueue
from utils.mysql import select_in_mysql, session_scope
from utils.redis import Redis
from utils.snowFlake import get_time_by_billid, get_next_id, get_business_type_by_id


logger = logging.getLogger("all")
task_mq_config = settings["Scheduler_RabbitMq"]
task_mq = MsgQueue(host=task_mq_config["host"], port=task_mq_config["port"], username=task_mq_config["username"],
                   pwd=task_mq_config["password"], vhost=task_mq_config["vhost"])
mongodb = MongoClient(settings["MONGODB_URI"], **settings["MONGODB_CONFIG"])
http_pool = urllib3.PoolManager(num_pools=20)


def get_process_id(app_id):
    # 工作号任务不脱敏
    process_collection = settings["PROCESS_COLLECTION"]
    return process_collection.get("atom_common", "") if app_id in GZH_LIST else process_collection.get("atom_common_dsz", "")


def get_server_url(location, as_id):
    if location == 1:
        server_url = settings["ZJH_SOUTH_FILE_SERVER"] if as_id >= 35 else settings["YSH_SOUTH_FILE_SERVER"]
    elif location == 2:
        server_url = settings["ZJH_NORTH_FILE_SERVER"] if as_id >= 35 else settings["YSH_NORTH_FILE_SERVER"]
    elif location == 3:
        server_url = settings["ZJH_SZ_FILE_SERVER"]
    elif location == 4:
        server_url = settings["ZJH_ZZ_FILE_SERVER"]
    else:
        server_url = ""
    return server_url


DCC_BILL_ENGINES = frozenset({"dcc_mid_bill_history", "dcc_mid_bill"})

# build_url + prepare_sub_data(bill=...) 所需字段
BILL_FIELDS_REGULAR = (
    "id", "location", "asid", "company_id", "mserverid", "nfsid", "extid", "record_type",
    "customer_id", "callernumber", "calleenumber", "handlestarttime", "midnumber", "extnum",
    "displaynum", "transfer_callee", "channel_id", "is_media_channel_inversed", "access_platform",
    "is_eb_business", "call_duration_s",
)

# build_url_v2 + prepare_sub_data(dcc=...) 所需字段
BILL_FIELDS_DCC = (
    "id", "location", "user_id", "m_server_id", "nfs_id", "record_type", "handle_start_time",
    "callee_number", "display_number", "transfer_callee", "app_id", "mobile_id", "channel_id",
    "platform_id", "caller_number", "mid_number", "is_media_channel_reversed", "call_duration_s",
)


def _get_bill_select_columns(engine):
    if engine in DCC_BILL_ENGINES:
        return ", ".join(BILL_FIELDS_DCC)
    return ", ".join(BILL_FIELDS_REGULAR)


def get_bill_list(id_list, history_engine="mid_bill_history", today_engine="hs_bill"):
    def partition_query(select_columns, table_sql, call_id_list, engine):
        """
        每5000个ID查询一次
        """
        result_list = []
        length_of_id_list = len(call_id_list)
        for i in range(0, length_of_id_list, 5000):
            batch = call_id_list[i:i + 5000]
            sql = f"select {select_columns} from {table_sql} where id in :id_list"
            if "mid_bill_history" == engine:
                sql = f"select {select_columns} from {table_sql} where id global in :id_list"
            try:
                result_list.extend(select_in_ck(sql, params={"id_list": batch}, engine=engine))
            except ClickHouseUnavailable:
                raise
            except Exception as err:
                logger.error(
                    "bill query failed, engine=%s, table=%s, batch_size=%d, error=%s",
                    engine, table_sql, len(batch), err,
                )
                raise
        return result_list

    if id_list:
        history_list = []
        daily_list = {}
        today = datetime.datetime.now().strftime("%Y_%m_%d")
        yesterday = (datetime.datetime.now() - datetime.timedelta(days=1)).strftime("%Y_%m_%d")
        for call_id in id_list:
            call_id = call_id.strip()
            if not call_id:
                continue
            date = get_time_by_billid(call_id).strftime("%Y_%m_%d")
            if date in (today, yesterday):
                daily_list.setdefault(date, []).append(call_id)
            else:
                history_list.append(call_id)
        bill_list = []
        if history_list:
            history_columns = _get_bill_select_columns(history_engine)
            bill_list.extend(partition_query(
                history_columns, "bill_history", history_list, history_engine,
            ))
        for date, call_id_list in daily_list.items():
            daily_columns = _get_bill_select_columns(today_engine)
            bill_list.extend(partition_query(
                daily_columns, f"bill_{date}", call_id_list, today_engine,
            ))
    else:
        bill_list = []

    return bill_list


def get_common_info():
    sql_company = "select id,record_suffix from config_company"
    company_list = select_in_mysql(sql_company, engine="mid_auth")
    company_info = {str(item["id"]): item["record_suffix"] for item in company_list}

    sql_channel = "select id,mobile_id from auth_channel"
    channel_list = select_in_mysql(sql=sql_channel, engine="mid_auth")
    channel_info = {str(item["id"]): item["mobile_id"] for item in channel_list}

    sql_customer = "select id,scene,name,record_suffix,is_customer_config from config_customer"
    customer_list = select_in_mysql(sql_customer, engine="mid_auth")
    customer_info = {str(item["id"]): item for item in customer_list}

    g.company_info = company_info
    g.channel_info = channel_info
    g.customer_info = customer_info


def build_url(bill):
    call_id = bill["id"]
    location = bill["location"]
    # 移动云asid大于50
    as_id = bill["asid"]

    server_url = get_server_url(location, as_id)
    if not server_url:
        return ""

    encryptid = encrypt_id(bill["company_id"])
    mserverid = encrypt_id(bill["mserverid"])
    nfs_id = bill.get("nfsid")
    if bill["extid"] in (15, 24):
        # 阿里录音后缀需要从话单里面读
        record_suffix = "wav" if bill["record_type"] == 1 else "mp3"
    else:
        company_info = g.company_info
        customer_info = g.customer_info
        customer = customer_info.get(str(bill["customer_id"]))
        if not customer:
            return ""
        company = company_info.get(str(bill["company_id"]))
        if company is None:
            return ""
        suffix = customer["record_suffix"] if customer["is_customer_config"] else company
        record_suffix = "mp3" if suffix == 0 else "wav"
    fs_version = 1
    callee = format_callee(bill["calleenumber"])
    datetime_str = bill["handlestarttime"].strftime("%Y%m%d%H%M%S")
    # 合成录音url
    if bill["customer_id"] != 17:
        # 1674491716024664_15192663992_15898889355_20220107141522.wav
        filename = "{}_{}_{}_{}.{}".format(call_id, bill["callernumber"], callee, datetime_str, record_suffix)
    else:
        # 65772_1674484113815031_18746818560_18446416935_13234381450_20220107140920.mp3
        filename = "65772_{}_{}_{}_{}_{}.{}".format(call_id, bill["callernumber"], bill["midnumber"], callee,
                                                    datetime_str, record_suffix)

    url_call = "{}/file/{}/{}/{}/{}/{}".format(server_url, fs_version, encryptid, mserverid, nfs_id, filename)
    return url_call


def format_callee(callee):
    """Format callee number by removing country code if present"""
    if callee and re.match("^86", callee):
        return callee[2:]
    return callee if callee else ""


def prepare_sub_data(task_id, process_id, json_data, create_time, app_info, bill=None, item=None, text=None, dcc=None):
    """
    缓存子任务数据到redis，用于补充质检结果数据
    """
    app_id = json_data.get("channelId")
    task_data = json_data.get("data", "")
    strategy_type = json_data.get("strategyType", "")
    user_data = json_data.get("userData")

    qa_type = app_info.get("qa_type")
    if not any((bill, item, text, dcc)):
        return None

    business_type = get_business_type_by_id(task_id)
    detail_id = get_next_id(business_type)

    if item:
        # 专线质检话单数据
        caller = user_data.get("callerNumber", "") if user_data else ""
        callee = user_data.get("calleeNumber", "") if user_data else ""
        call_time = user_data.get("handleStartTime", "") if user_data else ""
        duration = user_data.get("duration", "") if user_data else 0
        # urlList带callerNumber、calleeNumber、handleStartTime
        caller = caller if caller else item.get("callerNumber", "")
        callee = callee if callee else item.get("calleeNumber", "")
        call_time = call_time if call_time else item.get("handleStartTime", "")
        remark1 = item.get("remark1", "")
        remark2 = item.get("remark2", "")
        remark3 = item.get("remark3", "")
        remark4 = item.get("remark4", "")
        data = {"strategy_type": strategy_type, "data_id": str(item["id"]), "url": item["url"],
                "task_id": task_id, "app_id": app_id, "create_time": create_time, "is_reviewed": "0",
                "data": task_data, "process_id": process_id,
                "qa_mobile_id": app_info["qa_mobile_id"], "qa_channel_id": app_info["qa_channel_id"],
                "qa_company_id": app_info["qa_company_id"], "qa_customer_id": app_info["id"],
                "remark1": remark1, "remark2": remark2, "remark3": remark3, "remark4": remark4,
                "audio_tag": item.get("audioTag", ""), "duration": duration, "qa_type": qa_type,
                "caller": caller, "callee": callee, "call_time": call_time, "user_data": user_data,
                "id": detail_id}
        if user_data:
            data["is_segment"] = user_data.get("isSegment")
            data["segment_id"] = user_data.get("segmentId")
            # 表字段是 Int64，Boolean 直接入库会被 StarRocks 丢弃/置空，需转 0/1
            is_stream = user_data.get("isStream")
            if is_stream is not None:
                data["is_stream"] = 1 if is_stream else 0
            end_flag = user_data.get("endFlag")
            if end_flag is not None:
                data["end_flag"] = 1 if end_flag else 0
        return data
    if bill:
        url_call = build_url(bill) if bill["handlestarttime"] else ""

        audio_dict = g.audio_dict if hasattr(g, "audio_dict") else {}
        channel_info = g.channel_info
        customer_info = g.customer_info
        callee = format_callee(bill["calleenumber"])
        ext_num = bill["extnum"] if bill["extnum"] else ""
        display_num = bill["displaynum"] if bill["displaynum"] else ""
        transfer_callee = bill["transfer_callee"] if bill["transfer_callee"] else ""
        customer = customer_info.get(str(bill["customer_id"]))
        if customer:
            zjh_scene = customer.get("scene")
            eb_id = customer.get("name") if bill["is_eb_business"] == 1 else ""
            zjh_scene = zjh_scene if zjh_scene else ""
        else:
            eb_id = ""
            zjh_scene = ""
        data = {"mobile_id": channel_info[str(bill["channel_id"])], "channel_id": bill["channel_id"],
                "company_id": bill["company_id"], "customer_id": bill["customer_id"], "callee": callee,
                "caller": bill["callernumber"], "mid_num": bill["midnumber"], "display_num": display_num,
                "ext_num": ext_num, "call_time": bill["handlestarttime"].strftime("%Y-%m-%d %H:%M:%S"),
                "is_media_channel_inversed": bill["is_media_channel_inversed"],
                "access_platform": bill["access_platform"],
                "is_eb_business": bill["is_eb_business"],
                "strategy_type": strategy_type, "zjh_scene": zjh_scene,
                "data_id": str(bill["id"]), "url": url_call, "task_id": task_id, "app_id": app_id,
                "create_time": create_time, "eb_id": eb_id, "is_reviewed": "0",
                "data": task_data,
                "process_id": process_id, "transfer_callee": transfer_callee,
                "qa_mobile_id": app_info["qa_mobile_id"], "qa_channel_id": app_info["qa_channel_id"],
                "qa_company_id": app_info["qa_company_id"], "qa_customer_id": app_info["id"],
                "audio_tag": audio_dict.get(str(bill["id"]), ""), "duration": bill["call_duration_s"],
                "qa_type": qa_type, "id": detail_id}
        return data
    if text:
        remark1 = text.get("remark1", "")
        remark2 = text.get("remark2", "")
        remark3 = text.get("remark3", "")
        remark4 = text.get("remark4", "")
        data = {"strategy_type": strategy_type, "data_id": str(text["id"]), "content": text["text"],
                "task_id": task_id, "app_id": app_id,
                "create_time": create_time, "is_reviewed": "0", "data": task_data,
                "process_id": process_id,
                "qa_mobile_id": app_info["qa_mobile_id"], "qa_channel_id": app_info["qa_channel_id"],
                "qa_company_id": app_info["qa_company_id"], "qa_customer_id": app_info["id"],
                "remark1": remark1, "remark2": remark2, "remark3": remark3, "remark4": remark4,
                "qa_type": qa_type, "id": detail_id}
        return data
    if dcc:
        url_call = build_url_v2(dcc) if dcc["handle_start_time"] else ""

        audio_dict = g.audio_dict if hasattr(g, "audio_dict") else {}
        application_info = g.application_info
        callee = dcc["callee_number"] if dcc["callee_number"] else ""
        display_num = dcc["display_number"] if dcc["display_number"] else ""
        transfer_callee = dcc["transfer_callee"] if dcc["transfer_callee"] else ""
        application = application_info.get(dcc["app_id"])
        dcc_scene = application["scenario"]
        data = {"dcc_mobile_id": dcc["mobile_id"], "dcc_channel_id": dcc["channel_id"], "dcc_app_id": dcc["app_id"],
                "dcc_user_id": dcc["user_id"], "dcc_platform_id": dcc["platform_id"], "callee": callee,
                "caller": dcc["caller_number"], "mid_num": dcc["mid_number"], "display_num": display_num,
                "call_time": dcc["handle_start_time"].strftime("%Y-%m-%d %H:%M:%S"),
                "is_media_channel_reversed": dcc["is_media_channel_reversed"],
                "strategy_type": strategy_type, "dcc_scene": dcc_scene,
                "data_id": str(dcc["id"]), "url": url_call, "task_id": task_id, "app_id": app_id,
                "create_time": create_time, "data": task_data,
                "process_id": process_id, "transfer_callee": transfer_callee,
                "qa_mobile_id": app_info["qa_mobile_id"], "qa_channel_id": app_info["qa_channel_id"],
                "qa_company_id": app_info["qa_company_id"], "qa_customer_id": app_info["id"],
                "audio_tag": audio_dict.get(str(dcc["id"]), ""), "duration": dcc["call_duration_s"],
                "qa_type": qa_type, "id": detail_id}
        return data
    return None


def prepare_scheduler_params(json_data):
    """
    调度系统参数
    """
    _ = json_data.pop("taskCallBackUrl", "")
    _ = json_data.pop("subTaskCallBackUrl", "")
    _ = json_data.pop("priority", "")
    is_asr = json_data.get("transcription", "0") == "1"
    asr_type = json_data.pop("transcriptionType", "0")
    asr_language = json_data.pop("asrLanguage", "")
    is_word_match = json_data.get("wordMatching", "0") == "1"
    word_package_id = json_data.pop("wordPackageId", "")
    is_scene_compare = json_data.get("sceneAnalysis", "0") == "1"
    expected_scene = json_data.pop("sceneConsistency", "0")

    is_script_compare = json_data.get("scriptConsistency", "0") == "1"
    script_id = json_data.pop("scriptId", "")

    is_keyword_analyse = json_data.get("fraudDetection", "0") == "1"
    fd_scene = json_data.pop("fraudDetectionScene", "0")

    is_rt_check = json_data.get("fusionFraudSceneCheck", "0") == "1"
    rt_scene = json_data.pop("rtScene", "")
    rt_type = json_data.pop("rtType", "1")

    # 涉黄涉暴涉政涉赌毒分析
    is_porn_analyse = json_data.pop("pornAnalysis", "0") == "1"
    is_politics_analyse = json_data.pop("politicsAnalysis", "0") == "1"
    is_violence_analyse = json_data.pop("violenceAnalysis", "0") == "1"
    is_gambledrug_analyse = json_data.pop("gambleDrugAnalysis", "0") == "1"
    # dify参数
    customized_param = json_data.pop("customizedParam", "")

    # 九天声纹
    is_voice_compare = json_data.get("voiceComparison", "0") == "1"
    is_voice_analyse = json_data.get("voiceAnalysis", "0") == "1"
    vp_db = json_data.pop("voicePrintDb", "")
    vp_channel = json_data.pop("vpChannel", "0")
    vp_topn = json_data.pop("vpTopn", "1")
    # 身份比对
    is_identity_compare = json_data.get("identityComparison", "0") == "1"
    identity_preset = json_data.pop("identityPreset", "")
    # deepseek分析
    is_deepseek_analyse = json_data.get("deepSeekAnalysis", "0") == "1"
    deepseek_prompt_id = json_data.pop("deepSeekPromptId", "")
    # 词组匹配
    is_word_analyse = json_data.get("wordAnalysis", "0") == "1"
    word_group_id = json_data.pop("wordGroupId", "")
    # 情绪识别
    is_emotion_analyse = json_data.get("emotionAnalysis", "0") == "1"
    emotion_channel = json_data.pop("emotionChannel", "")
    # 正则匹配
    is_regex_analyse = json_data.get("regexWordMatching", "0") == "1"
    regex_id = json_data.pop("regexId", "")
    # 声音鉴伪
    is_vfd = json_data.get("forgeryDetection", "0") == "1"
    # AIGS
    is_aigs_analyse = json_data.get("aigsAnalysis", "0") == "1"
    # 文本相似度
    is_similarity_analyse = json_data.get("contentSimilarityAnalyse", "0") == "1"
    text_length = json_data.pop("textLength", "30")
    head_tail = json_data.pop("headTail", "0")
    text_threshold = json_data.pop("textThreshold", "0.60")
    # RAG检索
    is_rag_search = json_data.get("ragSearch", "0") == "1"
    rag_top_k = json_data.pop("ragTopK", 0)
    # qwen分析
    is_qwen_analyse = json_data.get("qwenInference", "0") == "1"
    qwen_prompt_id = json_data.pop("qwenPromptId", "")
    # BGE分类
    is_bge_classifier = json_data.get("bgeClassifier", "0") == "1"
    classifiers = json_data.pop("classifiers", "")
    bge_rule_id = json_data.pop("bgeRuleId", "")
    # 专线质检参数
    user_data = json_data.get("userData")
    is_stream = user_data.get("isStream", False) if user_data else False
    asr_prior = 1 if is_stream else 0

    parameter = json_data.copy()
    if is_asr and not asr_type:
        return parameter, "ASR声道为空"
    if asr_type:
        # ASR声道选择
        parameter["transcriptionType"] = asr_type
    if asr_language:
        parameter["language"] = asr_language
    parameter["asrPrior"] = asr_prior

    if is_word_match and not word_package_id:
        return parameter, "词袋ID为空"
    if word_package_id:
        # 词袋内容
        package_id_list = word_package_id.split(",")
        package_list = get_keyword_list(package_id_list)
        if not package_list:
            return parameter, "无对应词袋"
        parameter["wordPackage"] = package_list

    if is_script_compare and not script_id:
        return parameter, "话术ID为空"
    if script_id:
        # 话术内容
        group_id_list = script_id.split(",")
        script_list = get_script_list_by_group(group_id_list)
        if not script_list:
            return parameter, "无对应话术"
        parameter["scripts"] = script_list

    if is_scene_compare and not expected_scene:
        return parameter, "预设场景为空"
    if expected_scene:
        # 预设场景
        parameter["preset"] = expected_scene

    # 融通质检
    if is_rt_check and not rt_type:
        return parameter, "RT质检类型为空"
    if rt_type:
        parameter["svcType"] = rt_type
    if rt_type == "1":
        parameter["rtPreset"] = rt_scene

    # 涉诈检测场景
    if is_keyword_analyse and not fd_scene:
        return parameter, "涉诈检测场景为空"
    if fd_scene:
        parameter["fraudDetectionScene"] = fd_scene

    illegal_word_list = []
    # 涉黄涉暴涉政涉赌毒分析
    if is_porn_analyse:
        package_id_list = settings["PORN_ID"]
        package_list = get_keyword_list(package_id_list)
        content_list = []
        for package in package_list:
            content_list.append(package["word"])
        illegal_word_list.append({"type": "pornRelated", "word": ",".join(content_list)})
    if is_politics_analyse:
        package_id_list = settings["POLITICS_ID"]
        package_list = get_keyword_list(package_id_list)
        content_list = []
        for package in package_list:
            content_list.append(package["word"])
        illegal_word_list.append({"type": "politicsRelated", "word": ",".join(content_list)})
    if is_violence_analyse:
        package_id_list = settings["VIOLENCE_ID"]
        package_list = get_keyword_list(package_id_list)
        content_list = []
        for package in package_list:
            content_list.append(package["word"])
        illegal_word_list.append({"type": "violenceRelated", "word": ",".join(content_list)})
    if is_gambledrug_analyse:
        package_id_list = settings["GAMBLEDRUG_ID"]
        package_list = get_keyword_list(package_id_list)
        content_list = []
        for package in package_list:
            content_list.append(package["word"])
        illegal_word_list.append({"type": "gambleDrugRelated", "word": ",".join(content_list)})

    if illegal_word_list:
        parameter["forbiddenWordCheck"] = "1"
        parameter["wordRisk"] = illegal_word_list

    # dify
    if customized_param:
        parameter["difyParam"] = customized_param
    # 九天声纹
    if is_voice_compare or is_voice_analyse:
        if not vp_db:
            return parameter, "声纹库名为空"
    if vp_db:
        parameter["audioTagDb"] = vp_db
        if vp_channel:
            parameter["channel"] = int(vp_channel)
        if vp_topn:
            parameter["topN"] = int(vp_topn)

    # 身份比对
    if is_identity_compare and not identity_preset:
        return parameter, "身份比对值为空"
    if identity_preset:
        parameter["identityPreset"] = identity_preset

    # deepseek分析
    if is_deepseek_analyse and not deepseek_prompt_id:
        return parameter, "deepSeek提示词ID为空"
    if deepseek_prompt_id:
        deepseek_prompt = get_prompt_by_id(deepseek_prompt_id)
        if not deepseek_prompt:
            return parameter, "请使用正确的deepSeek提示词ID"
        parameter["deepSeekPrompt"] = deepseek_prompt

    # 词组匹配
    if is_word_analyse and not word_group_id:
        return parameter, "词组ID为空"
    if word_group_id:
        # 词组内容
        group_id_list = word_group_id.split(",")
        word_group_list = get_word_group_list(group_id_list)
        if not word_group_list:
            return parameter, "无对应词组"
        parameter["wordGroup"] = word_group_list

    # 情绪识别
    if is_emotion_analyse and not emotion_channel:
            return parameter, "情绪识别处理声道为空"
    if emotion_channel:
        parameter["emotionChannel"] = int(emotion_channel)

    # 正则匹配
    if is_regex_analyse and not regex_id:
        return parameter, "正则规则ID为空"
    if regex_id:
        # 词袋内容
        regex_id_list = regex_id.split(",")
        regex_list = get_regex_list(regex_id_list)
        if not regex_list:
            return parameter, "无对应正则规则"
        parameter["wordRegex"] = regex_list

    # 文本相似度
    if is_similarity_analyse:
        parameter["wordNum"] = text_length
        parameter["headtail"] = head_tail
        parameter["textThreshold"] = text_threshold
    
    # RAG检索
    if is_rag_search and not rag_top_k:
        return parameter, "RAG参数错误"
    if rag_top_k:
        parameter["ragTopK"] = rag_top_k
    
    # qwen分析
    if is_qwen_analyse and not qwen_prompt_id:
        return parameter, "qwen提示词ID为空"
    if qwen_prompt_id:
        qwen_prompt = get_prompt_by_id(qwen_prompt_id)
        if not qwen_prompt:
            return parameter, "请使用正确的qwen提示词ID"
        parameter["qwenPrompt"] = qwen_prompt

    # BGE分类
    if is_bge_classifier and not classifiers:
        return parameter, "BGE模型为空"
    if is_bge_classifier and not bge_rule_id:
        return parameter, "BGE规则ID为空"
    if classifiers:
        classifier_list = [name.strip() for name in classifiers.split(",") if name.strip()]
        if is_bge_classifier and not classifier_list:
            return parameter, "BGE模型为空"
        if classifier_list:
            for name in classifier_list:
                if not get_bge_classifier_by_name(name):
                    return parameter, "无对应BGE模型"
            parameter["classifiers"] = classifier_list
    if bge_rule_id:
        if not get_bge_rule_by_id(bge_rule_id):
            return parameter, "无对应BGE主规则"
        sub_rule_list = get_bge_rule_items_by_rule_id(bge_rule_id)
        if not sub_rule_list:
            return parameter, "无对应BGE子规则"
        parameter["rules"] = build_bge_scheduler_rules(sub_rule_list)

    # 重庆行业卡 Agent 复核
    cq_agent_use_tools = json_data.get("cqAgentUseTools")
    if cq_agent_use_tools is not None:
        parameter["cqAgentUseTools"] = bool(cq_agent_use_tools)
    cq_agent_use_fast = json_data.get("cqAgentUseFast")
    if cq_agent_use_fast is not None:
        parameter["cqAgentUseFast"] = bool(cq_agent_use_fast)

    return parameter, None


# 异步执行线程池：统一负责 Redis 子任务缓存写入 + 规则/任务落库 + MQ 发布
_async_executor = ThreadPoolExecutor(max_workers=settings["ASYNC_WORKER_THREAD_COUNT"], thread_name_prefix="async-task-worker")


def _submit_async_task(fn, *args, **kwargs):
    """submit 时计入 pending，任务结束再减，覆盖排队+执行中。"""
    pending_async_tasks.inc()

    def _run():
        try:
            fn(*args, **kwargs)
        finally:
            pending_async_tasks.dec()

    try:
        return _async_executor.submit(_run)
    except Exception:
        pending_async_tasks.dec()
        raise


def _async_cache_sub_tasks(task_id, cache_items):
    """
    将子任务补充数据批量写入 Redis。
    """
    if not cache_items:
        return
    client = Redis.get_connection()
    try:
        with client.pipeline() as pipe:
            for key, value in cache_items:
                pipe.set(key, value, ex=QA_INFO_EXPIRATION)
            pipe.execute()
        logger.info("[%s]-async cache sub tasks success, count=%d", task_id, len(cache_items))
    except Exception as err:
        logger.error("[%s]-async cache sub tasks failed, count=%d, error=%s", task_id, len(cache_items), err)
    finally:
        client.close()


def _build_sub_task_payloads(task_id, process_id, json_data, create_time,
                             url_list, bill_list, dcc_bill_list, text_list=None,
                             app_id=None, is_voice_compare="0", audio_dict=None, user_data=None):
    """
    预处理子任务数据（主请求线程内执行，CPU 密集部分）：
    - 调用 prepare_sub_data 构造每条子任务的补充数据；
    - 生成调度系统所需的 result_list（发给 MQ）；
    - 收集待写入 Redis 的 cache_items = [(key, json_value), ...]。
    """
    audio_dict = audio_dict or {}
    user_data = user_data or {}
    app_info = g.app_info
    is_reuse_asr = app_info.get("is_reuse_asr", 0)
    result_list = []
    cache_items = []

    for item in url_list:
        data = prepare_sub_data(task_id, process_id, json_data, create_time, app_info, item=item)
        key = "{}:{}".format(task_id, item["id"])
        cache_items.append((key, json.dumps(data)))
        sub_data = {"dataId": item["id"], "url": item["url"], "reuse": is_reuse_asr}
        if is_voice_compare == "1":
            sub_data["audioTag"] = item.get("audioTag", "")
        if user_data:
            sub_data["segmentId"] = user_data.get("segmentId")
            sub_data["endFlag"] = user_data.get("endFlag")
        result_list.append(sub_data)

    for bill in bill_list:
        call_id = bill["id"]
        data = prepare_sub_data(task_id, process_id, json_data, create_time, app_info, bill=bill)
        if not data:
            logger.info(f"[{task_id}]-appId:{app_id}-wrongCallId:{call_id}")
            continue
        url_call = data["url"]
        if not url_call:
            logger.info(f"[{task_id}]-appId:{app_id}-wrongCallId:{call_id}")
            continue
        sub_data = {"dataId": str(call_id), "url": url_call, "location": bill["location"],
                    "isReverse": bool(bill["is_media_channel_inversed"]), "reuse": is_reuse_asr}
        if is_voice_compare == "1":
            sub_data["audioTag"] = audio_dict.get(str(call_id), "")
        result_list.append(sub_data)
        key = "{}:{}".format(task_id, bill["id"])
        cache_items.append((key, json.dumps(data)))

    for item in text_list or []:
        data = prepare_sub_data(task_id, process_id, json_data, create_time, app_info, text=item)
        key = "{}:{}".format(task_id, item["id"])
        cache_items.append((key, json.dumps(data)))
        sub_data = {"dataId": item["id"], "text": item["text"], "location": 1}
        result_list.append(sub_data)

    for bill in dcc_bill_list:
        call_id = bill["id"]
        data = prepare_sub_data(task_id, process_id, json_data, create_time, app_info, dcc=bill)
        if not data:
            logger.info(f"[{task_id}]-appId:{app_id}-wrongCallId:{call_id}")
            continue
        url_call = data["url"]
        if not url_call:
            logger.info(f"[{task_id}]-appId:{app_id}-wrongCallId:{call_id}")
            continue
        sub_data = {"dataId": str(call_id), "url": url_call, "location": bill["location"],
                    "isReverse": bool(bill["is_media_channel_reversed"]), "reuse": is_reuse_asr}
        if is_voice_compare == "1":
            sub_data["audioTag"] = audio_dict.get(str(call_id), "")
        result_list.append(sub_data)
        key = "{}:{}".format(task_id, bill["id"])
        cache_items.append((key, json.dumps(data)))

    return result_list, cache_items


_shutdown_once = threading.Event()


def _shutdown_executors():
    """进程退出时优雅关闭异步线程池，等待已提交任务执行完毕。"""
    if _shutdown_once.is_set():
        return
    _shutdown_once.set()
    logger.info("shutting down async executor...")
    try:
        _async_executor.shutdown(wait=True, cancel_futures=False)
    except TypeError:
        _async_executor.shutdown(wait=True)
    except Exception:
        logger.exception("shutdown async executor failed")
    logger.info("async executor shutdown complete")


atexit.register(_shutdown_executors)


def _mark_task_failed(Task, task_id, app_id, total):
    """后台落库失败时，尽力标记任务状态为失败(2)，便于调用方轮询感知。"""
    try:
        with session_scope() as ss:
            existing = ss.get(Task, task_id)
            if existing:
                existing.status = "2"
        logger.info("[%s]-appId:%s-marked task as failed(2)", task_id, app_id)
    except Exception as e:
        logger.error("[%s]-appId:%s-mark task failed error:%s", task_id, app_id, e)


def _persist_task_and_cache(
    cache_items,
    task_id, app_id, total, create_time, is_dcc, app_info,
    process_id, priority, main_url, sub_url, task_data,
    is_asr="0", asr_type="",
    is_word_match="0", word_package_id="",
    is_emotion_analyse="0", emotion_channel="",
    is_scene_compare="0", expected_scene="",
    is_script_compare="0", script_id="",
    is_vfd="0", fd_scene="0",
    is_porn_analyse="0", is_politics_analyse="0", is_violence_analyse="0", is_gambledrug_analyse="0",
    is_identity_compare="0", identity_preset="",
    is_word_analyse="0", word_group_id="",
    is_regex_analyse="0", regex_id="",
    is_aigs_analyse="0", is_rag_search="0", rag_top_k="",
    is_deal_class="0", is_keyword_analyse_lt="0",
    is_bge_classifier="0", classifiers="", bge_rule_id="",
    is_cq_fraud_check="0", is_cq_agent_review="0",
    cq_agent_use_tools=None, cq_agent_use_fast=None,
    # 按原子能力创建任务
    asr_language="", asr_emotion_tag="", is_summarize="0",
    is_scene_recognize="0", is_keyword_analyse="0",
    is_rt_check="0", rt_type="0", rt_scene="",
    rule_id=None,
    # 按定制化流程创建任务
    is_dify="0",
    is_deepseek_analyse="0", deepseek_prompt_id="",
    is_qwen_analyse="0", qwen_prompt_id="",
    is_rt_fraud_review="0", is_market_call_analyse="0",
    # 文本相似度任务
    is_similarity_analyse=None, text_length=None,
    head_tail=None, text_threshold=None,
    opt_time=None, request_data=None,
    user_data=None,
):
    """
    后台线程：依次执行子任务缓存写入 + 规则/任务落库 + MQ 发布。
    主请求线程只负责构造数据，所有 IO 操作集中在同一条异步任务中执行。
    """
    Rule = DccQaRule if is_dcc else QaRule
    Task = DccQaTask if is_dcc else QaTask
    rclient = None
    is_text = is_similarity_analyse is not None
    rule_updated = False
    try:
        # Step 1: 将子任务补充数据批量写入 Redis
        _async_cache_sub_tasks(task_id, cache_items)

        # Step 2: rule 查询/新增 + task 新增 + QARULE 缓存 + MQ 发布
        rclient = Redis.get_connection()
        with session_scope() as ss:
            if rule_id:
                qa_rule = get_dcc_qa_rule_by_id(rule_id) if is_dcc else get_qa_rule_by_id(rule_id)
                if not qa_rule:
                    raise ValueError(f"rule not found: {rule_id}")
                resolved_rule_id = rule_id
                rule_property = {}
                if qa_rule["data"] != task_data:
                    qa_rule["data"] = task_data
                    rule_property["data"] = task_data
                if qa_rule["sub_url"] != sub_url:
                    qa_rule["sub_url"] = sub_url
                    rule_property["sub_url"] = sub_url
                if qa_rule["main_url"] != main_url:
                    qa_rule["main_url"] = main_url
                    rule_property["main_url"] = main_url
                if rule_property and app_info["qa_type"] == 1:
                    # 专线质检规则中的回调地址无需更新，直接使用userData中的url
                    ss.execute(update(Rule).where(Rule.id == rule_id).values(**rule_property))
                    rule_updated = True
                    logger.info(f"[{task_id}]-appId:{app_id}-update rule:{rule_id}-property:{rule_property}")
            else:
                if is_text:
                    rule = ss.execute(select(Rule).filter_by(
                        app_id=app_id, process_id=process_id,
                        text_length=text_length, head_tail=head_tail,
                        text_threshold=text_threshold, priority=priority,
                        main_url=main_url, sub_url=sub_url,
                        data=task_data,
                        qa_mobile_id=app_info["qa_mobile_id"],
                        qa_channel_id=app_info["qa_channel_id"],
                        qa_company_id=app_info["qa_company_id"],
                        qa_customer_id=app_info["id"],
                    )).scalars().first()
                else:
                    rule = ss.execute(select(Rule).filter_by(
                        app_id=app_id, process_id=process_id,
                        asr_type=asr_type, asr_language=asr_language,
                        asr_emotion_tag=asr_emotion_tag, word_package_id=word_package_id,
                        expected_scene=expected_scene, script_id=script_id,
                        rt_svc_type=rt_type, rt_expected_scene=rt_scene,
                        fd_scene=fd_scene, emotion_channel=emotion_channel,
                        identity_preset=identity_preset, word_group_id=word_group_id,
                        regex_id=regex_id, priority=priority, main_url=main_url,
                        sub_url=sub_url, deepseek_prompt_id=deepseek_prompt_id,
                        qwen_prompt_id=qwen_prompt_id,
                        data=task_data, cq_agent_use_fast=cq_agent_use_fast,
                        cq_agent_use_tools=cq_agent_use_tools,
                        bge_classifiers=classifiers, bge_rule_id=bge_rule_id,
                        qa_mobile_id=app_info["qa_mobile_id"], qa_channel_id=app_info["qa_channel_id"],
                        qa_company_id=app_info["qa_company_id"],
                        qa_customer_id=app_info["id"], rag_top_k=rag_top_k,
                    )).scalars().first()
                if not rule:
                    rule = Rule()
                    rule.process_id = process_id
                    rule.priority = priority
                    rule.app_id = app_id
                    rule.name = "OT" + opt_time.strftime("%Y%m%d%H%M%S")
                    rule.is_periodic = "0"
                    rule.first_execute_time = create_time
                    rule.next_execute_time = create_time
                    if is_text:
                        rule.is_similarity_analyse = is_similarity_analyse
                        rule.text_length = text_length if is_similarity_analyse == "1" else ""
                        rule.text_threshold = text_threshold if is_similarity_analyse == "1" else ""
                        rule.head_tail = head_tail if is_similarity_analyse == "1" else ""
                    else:
                        rule.is_asr = is_asr
                        rule.asr_type = asr_type
                        rule.asr_language = asr_language
                        rule.asr_emotion_tag = asr_emotion_tag
                        rule.is_dify = is_dify
                        rule.is_word_match = is_word_match
                        rule.word_package_id = word_package_id
                        rule.is_summarize = is_summarize
                        rule.is_emotion_analyse = is_emotion_analyse
                        rule.emotion_channel = emotion_channel
                        rule.is_scene_compare = is_scene_compare
                        rule.expected_scene = expected_scene
                        rule.is_script_compare = is_script_compare
                        rule.script_id = script_id
                        rule.is_vfd = is_vfd
                        rule.is_scene_recognize = is_scene_recognize
                        rule.is_keyword_analyse = is_keyword_analyse
                        rule.fd_scene = fd_scene
                        rule.is_rt_check = is_rt_check
                        rule.rt_svc_type = rt_type
                        rule.rt_expected_scene = rt_scene
                        rule.is_porn_analyse = is_porn_analyse
                        rule.is_politics_analyse = is_politics_analyse
                        rule.is_violence_analyse = is_violence_analyse
                        rule.is_gambledrug_analyse = is_gambledrug_analyse
                        rule.is_deepseek_analyse = is_deepseek_analyse
                        rule.deepseek_prompt_id = deepseek_prompt_id
                        rule.is_qwen_analyse = is_qwen_analyse
                        rule.qwen_prompt_id = qwen_prompt_id
                        rule.is_rt_fraud_review = is_rt_fraud_review
                        rule.is_market_call_analyse = is_market_call_analyse
                        rule.is_identity_compare = is_identity_compare
                        rule.identity_preset = identity_preset
                        rule.is_word_analyse = is_word_analyse
                        rule.word_group_id = word_group_id
                        rule.is_regex_analyse = is_regex_analyse
                        rule.regex_id = regex_id
                        rule.is_aigs_analyse = is_aigs_analyse
                        rule.is_deal_class = is_deal_class
                        rule.is_rag_search = is_rag_search
                        rule.rag_top_k = rag_top_k
                        rule.is_keyword_analyse_lt = is_keyword_analyse_lt
                        rule.is_bge_classifier = is_bge_classifier
                        rule.bge_classifiers = classifiers
                        rule.bge_rule_id = bge_rule_id
                        rule.is_cq_fraud_check = is_cq_fraud_check
                        rule.is_cq_agent_review = is_cq_agent_review
                        rule.cq_agent_use_tools = cq_agent_use_tools
                        rule.cq_agent_use_fast = cq_agent_use_fast
                    rule.main_url = main_url
                    rule.sub_url = sub_url
                    rule.data = task_data
                    rule.qa_mobile_id = app_info["qa_mobile_id"]
                    rule.qa_channel_id = app_info["qa_channel_id"]
                    rule.qa_company_id = app_info["qa_company_id"]
                    rule.qa_customer_id = app_info["id"]
                    rule.create_time = create_time
                    ss.add(rule)
                    ss.flush()
                    resolved_rule_id = rule.id
                    qa_rule = rule.to_dict()
                else:
                    resolved_rule_id = rule.id
                    qa_rule = rule.to_dict()
            rclient.set(f"QARULE:{task_id}", json.dumps(qa_rule, cls=JsonEncoder), ex=QA_INFO_EXPIRATION)

            task = Task()
            task.id = task_id
            task.rule_id = resolved_rule_id
            task.status = "0"
            task.total = total
            task.completion_ratio = 0
            task.error_num = 0
            task.qa_type = app_info.get("qa_type")
            task.create_time = create_time
            if isinstance(user_data, dict) and user_data:
                if user_data.get("isStream") is not None:
                    task.is_stream = 1 if user_data.get("isStream") else 0
                if user_data.get("endFlag") is not None:
                    task.end_flag = 1 if user_data.get("endFlag") else 0
            ss.add(task)

        # 规则写入内存（拷贝，避免与后续逻辑共享同一 dict）
        cache_rule = dict(qa_rule)
        if is_dcc:
            set_dcc_qa_rule(resolved_rule_id, cache_rule)
        else:
            set_qa_rule(resolved_rule_id, cache_rule)
        if rule_updated:
            notify_cache_reload("rule")
            etcd_config = settings["ETCD_CONFIG"]
            update_version(etcd_config["rule_key"], etcd_config["all_addrs"])
        # 事务在此处（with 块退出）提交；MQ 发布放到提交之后，避免消息已发但 task 未落库
        task_mq.publish_message(exchange=task_mq_config["to_scheduler_exchange"],
                                routing_key=task_mq_config["to_scheduler_routing_key"],
                                msg=json.dumps(request_data))
        _ = request_data.pop("data")
        logger.info(f"[{task_id}]-appId:{app_id}-sendToMq:{request_data}")
        count_created_main_task(status="1", status_code=200, app_id=app_id)
        count_created_sub_task(status="1", amount=total, app_id=app_id)
    except Exception as err:
        logger.error(f"[{task_id}]-appId:{app_id}-async persist failed:[{err}]")
        _mark_task_failed(Task, task_id, app_id, total)
        count_created_main_task(status="0", status_code=200, app_id=app_id)
        count_created_sub_task(status="0", amount=total, app_id=app_id)
    finally:
        if rclient:
            rclient.close()


@create_task_latency.time()
def create_task_by_ability(json_data, process_id, priority=None):
    """
    根据原子能力开关创建质检任务
    :param json_data:
    :param process_id:
    :param priority:
    :return:
    """
    app_id = json_data.get("channelId")
    id_list = json_data.pop("callIdList", "")
    url_list = json_data.pop("urlList", [])
    audio_list = json_data.pop("audioList", [])
    dcc_list = json_data.pop("dccList", [])
    # 是否700话单质检
    is_dcc = len(dcc_list) > 0
    business_type = 1 if is_dcc else 0
    task_id = str(get_next_id(business_type))
    logger.info(f"[{task_id}]-appId:{app_id}-requestData:{json_data}")

    is_voice_compare = json_data.get("voiceComparison", "0")
    if is_voice_compare == "1" and not audio_list:
        return "1001", "缺少声纹对比数据", task_id

    if is_voice_compare == "1":
        id_list = []
        audio_dict = {}
        for item in audio_list:
            audio_tag = item.get("audioTag")
            if not audio_tag:
                return "1001", "声纹对比时请提供特征码", task_id
            if item["id"].startswith("700") and len(item["id"]) == 32:
                dcc_list.append(item["id"])
            else:
                id_list.append(item["id"])
            audio_dict[item["id"]] = audio_tag
    else:
        audio_dict = {}
        id_list = id_list.split(",") if id_list else []
    if len(url_list) + len(id_list) + len(dcc_list) > settings["TASK_LIMITATION"]:
        return "1003", "单次质检量过大", task_id
    # 保存话单对应声纹标签
    g.audio_dict = audio_dict
    # 根据话单ID获取话单数据
    try:
        bill_list = get_bill_list(id_list)
        if id_list and not bill_list:
            return "1002", "请提供有效话单Id", task_id
        if audio_list and not bill_list:
            return "1002", "请提供有效话单Id", task_id

        if len(url_list) > len({url["id"] for url in url_list}):
            return "1001", "存在重复录音ID", task_id
        dcc_bill_list = get_bill_list(dcc_list, history_engine="dcc_mid_bill_history", today_engine="dcc_mid_bill")
        if dcc_list and not dcc_bill_list:
            return "1002", "请提供有效700话单Id", task_id
    except ClickHouseUnavailable:
        logger.error("[%s]-appId:%s-clickhouse unavailable while querying bills", task_id, app_id)
        return "1001", "话单服务暂不可用，请稍后重试", task_id

    if dcc_list and id_list:
        return "1002", "callIdList和dccList无法同时使用", task_id

    is_asr = json_data.get("transcription", "0")
    asr_type = json_data.get("transcriptionType", "0")
    asr_language = json_data.get("asrLanguage", "")
    asr_emotion_tag = json_data.get("asrEmotionTag", "")
    is_word_match = json_data.get("wordMatching", "0")
    word_package_id = json_data.get("wordPackageId", "")
    is_summarize = json_data.get("summary", "0")
    is_emotion_analyse = json_data.get("emotionAnalysis", "0")
    emotion_channel = json_data.get("emotionChannel", "")
    is_scene_compare = json_data.get("sceneAnalysis", "0")
    expected_scene = json_data.get("sceneConsistency", "0")

    is_script_compare = json_data.get("scriptConsistency", "0")
    script_id = json_data.get("scriptId", "")
    # 身份比对
    is_identity_compare = json_data.get("identityComparison", "0")
    identity_preset = json_data.get("identityPreset", "")
    # 词组匹配
    is_word_analyse = json_data.get("wordAnalysis", "0")
    word_group_id = json_data.get("wordGroupId", "")
    # 正则匹配
    is_regex_analyse = json_data.get("regexWordMatching", "0")
    regex_id = json_data.get("regexId", "")
    # AIGS
    is_aigs_analyse = json_data.get("aigsAnalysis", "0")
    # RAG检索
    is_rag_search = json_data.get("ragSearch", "0")
    rag_top_k = json_data.get("ragTopK")
    # 成交分类
    is_deal_class = json_data.get("dealClass", "0")
    # BGE分类
    is_bge_classifier = json_data.get("bgeClassifier", "0")
    classifiers = json_data.get("classifiers", "")
    bge_rule_id = json_data.get("bgeRuleId", "")

    is_vfd = json_data.get("forgeryDetection", "0")
    is_scene_recognize = json_data.get("sceneRecognition", "0")
    is_keyword_analyse = json_data.get("fraudDetection", "0")
    fd_scene = json_data.get("fraudDetectionScene", "0")

    is_rt_check = json_data.get("fusionFraudSceneCheck", "0")
    rt_scene = json_data.get("rtScene", "")
    rt_type = json_data.get("rtType", "1")

    # 涉黄涉暴涉政涉赌毒分析
    is_porn_analyse = json_data.get("pornAnalysis", "0")
    is_politics_analyse = json_data.get("politicsAnalysis", "0")
    is_violence_analyse = json_data.get("violenceAnalysis", "0")
    is_gambledrug_analyse = json_data.get("gambleDrugAnalysis", "0")

    is_keyword_analyse_lt = json_data.get("fraudDetectionLT", "0")
    is_cq_fraud_check = json_data.get("cqFraudCheck", "0")

    is_cq_agent_review = json_data.get("cqAgentReview", "0")
    cq_agent_use_tools = json_data.get("cqAgentUseTools")
    cq_agent_use_fast = json_data.get("cqAgentUseFast")
    if cq_agent_use_tools is not None:
        cq_agent_use_tools = int(cq_agent_use_tools)
    if cq_agent_use_fast is not None:
        cq_agent_use_fast = int(cq_agent_use_fast)

    main_url = json_data.get("taskCallBackUrl", "")
    sub_url = json_data.get("subTaskCallBackUrl", "")
    task_data = json_data.get("data", "")

    rule_id = json_data.get("ruleId")
    user_data = json_data.get("userData", {})

    if bill_list:
        # 获取中间号企业信息
        get_common_info()
    if dcc_bill_list:
        get_common_info_v2()

    app_info = g.app_info
    priority = app_info["priority"] if priority is None else priority

    opt_time = datetime.datetime.now()
    create_time = opt_time.strftime("%Y-%m-%d %H:%M:%S")

    result_list, cache_items = _build_sub_task_payloads(
        task_id, process_id, json_data, create_time,
        url_list, bill_list, dcc_bill_list,
        app_id=app_id, is_voice_compare=is_voice_compare,
        audio_dict=audio_dict, user_data=user_data,
    )

    total = len(result_list)
    logger.info(f"[{task_id}]-appId:{app_id}-urlTotal:{len(url_list)}-billTotal:{len(bill_list)}-dccTotal:{len(dcc_bill_list)}-total:{total}")

    if not result_list:
        return "1001", "无有效质检数据", task_id

    parameter, err = prepare_scheduler_params(json_data.copy())
    if err:
        return "1001", err, task_id

    # 是否实时质检
    is_stream = user_data.get("isStream", False)
    # 生成requestID
    request_id = uuid.uuid1().hex
    request_data = {"requestId": request_id, "data": result_list, "processId": process_id,
                    "priority": priority, "transactionId": int(task_id), "isStream": is_stream}
    if parameter:
        request_data["parameter"] = parameter

    # 子任务缓存写入 + rule 查询/新增 + task 新增 + QARULE 缓存 + MQ 发布，合并为一条异步任务执行
    _submit_async_task(
        _persist_task_and_cache,
        cache_items,
        task_id, app_id, total, create_time, is_dcc, app_info,
        process_id, priority, main_url, sub_url, task_data,
        is_asr=is_asr, asr_type=asr_type,
        asr_language=asr_language, asr_emotion_tag=asr_emotion_tag,
        is_word_match=is_word_match, word_package_id=word_package_id,
        is_summarize=is_summarize,
        is_emotion_analyse=is_emotion_analyse, emotion_channel=emotion_channel,
        is_scene_compare=is_scene_compare, expected_scene=expected_scene,
        is_script_compare=is_script_compare, script_id=script_id,
        is_vfd=is_vfd, is_scene_recognize=is_scene_recognize,
        is_keyword_analyse=is_keyword_analyse, fd_scene=fd_scene,
        is_rt_check=is_rt_check, rt_type=rt_type, rt_scene=rt_scene,
        is_porn_analyse=is_porn_analyse, is_politics_analyse=is_politics_analyse,
        is_violence_analyse=is_violence_analyse, is_gambledrug_analyse=is_gambledrug_analyse,
        is_identity_compare=is_identity_compare, identity_preset=identity_preset,
        is_word_analyse=is_word_analyse, word_group_id=word_group_id,
        is_regex_analyse=is_regex_analyse, regex_id=regex_id,
        is_aigs_analyse=is_aigs_analyse, is_rag_search=is_rag_search, rag_top_k=rag_top_k,
        is_deal_class=is_deal_class, is_keyword_analyse_lt=is_keyword_analyse_lt,
        is_bge_classifier=is_bge_classifier, classifiers=classifiers, bge_rule_id=bge_rule_id,
        is_cq_fraud_check=is_cq_fraud_check, is_cq_agent_review=is_cq_agent_review,
        cq_agent_use_tools=cq_agent_use_tools, cq_agent_use_fast=cq_agent_use_fast,
        opt_time=opt_time, request_data=request_data, rule_id=rule_id, user_data=user_data,
    )
    return "0000", "success", task_id


@create_task_latency.time()
def create_task_by_process(json_data):
    """
    根据定制化流程创建任务
    :param json_data:
    :return:
    """
    app_id = json_data.get("channelId")
    id_list = json_data.pop("callIdList", "")
    url_list = json_data.pop("urlList", [])
    text_list = json_data.pop("textList", [])
    dcc_list = json_data.pop("dccList", [])
    # 是否700话单质检
    is_dcc = len(dcc_list) > 0
    business_type = 1 if is_dcc else 0
    task_id = str(get_next_id(business_type))
    logger.info(f"[{task_id}]-appId:{app_id}-request:{json_data}")
    if text_list and (url_list or id_list or dcc_list):
        return "1002", "文本质检和语音质检无法同时进行", task_id
    # 根据话单ID获取话单数据
    id_list = id_list.split(",") if id_list else []
    if len(url_list) + len(id_list) + len(dcc_list) > settings["TASK_LIMITATION"]:
        return "1003", "单次质检量过大", task_id
    try:
        bill_list = get_bill_list(id_list)
        if id_list and not bill_list:
            return "1002", "请提供有效话单Id", task_id

        if len(url_list) > len({url["id"] for url in url_list}):
            return "1001", "存在重复录音ID", task_id
        if len(text_list) > len({text["id"] for text in text_list}):
            return "1001", "存在重复文本ID", task_id
        if len(text_list) > settings["TASK_LIMITATION"]:
            return "1003", "单次质检量过大", task_id

        dcc_bill_list = get_bill_list(dcc_list, history_engine="dcc_mid_bill_history", today_engine="dcc_mid_bill")
        if dcc_list and not dcc_bill_list:
            return "1002", "请提供有效700话单Id", task_id
    except ClickHouseUnavailable:
        logger.error("[%s]-appId:%s-clickhouse unavailable while querying bills", task_id, app_id)
        return "1001", "话单服务暂不可用，请稍后重试", task_id

    if dcc_list and id_list:
        return "1002", "callIdList和dccList无法同时使用", task_id

    customized_param = json_data.get("customizedParam")
    process_id = json_data.get("customizedDetectionId", "")
    if not process_id:
        return "1001", "流程ID为空", task_id

    collection = mongodb["scheduler"]["process"]
    process = collection.find_one(filter={"processId": process_id})
    if not process:
        return "1001", "无效流程ID", task_id
    ai_models = process.get("aiModels").split(",")

    app_info = g.app_info

    main_url = json_data.get("taskCallBackUrl", "")
    sub_url = json_data.get("subTaskCallBackUrl", "")
    task_data = json_data.get("data", "")

    is_asr = "1" if "asr" in ai_models else "0"
    asr_type = json_data.get("transcriptionType", "0")

    is_word_match = "1" if "keyword" in ai_models else "0"
    word_package_id = json_data.get("wordPackageId", "")
    if "keyword" in ai_models and not word_package_id:
        return "1001", "词袋Id为空", task_id
    is_emotion_analyse = "1" if "emotion" in ai_models else "0"
    emotion_channel = json_data.get("emotionChannel", "")
    if "emotion" in ai_models and not emotion_channel:
        return "1001", "emotionChannel为空", task_id
    is_scene_compare = "1" if "scenario" in ai_models else "0"
    expected_scene = json_data.get("sceneConsistency", "0")

    is_script_compare = "1" if "script" in ai_models else "0"
    script_id = json_data.get("scriptId", "")
    if "script" in ai_models and not script_id:
        return "1001", "话术ID为空", task_id

    is_vfd = "1" if "vfd" in ai_models else "0"
    is_dify = "1" if "dify" in ai_models else "0"

    is_keyword_analyse = "1" if "keywordAnalysis" in ai_models else "0"
    fd_scene = json_data.get("fraudDetectionScene", "0")

    is_deepseek_analyse = "1" if "deepseek" in ai_models or "deepSeek" in ai_models else "0"
    deepseek_prompt_id = json_data.get("deepSeekPromptId", "")
    if ("deepseek" in ai_models or "deepSeek" in ai_models) and not deepseek_prompt_id:
        return "1001", "deepSeek提示词ID为空", task_id

    is_qwen_analyse = "1" if "qwen" in ai_models else "0"
    qwen_prompt_id = json_data.get("qwenPromptId", "")
    if "qwen" in ai_models and not qwen_prompt_id:
        return "1001", "qwen提示词ID为空", task_id
    is_rt_fraud_review = "1" if "rtFraudReview" in ai_models else "0"
    is_market_call_analyse = "1" if "marketCall" in ai_models else "0"

    # 涉黄涉暴涉政涉赌毒分析
    is_porn_analyse = json_data.get("pornAnalysis", "0")
    is_politics_analyse = json_data.get("politicsAnalysis", "0")
    is_violence_analyse = json_data.get("violenceAnalysis", "0")
    is_gambledrug_analyse = json_data.get("gambleDrugAnalysis", "0")

    is_regex_analyse = "1" if "wordRegex" in ai_models else "0"
    regex_id = json_data.get("regexId", "")
    if "wordRegex" in ai_models and not regex_id:
        return "1001", "正则规则ID为空", task_id
    # 身份比对
    is_identity_compare = "1" if "identity" in ai_models else "0"
    identity_preset = json_data.get("identityPreset", "")
    if "identity" in ai_models and not identity_preset:
        return "1001", "身份比对值为空", task_id
    # 词组匹配
    is_word_analyse = "1" if "wordGroup" in ai_models else "0"
    word_group_id = json_data.get("wordGroupId", "")
    if "wordGroup" in ai_models and not word_group_id:
        return "1001", "词组Id为空", task_id
    # AIGS
    is_aigs_analyse = "1" if "aigs" in ai_models else "0"
    # 成交分类
    is_deal_class = "1" if "deal" in ai_models else "0"
    # RAG检索
    is_rag_search = "1" if "rag" in ai_models else "0"
    rag_top_k = json_data.get("ragTopK")
    if "rag" in ai_models and not rag_top_k:
        return "1001", "RAG参数错误", task_id
    # 关键词反诈（低阈值）
    is_keyword_analyse_lt = "1" if "keywordAnalysisLT" in ai_models else "0"

    # BGE分类
    is_bge_classifier = "1" if "bgeClassifier" in ai_models else "0"
    classifiers = json_data.get("classifiers", "")
    bge_rule_id = json_data.get("bgeRuleId", "")
    if "bgeClassifier" in ai_models and not classifiers:
        return "1001", "BGE模型为空", task_id
    if "bgeClassifier" in ai_models and not bge_rule_id:
        return "1001", "BGE规则ID为空", task_id
    
    # 重庆行业卡反诈
    is_cq_fraud_check = "1" if "cqAnti" in ai_models else "0"

    is_cq_agent_review = "1" if "cqAgent" in ai_models else "0"
    cq_agent_use_tools = json_data.get("cqAgentUseTools")
    cq_agent_use_fast = json_data.get("cqAgentUseFast")
    if cq_agent_use_tools is not None:
        cq_agent_use_tools = int(cq_agent_use_tools)
    if cq_agent_use_fast is not None:
        cq_agent_use_fast = int(cq_agent_use_fast)

    user_data = json_data.get("userData", {})
    
    # 调度系统参数
    parameter, err = prepare_scheduler_params(json_data.copy())
    if err:
        return "1001", err, task_id

    if bill_list:
        # 获取中间号企业信息
        get_common_info()
    if dcc_bill_list:
        get_common_info_v2()

    priority = app_info["priority"]

    opt_time = datetime.datetime.now()
    create_time = opt_time.strftime("%Y-%m-%d %H:%M:%S")

    result_list, cache_items = _build_sub_task_payloads(
        task_id, process_id, json_data, create_time,
        url_list, bill_list, dcc_bill_list, text_list=text_list,
        app_id=app_id, user_data=user_data,
    )

    total = len(result_list)
    logger.info(f"[{task_id}]-appId:{app_id}-urlTotal:{len(url_list)}-billTotal:{len(bill_list)}-dccTotal:{len(dcc_bill_list)}-textTotal:{len(text_list)}-total:{total}")

    # 是否实时质检
    is_stream = user_data.get("isStream", False)
    # 生成requestID
    request_id = uuid.uuid1().hex
    request_data = {"requestId": request_id, "data": result_list, "processId": process_id,
                    "priority": priority, "transactionId": int(task_id), "isStream": is_stream}
    if parameter:
        request_data["parameter"] = parameter

    # 子任务缓存写入 + rule 查询/新增 + task 新增 + QARULE 缓存 + MQ 发布
    # 合并为一条异步任务执行，降低接口耗时；task_id 同步返回，落库成败通过指标兜底。
    _submit_async_task(
        _persist_task_and_cache,
        cache_items,
        task_id, app_id, total, create_time, is_dcc, app_info,
        process_id, priority, main_url, sub_url, task_data,
        is_asr=is_asr, asr_type=asr_type,
        is_dify=is_dify,
        is_word_match=is_word_match, word_package_id=word_package_id,
        is_emotion_analyse=is_emotion_analyse, emotion_channel=emotion_channel,
        is_scene_compare=is_scene_compare, expected_scene=expected_scene,
        is_script_compare=is_script_compare, script_id=script_id,
        is_vfd=is_vfd, is_keyword_analyse=is_keyword_analyse, fd_scene=fd_scene,
        is_deepseek_analyse=is_deepseek_analyse, deepseek_prompt_id=deepseek_prompt_id,
        is_qwen_analyse=is_qwen_analyse, qwen_prompt_id=qwen_prompt_id,
        is_rt_fraud_review=is_rt_fraud_review, is_market_call_analyse=is_market_call_analyse,
        is_porn_analyse=is_porn_analyse, is_politics_analyse=is_politics_analyse,
        is_violence_analyse=is_violence_analyse, is_gambledrug_analyse=is_gambledrug_analyse,
        is_identity_compare=is_identity_compare, identity_preset=identity_preset,
        is_word_analyse=is_word_analyse, word_group_id=word_group_id,
        is_regex_analyse=is_regex_analyse, regex_id=regex_id,
        is_aigs_analyse=is_aigs_analyse, is_deal_class=is_deal_class,
        is_rag_search=is_rag_search, rag_top_k=rag_top_k, is_keyword_analyse_lt=is_keyword_analyse_lt,
        is_bge_classifier=is_bge_classifier, classifiers=classifiers, bge_rule_id=bge_rule_id,
        is_cq_fraud_check=is_cq_fraud_check,
        is_cq_agent_review=is_cq_agent_review,
        cq_agent_use_tools=cq_agent_use_tools, cq_agent_use_fast=cq_agent_use_fast,
        opt_time=opt_time, request_data=request_data, user_data=user_data,
    )
    return "0000", "success", task_id


@create_task_latency.time()
def create_task_of_text(json_data, process_id):
    """
    创建文件类型质检任务
    :param process_id:
    :param json_data:
    :return:
    """
    task_id = str(get_next_id())
    app_id = json_data.get("channelId")
    text_list = json_data.pop("textList", [])
    logger.info(f"[{task_id}]-appId:{app_id}-request:{json_data}")

    if len(text_list) > settings["TASK_LIMITATION"]:
        return "1003", "单次质检量过大", task_id

    if len(text_list) > len({text["id"] for text in text_list}):
        return "1001", "存在重复文本组ID", task_id

    is_similarity_analyse = json_data.get("contentSimilarityAnalyse", "0")
    text_length = json_data.get("textLength", "30")
    head_tail = json_data.get("headTail", "0")
    text_threshold = json_data.get("textThreshold", "0.60")

    main_url = json_data.get("taskCallBackUrl", "")
    sub_url = json_data.get("subTaskCallBackUrl", "")
    task_data = json_data.get("data", "")
    user_data = json_data.get("userData", {})

    app_info = g.app_info
    priority = app_info["priority"]
    qa_type = app_info.get("qa_type")

    opt_time = datetime.datetime.now()
    create_time = opt_time.strftime("%Y-%m-%d %H:%M:%S")

    result_list = []
    cache_items = []
    for item in text_list:
        data_id = str(item["id"])
        text_groups = item["groups"]
        remark1 = item.get("remark1", "")
        remark2 = item.get("remark2", "")
        remark3 = item.get("remark3", "")
        remark4 = item.get("remark4", "")
        # 缓存话单数据到redis，用于补充质检结果数据
        data = {"data_id": data_id, "task_id": task_id, "app_id": app_id,
                "create_time": create_time, "data": task_data,
                "process_id": process_id, "text": json.dumps(text_groups),
                "qa_mobile_id": app_info["qa_mobile_id"], "qa_channel_id": app_info["qa_channel_id"],
                "qa_company_id": app_info["qa_company_id"], "qa_customer_id": app_info["id"],
                "remark1": remark1, "remark2": remark2, "remark3": remark3, "remark4": remark4,
                "qa_type": qa_type}
        key = "{}:{}".format(task_id, item["id"])
        cache_items.append((key, json.dumps(data)))

        result_list.append({"dataId": item["id"], "groups": text_groups})

    total = len(result_list)
    text_total = len(text_list)
    logger.info(f"[{task_id}]-appId:{app_id}-textTotal:{text_total}-total:{total}")

    # 调度系统参数
    parameter, err = prepare_scheduler_params(json_data.copy())
    if err:
        return "1001", err, task_id

    # 是否实时质检
    is_stream = user_data.get("isStream", False)
    # 生成requestID
    request_id = uuid.uuid1().hex
    request_data = {"requestId": request_id, "data": result_list, "processId": process_id,
                    "priority": priority, "transactionId": int(task_id), "isStream": is_stream}
    if parameter:
        request_data["parameter"] = parameter

    # 子任务缓存写入 + rule 查询/新增 + task 新增 + QARULE 缓存 + MQ 发布，合并为一条异步任务执行
    _submit_async_task(
        _persist_task_and_cache,
        cache_items,
        task_id, app_id, total, create_time, False, app_info,
        process_id, priority, main_url, sub_url, task_data,
        is_similarity_analyse=is_similarity_analyse, text_length=text_length,
        head_tail=head_tail, text_threshold=text_threshold,
        opt_time=opt_time, request_data=request_data, user_data=user_data,
    )
    return "0000", "success", task_id


def get_server_url_v2(location):
    sql = "select * from file_down_host where host_type = 2 and location = :location"
    result_list = select_in_mysql(sql, engine="dcc_conf", params={"location": location})
    if result_list:
        return result_list[0]["host"]
    return ""


def get_common_info_v2():
    sql_user = "select id,record_file_type from config_user"
    user_list = select_in_mysql(sql_user, engine="dcc_conf")
    user_info = {str(item["id"]): item["record_file_type"] for item in user_list}

    sql_application = "select app_id,scenario,record_file_type,is_app_config from config_application"
    application_list = select_in_mysql(sql_application, engine="dcc_conf")
    application_info = {str(item["app_id"]): item for item in application_list}

    g.user_info = user_info
    g.application_info = application_info


def build_url_v2(bill):
    """
    700录音下载地址
    /internal/media/{userid}/{msid}/{callid}/{timestamp}/{nfsid}/{name}
    """
    call_id = bill["id"]
    location = bill["location"]

    server_url = get_server_url_v2(location)
    if not server_url:
        return ""

    user_id = bill["user_id"]
    m_server_id = bill["m_server_id"]
    nfs_id = bill["nfs_id"]
    record_suffix = "wav" if bill["record_type"] == 1 else "mp3"
    datetime_str = bill["handle_start_time"].strftime("%Y%m%d%H%M%S")
    ts = int(bill["handle_start_time"].timestamp())
    # {callid}_{YYYYMMDDhhmmss}.{suffix}
    filename = "{}_{}.{}".format(call_id, datetime_str, record_suffix)

    url_call = "{}/internal/media/{}/{}/{}/{}/{}/{}".format(server_url, user_id, m_server_id, call_id, ts, nfs_id, filename)
    return url_call


def save_scripts(name, script_list, app_id):
    with session_scope("mid_strategy") as ss:
        group = QaScriptGroup()
        group.app_id = app_id
        group.name = name
        ss.add(group)
        ss.flush()
        group_id = group.id

        data_list = []
        for script in script_list:
            qa_script = QaScript()
            qa_script.app_id = app_id
            qa_script.script = script
            qa_script.group_id = group_id
            ss.add(qa_script)
            ss.flush()
            script_id = qa_script.id

            data_list.append({"scriptId": str(script_id), "text": script})

        data = {"id": group_id, "script_list": data_list, "app_id": app_id, "name": name}
    set_script_group(group_id, data)
    return group_id


def map_scene_codes_to_word_package_ids(scene_codes, session=None):
    """
    将专线场景编码映射为词袋 ID（逗号拼接，去重保序）。
    :return: (word_package_id, missing_codes)
    """
    if not scene_codes:
        return "", []

    def _resolve(ss):
        scenes = ss.execute(
            select(EnterpriseScene).where(EnterpriseScene.code.in_(list(scene_codes)))
        ).scalars().all()
        code_map = {item.code: item.word_package_id or "" for item in scenes}
        missing = [code for code in scene_codes if code not in code_map]
        if missing:
            return "", missing
        package_ids = []
        seen = set()
        for code in scene_codes:
            for pid in code_map[code].split(","):
                pid = pid.strip()
                if pid and pid not in seen:
                    seen.add(pid)
                    package_ids.append(pid)
        return ",".join(package_ids), []

    if session is not None:
        return _resolve(session)
    with session_scope("user_qc") as ss:
        return _resolve(ss)


def clone_qa_rule_for_customer(
    template_rule_id,
    *,
    app_id,
    nickname,
    qa_mobile_id,
    qa_channel_id,
    qa_company_id,
    qa_customer_id,
    word_package_id,
    script_id,
):
    """
    以 qa_subject.rule_id 对应规则为模板，新建一条绑定到新企业的 qa_rule。
    场景词袋与话术覆盖为新企业配置，expected_scene 保持模板原值。
    """
    with session_scope() as ss:
        template = ss.get(QaRule, template_rule_id)
        if not template:
            return None
        rule = QaRule()
        for col in QaRule.__table__.columns:
            if col.name in ("id", "create_time", "update_time"):
                continue
            setattr(rule, col.name, getattr(template, col.name))
        now = datetime.datetime.now()
        rule.app_id = app_id
        rule.name = f"{nickname}-质检规则" if nickname else (template.name or "质检规则")
        rule.qa_mobile_id = qa_mobile_id
        rule.qa_channel_id = qa_channel_id
        rule.qa_company_id = qa_company_id
        rule.qa_customer_id = qa_customer_id
        rule.word_package_id = word_package_id or ""
        rule.script_id = str(script_id) if script_id else ""
        rule.creator = "system"
        rule.modifier = "system"
        rule.status = "1"
        rule.rule_version = "0"
        rule.is_default = "1"
        if not rule.first_execute_time:
            rule.first_execute_time = now
        if not rule.next_execute_time:
            rule.next_execute_time = now
        ss.add(rule)
        ss.flush()
        new_rule_id = rule.id
        cache_data = rule.to_dict()
    set_qa_rule(new_rule_id, cache_data)
    notify_cache_reload("rule")
    etcd_config = settings["ETCD_CONFIG"]
    update_version(etcd_config["rule_key"], etcd_config["all_addrs"])
    logger.info(
        "clone qa_rule from %s -> %s, customer_id=%s, word_package_id=%s, script_id=%s",
        template_rule_id, new_rule_id, qa_customer_id, word_package_id, script_id,
    )
    return new_rule_id


def update_scripts(group_id, opt, script, app_id):
    logger.info(f"app_id:{app_id}, groupId:{group_id}, opt:{opt}, script:{script}")
    with session_scope("mid_strategy") as ss:
        group = ss.execute(select(QaScriptGroup).filter_by(id=group_id, app_id=app_id)).scalars().first()
        if not group:
            return {"code": "1002", "message": "该话术组不存在"}

        if opt == "1":
            qa_script = ss.execute(select(QaScript).filter_by(group_id=group_id, script=script)).scalars().first()
            if qa_script:
                return {"code": "1002", "message": "该话术已存在"}
            # 新增话术
            qa_script = QaScript()
            qa_script.app_id = app_id
            qa_script.script = script
            qa_script.group_id = group_id
            ss.add(qa_script)
        elif opt == "0":
            # 删除话术
            total = ss.scalar(select(func.count(QaScript.id)).filter_by(group_id=group_id, app_id=app_id, script=script))
            if not total:
                return {"code": "1003", "message": "该话术不存在"}
            # 删除话术
            ss.execute(delete(QaScript).filter_by(group_id=group_id, app_id=app_id, script=script))
    load_scripts(group_id)
    return {"code": "0000", "message": "success"}


def create_task_by_rule(data, process_id, priority):
    app_info = g.app_info
    offline_priority = app_info["priority"]
    realtime_priority = app_info["stream_priority"]
    if app_info["qa_type"] == 1:
        if app_info["is_mid_qc"] == 1:
            return create_task_by_ability(data, process_id, priority=priority)
        else:
            return create_task_by_ability(data, process_id, priority=offline_priority)

    user_data = data.get("userData", {})
    route = resolve_segment_route_from_user_data(user_data, app_info)
    if route == SEGMENT_ROUTE_OFFLINE:
        code, message, task_id = create_task_by_ability(data, process_id, priority=offline_priority)
    elif route == SEGMENT_ROUTE_NO_AI:
        code, message, task_id = write_result_to_db(data, process_id)
    elif route == SEGMENT_ROUTE_REALTIME:
        code, message, task_id = create_task_by_ability(data, process_id, priority=realtime_priority)
    elif route == SEGMENT_ROUTE_NEAR_REALTIME:
        code, message, task_id = create_task_by_ability(data, process_id, priority=offline_priority)
    else:
        task_id = str(get_next_id())
        logger.error("[%s] unknown segment route: %s", task_id, route)
        return "9999", f"unknown segment route:{route}", task_id

    logger.info(f"[{task_id}]-route:{route}")
    return code, message, task_id


def write_result_to_db(data, process_id):
    """
    直接写入质检结果表（异步落库，接口同步返回 task_id）
    :param data:
    :param process_id:
    :return:
    """
    task_id = str(get_next_id())
    url_list = data.pop("urlList", [])
    app_id = data.get("channelId")
    if not url_list:
        return "1001", "urlList is empty", task_id
    app_info = g.app_info
    logger.info(f"[{task_id}]-appId:{app_id}-requestData:{data}")
    # 子任务缓存写入 + 结果表落库，合并为一条异步任务执行，降低接口耗时
    _submit_async_task(
        _persist_result_to_db,
        data, process_id, task_id, url_list, app_id, app_info,
    )
    return "0000", "success", task_id


def _persist_result_to_db(data, process_id, task_id, url_list, app_id, app_info):
    """
    后台线程：写入质检结果表 + 子结果缓存 + 结果回调。
    isStream=True 时与 result_consumer 一致：按 endFlag 写入 VIQASUBRESULT / QASUBRESULT。
    """
    opt_time = datetime.datetime.now()
    create_time = opt_time.strftime("%Y-%m-%d %H:%M:%S")
    minute = opt_time.strftime("%Y%m%d%H%M")
    rclient = None
    # 结果状态设为正常
    main_status = sub_status = "1"
    completion_ratio = 100
    # 质检结果设为正常
    qc_result = "1"

    main_url = data.get("taskCallBackUrl", "")
    sub_url = data.get("subTaskCallBackUrl", "")
    try:
        user_data = data.get("userData") or {}
        has_user_data = bool(isinstance(user_data, dict) and user_data)
        is_stream = bool(user_data.get("isStream")) if has_user_data else False
        write_segment = has_user_data and is_stream
        end_flag = bool(user_data.get("endFlag")) if write_segment else False
        if write_segment:
            list_keys = [f"VIQASUBRESULT:{minute}"]
            if end_flag:
                list_keys.append(f"QASUBRESULT:{minute}")
        else:
            list_keys = [f"QASUBRESULT:{minute}"]

        with session_scope() as ss:
            task = QaTask()
            task.id = task_id
            task.rule_id = data.get("ruleId")
            task.status = main_status
            task.total = len(url_list)
            task.completion_ratio = completion_ratio
            task.error_num = 0
            task.create_time = create_time
            task.qa_type = app_info.get("qa_type")
            if has_user_data:
                if user_data.get("isStream") is not None:
                    task.is_stream = 1 if user_data.get("isStream") else 0
                if user_data.get("endFlag") is not None:
                    task.end_flag = 1 if user_data.get("endFlag") else 0
            ss.add(task)
            ss.flush()

            rclient = Redis.get_connection()
            with rclient.pipeline() as pipe:
                for item in url_list:
                    detail = prepare_sub_data(task_id, process_id, data, create_time, app_info, item=item)
                    detail["finish_time"] = create_time
                    detail["status"] = sub_status
                    detail["qc_result"] = qc_result
                    if data.get("ruleId") is not None:
                        detail["rule_id"] = data.get("ruleId")
                    # 录音时长
                    duration = int(user_data.get("duration", 0)) if has_user_data else 0
                    detail["duration"] = duration
                    detail["duration_min"] = math.ceil(duration / 60) if duration else 0
                    detail["duration_ps"] = math.ceil(duration / 6) if duration else 0
                    if has_user_data:
                        detail["user_data"] = json.dumps(user_data)
                    payload = json.dumps(detail)
                    for list_key in list_keys:
                        pipe.lpush(list_key, payload)
                pipe.execute()
        logger.info(f"[{task_id}]-write result to table success-length:{len(url_list)}")

        # 推送质检结果
        for item in url_list:
            sub_data = {"taskId": task_id, "listId": item["id"], "status": sub_status, "qcResult": qc_result}
            push_http_message(sub_url, sub_data)
        push_http_message(main_url, {"taskId": task_id, "finishRate": completion_ratio})
    except Exception as err:
        logger.error(f"[{task_id}]-appId:{app_id}-async write result failed:[{err}]")
        _mark_task_failed(QaTask, task_id, app_id, len(url_list))
    finally:
        if rclient:
            rclient.close()


def push_http_message(url, request_data):
    """
    推送http消息
    :param url: 请求地址
    :param request_data: 请求体
    :return:
    """
    if not url:
        return
    task_id = request_data["taskId"]
    try:
        response = http_pool.request(method="POST", url=url, body=json.dumps(request_data), timeout=5,
                                        headers={"Content-Type": "application/json;charset=UTF-8",
                                                "Connection": "close"})
        status_code = response.status
        if status_code != 200:
            logger.info(f"[{task_id}]-url:[{url}]-statusCode:{status_code}-requestData:[{request_data}]")
        else:
            resp_json = json.loads(response.data.decode("utf-8"))
            logger.info(f"[{task_id}]-url:[{url}]-statusCode:{status_code}-response:[{resp_json}]-requestData:[{request_data}]")
    except Exception as err:
        # 推送到延迟队列进行重推
        request_data["destination"] = url
        task_mq.publish_message(exchange=task_mq_config["repush_exchange"],
                                routing_key=task_mq_config["repush_routing_key"],
                                msg=json.dumps(request_data))
        logger.info(f"[{task_id}]-url:[{url}]-requestData:[{request_data}]-error:[{err}]")

