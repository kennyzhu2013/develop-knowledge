package com.cmic.service;

import cn.hutool.json.JSONObject;
import com.cmic.pojo.bo.TaskMqBo;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class JobService {
    @Autowired
    private CallbackService callbackService;
    @Autowired
    private SubTaskService subTaskService;
    @Autowired
    private MqService mqService;
    @Autowired
    private RedisService redisService;


    public void jobFinished(Task task, SubTask subTask, int finish){
        if(task.getCallback() == 1){
            log.info("TaskId:{}, jobId:{}, task has callback request, subTask not process", task.getId(), subTask.getId());
            return;
        }
        if (subTask.getCallback() == 1){
            log.info("TaskId:{}, jobId:{}, subTask callback repeat", task.getId(), subTask.getId());
            return;
        }

        callbackService.callbackJob(task, subTask, 1, finish);
        checkCallbackCount(task);
        doAfter(subTask);
    }

    public void checkCallbackCount(Task task){
        int jobTotal = task.getJobCount();
        int callbackCount = subTaskService.countByCallbackAndTaskId(task.getId());

        if(callbackCount == jobTotal){
            //发送到mq
            TaskMqBo taskMqBo = new TaskMqBo();
            taskMqBo.setTaskId(task.getId());
            taskMqBo.setFinish(1);
            mqService.sendToTaskCallbackRepeat(taskMqBo, task.getPriority());
        }

    }

    public void doAfter(SubTask subTask){
        subTaskService.removeSubTaskAsr(subTask.getId());
        redisService.deleteAttributeJobCallback(subTask.getId());
    }

}
