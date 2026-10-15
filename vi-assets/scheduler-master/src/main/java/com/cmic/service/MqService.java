package com.cmic.service;

import cn.hutool.json.JSONObject;
import com.cmic.config.rabbitmq.MQBeanConfig;
import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.pojo.dto.ai.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class MqService {
    @Autowired
    private RabbitTemplate southRabbitTemplate;
    @Autowired
    private RabbitTemplate northRabbitTemplate;
    @Autowired
    private Semaphore semaphore;

    public RabbitTemplate getRabbitTemplate(int location){
//        if(location == 1){
//            return southRabbitTemplate;
//        }
//        return northRabbitTemplate;

        return southRabbitTemplate;
    }

    /**
     * 发送到南北 MQ，采用 topic模式发送到 exchange
     * @param location
     * @param request
     * @param priority
     */
    public void sendToASR(int location, ASRRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.ASR_TOPIC_EXCHANGE,
                MQBeanConfig.ASR_QUEUE_ROUTINGKEY);
    }

    public void sendToJTASR(int location, ASRRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.JT_ASR_TOPIC_EXCHANGE,
                MQBeanConfig.JT_ASR_QUEUE_ROUTINGKEY);
    }


    public void sendToBA(int location, BehaviorAnalysisRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.BA_TOPIC_EXCHANGE,
                MQBeanConfig.BA_QUEUE_ROUTINGKEY);
    }

    public void sendToScript(int location, ScriptRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.SCRIPT_TOPIC_EXCHANGE,
                MQBeanConfig.SCRIPT_QUEUE_ROUTINGKEY);
    }

    public void sendToKeyword(int location, TextRequest request, int priority) {
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.KEYWORD_TOPIC_EXCHANGE,
                MQBeanConfig.KEYWORD_QUEUE_ROUTINGKEY);
    }

    public void sendToTfidf(int location, TfidfRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.TFIDF_TOPIC_EXCHANGE,
                MQBeanConfig.TFIDF_QUEUE_ROUTINGKEY);
    }

    public void sendToScenario(int location, ScenarioRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.SCENARIO_TOPIC_EXCHANGE,
                MQBeanConfig.SCENARIO_QUEUE_ROUTINGKEY);
    }

    public void sendToCallback(JSONObject message, int priority){
        this.sendPriorityMessage(1, message, priority,
                MQBeanConfig.CALLBACK_TOPIC_EXCHANGE,
                MQBeanConfig.CALLBACK_QUEUE_ROUTINGKEY);
    }

    public void sendToVFD(int location, VFDRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.VFD_TOPIC_EXCHANGE,
                MQBeanConfig.VFD_QUEUE_ROUTINGKEY);
    }

    public void sendToVPR(int location, VPRRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.VPR_TOPIC_EXCHANGE,
                MQBeanConfig.VPR_QUEUE_ROUTINGKEY);
    }

    public void sendToDSZ(int location, DSZRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.DSZ_TOPIC_EXCHANGE,
                MQBeanConfig.DSZ_QUEUE_ROUTINGKEY);
    }

    public void sendToDIFY(int location, DyHttpRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.DIFY_TOPIC_EXCHANGE,
                MQBeanConfig.DIFY_QUEUE_ROUTINGKEY);
    }

    public void sendToHuawei(int location, HuaweiRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.HUAWEI_TOPIC_EXCHANGE,
                MQBeanConfig.HUAWEI_QUEUE_ROUTINGKEY);
    }

    public void sendToDyHttp(int location, DyHttpRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.DY_HTTP_TOPIC_EXCHANGE,
                MQBeanConfig.DY_HTTP_QUEUE_ROUTINGKEY);
    }

    public void sendToRongtong(int location, RongtongRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.RONGTONG_TOPIC_EXCHANGE,
                MQBeanConfig.RONGTONG_QUEUE_ROUTINGKEY);
    }

    public void sendToCollection(int location, TextRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.COLLECTION_TOPIC_EXCHANGE,
                MQBeanConfig.COLLECTION_QUEUE_ROUTINGKEY);
    }

    public void sendToTaskCallback(TaskMqBo taskMqBo, int priority){
        this.sendPriorityMessage(1, taskMqBo, priority,
                MQBeanConfig.CALLBACK_TOPIC_EXCHANGE,
                MQBeanConfig.TASK_LB_CALLBACK_QUEUE);
    }

    public void sendToTaskCallbackRepeat(TaskMqBo taskMqBo, int priority){
        this.sendPriorityMessage(1, taskMqBo, priority,
                MQBeanConfig.CALLBACK_TOPIC_EXCHANGE,
                MQBeanConfig.TASK_CALLBACK_REPEAT_QUEUE);
    }

    public void sendToTextSml(int location, TextSmlRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.TEXT_SIMILARITY_TOPIC_EXCHANGE,
                MQBeanConfig.TEXT_SIMILARITY_QUEUE_ROUTINGKEY);
    }

    public void sendToVP(int location, VPRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.VP_TOPIC_EXCHANGE,
                MQBeanConfig.VP_QUEUE_ROUTINGKEY);
    }

    public void sendToIdentity(int location, IdentityRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.IDENTITY_TOPIC_EXCHANGE,
                MQBeanConfig.IDENTITY_QUEUE_ROUTINGKEY);
    }

    public void sendToEmotion(int location, EmotionRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.EMOTION_TOPIC_EXCHANGE,
                MQBeanConfig.EMOTION_QUEUE_ROUTINGKEY);
    }

    public void sendToAIGS(int location, AIGSRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.AIGS_TOPIC_EXCHANGE,
                MQBeanConfig.AIGS_QUEUE_ROUTINGKEY);
    }

    public void sendToDeepSeek(int location, DyHttpRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.DEEPSEEK_TOPIC_EXCHANGE,
                MQBeanConfig.DEEPSEEK_QUEUE_ROUTINGKEY);
    }

    public void sendToQwen(int location, DyHttpRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.QWEN_TOPIC_EXCHANGE,
                MQBeanConfig.QWEN_QUEUE_ROUTINGKEY);
    }

    public void sendToRTFraudReview(int location, RongtongRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.RT_FRAUD_REVIEW_TOPIC_EXCHANGE,
                MQBeanConfig.RT_FRAUD_REVIEW_QUEUE_ROUTINGKEY);
    }

    public void sendToMarketCall(int location, MarketCallRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.MARKET_CALL_TOPIC_EXCHANGE,
                MQBeanConfig.MARKET_CALL_QUEUE_ROUTINGKEY);
    }

    public void sendToDeal(int location, DealRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.DEAL_TOPIC_EXCHANGE,
                MQBeanConfig.DEAL_QUEUE_ROUTINGKEY);
    }

    public void sendToRAG(int location, RAGRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.RAG_TOPIC_EXCHANGE,
                MQBeanConfig.RAG_QUEUE_ROUTINGKEY);
    }

    public void sendToFastASR(int location, ASRRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.FAST_ASR_TOPIC_EXCHANGE,
                MQBeanConfig.FAST_ASR_QUEUE_ROUTINGKEY);
    }

    public void sendToBGE(int location, BGERequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.BGE_TOPIC_EXCHANGE,
                MQBeanConfig.BGE_QUEUE_ROUTINGKEY);
    }

    public void sendToCQAnti(int location, CQAntiRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.CHONGQING_ANTI_TOPIC_EXCHANGE,
                MQBeanConfig.CHONGQING_ANTI_QUEUE_ROUTINGKEY);
    }

    public void sendToCQAgent(int location, CQAgentRequest request, int priority){
        this.sendPriorityMessage(location, request, priority,
                MQBeanConfig.CQ_AGENT_TOPIC_EXCHANGE,
                MQBeanConfig.CQ_AGENT_QUEUE_ROUTINGKEY);
    }

    public void sendPriorityMessage(int location, Object message, int priority, String exchange, String routingKey) {
//        String json = JacksonUtil.obj2String(message);
        try {
            semaphore.acquire();
            this.getRabbitTemplate(location).convertAndSend(exchange,
                    routingKey,
                    message,
                    messagePostProcessor -> {
                        messagePostProcessor.getMessageProperties().setPriority(priority);
                        messagePostProcessor.getMessageProperties().getHeaders().remove("__TypeId__");
                        return messagePostProcessor;
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            semaphore.release();
        }
    }
}
