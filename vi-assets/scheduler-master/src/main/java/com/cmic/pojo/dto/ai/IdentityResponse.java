package com.cmic.pojo.dto.ai;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class IdentityResponse implements Serializable {
    private String jobId;
    private String consistency;
    private Float score;
}
