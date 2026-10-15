package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.CQAntiRequest;
import com.cmic.pojo.dto.ai.CQAntiResponse;
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
public class CQAntiService implements IMqService {
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
        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

        CQAntiRequest request = CQAntiRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.CQ_ANTI.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to chongqing-anti", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToCQAnti(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        CQAntiResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), CQAntiResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, chongqing-anti response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
            responseMap.put(Attribute.CQ_ANTI_STATUS.getName(), true);
            responseMap.put(Attribute.CQ_ANTI_FILTER_TYPE.getName(), response.getFilterType());
            responseMap.put(Attribute.CQ_ANTI_RISK_LEVEL.getName(), response.getRiskLevel());
            responseMap.put(Attribute.CQ_ANTI_RESULT.getName(), response.getResult());
        } else {
            responseMap.put(Attribute.CQ_ANTI_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.CQ_ANTI.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.CHONGQING_ANTI_RESPONSE, task, subTask);
    }
}
