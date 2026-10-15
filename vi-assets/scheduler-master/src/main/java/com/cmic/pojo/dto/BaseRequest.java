package com.cmic.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class BaseRequest {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @Pattern(regexp = "0|10", message = "illegal priority")
    public String priority = "0";

    /**
     * 流程规则 ID
     */
    @NotBlank(message = "processId cannot be null")
    public String processId;

    /**
     * 总任务执行结果回调接口URL
     */
    public String taskCallbackUrl;

    /**
     * 子任务执行结果回调接口URL
     */
    public String subTaskCallbackUrl;

    /**
     * 额外的参数
     */
    public Map<String, Object> parameter;
}
