package com.cmic.controller;

import com.cmic.pojo.dto.*;
import com.cmic.pojo.entity.AIModel;
import com.cmic.pojo.entity.AIType;
import com.cmic.service.AIModelService;
import com.cmic.service.AITypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("admin/aiModel")
public class AIModelController {
    @Autowired
    private AIModelService aiModelService;
    @Autowired
    private AITypeService aiTypeService;

    @PostMapping("/http/create")
    @ResponseBody
    public BaseResponse httpModelCreate(@RequestBody @Validated HttpModelRequest request) {
        try {
            AIType aiType = aiTypeService.findById(request.getAiTypeId());
            if (aiType == null) {
                log.info("AiTypeId:{} does not exist", request.getAiTypeId());
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "AiTypeId:" + request.getAiTypeId() + " does not exist");
            }
            AIModel aiModel = aiModelService.queryByModel(request.getModel());
            if(aiModel != null){
                log.info("Model:{} is repeat", request.getModel());
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Model:" + request.getModel() + " is repeat");
            }

            aiModel = AIModel.builder()
                    .model(request.getModel())
                    .desc(request.getDesc())
                    .requestParameters(request.getRequestParameters())
                    .httpUrl(request.getHttpUrl())
                    .responseParameters(request.getResponseParameters())
                    .type(aiType.getName())
                    .authentication(request.getAuthentication())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            aiModelService.save(aiModel);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/http/update")
    @ResponseBody
    public BaseResponse httpModelUpdate(@RequestBody HttpModelRequest request) {
        try {
            String model = request.getModel();
            if(!StringUtils.hasLength(model)){
                log.info("model cannot be empty");
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "model cannot be empty");
            }
            AIModel aiModel = aiModelService.queryByModel(model);
            if(aiModel == null){
                log.info("Model:{} does not exist", model);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Model:" + model + " does not exist");
            }
            Long aiTypeId = request.getAiTypeId();
            if(aiTypeId != null){
                AIType aiType = aiTypeService.findById(aiTypeId);
                if(aiType == null){
                    log.info("AiTypeId:{} does not exist", aiTypeId);
                    return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "AiTypeId:"+aiTypeId+" does not exist");
                }
                aiModel.setType(aiType.getName());
            }

            String desc = request.getDesc();
            if(StringUtils.hasLength(desc)){
                aiModel.setDesc(desc);
            }
            String httpUrl = request.getHttpUrl();
            if(StringUtils.hasLength(httpUrl)){
                aiModel.setHttpUrl(httpUrl);
            }
            Map<String, Object> authentication = request.getAuthentication();
            if(!CollectionUtils.isEmpty(authentication)){
                aiModel.setAuthentication(authentication);
            }
            List<String> requestParameters = request.getRequestParameters();
            if(!CollectionUtils.isEmpty(requestParameters)){
                aiModel.setRequestParameters(requestParameters);
            }
            List<String> responseParameters = request.getResponseParameters();
            if(!CollectionUtils.isEmpty(responseParameters)){
                aiModel.setResponseParameters(responseParameters);
            }
            aiModel.setUpdateTime(LocalDateTime.now());
            aiModelService.save(aiModel);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @GetMapping("/http/query")
    @ResponseBody
    public BaseResponse httpModelQuery(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Long aiTypeId,
            @RequestParam(required = false) String desc,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size){
        try {
            AIType aiType = null;
            if(aiTypeId != null){
                aiType = aiTypeService.findById(aiTypeId);
                if(aiType == null){
                    log.info("AiTypeId:{} does not exist", aiTypeId);
                    return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "AiTypeId:"+aiTypeId+" does not exist");
                }
            }

            AiModelPageResponse response = aiModelService.findByCriteria(model,aiType,desc, page, size);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS, response);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/http/delete/{model}")
    @ResponseBody
    public BaseResponse httpModelDelete(@PathVariable String model) {
        try {
            AIModel aiModel = aiModelService.queryByModel(model);
            if(aiModel == null){
                log.info("Model:{} does not exist", model);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Model:" + model + " does not exist");
            }
            aiModelService.delete(aiModel);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/create")
    @ResponseBody
    public BaseResponse modelCreate(@RequestBody @Validated ModelRequest request) {
        try {
            AIModel aiModel = aiModelService.queryByModel(request.getModel());
            if(aiModel != null){
                log.info("Model:{} is repeat", request.getModel());
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Model:" + request.getModel() + " is repeat");
            }

            aiModel = AIModel.builder()
                    .model(request.getModel())
                    .desc(request.getDesc())
                    .requestParameters(request.getRequestParameters())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            aiModelService.save(aiModel);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }
}
