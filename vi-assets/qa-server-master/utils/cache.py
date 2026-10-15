"""
进程内配置缓存。

设计：
- 按业务分类维护结构化字典（非 Redis key 风格）
- load_* / 启动 / ETCD 刷新：先查库组装新表，再在锁内整段替换（全量覆盖，无需扫陈旧 key）
- get_*：锁内读取并 deepcopy，避免调用方改到缓存本体
- set_*：单条写入当前段（管理接口增量更新）
- 写入前经 JsonEncoder 规范化（与旧 Redis 缓存一致），避免 datetime 等无法再 json.dumps

注意：gunicorn 多 worker 时各进程内存独立。
ETCD 变更由 master 的 EtcdWatcher 触发本进程 load_*，再通过 utils.cache_sync
写入版本文件，各 worker 轮询后全量重载，实现跨进程同步。
"""
import copy
import json
import logging
import threading
from dataclasses import dataclass, field

from utils.helpers import JsonEncoder
from utils.mysql import select_in_mysql

logger = logging.getLogger("all")

select_rule = "select * from qa_rule where {}"
select_dcc_rule = "select * from dcc_qa_rule where {}"
select_info = """select mobile.id qa_mobile_id,channel.id qa_channel_id,company.id qa_company_id,
                    company.app_key app_key,company.app_secret app_secret,customer.* 
            from customer_tab customer
            join company_tab company on company.id = customer.parent_id
            join channel_tab channel on channel.id = company.parent_id
            join mobile_tab mobile on mobile.id = channel.parent_id
            where {}
            """
select_subject = "select * from qa_subject where {}"
select_script = """select qsg.id id,qsg.app_id app_id,qsg.name name,qs.id script_id,qs.group_id group_id,qs.script script 
                from qa_script_group qsg 
                join qa_script qs on qsg.id = qs.group_id
                where {}
                """
select_word_package = "select * from qa_word_package where {}"
select_word_group = "select * from qa_word_group where {}"
select_strategy_config = "select * from qa_strategy_config where {}"


@dataclass
class CacheState:
    info_by_app_id: dict = field(default_factory=dict)
    info_by_customer_id: dict = field(default_factory=dict)
    subject_by_app_id: dict = field(default_factory=dict)
    auth_by_app_key: dict = field(default_factory=dict)
    word_packages: dict = field(default_factory=dict)
    scripts: dict = field(default_factory=dict)
    word_groups: dict = field(default_factory=dict)
    # key: (customer_id, risk_level, process_id, control_type)
    strategy_configs: dict = field(default_factory=dict)
    qa_rules: dict = field(default_factory=dict)
    dcc_qa_rules: dict = field(default_factory=dict)


_lock = threading.RLock()
_state = CacheState()


def _to_cache_value(value):
    """与旧 Redis 缓存一致：经 JsonEncoder 规范化，避免 datetime/Decimal 等无法再 dumps。"""
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return json.loads(json.dumps(value, cls=JsonEncoder))


def _clone(value):
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return copy.deepcopy(value)


def _get_from_map(mapping, key, default=None):
    with _lock:
        value = mapping.get(str(key), default)
        return _clone(value)


def _replace_sections(**sections):
    """在锁内整段替换指定缓存段，实现全量覆盖。"""
    with _lock:
        for name, value in sections.items():
            if isinstance(value, dict):
                value = {k: _to_cache_value(v) for k, v in value.items()}
            setattr(_state, name, value)


def load_cache():
    """启动或 ETCD config 变更时全量刷新（不含质检规则，规则走 load_qa_rule）。"""
    load_company_info()
    load_keywords()
    load_scripts()
    load_word_groups()
    load_strategy_configs()


def load_company_info():
    info_list = select_in_mysql(select_info.format("1=1"), engine="user_qc")
    subject_list = select_in_mysql(select_subject.format("1=1"), engine="user_qc")

    info_by_app_id = {}
    info_by_customer_id = {}
    for info in info_list:
        info_by_app_id[str(info["app_id"])] = info
        info_by_customer_id[str(info["id"])] = info

    subject_by_app_id = {}
    auth_by_app_key = {}
    for subject in subject_list:
        subject_by_app_id[str(subject["app_id"])] = subject
        auth_by_app_key[str(subject["app_key"])] = subject["app_secret"]

    _replace_sections(
        info_by_app_id=info_by_app_id,
        info_by_customer_id=info_by_customer_id,
        subject_by_app_id=subject_by_app_id,
        auth_by_app_key=auth_by_app_key,
    )
    logger.info("update company info success, length:{}".format(len(info_list)))


def load_keywords():
    """加载词袋信息到缓存（全量覆盖）。"""
    package_list = select_in_mysql("select * from qa_word_package", engine="mid_strategy")
    word_packages = {str(item["id"]): item for item in package_list}
    _replace_sections(word_packages=word_packages)
    logger.info("update word package success, length:{}".format(len(package_list)))


def set_keyword(package_id, data):
    """设置词袋内容。"""
    with _lock:
        _state.word_packages[str(package_id)] = _to_cache_value(data)


def delete_keyword(package_id):
    """删除词袋缓存。"""
    with _lock:
        _state.word_packages.pop(str(package_id), None)


def get_script_dict(group_id=None):
    """从 DB 组装话术组字典。"""
    if not group_id:
        sql = select_script.format("1=1")
        script_list = select_in_mysql(sql, engine="mid_strategy")
    else:
        sql = select_script.format("qsg.id = :group_id")
        script_list = select_in_mysql(sql, engine="mid_strategy", params={"group_id": group_id})

    script_dict = {}
    for item in script_list:
        tmp = script_dict.get(item["id"])
        if tmp:
            content_list = tmp.get("script_list", [])
            content_list.append({"scriptId": str(item["script_id"]), "text": item["script"]})
            tmp["script_list"] = content_list
        else:
            script_dict[item["id"]] = {
                "id": item["id"],
                "app_id": item["app_id"],
                "name": item["name"],
                "script_list": [{"scriptId": str(item["script_id"]), "text": item["script"]}],
            }
    return script_dict


def load_scripts(group_id=None):
    """
    加载话术。group_id 为空时全量覆盖；指定 group_id 时只更新对应组。
    """
    script_dict = get_script_dict(group_id)
    normalized = {str(k): v for k, v in script_dict.items()}
    if group_id is None:
        _replace_sections(scripts=normalized)
    else:
        with _lock:
            for k, v in normalized.items():
                _state.scripts[k] = _to_cache_value(v)
    logger.info("update script success, length:{}".format(len(script_dict)))


def set_script_group(group_id, data):
    """设置话术内容。"""
    with _lock:
        _state.scripts[str(group_id)] = _to_cache_value(data)


def get_regex_list(regex_id_list):
    sql = "select * from qa_regex where id in :regex_id_list"
    regex_list = select_in_mysql(sql, engine="mid_strategy", params={"regex_id_list": regex_id_list})
    return [{"reId": str(item["id"]), "regex": item["content"]} for item in regex_list]


def get_prompt_by_id(prompt_id):
    sql = "select * from qa_prompt where id = :prompt_id"
    prompt_list = select_in_mysql(sql, engine="mid_strategy", params={"prompt_id": prompt_id})
    return prompt_list[0]["content"] if prompt_list else ""


def get_word_group_dict(group_id=None):
    if not group_id:
        sql_group = select_word_group.format("1=1")
        word_group_list = select_in_mysql(sql_group, engine="mid_strategy")
    else:
        sql_group = select_word_group.format("id = :group_id")
        word_group_list = select_in_mysql(sql_group, engine="mid_strategy", params={"group_id": group_id})
    sql_package = select_word_package.format("id = :word_package_id")
    word_group_dict = {}
    for word_group in word_group_list:
        if not word_group["content"]:
            continue
        word_package_id_list = word_group["content"].split(",")
        word_package_list = []
        for word_package_id in word_package_id_list:
            tmp_list = select_in_mysql(
                sql_package, engine="mid_strategy", params={"word_package_id": word_package_id},
            )
            if not tmp_list:
                continue
            word_package_list.append(tmp_list[0]["words"].split(","))
        word_group_dict[word_group["id"]] = {
            "id": word_group["id"],
            "content": word_package_list,
            "app_id": word_group["app_id"],
            "name": word_group["name"],
        }
    return word_group_dict


def load_word_groups():
    """加载关键词组（全量覆盖）。"""
    word_group_dict = get_word_group_dict()
    _replace_sections(word_groups={str(k): v for k, v in word_group_dict.items()})
    logger.info("update word group success, length:{}".format(len(word_group_dict)))


def load_strategy_configs():
    """加载策略配置到缓存（全量覆盖）。"""
    strategy_list = select_in_mysql(select_strategy_config.format("1 = 1"), engine="user_qc")
    strategy_configs = {}
    for item in strategy_list:
        key = (
            int(item["customer_id"]),
            int(item["risk_level"]),
            str(item["process_id"]),
            int(item["control_type"]),
        )
        strategy_configs[key] = item
    _replace_sections(strategy_configs=strategy_configs)
    logger.info("update strategy config success, length:{}".format(len(strategy_list)))


def get_app_secret_by_key(app_key):
    subject_list = select_in_mysql(
        "select * from qa_subject where app_key = :app_key",
        engine="user_qc",
        params={"app_key": app_key},
    )
    return subject_list[0]["app_secret"] if subject_list else ""


def _get_info_from_db_by_app_id(app_id):
    info_list = select_in_mysql(
        select_info.format("customer.app_id = :info_id"),
        params={"info_id": app_id},
        engine="user_qc",
    )
    return info_list[0] if info_list else {}


def _get_info_from_db_by_customer_id(customer_id):
    info_list = select_in_mysql(
        select_info.format("customer.id = :info_id"),
        params={"info_id": customer_id},
        engine="user_qc",
    )
    return info_list[0] if info_list else {}


def _get_subject_from_db(app_id):
    subject_list = select_in_mysql(
        select_subject.format("app_id = :info_id"),
        params={"info_id": app_id},
        engine="user_qc",
    )
    return subject_list[0] if subject_list else {}


def _get_word_package_from_db(package_id):
    package_list = select_in_mysql(
        select_word_package.format("id = :info_id"),
        params={"info_id": package_id},
        engine="mid_strategy",
    )
    return package_list[0] if package_list else {}


def _get_script_from_db(group_id):
    script_dict = get_script_dict(group_id)
    return list(script_dict.values())[0] if script_dict else {}


def _get_word_group_from_db(group_id):
    word_group_dict = get_word_group_dict(group_id)
    return list(word_group_dict.values())[0] if word_group_dict else {}


def get_info_by_app_id(app_id):
    """根据 app_id 获取企业信息。"""
    try:
        info = _get_from_map(_state.info_by_app_id, app_id)
        return info if info else _get_info_from_db_by_app_id(app_id)
    except Exception:
        return _get_info_from_db_by_app_id(app_id)


def get_info_by_customer_id(customer_id):
    """根据 customer_id 获取企业信息。"""
    try:
        info = _get_from_map(_state.info_by_customer_id, customer_id)
        return info if info else _get_info_from_db_by_customer_id(customer_id)
    except Exception:
        return _get_info_from_db_by_customer_id(customer_id)


def get_name_by_customer_id(customer_id):
    company_info = get_info_by_customer_id(customer_id)
    return company_info["nickname"] if company_info else ""


def get_subject_by_app_id(app_id):
    """根据 app_id 获取主体账户信息。"""
    try:
        subject = _get_from_map(_state.subject_by_app_id, app_id)
        return subject if subject else _get_subject_from_db(app_id)
    except Exception:
        return _get_subject_from_db(app_id)


def get_name_by_app_id(app_id):
    subject = get_subject_by_app_id(app_id)
    if not subject:
        return ""
    return (
        subject["customer_nickname"]
        or subject["company_nickname"]
        or subject["channel_nickname"]
        or subject["mobile_nickname"]
    )


def get_qa_type_by_app_id(app_id):
    subject = get_subject_by_app_id(app_id)
    return subject.get("qa_type", "") if subject else ""


def get_company_secret(app_key):
    try:
        secret = _get_from_map(_state.auth_by_app_key, app_key, default="")
        return secret if secret else get_app_secret_by_key(app_key)
    except Exception:
        return get_app_secret_by_key(app_key)


def get_strategy_config(customer_id, risk_level, process_id, control_type=1):
    """根据 customer_id + risk_level + process_id + control_type 获取策略配置。"""
    if customer_id is None or risk_level in (None, "") or process_id is None:
        return {}
    try:
        customer_id = int(customer_id)
        risk_level = int(risk_level)
        process_id = str(process_id)
        control_type = int(control_type)
    except (TypeError, ValueError):
        return {}

    key = (customer_id, risk_level, process_id, control_type)
    with _lock:
        strategy = _clone(_state.strategy_configs.get(key))
    if strategy:
        return strategy if strategy.get("status") == 1 else {}

    sql = select_strategy_config.format(
        "customer_id = :customer_id and risk_level = :risk_level and process_id = :process_id "
        "and control_type = :control_type "
        "order by update_time desc, id desc limit 1"
    )
    strategy_list = select_in_mysql(
        sql,
        engine="user_qc",
        params={
            "customer_id": customer_id,
            "risk_level": risk_level,
            "process_id": process_id,
            "control_type": control_type,
        },
    )
    if not strategy_list:
        return {}
    strategy = _to_cache_value(strategy_list[0])
    with _lock:
        _state.strategy_configs[key] = strategy
    return strategy if strategy.get("status") == 1 else {}


def load_qa_rule():
    """加载质检规则到缓存（全量覆盖）。"""
    rule_list = select_in_mysql(select_rule.format("1=1"), engine="mid_strategy")
    dcc_rule_list = select_in_mysql(select_dcc_rule.format("1=1"), engine="mid_strategy")
    _replace_sections(
        qa_rules={str(item["id"]): item for item in rule_list},
        dcc_qa_rules={str(item["id"]): item for item in dcc_rule_list},
    )
    logger.info(
        "update qa rule success, length:{}, dccLength:{}".format(len(rule_list), len(dcc_rule_list))
    )


def set_qa_rule(rule_id, data):
    """设置质检规则缓存。"""
    with _lock:
        _state.qa_rules[str(rule_id)] = _to_cache_value(data)


def set_dcc_qa_rule(rule_id, data):
    """设置 DCC 质检规则缓存。"""
    with _lock:
        _state.dcc_qa_rules[str(rule_id)] = _to_cache_value(data)


def get_qa_rule_by_id(rule_id):
    """根据 rule id 获取质检规则，缓存未命中时回源 DB 并回填。"""
    result = _get_from_map(_state.qa_rules, rule_id)
    if result:
        return result
    rule_list = select_in_mysql(
        select_rule.format("id = :rule_id"),
        engine="mid_strategy",
        params={"rule_id": rule_id},
    )
    if rule_list:
        result = _to_cache_value(rule_list[0])
        set_qa_rule(rule_id, result)
        return result
    return {}


def get_dcc_qa_rule_by_id(rule_id):
    """根据 rule id 获取 DCC 质检规则，缓存未命中时回源 DB 并回填。"""
    result = _get_from_map(_state.dcc_qa_rules, rule_id)
    if result:
        return result
    dcc_rule_list = select_in_mysql(
        select_dcc_rule.format("id = :rule_id"),
        engine="mid_strategy",
        params={"rule_id": rule_id},
    )
    if dcc_rule_list:
        result = _to_cache_value(dcc_rule_list[0])
        set_dcc_qa_rule(rule_id, result)
        return result
    return {}


def rule_to_task_params(rule):
    """
    将规则字典（缓存/DB 查询结果，列名为 key）转化为 task_params。
    """
    if not rule:
        return {}
    return {
        "transcription": rule.get("is_asr"), "transcriptionType": rule.get("asr_type"),
        "wordMatching": rule.get("is_word_match"), "wordPackageId": rule.get("word_package_id"),
        "summary": rule.get("is_summarize"), "emotionAnalysis": rule.get("is_emotion_analyse"),
        "sceneAnalysis": rule.get("is_scene_compare"), "sceneConsistency": rule.get("expected_scene"),
        "scriptConsistency": rule.get("is_script_compare"), "scriptId": rule.get("script_id"),
        "forgeryDetection": rule.get("is_vfd"), "sceneRecognition": rule.get("is_scene_recognize"),
        "fraudDetection": rule.get("is_keyword_analyse"), "fusionFraudSceneCheck": rule.get("is_rt_check"),
        "rtType": rule.get("rt_svc_type"), "rtScene": rule.get("rt_expected_scene"),
        "taskCallBackUrl": rule.get("main_url"), "subTaskCallBackUrl": rule.get("sub_url"), "data": rule.get("data"),
        "channelId": rule.get("app_id"), "strategyType": rule.get("strategy_type"),
        "ruleId": rule.get("id"), "fraudDetectionScene": rule.get("fd_scene"),
        "identityComparison": rule.get("is_identity_compare"), "identityPreset": rule.get("identity_preset"),
        "voiceComparison": rule.get("is_voice_compare"), "voiceAnalysis": rule.get("is_voice_analyse"),
        "voicePrintDb": rule.get("vp_db"), "vpChannel": rule.get("vp_channel"), "vpTopn": rule.get("vp_topn"),
        "deepSeekAnalysis": rule.get("is_deepseek_analyse"), "deepSeekPromptId": rule.get("deepseek_prompt_id"),
        "aigsAnalysis": rule.get("is_aigs_analyse"), "pornAnalysis": rule.get("is_porn_analyse"),
        "politicsAnalysis": rule.get("is_politics_analyse"), "violenceAnalysis": rule.get("is_violence_analyse"),
        "gambleDrugAnalysis": rule.get("is_gambledrug_analyse"), "priority": rule.get("priority"),
        "asrEmotionTag": rule.get("asr_emotion_tag"), "asrLanguage": rule.get("asr_language"),
        "wordAnalysis": rule.get("is_word_analyse"), "wordGroupId": rule.get("word_group_id"),
        "emotionChannel": rule.get("emotion_channel"), "regexWordMatching": rule.get("is_regex_analyse"),
        "regexId": rule.get("regex_id"), "qwenInference": rule.get("is_qwen_analyse"),
        "qwenPromptId": rule.get("qwen_prompt_id"),
        "dealClass": rule.get("is_deal_class"), "ragSearch": rule.get("is_rag_search"),
        "ragTopK": rule.get("rag_top_k"),
        "fraudDetectionLT": rule.get("is_keyword_analyse_lt"),
        "bgeClassifier": rule.get("is_bge_classifier"), "classifiers": rule.get("bge_classifiers"),
        "bgeRuleId": rule.get("bge_rule_id"), "cqFraudCheck": rule.get("is_cq_fraud_check"),
        "cqAgentReview": rule.get("is_cq_agent_review"), "cqAgentUseTools": rule.get("cq_agent_use_tools"),
        "cqAgentUseFast": rule.get("cq_agent_use_fast"),
    }


def get_keyword_list(package_id_list):
    package_list = []
    for package_id in package_id_list:
        try:
            item = _get_from_map(_state.word_packages, package_id)
            if not item:
                item = _get_word_package_from_db(package_id)
        except Exception:
            item = _get_word_package_from_db(package_id)
        if item:
            package_list.append({"packageId": str(item["id"]), "word": item["words"]})
    return package_list


def get_script_list_by_group(group_id_list):
    """获取多组话术内容。"""
    script_list = []
    for group_id in group_id_list:
        try:
            group = _get_from_map(_state.scripts, group_id)
            if not group:
                group = _get_script_from_db(group_id)
        except Exception:
            group = _get_script_from_db(group_id)
        if group:
            script_list.extend(group["script_list"])
    return script_list


def get_word_group_list(group_id_list):
    group_list = []
    for group_id in group_id_list:
        try:
            item = _get_from_map(_state.word_groups, group_id)
            if not item:
                item = _get_word_group_from_db(group_id)
        except Exception:
            item = _get_word_group_from_db(group_id)
        if item:
            group_list.append(item["content"])
    return group_list


def get_bge_classifier_by_name(model_name):
    classifier_list = select_in_mysql(
        "select * from bge_classifier where model_name = :model_name",
        engine="user_qc",
        params={"model_name": model_name},
    )
    return classifier_list[0] if classifier_list else None


def get_bge_rule_by_id(rule_id):
    rule_list = select_in_mysql(
        "select * from bge_rule where id = :rule_id",
        engine="user_qc",
        params={"rule_id": rule_id},
    )
    return rule_list[0] if rule_list else None


def get_bge_rule_items_by_rule_id(rule_id):
    return select_in_mysql(
        "select * from bge_rule_item where rule_id = :rule_id order by priority",
        engine="user_qc",
        params={"rule_id": rule_id},
    )


def build_bge_scheduler_rules(rule_item_list):
    rules = []
    for item in rule_item_list:
        rule_item = {
            "name": item["sub_rule_name"],
            "priority": str(item["priority"]),
            "expression": item["expression"],
            "outputLabel": item["output_label"],
        }
        if item.get("is_default") == 1:
            rule_item["isDefault"] = True
        rules.append(rule_item)
    return rules
