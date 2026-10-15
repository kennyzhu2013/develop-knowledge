package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
public class DyHttpResponse implements Serializable {
    private String jobId;
    private Map<String ,Object> customizedResult;
}
