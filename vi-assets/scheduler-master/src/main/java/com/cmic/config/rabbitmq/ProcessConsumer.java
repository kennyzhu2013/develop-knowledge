package com.cmic.config.rabbitmq;

import com.cmic.pojo.entity.Process;
import com.cmic.service.ProcessService;
import com.cmic.service.XmlEventLoaderService;
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
public class ProcessConsumer {
    @Autowired
    private ProcessService processService;
    @Autowired
    private XmlEventLoaderService xmlEventLoaderService;

    @RabbitListener(queues = "#{processSender.getDynamicQueueName()}", containerFactory = "southFactory")
    public void consumerProcessUpdate(Message message, Channel channel){
        MessageProperties properties = message.getMessageProperties();
        try{
            String processId =  new String(message.getBody());
            processId = processId.replaceAll("^\"|\"$", ""); // 去掉首尾双引号
            Process process = processService.findByProcessId(processId);
            if(process == null){
                log.info("ProcessId:{}, is null in db", processId);
                return;
            }
            processService.getProcessMap().put(processId, process);
            xmlEventLoaderService.reloadEventByProcessId(process);
            log.info("ProcessId:{}, reload process map", processId, processService.getProcessMap().get(processId));
            channel.basicAck(properties.getDeliveryTag(), true);
        }catch (Exception e){
            log.error("consumer update process queue message:"+new String(message.getBody())+" error:", e);
            //TODO 处理异常消息
            try {
                channel.basicNack(properties.getDeliveryTag(), false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

}
