package com.cmic.config.metrics;

import cn.hutool.json.JSONObject;
import com.cmic.pojo.dto.ErrorCode;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

@Component
public class CommonMetrics {

    @Resource
    private Counter model_send_count;
    @Resource
    private Counter model_receive_count;
    @Resource
    private Counter model_receive_status_count;
    @Resource
    private Counter model_receive_code_count;
    @Resource
    private Histogram model_receive_cost_histogram;
    @Resource
    private Counter model_receive_timeout_count;
    @Resource
    private Counter model_abnormal_count;

    @Resource
    private Counter task_timeout_10_minutes_count;
    @Resource
    private Counter task_timeout_6_hours_count;
    @Resource
    private Counter task_timeout_12_hours_count;
    @Resource
    private Counter task_timeout_24_hours_count;

    @Resource
    private Counter subtask_callback_result_count;

    public void sendModel(String model, String processId, Integer priority) {
        model_send_count.labels(model, processId, String.valueOf(priority)).inc();
    }

    public void receiveModel(String model, String processId, String code, Long startTime, Long endTime) {
        model_receive_count.labels(model, processId).inc();
        if (ErrorCode.SUCCESS.equals(code)) {
            model_receive_status_count.labels(model, "success").inc();
        } else {
            model_receive_status_count.labels(model, "failure").inc();
        }

        model_receive_code_count.labels(model, code).inc();

        double costSeconds = endTime - startTime / 1000.0;
        model_receive_cost_histogram.labels(model).observe(costSeconds);
    }

    public void modelReceiveTimeoutInc(String model, String processId, Integer priority) {
        model_receive_timeout_count.labels(model, processId, String.valueOf(priority)).inc();
    }

    public void modelAbnormalInc(String model, String processId, String abnormal) {
        model_abnormal_count.labels(model, processId, abnormal).inc();
    }

    public void taskTimeout10MinutesInc(String processId, Integer count) {
        task_timeout_10_minutes_count.labels(processId).inc(count);
    }

    public void taskTimeout6HoursInc(String processId, Integer count) {
        task_timeout_6_hours_count.labels(processId).inc(count);
    }

    public void taskTimeout12HoursInc(String processId, Integer count) {
        task_timeout_12_hours_count.labels(processId).inc(count);
    }

    public void taskTimeout24HoursInc(String processId, Integer count) {
        task_timeout_24_hours_count.labels(processId).inc(count);
    }

    public void statJobFinsh(JSONObject jobResult) {
        String status = safeLabelValue(jobResult.getStr("status"));
        String qcResult = safeLabelValue(jobResult.getStr("qcResult"));
        String tag = safeLabelValue(jobResult.getStr("tag"));
        subtask_callback_result_count.labels(status, qcResult, tag).inc();
    }

    private String safeLabelValue(String val) {
        return val == null ? "null" : val;
    }
}
