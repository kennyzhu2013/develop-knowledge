package com.cmic.service;


import com.cmic.dao.BillDao;
import com.cmic.pojo.entity.Bill;
import com.cmic.metrics.BillMetrics;
import com.cmic.rocketmq.RecordDelaySender;
import com.cmic.rocketmq.RecordSender;
import com.cmic.utils.RecoverLogUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class RecordService {


    @Autowired
    private BillDao billDao;
    @Autowired
    private RecordDelaySender recordDelaySender;
    @Autowired
    private BillMetrics billMetrics;
    @Autowired
    private QualityCheckService qualityCheckService;


    public void getRecording(Map<String, Object> param) {
        Object callIdObj = param.get("callId");
        if (callIdObj == null) {
            log.error("callId is null, cannot get recording");
            return;
        }

        String callIdStr = callIdObj.toString().trim();
        if (callIdStr.isEmpty()) {
            log.error("callId is empty string, cannot get recording");
            return;
        }

        Bill bill = null;
        try {
            bill = billDao.findBillNewById(callIdStr);
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("[" + callIdStr + "] - database opearation error : ", e);
            //todo:推延时队列
            return;
        }
        if (bill == null) {
            log.error("[{}] - {}", callIdStr, "bill can not found from database");
            //todo:推延时队列
            String recordReceiveTimes = param.get("recordReceiveTimes") == null ? "" : param.get("recordReceiveTimes").toString();
            if (StringUtils.isNotBlank(recordReceiveTimes) && StringUtils.isNumeric(recordReceiveTimes)) {
                int times = Integer.valueOf(recordReceiveTimes);
                if (times >= 3) {
                    RecoverLogUtil.logRecordReushfail(param.get("callId").toString());
                    return;
                }
                param.put("recordReceiveTimes", times + 1);
            } else {
                param.put("recordReceiveTimes", 1);
            }

            recordDelaySender.sendRecordDelay(param);
            return;
        }

        if (param.get("leftFileLength") != null) {
            bill.setLeftFileLength(Integer.valueOf(param.get("leftFileLength").toString()));
        }

        if (param.get("rightFileLength") != null) {
            bill.setRightFileLength(Integer.valueOf(param.get("rightFileLength").toString()));
        }

        if (param.get("fileDuration") != null) {
            bill.setFileDuration(Integer.valueOf(param.get("fileDuration").toString()));
        }

        try {
            billDao.updateBillNewRecord(bill);
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("[" + callIdStr + "] - database opearation error : ", e);
        }
        try {
            billMetrics.inspectionCreateRequestCounterInc();
            boolean checkRes = qualityCheckService.checkQuality(bill);
            if (checkRes) {
                bill.setQualityInspectionStatus(1);
                billMetrics.inspectionRequestSuccessCounterInc();
            } else {
                bill.setQualityInspectionStatus(-1);
                billMetrics.inspectionRequestFailureCounterInc();
            }
            billDao.updateBillNewInspectionStatus(bill);
        } catch (Exception e) {
            log.error("[{}] - quality check error: {}", callIdStr, e);
        }
/*
        String filename = "";
        if (param.get("fileName") != null) {
            filename = param.get("fileName").toString();
        }
        try {
            String msid = "";
            if (param.get("serverId") != null) {
                msid = param.get("serverId").toString();
            }

            boolean separateEarly = false;
            boolean fullRecord = false;
            if (param.containsKey("separateEarly") && param.containsKey("fullRecord")) {
                separateEarly = Boolean.parseBoolean(param.get("separateEarly").toString());
                fullRecord = Boolean.parseBoolean(param.get("fullRecord").toString());
            }
            String bindId = "";
            if (param.get("bindId") != null) {
                bindId = param.get("bindId").toString();
            }
            recordSender.send(bill, filename, msid, separateEarly, fullRecord, bindId);
        } catch (Exception e) {
            log.error("[" + callIdStr + "] - ", e);
        }*/
    }

    //目前不插入表，只统计合音失败次数
    public void getError(Map<String, Object> param) {
        // recordErrorCounter.inc();
        billMetrics.recordErrorCounterInc();
        Long id = Long.valueOf(param.get("id").toString());
        log.error("[{}] - {}", id, "Combine record Error :" + param.toString());
    }
}


