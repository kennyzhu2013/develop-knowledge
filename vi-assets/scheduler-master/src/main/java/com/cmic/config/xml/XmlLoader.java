package com.cmic.config.xml;

import com.cmic.config.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import javax.annotation.Resource;
import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

//@Configuration
@Slf4j
public class XmlLoader implements ApplicationRunner {
    @Resource
    ApplicationProperties config;
    //(service_id, events)
    private Map<String, ActionXml> exportActionMap = new HashMap<>();
    private Map<String, EventXml> exportEventMap = new HashMap<>();
    private Map<String, List<EventXml>> serviceEventsMap = new HashMap<>();

    public List<EventXml> getEventXmls(String serviceId){
        return serviceEventsMap.get(serviceId);
    }

    public EventXml getExportEventXml(String name){
        return exportEventMap.get(name);
    }

    public ActionXml getExportActionXml(String name){
        return exportActionMap.get(name);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String path = config.getXml().getPath();
        String exportPath = path+"export";
        loadExportXml(exportPath);
        loadXml(path);
    }

    private void loadExportXml(String path){
        File directory = new File(path);
        File[] files = directory.listFiles();
        if(files == null){
            log.info("Dir is null:{}", path);
            return;
        }
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && name.endsWith(".xml")) {
                doExportXml(file);
            }

        }
    }

    private void doExportXml(File file){
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

    private void loadXml(String path){
        File directory = new File(path);
        File[] files = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if(name.contains("export")){
                    return false;
                }else {
                    return true;
                }
            }
        });
        if(files == null){
            log.info("Dir is null:{}", path);
            return;
        }
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && name.endsWith(".xml")) {
                String serviceId = name.substring(0, name.indexOf('.'));
                List<EventXml> eventXmls = doEventXml(file);
                serviceEventsMap.put(serviceId, eventXmls);
            }
        }
    }

    private List<EventXml> doEventXml(File file){
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
                    doEventImport(eventElement, eventXmlList);
                }
            }
            return eventXmlList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void doEventImport(Element eventElement, List<EventXml> eventXmlList){
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

    public static void main(String[] args) {
        String path = "sources/";
        String exportPath = path+"export";
        XmlLoader xmlLoader = new XmlLoader();
        xmlLoader.loadExportXml(exportPath);
        xmlLoader.loadXml(path);
        System.out.println("");
    }
}
