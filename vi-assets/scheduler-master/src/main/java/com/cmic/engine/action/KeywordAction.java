package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.service.KeywordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KeywordAction implements Action{
    @Autowired
    private KeywordService keywordService;

    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        switch (op){
            case "match":
                this.match(actionXml, eventOrigin, obj);
                break;
        }
    }

    public void match(ActionXml actionXml, EventOrigin eventOrigin, Object... obj){
        MqEntityEvent event = (MqEntityEvent) obj[0];
        keywordService.match(event);
    }

}
