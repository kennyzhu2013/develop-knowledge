package com.cmic.config.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessSender {
    @Value("${service.instance.id}")
    private String instanceId;
    @Autowired
    private RabbitTemplate southRabbitTemplate;
    public static String PROCESS_UPDATE_EXCHANGE = "scheduler.process.update.exchange";

    public void broadcastProcessUpdateMessage(String message) {
        southRabbitTemplate.convertAndSend(PROCESS_UPDATE_EXCHANGE, "", message);
    }


    @Bean
    public FanoutExchange processUpdateExchange() {
        return new FanoutExchange(PROCESS_UPDATE_EXCHANGE);
    }

    // 定义队列，队列名从配置文件中读取
    @Bean
    public Queue processUpdateQueue() {
        String queueName = getDynamicQueueName();
        return new Queue(queueName, true);  // 持久化队列
    }

    // 将队列绑定到交换机
    @Bean
    public Binding bindingProcessUpdate(Queue processUpdateQueue, FanoutExchange processUpdateExchange) {
        return BindingBuilder.bind(processUpdateQueue).to(processUpdateExchange);
    }

    public String getDynamicQueueName() {
        return "scheduler.process.update." + instanceId;
    }
}
