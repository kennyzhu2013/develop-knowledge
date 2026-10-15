package com.cmic.engine.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class ActionFactory {
    @Resource
    private AsrAction asrAction;
    @Resource
    private ScriptAction scriptAction;
    @Resource
    private KeywordAnalysisAction keywordAnalysisAction;
    @Resource
    private KeywordAction keywordAction;
    @Resource
    private ProcessAction processAction;
    @Resource
    private NullAction nullAction;
    @Resource
    private ScenarioAction scenarioAction;
    @Autowired
    private EventAction eventAction;
    @Autowired
    private VPRAction vprAction;
    @Autowired
    private VFDAction vfdAction;
    @Autowired
    private DSZAction dszAction;
    @Autowired
    private DIFYAction difyAction;
    @Autowired
    private HuaweiQCAction huaweiQCAction;
    @Autowired
    private HttpModelAction httpModelAction;
    @Autowired
    private AtomCommonAction atomCommonAction;
    @Autowired
    private RongtongAction rongtongAction;
    @Autowired
    private JtAsrAction jtAsrAction;
    @Autowired
    private CollectionAction collectionAction;
    @Autowired
    private TextSmlAction textSmlAction;
    @Autowired
    private VPAction vpAction;
    @Autowired
    private KeywordRiskShieldAction keywordRiskShieldAction;
    @Autowired
    private IdentityAction identityAction;
    @Autowired
    private EmotionAction emotionAction;
    @Autowired
    private AIGSAction aigsAction;
    @Autowired
    private DeepSeekAction deepSeekAction;
    @Autowired
    private KeyWordGroupAction keyWordGroupAction;
    @Autowired
    private QwenAction qwenAction;
    @Autowired
    private AttributesAction attributesAction;
    @Autowired
    private RTFraudReviewAction rtFraudReviewAction;
    @Autowired
    private MarketCallAction marketCallAction;
    @Autowired
    private KeywordRegexAction keywordRegexAction;
    @Autowired
    private DealAction dealAction;
    @Autowired
    private RAGAction ragAction;
    @Autowired
    private KeywordAnalysisLTAction keywordAnalysisLTAction;
    @Autowired
    private ShardAction shardAction;
    @Autowired
    private BGEAction bgeAction;
    @Autowired
    private CQAntiAction cqAntiAction;
    @Autowired
    private CQAgentAction cqAgentAction;

    public Action action(String application) {
        Action action;
        switch (application) {
            case "asr":
                action = asrAction;
                break;
            case "jt_asr":
                action = jtAsrAction;
                break;
            case "keyword":
                action = keywordAction;
                break;
            case "process":
                action = processAction;
                break;
            case "script":
                action = scriptAction;
                break;
            case "keywordAnalysis":
                action = keywordAnalysisAction;
                break;
            case "scenario":
                action = scenarioAction;
                break;
            case "event":
                action = eventAction;
                break;
            case "vpr":
                action = vprAction;
                break;
            case "vfd":
                action = vfdAction;
                break;
            case "desensitization":
                action = dszAction;
                break;
            case "dify":
                action = difyAction;
                break;
            case "huawei_qc":
                action = huaweiQCAction;
                break;
            case "http_model":
                action = httpModelAction;
                break;
            case "atom_process":
                action = atomCommonAction;
                break;
            case "rt_qc":
                action = rongtongAction;
                break;
            case "collection":
                action = collectionAction;
                break;
            case "text_sml":
                action = textSmlAction;
                break;
            case "vp":
                action = vpAction;
                break;
            case "keyword_rs":
                action = keywordRiskShieldAction;
                break;
            case "identity":
                action = identityAction;
                break;
            case "emotion":
                action = emotionAction;
                break;
            case "aigs":
                action = aigsAction;
                break;
            case "deepseek":
                action = deepSeekAction;
                break;
            case "word_group":
                action = keyWordGroupAction;
                break;
            case "qwen":
                action = qwenAction;
                break;
            case "attribute":
                action = attributesAction;
                break;
            case "rt_fraud_review":
                action = rtFraudReviewAction;
                break;
            case "market_call":
                action = marketCallAction;
                break;
            case "word_regex":
                action = keywordRegexAction;
                break;
            case "deal":
                action = dealAction;
                break;
            case "rag":
                action = ragAction;
                break;
            case "keywordAnalysisLT":
                action = keywordAnalysisLTAction;
                break;
            case "shard":
                action = shardAction;
                break;
            case "bge":
                action = bgeAction;
                break;
            case "cq_anti":
                action = cqAntiAction;
                break;
            case "cq_agent":
                action = cqAgentAction;
                break;
            default:
                action = nullAction;
                break;
        }
        return action;
    }
}
