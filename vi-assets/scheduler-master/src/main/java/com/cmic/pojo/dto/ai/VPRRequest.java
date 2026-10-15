package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
public class VPRRequest extends MQBaseDto {
    private List<Group> groups;

    @Data
    @NoArgsConstructor
    public static class Group {
        private String callId;
        private String fileUrl;
    }
}
