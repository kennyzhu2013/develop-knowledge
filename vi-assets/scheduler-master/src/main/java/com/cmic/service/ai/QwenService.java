package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.*;
import com.cmic.pojo.entity.ModelMeta;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ModelMetaService;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.cmic.util.ModelUtil;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QwenService implements IMqService {
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private ModelMetaService modelMetaService;

    @Override
    public void sendToMq(MqEntityEvent event) {
    }

    public void sendToMq(Task task, SubTask subTask, DyHttpRequest request) {
        String asrText = (String) subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        request.getRequestParameters().put("text", asrText);

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.QWEN.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to qwen", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority());
        mqService.sendToQwen(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        QwenResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), QwenResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, qwen response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if (ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.QWEN_STATUS.getName(), true);
            responseMap.put(Attribute.QWEN_RESULT.getName(), response.getResult());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.QWEN_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.QWEN.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.QWEN_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.QWEN.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.QWEN_RESPONSE, task, subTask);
    }


    public DyHttpRequest getRequest(Task task, SubTask subTask, Map<String, String> xmlAttribute) throws Exception {
        String model = xmlAttribute.getOrDefault("model", "qwen");
        String version = xmlAttribute.get("version"); // 可选

        ModelMeta modelMeta = StringUtils.isNotBlank(version)
                ? modelMetaService.queryByModelAndVersion(model, version)
                : modelMetaService.queryDefaultByModel(model);

        if (modelMeta == null) {
            log.error("TaskId:{}, jobId:{}, model:{}, version:{} does not exists",
                    task.getId(), subTask.getId(), model, version);
            throw new Exception();
        }

        Map<String, Object> requestParameters = getParameters(modelMeta.getRequestParameters(), task.getAttributes(), xmlAttribute);
        DyHttpRequest request = DyHttpRequest.builder()
                .jobId(ModelUtil.buildModelJobId(subTask.getId(), subTask.getDataId()))
                .model(modelMeta.getModel())
                .version(modelMeta.getVersion())
                .url(modelMeta.getHttpUrl())
                .authorization(modelMeta.getAuthentication())
                .requestParameters(requestParameters)
                .responseParameters(modelMeta.getResponseParameters())
                .type(modelMeta.getType())
                .build();
        return request;
    }

    private Map<String, Object> getParameters(List<String> targetParameters, Map<String, Object> taskAttribute, Map<String, String> xmlAttribute) {
        Map<String, Object> result = new HashMap<>();
        if (CollectionUtils.isEmpty(targetParameters)) {
            return result;
        }
        for (String parameter : targetParameters) {
            Object value = taskAttribute.get(parameter);
            if (value != null) {
                result.put(parameter, value);
                continue;
            }
            String xmlValue = xmlAttribute.get(parameter);
            if (xmlValue != null) {
                result.put(parameter, xmlValue);
            }
        }
        return result;
    }
}
