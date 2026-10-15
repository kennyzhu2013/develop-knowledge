package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.ASRRequest;
import com.cmic.pojo.dto.ai.ASRResponse;
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
public class JiutianASRService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;
    @Override
    public void sendToMq(MqEntityEvent event) throws Exception {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        ASRRequest asrRequest = ASRRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .fileUrl((String) subTaskAttributes.get("url"))
                .build();
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.ASR.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to jiutian asr", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToJTASR(subTask.getLocation(), asrRequest, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        ASRResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), ASRResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Jiutian asr response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)){
            String text = response.getText();
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.ASR_STATUS.getName(), true);
            responseMap.put(Attribute.ASR_TEXT.getName(), text);
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        }else {
            responseMap.put(Attribute.ASR_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.ASR.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.ASR_STATUS.getName(), false);
        }

        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.ASR.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.ASR_RESPONSE, task, subTask);
    }
}
