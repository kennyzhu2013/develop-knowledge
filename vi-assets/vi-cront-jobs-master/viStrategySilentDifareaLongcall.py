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
SCRIPT_TAG = "15"

STRATEGY_NAME = "15"
CONTROL_DIMENSION = ""

# 历史话单表
HISTORY_TABLE = "vi_bill_history.bill_history"


def gen_snowflake_id(seq):
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【沉默号码外地长通话突增策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【沉默号码外地长通话突增策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, strategy_name, customer_id, effect_area, effect_object,
            stat_cycle_second, call_threshold, cross_area_ratio_threshold,
            backtrack_cycle_day,
            long_call_def_second, long_call_threshold,
            control_valid_second, control_command, append_reason,
            status, effect_time, update_time
        FROM strategy_silent_difarea_longcall_burst
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【沉默号码外地长通话突增策略】strategy_silent_difarea_longcall_burst 无生效策略，退出")
        sys.exit()

    strategy_list = [s for s in strategy_list if str(s["customer_id"]) in call_enabled_cids]
    if not strategy_list:
        logger.info("【沉默号码外地长通话突增策略】strategy_silent_difarea_longcall_burst 解析后无生效策略，退出")
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
        effect_area = strategy.get("effect_area", 1)

        is_updated_today = is_strategy_updated_today(strategy, opt_time)

        # stat_cycle_second 健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
            continue

        # call_threshold 健壮校验
        call_th_raw = strategy.get("call_threshold")
        if call_th_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            call_threshold = int(call_th_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} call_threshold=[{call_th_raw}] 无法转为整数，跳过本条策略")
            continue

        # cross_area_ratio_threshold 健壮校验
        ratio_raw = strategy.get("cross_area_ratio_threshold")
        if ratio_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} cross_area_ratio_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            cross_area_ratio = float(ratio_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} cross_area_ratio_threshold=[{ratio_raw}] 无法转为浮点数，跳过本条策略")
            continue
        cross_area_pct_threshold = cross_area_ratio * 100

        # backtrack_cycle_day 健壮校验
        bt_raw = strategy.get("backtrack_cycle_day")
        if bt_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} backtrack_cycle_day 为空/空字符串，跳过本条策略")
            continue
        try:
            backtrack_day = int(bt_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} backtrack_cycle_day=[{bt_raw}] 无法转为整数，跳过本条策略")
            continue

        # long_call_def_second 健壮校验
        lcd_raw = strategy.get("long_call_def_second")
        if lcd_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} long_call_def_second 为空/空字符串，跳过本条策略")
            continue
        try:
            long_call_def_second = int(lcd_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} long_call_def_second=[{lcd_raw}] 无法转为整数，跳过本条策略")
            continue

        # long_call_threshold 健壮校验
        lct_raw = strategy.get("long_call_threshold")
        if lct_raw in (None, "", " "):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} long_call_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            long_call_threshold = int(lct_raw)
        except (ValueError, TypeError):
            logger.error(f"【沉默号码外地长通话突增策略】strategy_id:{strategy_id} long_call_threshold=[{lct_raw}] 无法转为整数，跳过本条策略")
            continue

        op_type = strategy["control_command"]
        append_reason = strategy["append_reason"]
        control_valid_second = int(strategy.get("control_valid_second", 0))
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

        # ===================== 查询沉默号码（active_phone_tab） =====================
        # 沉默号码：active_phone_tab 中 active_time 距今超过 backtrack_cycle_day 天
        silent_sql = f"""
            SELECT DISTINCT biz_number
            FROM active_phone_tab
            WHERE customer_id = {customer_id}
              AND DATEDIFF(CURRENT_DATE(), active_time) > {backtrack_day}
        """
        silent_rows = selectForSQL(silent_sql, engine="vi_strategy")
        silent_numbers = [str(r["biz_number"]) for r in silent_rows if r["biz_number"]]

        if not silent_numbers:
            logger.info(f"【沉默号码外地长通话突增策略】customer_id:{customer_id} 回溯周期{backtrack_day}天内无沉默号码，跳过")
            continue

        logger.info(f"【沉默号码外地长通话突增策略】customer_id:{customer_id} 沉默号码: {len(silent_numbers)} 个")

        # ===================== effect_area → 区域字段映射 =====================
        if effect_area == 1:
            # 省级
            area_field_in = "biz_report_province_id"
            area_field_out = "call_out_province_id"
        else:
            # 市级
            area_field_in = "biz_report_area_code"
            area_field_out = "call_out_area_code"

        # ===================== 查询当前统计周期话单（仅沉默号码） =====================
        # 分批查询避免 IN 列表过长
        bill_list = []
        batch_size = 200
        for i in range(0, len(silent_numbers), batch_size):
            batch = silent_numbers[i:i + batch_size]
            num_list_sql = ", ".join(f"'{n.replace(chr(39), chr(39) + chr(39))}'" for n in batch)
            sql = f"""
                SELECT
                    mobile_id,
                    channel_id,
                    company_id,
                    customer_id,
                    app_id,
                    caller_number,
                    COUNT(*) AS total_calls,
                    SUM(CASE WHEN call_duration_s > {long_call_def_second} THEN 1 ELSE 0 END) AS long_calls,
                    SUM(CASE WHEN {area_field_out} != {area_field_in} THEN 1 ELSE 0 END) AS outbound_calls,
                    ROUND(SUM(CASE WHEN {area_field_out} != {area_field_in} THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) AS out_area_pct
                FROM {table}
                WHERE {time_condition}
                  AND call_duration_s > 0
                  AND customer_id = {customer_id}
                  AND caller_number IN ({num_list_sql})
                GROUP BY mobile_id, channel_id, company_id, customer_id, app_id, caller_number
                HAVING COUNT(*) > {call_threshold}
                  AND SUM(CASE WHEN call_duration_s > {long_call_def_second} THEN 1 ELSE 0 END) > {long_call_threshold}
                  AND ROUND(SUM(CASE WHEN {area_field_out} != {area_field_in} THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) > {cross_area_pct_threshold}
            """

            if USE_MYSQL:
                rows = selectForSQL(sql, engine="vi_bill")
            else:
                rows = ckSelectForSQL(sql, engine="vi_bill")
            bill_list.extend(rows)

        logger.info(f"【沉默号码外地长通话突增策略】customer_id:{customer_id} effect_area:{effect_area} 当前周期命中话单查询：{len(bill_list)} 条")

        if not bill_list:
            continue

        # ===================== 判断触发条件 =====================
        for bill in bill_list:
            total_calls = bill["total_calls"]
            long_calls = bill["long_calls"]
            out_area_pct = bill["out_area_pct"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 触发条件已在 HAVING 中过滤，Python 侧双重保险
            if (total_calls > call_threshold and
                    long_calls > long_call_threshold and
                    out_area_pct > cross_area_pct_threshold):

                redis_key = f"inactive-difarea-longcall-{caller_number}"

                key_exists = client.exists(redis_key)
                if (not key_exists) or is_updated_today:
                    data = {
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "32"}
                    }
                    logger.info(f"【沉默号码外地长通话突增策略】customer_id:{customer_id} 策略命中, "
                                f"total_calls:{total_calls}>{call_threshold} "
                                f"long_calls:{long_calls}>{long_call_threshold} "
                                f"out_area_pct:{out_area_pct}>{cross_area_pct_threshold} "
                                f"backtrack_day:{backtrack_day}(沉默) "
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
                        f"call_threshold:{call_threshold},cross_area_ratio:{cross_area_pct_threshold},"
                        f"backtrack_day:{backtrack_day},long_call_def:{long_call_def_second},long_call_threshold:{long_call_threshold}",
                        f"total_calls:{total_calls},long_calls:{long_calls},out_area_pct:{out_area_pct}",
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
            logger.info(f"【沉默号码外地长通话突增策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【沉默号码外地长通话突增策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【沉默号码外地长通话突增策略】策略执行完成，命中并写入Redis：{hit_count} 条")
