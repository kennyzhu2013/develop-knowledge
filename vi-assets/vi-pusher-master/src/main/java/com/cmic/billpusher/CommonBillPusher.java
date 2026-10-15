package com.cmic.billpusher;



import com.alibaba.fastjson2.JSONObject;
import com.cmic.dao.BillDao;
import com.cmic.metrics.PusherMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.utils.CommonThreadPool;
import com.cmic.utils.CommonUtil;
import com.cmic.utils.HttpSender;
import com.cmic.utils.Parameters;
import com.discovery.RegistryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
public class CommonBillPusher {

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

    public void pushBill(Bill bill) {

        try {
            String url = "";
            if (url != null && url.length() >= 7) {
                String host = RegistryService.GetServiceNode(parameters.HttpproxyServiceName);
                Map<String, Object> httpParams = createParams(bill);
                Map<String, String> header = createheader(bill, httpParams, host);
                CompletableFuture<HttpResponse<String>> future = httpSender.sendPostRequestAsync(host + "/proxy", header, httpParams);
                Bill finalBill = bill;
                future.handleAsync((resp, e) -> {
                    if (e != null) {
                        log.error("[" + finalBill.getId() + "] - common push request error: ", e);

                        return httpSender.NULL_RESPONSE;
                    } else {
                        handleResponse(resp, finalBill);
                        return resp;
                    }
                }, CommonThreadPool.adapterCallbackThreadPool);
                //和留言二次触达话单Variable used in lambda expression should be final or effectively final
                billCommonFun.checkRecrodDelay(bill);
            } else {
                log.info("[{}] - {}", bill.getId(), "url is empty, archive bill success!");
                pusherMetrics.billPushSuccessCountInc();
                billDao.updateBillNewIsBillOk(bill, 2);
                bill = billDao.findBillNewById(bill.getId());
                if (bill == null) {
                    return;
                }
                if (bill.getCallDurationS() > 0) {
                    if (bill.getIsRecordOk() == 1 || bill.getIsRecordOk() == 10 || bill.getIsRecordOk() == 21 || bill.getIsRecordOk() == 2) {
                        int row = billDao.createBillHistory(bill);
                        if (row == 1) {
                            billDao.deleteBillNew(bill);
                        } else {
                            log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                        }
                    }
                } else if (bill.getCallForwardTime() != null) {
                    if (bill.getIsRecordOk() == 1 || bill.getIsRecordOk() == 10 || bill.getIsRecordOk() == 2) {
                        int row = billDao.createBillHistory(bill);
                        if (row == 1) {
                            billDao.deleteBillNew(bill);
                        } else {
                            log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                        }
                    }
                } else {
                    int row = billDao.createBillHistory(bill);
                    if (row == 1) {
                        billDao.deleteBillNew(bill);
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
                billDao.updateBillNewIsBillOk(bill, -1);
                pusherMetrics.billPushErrorCountInc();
            }
        }

    }


    private Map<String, Object> createParams(Bill bill) {
        Map<String, Object> params = new TreeMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

        params.put("CallID", String.valueOf(bill.getId()));
        params.put("InboundCallerNo", bill.getCallerNumber() == null ? "" : bill.getCallerNumber());

        params.put("OutboundCalleeNo", StringUtils.isBlank(bill.getCalleeNumber()) ? null : bill.getCalleeNumber());
        params.put("CallTime", sdf.format(bill.getHandleStartTime()));
        if (bill.getCallForwardTime() != null) {
            params.put("CalloutTime", sdf.format(bill.getCallForwardTime()));
        }
        if (bill.getRingTime() != null) {
            params.put("RingTime", sdf.format(bill.getRingTime()));
        }
        if (bill.getCallStartTime() != null) {
            params.put("StartTime", sdf.format(bill.getCallStartTime()));
        }
        if (bill.getCallEndTime() != null) {
            params.put("ReleaseTime", sdf.format(bill.getCallEndTime()));
        } else if (bill.getHandleEndTime() != null) {
            params.put("ReleaseTime", sdf.format(bill.getHandleEndTime()));
        }
        params.put("CallDuration", bill.getCallDurationS());
        if (bill.getCallDurationS() == 0) {
            params.put("UnconnectedCause", CommonUtil.UnconnectedCauseMap.getOrDefault(bill.getFinishState(), 99));
        }

        params.put("ReleaseCause", bill.getFinishType());

        //通话时长为0或接续时间为空，则不需要推送
        if (bill.getIsRecordOk() != 10 && bill.getCallDurationS() != 0 && bill.getCallForwardTime() != null) {
            params.put("RecordUrl", createRecordUrl(bill, false));
        }

        params.put("RequestId", CommonUtil.generateRequestId());


        return params;
    }


    private Map<String, String> createheader(Bill bill, Map<String, Object> params, String host) {
        Map<String, String> header = new HashMap<String, String>();
        String paramsBody = params.toString();
        String pushBillUrl = "";
        log.info("[{}] - bill data : {}, push bill url : {} with proxy : {}", bill.getId(), paramsBody, pushBillUrl, host);
        String authorizationHeader = null;
        authorizationHeader = null;

        //  log.info("[{}] - {}", bill.getId(), "authorizationHeader : " + authorizationHeader);
        header.put("Authorization", authorizationHeader);
        header.put("X-Forwarded-For", pushBillUrl);
        header.put("X-Session-Id", String.valueOf(bill.getId()));
        header.put("X-Platform-Id", String.valueOf(bill.getChannelId()));
        header.put("X-User-Id", String.valueOf(bill.getCompanyId()));
        header.put("X-App-Id", String.valueOf(bill.getCustomerId()));
        header.put("X-Timeout", "5000");

        return header;
    }

    /**
     * http://ip:port/media/userId/msId/sessionId[/nfsId]/name 通话合音
     * http://ip:port/early/userId/msId/sessionId[/nfsId]/name 早期合音
     * http://ip:port/origin/userId/msId/sessionId[/nfsId]/name 左右腿合音
     */
    //Early_callId_yyyyMMddhhmmss(startTime).wav/.mp3
    private String createRecordUrl(Bill bill, boolean ringFlag) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String prefix = "";
        String recordSource = "/media/";
        if (ringFlag) {
            recordSource = "/emedia/";
            prefix = "Early_";
        }
        String host = "";
        long handleStartTimeStamp = bill.getHandleStartTime().toInstant().getEpochSecond();
        String recordUrl = host + recordSource + bill.getCompanyId() + "/" + bill.getMsId() + "/" + bill.getId() + "/" + handleStartTimeStamp + "/" + bill.getPfsId() + "/";
        String suffix = ".mp3";
        if (bill.getRecordType() == 1) {
            suffix = ".wav";
        }

        String fileName = prefix + bill.getId() + "_" + sdf.format(bill.getHandleStartTime()) + suffix;
        recordUrl += fileName;
        return recordUrl;
    }


    private void handleResponse(HttpResponse<String> resp, Bill bill) {
        try {
            if (resp.statusCode() == 200) {
                String body = resp.body();
                log.info("[{}] - common bill push response: {}", bill.getId(), body.replaceAll("[\\t\\n\\r]", ""));
                JSONObject res = JSONObject.parseObject(body);
                // log.info("[{}] - {}", bill.getId(), end - start);
                if (res.containsKey("Code")) {
                    String code = res.getString("Code");
                    if (!"0000".equals(code)) {
                        log.error("[{}] - {}", bill.getId(), res.getString("message"));
                        pusherMetrics.billPushErrorCountInc();
                    } else {
                        log.info("[{}] - {}", bill.getId(), "pushed bill success!");
                        pusherMetrics.billPushSuccessCountInc();
                        billDao.updateBillNewIsBillOk(bill, 1);
                        bill = billDao.findBillNewById(bill.getId());
                        if (bill == null) {
                            return;
                        }
                        if (bill.getCallDurationS() > 0) {
                            if (bill.getIsRecordOk() == 1 || bill.getIsRecordOk() == 10 || bill.getIsRecordOk() == 21 || bill.getIsRecordOk() == 2) {
                                int row = billDao.createBillHistory(bill);
                                if (row == 1) {
                                    billDao.deleteBillNew(bill);
                                } else {
                                    log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                                }
                            }
                        } else if (bill.getCallForwardTime() != null) {
                            if (bill.getIsRecordOk() == 1 || bill.getIsRecordOk() == 10 || bill.getIsRecordOk() == 2) {
                                int row = billDao.createBillHistory(bill);
                                if (row == 1) {
                                    billDao.deleteBillNew(bill);
                                } else {
                                    log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
                                }
                            }
                        } else {
                            int row = billDao.createBillHistory(bill);
                            if (row == 1) {
                                billDao.deleteBillNew(bill);
                            }
                        }
                    }
                } else {
                    log.error("[{}] - {}", bill.getId(), "Pushed bill fail! Reason : response with out code parameter");
                    billDao.updateBillNewIsBillOk(bill, -1);
                    pusherMetrics.billPushErrorCountInc();
                }
            } else if (resp.statusCode() > 400 && resp.statusCode() < 500) {
                log.error("[{}] - Pushed Common bill fail! Reason : 4XX ERROR : {}", bill.getId(), resp.body());
                billDao.updateBillNewIsBillOk(bill, -1);
                pusherMetrics.billPushErrorCountInc();
            } else if (resp.statusCode() > 500) {
                log.error("[{}] - Pushed Common bill fail! Reason : 5XX ERROR : {}", bill.getId(), resp.body());
                billDao.updateBillNewIsBillOk(bill, -1);
                pusherMetrics.billPushErrorCountInc();
            } else {
                log.error("[{}] - Pushed Common bill fail! Reason : {}", bill.getId(), resp.body());
                billDao.updateBillNewIsBillOk(bill, -1);
                pusherMetrics.billPushErrorCountInc();
            }
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                pusherMetrics.getConnectionTimeoutCountInc();
            }
            if (e.getCause() != null && e.getCause().getMessage().contains("Duplicate entry")) {
                log.warn("[" + bill.getId() + "] - Duplicate entry into bill_history for the same id");
            } else {
                log.error("[" + bill.getId() + "] - ", e);
                billDao.updateBillNewIsBillOk(bill, -1);
                pusherMetrics.billPushErrorCountInc();
            }
        }

    }
}
