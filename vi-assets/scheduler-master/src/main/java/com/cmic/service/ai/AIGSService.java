package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.AIGSRequest;
import com.cmic.pojo.dto.ai.AIGSResponse;
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
public class AIGSService implements IMqService {
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


        AIGSRequest request = AIGSRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .fileUrl((String) subTaskAttributes.get("url"))
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.AIGS.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to AIGS", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToAIGS(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        AIGSResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), AIGSResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, AIGS response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.AIGS_STATUS.getName(), true);
            responseMap.put(Attribute.AIGS_CONTENT.getName(), response.getContent());
            responseMap.put(Attribute.AIGS_REASON.getName(), response.getReason());
            responseMap.put(Attribute.AIGS_CONFIDENCE.getName(), response.getConfidence());
            responseMap.put(Attribute.AIGS_IS_FRAUD.getName(), response.getIsFraud());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.AIGS_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.AIGS.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.AIGS_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.AIGS.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.AIGS_RESPONSE, task, subTask);
    }
}
