package com.cmic.service;

import com.cmic.config.ApplicationProperties;
import com.cmic.config.xml.ActionXml;
import com.cmic.config.xml.Dom4jUtil;
import com.cmic.config.xml.EventXml;
import com.cmic.pojo.entity.Process;
import com.cmic.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Slf4j
@Service
public class XmlEventLoaderService {
    @Autowired
    ApplicationProperties config;
    @Autowired
    private ProcessService processService;

    private Map<String, ActionXml> exportActionMap = new HashMap<>();
    private Map<String, EventXml> exportEventMap = new HashMap<>();
    private Map<String, List<EventXml>> serviceEventsMap = new HashMap<>();

    @PostConstruct
    public void init(){
        loadExportXml();
        loadEventsXml();
    }

    private void loadExportXml(){
        String path = config.getXml().getPath();
        String exportPath = path+"export";
        File directory = new File(exportPath);
        File[] files = directory.listFiles();
        if(files == null){
            log.info("Dir is null:{}", exportPath);
            return;
        }
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (file.isFile() && name.endsWith(".xml")) {
                Dom4jUtil.doExportXml(file, exportEventMap, exportActionMap);
            }
        }
    }

    private void loadEventsXml(){
        List<File> files = this.getFiles();
        if(files == null){
            return;
        }
        Collection<Process> list = processService.getProcessMap().values();
        for(Process process: list){
            loadEventXml(process, files);
        }
    }

    private List<File> getFiles(){
        String path = config.getXml().getPath();
        File directory = new File(path);
        List<File> files = new ArrayList<>();
        CommonUtil.scanFiles(directory, "export", files);
        return files;
    }

    private void loadEventXml(Process process, List<File> files){
        String processId = process.getProcessId();
        String filename = process.getXmlFileName();
        if(!filename.endsWith(".xml")){
            filename = filename + ".xml";
        }

        File file = CommonUtil.findFileByName(files, filename);
        if(file == null){
            log.error("ProcessId:{}, filename:{} not exists", processId, filename);
            return;
        }
        List<EventXml> eventXmlList = Dom4jUtil.doEventXml(file, exportEventMap);
        serviceEventsMap.put(processId, eventXmlList);
    }

    public void reloadFile(String processId, File file){
        List<EventXml> eventXmlList = Dom4jUtil.doEventXml(file, exportEventMap);
        serviceEventsMap.put(processId, eventXmlList);
    }

    /**
     * 获取processId 对应的 event list
     * @param processId
     * @return
     */
    public List<EventXml> getEventXmls(String processId){
        if(!serviceEventsMap.containsKey(processId)){
            loadEventByProcessId(processId);
        }
        return serviceEventsMap.get(processId);
    }

    public EventXml getExportEventXml(String name){
        return exportEventMap.get(name);
    }

    public ActionXml getExportActionXml(String name){
        return exportActionMap.get(name);
    }

    public void loadEventByProcessId(String processId){
        Process process = processService.findEnableByProcessId(processId);
        if(process == null){
            log.info("ProcessId:{}, is null in db", processId);
            return;
        }
        if(!serviceEventsMap.containsKey(processId)){
            List<File> files = this.getFiles();
            loadEventXml(process, files);
        }
    }

    public void reloadEventByProcessId(Process process){
        List<File> files = this.getFiles();
        loadEventXml(process, files);
    }
}
