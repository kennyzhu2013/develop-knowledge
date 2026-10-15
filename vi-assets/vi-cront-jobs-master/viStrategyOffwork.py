import json
import logging
import redis
from datetime import datetime

from apps.database import selectForSQL
from apps.general import get_status_enabled_customer_ids, resolve_strategies_with_main
from config import settings

logger = logging.getLogger("vi_strategy")

if __name__ == '__main__':
    logger.info("【非工作时间外呼策略】定时同步")

    status_enabled_ids = get_status_enabled_customer_ids()

    # Redis 连接
    redis_config = settings["VI_STRATEGY_REDIS"]
    client = redis.Redis(
        host=redis_config["HOST"],
        port=redis_config["PORT"],
        password=redis_config["PASSWORD"],
        db=redis_config["DB"],
        decode_responses=True
    )

    # 查询字段新增 effect_time
    sql = """
        SELECT id, customer_id, control_time_range, call_threshold,
               control_command, append_reason, effect_time,
               is_main, app_id
        FROM strategy_offwork_call
        WHERE status = 1
    """
    strategy_list = selectForSQL(sql=sql, engine="vi_strategy")

    strategy_list = resolve_strategies_with_main(strategy_list, status_enabled_ids, strategy_name="非工作时间外呼策略")

    count = 0
    skip_count = 0
    # 获取当前时间，用于对比生效时间
    now = datetime.now()

    for item in strategy_list:
        customer_id = item["customer_id"]
        effect_time = item["effect_time"]

        # 时间判断：生效时间 > 当前时间 → 未到生效期，跳过
        if effect_time > now:
            logger.info(f"【非工作时间外呼策略】企业[{customer_id}] 策略未到生效时间 {effect_time}，跳过")
            skip_count += 1
            continue

        redis_key = f"offtime-{customer_id}"

        is_main = int(item.get("is_main", 0) or 0)
        data = {
            "isMain": is_main,
            "priority": 0,
            "condition": {
                "customerId": str(customer_id),
                "threshold": item["call_threshold"],
                "curfew": item["control_time_range"]
            },
            "action": {
                "opType": str(item["control_command"]),
                "appendReason": "13"
            }
        }
        logger.info(f"【非工作时间外呼策略】customer_id:{customer_id} 策略同步更新redis,redis_key:{redis_key} value:{data}")

        # 覆盖写入
        client.set(redis_key, json.dumps(data, ensure_ascii=False))
        count += 1

    client.close()
    logger.info(f"【非工作时间外呼策略】策略同步完成，本次同步生效策略：{count} 条")