package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.DSZRequest;
import com.cmic.pojo.dto.ai.DSZResponse;
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
import java.util.Map;

@Slf4j
@Service
public class DSZService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    @Override
    public void sendToMq(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());
        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

        DSZRequest request = DSZRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .build();
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.DSZ.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to desensitization", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToDSZ(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        DSZResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), DSZResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Desensitization response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)){
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.DSZ_STATUS.getName(), true);
            responseMap.put(Attribute.ASR_TEXT.getName(), response.getText());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        }else {
            responseMap.put(Attribute.DSZ_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.DSZ.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.DSZ_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.DSZ.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.DSZ_RESPONSE, task, subTask);
    }
}
