import os
import threading
import time
from datetime import datetime

"""
根据话单ID获取对应信息
"""


def get_time_by_billid(billid):
    id_len = len(str(billid))
    billid = str(billid) if id_len < 32 else str(billid)[14:]
    start_stmp = 1561690135700
    sequence_bit = 13 if id_len >= 18 else 12
    machine_bit = 10 if id_len >= 18 else 7
    datacenter_bit = 4 if id_len >= 18 else 2
    datacenter_left = sequence_bit + machine_bit
    timestmp_left = datacenter_bit + datacenter_left

    time_stamp = (int(billid) >> timestmp_left) * 100 + start_stmp
    # print(time_stamp)
    return datetime.fromtimestamp(time_stamp / 1000)


def get_location_by_billid(billid):
    id_len = len(str(billid))
    billid = str(billid) if id_len < 32 else str(billid)[14:]
    bit = 23 if id_len >= 18 else 19
    max_val = 15 if id_len >= 18 else 3
    return (int(billid) >> bit) & max_val


def get_asid_by_billid(billid):
    id_len = len(str(billid))
    billid = str(billid) if id_len < 32 else str(billid)[14:]
    bit = 13 if id_len >= 18 else 12
    max_val = 1023 if id_len >= 18 else 127
    return (int(billid) >> bit) & max_val


seq = 0
start_ts = 1561690135700
sequence_bit = 6
machine_bit = 6
business_bit = 2
# 机器ID从环境变量注入，需落在 machine_bit 位范围内（0~63）
machine_id = int(os.environ.get("QA_SERVER_ID", 0)) & ((1 << machine_bit) - 1)
lock = threading.Lock()
last_ts = 0


def get_next_id(business_val=0):
    """
    生成本服务记录ID
    位布局（高位->低位）：[时间戳][业务类型(2位)][机器ID(6位)][序列号(6位)]
    业务类型位由入参 business_val 决定（如外部按 is_dcc 传 0 或 1）
    :param business_val: 业务类型值（0~3），默认 0
    :return:
    """
    global seq, last_ts
    # 加锁防止取值相同
    with lock:
        seq = (seq + 1) & 63

    # 保证时间戳严格递增
    ts = int(time.time() * 1000)
    while ts <= last_ts:
        ts = int(time.time() * 1000)
    last_ts = ts
    return (((ts - start_ts) << (business_bit + machine_bit + sequence_bit))
            | ((business_val & ((1 << business_bit) - 1)) << (machine_bit + sequence_bit))
            | (machine_id << sequence_bit)
            | seq)


def get_business_type_by_id(billid):
    """
    从记录ID中解析业务类型位
    位布局（高位->低位）：[时间戳][业务类型(2位)][机器ID(6位)][序列号(6位)]
    :param billid: 记录ID
    :return: 业务类型值（0~3）
    """
    return (int(billid) >> (machine_bit + sequence_bit)) & ((1 << business_bit) - 1)


def get_machine_id_by_id(billid):
    """
    从记录ID中解析机器ID位
    位布局（高位->低位）：[时间戳][业务类型(2位)][机器ID(6位)][序列号(6位)]
    :param billid: 记录ID
    :return: 机器ID（0~63）
    """
    return (int(billid) >> sequence_bit) & ((1 << machine_bit) - 1)

