package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.TextRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 催收模型对接
 */
@Slf4j
@Service
public class CollectionService implements IMqService{
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

        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

        if(!StringUtils.hasLength(asrText)){
            log.error("TaskId:{}, jobId:{}, send to collection mq error, text is null", taskId, jobId);
            return;
        }

        TextRequest request = TextRequest.builder().jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId())).text(asrText).build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.COLLECTION.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to collection", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToCollection(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        Map<String, Object> response = (Map<String, Object>) baseResponse.getData();

        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        log.info("TaskId:{}, jobId:{}, processId:{}, Collection response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.COLLECTION_STATUS.getName(), true);
            responseMap.put(Attribute.COLLECTION_RESULT.getName(), response.get("result"));
            responseMap.put(Attribute.COLLECTION_SCORE.getName(), response.get("score"));
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.COLLECTION_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.COLLECTION.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.COLLECTION_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.COLLECTION.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.COLLECTION_RESPONSE, task, subTask);
    }
}
