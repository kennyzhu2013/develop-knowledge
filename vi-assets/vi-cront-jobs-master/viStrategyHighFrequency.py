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

# 环境切换开关：True=MySQL测试  False=StarRocks正式
USE_MYSQL = False

# 当前脚本唯一标识，和其他策略脚本区分，避免ID重复
SCRIPT_TAG = "1"

STRATEGY_NAME = "1"  # 短时高频
CONTROL_DIMENSION = ""

def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")

if __name__ == '__main__':
    opt_time = datetime.datetime.now()

    logger.info("【短时高频外呼策略】定时同步")

    call_enabled_cids = get_call_enabled_customer_ids()
    logger.info(f"【短时高频外呼策略】已获取开启话务质检的企业数量: {len(call_enabled_cids)}")

    strategy_list = selectForSQL("""
        SELECT
            id, customer_id, frequency_threshold, stat_cycle_second,
            control_command, append_reason, control_valid_second, effect_time,
            is_main, app_id, status, update_time
        FROM strategy_high_frequency
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("【短时高频外呼策略】strategy_high_frequency 无生效策略，退出")
        sys.exit()

    strategy_list = resolve_strategies_with_main(strategy_list, call_enabled_cids, strategy_name="短时高频外呼策略")
    if not strategy_list:
        logger.info("【短时高频外呼策略】strategy_high_frequency 解析后无生效策略，退出")
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

        # 策略今天更新过：用于决定 redis 是否覆盖写入（避免阈值/配置变更后 redis 残留旧值）
        is_updated_today = is_strategy_updated_today(strategy, opt_time)
        # is_main 标记策略来源：1=主账户通用策略展开副本，0=独立四级策略
        # 写入 redis value 供清理脚本区分独立残留 key 与主账户生效 key
        is_main = int(strategy.get("is_main", 0) or 0)

        # frequency_threshold 健壮校验
        freq_raw = strategy.get("frequency_threshold")
        if freq_raw in (None, "", " "):
            logger.error(f"【短时高频外呼策略】strategy_id:{strategy_id} frequency_threshold 为空/空字符串，跳过本条策略")
            continue
        try:
            threshold = int(freq_raw)
        except (ValueError, TypeError):
            logger.error(f"【短时高频外呼策略】strategy_id:{strategy_id} frequency_threshold=[{freq_raw}] 无法转为整数，跳过本条策略")
            continue

        # stat_cycle_second 健壮校验
        cycle_raw = strategy.get("stat_cycle_second")
        if cycle_raw in (None, "", " "):
            logger.error(f"【短时高频外呼策略】 strategy_id:{strategy_id} stat_cycle_second 为空/空字符串，跳过本条策略")
            continue
        try:
            stat_cycle = int(cycle_raw)
        except (ValueError, TypeError):
            logger.error(f"【短时高频外呼策略】strategy_id:{strategy_id} stat_cycle_second=[{cycle_raw}] 无法转为整数，跳过本条策略")
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

        # 按环境适配时间条件
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
                # StarRocks 使用 date_trunc，增加日期限定杜绝昨日同小时数据
                time_condition = """
                    to_date(handle_start_time) = to_date(current_timestamp)
                    AND date_trunc('hour', handle_start_time) = date_trunc('hour', current_timestamp)
                    AND handle_start_time <= current_timestamp
                """
        else:
            logger.info(f"【短时高频外呼策略】不支持的统计周期 {stat_cycle}，跳过")
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
                COUNT(*) AS call_cnt
            FROM {table}
            WHERE {time_condition}
              AND call_duration_s > 0
              AND customer_id = {customer_id}
            GROUP BY mobile_id,channel_id,company_id,customer_id, app_id, caller_number
        """

        # 选择对应查询方法
        if USE_MYSQL:
            bill_list = selectForSQL(sql, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql, engine="vi_bill")

        logger.info(f"【短时高频外呼策略】customer_id:{customer_id} 话单查询：{len(bill_list)} 条")

        for bill in bill_list:
            call_cnt = bill["call_cnt"]
            caller_number = bill["caller_number"]
            mobile_id = bill["mobile_id"]
            channel_id = bill["channel_id"]
            company_id = bill["company_id"]

            # 超过阈值触发
            if call_cnt >= threshold:
                redis_key = f"highfreq-{caller_number}"

                key_exists = client.exists(redis_key)
                # 今天更新过的策略：覆盖写 redis，确保最新阈值/配置生效；
                # 否则维持原逻辑：仅首次命中时写入
                if (not key_exists) or is_updated_today:
                    data = {
                        "isMain": is_main,
                        "priority": 0,
                        "condition": {"customerId": str(customer_id)},
                        "action": {"opType": str(op_type), "appendReason": "5"}
                    }
                    logger.info(f"【短时高频外呼策略】customer_id:{customer_id} 策略命中,call_cnt:{call_cnt} threshold:{threshold} "
                                f"{'覆盖' if key_exists else '首次'}写入 Redis Key：{redis_key} value:{data}")

                    client.set(redis_key, json.dumps(data, ensure_ascii=False))

                    # 设置过期时间
                    if control_valid_second > 0:
                        client.expire(redis_key, control_valid_second)

                # 明细写入逻辑不变：仅在 key 不存在（首次触发）时写
                if not key_exists:
                    now = datetime.datetime.now()
                    # 生成唯一ID：日期前缀 + 脚本标识 + 4位自增序号
                    #new_id_str = f"{id_prefix}{SCRIPT_TAG}{id_index:0>4}"
                    #new_id = int(new_id_str)
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
                        int(strategy_id) if strategy_id is not None else 0,  # UInt32 - 关键修改
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
            logger.info(f"【短时高频外呼策略】写入CK明细成功：{len(detail_batch)} 条")
        except Exception as e:
            logger.error(f"【短时高频外呼策略】写入CK明细失败: {str(e)}", exc_info=True)

    client.close()
    logger.info(f"【短时高频外呼策略】策略执行完成，命中并写入Redis：{hit_count} 条 ")
