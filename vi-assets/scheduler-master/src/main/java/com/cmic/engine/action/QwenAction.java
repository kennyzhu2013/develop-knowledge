package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ai.DyHttpRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ai.QwenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QwenAction implements Action {

    @Autowired
    private QwenService qwenService;

    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if (op.equalsIgnoreCase("sendToMq")) {
            MqEntityEvent event = (MqEntityEvent) obj[0];
            this.sendToMq(actionXml, eventOrigin, obj);
        }
    }

    public void sendToMq(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        MqEntityEvent event = (MqEntityEvent) obj[0];
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        Map<String, String> xmlAttribute = actionXml.getAttributeMap();
        DyHttpRequest request = qwenService.getRequest(task, subTask, xmlAttribute);
        qwenService.sendToMq(task, subTask, request);
    }
}
