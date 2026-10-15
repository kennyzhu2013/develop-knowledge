import json
import logging
import socket
import threading

import pika
from pika.exceptions import AMQPError

logger = logging.getLogger("all")

_PUBLISH_MAX_ATTEMPTS = 3
# 建连/读写 socket 超时；broker 因内存/磁盘告警 blocked 时，超时后抛 ConnectionBlockedTimeout
_MQ_SOCKET_TIMEOUT = 10
_MQ_BLOCKED_CONNECTION_TIMEOUT = 15
_MQ_CONNECTION_ATTEMPTS = 3
_MQ_RETRY_DELAY = 2
_PUBLISH_RETRY_ERRORS = (AMQPError, OSError, socket.timeout)


def loads_mq_json(body):
    """解析 MQ 消息体；非法 JSON 或非 object 抛 ValueError。"""
    try:
        data = json.loads(body)
    except (TypeError, ValueError) as err:
        raise ValueError("invalid mq json: %s" % err) from err
    if not isinstance(data, dict):
        raise ValueError("mq body is not a json object")
    return data


def safe_ack(ch, method):
    try:
        ch.basic_ack(delivery_tag=method.delivery_tag)
    except Exception:
        logger.exception("mq ack failed tag=%s", getattr(method, "delivery_tag", None))


class MsgQueue:
    _connection = None
    _conn_params = None

    def __init__(self, host, port, username, pwd, vhost):
        auth = pika.PlainCredentials(username=username, password=pwd)
        # heartbeat 保活；socket/blocked 超时避免 publish 在 HTTP/消费线程无限挂起
        self._conn_params = pika.ConnectionParameters(
            host=host,
            port=port,
            virtual_host=vhost,
            credentials=auth,
            heartbeat=60,
            socket_timeout=_MQ_SOCKET_TIMEOUT,
            blocked_connection_timeout=_MQ_BLOCKED_CONNECTION_TIMEOUT,
            connection_attempts=_MQ_CONNECTION_ATTEMPTS,
            retry_delay=_MQ_RETRY_DELAY,
        )
        self._publish_local = threading.local()

    def init_connection(self):
        """
        初始化连接
        :return:
        """
        if not self._connection:
            self._connection = pika.BlockingConnection(self._conn_params)

        else:
            logger.info("connection is not None, please close connection first")

    def close_connection(self):
        """
        关闭连接
        :return:
        """
        if self._connection and self._connection.is_open:
            self._connection.close()
            self._connection = None
        else:
            logger.info("connection was closed already")
        self._close_publish_conn()

    def _close_publish_conn(self):
        channel = getattr(self._publish_local, "channel", None)
        connection = getattr(self._publish_local, "connection", None)
        self._publish_local.channel = None
        self._publish_local.connection = None
        if channel is not None:
            try:
                if channel.is_open:
                    channel.close()
            except Exception:
                pass
        if connection is not None:
            try:
                if connection.is_open:
                    connection.close()
            except Exception:
                pass

    def _get_publish_channel(self):
        connection = getattr(self._publish_local, "connection", None)
        channel = getattr(self._publish_local, "channel", None)
        if (
            connection is not None and getattr(connection, "is_open", False)
            and channel is not None and getattr(channel, "is_open", False)
        ):
            return channel
        self._close_publish_conn()
        connection = pika.BlockingConnection(self._conn_params)
        channel = connection.channel()
        channel.confirm_delivery()
        self._publish_local.connection = connection
        self._publish_local.channel = channel
        return channel

    def _publish_body(self, exchange, routing_key, body, properties=None):
        last_err = None
        for attempt in range(1, _PUBLISH_MAX_ATTEMPTS + 1):
            try:
                channel = self._get_publish_channel()
                channel.basic_publish(
                    exchange=exchange,
                    routing_key=routing_key,
                    body=body,
                    properties=properties,
                )
                return
            except _PUBLISH_RETRY_ERRORS as err:
                last_err = err
                logger.warning(
                    "mq publish failed attempt=%s/%s exchange=%s routing_key=%s: %s",
                    attempt, _PUBLISH_MAX_ATTEMPTS, exchange, routing_key, err,
                )
                self._close_publish_conn()
        raise last_err

    def publish_message(self, exchange: str, routing_key: str, msg: str, properties=None):
        """
        发送单条消息
        :param exchange:
        :param routing_key:
        :param msg:
        :param properties:
        :return:
        """
        self._publish_body(exchange, routing_key, msg.encode("utf-8"), properties)

    def publish_message_list(self, exchange: str, routing_key: str, msg_list: list, properties=None):
        """
        发送多条消息
        :param exchange:
        :param routing_key:
        :param msg_list:
        :param properties:
        :return:
        """
        for msg in msg_list:
            self._publish_body(exchange, routing_key, msg.encode("utf-8"), properties)

    def publish_json_list(self, exchange: str, routing_key: str, msg_list: list, properties=None):
        """
        发送多条消息
        :param exchange:
        :param routing_key:
        :param msg_list:
        :param properties:
        :return:
        """
        for msg in msg_list:
            self._publish_body(exchange, routing_key, json.dumps(msg).encode("utf-8"), properties)

    def request_stop_consuming(self):
        """
        从其他线程调用：打断当前阻塞的 channel.start_consuming()
        """
        conn = self._consume_connection
        if not conn or not getattr(conn, "is_open", False):
            return

        def _stop():
            try:
                ch = self._consume_channel
                if ch and getattr(ch, "is_open", False):
                    ch.stop_consuming()
            except Exception:
                pass

        try:
            conn.add_callback_threadsafe(_stop)
        except Exception:
            pass

    def consume_queue(self, queue_list: list, callback, auto_ack=False, prefetch_count=0, stop_event=None):
        """
        监听一个队列
        :param queue: 队列名
        :param callback:
        :param auto_ack:
        :param prefetch_count:
        :param stop_event: threading.Event；set() 后退出外层循环，并尝试优雅停止 consuming
        :return:
        """
        from apps.prometheus import count_mq_consume_reconnect, set_mq_consumer_alive

        stop_event = stop_event or threading.Event()
        queue_label = ",".join(queue_list)
        thread_name = threading.current_thread().name

        def wrapped_callback(ch, method, properties, body):
            try:
                callback(ch, method, properties, body)
            except Exception:
                logger.exception("mq callback 异常, queues=%s", queue_list)
                if not auto_ack:
                    try:
                        ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
                    except Exception:
                        logger.exception("mq callback nack 失败, queues=%s", queue_list)

        def work():
            self._consume_connection = None
            self._consume_channel = None
            try:
                connection = pika.BlockingConnection(self._conn_params)
                self._consume_connection = connection

                channel = connection.channel()
                self._consume_channel = channel

                channel.basic_qos(prefetch_count=prefetch_count)
                for queue in queue_list:
                    channel.basic_consume(
                        queue=queue,
                        auto_ack=auto_ack,
                        on_message_callback=wrapped_callback,
                    )
                    logger.info("开始监听队列:{}".format(queue))
                channel.start_consuming()
            finally:
                try:
                    if self._consume_channel and self._consume_channel.is_open:
                        self._consume_channel.close()
                except Exception:
                    pass
                try:
                    if self._consume_connection and self._consume_connection.is_open:
                        self._consume_connection.close()
                except Exception:
                    pass
                self._consume_channel = None
                self._consume_connection = None

        set_mq_consumer_alive(queue_label, thread_name, True)
        try:
            while not stop_event.is_set():
                try:
                    work()
                    if stop_event.is_set():
                        break
                except Exception as err:
                    if stop_event.is_set():
                        break
                    logger.exception("mq消费循环异常, queues=%s: %s", queue_list, err)
                    count_mq_consume_reconnect(queue_label)
                    if stop_event.wait(3):
                        break
        finally:
            set_mq_consumer_alive(queue_label, thread_name, False)

        logger.info("consume_queue 退出: {}".format(queue_list))


class MqUtil(threading.Thread):

    def __init__(self, daemon):
        super().__init__(daemon=daemon)
        self._stop_event = threading.Event()
        self.mq = None

    def stop(self) -> None:
        self._stop_event.set()
        if getattr(self, "mq", None):
            self.mq.request_stop_consuming()
