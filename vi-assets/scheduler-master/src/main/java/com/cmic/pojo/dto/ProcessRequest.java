package com.cmic.pojo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProcessRequest {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @NotBlank(message = "processName cannot be empty")
    public String processName;

    @NotBlank(message = "xmlFileName cannot be null")
    private String xmlFileName;

    @Min(value = 1, message = "Timeout must be at least 1")
    @Max(value = 60, message = "Timeout must not exceed 60")
    private Integer timeout = 10;  //分钟

    @NotBlank(message = "aiModels cannot be null")
    private String aiModels;
}
