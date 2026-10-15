package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.BGERequest;
import com.cmic.pojo.dto.ai.BGEResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class BGEService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;

    public void sendToMq(MqEntityEvent event) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        List<String> classifiers = (List<String>) attributes.get("classifiers");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) attributes.get("rules");

        BGERequest bgeRequest = BGERequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .classifiers(classifiers)
                .rules(JacksonUtil.string2Obj(JacksonUtil.obj2String(rules), new TypeReference<List<BGERequest.Rule>>() {}))
                .build();

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.BGE.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to bge", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToBGE(subTask.getLocation(), bgeRequest, task.getPriority());

    }

    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        BGEResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), BGEResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, bge response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, message);


        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
            responseMap.put(Attribute.BGE_STATUS.getName(), true);
            responseMap.put(Attribute.BGE_RULE_NAME.getName(), response.getName());
            responseMap.put(Attribute.BGE_RULE_EXPRESSION.getName(), response.getExpression());
            responseMap.put(Attribute.BGE_RULE_OUTPUT_LABEL.getName(), response.getOutputLabel());
            responseMap.put(Attribute.BGE_RULE_PRIORITY.getName(), response.getPriority());
        }else {
            responseMap.put(Attribute.BGE_STATUS.getName(), false);
        }

        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.BGE.getName(), responseMap);

        xmlEventPublisher.mqEventPublish(MqEvent.BGE_RESPONSE, task, subTask);
    }
}
