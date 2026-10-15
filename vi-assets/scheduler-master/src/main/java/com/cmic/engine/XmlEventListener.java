package com.cmic.engine;

import com.cmic.config.xml.XmlService;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.HttpEvent;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.engine.event.MqEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class XmlEventListener {
    @Autowired
    XmlService xmlService;
    @Resource
    private TaskService taskService;

    @EventListener(MqEvent.class)
    public void mqEvent(MqEvent event) throws Exception {
        String eventName = (String) event.getSource();
        String jobId = event.getJobId();
        String taskId = event.getTaskId();
        try {
            log.info("TaskId:{}, jobId:{}, eventName:{}", taskId, jobId, eventName);

            Task task = taskService.getTaskById(Long.parseLong(taskId));
            String processId = task.getProcessId();
            xmlService.handlerEvent(processId, eventName, EventOrigin.MQ, event);
        } catch (Exception e) {
            log.error("exec action error:"+e);
            throw e;
        }

    }

    @EventListener(HttpEvent.class)
    public void httpEvent(HttpEvent event) throws Exception {
        String eventName = (String) event.getSource();
        String processId = event.getProcessId();
        try {
            xmlService.handlerEvent(processId, eventName, EventOrigin.HTTP, event);
        } catch (Exception e) {
            log.error("exec action error:", e);
            throw e;
        }
    }

    @EventListener(MqEntityEvent.class)
    public void mqEntityEvent(MqEntityEvent event) throws Exception{
        String eventName = (String) event.getSource();
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();
        log.info("TaskId:{}, jobId:{}, eventName:{}", task.getId(), subTask.getId(), eventName);
        try {
            String processId = task.getProcessId();
            xmlService.handlerEvent(processId, eventName, EventOrigin.MQ, event);
        } catch (Exception e) {
            log.error("exec action error:", e);
            throw e;
        }
    }
}
