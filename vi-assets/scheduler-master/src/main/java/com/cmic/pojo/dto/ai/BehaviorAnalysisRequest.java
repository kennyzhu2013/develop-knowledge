package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class BehaviorAnalysisRequest implements Serializable {
    private String jobId;
}
