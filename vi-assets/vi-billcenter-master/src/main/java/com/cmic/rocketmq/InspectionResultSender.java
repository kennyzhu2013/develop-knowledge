package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.metrics.BillMetrics;
import com.cmic.pojo.entity.Bill;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class InspectionResultSender {
    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    @Autowired
    private BillMetrics billMetrics;



    public void send(Map<String, Object> params) {
        String taskId = params.get("taskId") == null ? "" : params.get("taskId").toString();
        String callIdStr = params.get("listId") == null ? "" : params.get("listId").toString();
        log.info("[{}] - start send a inspection result to mq: {}", callIdStr, params);

        String paramsJson = JSON.toJSONString(params);
        byte[] messageBody = paramsJson.getBytes(StandardCharsets.UTF_8);

        String destination = "TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL";
        org.springframework.messaging.Message<byte[]> message = MessageBuilder
                .withPayload(messageBody)
                .setHeader(RocketMQHeaders.KEYS, callIdStr)
                .build();

        SendReceipt receipt = rocketMQClientTemplate.syncSendNormalMessage(destination, message);

        log.info("[{}] - Send a voice inspection result to RocketMQ, taskId: {}  msgID:{}",
                callIdStr, taskId, receipt.getMessageId());

        // billCreateCounter.inc();
        billMetrics.inspectionResultSendCounterInc();

    }
}