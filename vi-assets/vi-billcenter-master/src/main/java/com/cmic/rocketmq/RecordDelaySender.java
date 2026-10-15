package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
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
public class RecordDelaySender {
    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    private static final Duration DELAY_DURATION = Duration.ofSeconds(60);

    private static final String RECORD_DELAY_TOPIC = "TID_VI_ONE_BILLCENTER_RECORD_PUSH_BILLCENTER_DELAY";

    public void sendRecordDelay(Map<String, Object> recordParams) {
        Object callIdObj = recordParams.get("callId");
        String callIdStr = (callIdObj != null) ? callIdObj.toString() : "unknown";

        try {
            String recordJson = JSON.toJSONString(recordParams);
            byte[] messageBody = recordJson.getBytes(StandardCharsets.UTF_8);

            org.springframework.messaging.Message<byte[]> message = MessageBuilder
                    .withPayload(messageBody)
                    .setHeader(RocketMQHeaders.KEYS, callIdStr)
                    .build();

            SendReceipt receipt = rocketMQClientTemplate.syncSendDelayMessage(
                    RECORD_DELAY_TOPIC,
                    message,
                    DELAY_DURATION
            );

            log.info("[{}] send record delay msg success, msgID: {}, delay: {}s",
                    callIdStr, receipt.getMessageId(), DELAY_DURATION.toSeconds());

        } catch (Exception e) {
            log.error("[{}] - send record delay msg fail", callIdStr, e);
        }
    }
}
