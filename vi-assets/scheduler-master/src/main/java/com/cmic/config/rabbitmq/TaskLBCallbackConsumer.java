package com.cmic.config.rabbitmq;

import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.service.task.TaskService;
import com.cmic.util.JacksonUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Slf4j
@Component
public class TaskLBCallbackConsumer {
    @Autowired
    private TaskService taskService;
    private final DynamicBeanRegistry dynamicBeanRegistry;

    public TaskLBCallbackConsumer(DynamicBeanRegistry dynamicBeanRegistry) {
        this.dynamicBeanRegistry = dynamicBeanRegistry;
    }

    @PostConstruct
    public void init(){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.TASK_LB_CALLBACK_QUEUE, "taskLBCallbackQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.CALLBACK_TOPIC_EXCHANGE, "taskLBCallbackExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.TASK_LB_CALLBACK_QUEUE, "taskLBCallbackBindingExchange");
    }

    @RabbitListener(queues = MQBeanConfig.TASK_LB_CALLBACK_QUEUE, containerFactory = "southFactory", concurrency="2")
    public void consumerTask(Message message, Channel channel){
        MessageProperties properties = message.getMessageProperties();
        try{
            TaskMqBo taskMqBo = JacksonUtil.string2Obj(new String(message.getBody()), TaskMqBo.class);
            taskService.handlerMqTaskCallback(taskMqBo);
            channel.basicAck(properties.getDeliveryTag(), true);
        }catch (Exception e){
            log.error("consumer task lb callback queue message:"+new String(message.getBody())+" error:", e);
            //TODO 处理异常消息
            try {
                channel.basicNack(properties.getDeliveryTag(), false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
