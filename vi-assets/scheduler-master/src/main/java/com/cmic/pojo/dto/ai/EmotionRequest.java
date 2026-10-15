package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmotionRequest {
    private String jobId;
    private String fileUrl;
    private Integer setChannel;
    private List texts;
    private List channels;
    private List timestamps;
}
