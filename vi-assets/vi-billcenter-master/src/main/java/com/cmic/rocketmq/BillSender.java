package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.pojo.entity.Bill;
import com.cmic.metrics.BillMetrics;
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
public class BillSender {
    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    @Autowired
    private BillMetrics billMetrics;


    public static Map<Integer, String> ExtidQueueGroup = new HashMap<>();


    public void send(Bill bill) {
        String billJson = JSON.toJSONString(bill);
        byte[] messageBody = billJson.getBytes(StandardCharsets.UTF_8);

        String callIdStr = bill.getId();

        String destination = "TID_VI_BILLCENTER_BILL_PUSH_PUSHER_NORMAL";
        org.springframework.messaging.Message<byte[]> message = MessageBuilder
                .withPayload(messageBody)
                .setHeader(RocketMQHeaders.KEYS, callIdStr)
                .build();

        SendReceipt receipt = rocketMQClientTemplate.syncSendNormalMessage(destination, message);

        log.info("[{}] - Send a completed bill to RocketMQ: {}  msgID:{}",
                bill.getId(), destination, receipt.getMessageId());


        // billCreateCounter.inc();
        billMetrics.billCreateCounterInc();

    }
}