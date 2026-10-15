package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.JobService;
import com.cmic.service.task.SubTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class ProcessAction implements Action {
    @Autowired
    private JobService jobService;
    @Autowired
    private SubTaskService subTaskService;

    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if(op.equalsIgnoreCase("stop")){
            stop(actionXml, eventOrigin, obj);
        }
        if(op.equalsIgnoreCase("success")){
            success(actionXml, eventOrigin, obj);
        }
        if(op.equalsIgnoreCase("fail")){
            fail(actionXml, eventOrigin, obj);
        }
    }

    public void stop(ActionXml actionXml, EventOrigin eventOrigin, Object... obj){
        MqEntityEvent event = (MqEntityEvent) obj[0];
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();
        jobService.jobFinished(task, subTask, 2);
    }

    public void success(ActionXml actionXml, EventOrigin eventOrigin, Object... obj){
        MqEntityEvent event = (MqEntityEvent) obj[0];
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();
        jobService.jobFinished(task, subTask, 1);
    }

    public void fail(ActionXml actionXml, EventOrigin eventOrigin, Object... obj){
        MqEntityEvent event = (MqEntityEvent) obj[0];
        Task task = event.getTask();
        SubTask subTask = event.getSubTask();

        if(!CollectionUtils.isEmpty(actionXml.getAttributes())){
            String model = actionXml.getAttribute("model").getValue();
            String errorMessage = actionXml.getAttribute("errorMessage").getValue();
            subTaskService.updateSubTaskErrorMessage(subTask, model, errorMessage);
        }

        jobService.jobFinished(task, subTask, 0);
    }
}
