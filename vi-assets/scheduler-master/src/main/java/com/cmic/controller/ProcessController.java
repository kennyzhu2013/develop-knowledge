package com.cmic.controller;

import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.dto.HisProcessPageResponse;
import com.cmic.pojo.dto.ProcessPageResponse;
import com.cmic.pojo.dto.ProcessRequest;
import com.cmic.pojo.entity.Process;
import com.cmic.service.ProcessService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("admin")
public class ProcessController {
    @Autowired
    private ProcessService processService;
    @Autowired
    private Validator validator;

    @PostMapping("/process/create")
    @ResponseBody
    public BaseResponse processCreate(@Validated
                                          @RequestParam("xmlFile") MultipartFile file,
                                      @ModelAttribute ProcessRequest request) {
        try {
            Set<ConstraintViolation<ProcessRequest>> violations = validator.validate(request);


            if (!violations.isEmpty()) {
                StringBuilder errors = new StringBuilder();
                for (ConstraintViolation<ProcessRequest> violation : violations) {
                    errors.append(violation.getPropertyPath()).append(": ").append(violation.getMessage()).append("\n");
                }

                if(StringUtils.hasLength(errors)){
                    return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), errors.toString());
                }
            }
            String requestId = request.getRequestId();
            if(processService.isRepeat(requestId)){
                log.info("RequestId:{} is repeat", requestId);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "RequestId:"+requestId+" is repeat");
            }
            if(processService.isRepeatXmlFileName(request.getXmlFileName())){
                log.info("XmlFileName:{} is repeat", request.getXmlFileName());
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "XmlFileName:"+request.getXmlFileName()+" is repeat");
            }

            String processId = processService.save(request);
            Map<String, String > map = new HashMap<>();
            map.put("processId", processId);
            processService.upload(file, request.getXmlFileName());

            return BaseResponse.constructResponse(ErrorCode.SUCCESS, map);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @GetMapping("/process/query")
    @ResponseBody
    public BaseResponse processQuery(
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String xmlFileName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size){
        try {
            ProcessPageResponse response = processService.findByCriteria(processId,processName,status,xmlFileName, page, size);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS, response);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @GetMapping("/process/history/query")
    @ResponseBody
    public BaseResponse hisProcessQuery(
            @RequestParam String processId,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String xmlFileName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size){
        try {
            if(!StringUtils.hasLength(processId)){
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "processId is required");
            }
            HisProcessPageResponse response = processService.findHisProcessByCriteria(processId, processName,status, xmlFileName, page, size);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS, response);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/process/update")
    @ResponseBody
    public BaseResponse processUpload(
            @RequestParam(name = "xmlFile", required = false) MultipartFile file,
            @RequestParam String processId,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer timeout,
            @RequestParam(required = false) String aiModels) {
        try {
            Process process = processService.findByProcessId(processId);
            if(process == null){
                log.info("ProcessId:{} does not exist", processId);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "ProcessId:"+processId+" does not exist");
            }
            if(StringUtils.hasLength(processName)){
                process.setProcessName(processName);
            }
            if(status != null && (status ==0 || status == 1)){
                process.setStatus(status);
            }
            if(timeout != null && timeout>=1 && timeout<=60){
                process.setTimeout(timeout);
            }
            if(StringUtils.hasLength(aiModels)){
                process.setAiModels(aiModels);
            }
            processService.update(process, file);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @GetMapping(value = "/process/queryXml/{processId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> showXmlFile(@PathVariable String processId) throws IOException {
        Process process = processService.findByProcessId(processId);
        if(process == null){
            log.info("ProcessId:{} does not exist", processId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ProcessId:"+processId+" does not exist");
        }
        return processService.queryXmlFile(process);
    }
}
