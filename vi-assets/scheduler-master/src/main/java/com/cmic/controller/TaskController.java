package com.cmic.controller;

import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.TaskRequest;
import com.cmic.pojo.entity.Process;
import com.cmic.service.ProcessService;
import com.cmic.service.task.TaskService;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("v1/front")
public class TaskController {

    @Resource
    private TaskService taskService;
    @Autowired
    private Validator validator;
    @Autowired
    private ProcessService processService;

    @PostMapping("/task/create")
    @ResponseBody
    public BaseResponse audioCreate(@RequestBody @Validated TaskRequest taskRequest) {
        String requestId = taskRequest.getRequestId();
        if(taskService.isRepeat(requestId)){
            log.info("RequestId:{} is repeat", requestId);
            return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "RequestId:"+requestId+" is repeat");
        }

        String processId = taskRequest.getProcessId();
        Process process = processService.findEnableByProcessId(processId);
        if(process == null){
            log.info("ProcessId:{} does not exist", processId);
            return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "ProcessId:"+processId+" does not exist");
        }

        List<Map<String, Object>> data = taskRequest.getData();
        boolean hasError = data.stream()
                .anyMatch(map ->
                        !map.containsKey("dataId") ||
                        map.get("dataId") == null ||
                        map.get("dataId").toString().trim().isEmpty()
                );
        if(hasError){
            return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Check data list, dataId does not exist or dataId is null");
        }

        return taskService.createTask(taskRequest);
    }


}
