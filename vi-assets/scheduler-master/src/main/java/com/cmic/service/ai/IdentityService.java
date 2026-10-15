package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.IdentityRequest;
import com.cmic.pojo.dto.ai.IdentityResponse;
import com.cmic.pojo.dto.ai.ScenarioRequest;
import com.cmic.pojo.dto.ai.ScenarioResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class IdentityService implements IMqService {
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

        Map<String, Object> attributes = task.getAttributes();
        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        String asrText = (String) subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        String preset = (String) attributes.get("identityPreset");

        IdentityRequest request = IdentityRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .preset(preset)
                .build();
        if (subTaskAttributes.containsKey("isReverse")) {
            request.setIsReverseLeg((Boolean) subTaskAttributes.get("isReverse"));
        } else {
            request.setIsReverseLeg(false);
        }

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.IDENTITY.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to identity", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToIdentity(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        IdentityResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), IdentityResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Identity response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.IDENTITY_STATUS.getName(), true);
            responseMap.put(Attribute.IDENTITY_SCORE.getName(), response.getScore());
            responseMap.put(Attribute.IDENTITY_CONSISTENCY.getName(), response.getConsistency());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.IDENTITY_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.IDENTITY.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.IDENTITY_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.IDENTITY.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.IDENTITY_RESPONSE, task, subTask);
    }
}
