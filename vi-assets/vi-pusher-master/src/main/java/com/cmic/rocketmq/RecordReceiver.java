package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.recordpusher.CommonRecordPusher;
import com.cmic.utils.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumerBuilder;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.apache.rocketmq.client.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "pusher.recordpusher", havingValue = "true", matchIfMissing = false)
//@RocketMQMessageListener(consumerGroup = "GID_DCC_PUSHER_RECORD_PUSH", topic = "TID_DCC_BILLCENTER_RECORD_PUSH_PUSHER_NORMAL", tag = "*")
public class RecordReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {


    @Autowired
    private CommonRecordPusher pusher;
    @Autowired
    private Parameters parameters;

    private PushConsumerBuilder consumerBuilder;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            // 从 MessageView 中获取 ByteBuffer
            ByteBuffer byteBuffer = messageView.getBody();

            // 转换 ByteBuffer 为字节数组
            byte[] body = new byte[byteBuffer.remaining()];
            byteBuffer.get(body);
            String mapjson = new String(body, StandardCharsets.UTF_8);
            try {
                Map<String, Object> params = JSON.toJavaObject(JSON.parseObject(mapjson), Map.class);
                log.info("[{}] - receive record params: {}", params.get("billId"), mapjson);
                pusher.pushRecording(params);
            } catch (Exception e) {
                log.error("recored receive error: {}, params: {}", e, mapjson);
            }
        } catch (Exception e) {
            log.error("recored process error: {}, messageView: {}", e, messageView);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("prepareStart,consumerBuilder={}, record consumptionThreadCount={}", consumerBuilder, parameters.recordConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.recordConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }

}
