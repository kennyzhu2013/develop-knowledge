package com.cmic.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskRedisBo {
    private List<Long> jobIds;
    private String processId;
    private int priority;
    private String taskCallBackUrl;
    public String subTaskCallBackUrl;
    private int jobSize;
    private int asrType;
    private List<KeywordBo> wordPackage;
    private List<ScriptBo> scripts;
    private String refSubTableName;
}


