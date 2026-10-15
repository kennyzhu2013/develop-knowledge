package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.DyHttpRequest;
import com.cmic.pojo.dto.ai.DyHttpResponse;
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
public class DIFYService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    @Override
    public void sendToMq(MqEntityEvent event) throws Exception {

    }

    public void sendToMq(Task task, SubTask subTask, DyHttpRequest request){
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.DIFY.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to DIFY", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority());
        request.setJobId(ModelUtil.buildModelJobId(subTask.getId(), subTask.getDataId()));
        mqService.sendToDIFY(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        DyHttpResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), DyHttpResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, DIFY response, code:{}", task.getId(), subTask.getId(), task.getProcessId(), code);

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.DIFY_STATUS.getName(), true);
            responseMap.put(Attribute.DIFY_RESULT.getName(), response.getCustomizedResult());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.DIFY_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.DIFY.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.DIFY_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.DIFY.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.DIFY_RESPONSE, task, subTask);
    }
}
