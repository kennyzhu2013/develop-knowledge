import datetime
import logging
import sys
import traceback

from apps.clickhouse import ckSelectForSQL
from apps.database import selectForSQL, executeForSQL
from apps.email import send_email

logger = logging.getLogger("vi_strategy")

TAG = "【号码活跃时间日统计】"

# 数据源开关：False=ClickHouse/StarRocks, True=MySQL
USE_MYSQL = False

# 批量 INSERT 每批条数
BATCH_SIZE = 500


def parse_stat_date():
    """解析统计日期，无参返回昨日，参数格式 YYYY-MM-DD"""
    if len(sys.argv) == 1:
        now = datetime.datetime.now()
        target_date = (now - datetime.timedelta(days=1)).strftime("%Y-%m-%d")
        logger.info(f"{TAG}未传入日期参数，自动使用昨日日期：{target_date}")
        return target_date

    if len(sys.argv) > 2:
        logger.error(f"{TAG}参数错误：仅支持传入1个日期参数，格式 YYYY-MM-DD")
        sys.exit(1)

    input_date = sys.argv[1].strip()
    try:
        datetime.datetime.strptime(input_date, "%Y-%m-%d")
    except ValueError:
        logger.error(f"{TAG}日期格式非法 [{input_date}]，必须使用 YYYY-MM-DD，示例：2026-09-13")
        sys.exit(1)
    logger.info(f"{TAG}接收外部指定统计日期：{input_date}")
    return input_date


def sync_phone_numbers():
    """从 user_qc.biz_num_auth 同步精确匹配号码到 vi_strategy.active_phone_tab
    - 新号码：active_time 初始为 online_time（= create_time）
    - 已有号码：仅更新 customer_id 和 online_time，不覆盖 active_time
    """
    auth_sql = """
    SELECT num, customer_id, create_time
    FROM biz_num_auth
    WHERE match_type = 1
    """
    try:
        auth_rows = selectForSQL(auth_sql, engine="user_qc")
    except Exception as e:
        logger.error(f"{TAG}查询 biz_num_auth 失败：{str(e)}")
        raise

    if not auth_rows:
        logger.info(f"{TAG}biz_num_auth 无精确匹配号码，跳过同步")
        return 0

    logger.info(f"{TAG}从 biz_num_auth 查询到 {len(auth_rows)} 个精确匹配号码")

    total = 0
    for i in range(0, len(auth_rows), BATCH_SIZE):
        batch = auth_rows[i:i + BATCH_SIZE]
        values_list = []
        for row in batch:
            num = str(row["num"]) if row["num"] is not None else ""
            if not num:
                continue
            cid = int(row["customer_id"])
            ct = row["create_time"]
            if isinstance(ct, str):
                ct = datetime.datetime.strptime(ct, "%Y-%m-%d %H:%M:%S")
            ct_str = ct.strftime("%Y-%m-%d %H:%M:%S")
            # 转义单引号防注入
            num_escaped = num.replace("'", "''")
            values_list.append(f"('{num_escaped}', {cid}, '{ct_str}', '{ct_str}')")

        if not values_list:
            continue

        values_sql = ", ".join(values_list)
        upsert_sql = f"""
        INSERT INTO active_phone_tab (biz_number, customer_id, online_time, active_time)
        VALUES {values_sql}
        ON DUPLICATE KEY UPDATE
            customer_id = VALUES(customer_id),
            online_time = VALUES(online_time)
        """
        try:
            executeForSQL(upsert_sql, engine="vi_strategy")
            total += len(values_list)
        except Exception as e:
            logger.error(f"{TAG}批量 UPSERT 失败（批次 {i // BATCH_SIZE + 1}）：{str(e)}")
            raise

    logger.info(f"{TAG}号码同步完成，共 {total} 个号码")
    return total


def is_first_run():
    """检测是否为首次运行：如果没有任何记录的 active_time > online_time，
    说明从未从话单更新过活跃时间，需要先读历史表初始化"""
    sql = "SELECT COUNT(*) AS cnt FROM active_phone_tab WHERE active_time > online_time"
    try:
        rows = selectForSQL(sql, engine="vi_strategy")
        return rows[0]["cnt"] == 0
    except Exception as e:
        logger.error(f"{TAG}检测首次运行失败：{str(e)}")
        return True


def update_active_time(bill_table, stat_date=None):
    """从话单表查询各号码最新通话时间，更新 active_phone_tab.active_time
    只更新比当前 active_time 更新的记录
    - stat_date 为 None 时：全量扫描历史表（首次运行初始化）
    - stat_date 有值时：只查指定日期的日表（每日定时更新）
    """
    # 获取 active_phone_tab 中所有 biz_number
    biz_sql = "SELECT biz_number FROM active_phone_tab"
    try:
        biz_rows = selectForSQL(biz_sql, engine="vi_strategy")
    except Exception as e:
        logger.error(f"{TAG}查询 active_phone_tab 号码列表失败：{str(e)}")
        raise

    biz_numbers = [str(r["biz_number"]) for r in biz_rows if r["biz_number"]]
    if not biz_numbers:
        logger.info(f"{TAG}active_phone_tab 无号码，跳过活跃时间更新")
        return 0

    logger.info(f"{TAG}active_phone_tab 共 {len(biz_numbers)} 个号码，开始查询话单活跃时间")

    # 查询话单表中这些号码的最新通话时间（分批 IN 查询）
    # 话单表中关联业务号码的字段为 biz_number
    active_map = {}  # biz_number -> max(handle_start_time)
    for i in range(0, len(biz_numbers), BATCH_SIZE):
        batch = biz_numbers[i:i + BATCH_SIZE]
        num_list_sql = ", ".join(f"'{n.replace(chr(39), chr(39) + chr(39))}'" for n in batch)
        date_filter = f"AND DATE(handle_start_time) = DATE('{stat_date}')" if stat_date else ""
        bill_sql = f"""
        SELECT biz_number, MAX(handle_start_time) AS active_time
        FROM {bill_table}
        WHERE biz_number IN ({num_list_sql})
        {date_filter}
        GROUP BY biz_number
        """
        try:
            if USE_MYSQL:
                bill_rows = selectForSQL(bill_sql, engine="vi_bill")
            else:
                bill_rows = ckSelectForSQL(bill_sql, engine="vi_bill")
        except Exception as e:
            logger.error(f"{TAG}查询话单活跃时间失败（批次 {i // BATCH_SIZE + 1}）：{str(e)}")
            raise

        for row in bill_rows:
            biz_num = str(row["biz_number"])
            at = row["active_time"]
            if isinstance(at, str):
                at = datetime.datetime.strptime(at, "%Y-%m-%d %H:%M:%S")
            if at is not None:
                active_map[biz_num] = at

    if not active_map:
        logger.info(f"{TAG}话单表中无匹配号码的通话记录，跳过活跃时间更新")
        return 0

    logger.info(f"{TAG}话单表中匹配到 {len(active_map)} 个号码有通话记录")

    # 批量更新 active_time（只更新比当前更新的记录）
    updated = 0
    failed_numbers = []
    for biz_num, at in active_map.items():
        at_str = at.strftime("%Y-%m-%d %H:%M:%S")
        biz_escaped = biz_num.replace("'", "''")
        update_sql = f"""
        UPDATE active_phone_tab
        SET active_time = '{at_str}'
        WHERE biz_number = '{biz_escaped}'
          AND active_time < '{at_str}'
        """
        try:
            executeForSQL(update_sql, engine="vi_strategy")
            updated += 1
        except Exception as e:
            logger.error(f"{TAG}更新号码 {biz_num} 活跃时间失败：{str(e)}")
            failed_numbers.append(biz_num)

    if failed_numbers:
        send_email(
            f"号码活跃表以下 {len(failed_numbers)} 个号码更新失败：\n{', '.join(failed_numbers)}",
            "号码活跃表部分号码活跃时间更新失败"
        )

    logger.info(f"{TAG}活跃时间更新完成，共更新 {updated} 个号码，失败 {len(failed_numbers)} 个")
    return updated


if __name__ == '__main__':
    stat_date = parse_stat_date()
    bill_table = f"vi_bill.bill_{datetime.datetime.strptime(stat_date, '%Y-%m-%d').strftime('%Y_%m_%d')}"

    logger.info(f"{TAG}开始：统计日期 {stat_date}，话单日表 {bill_table}")

    try:
        # 同步号码：biz_num_auth(match_type=1) → active_phone_tab
        sync_phone_numbers()

        # 更新活跃时间
        #    首次运行：读全量历史表 vi_bill_history.bill_history 初始化
        #    后续每日：读昨日日表 vi_bill.bill_YYYY_MM_DD
        if is_first_run():
            logger.info(f"{TAG}首次运行，从历史表 vi_bill_history.bill_history 初始化活跃时间")
            update_active_time("vi_bill_history.bill_history", stat_date=None)
        else:
            logger.info(f"{TAG}非首次运行，从昨日日表 {bill_table} 更新活跃时间")
            update_active_time(bill_table, stat_date=stat_date)

        logger.info(f"{TAG}任务完成")
    except Exception as e:
        err_msg = f"号码活跃表同步更新失败：{str(e)}\n{traceback.format_exc()}"
        logger.error(f"{TAG}{err_msg}")
        send_email(err_msg, "号码活跃表同步更新失败")
        sys.exit(1)
