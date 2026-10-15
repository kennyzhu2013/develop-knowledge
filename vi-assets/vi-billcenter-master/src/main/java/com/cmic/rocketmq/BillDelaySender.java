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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class BillDelaySender {

    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;


    public void sendDelayBill(String billId, Map<String, Object> params) {
        try {
            String paramsJson = JSON.toJSONString(params);
            byte[] messageBody = paramsJson.getBytes(StandardCharsets.UTF_8);


            String destination = "TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY";
            org.springframework.messaging.Message<byte[]> message = MessageBuilder
                    .withPayload(messageBody)
                    .setHeader(RocketMQHeaders.KEYS, billId)
                    .build();


            SendReceipt receipt = rocketMQClientTemplate.syncSendDelayMessage(
                    destination,
                    message,
                    Duration.ofSeconds(5)
            );

            log.info("[{}] - Send a bill to RocketMQ: {}  msgID:{}",
                    billId, destination, receipt.getMessageId());

        } catch (Exception e) {
            log.error("[{}] - send bill delay msg fail", billId, e);
        }
    }

}
