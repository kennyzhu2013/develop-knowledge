import datetime
import logging
import traceback
import time
import sys

from apps.clickhouse import ckAddObjects, ckSelectForSQL

logger = logging.getLogger("vi_strategy")


def gen_snowflake_id(seq):
    """毫秒时间戳 + 4位补零序号 生成雪花简易ID"""
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


def parse_stat_date() -> str:
    """解析入参日期，无参返回昨日，参数格式强制YYYY-MM-DD"""
    if len(sys.argv) == 1:
        # 无入参，默认统计昨日
        now = datetime.datetime.now()
        target_date = (now - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
        logger.info(f"未传入日期参数，自动使用昨日日期：{target_date}")
        return target_date

    if len(sys.argv) > 2:
        logger.error("参数错误：仅支持传入1个日期参数，格式 YYYY-MM-DD")
        sys.exit(1)

    input_date = sys.argv[1].strip()
    # 校验日期格式合法性
    try:
        datetime.datetime.strptime(input_date, "%Y-%m-%d")
    except ValueError:
        logger.error(f"日期格式非法 [{input_date}]，必须使用 YYYY-MM-DD，示例：2026-06-28")
        sys.exit(1)
    logger.info(f"接收外部指定统计日期：{input_date}")
    return input_date


if __name__ == '__main__':
    # 解析统计日期
    stat_date = parse_stat_date()
    stat_time = f"{stat_date} 00:00:00"
    id_index = 1

    logger.info(f"===== 开始生成话务管控日统计：{stat_date} =====")

    # 1. 防重校验：目标日期是否已存在统计数据
    check_exist_sql = f"""
    SELECT count(*) AS cnt FROM mid_bussops.call_control_statistics
    WHERE toDate(stat_time) = toDate('{stat_date}')
    """
    try:
        exist_res = ckSelectForSQL(check_exist_sql, engine="mid_bussops")
        if exist_res and exist_res[0]["cnt"] > 0:
            logger.info(f"{stat_date} 统计数据已存在，任务直接退出")
            sys.exit(0)
    except Exception as e:
        logger.error(f"【前置防重校验失败】{str(e)}\n{traceback.format_exc()}")
        sys.exit(1)

    # 2. ClickHouse 明细聚合查询
    agg_sql = f"""
    SELECT
        mobile_id,
        channel_id,
        company_id,
        customer_id,
        toDateTime('{stat_time}') AS stat_time,
        strategy_name,
        control_dimension,
        COUNT(DISTINCT control_subjects) AS trigger_phone_num,
        COUNT(*) AS trigger_count,
        now() AS create_time,
        now() AS update_time
    FROM mid_bussops.call_control_detail
    WHERE toDate(trigger_time) = toDate('{stat_date}')
    GROUP BY
        mobile_id, channel_id, company_id, customer_id,
        strategy_name, control_dimension
    """

    try:
        agg_result = ckSelectForSQL(agg_sql, engine="mid_bussops")
        logger.info(f"聚合查询完成，共获取 {len(agg_result)} 条待写入数据")
    except Exception as e:
        logger.error(f"【聚合明细查询失败】{str(e)}\n{traceback.format_exc()}")
        sys.exit(1)

    # 3. 组装批量写入数据
    batch_list = []
    for row in agg_result:
        new_id = gen_snowflake_id(id_index)
        id_index += 1

        batch_list.append([
            new_id,
            row["mobile_id"],
            row["channel_id"],
            row["company_id"],
            row["customer_id"],
            row["stat_time"],
            row["strategy_name"],
            row["control_dimension"],
            row["trigger_phone_num"],
            row["trigger_count"],
            row["create_time"],
            row["update_time"]
        ])

    # 4. 批量插入 ClickHouse 统计表
    insert_sql = """
    INSERT INTO mid_bussops.call_control_statistics (
        id, mobile_id, channel_id, company_id, customer_id,
        stat_time, strategy_name, control_dimension,
        trigger_phone_num, trigger_count,
        create_time, update_time
    ) VALUES
    """
    if batch_list:
        logger.info("开始批量写入日统计数据表")
        try:
            write_rows = ckAddObjects(insert_sql, batch_list, engine="mid_bussops")
            logger.info(f"批量写入完成，实际入库 {write_rows} 行")
        except Exception as e:
            logger.error(f"【批量写入统计表失败】{str(e)}\n{traceback.format_exc()}")
            sys.exit(1)
    else:
        logger.info(f"{stat_date} 无管控触发明细数据，跳过写入步骤")


    logger.info(f"===== {stat_date} 话务管控日统计任务执行完毕 =====")