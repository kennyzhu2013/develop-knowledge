package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class HuaweiResponse implements Serializable {
    private String jobId;
    /**
     * 违规检测结果：
     * pass: 质检无问题
     * fail: 质检任务异常
     * forbid:质检检查项不通过
     */
    private String generalDetectionResult;
    /**
     * 违规检测结果详情（如正常|涉诈|疑似涉诈|辱骂|骚扰等）
     */
    private String generalDetectionDetail;
    /**
     * 场景一致性检测结果（相符|不相符|无）
     */
    private String sceneConsistencyDetection;
}
