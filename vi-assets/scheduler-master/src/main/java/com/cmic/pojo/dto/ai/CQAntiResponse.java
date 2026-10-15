package com.cmic.pojo.dto.ai;

import lombok.Data;

import java.util.List;

@Data
public class CQAntiResponse {
    private String jobId;
    private Integer filterType;
    private Integer riskLevel;
    private List<CQResult> result;

    @Data
    public static class CQResult {
        private Integer secondaryFilterType;
        private String label;
        private Double percent;
    }
}
