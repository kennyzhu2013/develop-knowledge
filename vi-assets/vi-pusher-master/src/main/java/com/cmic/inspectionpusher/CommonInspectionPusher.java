package com.cmic.inspectionpusher;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cmic.billpusher.BillCommonFun;
import com.cmic.dao.BillDao;
import com.cmic.dao.MidAuthDao;
import com.cmic.metrics.PusherMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.pojo.entity.CompanyTab;
import com.cmic.pojo.entity.CustomerTab;
import com.cmic.rocketmq.InspectionDelaySender;
import com.cmic.utils.*;
import com.discovery.RegistryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CommonInspectionPusher {

    @Autowired
    private BillDao billDao;
    @Autowired
    private MidAuthDao midAuthDao;
    @Autowired
    private HttpUtil httpUtil;
    @Autowired
    private Parameters parameters;
    @Autowired
    private BillCommonFun billCommonFun;
    @Autowired
    private HttpSender httpSender;
    @Autowired
    private PusherMetrics pusherMetrics;
    @Autowired
    private InspectionDelaySender inspectionDelaySender;


    public void pushInspection(Map<String, Object> params) {
        String callIdStr = params.get("listId") == null ? "" : params.get("listId").toString();
        //log.info("[{}] - receive a inspection result from vi-billcenter: {}", callIdStr, params);
        int retryTimes = 1;

        Bill bill = billDao.findBillNewById(callIdStr);
        if (bill == null) {
            log.error("[{}] bill can not find from db", callIdStr);
            return;
        }
        if (params.containsKey("retryTimes") && params.get("retryTimes") != null) {
            retryTimes = Integer.parseInt(params.get("retryTimes").toString());
            if (retryTimes > 3) {
                log.error("[{}] - Push inspection result retry limit exceede", callIdStr);
                bill.setQualityInspectionStatus(-3);
                billDao.updateBillNewInspectionStatus(bill);
                int row = billDao.createBillHistory(bill);
                if (row == 1) {
                    billDao.deleteBillNew(bill);
                } else {
                    log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                }
                pusherMetrics.inspectionPushErrorCountInc();
                return;
            }
            retryTimes += 1;
            params.remove("retryTimes");

        }
        CompanyTab companyTab = CompanyTabManager.companyTabMap.get(bill.getCompanyId());
        CustomerTab customerTab = CustomerTabManager.customerTabMap.get(bill.getCustomerId());
        if (companyTab == null || customerTab == null) {
            log.error("[{}] - companyTab or customerTab  is null, company_id: {}, customer_id: {}", bill.getId(), bill.getCompanyId(), bill.getCustomerId());
            bill.setQualityInspectionStatus(-3);
            billDao.updateBillNewInspectionStatus(bill);
            params.put("retryTimes", retryTimes);
            inspectionDelaySender.sendInspectionResultDelay(bill, params, 60);
            return;
        }

        try {
            if (StringUtils.isBlank(customerTab.getResultPushUrl())) {
                log.info("[{}] - {}", bill.getId(), "inspection result push url is empty, archive bill success!");
                bill.setQualityInspectionStatus(3);
                billDao.updateBillNewInspectionStatus(bill);
                int row = billDao.createBillHistory(bill);
                if (row == 1) {
                    billDao.deleteBillNew(bill);
                } else {
                    log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                }
                pusherMetrics.inspectionPushSuccessCountInc();
                return;
            } else {
                Map<String, String> header = createheader(companyTab, customerTab, params, customerTab.getResultPushUrl());
                String host = RegistryService.GetServiceNode(parameters.HttpproxyServiceName);
                ResponseEntity<String> resp = httpUtil.sendPostRequest(host + "/proxy", header, params);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    //todo 更新状态
                    String body = resp.getBody();
                    log.info("[{}] - common quality inspection push response: {}", bill.getId(), body.replaceAll("[\\t\\n\\r]", ""));
                    JSONObject res = JSONObject.parseObject(body);
                    // log.info("[{}] - {}", bill.getId(), end - start);
                    if (res.containsKey("code")) {
                        String code = res.getString("code");
                        if (code.equals("0000")) {
                            bill.setQualityInspectionStatus(3);
                            billDao.updateBillNewInspectionStatus(bill);
                            int row = billDao.createBillHistory(bill);
                            if (row == 1) {
                                billDao.deleteBillNew(bill);
                            } else {
                                log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                            }
                            pusherMetrics.inspectionPushSuccessCountInc();
                            return;
                        }
                    }
                } else {
                    log.error("[{}] - {}", bill.getId(), "Pushed inspection result fail! Reason : " + JSON.toJSONString(resp));
                }
            }
        } catch (Exception e) {
            log.error("[{}] - push inspection result occurs exception: {}", e);
        }

        bill.setQualityInspectionStatus(-3);
        billDao.updateBillNewInspectionStatus(bill);
        //todo 发送到延时队列重试
        params.put("retryTimes", retryTimes);
        inspectionDelaySender.sendInspectionResultDelay(bill, params, 60);
    }

    private Map<String, String> createheader(CompanyTab companyTab, CustomerTab customerTab, Map<String, Object> params, String pushUrl) {
        Map<String, String> header = new HashMap<String, String>();
        String authorizationHeader = null;
        authorizationHeader = AuthorizationGenHelp.getCommonAuthorizationHeader(companyTab);
        //log.info("[{}] - {}", bill.getId(), "authorizationHeader : " + authorizationHeader);
        header.put("Authorization", authorizationHeader);
        header.put("X-Forwarded-For", pushUrl);
        header.put("X-Session-Id", String.valueOf(params.get("listId")));
        header.put("X-Platform-Id", String.valueOf(companyTab.getId()));
        header.put("X-User-Id", String.valueOf(customerTab.getId()));
        header.put("X-App-Id", String.valueOf(customerTab.getAppId()));
        header.put("X-Timeout", "10000");
        return header;
    }


}
