package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.VPRRequest;
import com.cmic.pojo.dto.ai.VPRResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
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
public class VPRService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;

    @Autowired
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;
    @Override
    public void sendToMq(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        List<Map<String, Object>> groups = (List<Map<String, Object>>) subTaskAttributes.get("groups");
        List<VPRRequest.Group> groupList = groups.stream().map(map -> {
            VPRRequest.Group group = new VPRRequest.Group();
            group.setCallId((String) map.get("callId"));
            group.setFileUrl((String) map.get("url"));
            return group;
        }).collect(Collectors.toList());
        VPRRequest request = VPRRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .groups(groupList)
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.VPR.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to voice print recognition", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToVPR(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        VPRResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), VPRResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Voice print recognition response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.VPR_STATUS.getName(), true);
            responseMap.put(Attribute.VPR_RESULT.getName(), response.getResult());
            responseMap.put(Attribute.VPR_DIFF_PAIRS.getName(), response.getDifferentPairs());
            responseMap.put(Attribute.VPR_DIFF_IDS.getName(), response.getDifferentIds());
            responseMap.put(Attribute.VPR_ERROR.getName(), response.getError());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.VPR_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.VPR.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.VPR_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.VPR.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.VPR_RESPONSE, task, subTask);
    }
}
