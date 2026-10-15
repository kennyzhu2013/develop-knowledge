package com.cmic.pojo.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class RAGResponse {
    private String jobId;
    private List<RagResult> results;
    private Integer count;
    private Integer processId;

    @Data
    public static class RagResult {
        private String id;
        private String text;
        private Float score;
        private RagMetadata metadata;
    }

    @Data
    public static class RagMetadata {
        private String note;
    }

}
