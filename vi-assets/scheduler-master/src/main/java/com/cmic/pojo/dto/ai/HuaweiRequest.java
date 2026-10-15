package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class HuaweiRequest extends MQBaseDto {
    private Integer checkType;
    private String appKey;
    private String serviceType;
    private String sessionId;
    private String recordUrl;
    private String recordText;
    private String industryCategory;
    private String industrySegmentation;
}
