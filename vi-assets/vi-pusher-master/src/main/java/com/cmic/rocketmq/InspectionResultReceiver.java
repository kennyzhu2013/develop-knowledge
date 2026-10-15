package com.cmic.rocketmq;


import com.alibaba.fastjson2.JSON;
import com.cmic.inspectionpusher.CommonInspectionPusher;
import com.cmic.utils.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
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
@ConditionalOnProperty(name = "pusher.inspectionpusher", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(consumerGroup = "GID_VI_ONE_PUSHER_INSPECTION_PUSH", topic = "TID_VI_ONE_BILLCENTER_INSPECTION_PUSH_PUSHER_NORMAL", tag = "*")
public class InspectionResultReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {


    @Autowired
    private Parameters parameters;
    @Autowired
    private CommonInspectionPusher pusher;

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
            log.error("[{}] - parse inspection params: {}, error: {}, ", callId, paramsjson, e);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("prepareStart,consumerBuilder={}, bill consumptionThreadCount={}", consumerBuilder, parameters.billConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.inspectionConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }


}


