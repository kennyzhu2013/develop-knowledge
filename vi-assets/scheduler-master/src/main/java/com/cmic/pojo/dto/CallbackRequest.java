package com.cmic.pojo.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class CallbackRequest  implements Serializable {
    public int type; //1：子任务回调，2：主任务回调

    private String code;
    private String message;

    /*******主任务参数*********/
    public Integer finishRate;
    public List<CallbackRequest.ErrorMessage> error;

    private Integer failCount;
    private Integer total;

    @Data
    public static class ErrorMessage{
        private String id;
        private String message;
    }

    /*******子任务参数*********/
    /**
     * 通话id，对应任务创建接口urlList字段中每个元素的id
     */
    private String dataId;

    /**
     * 状态：
     * 0 - 失败  1- 成功  2-部分成功
     */
    private String status;

    private String errorMessage;

    private Long transactionId;
}
