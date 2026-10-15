package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.DealRequest;
import com.cmic.pojo.dto.ai.DealResponse;
import com.cmic.pojo.dto.ai.MarketCallRequest;
import com.cmic.pojo.dto.ai.MarketCallResponse;
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
public class DealService implements IMqService {
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

        DealRequest request = DealRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.DEAL.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to deal", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToDeal(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        DealResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(),DealResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, deal response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.DEAL_STATUS.getName(), true);
            responseMap.put(Attribute.DEAL_LABEL.getName(), response.getLabel());
            responseMap.put(Attribute.DEAL_NEED_REVIEW.getName(), response.getNeedReview());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.DEAL_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.DEAL.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.DEAL_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.DEAL.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.DEAL_RESPONSE, task, subTask);
    }
}
