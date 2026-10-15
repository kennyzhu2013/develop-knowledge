package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class DSZResponse implements Serializable {
    private String jobId;

    private String text;
}
