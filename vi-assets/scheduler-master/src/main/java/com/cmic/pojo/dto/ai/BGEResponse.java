package com.cmic.pojo.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class BGEResponse {
    private String jobId;
    private String name;
    private String expression;
    @JsonProperty("output_label")
    private String outputLabel;
    private String priority;
}
