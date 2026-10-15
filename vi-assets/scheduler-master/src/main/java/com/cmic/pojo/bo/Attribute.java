package com.cmic.pojo.bo;

public enum Attribute {
    ASR_STATUS("asrStatus", "ASR 状态"),
    ASR_TEXT("asrText", "ASR文本"),
    ASR_DURATION("audioDuration", "语音时长"),
    ASR_INFO("asrInfo", "ASR分片"),

    KEYWORD_STATUS("keywordStatus", "关键词 状态"),
    KEYWORD_MATCHED("matchedWord", "匹配上的关键词列表"),

    SCRIPT_STATUS("scriptStatus", "话术状态"),
    SCRIPT_RESULT("scriptResult", "话术匹配结果"),
    SCRIPTS("scriptsIds", "命中话术 ID"),

    KEYWORD_ANALYSIS_STATUS("keywordAnalysisStatus", "关键词反诈服务返回状态"),
    KEYWORD_ANALYSIS_RESULT("keywordAnalysisResult", "关键词反诈服务返回结果"),

    SCENARIO_STATUS("scenarioStatus", "场景分析状态"),
    SCENARIO_RESULT("scenarioResult", "场景分析结果"),
    SCENARIO_CONSISTENCY("scenarioConsistency", "场景一致性结果"),
    SCENARIO_SCORE("scenarioScore", "场景相似度分数"),

    VPR_STATUS("vprStatus", "声纹比对状态"),
    VPR_RESULT("vprResult","声纹比对结果"),
    VPR_DIFF_PAIRS("differentPairs","声纹比对结果不正常的callId对数组"),
    VPR_DIFF_IDS("differentIds","differentPairs元素的去重集合"),
    VPR_ERROR("vprError","声纹比对异常详情"),

    VFD_STATUS("vfdStatus", "声音鉴伪状态"),
    VFD_RESULT("vfdResult", "声音鉴伪结果"),
    VFD_SCORE("vfdScore", "声音鉴伪相似度分数"),

    DSZ_STATUS("desensitizationStatus", "文本脱敏状态"),
    DSZ_RESULT("desensitizationResult", "文本脱敏结果"),

    DIFY_STATUS("difyStatus", "DIFY标签状态"),
    DIFY_RESULT("difyResult", "DIFY 结果"),

    DYHTTP_STATUS("httpModelStatus", "http模型标签状态"),
    DYHTTP_RESULT("httpModelResult", "http模型结果"),

    HUAWEI_QC_STATUS("huaweiQCStatus", "华为质检状态"),
    HUAWEI_QC_RESULT("huaweiQCResult", "华为质检结果"),
    HUAWEI_QC_DETAIL("huaweiQCDetail", "华为质检结果详细"),
    HUAWEI_QC_CONSISTENCY("huaweiQCConsistency", "华为质检场景一致性检测结果"),

    RT_STATUS("rtStatus", "融通质检状态"),
    RT_RESULT_CODE("rtResultCode", "反馈码"),
    RT_ABILITY_TYPE("rtAbilityType", "能力字段"),
    RT_FIRST_LABEL("rtFirstLabel", "一级标签"),
    RT_SECOND_LABEL("rtSecondLabel", "二级标签"),
    RT_THIRD_LABEL("rtThirdLabel", "三级标签"),
    RT_SVC_TYPE("svcType", "业务类型编号"),

    COLLECTION_STATUS("cmic_internal_collection_status", "催收状态"),
    COLLECTION_RESULT("cmic_internal_collection_result", "催收返回结果"),
    COLLECTION_SCORE("collectionScore", "催收相似度分数"),

    TEXT_SIMILARITY_STATUS("content_similarity_qc_status", "文本相似度状态"),
    TEXT_SIMILARITY_RESULT("content_similarity_qc_result", "文本相似度返回结果"),
    TEXT_SIMILARITY_SCORE("contentSimilarityScore", "文本相似度分数"),

    VP_STATUS("vpStatus", "声纹比对状态"),
    VP_SCORE("vpScore","声纹相似度分数"),
    VP_TOPK_TAGS("vpTopKTags","声纹名和相似度分数的字典列表"),

    KEYWORD_RS_STATUS("keywordRSStatus", "关键词高危风险状态"),
    KEYWORD_RS_RESULT("keywordRSResult", "关键词高危风险结果列表"),

    IDENTITY_STATUS("identityStatus", "身份比对状态"),
    IDENTITY_CONSISTENCY("identityConsistency", "身份比对一致性结果"),
    IDENTITY_SCORE("identityScore", "身份比对相似度分数"),

    EMOTION_STATUS("emotionStatus", "情绪识别状态"),
    EMOTION_RESULT("emotionResult", "情绪识别结果"),

    AIGS_STATUS("aigsStatus", "AIGS状态"),
    AIGS_CONTENT("aigsContent", "通话内容"),
    AIGS_REASON("aigsReason", "原因"),
    AIGS_CONFIDENCE("aigsConfidence", "置信度"),
    AIGS_IS_FRAUD("aigsIsFraud", "是否涉诈"),

    DEEPSEEK_PROMPT("deepSeekPrompt", "DeepSeek提示词"),
    DEEPSEEK_STATUS("deepSeekStatus", "DeepSeek状态"),
    DEEPSEEK_RESULT("deepSeekResult", "结果"),
    DEEPSEEK_REASON("deepSeekReason", "原因"),
    DEEPSEEK_EXTRA_DATA("deepSeekExtraData", "额外字段"),

    WORD_GROUP_STATUS("wordGroupStatus", "关键词组合状态"),
    WORD_GROUP_RESULT("wordGroupResult", "关键词组合命中结果"),

    QWEN_PROMPT("qwenPrompt", "Qwen提示词"),
    QWEN_STATUS("qwenStatus", "Qwen状态"),
    QWEN_RESULT("qwenResult", "结果"),
    QWEN_MODEL_TYPE("qwenModelType", "Qwen调用模型"),


    RT_FRAUD_REVIEW_STATUS("rtFraudReviewStatus", "融通涉诈复核状态"),
    RT_FRAUD_REVIEW_RESULT_CODE("rtFraudReviewResultCode", "反馈码"),
    RT_FRAUD_REVIEW_ABILITY_TYPE("rtFraudReviewAbilityType", "能力字段"),
    RT_FRAUD_REVIEW_FIRST_LABEL("rtFraudReviewFirstLabel", "一级标签"),
    RT_FRAUD_REVIEW_SECOND_LABEL("rtFraudReviewSecondLabel", "二级标签"),
    RT_FRAUD_REVIEW_THIRD_LABEL("rtFraudReviewThirdLabel", "三级标签"),

    MARKET_CALL_STATUS("marketCallStatus", "营销外呼状态"),
    MARKET_CALL_RESULT("marketCallResult", "营销外呼结果"),

    WORD_REGEX_STATUS("wordRegexStatus", "关键词正则表达状态"),
    WORD_REGEX_RESULT("wordRegexResult", "关键词正则表达命中结果"),

    DEAL_STATUS("dealStatus", "成交分类状态"),
    DEAL_LABEL("dealLabel", "成交分类结果标签"),
    DEAL_NEED_REVIEW("dealNeedReview", "是否需要人工复核"),

    RAG_STATUS("ragStatus", "RAG状态"),
    RAG_RESULTS("ragResults", "检索结果数组"),
    RAG_COUNT("ragCount", "结果数量"),

    KEYWORD_ANALYSIS_LT_STATUS("keywordAnalysisLTStatus", "低阈值关键词反诈返回状态"),
    KEYWORD_ANALYSIS_LT_RESULT("keywordAnalysisLTResult", "低阈值关键词反诈返回结果"),

    BGE_STATUS("bgeStatus", "BGE分类器状态"),
    BGE_RULE_NAME("bgeRuleName", "命中规则名称"),
    BGE_RULE_EXPRESSION("bgeExpression", "规则表达式"),
    BGE_RULE_OUTPUT_LABEL("bgeOutputLabel", "规则输出的类别"),
    BGE_RULE_PRIORITY("bgePriority", "规则优先级"),

    CQ_ANTI_STATUS("cqAntiStatus", "重庆行业卡反诈状态"),
    CQ_ANTI_FILTER_TYPE("cqAntiFilterType", "一级标签类型"),
    CQ_ANTI_RISK_LEVEL("cqAntiRiskLevel", "风险等级"),
    CQ_ANTI_RESULT("cqAntiResult", "二级标签识别结果列表"),


    CQ_AGENT_STATUS("cqAgentStatus", "重庆行业卡agent复核状态"),
    CQ_AGENT_IS_VIOLATION("cqAgentIsViolation", "是否违规"),
    CQ_AGENT_IS_FRAUD("cqAgentIsFraud", "是否涉诈"),
    CQ_AGENT_RISK_LEVEL("cqAgentRiskLevel", "风险等级"),
    CQ_AGENT_SCENE_CATEGORY("cqAgentSceneCategory", "违规场景大类"),
    CQ_AGENT_SCENE_SUBTYPE("cqAgentSceneSubtype", "违规场景子类"),
    CQ_AGENT_CONFIDENCE("cqAgentConfidence", "置信度"),
    CQ_AGENT_SOURCE("cqAgentSource", "结果来源"),
    CQ_AGENT_DETAIL("cqAgentDetail", "详情"),

    ERROR_MESSAGE("errorMessage", "错误信息"),
    STATUS("status", "状态"),
    FINISH("finish", "是否完成"),
    ;

    private String name;
    private String message;
    Attribute(String name, String message){
        this.name = name;
        this.message = message;
    }

    public String getName(){
        return name;
    }

    public String getMessage(){
        return message;
    }


    public static Attribute getEnumByName(String name){
        for(Attribute attribute : Attribute.values()){
            if(attribute.getName().equalsIgnoreCase(name)){
                return attribute;
            }
        }
        return Attribute.ASR_STATUS;
    }
}
