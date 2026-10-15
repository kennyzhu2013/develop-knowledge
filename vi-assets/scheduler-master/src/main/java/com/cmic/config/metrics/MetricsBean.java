package com.cmic.config.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MetricsBean {

    @Autowired
    private CollectorRegistry collectorRegistry;

    @Bean(name = "task_create_count")
    public Counter task_create_count() {
        return Counter.build().name("task_create_count").help("主任务创建次数").register(collectorRegistry);
    }

    @Bean(name = "task_create_latency_seconds")
    public Histogram task_create_latency_seconds() {
        return Histogram.build().name("task_create_latency_seconds").help("主任务创建耗时").buckets(.1, .2, .4, .8, 1.6, 3.2, 6.4, 12.8).register(collectorRegistry);
    }

    @Bean(name = "task_callback_count")
    public Counter task_callback_count() {
        return Counter.build().name("task_callback_count").help("主任务回调次数").register(collectorRegistry);
    }

    @Bean(name = "subtask_callback_count")
    public Counter subtask_callback_count() {
        return Counter.build().name("subtask_callback_count").help("子任务回调次数").register(collectorRegistry);
    }

    @Bean(name = "subtask_callback_result_count")
    public Counter subtask_callback_result_count() {
        return Counter.build().name("subtask_callback_result_count").help("子任务回调结果").labelNames("status", "qcResult", "tag").register(collectorRegistry);
    }

    @Bean(name = "task_callback_latency_seconds")
    public Histogram task_callback_latency_seconds() {
        return Histogram.build().name("task_callback_latency_seconds").help("主任务回调耗时").register(collectorRegistry);
    }

    @Bean(name = "subtask_callback_latency_seconds")
    public Histogram subtask_callback_latency_seconds() {
        return Histogram.build().name("subtask_callback_latency_seconds").help("子任务回调耗时").register(collectorRegistry);
    }

    @Bean(name = "job_send_error")
    public Counter jobSendErrorCount() {
        return Counter.build().name("job_send_error").help("子任务发送 MQ 失败数量").register(collectorRegistry);
    }

    @Bean(name = "send_mq_exchange_error")
    public Counter sendMqExchangeErrorCount() {
        return Counter.build().name("send_mq_exchange_error").help("发送到MQ exchange失败").register(collectorRegistry);
    }

    @Bean(name = "send_mq_queue_error")
    public Counter sendMqQueueErrorCount() {
        return Counter.build().name("send_mq_queue_error").help("发送到MQ queue失败").register(collectorRegistry);
    }

    @Bean(name = "model_send_count")
    public Counter modelSendCount() {
        return Counter.build().name("model_send_count").help("发送到模型数量").labelNames("model", "process", "priority").register(collectorRegistry);
    }

    @Bean(name = "model_receive_count")
    public Counter modelReceiveCount() {
        return Counter.build().name("model_receive_count").help("接收到模型数量").labelNames("model", "process").register(collectorRegistry);
    }

    @Bean(name = "model_receive_status_count")
    public Counter modelReceiveStatusCount() {
        return Counter.build().name("model_receive_status_count").help("接收到模型成功/失败数量").labelNames("model", "status").register(collectorRegistry);
    }

    @Bean(name = "model_receive_code_count")
    public Counter modelReceiveCodeCount() {
        return Counter.build().name("model_receive_code_count").help("接收到模型响应码数量").labelNames("model", "code").register(collectorRegistry);
    }

    @Bean(name = "model_receive_cost_histogram")
    public Histogram modelReceiveCostHistogram() {
        return Histogram.build().name("model_receive_cost_histogram").help("接收到模型响应耗时").labelNames("model")
                // 30s 1min 2min 5min 10min 30min 1h 6h 12h
                .buckets(30, 60, 120, 300, 600, 1800, 3600, 21600, 43200)
                .register(collectorRegistry);
    }

    @Bean(name = "model_receive_timeout_count")
    public Counter modelReceiveTimeoutCount() {
        return Counter.build().name("model_receive_timeout_count").help("模型接收超时数量").labelNames("model", "process", "priority").register(collectorRegistry);
    }

    @Bean(name = "model_abnormal_count")
    public Counter modelAbnormalCount() {
        return Counter.build().name("model_abnormal_count").help("模型数据异常情况").labelNames("model", "process", "abnormal").register(collectorRegistry);
    }

    @Bean(name = "task_timeout_10_minutes_count")
    public Counter taskTimeout10MinutesCount() {
        return Counter.build().name("task_timeout_10_minutes_count").help("任务超时10分钟数量").labelNames("process").register(collectorRegistry);
    }

    @Bean(name = "task_timeout_6_hours_count")
    public Counter taskTimeout6HoursCount() {
        return Counter.build().name("task_timeout_6_hours_count").help("任务超时6小时数量").labelNames("process").register(collectorRegistry);
    }

    @Bean(name = "task_timeout_12_hours_count")
    public Counter taskTimeout12HoursCount() {
        return Counter.build().name("task_timeout_12_hours_count").help("任务超时12小时数量").labelNames("process").register(collectorRegistry);
    }

    @Bean(name = "task_timeout_24_hours_count")
    public Counter taskTimeout24HoursCount() {
        return Counter.build().name("task_timeout_24_hours_count").help("任务超时24小时数量").labelNames("process").register(collectorRegistry);
    }

    @Bean(name = "database_error_count")
    public Counter databaseErrorCount() {
        return Counter.build().name("database_error_count").help("数据库请求失败").labelNames("dbName", "operation", "exception").register(collectorRegistry);
    }

    @Bean(name = "redis_error_count")
    public Counter redisErrorCount() {
        return Counter.build().name("redis_error_count").help("Redis请求失败").labelNames("operation", "exception").register(collectorRegistry);
    }

    @Bean(name = "mongodb_error_count")
    public Counter mongodbErrorCount() {
        return Counter.build().name("mongodb_error_count").help("MongoDB请求失败").labelNames("operation", "exception").register(collectorRegistry);
    }


}
