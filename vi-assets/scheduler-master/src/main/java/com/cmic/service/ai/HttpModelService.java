package com.cmic.service.ai;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.ai.DyHttpRequest;
import com.cmic.pojo.entity.AIModel;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.AIModelService;
import com.cmic.service.MqService;
import com.cmic.service.task.SubTaskService;
import com.cmic.service.task.TaskService;
import com.cmic.util.ModelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HttpModelService implements IMqService {
    @Autowired
    private MqService mqService;
    @Autowired
    private AIModelService aiModelService;
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Autowired
    private TaskService taskService;
    @Autowired
    private SubTaskService subTaskService;
    @Override
    public void sendToMq(MqEntityEvent event) throws Exception {
    }


    public void sendToMq(Task task, SubTask subTask, DyHttpRequest request){
        subTaskService.beforeSendModel(task, subTask, AIModelEnum.DYHTTP.getName());
        log.info("TaskId:{}, jobId:{}, processId:{}, priority:{}, send to scheduler adapter dyHttp queue", task.getId(), subTask.getId(), task.getProcessId(), task.getPriority());
        request.setJobId(ModelUtil.buildModelJobId(subTask.getId(), subTask.getDataId()));
        mqService.sendToDyHttp(subTask.getLocation(), request, task.getPriority());
    }


    @Override
    public void handlerMessage(Task task, SubTask subTask, BaseResponse<?> baseResponse) {
        String code = baseResponse.getCode();
        String message = baseResponse.getMessage();
        Map<String, Object> responseMap = (Map<String, Object>) baseResponse.getData();

        if(responseMap.get("model") == null){
            log.error("DyHttp response is invalid, case model is null");
            return;
        }

        String model = (String) responseMap.get("model");

        log.info("TaskId:{}, jobId:{}, processId:{}, Model:{} response, code:{}", task.getId(), subTask.getId(), task.getProcessId(), model, code);
        Map<String, Object> updateMap = new HashMap<>();
        if(ErrorCode.SUCCESS.equals(code)){
//            Map<String, Object> updateMap = new HashMap<>();
            for(String key:responseMap.keySet()){
                if(key.equals("model") || key.equals("jobId")){
                    continue;
                }
                Object value = responseMap.get(key);
                updateMap.put(AIModelEnum.getModelParameter(model, key), value);
            }
            updateMap.put(AIModelEnum.getModelStatus(model), true);
//            subTaskService.updateSubTaskResponseMap(subTask, updateMap);
        }else {
            updateMap.put(AIModelEnum.getModelStatus(model), false);
//            subTaskService.updateSubTaskResponseResult(subTask, AIModelEnum.getModelStatus(model), false);
//            subTaskService.updateSubTaskErrorMessage(subTask, model, baseResponse.getMessage());
        }
        subTask = subTaskService.afterResponseModel(code, message, task, subTask, AIModelEnum.DYHTTP.getName(), updateMap);
        xmlEventPublisher.mqEventPublish(MqEvent.modelResp(model), task, subTask);
    }

    public DyHttpRequest getRequest(String taskId, String jobId, String model, Map<String, Object> customizedParam, String asrTextParam) throws Exception {
        AIModel aiModel = aiModelService.queryByModel(model);
        if(aiModel == null){
            log.error("TaskId:{}, jobId:{}, model:{} does not exists", taskId, jobId, model);
            throw new Exception();
        }
        if(!checkAllParameter(aiModel.getRequestParameters(), customizedParam, asrTextParam)){
            log.error("TaskId:{}, jobId:{}, customizedParam:{} does not match", taskId, jobId, customizedParam);
            throw new Exception();
        }

        Map<String , Object> requestParameters = getParameters(aiModel.getRequestParameters(), customizedParam, asrTextParam);
        DyHttpRequest request = DyHttpRequest.builder()
                .jobId(jobId)
                .model(aiModel.getModel())
                .url(aiModel.getHttpUrl())
                .authorization(aiModel.getAuthentication())
                .requestParameters(requestParameters)
                .responseParameters(aiModel.getResponseParameters())
                .type(aiModel.getType())
                .build();
        return request;
    }

    private Map<String ,Object> getParameters(List<String> targetParameters, Map<String, Object> originParameter, String asrTextParam){
        Map<String ,Object> result = new HashMap<>();
        if(CollectionUtils.isEmpty(targetParameters)){
            return result;
        }
        for(String parameter: targetParameters){
            if(parameter.equals(asrTextParam)){
                continue;
            }
            result.put(parameter, originParameter.get(parameter));
        }
        return result;
    }

    private boolean checkAllParameter(List<String> targetParameters, Map<String, Object> originParameter, String asrTextParam){
        if(CollectionUtils.isEmpty(targetParameters)){
            return true;
        }
        for(String parameter: targetParameters){
            if(parameter.equals(asrTextParam)){
                continue;
            }
            if(!originParameter.containsKey(parameter)){
                return false;
            }
        }
        return true;
    }
}
