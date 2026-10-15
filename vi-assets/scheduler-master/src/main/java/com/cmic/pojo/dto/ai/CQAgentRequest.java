package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CQAgentRequest {
    private String jobId;
    private String text;
    private Boolean useTools;
    private Boolean useFast;
}
