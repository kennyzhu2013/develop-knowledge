import datetime
import logging
import traceback
from collections import defaultdict
from apps.clickhouse import ckAddObjects, ckSelectForSQL
from apps.database import selectForSQL
import time

from apps.email import send_email

logger = logging.getLogger("vi_strategy")

TAG = "【话务管控日统计】"

# 数据源开关：False=ClickHouse/StarRocks, True=MySQL
USE_MYSQL = False

# 策略编码 → 中文名称映射（与各 viStrategy*.py 中 STRATEGY_NAME 保持一致）
STRATEGY_NAME_MAP = {
    "1": "短时高频外呼管控",
    "2": "异地大范围外呼管控",
    "3": "非工作时间外呼管控",
    "4": "超业务门限外呼管控",
    "5": "超业务范围外呼管控",
    "8": "长通话管控",
    "9": "外省大范围高频外呼管控",
    "10": "外地市大范围高频外呼管控",
    "11": "沉默号码话务突增管控",
    "12": "闲时高频外呼管控",
    "13": "短时高频突增管控",
    "14": "短时长通话突增管控",
    "15": "沉默号码外地长通话突增管控",
}

# 话单 append_reason → (strategy_name, control_dimension) 映射
APPEND_REASON_MAP = {
    5:   ("1",  ""),   # 呼叫频次异常管控 → 短时高频
    13:  ("3", ""),   # 呼叫时间段管控 → 非工作时间
    20:  ("4", ""),   # 企业最大呼叫数管控 → 超业务门限
    21:  ("2",  ""),   # 异地外呼超限管控 → 异地大范围
    22:  ("5",  "5"),  # 超业务范围-盲呼
    23:  ("5",  "6"),  # 超业务范围-短秒率
    24:  ("5",  "7"),  # 超业务范围-接通率
    25:  ("8",  ""),   # 长通话管控
    26:  ("9",  ""),   # 外省大范围高频外呼管控
    27:  ("10", ""),   # 外地市大范围高频外呼管控
    28:  ("11", ""),   # 沉默号码话务突增管控
    29:  ("12", ""),   # 闲时高频外呼管控
    30:  ("13", ""),   # 短时高频突增管控
    31:  ("14", ""),   # 短时长通话突增管控
    32:  ("15", ""),   # 沉默号码外地长通话突增管控
}

def gen_snowflake_id(seq):
    # 毫秒时间戳 + 4位补零序号
    ms_ts = int(time.time() * 1000)
    return int(f"{ms_ts}{seq:04d}")


def build_daily_report_email_body(stat_date, agg_result, batch_size):
    """按策略维度汇总当日命中数据，构造邮件正文。"""
    # strategy_name -> {customers:set, phone_num:int, count:int}
    summary = defaultdict(lambda: {"customers": set(), "phone_num": 0, "count": 0})
    for row in agg_result:
        sname = str(row["strategy_name"]) if row["strategy_name"] is not None else ""
        s = summary[sname]
        s["customers"].add(row["customer_id"])
        s["phone_num"] += int(row["trigger_phone_num"])
        s["count"] += int(row["trigger_count"])

    lines = [f"话务管控日统计报告（{stat_date}）", ""]
    total_customers = set()
    total_phone_num = 0
    total_count = 0
    # 按策略编码排序，保证邮件正文顺序稳定
    for sname in sorted(summary.keys()):
        s = summary[sname]
        display_name = STRATEGY_NAME_MAP.get(sname, f"策略{sname}" if sname else "未知策略")
        lines.append(f"【{display_name}】")
        lines.append(f"  触发企业数：{len(s['customers'])}")
        lines.append(f"  触发号码数：{s['phone_num']}")
        lines.append(f"  触发次数：{s['count']}")
        lines.append("")
        total_customers.update(s["customers"])
        total_phone_num += s["phone_num"]
        total_count += s["count"]

    lines.append("【合计】")
    lines.append(f"  触发企业数：{len(total_customers)}")
    lines.append(f"  触发号码数：{total_phone_num}")
    lines.append(f"  触发次数：{total_count}")
    lines.append("")
    lines.append(f"统计明细已写入 call_control_statistics 表（共 {batch_size} 条）。")
    return "\n".join(lines)

#每日凌晨1点执行 统计昨日的数据
if __name__ == '__main__':
    now = datetime.datetime.now()
    stat_date = (now - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
    stat_time = f"{stat_date} 00:00:00"
    id_index = 1

    logger.info(f"{TAG}开始生成：{stat_date}")

    # 话单表名（按日分表）
    bill_table = f"vi_bill.bill_{(now - datetime.timedelta(days=1)).strftime('%Y_%m_%d')}"

    # 防重校验
    '''
    check_exist_sql = f"""
    SELECT count(*) AS cnt FROM mid_bussops.call_control_statistics
    WHERE toDate(stat_time) = toDate('{stat_date}')
    """
    try:
        exist_res = ckSelectForSQL(check_exist_sql, engine="mid_bussops")
        if exist_res and exist_res[0]["cnt"] > 0:
            logger.info(f"{stat_date} 统计数据已存在，直接退出")
            exit(0)
    except Exception as e:
        logger.error(f"校验重复数据失败：{str(e)}\n{traceback.format_exc()}")
        exit(1)
    '''

    # 从话单表聚合：按 append_reason 分组统计触发号码数和触发次数
    # append_reason → strategy_name / control_dimension 的映射在 Python 层处理
    valid_reasons = ",".join(str(k) for k in APPEND_REASON_MAP.keys())

    if USE_MYSQL:
        agg_sql = f"""
        SELECT
            mobile_id,
            channel_id,
            company_id,
            customer_id,
            append_reason,
            COUNT(DISTINCT caller_number) AS trigger_phone_num,
            COUNT(*) AS trigger_count
        FROM {bill_table}
        WHERE append_reason IN ({valid_reasons})
          AND DATE(handle_start_time) = DATE('{stat_date}')
        GROUP BY
            mobile_id, channel_id, company_id, customer_id, append_reason
        """
        try:
            raw_result = selectForSQL(agg_sql, engine="vi_bill")
        except Exception as e:
            logger.error(f"{TAG}聚合查询失败：{str(e)}\n{traceback.format_exc()}")
            send_email(f"聚合查询失败：{str(e)}\n{traceback.format_exc()}", '话务管控查询数据库失败')
            exit(1)
    else:
        agg_sql = f"""
        SELECT
            mobile_id,
            channel_id,
            company_id,
            customer_id,
            append_reason,
            COUNT(DISTINCT caller_number) AS trigger_phone_num,
            COUNT(*) AS trigger_count
        FROM {bill_table}
        WHERE append_reason IN ({valid_reasons})
          AND DATE(handle_start_time) = DATE('{stat_date}')
        GROUP BY
            mobile_id, channel_id, company_id, customer_id, append_reason
        """
        try:
            raw_result = ckSelectForSQL(agg_sql, engine="vi_bill")
        except Exception as e:
            logger.error(f"{TAG}聚合查询失败：{str(e)}\n{traceback.format_exc()}")
            send_email(f"聚合查询失败：{str(e)}\n{traceback.format_exc()}", '话务管控查询数据库失败')
            exit(1)

    # 将 append_reason 转换为 strategy_name / control_dimension
    agg_result = []
    for row in raw_result:
        reason = int(row["append_reason"])
        if reason not in APPEND_REASON_MAP:
            continue
        strategy_name, control_dimension = APPEND_REASON_MAP[reason]
        agg_result.append({
            "mobile_id": row["mobile_id"],
            "channel_id": row["channel_id"],
            "company_id": row["company_id"],
            "customer_id": row["customer_id"],
            "stat_time": datetime.datetime.strptime(stat_time, "%Y-%m-%d %H:%M:%S"),
            "strategy_name": strategy_name,
            "control_dimension": control_dimension,
            "trigger_phone_num": row["trigger_phone_num"],
            "trigger_count": row["trigger_count"],
            "create_time": datetime.datetime.now(),
            "update_time": datetime.datetime.now(),
        })

    logger.info(f"{TAG}聚合完成得到 {len(agg_result)} 条待写入数据")


    batch_list = []

    for row in agg_result:
        #str_id = f"{id_prefix}{index:0>5}"
        #row_id = int(str_id)
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

    # 插入语句（首字段增加 id）
    insert_sql = """
    INSERT INTO mid_bussops.call_control_statistics (
        id, mobile_id, channel_id, company_id, customer_id,
        stat_time, strategy_name, control_dimension,
        trigger_phone_num, trigger_count,
        create_time, update_time
    ) VALUES
    """
    if batch_list:
        logger.info(f"{TAG}开始写入统计表")
        try:
            rows = ckAddObjects(insert_sql, batch_list, engine="mid_bussops")
            logger.info(f"{TAG}写入成功,实际写入 {rows} 行")
            # 发送日统计报告邮件
            try:
                email_body = build_daily_report_email_body(stat_date, agg_result, len(batch_list))
                send_email(email_body, f"话务管控日统计报告-{stat_date}")
            except Exception as e:
                logger.error(f"{TAG}发送日统计报告邮件失败：{str(e)}\n{traceback.format_exc()}")
        except Exception as e:
            logger.error(f"{TAG}批量写入失败：{str(e)}\n{traceback.format_exc()}")
            send_email(f"批量写入失败：{str(e)}\n{traceback.format_exc()}", "话务管控日统计写入CK出错")
            exit(1)
    else:
        logger.info(f"{TAG}无聚合数据，跳过写入")
        try:
            logger.info(f"{TAG}当日无命中数据，未写入统计表")
            send_email(f"话务管控日统计报告（{stat_date}）\n\n当日无命中数据，未写入统计表。", f"话务管控日统计报告-{stat_date}")
        except Exception as e:
            logger.error(f"{TAG}发送空数据通知邮件失败：{str(e)}\n{traceback.format_exc()}")
