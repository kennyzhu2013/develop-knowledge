package com.cmic.engine;

import com.cmic.engine.event.HttpEvent;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class XmlEventPublisher {
    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    private SubTaskService subTaskService;

    @Async("eventExecutor")
    public void mqEventAsyncPublish(String eventName, String jobId) {
        String taskId = String.valueOf(subTaskService.getSubTaskByJobId(jobId).getTaskId());
        this.mqEventPublish(eventName, taskId, jobId);
    }

    @Async("eventExecutor")
    public void mqEventAsyncPublish(String eventName, Task task, SubTask subTask) {
        MqEntityEvent mqEntityEvent = new MqEntityEvent(eventName);
        mqEntityEvent.setTask(task);
        mqEntityEvent.setSubTask(subTask);
        this.eventPublish(mqEntityEvent);
    }

    public void mqEventPublish(String eventName, String jobId) {
        String taskId = String.valueOf(subTaskService.getSubTaskByJobId(jobId).getTaskId());
        this.mqEventPublish(eventName, taskId, jobId);
    }

    public void mqEventPublish(String eventName, Task task, SubTask subTask) {
        MqEntityEvent mqEntityEvent = new MqEntityEvent(eventName);
        mqEntityEvent.setTask(task);
        mqEntityEvent.setSubTask(subTask);
        this.eventPublish(mqEntityEvent);
    }

    public void mqEventPublish(String eventName, String taskId, String jobId){
        MqEvent mqEvent = new MqEvent(eventName);
        mqEvent.setJobId(jobId);
        mqEvent.setTaskId(taskId);
        this.eventPublish(mqEvent);
    }

    public void httpEventPublish(String eventName, String processId){
        HttpEvent event = new HttpEvent(eventName);
        event.setProcessId(processId);
        eventPublish(event);
    }

    public void eventPublish(ApplicationEvent event) {
        applicationContext.publishEvent(event);
    }
}
