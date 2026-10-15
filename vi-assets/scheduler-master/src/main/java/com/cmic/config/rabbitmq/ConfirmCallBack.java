package com.cmic.config.rabbitmq;

import io.prometheus.client.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//@Component
@Slf4j
public class ConfirmCallBack implements RabbitTemplate.ConfirmCallback {
//    @Autowired
//    private Counter send_mq_queue_error;
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        //TODO 根据业务调整
        if(!ack){
//            send_mq_queue_error.inc();
//            log.info("Message failed:{}", cause);
        }
    }
}
