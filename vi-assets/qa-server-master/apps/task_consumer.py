import datetime
import json
import logging
import re
import uuid

from models import QaTask, QaRule
from apps.prometheus import count_quasi_realtime_task
from config import settings
from utils.helpers import encrypt_id, QA_INFO_EXPIRATION, JsonEncoder
from utils.mq import MsgQueue, MqUtil, loads_mq_json, safe_ack
from utils.mysql import session_scope, select_in_mysql
from utils.redis import Redis
from utils.snowFlake import get_next_id, get_location_by_billid, get_asid_by_billid

logger = logging.getLogger("realtime")


class QaTaskConsumeThread(MqUtil):

    def __init__(self):
        super().__init__(daemon=True)
        self.rdc = Redis.get_connection()
        # 话单mq
        self.mq_info = settings["Realtime_RabbitMq"]
        self.mq = MsgQueue(self.mq_info["host"], self.mq_info["port"], self.mq_info["username"],
                           self.mq_info["password"], self.mq_info["vhost"])
        # 调度系统mq
        self.task_mq_info = settings["Scheduler_RabbitMq"]
        self.task_mq = MsgQueue(self.task_mq_info["host"], self.task_mq_info["port"], self.task_mq_info["username"],
                                self.task_mq_info["password"], self.task_mq_info["vhost"])

        self.channel_dict = {}

    def process_data_for_voice_check(self, bill):
        task_id = bill.get("task_id")
        rule = self.rdc.get(task_id)
        if not rule:
            with session_scope() as ss:
                task = ss.query(QaTask).join(QaRule, QaTask.rule_id == QaRule.id).filter(QaTask.id == task_id).first()
                if not task:
                    logger.info(f"[{task_id}]-task_id错误")
                    return
                rule = task.to_dict()["rule"]
                self.rdc.set(task_id, json.dumps(rule, cls=JsonEncoder))
        else:
            rule = json.loads(rule)

        priority = rule.get("priority")
        process_id = rule.get("process_id")
        app_id = rule.get("app_id")
        is_asr = rule.get("is_asr", "0")
        asr_type = rule.get("asr_type", "")
        is_word_match = rule.get("is_word_match", "0")
        word_package_id = rule.get("word_package_id", "")
        is_scene_compare = rule.get("is_scene_compare", "0")
        script_id = rule.get("script_id", "")
        strategy_type = rule.get("strategy_type", "")
        qa_mobile_id = rule.get("qa_mobile_id")
        qa_channel_id = rule.get("qa_channel_id")
        qa_company_id = rule.get("qa_company_id")
        qa_customer_id = rule.get("qa_customer_id")

        create_time = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        eb_id = bill["customer_name"] if bill["isEbBusiness"] else ""
        mobile_id = self.channel_dict.get(str(bill["channelId"]))
        if not mobile_id:
            sql_channel = "select id,mobile_id from auth_channel where id = :id"
            channel_list = select_in_mysql(sql=sql_channel, engine="mid_auth", params={"id": bill["channelId"]})
            mobile_id = channel_list[0]["mobile_id"] if channel_list else 0
            self.channel_dict[str(bill["channelId"])] = mobile_id

        call_id = bill["id"]
        location = get_location_by_billid(call_id)
        # 移动云asid大于50
        as_id = get_asid_by_billid(call_id)

        if location == 1:
            server_url = settings["ZJH_SOUTH_FILE_SERVER"] if as_id >= 35 else settings["YSH_SOUTH_FILE_SERVER"]
        elif location == 2:
            server_url = settings["ZJH_NORTH_FILE_SERVER"] if as_id >= 35 else settings["YSH_NORTH_FILE_SERVER"]
        elif location == 3:
            server_url = settings["QA_FILE_SERVER"]
        else:
            logger.info(f"[{task_id}]-callId无法解析:{call_id}")
            return

        encryptid = encrypt_id(bill["companyId"])
        mserverid = encrypt_id(bill["mserverId"])
        nfs_id = bill.get("nfsId")
        record_suffix = "mp3" if bill["record_suffix"] == 0 else "wav"
        if bill["extId"] in (15, 24):
            # 阿里录音后缀需要从话单里面读
            record_suffix = "wav" if bill["recordType"] == 1 else "mp3"
        fs_version = 1
        if bill["calleeNumber"] and re.match("^86", bill["calleeNumber"]):
            callee = bill["calleeNumber"][2:]
        else:
            callee = bill["calleeNumber"] if bill["calleeNumber"] else ""
        if bill["handleStartTime"]:
            handle_start_time = datetime.datetime.fromtimestamp(bill["handleStartTime"]/1000)
            datetime_str = handle_start_time.strftime("%Y%m%d%H%M%S")
            # 合成录音url
            if bill["customerId"] != 17:
                # 1674491716024664_15192663992_15898889355_20220107141522.wav
                filename = "{}_{}_{}_{}.{}".format(call_id, bill["callerNumber"], callee, datetime_str,
                                                   record_suffix)
            else:
                # 65772_1674484113815031_18746818560_18446416935_13234381450_20220107140920.mp3
                filename = "65772_{}_{}_{}_{}_{}.{}".format(call_id, bill["callerNumber"], bill["midNumber"],
                                                            callee, datetime_str, record_suffix)

            url_call = "{}/file/{}/{}/{}/{}/{}".format(server_url, fs_version, encryptid, mserverid, nfs_id,
                                                       filename)

            result_list = [{"dataId": str(call_id), "url": url_call, "location": location}]

            detail_id = get_next_id()
            ext_num = bill.get("extNo", "")
            display_num = bill["displayNum"] if bill["displayNum"] else ""
            zjh_scene = bill["scene"] if bill["scene"] else ""
            # 缓存话单数据到redis，用于补充质检结果数据
            data = {"mobile_id": mobile_id, "channel_id": bill["channelId"],
                    "company_id": bill["companyId"], "customer_id": bill["customerId"], "callee": callee,
                    "caller": bill["callerNumber"], "mid_num": bill["midNumber"], "display_num": display_num,
                    "ext_num": ext_num, "call_time": handle_start_time.strftime("%Y-%m-%d %H:%M:%S"),
                    "is_media_channel_inversed": bill["isMediaChannelInversed"],
                    "access_platform": bill["accessPlatform"],
                    "is_eb_business": bill["isEbBusiness"],
                    "strategy_type": strategy_type, "zjh_scene": zjh_scene,
                    "data_id": str(bill["id"]), "url": url_call, "task_id": task_id, "app_id": app_id,
                    "create_time": create_time, "eb_id": eb_id, "is_reviewed": "0",
                    "id": detail_id, "fraud_reviewed_type": "", "fraud_reviewed_comment": "",
                    "qa_mobile_id": qa_mobile_id, "qa_channel_id": qa_channel_id,
                    "qa_company_id": qa_company_id, "qa_customer_id": qa_customer_id,
                    "remark1": "", "remark2": "", "remark3": "", "remark4": ""}
            key = "{}:{}".format(task_id, bill["id"])
            self.rdc.set(key, json.dumps(data), ex=QA_INFO_EXPIRATION)

            # 调用接口创建任务
            parameter = {}
            if is_asr == "1" and asr_type:
                parameter["transcriptionType"] = asr_type

            if is_word_match == "1" and word_package_id:
                package_id_list = word_package_id.split(",")
                sql = "select * from qa_word_package where id in :package_id_list"
                package_list = select_in_mysql(sql, params={"package_id_list": package_id_list}, engine="mid_strategy")
                word_package = [{"packageId": str(item["id"]), "word": item["words"]} for item in package_list]
                parameter["wordPackage"] = word_package

            if is_scene_compare == "1" and script_id:
                script_id_list = script_id.split(",")
                sql = "select * from qa_script where id in :script_id_list"
                script_list = select_in_mysql(sql, params={"script_id_list": script_id_list}, engine="mid_strategy")
                scripts = [{"scriptId": str(item["id"]), "text": item["script"]} for item in script_list]
                parameter["scripts"] = scripts

            # 生成requestID, 不需要总结果回调
            request_id = uuid.uuid1().hex
            request_data = {"requestId": request_id, "data": result_list, "processId": process_id,
                            "priority": priority, "transactionId": int(task_id), "needTaskCallback": False}
            if parameter:
                request_data["parameter"] = parameter

            self.task_mq.publish_message(exchange=self.task_mq_info["to_scheduler_exchange"],
                                         routing_key=self.task_mq_info["to_scheduler_routing_key"],
                                         msg=json.dumps(request_data))
            logger.info(f"[{task_id}]-sendToMq:[{request_data}]")
        else:
            logger.info(f"[{task_id}]-呼入时间为空:{bill}")
            raise Exception("handlestarttime is null")

    def callback(self, ch, method, properties, body):
        """
        回调函数
        :param ch:
        :param method:
        :param properties:
        :param body:
        :return:
        """
        data = None
        try:
            data = loads_mq_json(body)
            self.process_data_for_voice_check(data)
            count_quasi_realtime_task(
                status="1",
                company_id=data.get("companyId", ""),
                customer_id=data.get("customerId", ""),
            )
        except Exception as err:
            logger.info(f"error happen:{err},body:{body}")
            if isinstance(data, dict):
                count_quasi_realtime_task(
                    status="0",
                    company_id=data.get("companyId", ""),
                    customer_id=data.get("customerId", ""),
                )
        finally:
            safe_ack(ch, method)

    def run(self):
        queue = self.mq_info["from_xpusher_queue"]
        prefetch_count = self.mq_info["prefetch_count"]
        self.mq.consume_queue(
            queue_list=[queue], 
            callback=self.callback, 
            auto_ack=False, 
            prefetch_count=prefetch_count,
            stop_event=self._stop_event
        )
