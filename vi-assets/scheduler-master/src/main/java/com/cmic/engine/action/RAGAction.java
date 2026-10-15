package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ai.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RAGAction implements Action {

    @Autowired
    private RAGService ragService;

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

        AttributeXml textXml = actionXml.getAttribute("text");
        String textParam = textXml == null ? Attribute.ASR_TEXT.getName() : textXml.getValue().trim();
        String text = (String) subTask.getResponseResults().get(textParam);

        AttributeXml kbTypeXml = actionXml.getAttribute("kbType");
        String kbType = kbTypeXml == null ? null : kbTypeXml.getValue().trim();
        ragService.sendToMq(event, text, kbType);
    }
}
