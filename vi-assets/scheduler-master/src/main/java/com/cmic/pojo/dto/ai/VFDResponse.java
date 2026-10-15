package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
@Data
@NoArgsConstructor
public class VFDResponse implements Serializable {
    private String jobId;
    /**
     * 0=疑似合成，1=人声，-1=处理失败
     */
    private String result;
    private float duration;
    private Float score;
}
