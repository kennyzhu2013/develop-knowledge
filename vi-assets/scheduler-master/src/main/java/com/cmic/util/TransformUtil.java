package com.cmic.util;

import com.cmic.pojo.dto.TaskRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

public class TransformUtil {

    public static Task transformRequestToTask(Long taskId, TaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return Task.builder()
                .id(taskId)
                .requestId(request.getRequestId())
                .processId(request.getProcessId())
                .priority(Integer.parseInt(request.getPriority()))
                .attributes(request.getParameter())
                .createTime(now)
                .updateTime(now)
                .needTaskCallback(request.isNeedTaskCallback())
                .needSubTaskCallback(request.isNeedSubTaskCallback())
                .transactionId(request.getTransactionId())
                .jobCount(request.getData().size())
                .stream(request.isStream())
                .build();
    }

    public static SubTask transformRequestToSubTask(Long taskId, Map<String, Object> map){
        long jobId = SnowflakeUtils.generateId();
        LocalDateTime now = LocalDateTime.now();
        int location = map.get("location") == null?1: Integer.parseInt(map.get("location").toString());
        return SubTask.builder()
                .id(jobId)
                .taskId(taskId)
                .attributes(map)
                .dataId((String) map.get("dataId"))
                .location(location)
                .createTime(now)
                .updateTime(now)
                .build();
    }
}
