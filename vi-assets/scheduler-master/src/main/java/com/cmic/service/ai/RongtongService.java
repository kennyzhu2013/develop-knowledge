package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.RongtongRequest;
import com.cmic.pojo.dto.ai.RongtongResponse;
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

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class RongtongService implements IMqService{
    @Autowired
    private MqService mqService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Resource
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;

    @Override
    public void sendToMq(MqEntityEvent event) throws Exception {
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        Map<String, Object> attributes = task.getAttributes();
        String svcType = (String) attributes.get("svcType");
        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());

        RongtongRequest request = RongtongRequest.builder()
                .jobId(ModelUtil.buildModelJobId(jobId, subTask.getDataId()))
                .svcType(svcType)
                .contentText(asrText)
                .build();

        if(attributes.containsKey("rtPreset")){
            request.setRemark((String) attributes.get("rtPreset"));
        }

        subTaskService.beforeSendModel(task, subTask, AIModelEnum.RT.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to rt", taskId, jobId, task.getProcessId(), task.getPriority());
        mqService.sendToRongtong(subTask.getLocation(), request, task.getPriority());
    }

    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        RongtongResponse response = JacksonUtil.mapToBean((Map) baseResponse.getData(), RongtongResponse.class);

        log.info("TaskId:{}, jobId:{}, processId:{}, Rt response, code:{}, message:{}, body:{}", task.getId(), subTask.getId(), task.getProcessId(), code, baseResponse.getMessage(), baseResponse.getData());
        Map<String, Object> responseMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)) {
//            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put(Attribute.RT_STATUS.getName(), true);
            responseMap.put(Attribute.RT_RESULT_CODE.getName(), response.getResultCode());
            responseMap.put(Attribute.RT_ABILITY_TYPE.getName(), response.getAbilityType());
            responseMap.put(Attribute.RT_FIRST_LABEL.getName(), response.getFirstLabel());
            responseMap.put(Attribute.RT_SECOND_LABEL.getName(), response.getSecondLabel());
            responseMap.put(Attribute.RT_THIRD_LABEL.getName(), response.getThirdLabel());
            responseMap.put(Attribute.RT_SVC_TYPE.getName(), response.getSvcType());
//            subTaskService.updateSubTaskResponseMap(subTask, responseMap);
        } else {
            responseMap.put(Attribute.RT_STATUS.getName(), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, AIModelEnum.RT.getName(), baseResponse.getMessage());
//            subTaskService.updateSubTaskResponseResult(subTask, Attribute.RT_STATUS.getName(), false);
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.RT.getName(), responseMap);
        xmlEventPublisher.mqEventPublish(MqEvent.RONGTONG_RESPONSE, task, subTask);
    }
}
