package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class ASRResponse implements Serializable {
    private String jobId;
    private String text;
    private long audioDuration;
    private Info info;

    @Data
    @NoArgsConstructor
    public static class Info {
        private List sentences;
        private List channels;
        private List timestamps;
    }
}
