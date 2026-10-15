package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketCallRequest {
    private String jobId;
    private String text;
}
