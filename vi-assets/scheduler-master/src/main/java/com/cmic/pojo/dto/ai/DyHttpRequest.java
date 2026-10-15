package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
public class DyHttpRequest extends MQBaseDto{
    private String model;
    private String version;
    private String url;
    private Map<String, Object> requestParameters;
    private List<String> responseParameters;
    private Map<String, Object> authorization;
    private String type;
}
