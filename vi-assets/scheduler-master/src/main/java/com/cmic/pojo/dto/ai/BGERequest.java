package com.cmic.pojo.dto.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
public class BGERequest {
    private String jobId;
    private String text;
    private List<String> classifiers;
    private List<Rule> rules;

    @Data
    @NoArgsConstructor
    public static class Rule {
        private String name;
        private String priority;
        private String expression;
        @JsonProperty("output_label")
        @JsonAlias({"output_label", "outputLabel"})
        private String outputLabel;
        @JsonProperty("is_default")
        @JsonAlias({"is_default", "isDefault"})
        private Boolean isDefault;
    }
}

