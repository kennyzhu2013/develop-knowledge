package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.CQAgentRequest;
import com.cmic.pojo.dto.ai.CQAgentResponse;
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
import java.util.Objects;

@Slf4j
@Service
public class CQAgentService implements IMqService {
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

        CQAgentRequest request = CQAgentRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .build();

        Boolean useTools = (Boolean) attributes.get("useTools");
        Boolean useFast = (Boolean) attributes.get("useFast");
        if (Objects.nonNull(useTools)) {
            request.setUseTools(useTools);
        }
        if (Objects.nonNull(useFast)) {
            request.setUseFast(useFast);
        }

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.CQ_AGENT.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to cq-agent", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToCQAgent(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        CQAgentResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), CQAgentResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, cq-agent response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
            responseMap.put(Attribute.CQ_AGENT_STATUS.getName(), true);
            responseMap.put(Attribute.CQ_AGENT_IS_VIOLATION.getName(), response.getResult().getIsViolation());
            responseMap.put(Attribute.CQ_AGENT_IS_FRAUD.getName(), response.getResult().getIsFraud());
            responseMap.put(Attribute.CQ_AGENT_RISK_LEVEL.getName(), response.getResult().getRiskLevel());
            responseMap.put(Attribute.CQ_AGENT_SCENE_CATEGORY.getName(), response.getResult().getSceneCategory());
            responseMap.put(Attribute.CQ_AGENT_SCENE_SUBTYPE.getName(), response.getResult().getSceneSubtype());
            responseMap.put(Attribute.CQ_AGENT_CONFIDENCE.getName(), response.getResult().getConfidence());
            responseMap.put(Attribute.CQ_AGENT_SOURCE.getName(), response.getResult().getSource());
            Map<String, Object> detail = new HashMap<>();
            detail.put("explanation", response.getResult().getExplanation());
            detail.put("detectedFeatures", response.getResult().getDetectedFeatures());
            detail.put("evidenceQuotes", response.getResult().getEvidenceQuotes());
            detail.put("analysisThought", response.getResult().getAnalysisThought());
            responseMap.put(Attribute.CQ_AGENT_DETAIL.getName(), detail);
        } else {
            responseMap.put(Attribute.CQ_AGENT_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.CQ_AGENT.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.CQ_AGENT_RESPONSE, task, subTask);
    }
}

