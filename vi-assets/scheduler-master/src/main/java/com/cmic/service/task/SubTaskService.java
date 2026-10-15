package com.cmic.service.task;

import com.cmic.config.metrics.CommonMetrics;
import com.cmic.dao.SubTaskDaoImpl;
import com.cmic.pojo.dto.CallbackModelRequest;
import com.cmic.pojo.dto.CallbackRequest;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SubTaskService {
    @Autowired
    private SubTaskDaoImpl subTaskDao;
    @Autowired
    private CommonMetrics commonMetrics;

    @Transactional
    public void save(SubTask entity){
        subTaskDao.save(entity);
    }

    @Transactional
    public void saveAll(List<SubTask> list){
        subTaskDao.saveAll(list);
    }


    public SubTask getSubTaskByJobId(String jobId){
        return subTaskDao.findById(Long.parseLong(jobId));
    }

    public void deleteByTaskId(long taskId){
        subTaskDao.deleteByTaskId(taskId);
    }

    public void updateSubTaskItem(long jobId, Map<String, Object> params){
        subTaskDao.updateSubTaskItem(jobId, params);
    }
    public void updateCallback(long jobId){
        subTaskDao.updateSubTaskCallback(jobId, 1);
    }

    public void updateSubTaskResponseResult(String jobId, Map<String, Object> response){
        subTaskDao.updateSubTaskResponseResult(jobId, response);
    }

    /**
     * 单个更新模型返回的值
     * @param subTask
     * @param key
     * @param value
     */
    public SubTask updateSubTaskResponseResult(SubTask subTask, String key, Object value){
        return subTaskDao.updateSubTaskResponseResult(subTask.getId(), key, value);
//        subTask.getResponseResults().put(key, value);
    }

    public void updateSubTaskResponseMap(SubTask subTask, Map<String, Object> results){
        subTaskDao.updateSubTaskResponseMap(subTask.getId(), results);
        subTask.getResponseResults().putAll(results);
    }

    public void updateSubTaskResponseMap(String model, SubTask subTask, Map<String, Object> results){
        subTaskDao.updateSubTaskResponseMap(subTask.getId(), model, results);
        subTask.getResponseResults().putAll(results);
    }

    public void updateSubTaskResponseModel(String model, SubTask subTask){
        subTaskDao.updateSubTaskResponseModel(subTask.getId(), model);
    }

    public void beforeSendModel(Task task, SubTask subTask, String model){
        CallbackModelRequest request = CallbackModelRequest.builder().build();
        request.setName(model);
        request.setStartTime(System.currentTimeMillis());
        request.setStatus(CallbackModelRequest.NO_PROCESS);

        this.updateSubTaskModel(subTask, request);
        commonMetrics.sendModel(model, task.getProcessId(), task.getPriority());
    }

    public SubTask afterResponseModel(String code, String message, Task task, SubTask subTask, String model, Map<String, Object> results){
        CallbackModelRequest request = CallbackModelRequest.builder().build();
        request.setName(model);
        request.setEndTime(System.currentTimeMillis());
        request.setCode(code);
        request.setMessage(message);
        if(ErrorCode.SUCCESS.equals(code)){
            request.setStatus(CallbackModelRequest.SUCCESS);
        }else {
            request.setStatus(CallbackModelRequest.FAIL);
        }
        subTask =  this.updateSubTaskModel(subTask, request, results);
        this.afterUpdateModel(task, subTask, model);
        return subTask;
    }

    public void afterUpdateModel(Task task, SubTask subTask, String model) {
        Map<String, Object> modelMap = (Map<String, Object>) subTask.getModelInfoResult(model);
        String code = (String) modelMap.get("code");
        Long startTime = (Long) modelMap.get("startTime");
        Long endTime = (Long) modelMap.get("endTime");
        commonMetrics.receiveModel(model, task.getProcessId(), code, startTime, endTime);
    }

    public SubTask updateSubTaskModel(SubTask subTask, CallbackModelRequest request, Map<String, Object> results){
        return subTaskDao.updateSubTaskModel(subTask.getId(), request, results);
    }

    public SubTask updateSubTaskModel(SubTask subTask, CallbackModelRequest request){
        return subTaskDao.updateSubTaskModel(subTask.getId(), request, null);
    }

    public void updateSubTaskModelAttribute(SubTask subTask, String model, Map<String, Object> results){
        SubTask updatedSubtask = subTaskDao.updateSubTaskModelAttribute(subTask.getId(), model, results);
        BeanUtils.copyProperties(updatedSubtask, subTask);
    }

    public void updateSubTaskErrorMessage(SubTask subTask, String model, String errorMessage){
        errorMessage = subTask.getErrorMessage(model, errorMessage);
        subTaskDao.updateSubTaskErrorMessage(String.valueOf(subTask.getId()), errorMessage);

    }

    public void updateSubTaskFinish(SubTask subTask, int finish){
        subTaskDao.updateSubTaskFinish(subTask.getId(), finish);
        subTask.setFinish(finish);
    }

    public List<SubTask> constructJobErrorMessage(Task task, List<CallbackRequest.ErrorMessage> errorMessages){
        long taskId = task.getId();
        List<SubTask> subTaskList = findAllByTaskId(taskId);
        List<SubTask> timeoutJob = new ArrayList<>();
        for(SubTask subTask : subTaskList){
            if(subTask.getCallback() == 1){
                String error = subTask.getErrorMessage();
                if(!StringUtils.hasLength(error)){
                    continue;
                }
                errorMessages.add(getErrorMessage(subTask.getDataId(), error));
            }else {
                errorMessages.add(getErrorMessage(subTask.getDataId(), "超时"));
                timeoutJob.add(subTask);
            }
        }
        return timeoutJob;
    }

    private CallbackRequest.ErrorMessage getErrorMessage(String dataId, String message){
        CallbackRequest.ErrorMessage errorMessage = new CallbackRequest.ErrorMessage();
        errorMessage.setId(dataId);
        errorMessage.setMessage(message);
        return errorMessage;
    }

    public List<SubTask> findAllByTaskId(long taskId){
        return subTaskDao.findAllByTaskId(taskId);
    }

    public int countByCallbackAndTaskId(long taskId){
        return subTaskDao.countByCallbackAndTaskId(taskId);
    }

    public int countByNotCallbackAndTaskId(long taskId){
        return subTaskDao.countByNotCallbackAndTaskId(taskId);
    }

    public SubTask findByTaskIdAndCallbackOrderByUpdateTimeDesc(long taskId){
        return subTaskDao.findByTaskIdAndCallbackOrderByUpdateTimeDesc(taskId);
    }

    public void removeSubTaskAsr(long jobId){
        subTaskDao.removeSubTaskAsr(jobId);
    }

    public void updateSubTaskStatus(long jobId, int status){
        subTaskDao.updateSubTaskStatus(jobId, status);
    }

    public void timeoutReceiveModel(Task task, SubTask subTask, String model) {
        commonMetrics.modelReceiveTimeoutInc(model, task.getProcessId(), task.getPriority());
    }
}
