package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextRequest {
    private String jobId;
    private String text;
    @Builder.Default
    private String scene = "0";
    @Builder.Default
    private Boolean isReverseLeg = false;
    private String threshLevel;  // 0:低阈值 1:高阈值
}
