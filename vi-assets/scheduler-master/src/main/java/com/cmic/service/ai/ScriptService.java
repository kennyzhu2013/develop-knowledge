package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.ScriptRequest;
import com.cmic.pojo.dto.ai.ScriptResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScriptService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    public void sendToMq(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        Map<String, Object> attributes = task.getAttributes();

        List<Map<String, Object>> scriptList = (List<Map<String, Object>>) attributes.get("scripts");
        List<ScriptRequest.ScriptInfo> scriptInfos = scriptList.stream()
                .map(map -> ScriptRequest.ScriptInfo.builder()
                        .scriptId(map.get("scriptId").toString())
                        .scriptTemplate(map.get("text").toString())
                        .build())
                .collect(Collectors.toList());

        ScriptRequest request = ScriptRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .scriptInfos(scriptInfos)
                .build();

        if(attributes.containsKey("threshold")){
            request.setThreshold(Float.parseFloat(attributes.get("threshold").toString()));
        }
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.SCRIPT.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to script", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToScript(subTask.getLocation(), request, task.getPriority());
    }

    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse){
        ScriptResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), ScriptResponse.class);
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        log.info("TaskId:{}, jobId:{}, processId:{}, Script response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.SCRIPT_STATUS.getName(), true);
            responseMap.put(Attribute.SCRIPT_RESULT.getName(), response.getTargetFlag());
            responseMap.put(Attribute.SCRIPTS.getName(), response.getTargetIds());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.SCRIPT_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.SCRIPT.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.SCRIPT_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.SCRIPT.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.SCRIPT_RESPONSE, task, subTask);
    }
}
