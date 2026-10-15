package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
public class ScriptRequest extends MQBaseDto{
    private String text;
    @Builder.Default
    private float threshold = 0.85F;
    private List<ScriptInfo> scriptInfos;

    @Data
    @SuperBuilder
    public static class ScriptInfo {
        private String scriptId;
        private String scriptTemplate;
    }
}
