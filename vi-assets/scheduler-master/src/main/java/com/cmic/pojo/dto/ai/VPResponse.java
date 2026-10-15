package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class VPResponse implements Serializable {
    private String jobId;
    private int type;
    private float score;
    private List<TopKTags> topKTags;

    @Data
    @NoArgsConstructor
    public static class TopKTags {
        private float score;
        private String tag;
    }
}
