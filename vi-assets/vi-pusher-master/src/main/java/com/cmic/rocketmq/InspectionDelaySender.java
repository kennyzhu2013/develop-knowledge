package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.pojo.entity.Bill;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class InspectionDelaySender {

    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    public void sendInspectionResultDelay(Bill bill, Map<String, Object> params, Integer second) {
        try {
            String billid = String.valueOf(bill.getId());
            log.info("[{}] - inspection result push send record to delay queue", billid);

            String paramsJson = JSON.toJSONString(params);
            byte[] messageBody = paramsJson.getBytes(StandardCharsets.UTF_8);

            org.springframework.messaging.Message<byte[]> message = MessageBuilder.withPayload(messageBody).setHeader(RocketMQHeaders.KEYS, billid).build();

            rocketMQClientTemplate.syncSendDelayMessage(
                    "TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY",
                    message,
                    Duration.ofSeconds(second)
            );

        } catch (Exception e) {
            log.error("[" + bill.getId() + "] -", e);
        }
    }

}
