package com.cmic.rocketmq;


import com.alibaba.fastjson2.JSON;
import com.cmic.inspectionpusher.CommonInspectionPusher;
import com.cmic.metrics.PusherMetrics;
import com.cmic.utils.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumerBuilder;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.apache.rocketmq.client.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "GID_VI_ONE_PUSHER_INSPECTION_DELAY",
        topic = "TID_VI_ONE_PUSHER_INSPECTION_PUSH_PUSHER_DELAY",
        tag = "*"
)
public class InspectionDelayReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {

    @Autowired
    private CommonInspectionPusher pusher;

    @Autowired
    private Parameters parameters;

    @Autowired
    private PusherMetrics pusherMetrics;

    private PushConsumerBuilder consumerBuilder;

    @Override
    public ConsumeResult consume(MessageView messageView) {

        // 从 MessageView 中获取 ByteBuffer
        ByteBuffer byteBuffer = messageView.getBody();

        // 转换 ByteBuffer 为字节数组
        byte[] body = new byte[byteBuffer.remaining()];
        byteBuffer.get(body);
        String paramsjson = new String(body, StandardCharsets.UTF_8);
        String callId = "";
        try {
            Map<String, Object> params = JSON.parseObject(paramsjson, Map.class);
            callId = params.get("listId").toString();
            log.info("[{}] - receive bill params: {}", callId, paramsjson);
            pusher.pushInspection(params);
        } catch (Exception e) {
            log.error("[{}] - Delayreceiver parse inspection params: {}, error: {} ", callId, paramsjson, e);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("billCenter prepareStart,consumerBuilder={},  delay record consumptionThreadCount={}", consumerBuilder, parameters.delayRecordConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.delayRecordConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }

}
