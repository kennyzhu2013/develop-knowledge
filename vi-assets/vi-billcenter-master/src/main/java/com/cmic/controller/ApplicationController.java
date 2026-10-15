package com.cmic.controller;


import com.alibaba.fastjson2.JSONObject;
import com.cmic.dao.BillDao;
import com.cmic.metrics.BillMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.pojo.vo.CommonResult;
import com.cmic.rocketmq.InspectionResultSender;
import com.cmic.service.QualityCheckCallbackService;
import com.cmic.service.QualityCheckRecreateService;
import com.cmic.service.QualityCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/vibillcenter")
public class ApplicationController {

    @Autowired
    private QualityCheckService qualityCheckService;
    @Autowired
    private InspectionResultSender inspectionResultSender;
    @Autowired
    private BillDao billDao;
    @Autowired
    private QualityCheckCallbackService qualityCheckCallbackService;
    @Autowired
    private BillMetrics billMetrics;
    @Autowired
    private QualityCheckRecreateService qualityCheckRecreateService;

    @ResponseBody
    @RequestMapping(value = "/task/callback", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public CommonResult<String> taskCallback(@RequestBody Map<String, Object> params) {
        try {
            //log.info("receive a task callback from VI: {}",  params);
            if (params == null || params.isEmpty()) {
                log.error("task callback params is empty");
                return CommonResult.error("回调处理失败");
            }
            log.info("[{}] - receive a task callback from VI: {}", params.get("taskId"), params);
        } catch (Exception e) {
            log.error("application controller occurs exception: {}", e.getMessage());
            return CommonResult.error("回调处理失败");
        }
        return CommonResult.success("回调处理成功");
    }


    @ResponseBody
    @RequestMapping(value = "/subtask/callback", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public CommonResult<String> subtaskCallback(@RequestBody Map<String, Object> params) {
        String callId = "";
        try {
            log.info("receive a task subcallback from VI: {}",  params);
            if (params == null || params.isEmpty()) {
                log.error("subtask callback params is empty");
                billMetrics.inspectionCallbackFailureCounterInc();
                return CommonResult.error("回调处理失败");
            }
            callId = params.get("listId").toString();
            qualityCheckCallbackService.processSubtackCallback(params);
        } catch (Exception e) {
            log.error("[{}] - application controller occurs exception: {}", callId, e.getMessage());
            billMetrics.inspectionCallbackFailureCounterInc();
            return CommonResult.error("回调处理失败");
        }
        billMetrics.inspectionCallbackSuccessCounterInc();
        return CommonResult.success("回调处理成功");
    }



    @ResponseBody
    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public CommonResult<String> test(@RequestBody Map<String, Object> params) {
        try {
            Bill bill = new Bill();
            bill.setId(params.get("id").toString());
            bill.setHandleStartTime(new Date());
            bill.setCompanyId(15);
            bill.setCustomerId(30);
            qualityCheckService.checkQuality(bill);
        } catch (Exception e) {
            log.error("application controller occurs exception: {}", e.getMessage());
            return CommonResult.error("回调处理失败");
        }
        return CommonResult.success("回调处理成功");
    }

    @PostMapping(value = "/task/recreate")
    public JSONObject recreateTask(@RequestBody Map<String, String> params) {
        JSONObject response = new JSONObject();
        log.info("quality check task recreate action request");
        try {

            boolean res = qualityCheckRecreateService.recreatingTask(params);
            if (res) {
                response.put("result", "success");
            } else {
                response.put("result", "fail");
            }

            return response;
        } catch (Exception e) {
            log.error("quality check recreate task occurs exception, ex = {}", e.getMessage());
            response.put("result", "fail");
            return response;
        }
    }
}
