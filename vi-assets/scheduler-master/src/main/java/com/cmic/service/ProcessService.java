package com.cmic.service;

import cn.hutool.core.util.RandomUtil;
import com.cmic.config.ApplicationProperties;
import com.cmic.config.rabbitmq.ProcessSender;
import com.cmic.dao.HisProcessDaoImpl;
import com.cmic.dao.IHisProcessDao;
import com.cmic.dao.IProcessDao;
import com.cmic.dao.ProcessDaoImpl;
import com.cmic.pojo.dto.HisProcessPageResponse;
import com.cmic.pojo.dto.ProcessPageResponse;
import com.cmic.pojo.dto.ProcessRequest;
import com.cmic.pojo.entity.HisProcess;
import com.cmic.pojo.entity.Process;
import com.cmic.util.SnowflakeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProcessService{
    @Autowired
    private IProcessDao iProcessDao;
    @Autowired
    private IHisProcessDao iHisProcessDao;
    @Autowired
    private HisProcessDaoImpl hisProcessDao;
    @Autowired
    private ProcessDaoImpl processDao;
    @Resource
    ApplicationProperties config;
    @Value("${spring.profiles.active}")
    public String env;
    @Autowired
    @Lazy
    private XmlEventLoaderService xmlEventLoaderService;
    @Autowired
    private ProcessSender processSender;

    private Map<String, Process> processMap = new HashMap<>();

    @PostConstruct
    public void init(){
        loadAll();
    }

    public void loadAll(){
        List<Process> list = iProcessDao.findAllByStatus(1);
        for(Process process:list){
            processMap.put(process.getProcessId(), process);
        }
    }

    public List<Process> findAll(){
        return iProcessDao.findAll();
    }

    public long count(){
        return iProcessDao.count();
    }

    public ProcessPageResponse findByCriteria(String processId, String processName, Integer status, String xmlFileName, int page, int size){
        return processDao.findByCriteria(processId, processName, status, xmlFileName, page, size);
    }

    public HisProcessPageResponse findHisProcessByCriteria(String processId, String processName, Integer status,  String xmlFileName, int page, int size){
        return hisProcessDao.findByCriteria(processId, processName,status, xmlFileName, page, size);
    }

    public Process findEnableByProcessId(String processId){
        if(processMap.containsKey(processId)){
            return processMap.get(processId);
        }
        Process process = iProcessDao.findByProcessIdAndStatus(processId, 1);
        if(process == null){
            log.info("ProcessId:{}, is null in db", processId);
            return null;
        }
        processMap.put(process.getProcessId(), process);
        return process;
    }

    public Process findByProcessId(String processId){
        return iProcessDao.findByProcessId(processId);
    }

    public Map<String, Process> getProcessMap() {
        return processMap;
    }

    public boolean isRepeat(String requestId){
        Process process = iProcessDao.findByRequestId(requestId);
        if(process == null){
            return false;
        }
        return true;
    }

    public boolean isRepeatXmlFileName(String xmlFileName){
        Process process = iProcessDao.findByXmlFileName(xmlFileName);
        if(process == null){
            return false;
        }
        return true;
    }

    public String save(ProcessRequest request){
        String randomCode = getProcessId();
        LocalDateTime now = LocalDateTime.now();
        Process process = new Process();
        process.setRequestId(request.getRequestId());
        process.setUpdateTime(now);
        process.setCreateTime(now);
        process.setXmlFileName(request.getXmlFileName());
        process.setAiModels(request.getAiModels());
        process.setTimeout(request.getTimeout());
        process.setProcessId(randomCode);
        process.setProcessName(request.getProcessName());
        process.setStatus(1);
        iProcessDao.save(process);
        return process.getProcessId();
    }

    private String getProcessId(){
        long id = SnowflakeUtils.generateId();
        String randomCode = "p"+RandomUtil.randomString(RandomUtil.BASE_CHAR_NUMBER, 6) + id;
        return randomCode;
    }

    public void update(Process process, MultipartFile file) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 获取当前process数据作为历史数据基础
        Process currentProcess = iProcessDao.findByProcessId(process.getProcessId());
        
        // 2. 读取xmlFileName对应的本地xml文件内容
        String root = config.getXml().getPath();
        String xmlFilePath = root + File.separator + env + File.separator + currentProcess.getXmlFileName() + ".xml";
        String xmlText = new String(Files.readAllBytes(Paths.get(xmlFilePath)));
        // 3. 确定新历史记录的startTime
        LocalDateTime startTime=currentProcess.getUpdateTime();
        
        // 4. 创建历史记录，endTime为当前时间
        HisProcess newHisProcess = HisProcess.builder()
                .requestId(currentProcess.getRequestId())
                .processId(currentProcess.getProcessId())
                .processName(currentProcess.getProcessName())
                .xmlFileName(currentProcess.getXmlFileName())
                .timeout(currentProcess.getTimeout())
                .aiModels(currentProcess.getAiModels())
                .status(currentProcess.getStatus())
                .xmlText(xmlText)
                .startTime(startTime)
                .endTime(now)
                .build();
        iHisProcessDao.save(newHisProcess);
        
        // 5. 更新process表，updateTime为当前时间
        process.setUpdateTime(now);
        iProcessDao.save(process);


        // 6. 处理新的xml文件上传和重载
        if (file != null && !file.isEmpty()) {
            File newFile = upload(file, process.getXmlFileName());
            xmlEventLoaderService.reloadFile(process.getProcessId(), newFile);
        }
        processSender.broadcastProcessUpdateMessage(process.getProcessId());
    }

    public File upload(MultipartFile file,  String xmlFileName) throws Exception {
        String root = config.getXml().getPath();
        File dir = new File(root + File.separator + env);
        if (!dir.exists()) {
            dir.mkdir();
        }
        File newFile = new File(dir.getAbsolutePath() + File.separator + xmlFileName + ".xml");
        file.transferTo(newFile);
        return newFile;
    }

    public ResponseEntity<String> queryXmlFile(Process process){
        String root = config.getXml().getPath();
        String xmlFile = root + File.separator + env + File.separator + process.getXmlFileName() + ".xml";

        try {
            // 读取文件内容为字符串
            String content = new String(Files.readAllBytes(Paths.get(xmlFile)));
            return ResponseEntity.ok(content);
        } catch (NoSuchFileException e) {
            // 如果文件未找到，返回 404 错误
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        } catch (IOException e) {
            // 其他 IO 错误，返回 500 错误
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading file");
        }
    }

}
