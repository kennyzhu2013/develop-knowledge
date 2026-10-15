package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.VFDRequest;
import com.cmic.pojo.dto.ai.VFDResponse;
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
public class VFDService implements IMqService{
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

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        String url = (String) subTaskAttributes.get("url");
        VFDRequest request = VFDRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .fileUrl(url)
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.VFD.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to voice forgery detection", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToVFD(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        VFDResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), VFDResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Voice forgery detection response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
            float duration = response.getDuration();
            int vfdDuration = (int) Math.ceil(duration);
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.VFD_STATUS.getName(), true);
            responseMap.put(Attribute.VFD_RESULT.getName(), response.getResult());
            responseMap.put(Attribute.ASR_DURATION.getName(), vfdDuration);
            responseMap.put(Attribute.VFD_SCORE.getName(), response.getScore());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.VFD_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.VFD.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.VFD_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.VFD.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.VFD_RESPONSE, task, subTask);
    }
}
