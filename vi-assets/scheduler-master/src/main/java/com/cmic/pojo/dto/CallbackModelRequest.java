package com.cmic.pojo.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class CallbackModelRequest implements Serializable {
    //模型名称、code、message、status、开始时间、结束时间

    public static int NO_PROCESS = 0;
    public static int FAIL = 1;
    public static int SUCCESS = 2;

    private String name;
    private String code;
    private String message;
    /**
     * 模型执行状态 0-未执行 1-执行失败 2-执行成功
     */
    private Integer status;
    /**
     * 发送到模型队列时间
     */
    private Long startTime;
    /**
     * 接收到模型返回时间
     */
    private Long endTime;
    /**
     * 质检结果 0-异常 1-正常
     */
    private String modelQcResult;
    /**
     * 质检异常风险等级 1-高风险 2-中风险 3-低风险
     */
    private String riskLevel;
}
