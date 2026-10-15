package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QwenRequest {
    private String jobId;
    private String text;
    private String prompt;
    private String modelType;
}
