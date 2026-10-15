package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EventAction  implements Action{
    @Autowired
    private XmlEventPublisher xmlEventPublisher;
    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if(op.equalsIgnoreCase("sendToEvent")){
            sendToEvent(actionXml, eventOrigin, obj);
        }
    }

    public void sendToEvent(ActionXml actionXml, EventOrigin eventOrigin, Object... obj){
        String eventName = actionXml.getData();

        MqEntityEvent mqEvent = (MqEntityEvent) obj[0];
        Task task = mqEvent.getTask();
        SubTask subTask = mqEvent.getSubTask();

        log.info("TaskId:{}, jobId:{}, publish event:{}", task.getId(), subTask.getId(), eventName);
        xmlEventPublisher.mqEventPublish(eventName, task, subTask);
    }
}
