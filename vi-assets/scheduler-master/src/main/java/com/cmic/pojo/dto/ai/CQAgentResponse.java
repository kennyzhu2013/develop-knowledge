package com.cmic.pojo.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class CQAgentResponse {
    private String jobId;
    private AgentResult result;

    @Data
    public static class AgentResult {
        private Boolean isViolation;
        private Boolean isFraud;
        private String riskLevel;
        private String sceneCategory;
        private String sceneSubtype;
        private String explanation;
        private List<String> detectedFeatures;
        private List<String> evidenceQuotes;
        private Float confidence;
        private String analysisThought;
        private String source;
    }
}
