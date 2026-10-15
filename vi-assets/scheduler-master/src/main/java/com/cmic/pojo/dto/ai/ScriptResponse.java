package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class ScriptResponse implements Serializable {
    private String jobId;
    /**
     * 1=存在高于相似度阈值的话术，
     * 0=无存在高于相似度阈值的话术
     */
    private String targetFlag;
    /**
     * 命中的话术id数组
     */
    private List<String> targetIds;
}
