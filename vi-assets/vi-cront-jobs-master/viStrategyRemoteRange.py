import datetime
import json
import logging
import redis
import sys

from apps.clickhouse import ckSelectForSQL, ckAddObjects
from apps.database import selectForSQL
from apps.general import get_call_enabled_customer_ids, resolve_strategies_with_main, is_strategy_updated_today
from config import settings
import time

logger = logging.getLogger("vi_strategy")

# 环境切换开关：True=MySQL测试环境  False=StarRocks正式环境
USE_MYSQL = False

# 当前脚本唯一标识，区分不同策略，防止ID重复
SCRIPT_TAG = "2"

# -------------------------- 策略固定配置 --------------------------
STRATEGY_NAME = "2"  # 异地大范围外呼管控
CONTROL_DIMENSION = ""

def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")

if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【异地大范围外呼策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()

    # 按你的 DDL 读取字段
    strategy_list = selectForSQL("""
        SELECT id, customer_id, area_threshold, stat_cycle_second,
               control_command, append_reason, control_valid_second, effect_time,
               is_main, app_id, status, update_time
        FROM strategy_remote_range
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【异地大范围外呼策略】strategy_remote_range 无生效策略，退出")
        sys.exit()

    strategy_list = resolve_strategies_with_main(strategy_list, call_enabled_cids, strategy_name="异地大范围外呼策略")
    if not strategy_list:
        logger.info("【异地大范围外呼策略】strategy_remote_range 解析后无生效策略，退出")
        sys.exit()

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

    # ID 分段规则初始化
    today_date = opt_time.strftime("%Y-%m-%d")
    id_prefix = today_date.replace("-", "")[2:]
    id_index = 1

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]

        # 策略今天更新过：用于决定 redis 是否覆盖写入（避免阈值/配置变更后 redis 残留旧值）
        is_updated_today = is_strategy_updated_today(strategy, opt_time)
        # is_main 标记策略来源：1=主账户通用策略展开副本，0=独立四级策略
        # 写入 redis value 供清理脚本区分独立残留 key 与主账户生效 key
        is_main = int(strategy.get("is_main", 0) or 0)

        # area_threshold 完整健壮校验
        area_raw = strategy.get("area_threshold")
        if area_raw in (None, "", " "):
            logger.error(f"【异地大范围外呼策略】strategy_id:{strategy_id} area_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            threshold = int(area_raw)
        except (ValueError, TypeError):
            logger.error(f"【异地大范围外呼策略】strategy_id:{strategy_id} area_threshold=[{area_raw}] 无法转为整数，跳过本条策略")
            continue

        # stat_cycle_second 完整健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【异地大范围外呼策略】strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【异地大范围外呼策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
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

        # ===================== 动态时间：按天 / 按小时 分环境适配 =====================
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
        elif stat_cycle == 3600:
            if USE_MYSQL:
                time_condition = """
                    handle_start_time >= DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00')
                    AND handle_start_time < DATE_ADD(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), INTERVAL 1 HOUR)
                    AND handle_start_time <= NOW()
                """
            else:
                # StarRocks 标准截断函数date_trunc，增加日期限制防止跨天
                time_condition = """
                    to_date(handle_start_time) = to_date(current_timestamp)
                    AND date_trunc('hour', handle_start_time) = date_trunc('hour', current_timestamp)
                    AND handle_start_time <= current_timestamp
                """
        else:
            logger.info(f"【异地大范围外呼策略】不支持的统计周期 {stat_cycle}，跳过")
            continue

        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        sql = f"""
            SELECT  
                mobile_id, channel_id, company_id,
                customer_id, app_id, caller_number,
                COUNT(DISTINCT call_out_area_code) AS city_cnt
            FROM {table}
            WHERE {time_condition}
              AND call_duration_s > 0
              AND customer_id = {customer_id}
            GROUP BY mobile_id,channel_id,company_id,customer_id, app_id, caller_number
        """

        # 根据环境选择查询方法
        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【异地大范围外呼策略】customer_id:{customer_id} 策略话单查询结果：{len(bill_list)} 条")

        for bill in bill_list:
            city_cnt = bill["city_cnt"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            if city_cnt > threshold:
                redis_key = f"difarea-{caller_number}"

                # ===================== 周期内只触发1次，不重复 =====================
                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "isMain": is_main,
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "21"}
                    }
                    client.set(redis_key, json.dumps(data, ensure_ascii=False))
                    logger.info(f"【异地大范围外呼策略】customer_id:{customer_id} 策略命中,city_cnt:{city_cnt} threshold:{threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    # 过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑不变：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    # 生成唯一ID：6位日期 + 脚本标识 + 4位自增序号
                   # new_id_str = f"{id_prefix}{SCRIPT_TAG}{id_index:0>4}"
                   # new_id = int(new_id_str)
                    new_id = gen_snowflake_id(id_index)
                    id_index += 1

                    # 元组首位新增 id 字段
                    detail = (
                        new_id,
                        int(mobile_id) if mobile_id is not None else 0,
                        int(channel_id) if channel_id is not None else 0,
                        int(company_id) if company_id is not None else 0,
                        int(customer_id) if customer_id is not None else 0,
                        now,  # datetime 对象
                        str(caller_number) if caller_number is not None else '',
                        int(strategy_id) if strategy_id is not None else 0,
                        str(STRATEGY_NAME) if STRATEGY_NAME is not None else '',
                        str(CONTROL_DIMENSION) if CONTROL_DIMENSION is not None else '',
                        str(control_command) if control_command is not None else '',
                        str(threshold) if threshold is not None else '',
                        str(city_cnt) if city_cnt is not None else '',  # city_cnt 是实际值
                        now,  # create_time
                        now  # update_time
                    )

                    detail_batch.append(detail)

    hit_count = len(detail_batch)

    if detail_batch:
        # 插入语句首字段添加 id
        detail_sql = """
        INSERT INTO mid_bussops.call_control_detail
        (id, mobile_id,channel_id,company_id,customer_id,trigger_time,control_subjects,
         strategy_id,strategy_name,control_dimension,control_command,
         threshold_config,actual_value,create_time,update_time)
        VALUES
        """
        try:
            ckAddObjects(detail_sql, detail_batch, engine="mid_bussops")
            logger.info(f"【异地大范围外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【异地大范围外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【异地大范围外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条")