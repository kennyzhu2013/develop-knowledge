package com.cmic.rocketmq;


import com.cmic.pojo.entity.Bill;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;


@Component
@Slf4j
public class RecordDelaySender {

    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    public void sendBillDelay(Bill bill, Integer second) {
        try {
            String billid = String.valueOf(bill.getId());
            log.info("[{}] - record push send record to delay queue", billid);

            byte[] bytes = billid.getBytes(StandardCharsets.UTF_8);
            org.springframework.messaging.Message<byte[]> message = MessageBuilder.withPayload(bytes).setHeader(RocketMQHeaders.KEYS, billid).build();

            rocketMQClientTemplate.syncSendDelayMessage(
                    "TID_VI_PUSHER_RECORD_PUSH_PUSHER_DELAY",
                    message,
                    Duration.ofSeconds(second)
            );

        } catch (Exception e) {
            log.error("[" + bill.getId() + "] -", e);
        }
    }
}
