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
SCRIPT_TAG = "12"

STRATEGY_NAME = "12"  # 闲时高频外呼
CONTROL_DIMENSION = ""


def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


def parse_idle_hours(idle_time_range):
    """解析闲时时间段字符串为小时集合
    例如 '00:00-08:00,18:00-24:00' → {0,1,2,3,4,5,6,7,18,19,20,21,22,23}
    所有时间段均同一天内，不存在跨午夜区间
    """
    hours = set()
    if not idle_time_range:
        return hours
    for segment in idle_time_range.split(","):
        segment = segment.strip()
        if not segment or "-" not in segment:
            continue
        try:
            start, end = segment.split("-", 1)
            start_h = int(start.strip().split(":")[0])
            end_h = int(end.strip().split(":")[0])
            if end_h > 24:
                end_h = 24
            for h in range(start_h, end_h):
                if 0 <= h < 24:
                    hours.add(h)
        except (ValueError, IndexError):
            continue
    return hours


if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【闲时高频外呼策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【闲时高频外呼策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, customer_id, stat_cycle_second, call_threshold,
            idle_time_range, idle_ratio_threshold, control_valid_second,
            control_command, append_reason, status, effect_time, update_time
        FROM strategy_idle_high_frequency
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【闲时高频外呼策略】strategy_idle_high_frequency 无生效策略，退出")
        sys.exit()

    # 本策略表无 is_main / app_id 字段，直接按 customer_id 过滤已开启话务质检的企业
    strategy_list = [s for s in strategy_list if str(s["customer_id"]) in call_enabled_cids]
    if not strategy_list:
        logger.info("【闲时高频外呼策略】strategy_idle_high_frequency 解析后无生效策略，退出")
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

        # 策略今天更新过：用于决定 redis 是否覆盖写入（避免阈值/配置变更后 redis 拦留旧值）
        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        # call_threshold 健壮校验
        call_th_raw = strategy.get("call_threshold")
        if call_th_raw in (None, "", " "):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            call_threshold = int(call_th_raw)
        except (ValueError, TypeError):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} call_threshold=[{call_th_raw}] 无法转为整数，跳过本条策略")
            continue

        # idle_ratio_threshold 健壮校验（varchar 存储的小数/百分比）
        ratio_raw = strategy.get("idle_ratio_threshold")
        if ratio_raw in (None, "", " "):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} idle_ratio_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            idle_ratio_threshold = float(ratio_raw)
        except (ValueError, TypeError):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} idle_ratio_threshold=[{ratio_raw}] 无法转为浮点数，跳过本条策略")
            continue

        # stat_cycle_second 健壮校验（本策略默认 86400=当天）
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
            continue

        # 解析闲时小时集合
        idle_time_range = strategy.get("idle_time_range") or ""
        idle_hours = parse_idle_hours(idle_time_range)
        if not idle_hours:
            logger.error(f"【闲时高频外呼策略】strategy_id:{strategy_id} idle_time_range=[{idle_time_range}] 解析后闲时小时为空，跳过本条策略")
            continue
        idle_hours_str = ",".join(str(h) for h in sorted(idle_hours))

        op_type = strategy["control_command"]
        control_valid_second = int(strategy.get("control_valid_second", 0))
        # 策略表 control_command → 明细表 control_command 映射
        # 策略表：0-拦截 1-放通 2-告警
        # 明细表：0-拦截 1-告警 2-放通
        _cmd_map = {0: "0", 1: "2", 2: "1"}
        control_command = _cmd_map.get(op_type, "2")
        effect_time = strategy.get("effect_time")

        # 本策略仅支持当天统计周期
        if stat_cycle == 86400:
            if USE_MYSQL:
                time_condition = """
                    handle_start_time >= CURDATE()
                    AND handle_start_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    AND handle_start_time <= NOW()
                """
            else:
                time_condition = """
                    to_date(handle_start_time) = to_date(current_timestamp)
                    AND handle_start_time <= current_timestamp
                """
        else:
            logger.info(f"【闲时高频外呼策略】不支持的统计周期 {stat_cycle}，跳过")
            continue

        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        sql = f"""
            SELECT
                mobile_id,
                channel_id,
                company_id,
                customer_id,
                app_id,
                caller_number,
                COUNT(*) AS total_calls,
                SUM(CASE WHEN HOUR(handle_start_time) IN ({idle_hours_str}) THEN 1 ELSE 0 END) AS idle_calls
            FROM {table}
            WHERE {time_condition}
              AND call_duration_s > 0
              AND customer_id = {customer_id}
            GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
        """

        # 选择对应查询方法
        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【闲时高频外呼策略】customer_id:{customer_id} 话单查询：{len(bill_list)} 条")

        for bill in bill_list:
            total_calls = bill["total_calls"]
            idle_calls = bill["idle_calls"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 闲时通话占比（total_calls 经 call_duration_s > 0 过滤后必 >=1）
            idle_ratio = (idle_calls / total_calls) if total_calls else 0.0

            # 当日总通话 > call_threshold 且 闲时通话占比 > idle_ratio_threshold → 触发
            if total_calls > call_threshold and idle_ratio > idle_ratio_threshold:
                redis_key = f"idle-highfreq-{caller_number}"

                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "29"}
                    }
                    logger.info(f"【闲时高频外呼策略】customer_id:{customer_id} 策略命中, "
                                f"total_calls:{total_calls} call_threshold:{call_threshold} "
                                f"idle_calls:{idle_calls} idle_ratio:{idle_ratio:.4f} idle_ratio_threshold:{idle_ratio_threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    client.set(redis_key, json.dumps(data, ensure_ascii=False))

                    # 设置过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    # 生成唯一ID：毫秒时间戳 + 4位自增序号
                    new_id = gen_snowflake_id(id_index)
                    id_index += 1

                    detail = (
                        new_id,  # UInt64
                        int(mobile_id) if mobile_id is not None else 0,  # Int32
                        int(channel_id) if channel_id is not None else 0,  # Int32
                        int(company_id) if company_id is not None else 0,  # Int32
                        int(customer_id) if customer_id is not None else 0,  # Int32
                        now,  # DateTime
                        str(caller_number) if caller_number is not None else '',  # String
                        int(strategy_id) if strategy_id is not None else 0,  # UInt32
                        str(STRATEGY_NAME),  # String
                        str(CONTROL_DIMENSION),  # String
                        str(control_command),  # String
                        str(call_threshold),  # String
                        str(total_calls),  # String
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
            logger.info(f"【闲时高频外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【闲时高频外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【闲时高频外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条 ")
