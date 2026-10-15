package com.cmic.controller;

import com.cmic.pojo.dto.AiTypeRequest;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import com.cmic.pojo.entity.AIType;
import com.cmic.service.AITypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("admin/aiType")
public class AITypeController {
    @Autowired
    private AITypeService aiTypeService;

    @PostMapping("/create")
    @ResponseBody
    public BaseResponse aiTypeCreate(@RequestBody @Validated AiTypeRequest request) {
        try {
            AIType aiType = aiTypeService.findByName(request.getName());
            if(aiType != null){
                log.info("Name:{} is repeat", request.getName());
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "Name:"+request.getName()+" is repeat");
            }
            aiTypeService.save(request);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/update/{aiTypeId}")
    @ResponseBody
    public BaseResponse aiTypeUpdate(@PathVariable Long aiTypeId,
                                     @RequestBody AiTypeRequest request) {
        try {
            AIType aiType = aiTypeService.findById(aiTypeId);
            if(aiType == null){
                log.info("AiTypeId:{} does not exist", aiTypeId);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "AiTypeId:"+aiTypeId+" does not exist");
            }
            if(StringUtils.hasLength(request.getName())){
                aiType.setName(request.getName());
            }
            if(StringUtils.hasLength(request.getNickName())){
                aiType.setNickName(request.getNickName());
            }
            if(StringUtils.hasLength(request.getComment())){
                aiType.setComment(request.getComment());
            }
            aiType.setUpdateTime(LocalDateTime.now());
            aiTypeService.save(aiType);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @GetMapping("/query")
    @ResponseBody
    public BaseResponse aiTypeQuery(){
        try {
            List<AIType> aiTypes = aiTypeService.findAll();
            return BaseResponse.constructResponse(ErrorCode.SUCCESS, aiTypes);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }

    @PostMapping("/delete/{aiTypeId}")
    @ResponseBody
    public BaseResponse aiTypeDelete(@PathVariable Long aiTypeId) {
        try {
            AIType aiType = aiTypeService.findById(aiTypeId);
            if(aiType == null){
                log.info("AiTypeId:{} does not exist", aiTypeId);
                return BaseResponse.constructResponse(ErrorCode.PARAMETER.getCode(), "AiTypeId:"+aiTypeId+" does not exist");
            }
            aiTypeService.delete(aiType);
            return BaseResponse.constructResponse(ErrorCode.SUCCESS);
        }catch (Exception e){
            return BaseResponse.constructResponse(ErrorCode.FAILED);
        }
    }
}
