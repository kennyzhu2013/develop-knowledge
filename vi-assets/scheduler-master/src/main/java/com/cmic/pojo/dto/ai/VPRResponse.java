package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class VPRResponse implements Serializable {
    private String jobId;
    /**
     * 对比状态：
     * 1 - 模型认为全部为同一人
     * 0 – 模型认为非同一人
     */
    private String result;

    private List<List<String>> differentPairs;
    private List<String> differentIds;
    private List<Error> error;

    @Data
    @NoArgsConstructor
    public static class Error {
        private String callId;
        private String message;
    }
}
