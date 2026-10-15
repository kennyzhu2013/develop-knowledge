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

# 切换开关：True=MySQL测试环境  False=StarRocks正式环境
USE_MYSQL = False

SCRIPT_TAG = "3"
STRATEGY_NAME = "5"  # 超业务范围
DIM_MAP = {5: "5", 6: "6", 7: "7"}

def gen_snowflake_id(seq):
    """生成雪花ID"""
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")

if __name__ == '__main__':
    opt_time = datetime.datetime.now()
    logger.info("【超业务范围外呼策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    # 读取生效策略
    strategy_list = selectForSQL("""
        SELECT
            id, customer_id, dimension, threshold_value,
            control_command, append_reason, stat_cycle_second,
            control_valid_second, effect_object, effect_time,
            is_main, app_id, status, update_time
        FROM strategy_business_range
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【超业务范围外呼策略】strategy_business_range 无生效策略，退出")
        sys.exit()

    # 按customer_id+dimension分组：同一企业同一维度优先独立策略，回退通用策略
    strategy_list = resolve_strategies_with_main(
        strategy_list, call_enabled_cids,
        key_func=lambda s: (str(s["customer_id"]), s["dimension"]),
        strategy_name="超业务范围外呼策略"
    )
    if not strategy_list:
        logger.info("【超业务范围外呼策略】strategy_business_range 解析后无生效策略，退出")
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

    table = f"bill_{opt_time.strftime('%Y_%m_%d')}"
    detail_batch = []
    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        is_main = int(strategy.get("is_main", 0) or 0)

        dimension = strategy["dimension"]
        # threshold_value 健壮校验：None / 空字符串 / 无法转浮点数
        threshold_val = strategy.get("threshold_value")
        if threshold_val in (None, "", " "):
            logger.error(f"【超业务范围外呼策略】strategy_id:{strategy_id} threshold_value 为空(null/空字符串)，跳过该策略")
            continue
        try:
            threshold = float(threshold_val)
        except (ValueError, TypeError):
            logger.error(f"【超业务范围外呼策略】strategy_id:{strategy_id} threshold_value[{threshold_val}] 无法转换为浮点数，跳过该策略")
            continue

        op_type = strategy.get("control_command")

        # stat_cycle_second 健壮校验
        stat_cycle_val = strategy.get("stat_cycle_second")
        if stat_cycle_val in (None, "", " "):
            logger.error(f"【超业务范围外呼策略】strategy_id:{strategy_id} stat_cycle_second 为空，跳过该策略")
            continue
        try:
            stat_cycle = int(stat_cycle_val)
        except (ValueError, TypeError):
            logger.error(f"【超业务范围外呼策略】strategy_id:{strategy_id} stat_cycle_second[{stat_cycle_val}] 无法转为整数，跳过该策略")
            continue

        control_valid_second = int(strategy.get("control_valid_second", 0))
        # 策略表 control_command → 明细表 control_command 映射
        # 策略表：0-拦截 1-放通 2-告警
        # 明细表：0-拦截 1-告警 2-放通
        _cmd_map = {0: "0", 1: "2", 2: "1"}
        control_command = _cmd_map.get(op_type, "2")
        control_dim = DIM_MAP.get(dimension, "")
        effect_time = strategy.get("effect_time")

        # 按统计周期拼接时间条件
        if stat_cycle == 86400:
            if USE_MYSQL:
                time_sql = """
                    handle_start_time >= CURDATE()
                    AND handle_start_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY)
                    AND handle_start_time <= NOW()
                """
            else:
                time_sql = """
                    to_date(handle_start_time) = to_date(now())
                    AND handle_start_time <= now()
                """
        elif stat_cycle == 3600:
            if USE_MYSQL:
                time_sql = """
                    handle_start_time >= DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00')
                    AND handle_start_time < DATE_ADD(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), INTERVAL 1 HOUR)
                    AND handle_start_time <= NOW()
                """
            else:
                # StarRocks 替换 ClickHouse 函数
                time_sql = """
                    date_trunc('hour', handle_start_time) = date_trunc('hour', current_timestamp)
                    AND handle_start_time <= current_timestamp
                """
        else:
            logger.info(f"【超业务范围外呼策略】不支持的统计周期 {stat_cycle}，跳过")
            continue

        # 叠加策略生效时间
        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_sql += f" AND handle_start_time >= '{fmt_effect}'"

        # 拼接完整 CTE 分层SQL（适配 MySQL / StarRocks）
        if USE_MYSQL:
            # MySQL 写法
            sql = f"""
            WITH t1 AS (
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    substr(callee_number, 1, 9) AS called_pre_num9,
                    COUNT(DISTINCT RIGHT(callee_number, 2)) AS callee_cnt
                FROM {table}
                WHERE {time_sql}
                  AND LENGTH(callee_number) = 11 
                  AND callee_number LIKE '1%'
                  AND customer_id = {customer_id}
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number, called_pre_num9
            ),
            t2 AS (
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    ROUND(
                        SUM(IF(call_duration_s >0 and call_duration_s <= 3, 1, 0)) / SUM(IF(call_duration_s >= 0, 1, 0)),
                        2
                    ) AS short_second_rate,
                    ROUND(
                        SUM(IF(finish_state = 1, 1, 0)) / IF(COUNT(*)=0, NULL, COUNT(*)),
                        2
                    ) AS call_forward_rate
                FROM {table}
                WHERE {time_sql}
                  AND customer_id = {customer_id}
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
                HAVING COUNT(*) > 50
            )
            SELECT DISTINCT
                t1.mobile_id,
                t1.channel_id,
                t1.company_id,
                t1.customer_id,
                t1.app_id,
                t1.caller_number,
                t1.callee_cnt,
                t2.short_second_rate,
                t2.call_forward_rate
            FROM t1
            INNER JOIN t2 
                ON t1.customer_id = t2.customer_id 
                AND t1.app_id = t2.app_id 
                AND t1.caller_number = t2.caller_number
                AND t1.mobile_id = t2.mobile_id
                AND t1.channel_id = t2.channel_id
                AND t1.company_id = t2.company_id
            WHERE
                t1.callee_cnt > {threshold}
                OR t2.short_second_rate > {threshold}
                OR t2.call_forward_rate < {threshold}
            """
        else:
            # StarRocks 写法
            sql = f"""
            WITH t1 AS (
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    substr(callee_number, 1, 9) AS called_pre_num9,
                    COUNT(DISTINCT RIGHT(callee_number, 2)) AS callee_cnt
                FROM {table}
                WHERE {time_sql}
                  AND LENGTH(callee_number) = 11 
                  AND callee_number LIKE '1%'
                  AND customer_id = {customer_id}
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number, called_pre_num9
            ),
            t2 AS (
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    ROUND(
                        SUM(CASE WHEN call_duration_s > 0 AND call_duration_s <= 3 THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0),
                        2
                    ) AS short_second_rate,
                    ROUND(
                        SUM(CASE WHEN finish_state = 1 THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0),
                        2
                    ) AS call_forward_rate
                FROM {table}
                WHERE {time_sql}
                  AND customer_id = {customer_id}
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
                HAVING COUNT(*) > 50
            )
            SELECT DISTINCT
                t1.mobile_id,
                t1.channel_id,
                t1.company_id,
                t1.customer_id,
                t1.app_id,
                t1.caller_number,
                t1.callee_cnt,
                t2.short_second_rate,
                t2.call_forward_rate
            FROM t1
            INNER JOIN t2 
                ON t1.customer_id = t2.customer_id 
                AND t1.app_id = t2.app_id 
                AND t1.caller_number = t2.caller_number
                AND t1.mobile_id = t2.mobile_id
                AND t1.channel_id = t2.channel_id
                AND t1.company_id = t2.company_id
            WHERE
                t1.callee_cnt > {threshold}
                OR t2.short_second_rate > {threshold}
                OR t2.call_forward_rate < {threshold}
            """

        # 执行查询
        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【超业务范围外呼策略】customer_id:{customer_id} 超业务范围策略查询话单结果：{len(bill_list)} 条")

        for bill in bill_list:
            callee_cnt = bill["callee_cnt"]
            short_rate = bill["short_second_rate"]
            answer_rate = bill["call_forward_rate"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]


            logger.info(
                f"【超业务范围外呼策略】bill customer_id:{customer_id}| 盲呼离散度:{callee_cnt} 短秒率:{short_rate} 接通率:{answer_rate} config threshold:{threshold} dimension:{dimension}"
            )

            hit = False
            actual_val = None
            appendReason = ""

            # 维度判断 + 阈值二次校验
            if dimension == 5:
                if callee_cnt is not None and float(callee_cnt) > threshold:
                    appendReason = "22"
                    hit = True
                    actual_val = str(callee_cnt)

            elif dimension == 6:
                if short_rate is not None and float(short_rate) > threshold:
                    appendReason = "23"
                    hit = True
                    actual_val = str(short_rate)

            elif dimension == 7:
                if answer_rate is not None and float(answer_rate) < threshold:
                    appendReason = "24"
                    hit = True
                    actual_val = str(answer_rate)
            else:
                logger.info(f"【超业务范围外呼策略】未知维度: {dimension}，跳过")
                continue

            if hit:
                redis_key = f"bizrange-{caller_number}"
                logger.info(f"【超业务范围外呼策略】触发管控,appendReason:{appendReason} dimension:{dimension} threshold:{threshold}")

                # 周期内仅写入一次
                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "isMain": is_main,
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": appendReason}
                    }
                    client.set(redis_key, json.dumps(data, ensure_ascii=False))
                    logger.info(f"【超业务范围外呼策略】customer_id:{customer_id} 超业务范围策略命中,盲呼离散度:{callee_cnt} 短秒率:{short_rate} 接通率:{answer_rate} config threshold:{threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    # 设置过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑不变：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    new_id = gen_snowflake_id(id_index)
                    id_index += 1

                    # 组装CK明细数据
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
                        str(control_dim) if control_dim is not None else '',
                        str(control_command) if control_command is not None else '',
                        str(threshold) if threshold is not None else '',
                        str(actual_val) if actual_val is not None else '',
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
            logger.info(f"【超业务范围外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【超业务范围外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【超业务范围外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条")