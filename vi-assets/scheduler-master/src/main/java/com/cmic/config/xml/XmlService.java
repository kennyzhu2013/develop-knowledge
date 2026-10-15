package com.cmic.config.xml;

import com.cmic.engine.action.Action;
import com.cmic.engine.action.ActionFactory;
import com.cmic.engine.condition.Condition;
import com.cmic.engine.condition.ConditionFactory;
import com.cmic.engine.event.EventOrigin;
import com.cmic.service.XmlEventLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

@Slf4j
@Service
public class XmlService {
    @Autowired
    private XmlEventLoaderService loaderService;
    @Autowired
    private ActionFactory actionFactory;
    @Autowired
    private ConditionFactory conditionFactory;

    public void handlerEvent(String processId, String eventName, EventOrigin eventOrigin, Object... objs) throws Exception{
        List<EventXml> eventXmlList = loaderService.getEventXmls(processId);
        EventXml eventXml = matchEventXml(eventXmlList, eventName);
        if (eventXml == null) {
            log.error("ProcessId:{}, event:{} not exist", processId, eventName);
            return;
        }
        doEvent(eventXml, eventOrigin, objs);
    }

    public void doEvent(EventXml eventXml, EventOrigin eventOrigin, Object... objs) throws Exception{
        Queue<ObjXml> queue = eventXml.getQueue();
        for(ObjXml objXml : queue){
            if(objXml instanceof ActionXml){
                ActionXml actionXml = (ActionXml) objXml;
                doAction(actionXml, eventOrigin, objs);
            }
            if(objXml instanceof ConditionXml){
                ConditionXml conditionXml = (ConditionXml) objXml;
                doCondition(conditionXml, eventOrigin, objs);
            }
            if(objXml instanceof ImportXml){
                ImportXml importXml = (ImportXml) objXml;
                doImport(importXml, eventOrigin, objs);
            }
        }
    }

    public void doAction(ActionXml actionXml, EventOrigin eventOrigin, Object... objs) throws Exception{
        String application = actionXml.getApplication();
        Action action = actionFactory.action(application);
        action.validate(actionXml);
        action.apply(actionXml, eventOrigin, objs);
    }

    public void doCondition(ConditionXml conditionXml, EventOrigin eventOrigin, Object... objs) throws Exception{
        Queue<ObjXml> queue = new ArrayDeque<>();

        Queue<IfElseXml> ifElseXmlQueue = conditionXml.getQueue();
        for(IfElseXml ifElseXml:ifElseXmlQueue){
            if(!StringUtils.hasLength(ifElseXml.getExpression())){
                queue = ifElseXml.getQueue();
                break;
            }else{
                Condition condition = conditionFactory.getCondition(ifElseXml);
                if (condition.match(ifElseXml, eventOrigin, objs)) {
                    queue = ifElseXml.getQueue();
                    break;
                }
            }

        }

        for(ObjXml objXml : queue){
            if(objXml instanceof ActionXml){
                ActionXml actionXml = (ActionXml) objXml;
                doAction(actionXml, eventOrigin, objs);
            }
            if(objXml instanceof ImportXml){
                ImportXml importXml = (ImportXml) objXml;
                doImport(importXml, eventOrigin, objs);
            }
        }
    }

    public void doImport(ImportXml importXml, EventOrigin eventOrigin, Object... objs) throws Exception{
        String type = importXml.getType();
        String name = importXml.getName();
        if(type.equals("action")){
            ActionXml actionXml = loaderService.getExportActionXml(name);
            doAction(actionXml, eventOrigin, objs);
        }
    }

    private EventXml matchEventXml(List<EventXml> eventXmlList, String eventName){
        for(EventXml eventXml : eventXmlList){
            if(eventXml.getName().equals(eventName)){
                return eventXml;
            }
        }
        return null;
    }
}
