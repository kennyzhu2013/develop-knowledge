package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class MarketCallResponse {
    private String jobId;
    private String prediction;
}
