package com.cmic.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiTypeRequest {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @NotBlank(message = "name cannot be empty")
    private String name;
    @NotBlank(message = "nickName cannot be empty")
    private String nickName;
    @NotBlank(message = "comment cannot be empty")
    private String comment;
}
