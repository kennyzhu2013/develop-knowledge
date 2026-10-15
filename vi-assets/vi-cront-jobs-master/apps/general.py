import logging
from datetime import datetime

import requests

from apps.database import selectForSQL
from config import settings

logger = logging.getLogger(__name__)


def get_city_dict():
    city_list = selectForSQL("select id,name,code from config_city", engine="mid_auth")
    city_dict = {}
    for city in city_list:
        city_dict[city["id"]] = city
    return city_dict


def get_province_dict():
    province_list = selectForSQL("select id,name,bossid from config_province", engine="mid_auth")
    province_dict = {}
    for province in province_list:
        province_dict[province["id"]] = province
    return province_dict


def get_customer_dict():
    customer_dict = {}
    sql_customer = "SELECT * FROM config_customer"
    customer_list = selectForSQL(sql_customer, engine="mid_auth")
    for customer in customer_list:
        customer_dict[customer["id"]] = customer
    return customer_dict


def get_company_dict():
    company_dict = {}
    sql_company = "SELECT * FROM config_company"
    company_list = selectForSQL(sql_company, engine="mid_auth")
    for company in company_list:
        company_dict[company["id"]] = company
    return company_dict


def get_channel_dict():
    channel_dict = {}
    sql_channel = "SELECT * FROM auth_channel"
    channel_list = selectForSQL(sql_channel, engine="mid_auth")
    for channel in channel_list:
        channel_dict[channel["id"]] = channel
    return channel_dict


def get_mobile_dict():
    mobile_dict = {}
    sql_mobile = "SELECT * FROM auth_mobile"
    mobile_list = selectForSQL(sql_mobile, engine="mid_auth")
    for mobile in mobile_list:
        mobile_dict[mobile["id"]] = mobile
    return mobile_dict


def get_mobile_channel_dict():
    mobile_channel_dict = {}
    sql_channel = "SELECT * FROM auth_channel"
    channel_list = selectForSQL(sql_channel, engine="mid_auth")
    for channel in channel_list:
        tmp = mobile_channel_dict.get(channel["mobile_id"])
        if tmp:
            tmp.append(channel["id"])
            mobile_channel_dict[channel["mobile_id"]] = tmp
        else:
            mobile_channel_dict[channel["mobile_id"]] = [channel["id"]]
    return mobile_channel_dict


def get_eb_dict():
    sql_eb = "select customer_id,eb_id from bbss_customer where eb_id != ''"
    eb_list = selectForSQL(sql_eb, engine="mid_bussops")
    return {item["customer_id"]: item["eb_id"] for item in eb_list}


# 获取上个月同一天的日期(datetime类型), if tmp_time=2020-02-01 return 2020-01-01
def get_last_month_time(tmp_time: datetime) -> datetime:
    month = tmp_time.month
    year = tmp_time.year
    if month == 1:
        year = year - 1
        month = 12
        return tmp_time.replace(year=year, month=month)
    elif month == 3:
        day = tmp_time.day
        if (year % 4) == 0:
            if (year % 100) == 0:
                if (year % 400) == 0:
                    # 整百年能被400整除的是闰年
                    n_day = 29 if day >= 29 else day
                else:
                    # 不是闰年
                    n_day = 28 if day >= 28 else day
            else:
                # 非整百年能被4整除的为闰年
                n_day = 29 if day >= 29 else day
        else:
            # 不是闰年
            n_day = 28 if day >= 28 else day
        return tmp_time.replace(month=month-1, day=n_day)
    try:
        result = tmp_time.replace(month=month-1)
    except ValueError:
        result = tmp_time.replace(month=month-1, day=tmp_time.day-1)
    return result


def get_time_by_billid(billid) -> datetime:
    start_stmp = 1561690135700
    sequence_bit = 14
    machine_bit = 5
    datacenter_bit = 2
    datacenter_left = sequence_bit + machine_bit
    timestmp_left = datacenter_bit + datacenter_left

    time_stamp = (int(billid) >> timestmp_left) * 100 + start_stmp
    return datetime.fromtimestamp(time_stamp / 1000)


def get_location_by_billid(billid):
    return (int(billid) >> 19) & 3


def get_mail(group_name):
    # 获取收件人和抄送人
    url = "http://10.153.72.4:8201/mail/grouplist?group_name={}".format(group_name)
    resp = requests.get(url=url)
    mail_group = resp.json().get("data")
    copyer = mail_group["copyer"]
    receiver = mail_group["receiver"]
    return receiver, copyer


def get_scene_zl_dict():
    sql = "select * from config_scene_zl"
    scene_list = selectForSQL(sql, engine="mid_auth")
    return {item["yycjzlbm"]: item["yycjzl"] for item in scene_list}


def get_call_enabled_customer_ids():
    sql = "SELECT id FROM customer_tab WHERE status = 1 AND is_call = 1"
    try:
        customer_list = selectForSQL(sql, engine="user_qc")
        return {str(item["id"]) for item in customer_list}
    except Exception as e:
        logger.error(f"查询 user_qc.customer_tab is_call 开关失败: {str(e)}", exc_info=True)
        return set()

def get_status_enabled_customer_ids():
    sql = "SELECT id FROM customer_tab WHERE status = 1"
    try:
        customer_list = selectForSQL(sql, engine="user_qc")
        return {str(item["id"]) for item in customer_list}
    except Exception as e:
        logger.error(f"查询 user_qc.customer_tab status 四级组织状态可用失败: {str(e)}", exc_info=True)
        return set()


def get_customer_app_id_dict():
    sql = "SELECT id, app_id FROM customer_tab WHERE status = 1"
    try:
        customer_list = selectForSQL(sql, engine="user_qc")
        return {str(item["id"]): item.get("app_id", "") for item in customer_list}
    except Exception as e:
        logger.error(f"查询 customer_tab app_id 映射失败: {str(e)}", exc_info=True)
        return {}


def is_strategy_updated_today(strategy: dict, ref_time: datetime) -> bool:
    """判断策略 update_time 是否与 ref_time 同日。

    用于策略脚本：今天更新过的策略命中时，覆盖写 redis（而非仅首次写入），
    避免策略阈值/配置变更后 redis 残留旧值。
    update_time 字段缺失或类型不支持时返回 False，退回原逻辑。
    """
    update_time = strategy.get("update_time")
    if isinstance(update_time, datetime):
        return update_time.date() == ref_time.date()
    if isinstance(update_time, str):
        # 容错字符串格式 "YYYY-MM-DD HH:MM:SS" 或 "YYYY-MM-DD"
        return update_time[:10] == ref_time.strftime("%Y-%m-%d")
    return False


def resolve_strategies_with_main(strategies, enabled_customer_ids, key_func=None, strategy_name="策略"):
    """
    解析策略列表：优先四级独立策略(is_main=0)，回退主账户通用策略(is_main=1)。

    is_main=1 策略通过 app_id 匹配主账户，展开到该主账户下所有无独立策略的四级企业。

    Args:
        strategies: 原始策略列表，每条需包含 is_main, app_id, customer_id 字段
        enabled_customer_ids: 可用的四级企业ID集合(set of str)
        key_func: 分组键函数，默认按 customer_id 分组；
                  strategy_business_range 需按 (customer_id, dimension) 分组
        strategy_name: 策略名称，用于日志标识（如"非工作时间策略"、"高频外呼策略"）

    Returns:
        解析后的策略列表，is_main=1 策略已展开为各四级企业的独立副本
    """
    if key_func is None:
        key_func = lambda s: str(s["customer_id"])

    customer_app_id = get_customer_app_id_dict()

    # 构建 app_id -> [customer_ids] 映射（仅可用企业）
    app_id_to_customers = {}
    for cid, app_id in customer_app_id.items():
        if cid in enabled_customer_ids and app_id:
            app_id_to_customers.setdefault(app_id, []).append(cid)

    # 已有独立策略的分组键集合
    independent_keys = set()
    result = []

    # 第一轮：收集 is_main=0 的四级独立策略（按分组键去重，保留第一条）
    for s in strategies:
        if int(s.get("is_main", 0)) == 0:
            cid = str(s["customer_id"])
            if cid in enabled_customer_ids:
                k = key_func(s)
                if k not in independent_keys:
                    result.append(s)
                    independent_keys.add(k)

    # 第二轮：is_main=1 主账户通用策略，展开到无独立策略的四级企业
    expanded_detail = {}  # app_id -> {"strategy": 策略对象, "customers": [customer_ids]}
    for s in strategies:
        if int(s.get("is_main", 0)) == 1:
            app_id = s.get("app_id", "")
            if not app_id:
                continue
            for cid in app_id_to_customers.get(app_id, []):
                s_copy = dict(s)
                s_copy["customer_id"] = int(cid)
                k = key_func(s_copy)
                if k not in independent_keys:
                    result.append(s_copy)
                    independent_keys.add(k)
                    if app_id not in expanded_detail:
                        expanded_detail[app_id] = {"strategy": s, "customers": []}
                    expanded_detail[app_id]["customers"].append(cid)

    independent_count = sum(1 for s in result if int(s.get("is_main", 0)) == 0)
    expanded_count = len(result) - independent_count
    logger.info(f"[{strategy_name}]策略解析完成：原始{len(strategies)}条 → 解析后{len(result)}条"
                f"（独立策略{independent_count}条，通用策略展开{expanded_count}条）")

    if expanded_detail:
        detail_msgs = []
        for app_id, info in expanded_detail.items():
            s = info["strategy"]
            sid = s.get("id", "?")
            cids = info["customers"]
            detail_msgs.append(f"[策略id={sid},app_id={app_id}]覆盖企业[{','.join(cids)}]")
        logger.info(f"[{strategy_name}]通用策略展开明细：{'; '.join(detail_msgs)}")

    return result

"""
工信部文件接口名称
"""
BILL_QUERY = "billQuery"
BIND_QUERY = "bindQuery"
USERINFO_QUERY = "userInfoQuery"
NUMBER_TZ = "numberTz"
NUMBER_QD = "numberQd"
BILL_SYNC = "billSync"
BIND_SYNC = "bindSync"
SCENE_QD = "sceneQd"
SCENE_TZ = "sceneTz"
"""
执行结果
"""
SUCCESS = "1"
FAIL = "0"
"""
通话策略
"""


def uniops_monitor(data, key):
    """
    工信部文件接口监控
    """
    url = settings["UNIOPS_MONITOR"][key]
    try:
        _ = requests.post(url=url, json=data, timeout=3, headers={"Content-Type": "application/json;charset=UTF-8"})
    except Exception as err:
        logger.info(err)

