package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.ScenarioRequest;
import com.cmic.pojo.dto.ai.ScenarioResponse;
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

@Slf4j
@Service
public class ScenarioService  implements IMqService{
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

        ScenarioRequest request = ScenarioRequest.builder().jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId())).text(asrText).build();
        Map<String, Object> attributes = task.getAttributes();
        if(attributes.containsKey("preset")){
            request.setPreset((String) attributes.get("preset"));
        }

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.SCENARIO.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to scenario", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToScenario(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        ScenarioResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), ScenarioResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Scenario response, code:{}, message:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage());

        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.SCENARIO_STATUS.getName(), true);
            responseMap.put(Attribute.SCENARIO_RESULT.getName(), response.getResult());
            responseMap.put(Attribute.SCENARIO_SCORE.getName(), response.getScore());
            if(StringUtils.hasLength(response.getConsistency())){
                responseMap.put(Attribute.SCENARIO_CONSISTENCY.getName(), response.getConsistency());
            }
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.SCENARIO_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.SCENARIO.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.SCENARIO_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.SCENARIO.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.SCENARIO_RESPONSE, task, subTask);
    }
}
