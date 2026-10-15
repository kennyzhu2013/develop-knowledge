package com.cmic.config.rabbitmq;

import cn.hutool.core.util.StrUtil;
import com.cmic.config.ApplicationProperties;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ai.IMqService;
import com.cmic.service.ai.MqServiceFactory;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import com.cmic.util.TraceIdUtil;
import com.google.common.util.concurrent.RateLimiter;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
public class MessageConsumer {
    protected int location;

    @Autowired
    private MqServiceFactory serviceFactory;
    @Autowired
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private ApplicationProperties config;

    public void consumer(Message message, Channel channel, String queueName){
        RateLimiter rateLimiter = MQConsumerRateLimiter.getRateLimiter(queueName, config.getRateLimiter().getRate());
        rateLimiter.acquire();

        MessageProperties properties = message.getMessageProperties();
        String jobId = "";
        try{
            BaseResponse<?> response = JacksonUtil.string2Obj(new String(message.getBody()), BaseResponse.class);
            Map<String, Object> responseData = (Map) response.getData();
            jobId = (String) responseData.get("jobId");
            if(!StringUtils.hasLength(jobId)){
                log.error("consumer {} response is invalid, case jobId is null", queueName);
                channel.basicAck(properties.getDeliveryTag(), true);
                return;
            }
            jobId = ModelUtil.getOriginJobId(jobId);
            SubTask subTask = subTaskService.getSubTaskByJobId(jobId);
            Task task = taskService.getTaskById(subTask.getTaskId());
            TraceIdUtil.setTraceId(StrUtil.join("-",task.getTransactionId(),subTask.getId()));
            AIModelEnum modelEnum = serviceFactory.getEnumByQueueName(queueName, response);
            if(subTask.getCallback() == 1){
                log.info("Consumer repeat already callback, jobId:{}, queue:{}", jobId, queueName);
                channel.basicAck(properties.getDeliveryTag(), true);
                subTaskService.timeoutReceiveModel(task, subTask, modelEnum.getName());
                return;
            }
            if (subTask.getResponseResults().get(modelEnum.getStatus()) != null) {
                log.info("Consumer repeat status updated, jobId:{}, queue:{}", jobId, queueName);
                channel.basicAck(properties.getDeliveryTag(), true);
                return;
            }

            IMqService mqService = serviceFactory.mqService(queueName);
            mqService.handlerMessage(task, subTask, response);
            channel.basicAck(properties.getDeliveryTag(), true);
        }catch (Exception e){
            log.error("jobId:{}, consumer {} queue", jobId, queueName, e);
            try {
                channel.basicNack(properties.getDeliveryTag(), false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

        }finally {
            TraceIdUtil.clear();
        }
    }

    public int getLocation(){
        return location;
    }
}
