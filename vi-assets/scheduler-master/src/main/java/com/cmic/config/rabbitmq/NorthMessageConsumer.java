package com.cmic.config.rabbitmq;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

//@Slf4j
//@Component
public class NorthMessageConsumer extends MessageConsumer{
    @RabbitListener(queues = MQBeanConfig.ASR_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthAsr(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.ASR_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.JT_ASR_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthJTAsr(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.JT_ASR_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.BA_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthBA(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.BA_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.SCRIPT_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthScript(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.SCRIPT_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.KEYWORD_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthKeyword(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.KEYWORD_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.SCENARIO_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthScenario(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.SCENARIO_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.VFD_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthVFD(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.VFD_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.VPR_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthVPR(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.VPR_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DSZ_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthDSZ(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DSZ_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DIFY_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthDIFY(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DIFY_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.HUAWEI_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthHuawei(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.HUAWEI_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.RONGTONG_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthRongtong(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.RONGTONG_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DY_HTTP_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthDyHttp(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DY_HTTP_RESPONSE_QUEUE);
    }
    @RabbitListener(queues = MQBeanConfig.COLLECTION_RESPONSE_QUEUE, containerFactory = "northFactory")
    public void consumerNorthCollection(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.COLLECTION_RESPONSE_QUEUE);
    }

    @Override
    public int getLocation() {
        return 2;
    }
}
