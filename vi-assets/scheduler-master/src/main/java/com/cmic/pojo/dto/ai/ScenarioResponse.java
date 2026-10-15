package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScenarioResponse {
    private String jobId;
    private String result;
    private String consistency;
    private Float score;
}
