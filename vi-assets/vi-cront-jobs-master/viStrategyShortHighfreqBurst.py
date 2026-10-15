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

# 当前脚本唯一标识
SCRIPT_TAG = "13"

STRATEGY_NAME = "13"
CONTROL_DIMENSION = ""

# 历史话单表
HISTORY_TABLE = "vi_bill_history.bill_history"


def gen_snowflake_id(seq):
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【短时高频突增策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【短时高频突增策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, strategy_name, customer_id, effect_object, stat_cycle_second,
            call_threshold, backtrack_cycle_day, avg_history_call_count,
            control_valid_second, control_command, append_reason,
            status, effect_time, update_time
        FROM strategy_short_highfreq_burst
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【短时高频突增策略】strategy_short_highfreq_burst 无生效策略，退出")
        sys.exit()

    logger.info(f"【短时高频突增策略】call_enabled_cids is:{call_enabled_cids}")
    logger.info(f"【短时高频突增策略】strategy_list is:{strategy_list}")

    strategy_list = [s for s in strategy_list if str(s["customer_id"]) in call_enabled_cids]
    if not strategy_list:
        logger.info("【短时高频突增策略】strategy_short_highfreq_burst 解析后无生效策略，退出")
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
    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        # call_threshold 健壮校验
        call_th_raw = strategy.get("call_threshold")
        if call_th_raw in (None, "", " "):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            call_threshold = int(call_th_raw)
        except (ValueError, TypeError):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} call_threshold=[{call_th_raw}] 无法转为整数，跳过本条策略")
            continue

        # stat_cycle_second 健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
            continue

        # backtrack_cycle_day 健壮校验
        bt_raw = strategy.get("backtrack_cycle_day")
        if bt_raw in (None, "", " "):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} backtrack_cycle_day 为空/空字符串，跳过本条策略")
            continue
        try:
            backtrack_day = int(bt_raw)
        except (ValueError, TypeError):
            logger.error(f"【短时高频突增策略】strategy_id:{strategy_id} backtrack_cycle_day=[{bt_raw}] 无法转为整数，跳过本条策略")
            continue

        # avg_history_call_count 健壮校验
        avg_raw = strategy.get("avg_history_call_count")
        try:
            avg_history_call_count = int(avg_raw) if avg_raw is not None else 0
        except (ValueError, TypeError):
            avg_history_call_count = 0

        op_type = strategy["control_command"]
        append_reason = strategy["append_reason"]
        control_valid_second = int(strategy.get("control_valid_second", 0))
        # 策略表 control_command → 明细表 control_command 映射
        _cmd_map = {0: "0", 1: "2", 2: "1"}
        control_command = _cmd_map.get(op_type, "2")
        effect_time = strategy.get("effect_time")

        # ===================== 统计周期时间窗口 =====================
        today_zero = datetime.datetime(opt_time.year, opt_time.month, opt_time.day, 0, 0, 0)
        window_start = opt_time - datetime.timedelta(seconds=stat_cycle)
        if window_start < today_zero:
            window_start = today_zero

        fmt_start = window_start.strftime("%Y-%m-%d %H:%M:%S")
        fmt_now = opt_time.strftime("%Y-%m-%d %H:%M:%S")

        time_condition = f"""
            handle_start_time >= '{fmt_start}'
            AND handle_start_time <= '{fmt_now}'
        """

        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        # ===================== 查询当前统计周期话单 =====================
        sql = f"""
            SELECT
                mobile_id,
                channel_id,
                company_id,
                customer_id,
                app_id,
                caller_number,
                COUNT(*) AS total_calls
            FROM {table}
            WHERE {time_condition}
              AND call_duration_s > 0
              AND customer_id = {customer_id}
            GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
            HAVING COUNT(*) > {call_threshold}
        """

        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【短时高频突增策略】customer_id:{customer_id} 当前周期话单查询：{len(bill_list)} 条")

        if not bill_list:
            continue

        # ===================== 查询历史话单计算回溯周期均值 =====================
        # 如果 avg_history_call_count > 0，使用配置值；否则从历史表计算
        history_avg_map = {}  # caller_number → avg_daily_calls

        if avg_history_call_count > 0:
            # 使用配置的均值，所有号码统一使用
            for bill in bill_list:
                history_avg_map[bill["caller_number"]] = avg_history_call_count
            logger.info(f"【短时高频突增策略】customer_id:{customer_id} 使用配置的回溯均值: {avg_history_call_count}")
        else:
            # 从历史表计算每个号码的日均通话数（AVG每日通话数）
            backtrack_start = opt_time - datetime.timedelta(days=backtrack_day)
            fmt_bt_start = backtrack_start.strftime("%Y-%m-%d %H:%M:%S")
            fmt_today_start = today_zero.strftime("%Y-%m-%d %H:%M:%S")

            history_sql = f"""
                SELECT
                    caller_number,
                    AVG(daily_count) AS avg_daily_calls
                FROM (
                    SELECT
                        caller_number,
                        DATE(handle_start_time) AS call_date,
                        COUNT(*) AS daily_count
                    FROM {HISTORY_TABLE}
                    WHERE handle_start_time >= '{fmt_bt_start}'
                      AND handle_start_time < '{fmt_today_start}'
                      AND call_duration_s > 0
                      AND customer_id = {customer_id}
                    GROUP BY caller_number, DATE(handle_start_time)
                ) t
                GROUP BY caller_number
            """

            if USE_MYSQL:
                history_list = selectForSQL(history_sql, engine="vi_bill")
            else:
                history_list = ckSelectForSQL(history_sql, engine="vi_bill")

            for h in history_list:
                caller = h["caller_number"]
                # 日均 = 有通话天数的平均通话数
                history_avg_map[caller] = float(h["avg_daily_calls"]) if h["avg_daily_calls"] is not None else 0

            logger.info(f"【短时高频突增策略】customer_id:{customer_id} 历史表查询到 {len(history_list)} 个号码有历史通话记录")

        # ===================== 判断触发条件 =====================
        for bill in bill_list:
            total_calls = bill["total_calls"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 获取该号码的回溯周期日均通话数
            avg_history = history_avg_map.get(caller_number, 0)

            # 触发条件：当前周期通话 > call_threshold AND 当前周期通话 > 回溯周期日均
            if total_calls > call_threshold and total_calls > avg_history:
                redis_key = f"short-highfreq-{caller_number}"

                key_exists = client.exists(redis_key)
                if (not key_exists) or is_updated_today:
                    data = {
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "30"}
                    }
                    logger.info(f"【短时高频突增策略】customer_id:{customer_id} 策略命中, "
                                f"total_calls:{total_calls}>{call_threshold} avg_history:{avg_history:.1f} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    client.set(redis_key, json.dumps(data, ensure_ascii=False))

                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入：仅在 key 不存在（首次触发）时写
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
                        str(STRATEGY_NAME),
                        str(CONTROL_DIMENSION),
                        str(control_command),
                        f"call_threshold:{call_threshold},backtrack_day:{backtrack_day},avg_history:{avg_history:.1f}",
                        f"total_calls:{total_calls}",
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
            logger.info(f"【短时高频突增策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【短时高频突增策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【短时高频突增策略】策略执行完成，命中并写入Redis：{hit_count} 条")
