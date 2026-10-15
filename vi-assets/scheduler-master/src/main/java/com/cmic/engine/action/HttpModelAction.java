package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.bo.Attribute;
import com.cmic.pojo.dto.ai.DyHttpRequest;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.ai.HttpModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class HttpModelAction implements Action{
    @Autowired
    private HttpModelService httpModelService;
    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if(op.equalsIgnoreCase("sendToMq")){
            this.sendToMq(actionXml, eventOrigin, obj);
        }
    }

    public void sendToMq(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        MqEntityEvent event = (MqEntityEvent) obj[0];
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String taskId = String.valueOf(task.getId());
        String jobId = String.valueOf(subTask.getId());

        String model = actionXml.getAttributeValue("model");
        AttributeXml asrTextXml = actionXml.getAttribute("asrText");

        String asrTextParam = asrTextXml==null?"text":asrTextXml.getValue().trim();
        Map<String, Object> customizedParam = (Map<String, Object>) task.getAttributes().get("httpParam");
        DyHttpRequest request = httpModelService.getRequest(taskId, jobId, model, customizedParam, asrTextParam);

        String asrText = (String)subTask.getResponseResults().get(Attribute.ASR_TEXT.getName());
        request.getRequestParameters().put(asrTextParam, asrText);

        httpModelService.sendToMq(task, subTask, request);
    }
}
