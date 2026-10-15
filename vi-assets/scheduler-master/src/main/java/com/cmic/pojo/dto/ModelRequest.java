package com.cmic.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
@Data
public class ModelRequest {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @NotBlank(message = "model cannot be empty")
    public String model;

    @NotBlank(message = "desc cannot be empty")
    public String desc;

    private List<String> requestParameters;

}
