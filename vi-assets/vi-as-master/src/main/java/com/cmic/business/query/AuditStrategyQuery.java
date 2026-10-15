package com.cmic.business.query;

import com.cmic.Java11HttpClient;
import com.cmic.util.DtmfLogger;
import net.sf.json.JSONObject;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

public class AuditStrategyQuery {
    public void query(String billId, String auditCheckUrl, JSONObject jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Java11HttpClient.httpCallback<JSONObject> failAction) {
        query(billId, auditCheckUrl, jsonObject, action, failAction, 500);
    }

    public void query(String billId, String auditCheckUrl, JSONObject jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Java11HttpClient.httpCallback<JSONObject> failAction, int timeout) {
        Map<String, String> headers = new HashMap<>();
        ThreadPoolService.queueFull.set(false);
        ThreadPoolService.billId.set(billId);
        boolean postSuss = false;
        try {
            int auditQueryQueueSize = ThreadPoolService.getAuditQueryQueueSize();
            int auditResponseQueueSize = ThreadPoolService.getAuditResponseQueueSize();
            if (auditQueryQueueSize > 50 || auditResponseQueueSize > 50) {
                DtmfLogger.info(billId, "Audit query queue size:{}, audit response queue size:{}", auditQueryQueueSize, auditResponseQueueSize);
            }
            AsyncJava11HttpClient.postJsonWithTimeoutMillWithTag(billId, AsyncJava11HttpClient.PostType.AUDIT, auditCheckUrl, headers, jsonObject, action, HttpClient.Version.HTTP_2, timeout, "audit");
            postSuss = true;
        } catch (Exception e) {
            DtmfLogger.error(billId, "something error happen in auditStrategy， jsonObject:{}", jsonObject, e);
            failAction.apply(null);
        }
        if (ThreadPoolService.queueFull.get() && !postSuss) {
            failAction.apply(null);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AuditStrategyQuery auditStrategyQuery = new AuditStrategyQuery();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("billid", 1234567890);
        jsonObject.put("callerNo", "19802020000x");
        jsonObject.put("calleeNo", "19802021845");
        jsonObject.put("secretNo", "13803111233");
        jsonObject.put("displayNo", "13803111233");
        jsonObject.put("companyId", "1005");
        jsonObject.put("customerId", "300");
        jsonObject.put("provinceId", "1");
        jsonObject.put("cityId", "10");
        jsonObject.put("location", "1");
        jsonObject.put("asId", "1");
        jsonObject.put("cellId", "2");
        jsonObject.put("imei", "1111111");
        jsonObject.put("inviteHistoryInfo", "123");
        jsonObject.put("userAgent", "1.0");
        jsonObject.put("containPrivacy", 1);
        jsonObject.put("AccessPlatform", 1);
        long now = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            String billId = (now + i) + "";
            System.out.println("prepare send I:" + i + " , query billId:" + billId);
            auditStrategyQuery.query(billId, "http://10.153.90.14:9654/strategy?" + i, jsonObject, resp -> {
                System.out.println("resp: " + resp);
            }, resp2 -> {
                System.out.println("fail:" + resp2);
            });
        }
    }
}
