package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class KeywordAnalysisResponse implements Serializable {

    @Data
    @NoArgsConstructor
    public static class KeywordResult {
        private String scene;
        private String result;
        private List<String> keywords;
//        private String threshLevel;  // 0:低阈值 1:高阈值
    }

    private String jobId;
    private List<KeywordResult> results;
    private String threshLevel;  // 0:低阈值 1:高阈值
}
