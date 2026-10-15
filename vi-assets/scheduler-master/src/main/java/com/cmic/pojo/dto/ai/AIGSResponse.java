package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class AIGSResponse implements Serializable {
    private String jobId;
    private String content;
    private String reason;
    private Float confidence;
    private Integer isFraud;
}
