package com.cmic.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRedisBo {
    private String taskId;
    private int location;
    private String callId;
    private String fileUrl;
    private String asrText;
    private Map<String, Object> attributes;
    //子任务推送 MQ 状态 0-未开始 1-推送成功 2-推送失败
    private byte status = 0;
    //0-失败，1-成功
    private byte finish = 0;
    private String errorMessage;
}
