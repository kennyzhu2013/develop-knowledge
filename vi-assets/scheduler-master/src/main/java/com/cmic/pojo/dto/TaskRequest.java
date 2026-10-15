package com.cmic.pojo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class TaskRequest implements Serializable {
    /**
     * 唯一识别一次任务创建请求
     */
    @NotBlank(message = "requestId cannot be empty")
    public String requestId;

    @Pattern(regexp = "([0-9]|10)", message = "illegal priority")
    public String priority = "0";

    /**
     * 流程规则 ID
     */
    @NotBlank(message = "processId cannot be null")
    public String processId;

    public boolean needTaskCallback = true;

    public boolean needSubTaskCallback = true;

    /**
     * 额外的参数
     */
    public Map<String, Object> parameter;

    /**
     * 子任务数据
     */
    @Valid
    @NotEmpty(message = "data should not be empty")
    @Size(max=100000, message = "max length of data is 100000")
    public List<Map<String, Object>> data;

    @NotNull(message = "transactionId cannot be null")
    @Digits(integer = 19, fraction = 0, message = "TransactionId; must be a valid long value")
    public Long transactionId;

    /**
     * 是否实时流
     */
    @JsonProperty("isStream")
    private boolean stream;
}
