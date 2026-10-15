package com.cmic.service;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONObject;
import com.cmic.config.ApplicationProperties;
import com.cmic.config.metrics.CommonMetrics;
import com.cmic.pojo.bo.AIModelEnum;
import com.cmic.pojo.dto.*;
import com.cmic.pojo.entity.AIModel;
import com.cmic.pojo.entity.Process;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import com.cmic.util.JacksonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CallbackService {
    @Autowired
    private ProcessService processService;
    @Autowired
    private MqService mqService;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private AIModelService aiModelService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ApplicationProperties config;
    @Autowired
    private CommonMetrics commonMetrics;

    public void callbackTaskError(BaseResponse response, Long transactionId, int priority){
        CallbackRequest request = CallbackRequest.builder()
                .type(2)
                .code(response.getCode())
                .message(response.getMessage())
                .transactionId(transactionId)
                .build();
        JSONObject jsonObject = new JSONObject(request);
        //TODO 创建任务失败，回调结果
//        mqService.sendToCallback(jsonObject, priority);
//        log.info("TransactionId:{}, callback error task request:{}", transactionId, JacksonUtil.obj2StringPretty(request));
    }

    public void callbackTask(Task task) {
        String taskId = String.valueOf(task.getId());
        try {
            List<SubTask> timeoutSubTaskList = new ArrayList<>();
            List<SubTask> subTaskList = subTaskService.findAllByTaskId(task.getId());

            int failNum = 0;
            for(SubTask subTask : subTaskList){
                if(subTask.getCallback() != 1){
                    timeoutSubTaskList.add(subTask);
                    failNum ++;
                }else if(subTask.getCallback() == 1 && subTask.getFinish() == 0){
                    failNum ++;
                }
            }
            //超时子任务推送
            for(SubTask subTask: timeoutSubTaskList){
                this.timeoutCallbackJob(task, subTask);
            }

            int jobSize = task.getJobCount();
            int finishNum = jobSize - failNum;
            int finishRate = (int) (NumberUtil.div(finishNum, jobSize, 2)*100);

            CallbackRequest request = CallbackRequest.builder()
                    .type(2)
                    .finishRate(finishRate)
                    .failCount(failNum)
                    .total(jobSize)
                    .transactionId(task.getTransactionId())
                    .build();
            JSONObject jsonObject = new JSONObject(request);
            mqService.sendToCallback(jsonObject, task.getPriority());
            log.info("TaskId:{}, transactionId:{}, callback task request:{}", taskId, task.getTransactionId(), JacksonUtil.obj2String(request));
        }catch (Exception e){
            log.error("TaskId:{}, transactionId:{}, callback task error:"+e.getMessage(), taskId, task.getTransactionId());
        }

    }

    public void timeoutCallbackJob(Task task, SubTask subTask){
        log.info("TaskId:{}, jobId:{}, callback timeout subtask", task.getId(), subTask.getId());
        this.callbackJob(task, subTask, 1,0);
    }

    @Transactional
    public void callbackJob(Task task, SubTask subTask, int callback, int finish){
        Map<String, Object> params = new HashMap<>();
        params.put("callback", callback);
        params.put("finish", finish);
        subTask.setFinish(finish);
        subTask.setCallback(callback);
        this.callbackJob(task, subTask);
        subTaskService.updateSubTaskItem(subTask.getId(), params);
    }
//    public JSONObject callbackJob(Task task, SubTask subTask) {
//        try {
//            String taskId = String.valueOf(task.getId());
//            String jobId = String.valueOf(subTask.getId());
//            String error = subTask.getErrorMessage();
//            int finish = subTask.getFinish();
//
//            CallbackRequest request = CallbackRequest.builder()
//                    .type(1)
//                    .dataId(subTask.getDataId())
//                    .status(String.valueOf(finish))
//                    .errorMessage(error)
//                    .transactionId(task.getTransactionId())
//                    .build();
//
//            JSONObject paramJson = getCallbackJson(task.getProcessId(), subTask, request);
//            mqService.sendToCallback(paramJson, task.getPriority());
//            log.info("TaskId:{}, jobId:{}, callback subtask model status:{}, request:{}", taskId, jobId, paramJson.get("modelStatus"), new JSONObject(request));
////            log.info("TaskId:{}, jobId:{}, callback subtask response:{}", taskId, jobId, paramJson);
//
//            return paramJson.getJSONObject("modelStatus");
//        }catch (Exception e){
//            log.error(e.getMessage());
//            return null;
//        }
//
//    }

    public void callbackJob(Task task, SubTask subTask) {
        try {
            String taskId = String.valueOf(task.getId());
            String jobId = String.valueOf(subTask.getId());
            String error = subTask.getErrorMessage();
            int finish = subTask.getFinish();

            CallbackJobRequest request = CallbackJobRequest.builder()
                    .type(1)
                    .dataId(subTask.getDataId())
                    .status(String.valueOf(finish))
                    .errorMessage(error)
                    .transactionId(task.getTransactionId())
                    .build();


            Process process = processService.findEnableByProcessId(task.getProcessId());

            String[] aiModels;
            List<String> atomicProcessIdList = config.getCommon().getAtomicProcessId();
            if(atomicProcessIdList.contains(process.getProcessId())){
                //原子能力回调流程
                List<String> keys = task.getAttributes().entrySet()
                        .stream()
                        .filter(e -> e.getValue().equals("1"))
                        .map(Map.Entry::getKey)
                        .toList();
                aiModels = AIModelEnum.getModelsByKeys(keys);
            }else{
                String aiModelArray = process.getAiModels();
                aiModels = aiModelArray.split(",");
            }
            //获取模型状态参数等
            List<CallbackModelRequest> models = getModelInfos(taskId, jobId, subTask, aiModels);
            request.setModels(models);

            //获取模型返回结果等
            JSONObject paramJson = this.getJobCallbackParam(subTask, aiModels, request);
            //获取redis中缓存的值
            this.getCallbackAttributes(subTask, paramJson);

            JSONObject modelStatus = new JSONObject();
            this.getModelStatus(modelStatus, models);
            paramJson.set("modelStatus", modelStatus);
            if(finish == 0) {
                // 失败任务去掉质检结果、风险等级
                paramJson.remove("qcResult");
                paramJson.remove("riskLevel");
            }

            mqService.sendToCallback(paramJson, task.getPriority());
            commonMetrics.statJobFinsh(paramJson);
//            log.info("TaskId:{}, jobId:{}, callback subtask response model:{}", taskId, jobId, new JSONObject(request));
            paramJson.remove("asrText");
            log.info("TaskId:{}, jobId:{}, callback subtask response:{}", taskId, jobId, paramJson);
        }catch (Exception e){
            log.error(e.getMessage());
        }

    }

    private JSONObject getModelStatus(JSONObject modelStatus, List<CallbackModelRequest> models) throws JsonProcessingException {
        for(CallbackModelRequest modelRequest : models){
            modelStatus = JacksonUtil.addJsonAttribute(modelStatus, modelRequest.getName(), modelRequest.getStatus());
        }

        return modelStatus;
    }

    /**
     *  1、如果responseResult中model不存在，则该模型未执行，初始化name、status=未执行
     *  2、如果responseResult中model存在，但endTime不存在，则已发送未回复，模型超时。status=超时
     *  3、如果responseResult中model存在，endTime存在，则该模型执行完成
     */
    private List<CallbackModelRequest> getModelInfos(String taskId, String jobId, SubTask subTask, String[] aiModels){
        List<CallbackModelRequest> models = new ArrayList<>();

        for(String model : aiModels){
            CallbackModelRequest modelRequest = CallbackModelRequest.builder().build();
            modelRequest.setName(model);

            Object modelMap = subTask.getModelInfoResult(model);
            if(modelMap == null){
                modelRequest.setStatus(CallbackModelRequest.NO_PROCESS);
                models.add(modelRequest);
                log.info("TaskId:{}, jobId:{}, model:{} db not exist, init status = 0", taskId, jobId, model);
                continue;
            }
            Map<String, Object> modelResponseMap = (Map<String, Object>) modelMap;
            Object endTime = modelResponseMap.get("endTime");
            if(endTime == null){
                modelRequest.setStatus(CallbackModelRequest.FAIL);
                modelRequest.setCode(ErrorCode.TIMEOUT.getCode());
                modelRequest.setMessage(ErrorCode.TIMEOUT.getMessage());
                log.info("TaskId:{}, jobId:{}, model:{} timeout", taskId, jobId, model);
            }else {
                modelRequest.setEndTime((Long) endTime);
            }

            Object code = modelResponseMap.get("code");
            if(code != null){
                modelRequest.setCode(String.valueOf(code));
            }
            Object message = modelResponseMap.get("message");
            if(message != null){
                modelRequest.setMessage(String.valueOf(message));
            }
            Object startTime = modelResponseMap.get("startTime");
            if(startTime != null){
                modelRequest.setStartTime((Long) startTime);
            }
            Object status = modelResponseMap.get("status");
            if(status != null){
                modelRequest.setStatus((Integer) status);
            }
            Object modelQcResult = modelResponseMap.get("modelQcResult");
            if(modelQcResult != null){
                modelRequest.setModelQcResult((String) modelQcResult);
            }

            models.add(modelRequest);
        }
        return models;
    }

    private JSONObject getJobCallbackParam(SubTask subTask, String[] aiModels, CallbackJobRequest request) throws JsonProcessingException {
        JSONObject paramJson = new JSONObject(request);

        for (String model : aiModels){
            List<String> attributes = AIModelEnum.getAttributes(model);
            for(String attribute: attributes){
                Object value = subTask.getResponseResults().get(attribute);
                JacksonUtil.addJsonAttribute(paramJson, attribute, value);
            }
        }
        return paramJson;
    }

    private JSONObject getCallbackJson(String processId, SubTask subTask, CallbackRequest subtaskCallbackRequest) throws JsonProcessingException {
        JSONObject jsonObject = new JSONObject(subtaskCallbackRequest);
        Process process = processService.findEnableByProcessId(processId);
        String aiModelArray = process.getAiModels();
        String[] aiModels = aiModelArray.split(",");
        JSONObject modelStatus = new JSONObject();
        for(String model : aiModels){
            getResponse(subTask, model, modelStatus, jsonObject);
        }
        jsonObject.set("modelStatus", modelStatus);
        getCallbackAttributes(subTask, jsonObject);
        return jsonObject;
    }

    private void getCallbackAttributes(SubTask subTask, JSONObject jsonObject){
        Map<String, Object> attributeMap = redisService.getAttributeJobCallback(subTask.getId());
        if(!attributeMap.containsKey("review")){
            attributeMap.put("review", "0");
        }
        for(String key:attributeMap.keySet()){
            jsonObject.set(key, attributeMap.get(key));
        }
    }

    private void getResponse(SubTask subTask, String model, JSONObject modelStatus, JSONObject modelResponse) throws JsonProcessingException {
        AIModel aiModel = aiModelService.queryByModel(model);
        if(aiModel == null){
            getResponseFromEnum(subTask, model, modelStatus, modelResponse);
        }else {
            getResponseFromDB(aiModel, subTask, model, modelStatus, modelResponse);
        }

    }

    private void getResponseFromDB(AIModel aiModel, SubTask subTask, String model, JSONObject modelStatus, JSONObject modelResponse) throws JsonProcessingException {
        String statusKey = AIModelEnum.getModelStatus(model);
        getModelStatus(subTask, modelStatus, model, statusKey);
        List<String> responseParameters = aiModel.getResponseParameters();
        if(!CollectionUtils.isEmpty(responseParameters)){
            for(String key:responseParameters){
                String parameter = AIModelEnum.getModelParameter(model, key);
                Object value = subTask.getResponseResults().get(parameter);
                JacksonUtil.addJsonAttribute(modelResponse, parameter, value);
            }
        }
    }

    private void getResponseFromEnum(SubTask subTask, String model, JSONObject modelStatus, JSONObject modelResponse) throws JsonProcessingException {
        AIModelEnum aiModelEnum = AIModelEnum.getEnumByName(model);
        if(aiModelEnum != null){
            getModelStatus(subTask, modelStatus, model, aiModelEnum.getStatus());
        }
        List<String> attributes = AIModelEnum.getAttributes(model);
        for(String attribute: attributes){
            Object value = subTask.getResponseResults().get(attribute);
            JacksonUtil.addJsonAttribute(modelResponse, attribute, value);
        }
    }

    private JSONObject getModelStatus(SubTask subTask, JSONObject modelStatus, String model, String item) throws JsonProcessingException {
        Object value = subTask.getResponseResults().get(item);
        if(value == null){
            value = 0;
        }else{
            boolean status = (boolean) value;
            if(!status){
                value = 1;
            }else{
                value = 2;
            }
        }
        modelStatus = JacksonUtil.addJsonAttribute(modelStatus, model, value);
        return modelStatus;
    }
}
