package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RAGRequest {
    private String jobId;
    private String text;
    private Integer topK;
    private String kbType;
}
