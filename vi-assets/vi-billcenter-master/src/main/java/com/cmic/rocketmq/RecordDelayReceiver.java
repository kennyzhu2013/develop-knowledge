package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.metrics.BillMetrics;
import com.cmic.service.RecordService;
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
        consumerGroup = "GID_VI_ONE_BILLCENTER_RECORD_DELAY",
        topic = "TID_VI_ONE_BILLCENTER_RECORD_PUSH_BILLCENTER_DELAY",
        tag = "*"
)
public class RecordDelayReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {

    @Autowired
    private RecordService recordService;

    //@Resource(name = "GetConnectionTimeoutCounter")
    //private Counter getConnectionTimeoutCounter;
    @Autowired
    private BillMetrics billMetrics;

    @Autowired
    private Parameters parameters;

    private PushConsumerBuilder consumerBuilder;

    @Override
    public ConsumeResult consume(MessageView messageView) {

        ByteBuffer byteBuffer = messageView.getBody();
        byte[] body = new byte[byteBuffer.remaining()];
        byteBuffer.get(body);
        String mapjson = new String(body, StandardCharsets.UTF_8);

        Map<String, Object> params = JSON.toJavaObject(JSON.parseObject(mapjson), Map.class);
        Object callIdObj = params.get("callId");
        String callIdStr = (callIdObj != null) ? callIdObj.toString() : "unknown";
        log.info("[{}] RecordDelayReceiver mq msgID: {}", callIdStr, messageView.getMessageId());


        try {
            int type = Integer.valueOf(params.get("code").toString());
            if (type == 0) {
                recordService.getRecording(params);
            }
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                //getConnectionTimeoutCounter.inc();
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("消息 ID: {} 处理失败", messageView.getMessageId(), e);
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
