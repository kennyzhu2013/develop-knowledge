package com.cmic.config.rabbitmq;

import io.prometheus.client.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//@Component
@Slf4j
public class ReturnCallBack implements RabbitTemplate.ReturnsCallback{
//    @Autowired
//    private Counter send_mq_exchange_error;
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        //TODO
//        send_mq_exchange_error.inc();
//        log.info("消息发送队列失败：｛｝", returned);
    }
}
