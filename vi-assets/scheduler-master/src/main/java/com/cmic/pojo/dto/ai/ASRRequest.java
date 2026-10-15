package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ASRRequest implements Serializable {
    private String jobId;
    private String fileUrl;
    private String type;

    private String language;
    private Boolean emotionTag;
}
