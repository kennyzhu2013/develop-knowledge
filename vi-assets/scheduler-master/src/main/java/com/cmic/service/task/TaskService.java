package com.cmic.service.task;

import com.cmic.dao.TaskDaoImpl;
import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.TaskAudioQualityResponse;
import com.cmic.pojo.dto.TaskRequest;
import com.cmic.pojo.entity.Process;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.CallbackService;
import com.cmic.service.ProcessService;
import com.cmic.util.SnowflakeUtils;
import com.cmic.util.TransformUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskService {
    @Autowired
    private CallbackService callbackService;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private TaskDaoImpl taskDao;
    @Autowired
    private ProcessService processService;
    @Autowired
    private ProcessJobService jobService;

    @Transactional
    public BaseResponse createTask(TaskRequest taskRequest) {
        long taskId = SnowflakeUtils.generateId();
        try {
            List<SubTask> subTaskList = storeJob(taskId, taskRequest.getData());
            Task task = storeTask(taskId, taskRequest);
            jobService.processJob(task, subTaskList);
            this.updateStatus(taskId, 2);

            log.info("ProcessId:{}, TaskId:{}, TransactionId:{}, data size:{} create task success", taskRequest.getProcessId(), task.getId(), task.getTransactionId(), task.getJobCount());
            TaskAudioQualityResponse response = new TaskAudioQualityResponse();
            response.setTransactionId(task.getTransactionId());
            return BaseResponse.constructResponse(ErrorCode.SUCCESS, response);
        }catch (Exception e){
//            iTaskDao.deleteById(taskId);
//            subTaskService.deleteByTaskId(taskId);
            log.error("ProcessId:{}, TaskId:{} create error.",taskRequest.getProcessId(), taskId, e.toString());
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }

    }

    public void handlerMqTask(TaskRequest taskRequest) throws Exception {
        BaseResponse response = BaseResponse.constructResponse(ErrorCode.SUCCESS);
        if(!validTask(taskRequest, response)){
            log.info("TransactionId:{}, create task error response:{}", taskRequest.getTransactionId(), response.toString());
            callbackService.callbackTaskError(response, taskRequest.getTransactionId(), Integer.parseInt(taskRequest.getPriority()));
            return;
        }
        this.createMqTask(taskRequest);
    }

    public void createMqTask(TaskRequest taskRequest) throws Exception {
        long taskId = SnowflakeUtils.generateId();
        try {
            List<SubTask> subTaskList = storeJob(taskId, taskRequest.getData());
            Task task = storeTask(taskId, taskRequest);
            jobService.processJob(task, subTaskList);
            this.updateStatus(taskId, 2);

            log.info("ProcessId:{}, TaskId:{}, TransactionId:{}, data size:{}, priority:{} create task success",
                    taskRequest.getProcessId(), task.getId(), task.getTransactionId(), task.getJobCount(), task.getPriority());
        }catch (Exception e){
            log.error("ProcessId:{}, TaskId:{} create error",taskRequest.getProcessId(), taskId, e);
            throw new Exception(e);
        }

    }

    private boolean validTask(TaskRequest taskRequest, BaseResponse response){
        String requestId = taskRequest.getRequestId();
        if(!StringUtils.hasLength(requestId)){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("requestId cannot be null");
            return false;
        }
        String priority = taskRequest.getPriority();
//        String regex = "^[0-9]$";
        String regex = "^(10|[0-9])$";
        if(!priority.matches(regex)){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("illegal priority");
            return false;
        }
        String processId = taskRequest.getProcessId();
        if(!StringUtils.hasLength(processId)){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("processId cannot be null");
            return false;
        }
        Long transactionId = taskRequest.getTransactionId();
        if(transactionId == null){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("transactionId cannot be null");
            return false;
        }

        List<Map<String, Object>> data = taskRequest.getData();
        if(CollectionUtils.isEmpty(data)){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("data should not be empty");
            return false;
        }
        if(data.size() >=100000){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("max length of data is 100000");
            return false;
        }

        if(this.isRepeat(requestId)){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("RequestId:"+requestId+" is repeat");
            return false;
        }
        Process process = processService.findEnableByProcessId(processId);
        if(process == null){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("ProcessId:"+processId+" does not exist");
            return false;
        }

        boolean hasError = data.stream()
                .anyMatch(map ->
                        !map.containsKey("dataId") ||
                                map.get("dataId") == null ||
                                map.get("dataId").toString().trim().isEmpty()
                );
        if(hasError){
            response.setCode(ErrorCode.PARAMETER.getCode());
            response.setMessage("Check data list, dataId does not exist or dataId is null");
            return false;
        }
        return true;
    }


    private Task storeTask(long taskId, TaskRequest taskRequest){
        Task task = TransformUtil.transformRequestToTask(taskId, taskRequest);
        log.info("storing task to db, taskId = {}", taskId);
        this.save(task);
        return task;
    }

    private List<SubTask> storeJob(long taskId, List<Map<String, Object>> data){
        List<SubTask> list = new ArrayList<>();

        for(Map<String, Object> map : data){
            SubTask subTask = TransformUtil.transformRequestToSubTask(taskId, map);
            list.add(subTask);
        }

        subTaskService.saveAll(list);
        return list;
    }

    public Task getTaskById(long taskId){
        return taskDao.getTaskById(taskId);
    }

    public boolean isRepeat(String requestId){
        Task task = taskDao.findByRequestId(requestId);
        if(task == null){
            return false;
        }
        return true;
    }

    public void save(Task task){
        taskDao.save(task);
    }

    public void updateStatus(long taskId, int status){
        taskDao.updateStatus(taskId, status);
    }

    public void updateCallback(Task task){
        taskDao.updateCallback(task.getId(), 1);
        task.setCallback(1);
    }

    private void flushTaskToDb(Task task, int finish){
        taskDao.updateTaskFinish(task.getId(), finish);
        task.setFinish(finish);
    }

    public void doTaskCallback(Task task, int finish){
        try {
            flushTaskToDb(task, finish);
            this.callbackTask(task);
            doAfter(task);
            //调用重推 url
        }catch (Exception e){
            log.error(e.getMessage());
        }
    }

    @Deprecated
    public void handlerMqTaskCallback(String taskId){
        Task task = this.getTaskById(Long.parseLong(taskId));
        doTaskCallback(task, 1);
    }

    public void handlerMqTaskCallback(TaskMqBo taskMqBo){
        Task task = this.getTaskById(taskMqBo.getTaskId());
        doTaskCallback(task, taskMqBo.getFinish());
    }

    private void callbackTask(Task task){
        if(task.isNeedTaskCallback()){
            this.updateCallback(task);
            callbackService.callbackTask(task);
        }
    }

    private void doAfter(Task task){
//        for(String jobId: task.getJobIds()){
//            redisService.delSubTask(jobId);
//            redisService.delAttribute(jobId);
//            subTaskService.removeSubTaskMap(jobId);
//            redisService.removeSubTaskCallback(jobId);
//            redisService.removeSubTaskCallback(jobId);
//        }
//        redisService.delTask(taskId);
//        redisService.delJobFinish(taskId);
//        redisService.removeSchedulerTask(taskId);
    }

    public Integer getTaskCallback(long taskId) {
        return taskDao.getTaskCallback(taskId);
    }

    public List<Task> getNeedCallbackTask(){
        LocalDateTime timeoutHoursAgo = LocalDateTime.now().minusHours(24);
        LocalDateTime threeDayAgo = LocalDateTime.now().minusDays(3);
        return taskDao.findByNeedTaskCallbackAndCallbackAndCreateTimeBetween(true, 0, threeDayAgo, timeoutHoursAgo);
    }

    public List<Task> getTimeoutTaskByTime(LocalDateTime maxTime){
        LocalDateTime threeDayAgo = LocalDateTime.now().minusDays(3);
        return taskDao.findByNeedTaskCallbackAndCallbackAndCreateTimeBetween(true, 0, threeDayAgo, maxTime);
    }

    public List<Task> getTimeoutTaskByTime(boolean stream, LocalDateTime maxTime){
        LocalDateTime threeDayAgo = LocalDateTime.now().minusDays(3);
        return taskDao.findByNeedTaskCallbackAndCallbackAndStreamAndCreateTimeBetween(true, 0, stream, threeDayAgo, maxTime);
    }

    public Map<String, Integer> countStreamTaskByProcessId(LocalDateTime maxTime) {
        LocalDateTime threeDayAgo = LocalDateTime.now().minusDays(3);
        return taskDao.countTaskByProcessId(true, 0, true, threeDayAgo, maxTime);
    }

    public static void main(String[] args) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(2);
        System.out.println(oneDayAgo);
    }
}
