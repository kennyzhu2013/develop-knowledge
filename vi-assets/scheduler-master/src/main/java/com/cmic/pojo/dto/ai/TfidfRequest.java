package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TfidfRequest {
    public static String START = "start";
    public static String BODY = "body";
    public static String END = "end";

    private String jobId;
    private String transactionId;
    private String type;
    private String text;
    private String callId;
}
