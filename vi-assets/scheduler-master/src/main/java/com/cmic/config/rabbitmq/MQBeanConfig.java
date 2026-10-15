package com.cmic.config.rabbitmq;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class MQBeanConfig {
    /**********ASR***********/
    public static final String ASR_TOPIC_EXCHANGE = "scheduler.asr.exchange";
    public static final String ASR_QUEUE = "scheduler.asr.queue";
    public static final String ASR_TOPIC_ROUTINGKEY = "topic.asr.*";
    public static final String ASR_QUEUE_ROUTINGKEY = "topic.asr.key";
    public static final String ASR_RESPONSE_QUEUE = "scheduler.asr.response.queue";

    /**********九天ASR***********/
    public static final String JT_ASR_TOPIC_EXCHANGE = "scheduler.jiutian-asr.exchange";
    public static final String JT_ASR_QUEUE = "scheduler.jiutian-asr.queue";
    public static final String JT_ASR_TOPIC_ROUTINGKEY = "topic.jiutian-asr.*";
    public static final String JT_ASR_QUEUE_ROUTINGKEY = "topic.jiutian-asr.key";
    public static final String JT_ASR_RESPONSE_QUEUE = "scheduler.jiutian-asr.response.queue";

    public static final String BA_TOPIC_EXCHANGE = "scheduler.ba.exchange";
    public static final String BA_QUEUE = "scheduler.ba.queue";
    public static final String BA_TOPIC_ROUTINGKEY = "topic.ba.*";
    public static final String BA_QUEUE_ROUTINGKEY = "topic.ba.key";
    public static final String BA_RESPONSE_QUEUE = "scheduler.ba.response.queue";

    /**********话术***********/
    public static final String SCRIPT_TOPIC_EXCHANGE = "scheduler.script.exchange";
    public static final String SCRIPT_QUEUE = "scheduler.script.queue";
    public static final String SCRIPT_TOPIC_ROUTINGKEY = "topic.script.*";
    public static final String SCRIPT_QUEUE_ROUTINGKEY = "topic.script.key";
    public static final String SCRIPT_RESPONSE_QUEUE = "scheduler.script.response.queue";

    /**********关键词***********/
    public static final String KEYWORD_TOPIC_EXCHANGE = "scheduler.keyword.exchange";
    public static final String KEYWORD_QUEUE = "scheduler.keyword.queue";
    public static final String KEYWORD_TOPIC_ROUTINGKEY = "topic.keyword.*";
    public static final String KEYWORD_QUEUE_ROUTINGKEY = "topic.keyword.key";
    public static final String KEYWORD_RESPONSE_QUEUE = "scheduler.keyword.response.queue";

    /**********TFIDF***********/
    public static final String TFIDF_TOPIC_EXCHANGE = "scheduler.tfidf.exchange";
    public static final String TFIDF_QUEUE = "scheduler.tfidf.queue";
    public static final String TFIDF_TOPIC_ROUTINGKEY = "topic.tfidf.*";
    public static final String TFIDF_QUEUE_ROUTINGKEY = "topic.tfidf.key";
    public static final String TFIDF_RESPONSE_QUEUE = "scheduler.tfidf.response.queue";

    /**********场景分析***********/
    public static final String SCENARIO_TOPIC_EXCHANGE = "scheduler.scenario.exchange";
    public static final String SCENARIO_QUEUE = "scheduler.scenario.queue";
    public static final String SCENARIO_TOPIC_ROUTINGKEY = "topic.scenario.*";
    public static final String SCENARIO_QUEUE_ROUTINGKEY = "topic.scenario.key";
    public static final String SCENARIO_RESPONSE_QUEUE = "scheduler.scenario.response.queue";

    /**********接口回调***********/
    public static final String CALLBACK_TOPIC_EXCHANGE = "scheduler.callback.exchange";
    public static final String CALLBACK_QUEUE = "scheduler.callback.queue";
    public static final String CALLBACK_TOPIC_ROUTINGKEY = "topic.callback.*";
    public static final String CALLBACK_QUEUE_ROUTINGKEY = "topic.callback.key";
    public static final String TASK_CREATE_QUEUE = "scheduler.task.queue";
    public static final String TASK_CALLBACK_QUEUE = "scheduler.task.callback.queue";
    public static final String TASK_LB_CALLBACK_QUEUE = "scheduler.task.lb-callback.queue";
    public static final String TASK_CALLBACK_REPEAT_QUEUE = "scheduler.task.repeat.queue";

    /**********声音鉴伪 voice forgery detection***********/
    public static final String VFD_TOPIC_EXCHANGE = "scheduler.vfd.exchange";
    public static final String VFD_QUEUE = "scheduler.vfd.queue";
    public static final String VFD_TOPIC_ROUTINGKEY = "topic.vfd.*";
    public static final String VFD_QUEUE_ROUTINGKEY = "topic.vfd.key";
    public static final String VFD_RESPONSE_QUEUE = "scheduler.vfd.response.queue";

    /**********声纹比对 voice print recognition***********/
    public static final String VPR_TOPIC_EXCHANGE = "scheduler.vpr.exchange";
    public static final String VPR_QUEUE = "scheduler.vpr.queue";
    public static final String VPR_TOPIC_ROUTINGKEY = "topic.vpr.*";
    public static final String VPR_QUEUE_ROUTINGKEY = "topic.vpr.key";
    public static final String VPR_RESPONSE_QUEUE = "scheduler.vpr.response.queue";

    /**********文本脱敏***********/
    public static final String DSZ_TOPIC_EXCHANGE = "scheduler.desensitization.exchange";
    public static final String DSZ_QUEUE = "scheduler.desensitization.queue";
    public static final String DSZ_TOPIC_ROUTINGKEY = "topic.desensitization.*";
    public static final String DSZ_QUEUE_ROUTINGKEY = "topic.desensitization.key";
    public static final String DSZ_RESPONSE_QUEUE = "scheduler.desensitization.response.queue";

    /**********DIFY***********/
    public static final String DIFY_TOPIC_EXCHANGE = "scheduler.dify.exchange";
    public static final String DIFY_QUEUE = "scheduler.dify.queue";
    public static final String DIFY_TOPIC_ROUTINGKEY = "topic.dify.*";
    public static final String DIFY_QUEUE_ROUTINGKEY = "topic.dify.key";
    public static final String DIFY_RESPONSE_QUEUE = "scheduler.dify.response.queue";

    /**********Huawei***********/
    public static final String HUAWEI_TOPIC_EXCHANGE = "scheduler.huawei.exchange";
    public static final String HUAWEI_QUEUE = "scheduler.huawei.queue";
    public static final String HUAWEI_TOPIC_ROUTINGKEY = "topic.huawei.*";
    public static final String HUAWEI_QUEUE_ROUTINGKEY = "topic.huawei.key";
    public static final String HUAWEI_RESPONSE_QUEUE = "scheduler.huawei.response.queue";

    /**********DY Http to scheduler adapter***********/
    public static final String DY_HTTP_TOPIC_EXCHANGE = "scheduler.dyHttp.exchange";
    public static final String DY_HTTP_QUEUE = "scheduler.dyHttp.queue";
    public static final String DY_HTTP_TOPIC_ROUTINGKEY = "topic.dyHttp.*";
    public static final String DY_HTTP_QUEUE_ROUTINGKEY = "topic.dyHttp.key";
    public static final String DY_HTTP_RESPONSE_QUEUE = "scheduler.dyHttp.response.queue";

    /**********Rongtong***********/
    public static final String RONGTONG_TOPIC_EXCHANGE = "scheduler.rongtong.queue";
    public static final String RONGTONG_QUEUE = "scheduler.rongtong.queue";
    public static final String RONGTONG_TOPIC_ROUTINGKEY = "topic.rongtong.*";
    public static final String RONGTONG_QUEUE_ROUTINGKEY = "topic.rongtong.key";
    public static final String RONGTONG_RESPONSE_QUEUE = "scheduler.rongtong.response.queue";

    /**********催收***********/
    public static final String COLLECTION_TOPIC_EXCHANGE = "scheduler.scenario.exchange";
    public static final String COLLECTION_QUEUE = "scheduler.scenario.cuishou.queue";
    public static final String COLLECTION_TOPIC_ROUTINGKEY = "topic.scenario.cuishou.*";
    public static final String COLLECTION_QUEUE_ROUTINGKEY = "topic.scenario.cuishou.key";
    public static final String COLLECTION_RESPONSE_QUEUE = "scheduler.scenario.cuishou.response.queue";

    /**********催收***********/
    public static final String TEXT_SIMILARITY_TOPIC_EXCHANGE = "scheduler.cs_qc.exchange";
    public static final String TEXT_SIMILARITY_QUEUE = "scheduler.cs_qc.queue";
    public static final String TEXT_SIMILARITY_QUEUE_ROUTINGKEY = "topic.cs_qc.key";
    public static final String TEXT_SIMILARITY_RESPONSE_QUEUE = "scheduler.cs_qc.response.queue";

    /**********声纹比对 voice print recognition***********/
    public static final String VP_TOPIC_EXCHANGE = "scheduler.vp.exchange";
    public static final String VP_QUEUE = "scheduler.vp.queue";
    public static final String VP_TOPIC_ROUTINGKEY = "topic.vp.*";
    public static final String VP_QUEUE_ROUTINGKEY = "topic.vp.key";
    public static final String VP_RESPONSE_QUEUE = "scheduler.vp.response.queue";

    /**********身份比对***********/
    public static final String IDENTITY_TOPIC_EXCHANGE = "scheduler.identity.exchange";
    public static final String IDENTITY_QUEUE = "scheduler.identity.queue";
    public static final String IDENTITY_TOPIC_ROUTINGKEY = "topic.identity.*";
    public static final String IDENTITY_QUEUE_ROUTINGKEY = "topic.identity.key";
    public static final String IDENTITY_RESPONSE_QUEUE = "scheduler.identity.response.queue";

    /**********情绪识别***********/
    public static final String EMOTION_TOPIC_EXCHANGE = "scheduler.emotion.exchange";
    public static final String EMOTION_QUEUE = "scheduler.emotion.queue";
    public static final String EMOTION_TOPIC_ROUTINGKEY = "topic.emotion.*";
    public static final String EMOTION_QUEUE_ROUTINGKEY = "topic.emotion.key";
    public static final String EMOTION_RESPONSE_QUEUE = "scheduler.emotion.response.queue";

    /**********AIGS***********/
    public static final String AIGS_TOPIC_EXCHANGE = "scheduler.aigs.exchange";
    public static final String AIGS_QUEUE = "scheduler.aigs.queue";
    public static final String AIGS_TOPIC_ROUTINGKEY = "topic.aigs.*";
    public static final String AIGS_QUEUE_ROUTINGKEY = "topic.aigs.key";
    public static final String AIGS_RESPONSE_QUEUE = "scheduler.aigs.response.queue";

    /**********DeepSeek***********/
    public static final String DEEPSEEK_TOPIC_EXCHANGE = "scheduler.deepseek.exchange";
    public static final String DEEPSEEK_QUEUE = "scheduler.deepseek.queue";
    public static final String DEEPSEEK_TOPIC_ROUTINGKEY = "topic.deepseek.*";
    public static final String DEEPSEEK_QUEUE_ROUTINGKEY = "topic.deepseek.key";
    public static final String DEEPSEEK_RESPONSE_QUEUE = "scheduler.deepseek.response.queue";

    /**********Qwen***********/
    public static final String QWEN_TOPIC_EXCHANGE = "scheduler.qwen.exchange";
    public static final String QWEN_QUEUE = "scheduler.qwen.queue";
    public static final String QWEN_TOPIC_ROUTINGKEY = "topic.qwen.*";
    public static final String QWEN_QUEUE_ROUTINGKEY = "topic.qwen.key";
    public static final String QWEN_RESPONSE_QUEUE = "scheduler.qwen.response.queue";

    /**********融通涉诈复核***********/
    public static final String RT_FRAUD_REVIEW_TOPIC_EXCHANGE = "scheduler.rt-fraud-review.exchange";
    public static final String RT_FRAUD_REVIEW_QUEUE = "scheduler.rt-fraud-review.queue";
    public static final String RT_FRAUD_REVIEW_TOPIC_ROUTINGKEY = "topic.rt-fraud-review.*";
    public static final String RT_FRAUD_REVIEW_QUEUE_ROUTINGKEY = "topic.rt-fraud-review.key";
    public static final String RT_FRAUD_REVIEW_RESPONSE_QUEUE = "scheduler.rt-fraud-review.response.queue";

    /**********营销外呼***********/
    public static final String MARKET_CALL_TOPIC_EXCHANGE = "scheduler.market-call.exchange";
    public static final String MARKET_CALL_QUEUE = "scheduler.market-call.queue";
    public static final String MARKET_CALL_TOPIC_ROUTINGKEY = "topic.market-call.*";
    public static final String MARKET_CALL_QUEUE_ROUTINGKEY = "topic.market-call.key";
    public static final String MARKET_CALL_RESPONSE_QUEUE = "scheduler.market-call.response.queue";

    /**********成交分类***********/
    public static final String DEAL_TOPIC_EXCHANGE = "scheduler.deal.exchange";
    public static final String DEAL_QUEUE = "scheduler.deal.queue";
    public static final String DEAL_TOPIC_ROUTINGKEY = "topic.deal.*";
    public static final String DEAL_QUEUE_ROUTINGKEY = "topic.deal.key";
    public static final String DEAL_RESPONSE_QUEUE = "scheduler.deal.response.queue";

    /**********RAG***********/
    public static final String RAG_TOPIC_EXCHANGE = "scheduler.rag.exchange";
    public static final String RAG_QUEUE = "scheduler.rag.queue";
    public static final String RAG_TOPIC_ROUTINGKEY = "topic.rag.*";
    public static final String RAG_QUEUE_ROUTINGKEY = "topic.rag.key";
    public static final String RAG_RESPONSE_QUEUE = "scheduler.rag.response.queue";

    /**********优先ASR***********/
    public static final String FAST_ASR_TOPIC_EXCHANGE = "scheduler.asr.exchange";
    public static final String FAST_ASR_QUEUE = "scheduler.asr.fast.queue";
    public static final String FAST_ASR_QUEUE_ROUTINGKEY = "scheduler.task.fast.asr";
    public static final String FAST_ASR_RESPONSE_QUEUE = "scheduler.asr.response.fast.queue";

    /**********BGE多分类器***********/
    public static final String BGE_TOPIC_EXCHANGE = "scheduler.iterative_anti_model.exchange";
    public static final String BGE_QUEUE = "scheduler.iterative_anti_model.queue";
    public static final String BGE_QUEUE_ROUTINGKEY = "scheduler.task.iterative_anti_model";
    public static final String BGE_RESPONSE_QUEUE = "scheduler.iterative_anti_model.response.queue";

    /**********重庆行业卡反诈***********/
    public static final String CHONGQING_ANTI_TOPIC_EXCHANGE = "scheduler.chongqing-anti.exchange";
    public static final String CHONGQING_ANTI_QUEUE = "scheduler.chongqing-anti.queue";
    public static final String CHONGQING_ANTI_QUEUE_ROUTINGKEY = "scheduler.task.chongqing-anti";
    public static final String CHONGQING_ANTI_RESPONSE_QUEUE = "scheduler.chongqing-anti.response.queue";


    /**********重庆行业卡agent复核***********/
    public static final String CQ_AGENT_TOPIC_EXCHANGE = "scheduler.cqagent.exchange";
    public static final String CQ_AGENT_QUEUE = "scheduler.cqagent.queue";
    public static final String CQ_AGENT_QUEUE_ROUTINGKEY = "scheduler.task.cqagent";
    public static final String CQ_AGENT_RESPONSE_QUEUE = "scheduler.cqagent.response.queue";

    private final DynamicBeanRegistry dynamicBeanRegistry;

    @Autowired
    public MQBeanConfig(DynamicBeanRegistry dynamicBeanRegistry) {
        this.dynamicBeanRegistry = dynamicBeanRegistry;
    }

    @PostConstruct
    public void configureRabbitMQ() {
        configureSouth();
//        configureNorth();
    }

    private void configureSouth(){
        configureMq("south");
        configureCallback();
    }

    private void configureNorth(){
        configureMq("north");
    }

    private void configureMq(String location){
        configureASR(location);
        configureJTASR(location);
//        configureBA(location);
//        configureScript(location);
        configureKeyword(location);
//        configureTfidf(location);
        configureScenario(location);
        configureVFD(location);
        configureVPR(location);
        configureDSZ(location);
        configureDIFY(location);
//        configureHuawei(location);
        configureRongtong(location);
        configureDyHttp(location);
        configureCollection(location);
//        configureTextSimilarityQC(location);
        configureVP(location);
        configureIdentity(location);
        configureEmotion(location);
        configureAIGS(location);
        configureDeepSeek(location);
        configureQwen(location);
        configureRTFraudReview(location);
        configureMarketCall(location);
        configureDeal(location);
        configureRAG(location);
        configureFastASR(location);
        configureBGE(location);
        configureChongqingAnti(location);
        configureCQAgent(location);
    }

    private void configureASR(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.ASR_QUEUE, location+"AsrQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.ASR_TOPIC_EXCHANGE, location+"AsrExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.ASR_TOPIC_ROUTINGKEY, location+"AsrBindingExchange");
    }

    private void configureJTASR(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.JT_ASR_QUEUE, location+"JTAsrQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.JT_ASR_TOPIC_EXCHANGE, location+"JTAsrExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.JT_ASR_TOPIC_ROUTINGKEY, location+"JTAsrBindingExchange");
    }

    private void configureBA(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.BA_QUEUE, location+"BAQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.BA_TOPIC_EXCHANGE, location+"BAExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.BA_TOPIC_ROUTINGKEY, location+"BABindingExchange");
    }

    private void configureScript(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.SCRIPT_QUEUE, location+"ScriptQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.SCRIPT_TOPIC_EXCHANGE, location+"ScriptExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.SCRIPT_TOPIC_ROUTINGKEY, location+"ScriptBindingExchange");
    }

    private void configureKeyword(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.KEYWORD_QUEUE, location+"KeywordQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.KEYWORD_TOPIC_EXCHANGE, location+"KeywordExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.KEYWORD_TOPIC_ROUTINGKEY, location+"KeywordBindingExchange");
    }


    private void configureTfidf(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.TFIDF_QUEUE, location+"tfidfQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.TFIDF_TOPIC_EXCHANGE, location+"tfidfExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.TFIDF_TOPIC_ROUTINGKEY, location+"tfidfBindingExchange");
    }

    private void configureScenario(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.SCENARIO_QUEUE, location+"scenarioQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.SCENARIO_TOPIC_EXCHANGE, location+"scenarioExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.SCENARIO_TOPIC_ROUTINGKEY, location+"scenarioBindingExchange");
    }

    private void configureCallback(){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.CALLBACK_QUEUE, "callbackQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.CALLBACK_TOPIC_EXCHANGE, "callbackExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.CALLBACK_TOPIC_ROUTINGKEY, "callbackBindingExchange");
    }

    private void configureVFD(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.VFD_QUEUE, location+"vfdQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.VFD_TOPIC_EXCHANGE, location+"vfdExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.VFD_TOPIC_ROUTINGKEY, location+"vfdBindingExchange");
    }

    private void configureVPR(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.VPR_QUEUE, location+"vprQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.VPR_TOPIC_EXCHANGE, location+"vprExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.VPR_TOPIC_ROUTINGKEY, location+"vprBindingExchange");
    }

    private void configureDSZ(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.DSZ_QUEUE, location+"dszQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.DSZ_TOPIC_EXCHANGE, location+"dszExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.DSZ_TOPIC_ROUTINGKEY, location+"dszBindingExchange");
    }

    private void configureDIFY(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.DIFY_QUEUE, location+"difyQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.DIFY_TOPIC_EXCHANGE, location+"difyExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.DIFY_TOPIC_ROUTINGKEY, location+"difyBindingExchange");
    }

    private void configureHuawei(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.HUAWEI_QUEUE, location+"huaweiQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.HUAWEI_TOPIC_EXCHANGE, location+"huaweiExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.HUAWEI_TOPIC_ROUTINGKEY, location+"huaweiBindingExchange");
    }

    private void configureDyHttp(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.DY_HTTP_QUEUE, location+"dyHttpQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.DY_HTTP_TOPIC_EXCHANGE, location+"dyHttpExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.DY_HTTP_TOPIC_ROUTINGKEY, location+"dyHttpBindingExchange");
    }

    private void configureRongtong(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.RONGTONG_QUEUE, location+"rongtongQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.RONGTONG_TOPIC_EXCHANGE, location+"rongtongExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.RONGTONG_TOPIC_ROUTINGKEY, location+"rongtongBindingExchange");
    }

    private void configureCollection(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.COLLECTION_QUEUE, location+"collectionQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.COLLECTION_TOPIC_EXCHANGE, location+"collectionExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.COLLECTION_TOPIC_ROUTINGKEY, location+"collectionBindingExchange");
    }

    private void configureTextSimilarityQC(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.TEXT_SIMILARITY_QUEUE, location+"textSmlQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.TEXT_SIMILARITY_TOPIC_EXCHANGE, location+"textSmlExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.TEXT_SIMILARITY_QUEUE_ROUTINGKEY, location+"textSmlBindingExchange");
    }

    private void configureVP(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.VP_QUEUE, location+"vpQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.VP_TOPIC_EXCHANGE, location+"vpExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.VP_TOPIC_ROUTINGKEY, location+"vpBindingExchange");
    }

    private void configureIdentity(String location){
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.IDENTITY_QUEUE, location+"IdentityQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.IDENTITY_TOPIC_EXCHANGE, location+"IdentityExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.IDENTITY_TOPIC_ROUTINGKEY, location+"IdentityBindingExchange");
    }

    private void configureEmotion(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.EMOTION_QUEUE, location+"EmotionQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.EMOTION_TOPIC_EXCHANGE, location+"EmotionExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.EMOTION_TOPIC_ROUTINGKEY, location+"EmotionBindingExchange");
    }

    private void configureAIGS(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.AIGS_QUEUE, location+"aigsQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.AIGS_TOPIC_EXCHANGE, location+"aigsExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.AIGS_TOPIC_ROUTINGKEY, location+"aigsBindingExchange");
    }

    private void configureDeepSeek(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.DEEPSEEK_QUEUE, location+"deepSeekQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.DEEPSEEK_TOPIC_EXCHANGE, location+"deepSeekExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.DEEPSEEK_TOPIC_ROUTINGKEY, location+"deepSeekBindingExchange");
    }

    private void configureQwen(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.QWEN_QUEUE, location+"qwenQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.QWEN_TOPIC_EXCHANGE, location+"qwenExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.QWEN_TOPIC_ROUTINGKEY, location+"qwenBindingExchange");
    }

    private void configureRTFraudReview(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.RT_FRAUD_REVIEW_QUEUE, location+"RTFraudReviewQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.RT_FRAUD_REVIEW_TOPIC_EXCHANGE, location+"RTFraudReviewExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.RT_FRAUD_REVIEW_TOPIC_ROUTINGKEY, location+"RTFraudReviewBindingExchange");
    }

    private void configureMarketCall(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.MARKET_CALL_QUEUE, location+"MarketCallQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.MARKET_CALL_TOPIC_EXCHANGE, location+"MarketCallExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.MARKET_CALL_TOPIC_ROUTINGKEY, location+"MarketCallBindingExchange");
    }

    private void configureDeal(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.DEAL_QUEUE, location+"DealQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.DEAL_TOPIC_EXCHANGE, location+"DealExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.DEAL_TOPIC_ROUTINGKEY, location+"DealBindingExchange");
    }

    private void configureRAG(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.RAG_QUEUE, location+"RAGQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.RAG_TOPIC_EXCHANGE, location+"RAGExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.RAG_TOPIC_ROUTINGKEY, location+"RAGBindingExchange");
    }

    private void configureFastASR(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.FAST_ASR_QUEUE, location+"FasrAsrQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.FAST_ASR_TOPIC_EXCHANGE, location+"FastAsrExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.FAST_ASR_QUEUE_ROUTINGKEY, location+"FasrAsrBindingExchange");
    }

    private void configureBGE(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.BGE_QUEUE, location+"BGEQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.BGE_TOPIC_EXCHANGE, location+"BGEExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.BGE_QUEUE_ROUTINGKEY, location+"BGEBindingExchange");
    }

    private void configureChongqingAnti(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.CHONGQING_ANTI_QUEUE, location+"chongqingAntiQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.CHONGQING_ANTI_TOPIC_EXCHANGE, location+"chongqingAntiExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.CHONGQING_ANTI_QUEUE_ROUTINGKEY, location+"chongqingAntiBindingExchange");
    }

    private void configureCQAgent(String location) {
        Queue queue = dynamicBeanRegistry.registerQueue(MQBeanConfig.CQ_AGENT_QUEUE, location+"CQAgentQueue");
        TopicExchange exchange = dynamicBeanRegistry.registerDirectExchange(MQBeanConfig.CQ_AGENT_TOPIC_EXCHANGE, location+"CQAgentExchange");
        dynamicBeanRegistry.registerBinding(queue, exchange, MQBeanConfig.CQ_AGENT_QUEUE_ROUTINGKEY, location+"CQAgentBindingExchange");
    }

}
