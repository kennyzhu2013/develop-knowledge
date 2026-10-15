import datetime
import json
import logging
import redis
import sys
import time

from apps.clickhouse import ckSelectForSQL, ckAddObjects
from apps.database import selectForSQL
from apps.general import get_call_enabled_customer_ids, resolve_strategies_with_main, is_strategy_updated_today
from config import settings

logger = logging.getLogger("vi_strategy")

# 环境切换开关：True=MySQL测试  False=StarRocks正式
USE_MYSQL = True

# 当前脚本唯一标识
SCRIPT_TAG = "4"
STRATEGY_NAME = "8"  # 长通话管控
CONTROL_DIMENSION = ""

def gen_snowflake_id(seq):
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")

if __name__ == '__main__':
    opt_time = datetime.datetime.now()
    logger.info("【长通话管控】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()

    strategy_list = selectForSQL("""
        SELECT
            id, customer_id, long_call_threshold,
            control_command, append_reason, control_valid_second, effect_time,
            is_main, app_id, status, update_time
        FROM strategy_long_call
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【长通话外呼策略】strategy_long_call 无生效策略，退出")
        sys.exit()

    strategy_list = resolve_strategies_with_main(strategy_list, call_enabled_cids, strategy_name="长通话管控策略")
    if not strategy_list:
        logger.info("【长通话外呼策略】strategy_long_call 解析后无生效策略，退出")
        sys.exit()

    # Redis连接
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
    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        is_updated_today = is_strategy_updated_today(strategy, opt_time)
        is_main = int(strategy.get("is_main", 0) or 0)

        threshold_val = strategy.get("long_call_threshold")
        if threshold_val in (None, "", " "):
            logger.error(f"【长通话外呼策略】strategy_id:{strategy_id} long_call_threshold 为空/空字符串，跳过该策略")
            continue
        try:
            threshold = int(threshold_val)
        except (ValueError, TypeError):
            logger.error(f"【长通话外呼策略】strategy_id:{strategy_id} long_call_threshold=[{threshold_val}] 无法转为整数，跳过该策略")
            continue
        op_type = strategy["control_command"]
        append_reason = strategy["append_reason"]
        control_valid_second = int(strategy.get("control_valid_second", 0))
        # 策略表 control_command → 明细表 control_command 映射
        # 策略表：0-拦截 1-放通 2-告警
        # 明细表：0-拦截 1-告警 2-放通
        _cmd_map = {0: "0", 1: "2", 2: "1"}
        control_command = _cmd_map.get(op_type, "2")
        effect_time = strategy.get("effect_time")

        # 基础时间条件 区分MySQL/StarRocks
        if USE_MYSQL:
            time_condition = """
                DATE(handle_start_time) = CURDATE()
                AND handle_start_time <= NOW()
            """
        else:
            time_condition = """
                to_date(handle_start_time) = to_date(now())
                AND handle_start_time <= now()
            """

        # 叠加策略生效时间过滤：仅统计生效时间之后的话单
        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_et = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_et}'"

        # 拼接完整查询SQL
        base_sql = f"""
            SELECT DISTINCT 
                mobile_id, channel_id, company_id,
                customer_id, app_id, caller_number, call_duration_s
            FROM {table}
            WHERE {time_condition}
              AND customer_id = {customer_id}
        """

        # 按环境选择查询入口
        if USE_MYSQL:
            bill_list = selectForSQL(base_sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(base_sql, engine="vi_bill")

        logger.info(f"【长通话外呼策略】customer_id:{customer_id} 长通话管控策略话单查询结果: {len(bill_list)} 条")

        for bill in bill_list:
            dur = bill["call_duration_s"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            if dur >= threshold:
                redis_key = f"longcall-{caller_number}"
                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "isMain": is_main,
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "25"}
                    }
                    logger.info(f"【长通话外呼策略】customer_id:{customer_id} 策略命中,call_duration_s:{dur} threshold:{threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")
                    client.set(redis_key, json.dumps(data, ensure_ascii=False))
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑不变：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    new_id = gen_snowflake_id(id_index)
                    id_index += 1

                    detail = (
                        new_id,
                        int(mobile_id) if mobile_id is not None else 0,
                        int(channel_id) if channel_id is not None else 0,
                        int(company_id) if company_id is not None else 0,
                        int(customer_id) if customer_id is not None else 0,
                        now,
                        str(caller_number) if caller_number is not None else '',
                        int(strategy_id) if strategy_id is not None else 0,
                        str(STRATEGY_NAME) if STRATEGY_NAME is not None else '',
                        str(CONTROL_DIMENSION) if CONTROL_DIMENSION is not None else '',
                        str(control_command) if control_command is not None else '',
                        str(threshold) if threshold is not None else '',
                        str(dur) if dur is not None else '',
                        now,
                        now
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
            logger.info(f"【长通话外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【长通话外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【长通话外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条")