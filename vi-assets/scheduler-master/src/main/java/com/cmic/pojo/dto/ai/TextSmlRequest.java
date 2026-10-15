package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@Builder
public class TextSmlRequest {
    private String jobId;
    @Builder.Default
    private int wordNum = 30;
    @Builder.Default
    private String headtail = "0";
    @Builder.Default
    private float textThreshold = 0.6F;
    private List<Group> groups;

    @Data
    @NoArgsConstructor
    public static class Group {
        private String callId;
        private String text;
    }
}
