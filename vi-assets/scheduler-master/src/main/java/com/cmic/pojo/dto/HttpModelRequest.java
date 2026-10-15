package com.cmic.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class HttpModelRequest {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @NotBlank(message = "model cannot be empty")
    public String model;

    @NotBlank(message = "desc cannot be empty")
    public String desc;

    @NotBlank(message = "httpUrl cannot be empty")
    private String httpUrl;

    private List<String> requestParameters;

    private List<String> responseParameters;

    @NotNull(message = "aiTypeId cannot be null")
    private Long aiTypeId;

    private Map<String, Object> authentication;
}
