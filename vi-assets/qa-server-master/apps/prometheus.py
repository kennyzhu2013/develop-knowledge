import logging
import os
import re

from prometheus_client import multiprocess
from prometheus_client.core import CollectorRegistry
from prometheus_client.exposition import choose_encoder
from prometheus_client.metrics import Counter, Gauge, Histogram

from utils.cache import get_name_by_app_id, get_qa_type_by_app_id

logger = logging.getLogger("all")

# 仅用于 /metrics 聚合导出；指标本身不要注册到该 registry，避免与 MultiProcessCollector 重复导出
registry = CollectorRegistry()
multiprocess.MultiProcessCollector(registry)

# 指标写入默认 REGISTRY；multiprocess 模式下实际落盘到 PROMETHEUS_MULTIPROC_DIR
# 服务被请求数量
request_counter = Counter(name="request_counter", documentation="total of request to this server",
                          namespace="uniops", labelnames=["jobname", "instance", "status_code"])
# HTTP 200 时按业务响应 code 计数
response_code_counter = Counter(
    name="response_code_counter",
    documentation="count business response code when http status is 200",
    namespace="uniops",
    labelnames=["jobname", "code"],
)
# 创建的总任务数量
created_main_task_counter = Counter(name="created_main_task_counter", documentation="total of created task",
                                    namespace="uniops", labelnames=["status", "status_code", "jobname", "app_id", "app_name", "qa_type"])
# 完成的总任务数量
finished_main_task_counter = Counter(name="finished_main_task_counter",
                                     documentation="count when server receive a main result",
                                     namespace="uniops", labelnames=["status", "jobname", "app_id", "app_name", "qa_type"])
# 完成的子任务数量
finished_sub_task_counter = Counter(name="finished_sub_task_counter",
                                    documentation="count when server receive a sub result",
                                    namespace="uniops", labelnames=["status", "jobname", "app_id", "app_name", "qa_type"])
# 子结果 duration=0 数量
zero_duration_sub_result_counter = Counter(
    name="zero_duration_sub_result_counter",
    documentation="count sub result when duration is 0",
    namespace="uniops",
    labelnames=["jobname", "app_id", "app_name", "qa_type"],
)
# 待开始的子任务数量
created_sub_task_counter = Counter(name="created_sub_task_counter", documentation="count when server create a task",
                                   namespace="uniops", labelnames=["status", "jobname", "app_id", "app_name", "qa_type"])
# 子结果回调情况
sub_result_counter = Counter(name="sub_result_counter", documentation="total of sub result", namespace="uniops",
                             labelnames=["status_code", "status", "jobname", "app_id", "app_name", "qa_type"])
# 总结果回调情况
main_result_counter = Counter(name="main_result_counter", documentation="total of main result", namespace="uniops",
                              labelnames=["status_code", "status", "jobname", "app_id", "app_name", "qa_type"])

# 子任务质检流程时延
sub_task_process_latency = Histogram(name="sub_task_process_latency", documentation="monitor latency of processing sub task",
                                     namespace="uniops", labelnames=["jobname", "app_id", "app_name", "qa_type"])
# 每个模型的处理时延
sub_task_model_latency = Histogram(name="sub_task_model_latency", documentation="monitor latency of all models per sub task",
                                   namespace="uniops", labelnames=["jobname", "app_id", "app_name", "model", "qa_type"])
# 每个模型的处理次数
model_of_sub_task_counter = Counter(name="model_of_sub_task_counter",
                                    documentation="count model when receive a sub result",
                                    namespace="uniops", labelnames=["status", "jobname", "app_id", "app_name", "model", "code", "qa_type"])

# 准实时的子任务数量
quasi_realtime_task_counter = Counter(name="quasi_realtime_task_counter",
                                      documentation="count when server create a quasi-realtime task",
                                      namespace="uniops", labelnames=["status", "jobname", "company_id", "customer_id"])

# 准实时的子任务回调数量
quasi_realtime_result_counter = Counter(name="quasi_realtime_result_counter",
                                        documentation="count when server finish a quasi-realtime task",
                                        namespace="uniops", labelnames=["status", "jobname", "company_id", "customer_id"])
# 任务回调处理出错数量
callback_error_counter = Counter(name="callback_error_counter",
                                 documentation="count when error happens",
                                 namespace="uniops", labelnames=["jobname"])
# ETCD watch 连接/流异常次数
etcd_watch_error_counter = Counter(
    name="etcd_watch_error_counter",
    documentation="count when etcd watch connect or stream fails before reconnect",
    namespace="uniops",
    labelnames=["jobname", "prefix", "error_type", "phase"],
)
# ETCD watch 是否已建立（livesum 仅汇总存活进程）
etcd_watch_alive = Gauge(
    name="etcd_watch_alive",
    documentation="1 if etcd watch is established for prefix, else 0",
    namespace="uniops",
    labelnames=["jobname", "prefix"],
    multiprocess_mode="livesum",
)
# 子结果处理时延
sub_result_write_latency = Histogram(name="sub_result_write_latency",
                                     documentation="monitor latency of writing sub result data",
                                     namespace="uniops")
# 总结果处理时延
main_result_write_latency = Histogram(name="main_result_write_latency",
                                      documentation="monitor latency of updating main result data",
                                      namespace="uniops")

# 结果推送时延
push_result_latency = Histogram(name="push_result_latency",
                                documentation="monitor latency of pushing result",
                                namespace="uniops")

# 任务创建请求处理时延
create_task_latency = Histogram(name="create_task_latency",
                                documentation="monitor latency of creating task",
                                namespace="uniops")

# 重推结果回调情况
repush_result_counter = Counter(name="repush_result_counter", documentation="count when repushing result",
                                namespace="uniops", labelnames=["status", "jobname"])

# 请求处理报错计数
request_error_counter = Counter(name="request_error_counter", documentation="count when request error happens",
                                namespace="uniops", labelnames=["jobname", "app_id", "app_name", "qa_type"])

# 子结果入库报错计数
result_write_error_counter = Counter(name="result_write_error_counter", documentation="count when writing error happens",
                                     namespace="uniops", labelnames=["jobname"])

# 异步线程池排队+执行中任务数（submit 时 +1，任务结束 -1）
# livesum：只汇总仍存活进程的值，worker 退出后由 mark_process_dead 清理，避免死进程残留导致虚高
pending_async_tasks = Gauge(name="pending_async_tasks",
                            documentation="pending async tasks in _async_executor",
                            namespace="uniops",
                            multiprocess_mode="livesum")

# 消费循环是否仍在跑：按线程打标，避免同进程多消费者互相覆盖
mq_consumer_alive = Gauge(name="mq_consumer_alive",
                          documentation="1 if mq consume loop thread is running",
                          namespace="uniops",
                          labelnames=["jobname", "queue", "thread"],
                          multiprocess_mode="livesum")
mq_consume_reconnect_counter = Counter(name="mq_consume_reconnect_counter",
                                       documentation="count when mq consume loop reconnects after error",
                                       namespace="uniops",
                                       labelnames=["jobname", "queue"])

jobname = "uniops"

# multiproc 文件名形如 counter_12345.db / gauge_livesum_12345.db
_MULTIPROC_PID_RE = re.compile(r"_(\d+)\.db$")


def _pid_is_alive(pid):
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        # 进程存在但无权限发信号，视为仍存活
        return True
    except OSError:
        return False


def cleanup_multiproc_files(pids=None):
    """
    清理已退出进程的 multiproc 残留。

    注意：按 prometheus_client 约定，Counter/Histogram 的死进程文件应保留以便累计值连续；
    这里对指定/已死 pid 只做 mark_process_dead（清理 live* Gauge），不删除 counter/histogram，
    避免 worker 重启后面板出现计数断崖。目录级堆积仍依赖进程启动时的清空。
    """
    multiproc_dir = os.environ.get("PROMETHEUS_MULTIPROC_DIR", "")
    if not multiproc_dir or not os.path.isdir(multiproc_dir):
        return 0

    if pids is not None:
        target_pids = {int(p) for p in pids}
    else:
        target_pids = set()
        try:
            names = os.listdir(multiproc_dir)
        except OSError:
            logger.exception("列出 PROMETHEUS_MULTIPROC_DIR 失败: %s", multiproc_dir)
            return 0
        for name in names:
            match = _MULTIPROC_PID_RE.search(name)
            if not match:
                continue
            pid = int(match.group(1))
            if not _pid_is_alive(pid):
                target_pids.add(pid)

    cleaned = 0
    for pid in target_pids:
        try:
            multiprocess.mark_process_dead(pid, path=multiproc_dir)
            cleaned += 1
        except TypeError:
            # 兼容旧版签名 mark_process_dead(pid)
            try:
                multiprocess.mark_process_dead(pid)
                cleaned += 1
            except Exception:
                logger.exception("mark_process_dead 失败: pid=%s", pid)
        except Exception:
            logger.exception("mark_process_dead 失败: pid=%s", pid)

    if cleaned:
        logger.info("已对 %d 个退出进程执行 mark_process_dead (dir=%s)", cleaned, multiproc_dir)
    return cleaned


def get_metrics(accept_header):
    """
    获取指标
    :param accept_header:
    :return:
    """
    encoder, content_type = choose_encoder(accept_header)
    return encoder(registry), content_type


def count_request(status_code, instance, amount=1):
    """
    服务接口请求计数
    :param status_code:
    :param instance:
    :param amount:
    :return:
    """
    request_counter.labels(jobname=jobname, instance=instance, status_code=status_code).inc(amount=amount)


def count_response_code(code, amount=1):
    """
    HTTP 200 时按业务响应 code 计数
    :param code: 响应体中的 code，如 0000、1001
    :param amount:
    :return:
    """
    response_code_counter.labels(jobname=jobname, code=str(code) if code is not None else "").inc(amount=amount)


def count_created_main_task(status, status_code, app_id, amount=1):
    """
    创建的总任务个数
    :param app_id:
    :param status_code:
    :param status:
    :param amount:
    :return:
    """
    created_main_task_counter.labels(status=status, status_code=status_code, jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_finished_main_task(app_id, status="1", amount=1):
    """
    已完成总任务结果计数
    :param app_id:
    :param status:
    :param amount:
    :return:
    """
    finished_main_task_counter.labels(status=status, jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_created_sub_task(app_id, status="1", amount=1):
    """
    创建的子任务个数
    :param app_id:
    :param status:
    :param amount:
    :return:
    """
    created_sub_task_counter.labels(jobname=jobname, status=status, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_finished_sub_task(status, app_id, amount=1):
    """
    已完成子任务结果计数
    :param app_id:
    :param status:
    :param amount:
    :return:
    """
    finished_sub_task_counter.labels(status=status, jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_zero_duration_sub_result(app_id, amount=1):
    """
    子结果 duration=0 计数
    :param app_id:
    :param amount:
    :return:
    """
    zero_duration_sub_result_counter.labels(
        jobname=jobname,
        app_id=app_id or "",
        app_name=get_name_by_app_id(app_id),
        qa_type=get_qa_type_by_app_id(app_id),
    ).inc(amount=amount)


def count_sub_result(status, status_code, app_id, amount=1):
    """
    子结果回调计数
    :param app_id:
    :param status_code:
    :param status:
    :param amount:
    :return:
    """
    sub_result_counter.labels(jobname=jobname, status=status, status_code=status_code, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_main_result(status, status_code, app_id, amount=1):
    """
    总结果回调计数
    :param app_id:
    :param status_code:
    :param status:
    :param amount:
    :return:
    """
    main_result_counter.labels(jobname=jobname, status=status, status_code=status_code, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_quasi_realtime_task(company_id, customer_id, status="1", amount=1):
    """
    准实时子任务计数
    :param customer_id:
    :param company_id:
    :param status:
    :param amount:
    :return:
    """
    quasi_realtime_task_counter.labels(jobname=jobname, status=status, company_id=company_id, customer_id=customer_id).inc(amount=amount)


def count_quasi_realtime_result(company_id, customer_id, status="1", amount=1):
    """
    准实时子任务回调计数
    :param customer_id:
    :param company_id:
    :param status:
    :param amount:
    :return:
    """
    quasi_realtime_result_counter.labels(jobname=jobname, status=status, company_id=company_id, customer_id=customer_id).inc(amount=amount)


def count_callback_error(amount=1):
    """
    任务回调处理出错数量
    :param amount:
    :return:
    """
    callback_error_counter.labels(jobname=jobname).inc(amount=amount)


def count_etcd_watch_error(prefix, error_type, phase, amount=1):
    """
    ETCD watch 连接或流异常计数
    :param prefix: 监听的 key 前缀
    :param error_type: 异常类型名，如 WatchTimedOut、ConnectionFailedError
    :param phase: connect（建连/建 watch）或 stream（已建立后断流/僵死）
    :param amount:
    :return:
    """
    etcd_watch_error_counter.labels(
        jobname=jobname, prefix=prefix, error_type=error_type, phase=phase
    ).inc(amount=amount)


def set_etcd_watch_alive(prefix, alive):
    """
    ETCD watch 是否处于已建立状态
    :param prefix:
    :param alive: True/False
    :return:
    """
    etcd_watch_alive.labels(jobname=jobname, prefix=prefix).set(1 if alive else 0)


def count_repushed_result(status, amount=1):
    """
    重推结果计数
    :param status:
    :param amount:
    :return:
    """
    repush_result_counter.labels(status=status, jobname=jobname).inc(amount=amount)


def count_request_error(app_id, amount=1):
    """
    处理请求时报错计数
    :param app_id:
    :param amount:
    :return:
    """
    request_error_counter.labels(jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def count_result_write_error(amount=1):
    """
    子结果入库报错计数
    :param amount:
    :return:
    """
    result_write_error_counter.labels(jobname=jobname).inc(amount=amount)


def observe_sub_task_process_latency(app_id, amount):
    """
    子结果全流程处理时延
    :param app_id:
    :param amount:
    :return:
    """
    sub_task_process_latency.labels(jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), qa_type=get_qa_type_by_app_id(app_id)).observe(amount=amount)


def observe_sub_task_model_latency(app_id, model, amount):
    """
    子结果中每个模型处理时延
    :param app_id:
    :param model:
    :param amount:
    :return:
    """
    sub_task_model_latency.labels(jobname=jobname, app_id=app_id, app_name=get_name_by_app_id(app_id), model=model, qa_type=get_qa_type_by_app_id(app_id)).observe(amount=amount)


def count_model_of_sub_task(status, app_id, model, code, amount=1):
    """
    已完成子任务结果计数
    :param app_id:
    :param status:
    :param model:
    :param code:
    :param amount:
    :return:
    """
    model_of_sub_task_counter.labels(status=status, jobname=jobname, app_id=app_id, code=code, 
                                     app_name=get_name_by_app_id(app_id), model=model, qa_type=get_qa_type_by_app_id(app_id)).inc(amount=amount)


def set_mq_consumer_alive(queue, thread, alive=True):
    mq_consumer_alive.labels(jobname=jobname, queue=queue, thread=thread).set(1 if alive else 0)


def count_mq_consume_reconnect(queue, amount=1):
    mq_consume_reconnect_counter.labels(jobname=jobname, queue=queue).inc(amount=amount)
