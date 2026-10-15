import logging
import json
import redis
from datetime import datetime

from apps.database import selectForSQL
from apps.clickhouse import ckSelectForSQL
from config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("strategy_redis_daily_clean")

TAG = "【停用策略Redis清理】"

# 数据源开关：False=ClickHouse/StarRocks, True=MySQL
USE_MYSQL = False

# 策略表、Redis前缀、Key类型 一一绑定
STRATEGY_MAPPING = [
    ("strategy_high_frequency",    "highfreq-",    "phone"),
    ("strategy_remote_range",      "difarea-",     "phone"),
    ("strategy_offwork_call",      "offtime-",     "enterprise"),
    ("strategy_business_threshold","threshold-",   "enterprise"),
    ("strategy_business_range",    "bizrange-",    "phone"),
    ("strategy_long_call",         "longcall-",    "phone"),
]

# 新增定制策略（无主账户通用策略，仅独立策略）：策略表 -> Redis 前缀列表
NEW_STRATEGY_MAPPING = [
    ("strategy_difarea_high_frequency",        ["province-highfreq-", "city-highfreq-"]),
    ("strategy_silent_call_burst",             ["inactive-"]),
    ("strategy_idle_high_frequency",           ["idle-highfreq-"]),
    ("strategy_short_longcall_burst",          ["short-longcall-"]),
    ("strategy_short_highfreq_burst",          ["short-highfreq-"]),
    ("strategy_silent_difarea_longcall_burst", ["inactive-difarea-longcall-"]),
]

# 话单库与分表配置
BILL_DB = "vi_bill"
BILL_TABLE_PREFIX = "bill_"

def get_today_bill_table() -> str:
    today = datetime.now()
    return f"{BILL_DB}.{BILL_TABLE_PREFIX}{today.strftime('%Y_%m_%d')}"

def scan_redis_keys(rdb, prefix: str) -> list:
    keys = []
    cursor = 0
    match_rule = f"{prefix}*"
    while True:
        cursor, batch = rdb.scan(cursor, match=match_rule, count=1000)
        keys.extend(batch)
        if cursor == 0:
            break
    return keys

def get_app_id_to_customers() -> dict:
    """构建 app_id -> [cid] 映射，不过滤 customer_tab.status，
    以覆盖所有曾被主账户策略展开过的下属企业（含历史残留 key）。"""
    sql = "SELECT id, app_id FROM customer_tab WHERE app_id IS NOT NULL AND app_id != ''"
    rows = selectForSQL(sql, engine="user_qc")
    app_id_to_customers = {}
    for row in rows:
        cid = str(row["id"])
        app_id = row["app_id"]
        app_id_to_customers.setdefault(app_id, []).append(cid)
    return app_id_to_customers

def get_disabled_cids_simple(table_name: str) -> set:
    """新增定制策略专用：无 is_main/app_id 字段，仅独立策略。
    返回 status=0 的 customer_id 集合。"""
    sql = f"SELECT customer_id FROM {table_name} WHERE status = 0"
    rows = selectForSQL(sql, engine="vi_strategy")
    disabled_cids = {str(row["customer_id"]) for row in rows}
    logger.info(f"{TAG}【{table_name}】停用企业：{len(disabled_cids)} 个（仅独立策略）")
    return disabled_cids


def get_disabled_cid_by_table(table_name: str, app_id_to_customers: dict) -> tuple:
    """返回 (disabled_indep_cids, disabled_main_cids)：
    - disabled_indep_cids: status=0 独立策略 cid 集合
    - disabled_main_cids: status=0 主账户策略展开的 cid 集合

    清理逻辑（在 main 中实现）：直接根据 redis value 的 isMain 字段判断删除
    - value isMain=0 + cid 在 disabled_indep_cids → 删（独立策略已停用，残留 key 是独立策略写入的）
    - value isMain=1 + cid 在 disabled_main_cids → 删（主账户策略已停用，残留 key 是主账户策略写入的）
    - 其他情况不删
    """
    disabled_sql = f"SELECT customer_id, is_main, app_id FROM {table_name} WHERE status = 0"
    disabled_rows = selectForSQL(disabled_sql, engine="vi_strategy")

    disabled_indep_cids = set()
    disabled_main_cids = set()
    main_disabled_count = 0  # 主账户策略停用条数（用于日志）

    for row in disabled_rows:
        cid = str(row["customer_id"])
        is_main = int(row.get("is_main", 0) or 0)
        app_id = row.get("app_id", "") or ""

        if is_main == 0:
            # 独立策略停用：直接加入独立停用集
            disabled_indep_cids.add(cid)
        else:
            # 主账户策略停用：展开到下属企业，加入主账户停用集
            if not app_id:
                continue
            main_disabled_count += 1
            for sub_cid in app_id_to_customers.get(app_id, []):
                disabled_main_cids.add(sub_cid)

    logger.info(
        f"{TAG}【{table_name}】停用企业：独立停用 {len(disabled_indep_cids)} 个，"
        f"主账户停用展开 {len(disabled_main_cids)} 个下属企业（主账户停用 {main_disabled_count} 条）"
    )
    return disabled_indep_cids, disabled_main_cids


def get_customer_id_by_caller(caller_num: str, bill_table: str) -> str:
    sql = f"""
        SELECT DISTINCT customer_id
        FROM {bill_table}
        WHERE caller_number = '{caller_num}'
        LIMIT 1
    """
    if USE_MYSQL:
        res = selectForSQL(sql, engine="vi_bill")
    else:
        res = ckSelectForSQL(sql, engine="vi_bill")
    return str(res[0]["customer_id"]) if res else ""

def get_cid_and_ismain_from_redis_value(rdb, key: str) -> tuple:
    """从 Redis value 解析 (customerId, isMain)。

    - customerId 解析失败返回空串，由调用方回查话单表
    - isMain 字段缺失时默认 0（按独立策略写入处理）
    """
    try:
        val = rdb.get(key)
        if not val:
            return "", 0
        data = json.loads(val)
        cid = data.get("condition", {}).get("customerId", "")
        cid = str(cid) if cid is not None and cid != "" else ""
        is_main = int(data.get("isMain", 0) or 0)
        return cid, is_main
    except Exception as e:
        logger.error(f"{TAG}解析 Redis Key {key} value 失败: {e}")
        return "", 0

def get_ismain_from_redis_value(rdb, key: str) -> int:
    """仅从 Redis value 解析 isMain 字段（用于 enterprise 类型，cid 已在 key 中）。"""
    try:
        val = rdb.get(key)
        if not val:
            return 0
        data = json.loads(val)
        return int(data.get("isMain", 0) or 0)
    except Exception as e:
        logger.error(f"{TAG}解析 Redis Key {key} value 失败: {e}")
        return 0

def main():
    redis_cfg = settings["VI_STRATEGY_REDIS"]
    rdb = redis.Redis(
        host=redis_cfg["HOST"],
        port=redis_cfg["PORT"],
        password=redis_cfg["PASSWORD"],
        db=redis_cfg["DB"],
        decode_responses=True
    )

    today_bill_table = get_today_bill_table()
    logger.info(f"{TAG}当前使用当日话单表：{today_bill_table}")
    # 一次性加载主账户 app_id -> [cid] 映射，供各策略表展开停用下属企业复用
    app_id_to_customers = get_app_id_to_customers()
    logger.info(f"{TAG}已加载主账户→下属企业映射，共 {len(app_id_to_customers)} 个主账户")
    total_del = 0

    for table_name, prefix, key_type in STRATEGY_MAPPING:
        logger.info(f"{TAG}开始处理策略表：{table_name}，Redis前缀：{prefix}")
        disabled_indep_cids, disabled_main_cids = get_disabled_cid_by_table(table_name, app_id_to_customers)
        if not disabled_indep_cids and not disabled_main_cids:
            logger.info(f"{TAG}【{table_name}】暂无停用策略，跳过")
            continue

        key_list = scan_redis_keys(rdb, prefix)
        del_cnt = 0

        # 统一清理逻辑：直接根据 redis value 的 isMain 字段判断
        # - value isMain=0 + cid 在 disabled_indep_cids → 删（独立策略已停用）
        # - value isMain=1 + cid 在 disabled_main_cids → 删（主账户策略已停用）
        for key in key_list:
            if key_type == "enterprise":
                # enterprise 类型：key 本身含 cid，仅从 value 拿 isMain
                cid = key.replace(prefix, "")
                value_is_main = get_ismain_from_redis_value(rdb, key)
            else:
                # phone 类型：key 是 caller_number，从 value 拿 cid 和 isMain
                cid, value_is_main = get_cid_and_ismain_from_redis_value(rdb, key)
                if not cid:
                    # 兜底：value 解析失败时回查当日话单（无 isMain 信息，按独立策略残留处理）
                    caller = key.replace(prefix, "")
                    cid = get_customer_id_by_caller(caller, today_bill_table)
                    value_is_main = 0

            if not cid:
                continue

            should_delete = (
                (value_is_main == 0 and cid in disabled_indep_cids)
                or (value_is_main == 1 and cid in disabled_main_cids)
            )
            if should_delete:
                rdb.delete(key)
                del_cnt += 1
                logger.info(f"{TAG}已删除Key: {key}（cid={cid}, value.isMain={value_is_main}）")

        logger.info(f"{TAG}【{prefix}】扫描{len(key_list)}个Key，本轮清理 {del_cnt} 个")
        total_del += del_cnt

    # ===================== 新增定制策略清理（仅独立策略，无 isMain） =====================
    for table_name, prefixes in NEW_STRATEGY_MAPPING:
        logger.info(f"{TAG}开始处理定制策略表：{table_name}")
        disabled_cids = get_disabled_cids_simple(table_name)
        if not disabled_cids:
            logger.info(f"{TAG}【{table_name}】暂无停用策略，跳过")
            continue

        for prefix in prefixes:
            key_list = scan_redis_keys(rdb, prefix)
            # inactive- 是 inactive-difarea-longcall- 的前缀，SCAN 会误命中长前缀策略的 key，
            # 扫描 inactive- 时排除，避免误删仍生效的沉默号码外地长通话策略管控 key
            if prefix == "inactive-":
                key_list = [k for k in key_list if not k.startswith("inactive-difarea-longcall-")]
            del_cnt = 0
            for key in key_list:
                # 新策略 value 无 isMain 字段（按独立策略写入），仅按 cid 匹配
                cid, _ = get_cid_and_ismain_from_redis_value(rdb, key)
                if not cid:
                    caller = key.replace(prefix, "")
                    cid = get_customer_id_by_caller(caller, today_bill_table)
                if cid and cid in disabled_cids:
                    rdb.delete(key)
                    del_cnt += 1
                    logger.info(f"{TAG}已删除Key: {key}（cid={cid}）")
            logger.info(f"{TAG}【{prefix}】扫描{len(key_list)}个Key，本轮清理 {del_cnt} 个")
            total_del += del_cnt

    rdb.close()
    logger.info(f"{TAG}全量任务结束，累计删除Redis Key：{total_del} 个")

if __name__ == "__main__":
    logger.info(f"{TAG}启动 分策略表独立清理任务")
    main()