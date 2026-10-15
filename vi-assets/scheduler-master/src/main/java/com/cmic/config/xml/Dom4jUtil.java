package com.cmic.config.xml;

import lombok.extern.slf4j.Slf4j;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.*;

@Slf4j
public class Dom4jUtil {

    public static void doExportXml(File file, Map<String, EventXml> exportEventMap, Map<String, ActionXml> exportActionMap){
        SAXReader saxReader = new SAXReader();
        saxReader.setEncoding("UTF-8");
        try {
            Document document = saxReader.read(file);
            Element rootElement = document.getRootElement();
            Iterator iterator = rootElement.elementIterator();
            while (iterator.hasNext()) {
                Element exportElement = (Element) iterator.next();
                List<Attribute> attributes = exportElement.attributes();
                String type = null;
                String name = null;
                for (Attribute attribute : attributes) {
                    if (attribute.getName().equals("type")) {
                        type = attribute.getStringValue();
                    }
                    if (attribute.getName().equals("name")) {
                        name = attribute.getStringValue();
                    }
                }

                Iterator subIterator = exportElement.elementIterator();
                while (subIterator.hasNext()){
                    Element dataElement = (Element) subIterator.next();
                    if(type.equals("action")){
                        ActionXml actionXml = Dom4jUtil.doActionXml(dataElement);
                        exportActionMap.put(name, actionXml);
                    }
                    if(type.equals("event")){
                        EventXml eventXml = Dom4jUtil.doEventXml(dataElement);
                        exportEventMap.put(name, eventXml);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<EventXml> doEventXml(File file, Map<String, EventXml> exportEventMap){
        SAXReader saxReader = new SAXReader();
        saxReader.setEncoding("UTF-8");
        try {
            Document document = saxReader.read(file);
            Element rootElement = document.getRootElement();
            Iterator iterator = rootElement.elementIterator();
            List<EventXml> eventXmlList = new ArrayList<>();
            while (iterator.hasNext()) {
                Element eventElement = (Element) iterator.next();
                String subName = eventElement.getName();
                if(subName.equals("event")){
                    EventXml eventXml = Dom4jUtil.doEventXml(eventElement);
                    eventXmlList.add(eventXml);
                }
                if(subName.equals("import")){
                    Dom4jUtil.doEventImport(eventElement, eventXmlList, exportEventMap);
                }
            }
            return eventXmlList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void doEventImport(Element eventElement, List<EventXml> eventXmlList, Map<String, EventXml> exportEventMap){
        ImportXml importXml = Dom4jUtil.doImport(eventElement);
        if(!importXml.getType().equals("event")){
            log.info("event element only can configure import event type...");
            return;
        }
        String name = importXml.getName();
        EventXml eventXml = exportEventMap.get(name);
        if(eventXml == null){
            log.info("export eventName:{} is null", name);
        }
        eventXmlList.add(eventXml);
    }



    public static EventXml doEventXml(Element eventElement){
        EventXml eventXml = new EventXml();
        List<Attribute> attributes = eventElement.attributes();
        for(Attribute attribute: attributes){
            String name = attribute.getValue();
            eventXml.setName(name);
        }

        Queue<ObjXml> subQueue = new ArrayDeque<>();
        Iterator eventIterator = eventElement.elementIterator();
        while (eventIterator.hasNext()){
            Element dataElement = (Element) eventIterator.next();
            String subName = dataElement.getName();
            if(subName.equals("action")){
                ActionXml actionXml = doActionXml(dataElement);
                subQueue.add(actionXml);
            } else if (subName.equals("condition")) {
                ConditionXml conditionXml = doCondition(dataElement);
                subQueue.add(conditionXml);
            } else if (subName.equals("import")) {
                ImportXml importXml = doImport(dataElement);
                if(!importXml.getType().equals("action")){
                    log.info("event element only can configure import action type...");
                    continue;
                }
                subQueue.add(importXml);
            }
        }
        eventXml.setQueue(subQueue);
        return eventXml;
    }

    public static ActionXml doActionXml(Element dataElement){
        ActionXml actionXml = new ActionXml();
        List<Attribute> actionAttributes = dataElement.attributes();
        for(Attribute attribute: actionAttributes){
            if(attribute.getName().equals("application")){
                actionXml.setApplication(attribute.getValue());
            }
            if(attribute.getName().equals("op")){
                actionXml.setOp(attribute.getValue());
            }
            if(attribute.getName().equals("data")){
                actionXml.setData(attribute.getValue());
            }
            if(attribute.getName().equals("type")){
                actionXml.setType(attribute.getValue());
            }
        }
        List<ParamXml> paramXmls = new ArrayList<>();
        List<AttributeXml> attributeActionXmls = new ArrayList<>();
        Iterator actionIterator = dataElement.elementIterator();
        while (actionIterator.hasNext()){
            Element actionElement = (Element) actionIterator.next();
            String actionSubName = actionElement.getName();
            if(actionSubName.equals("param")){
                doParams(actionElement, paramXmls);
            }
            if(actionSubName.equals("attribute")){
                AttributeXml attributeXml = new AttributeXml();
                attributeXml.setName(actionElement.attributeValue("name"));
                attributeXml.setValue(actionElement.getStringValue());
                attributeActionXmls.add(attributeXml);
            }
        }
        actionXml.setParams(paramXmls);
        actionXml.setAttributes(attributeActionXmls);
        return actionXml;
    }

    private static void doParams(Element actionElement, List<ParamXml> paramXmls){
        ParamXml paramXml = new ParamXml();
        List<AttributeXml> attributeXmls = new ArrayList<>();
        List<ParamIncludeXml> paramIncludeXmls = new ArrayList<>();
        List<ParamExcludeXml> paramExcludeXmls = new ArrayList<>();
        Iterator paramIterator = actionElement.elementIterator();
        while (paramIterator.hasNext()){
            Element paramElement = (Element) paramIterator.next();
            String paramSubName = paramElement.getName();

            if(paramSubName.equals("attribute")){
                AttributeXml attributeXml = new AttributeXml();
                attributeXml.setName(paramElement.attributeValue("name"));
                attributeXml.setValue(paramElement.getStringValue());
                attributeXmls.add(attributeXml);
            }
            if(paramSubName.equals("include")){
                ParamIncludeXml paramIncludeXml = new ParamIncludeXml();
                paramIncludeXml.setName(paramElement.attributeValue("name"));
                paramIncludeXml.setValue(paramElement.getStringValue());
                paramIncludeXmls.add(paramIncludeXml);
            }
            if(paramSubName.equals("exclude")){
                ParamExcludeXml paramExcludeXml = new ParamExcludeXml();
                paramExcludeXml.setName(paramElement.attributeValue("name"));
                paramExcludeXml.setValue(paramElement.getStringValue());
                paramExcludeXmls.add(paramExcludeXml);
            }

        }
        paramXml.setAttributes(attributeXmls);
        paramXml.setIncludes(paramIncludeXmls);
        paramXml.setExcludes(paramExcludeXmls);
        paramXmls.add(paramXml);
    }

    public static ConditionXml doCondition(Element conditionElement){
        ConditionXml conditionXml = new ConditionXml();
        Iterator eventIterator = conditionElement.elementIterator();
        while (eventIterator.hasNext()) {
            Element dataElement = (Element) eventIterator.next();
            String subName = dataElement.getName();
            IfElseXml ifElseXml = doIf(dataElement);
            conditionXml.getQueue().add(ifElseXml);
        }
        return conditionXml;
    }

    private static IfElseXml doIf(Element dataElement){
        IfElseXml ifElseXml = new IfElseXml();
        List<Attribute> conditionAttributes = dataElement.attributes();
        for(Attribute attribute: conditionAttributes){
            if(attribute.getName().equals("application")){
                ifElseXml.setApplication(attribute.getValue());
            }
            if(attribute.getName().equals("expression")){
                ifElseXml.setExpression(attribute.getValue());
            }
        }
        Queue<ObjXml> actionQueue = new ArrayDeque<>();
        Iterator actionIterator = dataElement.elementIterator();
        while (actionIterator.hasNext()){
            Element actionElement = (Element) actionIterator.next();
            String subName = actionElement.getName();
            if(subName.equals("action")){
                ActionXml actionXml = doActionXml(actionElement);
                actionQueue.add(actionXml);
            } else if (subName.equals("import")) {
                ImportXml importXml = doImport(actionElement);
                if(!importXml.getType().equals("action")){
                    log.info("event element only can configure import action type...");
                    continue;
                }
                actionQueue.add(importXml);
            }

        }
        ifElseXml.setQueue(actionQueue);
        return ifElseXml;
    }

    private static ElseXml doElse(Element dataElement){
        ElseXml elseXml = new ElseXml();
        Queue<ObjXml> actionQueue = new ArrayDeque<>();
        Iterator actionIterator = dataElement.elementIterator();
        while (actionIterator.hasNext()){
            Element actionElement = (Element) actionIterator.next();
            String subName = actionElement.getName();
            if(subName.equals("action")){
                ActionXml actionXml = doActionXml(actionElement);
                actionQueue.add(actionXml);
            } else if (subName.equals("import")) {
                ImportXml importXml = doImport(actionElement);
                if(!importXml.getType().equals("action")){
                    log.info("event element only can configure import action type...");
                    continue;
                }
                actionQueue.add(importXml);
            }

        }
        elseXml.setQueue(actionQueue);
        return elseXml;
    }

    public static ImportXml doImport(Element dataElement){
        ImportXml importXml = new ImportXml();
        List<Attribute> importAttributes = dataElement.attributes();
        for(Attribute attribute: importAttributes){
            if(attribute.getName().equals("type")){
                importXml.setType(attribute.getValue());
            }
            if(attribute.getName().equals("name")){
                importXml.setName(attribute.getValue());
            }
        }
        return importXml;
    }

}
