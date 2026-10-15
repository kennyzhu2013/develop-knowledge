package com.cmic.engine.action;

import com.cmic.config.xml.ActionXml;
import com.cmic.engine.event.EventOrigin;
import com.cmic.engine.event.MqEntityEvent;
import com.cmic.service.ai.KeywordAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class KeywordAnalysisAction implements Action{
    @Resource
    private KeywordAnalysisService keywordAnalysisService;
    @Override
    public void validate(ActionXml obj) throws Exception {

    }

    @Override
    public void apply(ActionXml actionXml, EventOrigin eventOrigin, Object... obj) throws Exception {
        String op = actionXml.getOp();
        if(op.equalsIgnoreCase("sendToMq")){
            MqEntityEvent event = (MqEntityEvent) obj[0];
            keywordAnalysisService.sendToMq(event, KeywordAnalysisService.HIGH_THRESH);
        }
    }
}
