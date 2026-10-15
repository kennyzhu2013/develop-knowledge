import datetime
import logging
import time

from apps.clickhouse import ckAddObjects, ckSelectForSQL
from apps.database import selectForSQL
from apps.general import get_customer_app_id_dict

logger = logging.getLogger("vi_strategy")

TAG = "【非话务策略明细入库】"

# 环境切换开关：True=MySQL测试  False=StarRocks正式
USE_MYSQL = False

# 话单 append_reason → (strategy_name, control_dimension) 映射
APPEND_REASON_MAP = {
    20: ("4", ""),  # 企业最大呼叫数管控 → 超业务门限
    13: ("3", ""),  # 呼叫时间段管控 → 非工作时间
}

# 策略表 control_command → 明细表 control_command 映射
# 策略表（strategy_business_threshold / strategy_offwork_call）：
#   0-拦截  1-放通  2-告警
# 明细表（call_control_detail.control_command）：
#   0-拦截  1-告警  2-放通
CONTROL_COMMAND_MAP = {0: "0", 1: "2", 2: "1"}

# 默认值
DEFAULT_STAT_CYCLE = 86400       # 超业务门限默认统计周期：1天
DEFAULT_TIME_RANGE = "22:00‑24:00,00:00‑8:00"  # 非工作时间默认范围


def gen_snowflake_id(seq):
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


def parse_time_to_minutes(time_str):
    """将 '08:00' 或 '8:00' 解析为分钟数"""
    parts = time_str.strip().split(':')
    h = int(parts[0])
    m = int(parts[1]) if len(parts) > 1 else 0
    return h * 60 + m


def compute_threshold_window(handle_time, stat_cycle_second):
    """计算超业务门限的时间窗口起始epoch"""
    epoch = int(handle_time.timestamp())
    window_start = (epoch // stat_cycle_second) * stat_cycle_second
    return window_start


def compute_offwork_window(handle_time, control_time_range):
    """计算非工作时间的时间窗口
    返回 (period_index, window_date) 或 None
    - period_index: 第几个时间段（0-based）
    - window_date: 窗口归属日期
    注意：时间段均为同日不跨天，如 08:00-12:00, 20:00-24:00
    """
    periods = [p.strip() for p in control_time_range.split(',')]
    call_date = handle_time.date()
    call_min = handle_time.hour * 60 + handle_time.minute

    for i, period in enumerate(periods):
        start_str, end_str = period.split('-')
        start_min = parse_time_to_minutes(start_str)
        end_min = parse_time_to_minutes(end_str)

        if start_min <= call_min < end_min:
            return (i, call_date)
    return None


def load_strategy_configs():
    """从策略表加载配置，构建 (customer_id → config) 和 (app_id → config) 的映射
    config 包含：strategy_id, stat_cycle_second/control_time_range, control_command, threshold_config
    """
    # 超业务门限
    threshold_cid_map = {}
    threshold_app_map = {}
    try:
        rows = selectForSQL(
            "SELECT id, customer_id, stat_cycle_second, max_call_limit, control_command, is_main, app_id "
            "FROM strategy_business_threshold WHERE status=1",
            engine="vi_strategy"
        )
        for r in rows:
            cfg = {
                "strategy_id": int(r.get("id", 0)),
                "stat_cycle_second": int(r.get("stat_cycle_second", DEFAULT_STAT_CYCLE)),
                "control_command": int(r.get("control_command", 2)),
                "threshold_config": str(r.get("max_call_limit", "")),
            }
            if int(r.get("is_main", 0)) == 1 and r.get("app_id"):
                threshold_app_map[str(r["app_id"])] = cfg
            else:
                threshold_cid_map[str(r["customer_id"])] = cfg
    except Exception as e:
        logger.error(f"{TAG}查询超业务门限策略配置失败：{str(e)}")

    # 非工作时间
    offwork_cid_map = {}
    offwork_app_map = {}
    try:
        rows = selectForSQL(
            "SELECT id, customer_id, control_time_range, call_threshold, control_command, is_main, app_id "
            "FROM strategy_offwork_call WHERE status=1",
            engine="vi_strategy"
        )
        for r in rows:
            cfg = {
                "strategy_id": int(r.get("id", 0)),
                "control_time_range": r.get("control_time_range", DEFAULT_TIME_RANGE),
                "control_command": int(r.get("control_command", 2)),
                "threshold_config": str(r.get("call_threshold", "")),
            }
            if int(r.get("is_main", 0)) == 1 and r.get("app_id"):
                offwork_app_map[str(r["app_id"])] = cfg
            else:
                offwork_cid_map[str(r["customer_id"])] = cfg
    except Exception as e:
        logger.error(f"{TAG}查询非工作时间策略配置失败：{str(e)}")

    return threshold_cid_map, threshold_app_map, offwork_cid_map, offwork_app_map


DEFAULT_THRESHOLD_CFG = {"strategy_id": 0, "stat_cycle_second": DEFAULT_STAT_CYCLE, "control_command": 2, "threshold_config": ""}
DEFAULT_OFFWORK_CFG = {"strategy_id": 0, "control_time_range": DEFAULT_TIME_RANGE, "control_command": 2, "threshold_config": ""}


def lookup_config(customer_id, app_id, cid_map, app_map, default):
    """优先按 customer_id 查找，其次按 app_id 查找，最后用默认值"""
    cfg = cid_map.get(str(customer_id))
    if cfg is None and app_id:
        cfg = app_map.get(str(app_id))
    if cfg is None:
        cfg = default
    return cfg


if __name__ == '__main__':
    now = datetime.datetime.now()
    # 处理上一个小时的数据
    hour_start = now.replace(minute=0, second=0, microsecond=0) - datetime.timedelta(hours=1)
    hour_end = hour_start + datetime.timedelta(hours=1)
    # 话单表按 hour_start 的日期确定（跨午夜时用前一天的表）
    bill_table = f"vi_bill.bill_{hour_start.strftime('%Y_%m_%d')}"

    logger.info(f"{TAG}开始：{bill_table} 时间范围 {hour_start.strftime('%Y-%m-%d %H:%M:%S')} - {hour_end.strftime('%Y-%m-%d %H:%M:%S')}")

    # 加载策略配置
    threshold_cid_map, threshold_app_map, offwork_cid_map, offwork_app_map = load_strategy_configs()
    # 四级企业 → app_id 映射（用于主账户通用策略查找）
    customer_app_id_dict = get_customer_app_id_dict()
    logger.info(f"{TAG}策略配置加载完成：超业务门限(独立:{len(threshold_cid_map)},通用:{len(threshold_app_map)})，"
                f"非工作时间(独立:{len(offwork_cid_map)},通用:{len(offwork_app_map)})")

    # 查询已存在的明细记录，用于去重
    #    去重日期按 hour_start 确定（跨午夜时00:00运行，处理的是前一天23:00-24:00的数据）
    dedup_date = hour_start.strftime('%Y-%m-%d')
    dedup_sql = f"""
    SELECT strategy_name, customer_id, trigger_time
    FROM mid_bussops.call_control_detail
    WHERE DATE(trigger_time) = '{dedup_date}'
      AND strategy_name IN ('3', '4')
    """
    try:
        existing_records = ckSelectForSQL(dedup_sql, engine="mid_bussops")
    except Exception as e:
        logger.error(f"{TAG}查询已存在明细失败：{str(e)}")
        existing_records = []

    # 构建已存在的去重 key 集合
    existing_keys = set()
    for r in existing_records:
        sn = str(r.get("strategy_name", ""))
        cid = str(r.get("customer_id", "0"))
        tt = r.get("trigger_time")
        if isinstance(tt, str):
            tt = datetime.datetime.strptime(tt, "%Y-%m-%d %H:%M:%S")

        if sn == "4":
            # 超业务门限：按 customer_id + stat_cycle_second 窗口去重
            app_id = customer_app_id_dict.get(cid, "")
            cfg = lookup_config(cid, app_id, threshold_cid_map, threshold_app_map, DEFAULT_THRESHOLD_CFG)
            window = compute_threshold_window(tt, cfg["stat_cycle_second"])
            existing_keys.add(f"threshold:{cid}:{window}")
        elif sn == "3":
            # 非工作时间：按 customer_id + 时间段去重
            app_id = customer_app_id_dict.get(cid, "")
            cfg = lookup_config(cid, app_id, offwork_cid_map, offwork_app_map, DEFAULT_OFFWORK_CFG)
            window = compute_offwork_window(tt, cfg["control_time_range"])
            if window:
                existing_keys.add(f"offwork:{cid}:{window[0]}:{window[1]}")

    logger.info(f"{TAG}当日已存在明细去重key {len(existing_keys)} 个：{existing_keys}")

    # 查询上一个小时的话单（按 handle_start_time 升序，确保取到第一条）
    bill_sql = f"""
    SELECT mobile_id, channel_id, company_id, customer_id, app_id,
           append_reason, handle_start_time
    FROM {bill_table}
    WHERE append_reason IN (13, 20)
      AND handle_start_time >= '{hour_start.strftime('%Y-%m-%d %H:%M:%S')}'
      AND handle_start_time < '{hour_end.strftime('%Y-%m-%d %H:%M:%S')}'
    ORDER BY handle_start_time ASC
    """
    try:
        if USE_MYSQL:
            bill_list = selectForSQL(bill_sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(bill_sql, engine="vi_bill")
    except Exception as e:
        logger.error(f"{TAG}查询话单失败：{str(e)}")
        exit(1)

    if not bill_list:
        logger.info(f"{TAG}无话单数据，跳过")
        exit(0)

    logger.info(f"{TAG}查询到话单 {len(bill_list)} 条")

    # 按策略配置的时间窗口去重
    detail_batch = []
    id_index = 1
    now_ts = datetime.datetime.now()
    threshold_cnt = 0
    offwork_cnt = 0

    for bill in bill_list:
        reason = int(bill["append_reason"])
        mapping = APPEND_REASON_MAP.get(reason)
        if not mapping:
            continue
        strategy_name, control_dimension = mapping

        customer_id = str(bill["customer_id"]) if bill["customer_id"] is not None else "0"
        app_id = str(bill["app_id"]) if bill.get("app_id") is not None else ""

        # 处理 handle_start_time
        trigger_time = bill["handle_start_time"]
        if isinstance(trigger_time, str):
            trigger_time = datetime.datetime.strptime(trigger_time, "%Y-%m-%d %H:%M:%S")
        if trigger_time is None:
            continue

        # 计算去重 key 和策略配置
        if reason == 20:
            cfg = lookup_config(customer_id, app_id, threshold_cid_map, threshold_app_map, DEFAULT_THRESHOLD_CFG)
            window = compute_threshold_window(trigger_time, cfg["stat_cycle_second"])
            dedup_key = f"threshold:{customer_id}:{window}"
        elif reason == 13:
            cfg = lookup_config(customer_id, app_id, offwork_cid_map, offwork_app_map, DEFAULT_OFFWORK_CFG)
            window = compute_offwork_window(trigger_time, cfg["control_time_range"])
            if window is None:
                continue
            dedup_key = f"offwork:{customer_id}:{window[0]}:{window[1]}"
        else:
            continue

        if dedup_key in existing_keys:
            continue
        existing_keys.add(dedup_key)  # 本批次内也去重

        new_id = gen_snowflake_id(id_index)
        id_index += 1

        detail = (
            new_id,
            int(bill["mobile_id"]) if bill["mobile_id"] is not None else 0,
            int(bill["channel_id"]) if bill["channel_id"] is not None else 0,
            int(bill["company_id"]) if bill["company_id"] is not None else 0,
            int(bill["customer_id"]) if bill["customer_id"] is not None else 0,
            trigger_time,
            "",                          # control_subjects（管控对象为空，按企业）
            cfg["strategy_id"],          # strategy_id
            strategy_name,
            control_dimension,
            CONTROL_COMMAND_MAP.get(cfg["control_command"], str(cfg["control_command"])), # control_command（策略表→明细表映射）
            cfg["threshold_config"],     # threshold_config
            "",                          # actual_value
            now_ts,
            now_ts,
        )
        detail_batch.append(detail)

        if reason == 20:
            threshold_cnt += 1
        elif reason == 13:
            offwork_cnt += 1

    # 写入明细表
    if detail_batch:
        detail_sql = """
        INSERT INTO mid_bussops.call_control_detail
        (id, mobile_id, channel_id, company_id, customer_id, trigger_time, control_subjects,
         strategy_id, strategy_name, control_dimension, control_command,
         threshold_config, actual_value, create_time, update_time)
        VALUES
        """
        try:
            ckAddObjects(detail_sql, detail_batch, engine="mid_bussops")
            logger.info(f"{TAG}明细入库完成：{len(detail_batch)} 条（超业务门限 {threshold_cnt} 条，非工作时间 {offwork_cnt} 条）")
        except Exception as e:
            logger.error(f"{TAG}明细入库失败：{str(e)}", exc_info=True)
    else:
        logger.info(f"{TAG}无新增明细数据")
