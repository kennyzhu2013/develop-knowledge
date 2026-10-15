package com.cmic.pojo.bo;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

public enum AIModelEnum {
    ASR("asr", "ASR", Attribute.ASR_STATUS.getName(), Arrays.asList(
            Attribute.ASR_TEXT.getName(),
            Attribute.ASR_DURATION.getName()),
            "transcription"),

    DSZ("dsz", "dsz", Attribute.DSZ_STATUS.getName(), Attribute.ASR_TEXT.getName()),
    KEYWORD("keyword", "关键词", Attribute.KEYWORD_STATUS.getName(), Attribute.KEYWORD_MATCHED.getName(),
            "wordMatching"),
    KEYWORD_ANALYSIS("keywordAnalysis", "关键词分析", Attribute.KEYWORD_ANALYSIS_STATUS.getName(), Arrays.asList(
            Attribute.KEYWORD_ANALYSIS_RESULT.getName()),
            "fraudDetection"),
    SCRIPT("script", "话术", Attribute.SCRIPT_STATUS.getName(), Arrays.asList(
            Attribute.SCRIPTS.getName(),
            Attribute.SCRIPT_RESULT.getName()),
            "scriptConsistency"),
    SCENARIO("scenario", "场景分析",  Attribute.SCENARIO_STATUS.getName(), Arrays.asList(
            Attribute.SCENARIO_RESULT.getName(),
            Attribute.SCENARIO_CONSISTENCY.getName(),
            Attribute.SCENARIO_SCORE.getName()),
            Arrays.asList(
                    "sceneAnalysis",
                    "sceneRecognition")),
    VPR("vpr", "声纹识别", Attribute.VPR_STATUS.getName(), Arrays.asList(
            Attribute.VPR_RESULT.getName(),
            Attribute.VPR_DIFF_PAIRS.getName(),
            Attribute.VPR_DIFF_IDS.getName(),
            Attribute.VPR_ERROR.getName())),
    VFD("vfd", "声音鉴伪", Attribute.VFD_STATUS.getName(), Arrays.asList(
            Attribute.VFD_RESULT.getName(),
            Attribute.ASR_DURATION.getName(),
            Attribute.VFD_SCORE.getName()),
            "forgeryDetection"),
    DIFY("dify", "DIFY", Attribute.DIFY_STATUS.getName(), Attribute.DIFY_RESULT.getName(),
            "difyIntegration"),
    DYHTTP("dyHttp", "http模型", Attribute.DYHTTP_STATUS.getName(), Attribute.DYHTTP_RESULT.getName()),
    RT("rt","融通反诈+融通场景一致性",Attribute.RT_STATUS.getName(), Arrays.asList(
            Attribute.RT_RESULT_CODE.getName(),
            Attribute.RT_ABILITY_TYPE.getName(),
            Attribute.RT_FIRST_LABEL.getName(),
            Attribute.RT_SECOND_LABEL.getName(),
            Attribute.RT_THIRD_LABEL.getName(),
            Attribute.RT_SVC_TYPE.getName()),
            "fusionFraudSceneCheck"),
    HUAWEI_QC("huaweiQC","华为质检",Attribute.HUAWEI_QC_STATUS.getName(), Arrays.asList(
            Attribute.HUAWEI_QC_RESULT.getName(),
            Attribute.HUAWEI_QC_CONSISTENCY.getName(),
            Attribute.HUAWEI_QC_DETAIL.getName())),

    COLLECTION("collection","催收",Attribute.COLLECTION_STATUS.getName(), Arrays.asList(
            Attribute.COLLECTION_RESULT.getName(),
            Attribute.COLLECTION_SCORE.getName()),
            "collectionModel"),
    TEXT_SIMILARITY("text_similarity","文本相似度",Attribute.TEXT_SIMILARITY_STATUS.getName(), Arrays.asList(
            Attribute.TEXT_SIMILARITY_RESULT.getName(),
            Attribute.TEXT_SIMILARITY_SCORE.getName()),
            "contentSimilarityAnalyse"),

    VP("vp", "声纹", Attribute.VP_STATUS.getName(), Arrays.asList(
            Attribute.VP_SCORE.getName(),
            Attribute.VP_TOPK_TAGS.getName()),
            Arrays.asList(
                    "voiceComparison",
                    "voiceAnalysis")),
    KEYWORD_RS("keyword_rs", "关键词高危风险", Attribute.KEYWORD_RS_STATUS.getName(), Attribute.KEYWORD_RS_RESULT.getName(),
            "forbiddenWordCheck"),
    IDENTITY("identity", "身份比对", Attribute.IDENTITY_STATUS.getName(), Arrays.asList(
            Attribute.IDENTITY_CONSISTENCY.getName(),
            Attribute.IDENTITY_SCORE.getName()),
            "identityComparison"),
    EMOTION("emotion", "情绪识别", Attribute.EMOTION_STATUS.getName(), Attribute.EMOTION_RESULT.getName(),
            "emotionAnalysis"),
    AIGS("aigs", "AIGS", Attribute.AIGS_STATUS.getName(), Arrays.asList(
            Attribute.AIGS_CONTENT.getName(),
            Attribute.AIGS_REASON.getName(),
            Attribute.AIGS_CONFIDENCE.getName(),
            Attribute.AIGS_IS_FRAUD.getName()),
            "aigsAnalysis"),
    DEEPSEEK("deepSeek", "DeepSeek", Attribute.DEEPSEEK_STATUS.getName(), Arrays.asList(
            Attribute.DEEPSEEK_RESULT.getName(),
            Attribute.DEEPSEEK_REASON.getName(),
            Attribute.DEEPSEEK_EXTRA_DATA.getName()),
            "deepSeekAnalysis"),
    WORD_GROUP("wordGroup", "关键词组合", Attribute.WORD_GROUP_STATUS.getName(), Attribute.WORD_GROUP_RESULT.getName(),
            "wordAnalysis"),
    QWEN("qwen", "通义千问", Attribute.QWEN_STATUS.getName(), Attribute.QWEN_RESULT.getName(),
            "qwenInference"),

    RT_FRAUD_REVIEW("rtFraudReview","融通反诈复核",Attribute.RT_FRAUD_REVIEW_STATUS.getName(), Arrays.asList(
            Attribute.RT_FRAUD_REVIEW_RESULT_CODE.getName(),
            Attribute.RT_FRAUD_REVIEW_ABILITY_TYPE.getName(),
            Attribute.RT_FRAUD_REVIEW_FIRST_LABEL.getName(),
            Attribute.RT_FRAUD_REVIEW_SECOND_LABEL.getName(),
            Attribute.RT_FRAUD_REVIEW_THIRD_LABEL.getName()),
            "fusionFraudReview"),

    MARKET_CALL("marketCall", "营销外呼", Attribute.MARKET_CALL_STATUS.getName(), Attribute.MARKET_CALL_RESULT.getName(),
            "marketingCall"),
    WORD_REGEX("wordRegex", "关键词正则表达", Attribute.WORD_REGEX_STATUS.getName(), Attribute.WORD_REGEX_RESULT.getName(),
            "regexWordMatching"),
    DEAL("deal", "成交分类", Attribute.DEAL_STATUS.getName(), Arrays.asList(
            Attribute.DEAL_LABEL.getName(),
            Attribute.DEAL_NEED_REVIEW.getName()),
            "dealClass"),
    RAG("rag", "RAG", Attribute.RAG_STATUS.getName(), Arrays.asList(
            Attribute.RAG_RESULTS.getName(),
            Attribute.RAG_COUNT.getName()),
            "ragSearch"),

    KEYWORD_ANALYSIS_LT("keywordAnalysisLT", "低阈值关键词分析", Attribute.KEYWORD_ANALYSIS_LT_STATUS.getName(), Arrays.asList(
            Attribute.KEYWORD_ANALYSIS_LT_RESULT.getName()),
            "fraudDetectionLT"),
    BGE("bge", "BGE分类器", Attribute.BGE_STATUS.getName(), Arrays.asList(
            Attribute.BGE_RULE_NAME.getName(),
            Attribute.BGE_RULE_EXPRESSION.getName(),
            Attribute.BGE_RULE_OUTPUT_LABEL.getName(),
            Attribute.BGE_RULE_PRIORITY.getName()),
            "bgeClassifier"),

    CQ_ANTI("cqAnti", "重庆行业卡反诈", Attribute.CQ_ANTI_STATUS.getName(), Arrays.asList(
            Attribute.CQ_ANTI_FILTER_TYPE.getName(),
            Attribute.CQ_ANTI_RISK_LEVEL.getName(),
            Attribute.CQ_ANTI_RESULT.getName()),
            "cqFraudCheck"),

    CQ_AGENT("cqAgent", "重庆行业卡agent复核", Attribute.CQ_AGENT_STATUS.getName(), Arrays.asList(
            Attribute.CQ_AGENT_IS_VIOLATION.getName(),
            Attribute.CQ_AGENT_IS_FRAUD.getName(),
            Attribute.CQ_AGENT_RISK_LEVEL.getName(),
            Attribute.CQ_AGENT_SCENE_CATEGORY.getName(),
            Attribute.CQ_AGENT_SCENE_SUBTYPE.getName(),
            Attribute.CQ_AGENT_CONFIDENCE.getName(),
            Attribute.CQ_AGENT_SOURCE.getName(),
            Attribute.CQ_AGENT_DETAIL.getName()),
            "cqAgentReview"),

    ;

    private String name;
    private String message;
    private String status;
    private String attribute;

    private List<String> attributeList;

    private String atomicSwitch;
    private List<String> atomicSwitchList;

    AIModelEnum(String name, String message, String status, String attribute){
        this.name = name;
        this.message = message;
        this.status = status;
        this.attribute = attribute;
    }

    AIModelEnum(String name, String message, String status, List<String> attributes){
        this.name = name;
        this.message = message;
        this.status = status;
        this.attributeList = attributes;
    }

    AIModelEnum(String name, String message, String status, String attribute, String atomicSwitch){
        this.name = name;
        this.message = message;
        this.status = status;
        this.attribute = attribute;
        this.atomicSwitch = atomicSwitch;
    }

    AIModelEnum(String name, String message, String status, List<String> attributes, String atomicSwitch){
        this.name = name;
        this.message = message;
        this.status = status;
        this.attributeList = attributes;
        this.atomicSwitch = atomicSwitch;
    }

    AIModelEnum(String name, String message, String status, List<String> attributes, List<String> atomicSwitchList){
        this.name = name;
        this.message = message;
        this.status = status;
        this.attributeList = attributes;
        this.atomicSwitchList = atomicSwitchList;
    }


    public String getName(){
        return name;
    }

    public String getMessage(){
        return message;
    }

    public String getStatus() {
        return status;
    }

    public String getAttribute() {
        return attribute;
    }

    public List<String> getAttributeList(){
        return attributeList;
    }

    public List<String> getUnifiedAtomicSwitchList() {
        if (atomicSwitch != null && atomicSwitchList != null) {
            throw new IllegalStateException(name + " 同时配置了 atomicSwitch 和 atomicSwitchList");
        }
        if (atomicSwitch == null && (atomicSwitchList == null || atomicSwitchList.isEmpty())) {
            return Collections.emptyList();
        }

        if (atomicSwitchList != null) {
            return atomicSwitchList;
        }
        return List.of(atomicSwitch);
    }

    public static AIModelEnum getEnumByName(String name){
        for(AIModelEnum modelEnum : AIModelEnum.values()){
            if(modelEnum.getName().equalsIgnoreCase(name)){
                return modelEnum;
            }
        }
        return null;
    }

    public static List<String> getAttributes(String name){
        List<String> attributes = new ArrayList<>();
        AIModelEnum aiModelEnum = null;
        for(AIModelEnum modelEnum : AIModelEnum.values()){
            if(modelEnum.getName().equalsIgnoreCase(name)){
                aiModelEnum =  modelEnum;
                break;
            }
        }
        String attribute = aiModelEnum.getAttribute();
        if(StringUtils.hasLength(attribute)){
            attributes.add(attribute);
        }
        if(!CollectionUtils.isEmpty(aiModelEnum.getAttributeList())){
            attributes.addAll(aiModelEnum.getAttributeList());
        }

        return attributes;
    }

    public static String getModelStatus(String model){
        return model + "_status";
    }

    public static String getModelParameter(String model, String parameter){
        return model + "_" + parameter;
    }

    public static String[] getModelsByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new String[0];
        }

        Set<String> keySet = new HashSet<>(keys);

        return Arrays.stream(AIModelEnum.values())
                .filter(e -> e.getUnifiedAtomicSwitchList().stream().anyMatch(keySet::contains))
                .map(AIModelEnum::getName)
                .distinct()
                .toArray(String[]::new);
    }
}
