package com.cmic.pojo.dto.ai;

import lombok.Data;

@Data
public class DealResponse {
    private String jobId;
    private String label;
    private String needReview;
}
