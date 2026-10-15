package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.pojo.entity.SubTask;
import com.cmic.pojo.entity.Task;
import com.cmic.service.AttributeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class AttributesAction implements Action{
    @Autowired
    private AttributeService attributeService;
    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        MqEntityEvent event = (MqEntityEvent) obj[0];
        SubTask subTask = event.getSubTask();
        Task task = event.getTask();

        String op = actionXml.getOp();
        if(op.equalsIgnoreCase("set")){
            List<AttributeXml> attributes = actionXml.getAttributes();
            attributeService.setValue(task, subTask, actionXml,  attributes);
        }
    }
}
