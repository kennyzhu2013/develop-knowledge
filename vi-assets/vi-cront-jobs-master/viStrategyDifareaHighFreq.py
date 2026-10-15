import datetime
import json
import logging
import redis
import sys

from apps.clickhouse import ckSelectForSQL, ckAddObjects
from apps.database import selectForSQL
from apps.general import get_call_enabled_customer_ids, is_strategy_updated_today
from config import settings
import time

logger = logging.getLogger("vi_strategy")

# 环境切换开关：True=MySQL测试  False=StarRocks正式
USE_MYSQL = False

# 当前脚本唯一标识，和其他策略脚本区分，避免ID重复
# 本脚本同时处理省/市两种 effect_area，统一用 "9"
SCRIPT_TAG = "9"

CONTROL_DIMENSION = ""

# effect_area → STRATEGY_NAME 映射
# 1=省 → "9"  2=市 → "10"
_AREA_STRATEGY_NAME = {1: "9", 2: "10"}

# effect_area → Redis Key 前缀 / appendReason 映射
# 1=省 → province-highfreq- / "26"   2=市 → city-highfreq- / "27"
_AREA_REDIS_PREFIX = {1: "province-highfreq-", 2: "city-highfreq-"}
_AREA_APPEND_REASON = {1: "26", 2: "27"}


def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【跨区域大范围高频外呼策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【跨区域大范围高频外呼策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, strategy_name, customer_id, effect_area, effect_object,
            stat_cycle_second, call_threshold, area_ratio_threshold,
            control_valid_second, control_command, append_reason,
            status, effect_time, create_time, update_time
        FROM strategy_difarea_high_frequency
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【跨区域大范围高频外呼策略】strategy_difarea_high_frequency 无生效策略，退出")
        sys.exit()

    # 仅按 call_enabled_cids 过滤未开启话务质检的企业
    strategy_list = [s for s in strategy_list if str(s["customer_id"]) in call_enabled_cids]
    if not strategy_list:
        logger.info("【跨区域大范围高频外呼策略】解析后无生效策略（已过滤未开启话务质检的企业），退出")
        sys.exit()

    # Redis 连接
    redis_config = settings["VI_STRATEGY_REDIS"]
    client = redis.Redis(
        host=redis_config["HOST"],
        port=redis_config["PORT"],
        password=redis_config["PASSWORD"],
        db=redis_config["DB"],
        decode_responses=True
    )

    table = f"vi_bill.bill_{opt_time.strftime('%Y_%m_%d')}"
    detail_batch = []

    today_date = opt_time.strftime("%Y-%m-%d")
    id_prefix = today_date.replace("-", "")[2:]
    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        # 策略今天更新过：用于决定 redis 是否覆盖写入（避免阈值/配置变更后 redis 拜留旧值）
        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        # effect_area：1-省 2-市
        effect_area = int(strategy.get("effect_area", 1) or 1)
        strategy_name_val = _AREA_STRATEGY_NAME.get(effect_area, "9")
        redis_prefix = _AREA_REDIS_PREFIX.get(effect_area, "province-highfreq-")
        append_reason_val = _AREA_APPEND_REASON.get(effect_area, "26")

        # call_threshold 健壮校验
        call_raw = strategy.get("call_threshold")
        if call_raw in (None, "", " "):
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            call_threshold = int(call_raw)
        except (ValueError, TypeError):
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} call_threshold=[{call_raw}] 无法转为整数，跳过本条策略")
            continue

        # area_ratio_threshold 健壮校验（存储为字符串如 "0.40"）
        ratio_raw = strategy.get("area_ratio_threshold")
        if ratio_raw in (None, "", " "):
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} area_ratio_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            area_ratio = float(ratio_raw)
        except (ValueError, TypeError):
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} area_ratio_threshold=[{ratio_raw}] 无法转为浮点数，跳过本条策略")
            continue
        # SQL 已计算百分比，比较时 area_ratio * 100
        area_ratio_pct_threshold = area_ratio * 100

        # stat_cycle_second 健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【跨区域大范围高频外呼策略】 strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
            continue
        if stat_cycle <= 0:
            logger.error(f"【跨区域大范围高频外呼策略】strategy_id:{strategy_id} stat_cycle_second=[{stat_cycle}] 非正数，跳过本条策略")
            continue

        op_type = strategy["control_command"]
        control_valid_second = int(strategy.get("control_valid_second", 0))
        # 策略表 control_command → 明细表 control_command 映射
        # 策略表：0-拦截 1-放通 2-告警
        # 明细表：0-拦截 1-告警 2-放通
        _cmd_map = {0: "0", 1: "2", 2: "1"}
        control_command = _cmd_map.get(op_type, "2")
        effect_time = strategy.get("effect_time")

        # ===================== 动态时间窗口：NOW() - stat_cycle_second 到 NOW() =====================
        # 若窗口起点跨天（早于今天 00:00:00），则从今天 00:00:00 开始
        window_end = opt_time
        window_start = opt_time - datetime.timedelta(seconds=stat_cycle)
        today_midnight = opt_time.replace(hour=0, minute=0, second=0, microsecond=0)
        if window_start < today_midnight:
            window_start = today_midnight

        fmt_start = window_start.strftime("%Y-%m-%d %H:%M:%S")
        fmt_end = window_end.strftime("%Y-%m-%d %H:%M:%S")

        time_condition = f"""
            handle_start_time >= '{fmt_start}'
            AND handle_start_time <= '{fmt_end}'
        """

        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        # ===================== 按 effect_area 构建话单查询 SQL =====================
        if effect_area == 1:
            # 省级：call_in_province_id / call_out_province_id
            area_field_in = "biz_report_province_id"
            area_field_out = "call_out_province_id"
        else:
            # 市级：call_in_area_id / call_out_area_id
            area_field_in = "biz_report_area_code"
            area_field_out = "call_out_area_code"

        sql = f"""
            SELECT
                mobile_id,
                channel_id,
                company_id,
                customer_id,
                app_id,
                caller_number,
                COUNT(*) AS total_calls,
                SUM(CASE WHEN {area_field_out} != {area_field_in} THEN 1 ELSE 0 END) AS out_area_calls,
                ROUND(SUM(CASE WHEN {area_field_out} != {area_field_in} THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS out_area_pct
            FROM {table}
            WHERE {time_condition}
              AND call_duration_s > 0
              AND customer_id = {customer_id}
              AND {area_field_in} IS NOT NULL
            GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
        """

        # 选择对应查询方法
        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【跨区域大范围高频外呼策略】customer_id:{customer_id} effect_area:{effect_area} 话单查询：{len(bill_list)} 条")

        for bill in bill_list:
            total_calls = bill["total_calls"]
            out_area_pct = bill["out_area_pct"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 触发条件：总通话 > call_threshold AND 外省/外地市占比 > area_ratio_threshold * 100
            if total_calls > call_threshold and out_area_pct > area_ratio_pct_threshold:
                redis_key = f"{redis_prefix}{caller_number}"

                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": append_reason_val}
                    }
                    logger.info(f"【跨区域大范围高频外呼策略】customer_id:{customer_id} 策略命中,"
                                f"total_calls:{total_calls} call_threshold:{call_threshold} "
                                f"out_area_pct:{out_area_pct} area_ratio_threshold:{area_ratio_pct_threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    client.set(redis_key, json.dumps(data, ensure_ascii=False))

                    # 设置过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    new_id = gen_snowflake_id(id_index)
                    id_index += 1

                    # threshold_config / actual_value 组合两个阈值与实际值
                    detail = (
                        new_id,  # UInt64
                        int(mobile_id) if mobile_id is not None else 0,  # Int32
                        int(channel_id) if channel_id is not None else 0,  # Int32
                        int(company_id) if company_id is not None else 0,  # Int32
                        int(customer_id) if customer_id is not None else 0,  # Int32
                        now,  # DateTime
                        str(caller_number) if caller_number is not None else '',  # String
                        int(strategy_id) if strategy_id is not None else 0,  # UInt32
                        str(strategy_name_val),  # String
                        str(CONTROL_DIMENSION),  # String
                        str(control_command),  # String
                        f"{call_threshold}|{ratio_raw}",  # String - 阈值配置
                        f"{total_calls}|{out_area_pct}",  # String - 实际值
                        now,  # DateTime
                        now  # DateTime
                    )
                    detail_batch.append(detail)

    hit_count = len(detail_batch)

    if detail_batch:
        detail_sql = """
        INSERT INTO mid_bussops.call_control_detail
        (id, mobile_id,channel_id,company_id,customer_id,trigger_time,control_subjects,
         strategy_id,strategy_name,control_dimension,control_command,
         threshold_config,actual_value,create_time,update_time)
        VALUES
        """
        try:
            ckAddObjects(detail_sql, detail_batch, engine="mid_bussops")
            logger.info(f"【跨区域大范围高频外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【跨区域大范围高频外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【跨区域大范围高频外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条")
