package com.cmic.config.rabbitmq;

import com.cmic.pojo.dto.TaskRequest;
import com.cmic.service.task.TaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.TraceIdUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TaskConsumer {
    @Autowired
    private TaskService taskService;

    @RabbitListener(queues = MQBeanConfig.TASK_CREATE_QUEUE, containerFactory = "southFactory")
    public void consumerTask(Message message, Channel channel){
        MessageProperties properties = message.getMessageProperties();
        try{
            TaskRequest request = JacksonUtil.string2Obj(new String(message.getBody()), TaskRequest.class);
            TraceIdUtil.setTraceId(String.valueOf(request.getTransactionId()));
            taskService.handlerMqTask(request);
            channel.basicAck(properties.getDeliveryTag(), true);
        }catch (Exception e){
            log.error("consumer task queue message:"+new String(message.getBody())+" error:", e);
            //TODO 处理异常消息
            try {
                channel.basicNack(properties.getDeliveryTag(), false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }finally {
            TraceIdUtil.clear();
        }
    }
}
