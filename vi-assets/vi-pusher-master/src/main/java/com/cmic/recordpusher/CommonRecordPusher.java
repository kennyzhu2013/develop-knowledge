package com.cmic.recordpusher;

import com.alibaba.fastjson2.JSONObject;
import com.cmic.billpusher.BillCommonFun;
import com.cmic.dao.BillDao;
import com.cmic.metrics.PusherMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.rocketmq.RecordDelaySender;
import com.cmic.utils.CommonThreadPool;
import com.cmic.utils.CommonUtil;
import com.cmic.utils.HttpSender;
import com.cmic.utils.Parameters;
import com.discovery.RegistryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class CommonRecordPusher {

    @Autowired
    private BillDao billDao;
    @Autowired
    private Parameters parameters;
    @Autowired
    private BillCommonFun billCommonFun;
    @Autowired
    private HttpSender httpSender;
    @Autowired
    private PusherMetrics pusherMetrics;
    @Autowired
    private RecordDelaySender recordDelaySender;

    public void pushRecording(Map<String, Object> params) {
        Bill bill = null;
        String msId = params.get("msId").toString();
        params.remove("msId");
        try {
            bill = billDao.findBillNewById(params.get("billId").toString());
            if (bill == null) {
                log.info("[{}] - common bill can not found in billnew", params.get("billId"));
                return;
            }
        } catch (Exception e) {
            log.error("[" + params.get("billId") + "] - database opearation error : ", e);
            billDao.updateBillNewIsRecordOk(bill, -1);
            pusherMetrics.recordPushErrorCountInc();
            return;
        }

        if (bill.getIsRecordOk() == 0) {
            //as异步通知媒体合音，收到录音推送可能as还没更新字段，需要延时推送;为避免无限重试，判断是否携带了重试标识
            if (!params.containsKey("delayPushFlag") && bill.getFinishState() == 0) {
                log.info("[" + params.get("billId") + "] - bill param has not update yet, delay push common record");
                recordDelaySender.sendBillDelay(bill, 5);
                return;
            }

            try {
                String url = "";
                if (url != null && url.length() >= 7) {
                    String host = RegistryService.GetServiceNode(parameters.HttpproxyServiceName);
                    Map<String, Object> httpParams = createParams(bill, msId);
                    Map<String, String> header = createheader(bill, httpParams, host);
                    CompletableFuture<HttpResponse<String>> future = httpSender.sendPostRequestAsync(host + "/proxy", header, httpParams);
                    Bill finalBill = bill;
                    future.handleAsync((resp, e) -> {
                        if (e != null) {
                            log.error("[" + finalBill.getId() + "] - common record push error: ", e);
                            return httpSender.NULL_RESPONSE;
                        } else {
                            handleResponse(resp, finalBill);
                            return resp;
                        }
                    }, CommonThreadPool.adapterCallbackThreadPool);
                } else {
                    log.info("[{}] - {}", bill.getId(), "Url is empty,Archive record success!");
                    pusherMetrics.recordPushSuccessCountInc();
                    billDao.updateBillNewIsRecordOk(bill, 2);
                    bill = billDao.findBillNewById(bill.getId());
                    if (bill == null) {
                        return;
                    }
                    //    log.info("[{}] - {}", bill.getId(), "is_bill_ok : " + bill.getIs_bill_ok() + " ; is_record_ok : " + bill.getIs_record_ok());
                    if (bill.getIsBillOk() == 2 || bill.getIsBillOk() == 1) {
                        int row = billDao.createBillHistory(bill);
                        if (row == 1) {
                            billDao.deleteBillNew(bill);
                            //   log.info("[{}] - {}", bill.getId(), "bill_new data move to bill_history success!");
                        }
                    }

                }
            } catch (Exception e) {
                if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                    pusherMetrics.getConnectionTimeoutCountInc();
                }
                if (e.getCause() != null && e.getCause().getMessage().contains("Duplicate entry")) {
                    log.warn("[" + bill.getId() + "] - Duplicate entry into bill_history for the same id");
                } else {
                    log.error("[" + bill.getId() + "] - ", e);
                    billDao.updateBillNewIsRecordOk(bill, -1);
                    pusherMetrics.recordPushErrorCountInc();
                }
            }
        } else {
            log.info("[{}] - common record push cancel, is_record_ok : {} ", bill.getId(), bill.getIsRecordOk());
        }
    }


    private void handleResponse(HttpResponse<String> resp, Bill bill) {
        try {
            if (resp.statusCode() == 200) {
                String body = resp.body();
                JSONObject res = JSONObject.parseObject(body);
                log.info("[{}] - common record push response: {}", bill.getId(), body.replaceAll("[\\t\\n\\r]", ""));
                if (res.containsKey("Code")) {
                    String code = res.getString("Code");
                    if (!"0000".equals(code)) {
                        // log.error("[{}] - {}", bill.getId(), res.getString("message"));
                        billDao.updateBillNewIsRecordOk(bill, -1);
                        pusherMetrics.recordPushErrorCountInc();
                    } else {
                        log.info("[{}] - {}", bill.getId(), "pushed record success!");

                        pusherMetrics.recordPushSuccessCountInc();
                        billDao.updateBillNewIsRecordOk(bill, 1);
                        bill = billDao.findBillNewById(bill.getId());
                        if (bill == null) {
                            return;
                        }
                        //    log.info("[{}] - {}", bill.getId(), "is_bill_ok : " + bill.getIs_bill_ok() + " ; is_record_ok : " + bill.getIs_record_ok());
                        if (bill.getIsBillOk() == 2 || bill.getIsBillOk() == 1) {
                            int row = billDao.createBillHistory(bill);
                            if (row == 1) {
                                billDao.deleteBillNew(bill);
                                //   log.info("[{}] - {}", bill.getId(), "bill_new data move to bill_history success!");
                            }
                        }
                        //recordCommonFun.pushRecordQualityTest(bill);
                    }
                } else {
                    log.error("[{}] - {}", bill.getId(), "Pushed record fail! Reason : response with out code parameter");
                    billDao.updateBillNewIsRecordOk(bill, -1);
                    pusherMetrics.recordPushErrorCountInc();
                }
            } else if (resp.statusCode() > 400 && resp.statusCode() < 500) {
                log.error("[" + bill.getId() + "] - Pushed record fail! Reason : 4XX ERROR : {}", resp.body());
                billDao.updateBillNewIsRecordOk(bill, -1);
                pusherMetrics.recordPushErrorCountInc();
            } else if (resp.statusCode() > 500) {
                log.error("[" + bill.getId() + "] - Pushed record fail! Reason : 5XX ERROR : {}", resp.body());
                billDao.updateBillNewIsRecordOk(bill, -1);
                pusherMetrics.recordPushErrorCountInc();
            }
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                pusherMetrics.getConnectionTimeoutCountInc();
            }
            if (e.getCause() != null && e.getCause().getMessage().contains("Duplicate entry")) {
                log.warn("[" + bill.getId() + "] - Duplicate entry into bill_history for the same id");
            } else {
                log.error("[" + bill.getId() + "] - ", e);
                billDao.updateBillNewIsRecordOk(bill, -1);
                pusherMetrics.recordPushErrorCountInc();
            }
        }

    }

    private Map<String, Object> createParams(Bill bill, String mid) {
        Map<String, Object> params = new TreeMap<>();

        params.put("CallID", String.valueOf(bill.getId()));
        //通话时长为0或接续时间为空，则不需要推送
        if (bill.getCallDurationS() != 0 && bill.getCallForwardTime() != null) {
            params.put("RecordUrl", createRecordUrl(bill, mid, false));
        }
        params.put("RequestId", CommonUtil.generateRequestId());
        params.put("InboundCallerNo", bill.getCallerNumber() == null ? "" : bill.getCallerNumber());
        params.put("OutboundCalleeNo", bill.getCalleeNumber() == null ? "" : bill.getCalleeNumber());

        return params;
    }

    private String createRecordUrl(Bill bill, String msid, boolean ringFlag) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String prefix = "";
        int billMsid = bill.getMsId();
        if (billMsid == 0 && !msid.equals("")) {
            billMsid = Integer.valueOf(msid);
        }
        String recordSource = "/media/";
        if (ringFlag) {
            recordSource = "/emedia/";
            prefix = "Early_";
        }
        String platformType = "";
        if (CollectionUtils.isNotEmpty(parameters.inernalPlatformSet) && parameters.inernalPlatformSet.contains(String.valueOf(bill.getChannelId()))) {
            platformType = "1/";
        } else {
            platformType = "2/";
        }
        String host = "";
        long handleStartTimeStamp = bill.getHandleStartTime().toInstant().getEpochSecond();
        String recordUrl = host + recordSource + platformType + bill.getCompanyId() + "/" + billMsid + "/" + bill.getId() + "/" + handleStartTimeStamp + "/" + bill.getPfsId() + "/";
        String suffix = ".mp3";
        if (bill.getRecordType() == 1) {
            suffix = ".wav";
        }

        String fileName = prefix + bill.getId() + "_" + sdf.format(bill.getHandleStartTime()) + suffix;
        recordUrl += fileName;
        return recordUrl;
    }

    private Map<String, String> createheader(Bill bill, Map<String, Object> params, String host) {
        Map<String, String> header = new HashMap<String, String>();
        String paramsBody = params.toString();
        String pushRecordUrl = "";
        log.info("[{}] - recording data: {}, push record url : {} with proxy: {}", params.get("CallID"), paramsBody, pushRecordUrl, host);
        String authorizationHeader = null;
        authorizationHeader = null;

        header.put("Authorization", authorizationHeader);
        header.put("X-Forwarded-For", pushRecordUrl);
        header.put("X-Session-Id", String.valueOf(bill.getId()));
        header.put("X-Platform-Id", String.valueOf(bill.getChannelId()));
        header.put("X-User-Id", String.valueOf(bill.getCompanyId()));
        header.put("X-App-Id", String.valueOf(bill.getCustomerId()));
        header.put("X-Timeout", "5000");
        return header;
    }

}
