package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIGSRequest {
    private String jobId;
    private String fileUrl;
}
