import datetime
import logging

logger = logging.getLogger(__name__)


DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


# 返回上一天00:00:00时间
def get_last_data_date(last_date_file, default_day=1):
    try:
        with open(last_date_file, "r", encoding="utf-8") as time_file:
            last_datetime = datetime.datetime.strptime(
                time_file.readline().strip(), DATE_FORMAT).replace(hour=0, minute=0, second=0, microsecond=0)
    except Exception as e:
        logger.info(e)
        # 读取文件失败，则从上一天开始
        last_datetime = (datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
                         - datetime.timedelta(days=default_day))
    return last_datetime

def get_last_data_hour(last_hour_file):
    try:
        with open(last_hour_file, "r", encoding="utf-8") as time_file:
            last_datetime = datetime.datetime.strptime(
                time_file.readline().strip(), DATE_FORMAT)
    except Exception as e:
        logger.info(e)
        # 读取文件失败，则从上一小时开始
        last_datetime = (datetime.datetime.now().replace(minute=0, second=0, microsecond=0)
                         - datetime.timedelta(hours=1))
    return last_datetime

def write_last_data_date(last_date, last_date_file):
    with open(last_date_file, "w", encoding="utf-8") as time_file:
        time_file.write(last_date.strftime(DATE_FORMAT))
