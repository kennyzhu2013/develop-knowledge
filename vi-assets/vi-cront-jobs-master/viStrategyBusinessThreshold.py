import json
import logging
import redis
from datetime import datetime

from apps.database import selectForSQL
from apps.general import get_status_enabled_customer_ids, resolve_strategies_with_main, is_strategy_updated_today
from config import settings

logger = logging.getLogger("vi_strategy")

if __name__ == '__main__':
    logger.info("【超业务门限外呼策略】定时同步")

    status_enabled_ids = get_status_enabled_customer_ids()

    redis_config = settings["VI_STRATEGY_REDIS"]
    client = redis.Redis(
        host=redis_config["HOST"],
        port=redis_config["PORT"],
        password=redis_config["PASSWORD"],
        db=redis_config["DB"],
        decode_responses=True
    )

    # SQL 新增查询 effect_time
    sql = """
        SELECT id, customer_id, stat_cycle_second, max_call_limit,
               control_command, append_reason, control_valid_second, effect_time,
               is_main, app_id, update_time
        FROM strategy_business_threshold
        WHERE status = 1
    """
    strategy_list = selectForSQL(sql=sql, engine="vi_strategy")

    strategy_list = resolve_strategies_with_main(strategy_list, status_enabled_ids, strategy_name="超业务门限外呼策略")

    count = 0
    skip_count = 0
    now = datetime.now()

    for item in strategy_list:
        customer_id = item["customer_id"]
        effect_time = item["effect_time"]
        control_valid_second = int(item.get("control_valid_second", 0))


        is_updated_today = is_strategy_updated_today(item, now)
        is_main = int(item.get("is_main", 0) or 0)

        # 生效时间判断：未到生效时间直接跳过
        if effect_time > now:
            logger.info(f"【超业务门限外呼策略】企业[{customer_id}] 策略未到生效时间 {effect_time}，跳过")
            skip_count += 1
            continue

        redis_key = f"threshold-{customer_id}"
        key_exists = client.exists(redis_key)
        # redis 写入判断：
        # 1) 首次同步（key 不存在）
        # 2) 策略今天更新过（覆盖最新配置）
        if (not key_exists) or is_updated_today:
            data = {
                "isMain": is_main,
                "priority": 0,
                "condition": {
                    "customerId": str(customer_id),
                    "threshold": item["max_call_limit"]
                },
                "action": {
                    "opType": str(item["control_command"]),
                    "appendReason": "20"
                }
            }
            logger.info(f"【超业务门限外呼策略】customer_id:{customer_id} 超业务门限策略"
                        f"{'覆盖' if key_exists else '首次'}同步更新redis,redis_key:{redis_key} value:{data}")

            # 写入 Redis
            client.set(redis_key, json.dumps(data, ensure_ascii=False))
            if control_valid_second > 0:
                client.expire(redis_key, control_valid_second)

            count += 1

    client.close()
    logger.info(f"【超业务门限外呼策略】超业务门限策略同步完成，已生效同步：{count} 条")