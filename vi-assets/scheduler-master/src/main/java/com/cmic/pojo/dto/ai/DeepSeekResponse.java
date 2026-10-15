package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class DeepSeekResponse implements Serializable {
    private String jobId;
    private String result;
    private String reason;
    private String extraData;
}
