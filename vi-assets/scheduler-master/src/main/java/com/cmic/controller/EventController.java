package com.cmic.controller;

import com.cmic.engine.XmlEventPublisher;
import com.cmic.engine.event.HttpEvent;
import com.cmic.pojo.dto.BaseResponse;
import com.cmic.pojo.dto.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("v1/front/event")
public class EventController {
    @Resource
    private XmlEventPublisher xmlEventPublisher;

    //TODO 下线
    @PostMapping("/create")
    @ResponseBody
    public BaseResponse create(@Validated @RequestBody Map<String, Object> data) {
        /**
         * tfidf需要参数
         * transactionId
         * processId
         * tfidfType:start end
         */
        String processId = (String) data.get("processId");

        for(String key: data.keySet()){
//            redisService.setAttributeItem(processId, key, data.get(key));
        }

        xmlEventPublisher.httpEventPublish(HttpEvent.HTTP_EVENT, processId);
        return BaseResponse.constructResponse(ErrorCode.SUCCESS);
    }
}
