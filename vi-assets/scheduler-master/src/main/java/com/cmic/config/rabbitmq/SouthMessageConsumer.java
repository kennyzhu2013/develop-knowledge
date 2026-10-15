package com.cmic.config.rabbitmq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SouthMessageConsumer extends MessageConsumer{

    @RabbitListener(
            queues = {
//                    MQBeanConfig.ASR_RESPONSE_QUEUE,
                    MQBeanConfig.JT_ASR_RESPONSE_QUEUE,
//                    MQBeanConfig.KEYWORD_RESPONSE_QUEUE,
//                    MQBeanConfig.SCENARIO_RESPONSE_QUEUE,
//                    MQBeanConfig.VFD_RESPONSE_QUEUE,
//                    MQBeanConfig.VPR_RESPONSE_QUEUE,
//                    MQBeanConfig.DSZ_RESPONSE_QUEUE,
                    MQBeanConfig.DIFY_RESPONSE_QUEUE,
//                    MQBeanConfig.RONGTONG_RESPONSE_QUEUE,
//                    MQBeanConfig.DY_HTTP_RESPONSE_QUEUE,
                    MQBeanConfig.COLLECTION_RESPONSE_QUEUE,
//                    MQBeanConfig.VP_RESPONSE_QUEUE,
//                    MQBeanConfig.IDENTITY_RESPONSE_QUEUE,
//                    MQBeanConfig.EMOTION_RESPONSE_QUEUE,
//                    MQBeanConfig.AIGS_RESPONSE_QUEUE,
//                    MQBeanConfig.DEEPSEEK_RESPONSE_QUEUE,
//                    MQBeanConfig.QWEN_RESPONSE_QUEUE,
//                    MQBeanConfig.RT_FRAUD_REVIEW_RESPONSE_QUEUE,
//                    MQBeanConfig.MARKET_CALL_RESPONSE_QUEUE

//                    MQBeanConfig.BA_RESPONSE_QUEUE,
//                    MQBeanConfig.SCRIPT_RESPONSE_QUEUE,
//                    MQBeanConfig.HUAWEI_RESPONSE_QUEUE,
//                    MQBeanConfig.TEXT_SIMILARITY_RESPONSE_QUEUE,
            },
            containerFactory = "southFactory"
    )
    public void consumerSouth(Message message, Channel channel){
        String queueName = message.getMessageProperties().getConsumerQueue();
        consumer(message, channel, queueName);
    }

    @RabbitListener(queues = MQBeanConfig.ASR_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthAsr(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.ASR_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DSZ_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthDSZ(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DSZ_RESPONSE_QUEUE);
    }
//
//    @RabbitListener(queues = MQBeanConfig.JT_ASR_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthJTAsr(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.JT_ASR_RESPONSE_QUEUE);
//    }
//
//    @RabbitListener(queues = MQBeanConfig.BA_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthBA(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.BA_RESPONSE_QUEUE);
//    }
//
//    @RabbitListener(queues = MQBeanConfig.SCRIPT_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthScript(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.SCRIPT_RESPONSE_QUEUE);
//    }

    @RabbitListener(queues = MQBeanConfig.KEYWORD_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthKeyword(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.KEYWORD_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.SCENARIO_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthScenario(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.SCENARIO_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.VFD_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthVFD(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.VFD_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.VPR_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthVPR(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.VPR_RESPONSE_QUEUE);
    }
//
//    @RabbitListener(queues = MQBeanConfig.DIFY_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthDIFY(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.DIFY_RESPONSE_QUEUE);
//    }
//
//    @RabbitListener(queues = MQBeanConfig.HUAWEI_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthHuawei(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.HUAWEI_RESPONSE_QUEUE);
//    }

    @RabbitListener(queues = MQBeanConfig.RONGTONG_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthRongtong(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.RONGTONG_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DY_HTTP_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthDyHttp(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DY_HTTP_RESPONSE_QUEUE);
    }
//
//    @RabbitListener(queues = MQBeanConfig.COLLECTION_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthCollection(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.COLLECTION_RESPONSE_QUEUE);
//    }
//
//    @RabbitListener(queues = MQBeanConfig.TEXT_SIMILARITY_RESPONSE_QUEUE, containerFactory = "southFactory")
//    public void consumerSouthTextSml(Message message, Channel channel){
//        consumer(message, channel, MQBeanConfig.TEXT_SIMILARITY_RESPONSE_QUEUE);
//    }

    @RabbitListener(queues = MQBeanConfig.VP_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthVP(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.VP_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.IDENTITY_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthIdentity(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.IDENTITY_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.EMOTION_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthEmotion(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.EMOTION_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.AIGS_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthAIGS(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.AIGS_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DEEPSEEK_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthDeepSeek(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DEEPSEEK_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.QWEN_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthQwen(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.QWEN_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.RT_FRAUD_REVIEW_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthRTFraudReview(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.RT_FRAUD_REVIEW_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.MARKET_CALL_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthMarketCall(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.MARKET_CALL_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.DEAL_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthDeal(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.DEAL_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.RAG_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthRAG(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.RAG_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.FAST_ASR_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthFastAsr(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.FAST_ASR_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.BGE_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthBGE(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.BGE_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.CHONGQING_ANTI_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthChongqingAnti(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.CHONGQING_ANTI_RESPONSE_QUEUE);
    }

    @RabbitListener(queues = MQBeanConfig.CQ_AGENT_RESPONSE_QUEUE, containerFactory = "southFactory")
    public void consumerSouthCQAgent(Message message, Channel channel){
        consumer(message, channel, MQBeanConfig.CQ_AGENT_RESPONSE_QUEUE);
    }

    @Override
    public int getLocation() {
        return 1;
    }
}
