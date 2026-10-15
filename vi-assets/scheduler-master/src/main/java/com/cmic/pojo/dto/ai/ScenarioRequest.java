package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ScenarioRequest {
    private String jobId;
    private String text;
    private String preset;
}
