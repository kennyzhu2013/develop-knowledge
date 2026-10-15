package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.VPRequest;
import com.cmic.pojo.dto.ai.VPResponse;
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
public class VPService implements IMqService {
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;

    @Autowired
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;

    public static final int TYPE_1VN = 1;
    public static final int TYPE_1V1 = 2;
    public static final int TYPE_REGISTER = 3;
    public static final int TYPE_DELETE = 4;
    public static final int TYPE_CREATE_DB = 5;
    public static final int TYPE_DELETE_DB = 6;

    @Override
    public void sendToMq(MqEntityEvent event) {

    }

    public void sendToMq(MqEntityEvent event, int type) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        String audioTagDb = (String) attributes.get("audioTagDb");

        VPRequest request = VPRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .type(type)
                .audioTagDb(audioTagDb)
                .build();

        if (type == TYPE_1VN) {
            request.setUrl((String) subTaskAttributes.get("url"));
            if (attributes.containsKey("topN")) {
                request.setTopN((Integer) attributes.get("topN"));
            }
            if (attributes.containsKey("channel")) {
                request.setChannel((Integer) attributes.get("channel"));
            }
        } else if (type == TYPE_1V1) {
            request.setUrl((String) subTaskAttributes.get("url"));
            request.setAudioTag((String) subTaskAttributes.get("audioTag"));
            if (attributes.containsKey("channel")) {
                request.setChannel((Integer) attributes.get("channel"));
            }
        }
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.VP.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to voice print recognition", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToVP(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        VPResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), VPResponse.class);
        int type = response.getType();

        log.info("TaskId:{}, jobId:{}, processId:{}, Voice print recognition response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.VP_STATUS.getName(), true);
            if (type == TYPE_1V1) {
                responseMap.put(Attribute.VP_SCORE.getName(), response.getScore());
            } else if (type == TYPE_1VN) {
                responseMap.put(Attribute.VP_TOPK_TAGS.getName(), response.getTopKTags());
            }
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.VP_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.VP.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.VP_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.VP.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.VP_RESPONSE, task, subTask);
    }
}
