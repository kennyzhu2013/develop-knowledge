import base64
import copy
import datetime
import json
import logging
import math

import pika
import urllib3
from pymongo import MongoClient
from sqlalchemy.orm.attributes import flag_modified

from models import QaTask, QaDetail, QaSegment, DccQaTask, DccQaDetail, QaFail
from apps.prometheus import count_model_of_sub_task, count_quasi_realtime_result, count_finished_sub_task, count_sub_result, \
    count_finished_main_task, count_main_result, count_callback_error, observe_sub_task_model_latency, observe_sub_task_process_latency, sub_result_write_latency, \
    main_result_write_latency, push_result_latency, count_zero_duration_sub_result
from config import settings
from utils.aes import AesUtil
from utils.cache import get_dcc_qa_rule_by_id, get_qa_rule_by_id, get_strategy_config
from utils.clickhouse import ck_session_scope
from utils.helpers import GZH_LIST, QA_INFO_EXPIRATION, JsonEncoder
from utils.mq import MsgQueue, MqUtil, loads_mq_json, safe_ack
from utils.mysql import session_scope
from utils.redis import Redis
from utils.snowFlake import get_business_type_by_id, get_next_id

logger = logging.getLogger("result")

# 质检失败最大重试次数
QA_FAIL_MAX_RETRY = 5
# qa_fail 所在 MySQL 库
QA_FAIL_ENGINE = "vi_cdr"
# retry_status：0 待重试/回调已返回；1 重试中；2 成功；3 失败
QA_FAIL_STATUS_PENDING = 0
QA_FAIL_STATUS_RETRYING = 1
QA_FAIL_STATUS_SUCCESS = 2
QA_FAIL_STATUS_FAILED = 3


class QaResultConsumeThread(MqUtil):

    def __init__(self):
        super().__init__(daemon=True)
        self.rdc = Redis.get_connection()
        self.http = urllib3.PoolManager(num_pools=20)
        # 质检结果回调队列
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

    def get_rule(self, task_id):
        """
        获取任务对应的回调地址
        :param task_id:
        :return:
        """
        key = "QARULE:" + task_id
        result = self.rdc.get(key)
        if result:
            return json.loads(result)
        business_type = get_business_type_by_id(task_id)
        with session_scope() as ss:
            task = ss.get(DccQaTask, task_id) if business_type == 1 else ss.get(QaTask, task_id)
            if not task:
                return None
            rule_id = task.rule_id
        # session 结束后再查规则缓存/DB，避免嵌套占用连接
        rule = get_dcc_qa_rule_by_id(rule_id) if business_type == 1 else get_qa_rule_by_id(rule_id)
        if not rule:
            return None
        self.rdc.set(key, json.dumps(rule, cls=JsonEncoder), ex=QA_INFO_EXPIRATION)
        return rule

    def push_to_delay_mq(self, data, destination, delay=180000):
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
        company_id = request_data["companyId"]
        customer_id = request_data["customerId"]
        mq_config = settings["Realtime_RabbitMq"]
        exchange = mq_config["to_xpusher_exchange"]
        routing_key = mq_config["to_xpusher_routing_key"]
        try:
            self.realtime_mq.publish_message(exchange, routing_key, json.dumps(request_data))
            push_result = "1"
        except Exception as err:
            push_result = "0"
            logger.info(f"[{task_id}]-error:[{err}]")
            # 推送到延迟队列进行重推
            self.push_to_delay_mq(data=request_data, destination="mq")

        # 日志不包含ASR文本
        _ = request_data.pop("transcription", "")
        logger.info(f"[{task_id}]-mqData:[{request_data}]-pushResult:{push_result}")
        count_quasi_realtime_result(status=push_result, company_id=company_id, customer_id=customer_id)

    @push_result_latency.time()
    def push_http_message(self, url, request_data, app_id, count_method):
        """
        推送http消息
        :param app_id:
        :param url: 请求地址
        :param request_data: 请求体
        :param count_method: prometheus计数方法
        :return:
        """
        if not url:
            return
        task_id = request_data["taskId"]
        resp_json = {}
        error = ""
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

        if push_result == "0":
            # 推送到延迟队列进行重推
            self.push_to_delay_mq(data=request_data, destination=url)

        # 日志不包含ASR文本
        push_data = {k: v for k, v in request_data.items() if k != "transcription"}
        if "transcription" in request_data:
            push_data["transcription"] = "*"
        log_message = f"[{task_id}]-url:[{url}]-statusCode:{status_code}-response:[{resp_json}]-requestData:[{push_data}]"
        log_message = log_message + f"-pushError:[{error}]" if error else log_message
        logger.info(log_message)
        # 监控指标
        if count_method:
            count_method(status=push_result, status_code=status_code, app_id=app_id)

    def is_xpusher_result(self, task_id):
        """
        判断是否推送给xpusher
        如果存在taskId对应规则信息，返回True，否则返回False
        :param task_id:
        :return:
        """
        result = self.rdc.get(task_id)
        return True if result else False

    def build_user_action(self, strategy: dict):
        """
        根据策略生成专线控制字段
        """
        control_action = strategy.get("control_action") or 0
        audio_code = strategy.get("audio_code") or ""
        audio_direction = strategy.get("audio_direction") or 0
        return control_action, audio_code if control_action != 2 else "", audio_direction

    @staticmethod
    def _subresult_redis_keys(is_dcc, write_segment, end_flag, minute):
        """按业务类型与 endFlag 决定写入的 Redis list key。仅 isStream 时写 qa_segment。"""
        if is_dcc:
            return [f"DCCQASUBRESULT:{minute}"]
        if write_segment:
            keys = [f"VIQASUBRESULT:{minute}"]
            if end_flag:
                keys.append(f"QASUBRESULT:{minute}")
            return keys
        return [f"QASUBRESULT:{minute}"]

    @staticmethod
    def _subresult_db_targets(is_dcc, write_segment, end_flag):
        """Redis 失败时的直写目标：(engine, Model)。仅 isStream 时写 qa_segment。"""
        if is_dcc:
            return [("dcc_strategy_hs", DccQaDetail)]
        if write_segment:
            targets = [("mid_strategy_hs", QaSegment)]
            if end_flag:
                targets.append(("mid_strategy_hs", QaDetail))
            return targets
        return [("mid_strategy_hs", QaDetail)]

    def _qa_type_is_dedicated(self, qa_type):
        try:
            return int(qa_type) == 2
        except (TypeError, ValueError):
            return False

    def _in_strategy_scope(self, qa_type, is_stream, end_flag):
        """仅 qa_type=2 且 (isStream=False 或 isStream=True 且 endFlag=True) 才进入失败重试。"""
        if not self._qa_type_is_dedicated(qa_type):
            return False
        if is_stream and not end_flag:
            return False
        return True

    def _qa_fail_user_data_str(self, user_data):
        if not user_data:
            return ""
        if isinstance(user_data, str):
            raw = user_data
        else:
            raw = json.dumps(user_data, cls=JsonEncoder)
        return raw[:2048]

    def _parse_task_create_time(self, detail):
        raw = detail.get("create_time")
        if isinstance(raw, datetime.datetime):
            return raw
        if isinstance(raw, str) and raw:
            try:
                return datetime.datetime.strptime(raw, "%Y-%m-%d %H:%M:%S")
            except ValueError:
                pass
        return None

    def _get_qa_fail(self, fail_id):
        if fail_id is None:
            return None
        with session_scope(QA_FAIL_ENGINE) as ss:
            return ss.get(QaFail, fail_id)

    def _qa_fail_url(self, detail, user_data):
        """最后一个分片优先取 completeRecordUrl，否则用分片原 url。"""
        parsed = user_data if isinstance(user_data, dict) else {}
        if not parsed and isinstance(user_data, str) and user_data:
            try:
                data = json.loads(user_data)
                parsed = data if isinstance(data, dict) else {}
            except (TypeError, ValueError, json.JSONDecodeError):
                parsed = {}
        url = detail.get("url") or ""
        if parsed.get("endFlag"):
            complete_record_url = parsed.get("completeRecordUrl") or ""
            if complete_record_url:
                url = complete_record_url
        return url[:256]

    def _insert_qa_fail(self, detail, user_data, rule_id):
        row = QaFail(
            id=detail["id"],
            app_id=detail.get("app_id") or "",
            qa_mobile_id=detail.get("qa_mobile_id") or 0,
            qa_channel_id=detail.get("qa_channel_id") or 0,
            qa_company_id=detail.get("qa_company_id") or 0,
            qa_customer_id=detail.get("qa_customer_id") or 0,
            task_id=str(detail.get("task_id") or ""),
            data_id=str(detail.get("data_id") or ""),
            url=self._qa_fail_url(detail, user_data),
            remark1=(detail.get("remark1") or "")[:256],
            remark2=(detail.get("remark2") or "")[:256],
            remark3=(detail.get("remark3") or "")[:256],
            remark4=(detail.get("remark4") or "")[:256],
            user_data=self._qa_fail_user_data_str(user_data),
            caller=detail.get("caller") or "",
            callee=detail.get("callee") or "",
            retry_count=0,
            retry_status=QA_FAIL_STATUS_PENDING,
            is_done=0,
            rule_id=rule_id or 0,
            qa_type=int(detail.get("qa_type") or 0),
            task_create_time=self._parse_task_create_time(detail),
        )
        with session_scope(QA_FAIL_ENGINE) as ss:
            ss.add(row)

    def _update_qa_fail(self, fail_id, retry_count=None, retry_status=None, is_done=None):
        with session_scope(QA_FAIL_ENGINE) as ss:
            row = ss.get(QaFail, fail_id)
            if not row:
                logger.info(f"[{fail_id}]-qaFail not found")
                return
            if retry_count is not None:
                row.retry_count = retry_count
            # 即使值未变也强制写入，确保 update_time 刷新、中间失败可观测
            if retry_status is not None:
                row.retry_status = retry_status
                flag_modified(row, "retry_status")
            if is_done is not None:
                row.is_done = is_done
            row.update_time = datetime.datetime.now()

    def _handle_qa_fail_retry(self, detail, rule):
        """
        质检失败记录（只写 qa_fail；重试由 retry_worker 定时完成）。
        调用方须已保证 qa_type=2 且 (非流式 or 流式结束片)。
        返回 (write_redis, skip_customer_callback)
        - 成功且存在 qa_fail：retry_status=2，is_done=1，写 redis / 回调
        - 第一次失败：redis 写 status=3，插入 qa_fail
        - 中间失败：只更新 qa_fail（retry_status=0），不写 redis、不回调
        - 满 5 次：retry_status=3，is_done=1，写实际 status / 回调
        """
        task_id = detail.get("task_id")
        data_id = detail.get("data_id")
        status = detail.get("status")
        detail_id = detail.get("id")
        user_data = detail.get("user_data")
        qa_fail = self._get_qa_fail(detail_id)
        rule_id = detail.get("rule_id") or ((rule or {}).get("id") if rule else 0) or 0

        # 成功：有 qa_fail 则收尾
        if str(status) == "1":
            if qa_fail:
                detail["id"] = qa_fail.id
                self._update_qa_fail(
                    qa_fail.id, retry_status=QA_FAIL_STATUS_SUCCESS, is_done=1
                )
                logger.info(
                    f"[{task_id}]-dataId:{data_id}-retry success, id={qa_fail.id}, "
                    f"retry_status={QA_FAIL_STATUS_SUCCESS}, is_done=1"
                )
            return True, False

        # 非成功：写入/更新 qa_fail 并重试
        if not qa_fail:
            detail["status"] = "3"
            self._insert_qa_fail(detail, user_data, rule_id)
            logger.info(f"[{task_id}]-dataId:{data_id}-first fail, id={detail_id}, status=3")
            return True, True

        detail["id"] = qa_fail.id
        # 重试次数达上限
        if qa_fail.retry_count >= QA_FAIL_MAX_RETRY:
            self._update_qa_fail(
                qa_fail.id, retry_status=QA_FAIL_STATUS_FAILED, is_done=1
            )
            logger.info(
                f"[{task_id}]-dataId:{data_id}-retry exhausted, id={qa_fail.id}, "
                f"retry_count={qa_fail.retry_count}, status={status}, "
                f"retry_status={QA_FAIL_STATUS_FAILED}, is_done=1"
            )
            return True, False
        # 重试仍失败
        self._update_qa_fail(qa_fail.id, retry_status=QA_FAIL_STATUS_PENDING)
        logger.info(
            f"[{task_id}]-dataId:{data_id}-intermediate fail, id={qa_fail.id}, "
            f"retry_count={qa_fail.retry_count}, retry_status={QA_FAIL_STATUS_PENDING}"
        )
        return False, True

    def _notify_strategy_config(self, detail):
        """
        专线质检通话后策略
        """
        risk_level = detail.get("risk_level")
        app_id = detail.get("app_id")
        if not risk_level or not self._qa_type_is_dedicated(detail.get("qa_type")):
            return
        url = settings.get("STRATEGY_CONFIG_URL")
        if not url:
            return
        payload = {
            "taskId": detail.get("task_id"),
            "dataId": detail.get("data_id"),
            "riskLevel": risk_level,
            "userData": detail.get("user_data") or "",
            "qaMobileId": detail.get("qa_mobile_id"),
            "qaChannelId": detail.get("qa_channel_id"),
            "qaCompanyId": detail.get("qa_company_id"),
            "qaCustomerId": detail.get("qa_customer_id"),
            "finishTime": detail.get("finish_time"),
            "ragResults": detail.get("rag_results") or "",
        }
        self.push_http_message(url, payload, app_id, None)

    @sub_result_write_latency.time()
    def sub_result(self, data):
        """
        处理子结果
        :param data:
        :return:
        """
        finish_time = datetime.datetime.now()
        task_id = str(data.get("transactionId"))
        data_id = data.get("dataId")
        status = data.get("status")
        error_message = data.get("errorMessage", "")
        qc_result = data.get("qcResult", "")
        risk_level = data.get("riskLevel", "")
        model_info_list = data.get("models", [])
        tag = data.get("tag", "")

        rule = self.get_rule(task_id)
        business_type = get_business_type_by_id(task_id)
        is_dcc = business_type == 1

        key = f"{task_id}:{data_id}"
        detail = self.rdc.get(key)
        if not detail:
            logger.info(f"[{task_id}]-redis无子结果数据:{key}")
            app_id = rule.get("app_id") if rule else ""
            process_id = rule.get("process_id") if rule else ""
            qa_mobile_id = rule.get("qa_mobile_id") if rule else None
            qa_channel_id = rule.get("qa_channel_id") if rule else None
            qa_company_id = rule.get("qa_company_id") if rule else None
            qa_customer_id = rule.get("qa_customer_id") if rule else None
            task_data = rule.get("data") if rule else ""
            # 缓存话单数据到redis，用于补充质检结果数据
            detail = {"data_id": data_id, "task_id": task_id, "app_id": app_id,
                      "create_time": finish_time.strftime("%Y-%m-%d %H:%M:%S"),
                      "id": get_next_id(business_type), "process_id": process_id,
                      "qa_mobile_id": qa_mobile_id, "qa_channel_id": qa_channel_id,
                      "qa_company_id": qa_company_id, "qa_customer_id": qa_customer_id,
                      "data": task_data}
        else:
            detail = json.loads(detail)
            app_id = detail["app_id"]
            process_id = detail["process_id"]
            qa_customer_id = detail["qa_customer_id"]

        if rule and rule.get("id") is not None:
            detail["rule_id"] = rule.get("id")

        # 子结果计数
        count_finished_sub_task(status=status, app_id=app_id)
        create_time = datetime.datetime.strptime(detail["create_time"], "%Y-%m-%d %H:%M:%S")
        process_latency = (finish_time - create_time).seconds
        # 子结果耗时
        observe_sub_task_process_latency(app_id=app_id, amount=process_latency)
        # 流程包含融通模型且执行状态为未执行
        is_rt_not_run = False
        for model_info in model_info_list:
            model_name = model_info.get("name")
            model_status = model_info.get("status")
            model_code = model_info.get("code")
            model_message = model_info.get("message")
            if model_name == "rt" and model_status == 0:
                is_rt_not_run = True
            model_start_time = model_info.get("startTime")
            model_end_time = model_info.get("endTime")
            model_duration = model_end_time-model_start_time if model_start_time and model_end_time else None
            msg = f"[{task_id}]-dataId:{data_id}-modelInfo:[name:{model_name},duration:{model_duration}ms,status:{model_status},code:{model_code},message:{model_message}]"
            logger.info(msg)
            # 每个模型执行次数
            count_model_of_sub_task(status=model_status, app_id=app_id, model=model_name, code=model_code)
            if model_start_time and model_end_time:
                # 每个模型耗时
                observe_sub_task_model_latency(app_id=app_id, model=model_name, amount=model_duration)
                
        # 更新需要入库的数据：优先复用创建时缓存的 id
        if not detail.get("id"):
            detail["id"] = get_next_id(business_type)
        # 创建态快照：失败重试未结束且 key 丢失时回写，避免用处理态 detail 污染缓存
        origin_cache = copy.deepcopy(detail)
        detail["status"] = status
        detail["error_message"] = error_message
        detail["finish_time"] = finish_time.strftime("%Y-%m-%d %H:%M:%S")
        detail["model_status"] = json.dumps(data.get("modelStatus", {})) if data.get("modelStatus", {}) else ""
        detail["model_info"] = json.dumps(model_info_list) if model_info_list else ""
        detail["tag"] = tag
        # 场景字段可能为空，转成空字符串
        if not is_dcc:
            detail["zjh_scene"] = detail["zjh_scene"] if detail.get("zjh_scene") else ""
        # 回调的数据,部分成功视为成功
        request_data = {"taskId": task_id, "listId": data_id, "errorMessage": error_message,
                        "status": status if status == "0" else "1"}
        if qc_result:
            # 质检结果是否正常
            detail["qc_result"] = qc_result
            request_data["qcResult"] = qc_result
        if risk_level:
            detail["risk_level"] = risk_level
            request_data["riskLevel"] = risk_level

        # 透传数据
        task_data = detail.get("data", "")
        if task_data:
            request_data["data"] = task_data
        # 透传专线质检话单信息；isStream=True 时按 endFlag 决定落库表
        # endFlag=True → qa_detail + qa_segment；False → 仅 qa_segment；非流式仍只写 qa_detail
        user_data = detail.get("user_data")
        has_user_data = bool(user_data and isinstance(user_data, dict))
        is_stream = bool(user_data.get("isStream")) if has_user_data else False
        write_segment = has_user_data and is_stream
        end_flag = bool(user_data.get("endFlag")) if write_segment else False
        if has_user_data:
            detail["user_data"] = json.dumps(user_data)
            # 质检结果为异常时，生成专线控制字段
            if qc_result == "0" and risk_level:
                qa_customer_id = detail.get("qa_customer_id")
                strategy = get_strategy_config(qa_customer_id, risk_level, process_id)
                ctrl_action, audio_code, audio_direction = self.build_user_action(strategy)
                user_data["ctrlAction"] = ctrl_action
                user_data["audioCode"] = audio_code
                user_data["audioDirection"] = audio_direction
                user_data["callId"] = data_id

                detail["ctrl_action"] = ctrl_action
                detail["audio_code"] = audio_code
                detail["audio_direction"] = audio_direction
                detail["control_type"] = strategy.get("control_type") if strategy else ""
            request_data["userData"] = user_data

        # 录音时长
        duration = data.get("audioDuration", 0) if "audioDuration" in data else detail.get("duration", 0)
        detail["duration"] = duration
        request_data["duration"] = duration
        detail["duration_min"] = math.ceil(duration / 60) if duration else 0
        detail["duration_ps"] = math.ceil(duration / 6) if duration else 0
        try:
            if status != "0" and int(duration or 0) == 0:
                count_zero_duration_sub_result(app_id)
        except (TypeError, ValueError):
            count_zero_duration_sub_result(app_id)
        # 是否需要复核
        detail["is_need_review"] = data.get("review", "0")

        # asr文本
        if "asrText" in data:
            content = data.get("asrText", "")
            # 工作号ASR结果未脱敏不入库
            detail["content"] = content if app_id not in GZH_LIST else ""
            request_data["transcription"] = content
        asr_info = data.get("asrInfo")
        if asr_info:
            detail["asr_info"] = json.dumps(asr_info)
        # 匹配到的关键词
        matched_word = data.get("matchedWord", [])
        detail["matched_word"] = str(matched_word) if matched_word else ""
        if "matchedWord" in data:
            request_data["matchedWord"] = matched_word
        # 摘要
        summary = data.get("summary", "")
        detail["summary"] = summary
        if summary:
            request_data["summary"] = summary
        # 情绪分析结果
        emotion_analysis = data.get("emotionAnalysis", "")
        detail["emotion_analysis"] = emotion_analysis
        if emotion_analysis:
            request_data["emotionAnalysis"] = emotion_analysis

        # 场景识别结果
        scene = data.get("scenarioResult", "")
        detail["scene"] = scene
        if "scenarioResult" in data:
            request_data["scene"] = scene

        # 场景一致性结果
        scene_consistency = data.get("scenarioConsistency", "")
        detail["scene_consistency"] = scene_consistency
        if "scenarioConsistency" in data:
            request_data["sceneConsistency"] = scene_consistency
        scene_score = data.get("scenarioScore")
        detail["scene_score"] = scene_score

        # 话术一致性结果
        script_consistency = data.get("scriptResult", "")
        script_id_list = data.get("scriptsIds", [])
        detail["script_consistency"] = script_consistency
        detail["matched_script"] = ",".join(script_id_list)
        if "scriptResult" in data:
            request_data["scriptConsistency"] = script_consistency

        # 声音鉴伪
        vfd_result = data.get("vfdResult", "")
        detail["vfd_result"] = vfd_result
        if "vfdResult" in data:
            request_data["vfdResult"] = vfd_result
        vfd_score = data.get("vfdScore")
        detail["vfd_score"] = vfd_score
        # 关键词分析结果
        keyword_analysis_result = data.get("keywordAnalysisResult", [])
        ka_result = "0"
        for item in keyword_analysis_result:
            if item["result"] == "1":
                ka_result = "1"
        detail["keyword_analysis_result"] = ka_result if keyword_analysis_result else ""
        detail["keyword_analysis_words"] = str(keyword_analysis_result) if keyword_analysis_result else ""
        if "keywordAnalysisResult" in data:
            request_data["fraudDetectionResult"] = keyword_analysis_result

        customized_detection_result = data.get("difyResult")
        detail["customized_detection_result"] = str(customized_detection_result) if customized_detection_result else ""
        if "difyResult" in data:
            request_data["customizedDetectionResult"] = customized_detection_result

        # 融通质检
        rt_result_code = data.get("rtResultCode", "")
        rt_ability_type = data.get("rtAbilityType", "")
        rt_first_label = data.get("rtFirstLabel", "")
        rt_second_label = data.get("rtSecondLabel", "")
        rt_third_label = data.get("rtThirdLabel", "")
        rt_svc_type = data.get("svcType", "")
        detail["rt_result_code"] = rt_result_code
        detail["rt_ability_type"] = rt_ability_type
        detail["rt_first_label"] = rt_first_label
        detail["rt_second_label"] = rt_second_label
        try:
            detail["rt_third_label"] = float(rt_third_label) if rt_third_label else None
        except ValueError:
            detail["rt_third_label"] = None
        detail["rt_svc_type"] = rt_svc_type
        if "svcType" in data and "rtFirstLabel" in data:
            if rt_svc_type == "0":
                # 1-正常 0-涉诈
                rt_detection_result = "0" if rt_first_label == "4" else "1"
            else:
                # 1-场景一致 0-场景不一致
                rt_detection_result = "1" if rt_first_label == "11" else "0"
        else:
            if "转写文字长度" in error_message and is_rt_not_run:
                # 转写文字长度小于阈值不进行下一步，认为录音内容正常
                rt_detection_result = "1"
            else:
                rt_detection_result = ""
        if rt_detection_result:
            request_data["rtDetectionResult"] = rt_detection_result

        # 催收模型分析
        cmic_internal_collection_result = data.get("cmic_internal_collection_result", "")
        detail["cmic_internal_collection_result"] = str(cmic_internal_collection_result)
        if cmic_internal_collection_result:
            request_data["collectionAnalyseResult"] = cmic_internal_collection_result
        collection_score = data.get("collectionScore")
        detail["collection_score"] = collection_score

        # 文本相似度
        content_similarity_qc_result = data.get("content_similarity_qc_result", "")
        detail["content_similarity_qc_result"] = content_similarity_qc_result
        if content_similarity_qc_result:
            request_data["textSimilarityResult"] = content_similarity_qc_result
        content_similarity_score = data.get("contentSimilarityScore")
        detail["content_similarity_score"] = content_similarity_score

        # 涉黄涉暴涉政涉赌毒分析结果
        keyword_rs_result = data.get("keywordRSResult", [])
        for item in keyword_rs_result:
            if item["type"] == "pornRelated":
                request_data["pornRelated"] = item["result"]
                detail["porn_related"] = item["result"]
            if item["type"] == "politicsRelated":
                request_data["politicsRelated"] = item["result"]
                detail["politics_related"] = item["result"]
            if item["type"] == "violenceRelated":
                request_data["violenceRelated"] = item["result"]
                detail["violence_related"] = item["result"]
            if item["type"] == "gambleDrugRelated":
                request_data["gambleDrugRelated"] = item["result"]
                detail["gambledrug_related"] = item["result"]

        # 声纹结果-1:1
        vp_score = data.get("vpScore")
        if vp_score is not None:
            detail["vp_score"] = vp_score
            request_data["vpScore"] = vp_score
        # 声纹结果-1:N
        vp_top_k_tags = data.get("vpTopKTags", [])
        if vp_top_k_tags:
            detail["vp_top_k_tags"] = str(vp_top_k_tags)
            request_data["vpTopKTags"] = vp_top_k_tags

        # 从业者比对结果
        identity_consistency = data.get("identityConsistency")
        if identity_consistency:
            detail["identity_consistency"] = identity_consistency
            request_data["identityConsistency"] = identity_consistency
        identity_score = data.get("identityScore")
        detail["identity_score"] = identity_score

        emotion_result = data.get("emotionResult")
        detail["emotion_result"] = str(emotion_result) if emotion_result else ""
        if emotion_result:
            request_data["emotionResult"] = emotion_result

        # deepseek结果
        deepseek_result = data.get("deepSeekResult", "")
        deepseek_reason = data.get("deepSeekReason", "")
        deepseek_extra_data = data.get("deepSeekExtraData", "")
        detail["deepseek_result"] = deepseek_result
        detail["deepseek_reason"] = deepseek_reason
        detail["deepseek_extra_data"] = deepseek_extra_data
        if deepseek_result or deepseek_reason or deepseek_extra_data:
            request_data["deepseekResult"] = deepseek_result
            request_data["deepseekReason"] = deepseek_reason
            request_data["deepseekExtraData"] = deepseek_extra_data

        # AIGS结果
        aigs_content = data.get("aigsContent", "")
        aigs_reason = data.get("aigsReason", "")
        aigs_confidence = data.get("aigsConfidence", "")
        aigs_is_fraud = data.get("aigsIsFraud", "")
        detail["aigs_content"] = aigs_content
        detail["aigs_reason"] = aigs_reason
        detail["aigs_confidence"] = str(aigs_confidence)
        detail["aigs_is_fraud"] = str(aigs_is_fraud)
        if aigs_content or aigs_reason or aigs_confidence or aigs_is_fraud:
            request_data["aigsContent"] = aigs_content
            request_data["aigsReason"] = aigs_reason
            request_data["aigsConfidence"] = aigs_confidence
            request_data["aigsIsFraud"] = aigs_is_fraud

        # 词组匹配
        word_group_result = data.get("wordGroupResult", "")
        detail["word_group_result"] = word_group_result
        if word_group_result:
            request_data["wordGroupResult"] = word_group_result

        qwen_result = data.get("qwenResult")
        if qwen_result:
            detail["qwen_result"] = str(qwen_result)
            request_data["qwenResult"] = qwen_result

        # 融通涉诈复核
        rt_review_result_code = data.get("rtFraudReviewResultCode", "")
        rt_review_ability_type = data.get("rtFraudReviewAbilityType", "")
        rt_review_first_label = data.get("rtFraudReviewFirstLabel", "")
        rt_review_second_label = data.get("rtFraudReviewSecondLabel", "")
        rt_review_third_label = data.get("rtFraudReviewThirdLabel", "")
        if rt_review_result_code:
            detail["rt_review_result_code"] = rt_review_result_code
        if rt_review_ability_type:
            detail["rt_review_ability_type"] = rt_review_ability_type
        if rt_review_first_label:
            detail["rt_review_first_label"] = rt_review_first_label
        if rt_review_second_label:
            detail["rt_review_second_label"] = rt_review_second_label
        try:
            if rt_review_third_label:
                detail["rt_review_third_label"] = float(rt_review_third_label)
        except ValueError:
            detail["rt_review_third_label"] = None

        market_call_result = data.get("marketCallResult", "")
        if market_call_result:
            detail["market_call_result"] = market_call_result
            request_data["marketCallResult"] = market_call_result

        # 正则结果
        regex_result = data.get("wordRegexResult", [])
        if "wordRegexResult" in data:
            detail["regex_result"] = str(regex_result) if regex_result else ""
            request_data["wordRegexResult"] = regex_result

        deal_label = data.get("dealLabel", "")
        deal_need_review = data.get("dealNeedReview", "")
        if deal_label or deal_need_review:
            detail["deal_label"] = deal_label
            detail["deal_need_review"] = deal_need_review
            request_data["dealLabel"] = deal_label
            request_data["dealNeedReview"] = deal_need_review
        
        rag_results = data.get("ragResults", [])
        rag_count = data.get("ragCount")
        if "ragResults" in data or "ragCount" in data:
            detail["rag_results"] = str(rag_results) if rag_results else ""
            detail["rag_count"] = rag_count
            request_data["ragResults"] = rag_results
            request_data["ragCount"] = rag_count
        
        keyword_analysis_lt_result = data.get("keywordAnalysisLTResult", "")
        if keyword_analysis_lt_result:
            detail["keyword_analysis_lt_result"] = json.dumps(keyword_analysis_lt_result) if keyword_analysis_lt_result else ""
            request_data["keywordAnalysisLTResult"] = keyword_analysis_lt_result
        
        # 重庆行业卡反诈
        cq_anti_filter_type = data.get("cqAntiFilterType")
        cq_anti_risk_level = data.get("cqAntiRiskLevel")
        cq_anti_result = data.get("cqAntiResult", [])
        if cq_anti_filter_type is not None:
            detail["cq_anti_filter_type"] = str(cq_anti_filter_type)
            request_data["cqAntiFilterType"] = str(cq_anti_filter_type)
        if cq_anti_risk_level is not None:
            detail["cq_anti_risk_level"] = str(cq_anti_risk_level)
            request_data["cqAntiRiskLevel"] = str(cq_anti_risk_level)
        if cq_anti_result:
            detail["cq_anti_result"] = str(cq_anti_result)
            request_data["cqAntiResult"] = cq_anti_result

        bge_rule_name = data.get("bgeRuleName", "")
        bge_expression = data.get("bgeExpression", "")
        bge_output_label = data.get("bgeOutputLabel", "")
        bge_priority = data.get("bgePriority", "")
        if bge_rule_name or bge_expression or bge_output_label or bge_priority:
            detail["bge_rule_name"] = bge_rule_name
            detail["bge_expression"] = bge_expression
            detail["bge_output_label"] = bge_output_label
            detail["bge_priority"] = bge_priority
            request_data["bgeRuleName"] = bge_rule_name
            request_data["bgeExpression"] = bge_expression
            request_data["bgeOutputLabel"] = bge_output_label
            request_data["bgePriority"] = bge_priority

        # 重庆行业卡Agent复核
        cq_agent_is_violation = data.get("cqAgentIsViolation")
        cq_agent_is_fraud = data.get("cqAgentIsFraud")
        cq_agent_risk_level = data.get("cqAgentRiskLevel")
        cq_agent_scene_category = data.get("cqAgentSceneCategory")
        cq_agent_scene_subtype = data.get("cqAgentSceneSubtype")
        cq_agent_confidence = data.get("cqAgentConfidence")
        cq_agent_source = data.get("cqAgentSource")
        cq_agent_detail = data.get("cqAgentDetail")

        if cq_agent_is_violation is not None:
            detail["cq_agent_is_violation"] = bool(int(cq_agent_is_violation))
            request_data["cqAgentIsViolation"] = cq_agent_is_violation
        if cq_agent_is_fraud is not None:
            detail["cq_agent_is_fraud"] = bool(int(cq_agent_is_fraud))
            request_data["cqAgentIsFraud"] = cq_agent_is_fraud
        if cq_agent_risk_level is not None:
            detail["cq_agent_risk_level"] = cq_agent_risk_level
            request_data["cqAgentRiskLevel"] = cq_agent_risk_level
        if cq_agent_scene_category is not None:
            detail["cq_agent_scene_category"] = cq_agent_scene_category
            request_data["cqAgentSceneCategory"] = cq_agent_scene_category
        if cq_agent_scene_subtype is not None:
            detail["cq_agent_scene_subtype"] = cq_agent_scene_subtype
            request_data["cqAgentSceneSubtype"] = cq_agent_scene_subtype
        if cq_agent_confidence is not None and cq_agent_confidence != "":
            detail["cq_agent_confidence"] = float(cq_agent_confidence)
            request_data["cqAgentConfidence"] = cq_agent_confidence
        if cq_agent_source is not None:
            detail["cq_agent_source"] = cq_agent_source
            request_data["cqAgentSource"] = cq_agent_source
        if cq_agent_detail is not None:
            detail["cq_agent_detail"] = json.dumps(cq_agent_detail)
            request_data["cqAgentDetail"] = cq_agent_detail

        # qa_type=2 流式结束片：qa_detail 用完整录音 URL，qa_segment 仍用分片原 URL
        detail_url_override = None
        if (
            detail.get("qa_type") == 2
            and is_stream
            and end_flag
            and has_user_data
        ):
            complete_record_url = user_data.get("completeRecordUrl")
            if complete_record_url:
                detail_url_override = complete_record_url

        # 仅 qa_type=2 且 (非流式 or 流式结束片) 才走失败重试
        if self._in_strategy_scope(detail.get("qa_type"), is_stream, end_flag):
            write_redis, skip_customer_callback = self._handle_qa_fail_retry(detail, rule)
        else:
            write_redis, skip_customer_callback = True, False

        def _payload_for_key(list_key):
            if detail_url_override and list_key.startswith("QASUBRESULT:"):
                row = dict(detail)
                row["url"] = detail_url_override
                return json.dumps(row)
            return json.dumps(detail)

        if write_redis:
            try:
                minute = finish_time.strftime("%Y%m%d%H%M")
                list_keys = self._subresult_redis_keys(is_dcc, write_segment, end_flag, minute)
                for list_key in list_keys:
                    self.rdc.lpush(list_key, _payload_for_key(list_key))
            except Exception as err:
                logger.info(f"[{task_id}]-redisError:{err}")
                for engine, DetailModel in self._subresult_db_targets(is_dcc, write_segment, end_flag):
                    row = detail
                    if detail_url_override and DetailModel is QaDetail:
                        row = dict(detail)
                        row["url"] = detail_url_override
                    with ck_session_scope(engine=engine) as ss:
                        ss.add(DetailModel(**row))
                        ss.commit()

        # 失败重试未结束：保留 {task_id}:{data_id} 供后续回调复用 id；其余处理完即删
        try:
            if skip_customer_callback:
                if not self.rdc.expire(key, QA_INFO_EXPIRATION):
                    origin_cache["id"] = detail.get("id") or origin_cache.get("id")
                    self.rdc.set(key, json.dumps(origin_cache, cls=JsonEncoder), ex=QA_INFO_EXPIRATION)
            else:
                self.rdc.delete(key)
        except Exception as err:
            logger.info(f"[{task_id}]-dataId:{data_id}-cache cleanup error:{err}")

        if skip_customer_callback:
            return "", {}, app_id

        if self._in_strategy_scope(detail.get("qa_type"), is_stream, end_flag) and risk_level in ["1", "2"]:
            self._notify_strategy_config(detail)

        if self.is_xpusher_result(task_id) and not is_dcc:
            # 准实时质检结果推送到xpusher并由xpusher推送结果给客户
            request_data["callId"] = data_id
            request_data["companyId"] = detail["company_id"]
            request_data["customerId"] = detail["customer_id"]
            return "MQ", request_data, app_id
        # 非准实时质检结果直接推送给客户
        url = rule.get("sub_url") if rule else ""
        return url, request_data, app_id

    @main_result_write_latency.time()
    def main_result(self, data):
        """
        处理总结果
        :param data:
        :return:
        """
        task_id = str(data.get("transactionId"))
        finish_rate = data.get("finishRate")
        error_list = data.get("error", [])
        fail_count = data.get("failCount", 0)

        business_type = get_business_type_by_id(task_id)
        with session_scope() as ss:
            task = ss.get(DccQaTask, task_id) if business_type == 1 else ss.get(QaTask, task_id)
            if not task:
                logger.info(f"[{task_id}]-无任务记录")
                return "", {}, ""
            task.status = "1"
            task.completion_ratio = finish_rate
            task.error_num = fail_count
            rule_id = task.rule_id

        # 避免在已持有 MySQL session 时再嵌套开连接（易触发 Packet sequence 错乱）
        rule = self.get_rule(task_id)
        if not rule:
            logger.info(f"[{task_id}]-ruleId:{rule_id}-无规则记录")
            return "", {}, ""
        raw_data = rule.get("data")
        url = rule.get("main_url")
        app_id = rule.get("app_id")
        request_data = {"taskId": task_id, "finishRate": finish_rate, "error": error_list, "data": raw_data}
        count_finished_main_task(app_id=app_id)
        return url, request_data, app_id

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
        data_type = ""
        try:
            data = loads_mq_json(body)
            task_id = str(data.get("transactionId", ""))
            receive_data = {k: v for k, v in data.items() if k != "asrText"}
            if "asrText" in data:
                receive_data["asrText"] = "*"
            logger.info(f"[{task_id}]-receive:[{receive_data}]")
            data_type = data.pop("type", "")
            if data_type == 1:
                url, request_data, app_id = self.sub_result(data)
                if url == "MQ":
                    # 准实时任务结果推到mq
                    self.push_mq_message(request_data)
                else:
                    self.push_http_message(url, request_data, app_id, count_sub_result)
            else:
                url, request_data, app_id = self.main_result(data)
                self.push_http_message(url, request_data, app_id, count_main_result)
        except Exception as err:
            if not isinstance(data, dict):
                logger.error("mq poison/invalid json: %s body=%r", err, body)
                count_callback_error()
            else:
                # 日志不包含ASR文本
                asr = data.pop("asrText", "")
                logger.info(f"[{task_id}]-body:[{data}]-mqCallbackError:[{err}]")
                count_callback_error()
                encrypt_asr = self.aes_util.encrypt(base64.b64encode(asr.encode()).decode()) if asr else ""
                data["transcription"] = encrypt_asr
                data["create_time"] = datetime.datetime.now()
                data["type"] = data_type
                self.mongodb["service"]["process_fail"].insert_one(data)
        finally:
            safe_ack(ch, method)

    def run(self):
        queue = self.mq_info["from_scheduler_queue"]
        prefetch_count = self.mq_info["prefetch_count"]
        self.mq.consume_queue(
            queue_list=[queue], 
            callback=self.callback, 
            auto_ack=False, 
            prefetch_count=prefetch_count,
            stop_event=self._stop_event
        )
