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
SCRIPT_TAG = "11"

STRATEGY_NAME = "11"  # 沉默号码话务突增
CONTROL_DIMENSION = ""

# 批量查询每批大小（避免 IN 列表过长）
BATCH_SIZE = 500


def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【沉默号码话务突增策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【沉默号码话务突增策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, customer_id, effect_object, stat_cycle_second,
            backtrack_cycle_day, call_threshold, control_valid_second,
            control_command, append_reason, status, effect_time, update_time
        FROM strategy_silent_call_burst
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【沉默号码话务突增策略】strategy_silent_call_burst 无生效策略，退出")
        sys.exit()

    # 该策略表无 is_main / app_id 字段，直接按 customer_id 过滤
    strategy_list = [s for s in strategy_list if str(s["customer_id"]) in call_enabled_cids]
    if not strategy_list:
        logger.info("【沉默号码话务突增策略】strategy_silent_call_burst 解析后无生效策略，退出")
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

    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        # 策略今天更新过：用于决定 redis 是否覆盖写入（避免阈值/配置变更后 redis 残留旧值）
        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        # call_threshold 健壮校验
        threshold_raw = strategy.get("call_threshold")
        if threshold_raw in (None, "", " "):
            logger.error(f"【沉默号码话务突增策略】strategy_id:{strategy_id} call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            threshold = int(threshold_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码话务突增策略】strategy_id:{strategy_id} call_threshold=[{threshold_raw}] 无法转为整数，跳过本条策略")
            continue

        # stat_cycle_second 健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【沉默号码话务突增策略】 strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码话务突增策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
            continue

        # backtrack_cycle_day 健壮校验
        backtrack_raw = strategy.get("backtrack_cycle_day")
        if backtrack_raw in (None, "", " "):
            logger.error(f"【沉默号码话务突增策略】 strategy_id:{strategy_id} backtrack_cycle_day 为空/空字符串，跳过本条策略")
            continue
        try:
            backtrack_day = int(backtrack_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码话务突增策略】strategy_id:{strategy_id} backtrack_cycle_day=[{backtrack_raw}] 无法转为整数，跳过本条策略")
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

        # 查询沉默号码列表（active_phone_tab）：
        # DATEDIFF(NOW(), active_time) > backtrack_cycle_day
        silent_sql = f"""
            SELECT biz_number, customer_id
            FROM active_phone_tab
            WHERE customer_id = {customer_id}
              AND DATEDIFF(NOW(), active_time) > {backtrack_day}
        """
        silent_rows = selectForSQL(silent_sql, engine="vi_strategy")
        if not silent_rows:
            logger.info(f"【沉默号码话务突增策略】customer_id:{customer_id} 无沉默号码，跳过")
            continue

        # 提取 biz_number 列表，用于话单查询 caller_number IN (...) 条件
        biz_numbers = [str(r["biz_number"]) for r in silent_rows if r["biz_number"]]
        if not biz_numbers:
            logger.info(f"【沉默号码话务突增策略】customer_id:{customer_id} 沉默号码列表为空，跳过")
            continue

        logger.info(f"【沉默号码话务突增策略】customer_id:{customer_id} 沉默号码数: {len(biz_numbers)}")

        # 时间窗口：NOW() - stat_cycle_second 到 NOW()
        # 如果窗口起点跨天了（早于今天00:00:00），则从今天00:00:00开始
        if USE_MYSQL:
            time_condition = f"""
                handle_start_time >= GREATEST(
                    DATE_SUB(NOW(), INTERVAL {stat_cycle} SECOND),
                    CURDATE()
                )
                AND handle_start_time <= NOW()
            """
        else:
            # StarRocks 使用 date_trunc 和 current_timestamp
            time_condition = f"""
                handle_start_time >= GREATEST(
                    date_sub(current_timestamp(), INTERVAL {stat_cycle} SECOND),
                    date_trunc('day', current_timestamp())
                )
                AND handle_start_time <= current_timestamp()
            """

        # 叠加策略生效时间过滤：仅统计生效时间之后的话单
        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        # 分批查询话单表（避免 IN 列表过长）
        bill_list = []
        for i in range(0, len(biz_numbers), BATCH_SIZE):
            batch = biz_numbers[i:i + BATCH_SIZE]
            num_list_sql = ", ".join(f"'{n.replace(chr(39), chr(39) + chr(39))}'" for n in batch)
            sql = f"""
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    COUNT(*) AS call_cnt
                FROM {table}
                WHERE {time_condition}
                  AND call_duration_s > 0
                  AND customer_id = {customer_id}
                  AND caller_number IN ({num_list_sql})
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
            """

            # 选择对应查询方法
            if USE_MYSQL:
                rows = selectForSQL(sql, engine="vi_bill")
            else:
                rows = ckSelectForSQL(sql, engine="vi_bill")
            bill_list.extend(rows)

        logger.info(f"【沉默号码话务突增策略】customer_id:{customer_id} 话单查询：{len(bill_list)} 条")

        for bill in bill_list:
            call_cnt = bill["call_cnt"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 通话次数 > 阈值 → 触发
            if call_cnt > threshold:
                redis_key = f"inactive-{caller_number}"

                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "28"}
                    }
                    logger.info(f"【沉默号码话务突增策略】customer_id:{customer_id} 策略命中,call_cnt:{call_cnt} threshold:{threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    client.set(redis_key, json.dumps(data, ensure_ascii=False))

                    # 设置过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑不变：仅在 key 不存在（首次触发）时写
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
                        str(threshold),  # String
                        str(call_cnt),  # String
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
            logger.info(f"【沉默号码话务突增策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【沉默号码话务突增策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【沉默号码话务突增策略】策略执行完成，命中并写入Redis：{hit_count} 条")
