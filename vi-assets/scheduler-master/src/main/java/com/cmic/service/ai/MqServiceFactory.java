package com.cmic.service.ai;

import com.cmic.config.rabbitmq.MQBeanConfig;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ai.KeywordAnalysisResponse;
import com.cmic.util.JacksonUtil;
import groovy.util.logging.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

import static com.cmic.pojo.bo.AIModelEnum.*;

@Slf4j
@Service
public class MqServiceFactory {
    @Resource
    private ASRService asrService;
    @Autowired
    private JiutianASRService jiutianASRService;
    @Resource
    private ScriptService scriptService;
    @Resource
    private KeywordAnalysisService keywordAnalysisService;
    @Resource
    private ScenarioService scenarioService;
    @Autowired
    private VPRService vprService;
    @Autowired
    private VFDService vfdService;
    @Autowired
    private DSZService dszService;
    @Autowired
    private DIFYService difyService;
    @Autowired
    private RongtongService rongtongService;
    @Autowired
    private HttpModelService httpModelService;
    @Autowired
    private CollectionService collectionService;
    @Autowired
    private HuaweiService huaweiService;
    @Autowired
    private TextSmlService textSmlService;
    @Autowired
    private VPService vpService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private EmotionService emotionService;
    @Autowired
    private AIGSService aigsService;
    @Autowired
    private DeepSeekService deepSeekService;
    @Autowired
    private QwenService qwenService;
    @Autowired
    private RTFraudReviewService rtfraudReviewService;
    @Autowired
    private MarketCallService marketCallService;
    @Autowired
    private DealService dealService;
    @Autowired
    private RAGService ragService;
    @Autowired
    private BGEService bgeService;
    @Autowired
    private CQAntiService cqAntiService;
    @Autowired
    private CQAgentService cqAgentService;


    public IMqService mqService(String queueName) {
        switch (queueName) {
            case MQBeanConfig.ASR_RESPONSE_QUEUE:
                return asrService;
            case MQBeanConfig.SCRIPT_RESPONSE_QUEUE:
                return scriptService;
            case MQBeanConfig.KEYWORD_RESPONSE_QUEUE:
                return keywordAnalysisService;
            case MQBeanConfig.SCENARIO_RESPONSE_QUEUE:
                return scenarioService;
            case MQBeanConfig.VPR_RESPONSE_QUEUE:
                return vprService;
            case MQBeanConfig.VFD_RESPONSE_QUEUE:
                return vfdService;
            case MQBeanConfig.DSZ_RESPONSE_QUEUE:
                return dszService;
            case MQBeanConfig.DIFY_RESPONSE_QUEUE:
                return difyService;
            case MQBeanConfig.RONGTONG_RESPONSE_QUEUE:
                return rongtongService;
            case MQBeanConfig.DY_HTTP_RESPONSE_QUEUE:
                return httpModelService;
            case MQBeanConfig.JT_ASR_RESPONSE_QUEUE:
                return jiutianASRService;
            case MQBeanConfig.COLLECTION_RESPONSE_QUEUE:
                return collectionService;
            case MQBeanConfig.HUAWEI_RESPONSE_QUEUE:
                return huaweiService;
            case MQBeanConfig.TEXT_SIMILARITY_RESPONSE_QUEUE:
                return textSmlService;
            case MQBeanConfig.VP_RESPONSE_QUEUE:
                return vpService;
            case MQBeanConfig.IDENTITY_RESPONSE_QUEUE:
                return identityService;
            case MQBeanConfig.EMOTION_RESPONSE_QUEUE:
                return emotionService;
            case MQBeanConfig.AIGS_RESPONSE_QUEUE:
                return aigsService;
            case MQBeanConfig.DEEPSEEK_RESPONSE_QUEUE:
                return deepSeekService;
            case MQBeanConfig.QWEN_RESPONSE_QUEUE:
                return qwenService;
            case MQBeanConfig.RT_FRAUD_REVIEW_RESPONSE_QUEUE:
                return rtfraudReviewService;
            case MQBeanConfig.MARKET_CALL_RESPONSE_QUEUE:
                return marketCallService;
            case MQBeanConfig.DEAL_RESPONSE_QUEUE:
                return dealService;
            case MQBeanConfig.RAG_RESPONSE_QUEUE:
                return ragService;
            case MQBeanConfig.FAST_ASR_RESPONSE_QUEUE:
                return asrService;
            case MQBeanConfig.BGE_RESPONSE_QUEUE:
                return bgeService;
            case MQBeanConfig.CHONGQING_ANTI_RESPONSE_QUEUE:
                return cqAntiService;
            case MQBeanConfig.CQ_AGENT_RESPONSE_QUEUE:
                return cqAgentService;
            default:
                return asrService;
        }
    }

    public static AIModelEnum getEnumByQueueName(String queueName, BaseResponse<?> baseResponse){
        if (MQBeanConfig.KEYWORD_RESPONSE_QUEUE.equals(queueName)) {
            KeywordAnalysisResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), KeywordAnalysisResponse.class);
            return KeywordAnalysisService.LOW_THRESH.equals(response.getThreshLevel()) ? KEYWORD_ANALYSIS_LT : KEYWORD_ANALYSIS;
        }

        switch (queueName) {
            case MQBeanConfig.ASR_RESPONSE_QUEUE:
                return ASR;
            case MQBeanConfig.SCRIPT_RESPONSE_QUEUE:
                return SCRIPT;
//            case MQBeanConfig.KEYWORD_RESPONSE_QUEUE:
//                return KEYWORD_ANALYSIS;
            case MQBeanConfig.SCENARIO_RESPONSE_QUEUE:
                return SCENARIO;
            case MQBeanConfig.VPR_RESPONSE_QUEUE:
                return VPR;
            case MQBeanConfig.VFD_RESPONSE_QUEUE:
                return VFD;
            case MQBeanConfig.DSZ_RESPONSE_QUEUE:
                return DSZ;
            case MQBeanConfig.DIFY_RESPONSE_QUEUE:
                return DIFY;
            case MQBeanConfig.RONGTONG_RESPONSE_QUEUE:
                return RT;
            case MQBeanConfig.DY_HTTP_RESPONSE_QUEUE:
                return DYHTTP;
            case MQBeanConfig.JT_ASR_RESPONSE_QUEUE:
                return ASR;
            case MQBeanConfig.COLLECTION_RESPONSE_QUEUE:
                return COLLECTION;
            case MQBeanConfig.HUAWEI_RESPONSE_QUEUE:
                return HUAWEI_QC;
            case MQBeanConfig.TEXT_SIMILARITY_RESPONSE_QUEUE:
                return TEXT_SIMILARITY;
            case MQBeanConfig.VP_RESPONSE_QUEUE:
                return VP;
            case MQBeanConfig.IDENTITY_RESPONSE_QUEUE:
                return IDENTITY;
            case MQBeanConfig.EMOTION_RESPONSE_QUEUE:
                return EMOTION;
            case MQBeanConfig.AIGS_RESPONSE_QUEUE:
                return AIGS;
            case MQBeanConfig.DEEPSEEK_RESPONSE_QUEUE:
                return DEEPSEEK;
            case MQBeanConfig.QWEN_RESPONSE_QUEUE:
                return QWEN;
            case MQBeanConfig.RT_FRAUD_REVIEW_RESPONSE_QUEUE:
                return RT_FRAUD_REVIEW;
            case MQBeanConfig.MARKET_CALL_RESPONSE_QUEUE:
                return MARKET_CALL;
            case MQBeanConfig.DEAL_RESPONSE_QUEUE:
                return DEAL;
            case MQBeanConfig.RAG_RESPONSE_QUEUE:
                return RAG;
            case MQBeanConfig.FAST_ASR_RESPONSE_QUEUE:
                return ASR;
            case MQBeanConfig.BGE_RESPONSE_QUEUE:
                return BGE;
            case MQBeanConfig.CHONGQING_ANTI_RESPONSE_QUEUE:
                return CQ_ANTI;
            case MQBeanConfig.CQ_AGENT_RESPONSE_QUEUE:
                return CQ_AGENT;
            default:
                return null;
        }
    }
}
