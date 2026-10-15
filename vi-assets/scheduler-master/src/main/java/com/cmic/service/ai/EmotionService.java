package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.ASRResponse;
import com.cmic.pojo.dto.ai.EmotionRequest;
import com.cmic.pojo.dto.ai.EmotionResponse;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class EmotionService implements IMqService {
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
        Map<String, Object> subTaskAttributes = subTask.getAttributes();

        Map<String, Object> infoMap = (Map<String, Object>) subTask.getResponseResults().get(Attribute.ASR_INFO.getName());
        ASRResponse.Info info = JacksonUtil.mapToBean(infoMap, ASRResponse.Info.class);

        EmotionRequest request = EmotionRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .fileUrl((String) subTaskAttributes.get("url"))
                .texts(info.getSentences())
                .channels(info.getChannels())
                .timestamps(info.getTimestamps())
                .build();

        if (Objects.nonNull(attributes.get("emotionChannel"))) {
            request.setSetChannel((Integer) attributes.get("emotionChannel"));
        }
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.EMOTION.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to emotion", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToEmotion(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        EmotionResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), EmotionResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Emotion response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.EMOTION_STATUS.getName(), true);
            responseMap.put(Attribute.EMOTION_RESULT.getName(), response.getEmotions());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.EMOTION_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.EMOTION.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.EMOTION_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.EMOTION.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.EMOTION_RESPONSE, task, subTask);
    }
}
