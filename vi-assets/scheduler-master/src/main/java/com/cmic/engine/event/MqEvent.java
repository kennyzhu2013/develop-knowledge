package com.cmic.engine.event;

import org.springframework.context.ApplicationEvent;

public class MqEvent extends ApplicationEvent {
    public static final String ASR_RESPONSE = "asr_resp";
    public static final String BA_RESPONSE = "behavior_resp";
    public static final String SCRIPT_RESPONSE = "script_resp";
    public static final String KEYWORD_RESPONSE = "keyword_resp";
    public static final String KEYWORD_ANALYSIS_RESPONSE = "keyword_analysis_resp";
    public static final String SCENARIO_RESPONSE = "scenario_resp";
    public static final String VPR_RESPONSE = "vpr_resp";
    public static final String VFD_RESPONSE = "vfd_resp";
    public static final String DSZ_RESPONSE = "desensitization_resp";
    public static final String DIFY_RESPONSE = "dify_resp";
    public static final String DYHTTP_RESPONSE = "http_model_resp";
    public static final String HUAWEI_RESPONSE = "huawei_qc_resp";
    public static final String RONGTONG_RESPONSE = "rt_resp";
    public static final String COLLECTION_RESPONSE = "cs_resp";
    public static final String TEXT_SIMILARITY_RESPONSE = "text_sml_resp";
    public static final String VP_RESPONSE = "vp_resp";
    public static final String INITIAL = "initial";
    public static final String KEYWORD_RS_RESPONSE = "keyword_rs_resp";
    public static final String IDENTITY_RESPONSE = "identity_resp";
    public static final String EMOTION_RESPONSE = "emotion_resp";
    public static final String AIGS_RESPONSE = "aigs_resp";
    public static final String DEEPSEEK_RESPONSE = "deepseek_resp";
    public static final String WORD_GROUP_RESPONSE = "word_group_resp";
    public static final String QWEN_RESPONSE = "qwen_resp";
    public static final String RT_FRAUD_REVIEW_RESPONSE = "rt_fraud_review_resp";
    public static final String MARKET_CALL_RESPONSE = "market_call_resp";
    public static final String WORD_REGEX_RESPONSE = "word_regex_resp";
    public static final String DEAL_RESPONSE = "deal_resp";
    public static final String RAG_RESPONSE = "rag_resp";
    public static final String KEYWORD_ANALYSIS_LT_RESPONSE = "keyword_analysis_lt_resp";

    public static final String SHARD_REND_RESP = "shard_send_resp";
    public static final String BGE_RESPONSE = "bge_resp";
    public static final String CHONGQING_ANTI_RESPONSE = "cq_anti_resp";
    public static final String CQ_AGENT_RESPONSE = "cq_agent_resp";

    public MqEvent(Object source) {
        super(source);
    }
    private String taskId;
    private String jobId;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public static String modelResp(String model){
        return model + "_resp";
    }
}
