package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.TextSmlRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
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
public class TextSmlService implements IMqService{
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
        List<Map<String, Object>> groups = (List<Map<String, Object>>) subTaskAttributes.get("groups");
        List<TextSmlRequest.Group> groupList = groups.stream().map(map -> {
            TextSmlRequest.Group group = new TextSmlRequest.Group();
            group.setCallId((String) map.get("callId"));
            group.setText((String) map.get("text"));
            return group;
        }).collect(Collectors.toList());

        Map<String, Object> taskAttributes = task.getAttributes();

        TextSmlRequest request = TextSmlRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .groups(groupList)
                .build();

        if(taskAttributes.get("headtail") != null){
            String headTail = (String) taskAttributes.get("headtail");
            request.setHeadtail(headTail);
        }
        if(taskAttributes.get("wordNum") != null){
            int wordNum = Integer.parseInt(taskAttributes.get("wordNum").toString());
            request.setWordNum(wordNum);
        }
        if(taskAttributes.get("textThreshold") != null){
            float textThreshold = Float.parseFloat(taskAttributes.get("textThreshold").toString());
            request.setTextThreshold(textThreshold);
        }
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.TEXT_SIMILARITY.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to text similarity", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToTextSml(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        Map<String, Object> response = (Map<String, Object>) baseResponse.getData();

        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        log.info("TaskId:{}, jobId:{}, processId:{}, Text similarity response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.TEXT_SIMILARITY_STATUS.getName(), true);
            responseMap.put(Attribute.TEXT_SIMILARITY_RESULT.getName(), response.get("result"));
            responseMap.put(Attribute.TEXT_SIMILARITY_SCORE.getName(), response.get("score"));
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.TEXT_SIMILARITY_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.TEXT_SIMILARITY.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.TEXT_SIMILARITY_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.TEXT_SIMILARITY.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.TEXT_SIMILARITY_RESPONSE, task, subTask);
    }
}
