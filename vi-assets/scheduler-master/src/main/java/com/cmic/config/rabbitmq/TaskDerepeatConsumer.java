package com.cmic.config.rabbitmq;

import com.cmic.config.FixedSizeLinkedHashMap;
import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.service.MqService;
import com.cmic.util.JacksonUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "masterServer", havingValue = "true", matchIfMissing = false)
public class TaskDerepeatConsumer {
    private Map<String, String> taskMap = Collections.synchronizedMap(new FixedSizeLinkedHashMap<>(1000));
    private final MqService mqService;
    private final DynamicBeanRegistry dynamicBeanRegistry;

    public TaskDerepeatConsumer(DynamicBeanRegistry dynamicBeanRegistry, MqService mqService) {
        this.dynamicBeanRegistry = dynamicBeanRegistry;
        this.mqService = mqService;
    }

    @PostConstruct
    public void init(){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.TASK_CALLBACK_REPEAT_QUEUE, "taskCallbackRepeatQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.CALLBACK_TOPIC_EXCHANGE, "taskCallbackRepeatExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.TASK_CALLBACK_REPEAT_QUEUE, "taskCallbackRepeatBindingExchange");
    }

    @RabbitListener(queues = MQBeanConfig.TASK_CALLBACK_REPEAT_QUEUE, containerFactory = "southFactory")
    public void consumerTask(Message message, Channel channel){
        MessageProperties properties = message.getMessageProperties();
        try{
            TaskMqBo taskMqBo = JacksonUtil.string2Obj(new String(message.getBody()), TaskMqBo.class);
            String taskId = String.valueOf(taskMqBo.getTaskId());
            if(isCallback(taskId)){
                log.info("TaskId:{}, task callback is repeat...", taskId);
                channel.basicAck(properties.getDeliveryTag(), true);
                return;
            }
            int priority = message.getMessageProperties().getPriority();
            mqService.sendToTaskCallback(taskMqBo, priority);
            channel.basicAck(properties.getDeliveryTag(), true);
        }catch (Exception e){
            log.error("consumer task callback repeat queue message:"+new String(message.getBody())+" error:", e);
            //TODO 处理异常消息
            try {
                channel.basicNack(properties.getDeliveryTag(), false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public boolean isCallback(String taskId){
        return taskMap.putIfAbsent(taskId, "1") != null;
    }
}
