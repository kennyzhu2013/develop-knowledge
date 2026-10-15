package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.KeywordAnalysisResponse;
import com.cmic.pojo.dto.ai.TextRequest;
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
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class KeywordAnalysisService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Resource
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;

    public final static String LOW_THRESH = "0";
    public final static String HIGH_THRESH = "1";

    public void sendToMq(MqEntityEvent event) {

    }

    public void sendToMq(MqEntityEvent event, String threshLevel) {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());


        String scene = (String) task.getAttributes().get("fraudDetectionScene");
        scene = StringUtils.hasLength(scene)?scene:"0";

        Map<String, Object> subTaskAttributes = subTask.getAttributes();
        Boolean isReverse = (Boolean) subTaskAttributes.get("isReverse");
        isReverse = isReverse==null?false:isReverse;

        TextRequest request = TextRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .text(asrText)
                .scene(scene)
                .isReverseLeg(isReverse)
                .threshLevel(threshLevel)
                .build();

        if (StringUtils.hasLength(threshLevel) && LOW_THRESH.equals(threshLevel)) {
            request.setScene("0");
            subTaskService.beforeSendModel(task, subTask, AIModelEnum.KEYWORD_ANALYSIS_LT.getName());
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to keyword fraud LT", taskId, jobId, task.getProcessId(), task.getPriority());
        } else {
            subTaskService.beforeSendModel(task, subTask, AIModelEnum.KEYWORD_ANALYSIS.getName());
            log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to keyword fraud", taskId, jobId, task.getProcessId(), task.getPriority());
        }
        mqService.sendToKeyword(subTask.getLocation(), request, task.getPriority());
    }

    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        KeywordAnalysisResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), KeywordAnalysisResponse.class);
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        Boolean isLT = LOW_THRESH.equals(response.getThreshLevel()) ? true : false;
        if (isLT) {
            handlerMessageLT(task, subTask, baseResponse);
        } else {
            handlerMessageHT(task, subTask, baseResponse);
        }
    }

    public void handlerMessageHT(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        KeywordAnalysisResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), KeywordAnalysisResponse.class);
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        log.info("TaskId:{}, jobId:{}, processId:{}, Keyword fraud response, code:{}, message:{}, data: {}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage(), baseResponse.getData());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
            responseMap.put(Attribute.KEYWORD_ANALYSIS_STATUS.getName(), true);
            responseMap.put(Attribute.KEYWORD_ANALYSIS_RESULT.getName(), response.getResults());
        } else {
            responseMap.put(Attribute.KEYWORD_ANALYSIS_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.KEYWORD_ANALYSIS.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.KEYWORD_ANALYSIS_RESPONSE, task, subTask);
    }

    public void handlerMessageLT(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        KeywordAnalysisResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), KeywordAnalysisResponse.class);
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();

        log.info("TaskId:{}, jobId:{}, processId:{}, Keyword fraud LT response, code:{}, message:{}, data: {}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage(), baseResponse.getData());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
            responseMap.put(Attribute.KEYWORD_ANALYSIS_LT_STATUS.getName(), true);
            responseMap.put(Attribute.KEYWORD_ANALYSIS_LT_RESULT.getName(), response.getResults());
        } else {
            responseMap.put(Attribute.KEYWORD_ANALYSIS_LT_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.KEYWORD_ANALYSIS_LT.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.KEYWORD_ANALYSIS_LT_RESPONSE, task, subTask);
    }
}
