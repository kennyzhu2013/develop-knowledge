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
        consumerGroup = "GID_VI_ONE_BILLCENTER_RECORD",
        topic = "TID_VI_ONE_MERGE_MSG_BILL_NORMAL",
        tag = "*"
)
public class RecordReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {

    @Autowired
    private RecordService recordService;

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
        String paramsJson = new String(body, StandardCharsets.UTF_8);
        Map<String, Object> params = JSON.toJavaObject(JSON.parseObject(paramsJson), Map.class);

        Object callIdObj = params.get("callId");
        String callId = (callIdObj != null) ? callIdObj.toString() : "unknown";

        log.info("[{}] RecordReceiver mq msgID: {}, \nmessage: {}", callId, messageView.getMessageId(), paramsJson);

        try {
            int type = Integer.valueOf(params.get("code").toString());
            if (type == 0) {
                recordService.getRecording(params);
            } else if (type == 1) {
                log.error("[{}] RecordReceiver mq msgID:{} ,merge code =1 ,error", callId, messageView.getMessageId());
                recordService.getError(params);
            }
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("[{}] RecordReceiver handle record msg fail msgID: {}", callId, messageView.getMessageId(), e);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("billCenter prepareStart,consumerBuilder={},  record consumptionThreadCount={}", consumerBuilder, parameters.recordConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.recordConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }


}
