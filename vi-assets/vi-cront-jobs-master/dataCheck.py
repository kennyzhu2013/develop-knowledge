import datetime
import json
import logging
import sys

from apps.clickhouse import ckSelectForSQL, ckAddObjects
from apps.database import selectForSQL
from config import settings

logger = logging.getLogger("vi_strategy")

# 环境切换开关：True=MySQL测试环境  False=StarRocks正式环境
USE_MYSQL = False

if __name__ == '__main__':
    opt_time = datetime.datetime.now()
    logger.info("测试查询【异地大范围外呼】数据校验")

    # 读取生效策略
    strategy_list = selectForSQL("""
        SELECT id, customer_id, area_threshold, stat_cycle_second,
               control_command, append_reason, control_valid_second,effect_time
        FROM strategy_remote_range
        WHERE status = 1 AND effect_object = 1
    """, engine="vi_strategy")

    if not strategy_list:
        logger.info("strategy_remote_range 无生效策略，退出")
        sys.exit()

    table = f"vi_bill.bill_{opt_time.strftime('%Y_%m_%d')}"

    for strategy in strategy_list:
        strategy_id = strategy["id"]
        customer_id = strategy["customer_id"]
        # 增加数值转换容错
        try:
            threshold = int(strategy["area_threshold"])
            stat_cycle = int(strategy["stat_cycle_second"])
            op_type = strategy["control_command"]
            append_reason = strategy["append_reason"]
            control_valid_second = int(strategy.get("control_valid_second", 0))
        except (ValueError, TypeError) as e:
            logger.error(f"策略{strategy_id}数值字段异常，跳过：{e}")
            continue

        control_command = "1" if op_type == 2 else "2"
        effect_time = strategy.get("effect_time")

        # 动态时间条件
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
                time_condition = """
                    to_date(handle_start_time) = to_date(current_timestamp)
                    AND date_trunc('hour', handle_start_time) = date_trunc('hour', current_timestamp)
                    AND handle_start_time <= current_timestamp
                """
        else:
            logger.warning(f"不支持的统计周期 {stat_cycle}，跳过当前策略")
            continue

        # 叠加策略生效时间过滤
        if effect_time and isinstance(effect_time, datetime.datetime):
            fmt_effect = effect_time.strftime("%Y-%m-%d %H:%M:%S")
            time_condition += f" AND handle_start_time >= '{fmt_effect}'"

        # 明细查询sql
        sql = f"""
            SELECT  
                mobile_id, channel_id, company_id,
                customer_id, app_id, caller_number,
                call_out_area_code, call_duration_s,
                handle_start_time, handle_end_time
            FROM {table}
            WHERE {time_condition}
              AND call_out_area_code != ''
              AND caller_number = '18819302337'
              AND call_duration_s > 0
              AND customer_id = {customer_id}
        """

        # 聚合校验sql1（修复所有语法问题，过滤完全对齐）
        sql1 = f"""
            SELECT call_out_area_code, COUNT(*) AS call_count
            FROM {table}
            WHERE {time_condition}
              AND call_out_area_code != ''
              AND caller_number = '18819302337'
              AND call_duration_s > 0
              AND customer_id = {customer_id}
            GROUP BY call_out_area_code
        """

        # 执行聚合校验sql1
        if USE_MYSQL:
            bill_list = selectForSQL(sql1, engine="vi_bill")
        else:
            bill_list = ckSelectForSQL(sql1, engine="vi_bill")

        logger.info(f"customer_id:{customer_id} 城市聚合统计结果 bill:{bill_list} ")