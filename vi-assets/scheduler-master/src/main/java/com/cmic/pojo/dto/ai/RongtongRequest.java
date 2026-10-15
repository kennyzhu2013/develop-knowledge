package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RongtongRequest implements Serializable {
    private String jobId;
    private String svcType;
    private String contentText;
    private String remark;
}
