package com.cmic.rocketmq;


import com.alibaba.fastjson2.JSON;
import com.cmic.billpusher.CommonBillPusher;
import com.cmic.pojo.entity.Bill;
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


@Slf4j
@Component
@ConditionalOnProperty(name = "pusher.billpusher", havingValue = "true", matchIfMissing = false)
//@RocketMQMessageListener(consumerGroup = "GID_VI_PUSHER_BILL_PUSH", topic = "TID_VI_BILLCENTER_BILL_PUSH_PUSHER_NORMAL", tag = "*")
public class BillReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {


    @Autowired
    private Parameters parameters;
    @Autowired
    private CommonBillPusher pusher;

    private PushConsumerBuilder consumerBuilder;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        // 从 MessageView 中获取 ByteBuffer
        ByteBuffer byteBuffer = messageView.getBody();

        // 转换 ByteBuffer 为字节数组
        byte[] body = new byte[byteBuffer.remaining()];
        byteBuffer.get(body);
        String billjson = new String(body, StandardCharsets.UTF_8);

        try {
            Bill bill = JSON.parseObject(billjson, Bill.class);
            log.info("[{}] - receive bill params: {}", bill.getId(), billjson);
            pusher.pushBill(bill);
        } catch (Exception e) {
            log.error("parse bill params: {}, error: {}, ", billjson, e);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("prepareStart,consumerBuilder={}, bill consumptionThreadCount={}", consumerBuilder, parameters.billConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.billConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }


}


