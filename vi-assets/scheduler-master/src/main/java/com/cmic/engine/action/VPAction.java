package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.AttributeXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.service.ai.VPService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VPAction implements Action {
    @Autowired
    private VPService vpService;

    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if (op.equalsIgnoreCase("sendToMq")) {
            MqEntityEvent event = (MqEntityEvent) obj[0];
            AttributeXml typeAttribute = actionXml.getAttribute("type");
            int type = Integer.parseInt(typeAttribute.getValue());
            vpService.sendToMq(event, type);
        }
    }
}
