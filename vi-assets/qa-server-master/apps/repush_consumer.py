import base64
import datetime
import json
import logging

import pika
import urllib3
from pymongo import MongoClient

from apps.prometheus import push_result_latency, count_repushed_result
from config import settings
from utils.aes import AesUtil
from utils.mq import MsgQueue, MqUtil, loads_mq_json, safe_ack

logger = logging.getLogger("result")


class RepushConsumeThread(MqUtil):

    def __init__(self):
        super().__init__(daemon=True)

        self.http = urllib3.PoolManager(num_pools=20)
        # 重推队列
        self.mq_info = settings["Scheduler_RabbitMq"]
        self.mq = MsgQueue(self.mq_info["host"], self.mq_info["port"], self.mq_info["username"],
                           self.mq_info["password"], self.mq_info["vhost"])
        # 准实时质检队列
        realtime_mq_config = settings["Realtime_RabbitMq"]
        self.realtime_mq = MsgQueue(realtime_mq_config["host"], realtime_mq_config["port"],
                                    realtime_mq_config["username"],
                                    realtime_mq_config["password"], realtime_mq_config["vhost"])
        # mongoDb
        self.mongodb = MongoClient(settings["MONGODB_URI"], **settings["MONGODB_CONFIG"])
        # aes
        self.aes_util = AesUtil(settings["AES_INFO"]["key"].encode(), settings["AES_INFO"]["iv"].encode())

    def push_to_delay_mq(self, data, destination, delay):
        """
        结果推送失败时推送到延迟队列，等待3分钟后再次推送
        :param destination:
        :param data:
        :param delay:
        :return:
        """
        data["destination"] = destination
        exchange = self.mq_info["repush_exchange"]
        routing_key = self.mq_info["repush_routing_key"]
        self.mq.publish_message(exchange, routing_key, json.dumps(data),
                                properties=pika.BasicProperties(headers={"x-delay": delay}))

    def push_mq_message(self, request_data):
        """
        推送mq消息
        :param request_data: 消息体
        :return:
        """
        task_id = request_data["taskId"]
        mq_config = settings["Realtime_RabbitMq"]
        exchange = mq_config["to_xpusher_exchange"]
        routing_key = mq_config["to_xpusher_routing_key"]
        error = None
        try:
            self.realtime_mq.publish_message(exchange, routing_key, json.dumps(request_data))
            push_result = "1"
        except Exception as err:
            error = err
            push_result = "0"

        # 日志不包含ASR文本
        push_data = {k: v for k, v in request_data.items() if k != "transcription"}
        if "transcription" in request_data:
            push_data["transcription"] = "*"
        log_message = f"[{task_id}]-mqData:[{push_data}]-rePushResult:{push_result}"
        log_message = log_message + f"-error:[{error}]" if error else log_message
        logger.info(log_message)
        return push_result

    @push_result_latency.time()
    def push_http_message(self, url, request_data):
        """
        推送http消息
        :param url: 请求地址
        :param request_data: 请求体
        :return:
        """
        if not url:
            return
        task_id = request_data["taskId"]
        resp_json = {}
        error = None
        try:
            response = self.http.request(method="POST", url=url, body=json.dumps(request_data), timeout=5,
                                         headers={"Content-Type": "application/json;charset=UTF-8",
                                                  "Connection": "close"})
            status_code = response.status
            if status_code != 200:
                push_result = "0"
            else:
                resp_json = json.loads(response.data.decode("utf-8"))
                if resp_json.get("code") == "0000":
                    push_result = "1"
                else:
                    push_result = "0"

        except Exception as err:
            error = err
            push_result = "0"
            status_code = 0

        # 日志不包含ASR文本
        push_data = {k: v for k, v in request_data.items() if k != "transcription"}
        if "transcription" in request_data:
            push_data["transcription"] = "*"
        log_message = f"[{task_id}]-url:[{url}]-statusCode:{status_code}-response:[{resp_json}]-requestData:[{push_data}]"
        log_message = log_message + f"-pushError:[{error}]" if error else log_message
        logger.info(log_message)

        return push_result


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
        task_id = ""
        try:
            data = loads_mq_json(body)
            destination = data.pop("destination", "")
            task_id = str(data.get("transactionId", ""))
            headers = properties.headers or {}
            raw_delay = headers.get("x-delay")
            try:
                x_delay = int(raw_delay) if raw_delay is not None else None
            except (TypeError, ValueError):
                x_delay = None
            if x_delay is None:
                delay = 300000
            elif x_delay >= 600000:
                delay = -1
            elif x_delay == 300000:
                delay = 600000
            else:
                delay = 300000
            if destination == "mq":
                result = self.push_mq_message(data)
            else:
                result = self.push_http_message(destination, data)

            if delay > 0 and result == "0":
                # 推送失败并未达到重推次数
                self.push_to_delay_mq(data, destination, delay)

            if delay < 0 and result == "0":
                # 最后一次重推仍然失败则入库，等待人工处理
                asr = data.pop("transcription", "")
                # 入库前先对ASR文本加密
                encrypt_asr = self.aes_util.encrypt(base64.b64encode(asr.encode()).decode()) if asr else ""
                data["transcription"] = encrypt_asr
                data["create_time"] = datetime.datetime.now()
                self.mongodb["service"]["push_fail"].insert_one(data)

            # 监控指标
            count_repushed_result(status=result)
        except Exception as err:
            if isinstance(data, dict):
                _ = data.pop("transcription", "")
                logger.info(f"[{task_id}]-body:[{data}]-mqCallbackError:[{err}]")
            else:
                logger.error("mq poison/invalid json: %s body=%r", err, body)
        finally:
            safe_ack(ch, method)

    def run(self):
        queue = self.mq_info["repush_queue"]
        prefetch_count = self.mq_info["prefetch_count"]
        self.mq.consume_queue(
            queue_list=[queue], 
            callback=self.callback, 
            auto_ack=False, 
            prefetch_count=prefetch_count,
            stop_event=self._stop_event
        )
