
package com.cmic.business.query.httpcallback;

import com.cmic.BillNew;
import com.cmic.Java11HttpClient;
import com.cmic.business.BusinessEvent;
import com.cmic.business.ViConf;
import com.cmic.business.businessparam.BaseCallBinding;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.business.query.AuditStrategyQuery;
import com.cmic.config.ConstantConf;
import com.cmic.config.AppConfig;
import com.cmic.pojo.AuditResponseEntity;
import com.cmic.pojo.BillAppendReasonEnum;
import com.cmic.pojo.Optype;
import com.cmic.util.DevHelp;
import com.cmic.util.DtmfLogger;
import com.cmic.util.PhoneNumUtil;
import com.dcc.config.model.BaseProdNum;
import com.discovery.RegistryService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cmic.util.JSONObjectUtil.getStringFromJsonWithDefault;

/**
 * 处理咪咕视频彩铃的转接查询
 * <p>
 * Author: luohuanfa
 * Date: 2024/4/18
 */
public class AuditStrategyCallBack implements Java11HttpClient.httpCallback<BaseCallBinding> {
    BillNew billNew;
    String sessionId;
    CallParameter callParameter;
    public volatile AuditStrategyQuery auditStrategyQuery;
    boolean complete;
    BusinessEvent businessEvent;
    BaseProdNum prodNum;

    ViConf viConf;
    AtomicInteger concurrency;
    Java11HttpClient.httpCallback<BaseCallBinding> nextAction;

    public AuditStrategyCallBack(BusinessEvent businessEvent, BillNew billNew, CallParameter callParameter, ViConf viConf, BaseProdNum prodNum, Java11HttpClient.httpCallback<BaseCallBinding> nextAction, AtomicInteger concurrency) {
        this.businessEvent = businessEvent;
        this.billNew = billNew;
        this.sessionId = billNew.getId();
        this.callParameter = callParameter;
        this.viConf = viConf;
        this.nextAction = nextAction;
        this.prodNum = prodNum;
        this.concurrency = concurrency;
    }

    @Override
    public void apply(BaseCallBinding callBinding) {
        if (auditStrategyQuery == null) {
            synchronized (AuditStrategyCallBack.class) {
                if (auditStrategyQuery == null) {
                    auditStrategyQuery = new AuditStrategyQuery();
                }
            }
        }
        String strategyHost = RegistryService.GetServiceNode(ConstantConf.GO_STRATEGY_SERVICE_NAME);
        if (StringUtils.isBlank(strategyHost) || DevHelp.auditStrategyAllow == 0) {
            DtmfLogger.info(sessionId, "skip auditStrategy, strategyHost:{}, auditStrategyAllow:{}", strategyHost, DevHelp.auditStrategyAllow);
            triggerNextAction(callBinding);
            return;
        }
        int origOpType = callBinding.getOpType();
        if (origOpType == Optype.CloseOptype.getOptype() || callParameter.getCertOpType() == 0) {
            DtmfLogger.info(sessionId, "skip auditStrategy because businessEvent optype 0");
            triggerNextAction(callBinding);
            return;
        }

        DtmfLogger.info(sessionId, "prepare auditStrategy check");
        JSONObject jsonObject = initAuditSendJson(callBinding);
        if (jsonObject == null) {
            DtmfLogger.info(sessionId, "skip auditStrategy because jsonObject null");
            triggerNextAction(callBinding);
            return;
        }

        String url = strategyHost + "/strategy";
        DtmfLogger.info(sessionId, "url:{}, auditStrategy content:{}", url, jsonObject);
        long startTime = System.currentTimeMillis();
        auditStrategyQuery.query(sessionId, url, jsonObject, checkResp -> {
            try {
                long endTime = System.currentTimeMillis();
                DtmfLogger.info(sessionId, "time use:{}ms, auditStrategy check resp {}", (endTime - startTime), checkResp.toString());
                int http_code = checkResp.getInt("http_code");
                String numCheckResult = "";
                if (http_code == 200) {
                    String opType = getStringFromJsonWithDefault(checkResp, "opType");
                    String callNoPlayCode = getStringFromJsonWithDefault(checkResp, "callNoPlayCode");
                    String calledPlayCode = getStringFromJsonWithDefault(checkResp, "calledPlayCode");
                    String calledDisplayNo = getStringFromJsonWithDefault(checkResp, "calledDisplayNo");
                    String allowCalleeForwarding = getStringFromJsonWithDefault(checkResp, "allowCalleeForwarding");
                    String appendReason = getStringFromJsonWithDefault(checkResp, "appendReason");
                    if (checkResp.containsKey("numCheckResult")) {
                        numCheckResult = getStringFromJsonWithDefault(checkResp, "numCheckResult");
                    }
                    AuditResponseEntity auditResponseEntity = new AuditResponseEntity(opType, callNoPlayCode, calledPlayCode, calledDisplayNo, allowCalleeForwarding, appendReason);
                    JSONArray allowForwardedNoList;
                    if (checkResp.containsKey("allowForwardedNoList") && checkResp.get("allowForwardedNoList") instanceof JSONArray) {
                        allowForwardedNoList = checkResp.getJSONArray("allowForwardedNoList");
                        auditResponseEntity.setAllowForwardedNoList(allowForwardedNoList);
                    }
                    if (auditResponseEntity.passButChangeResponse()) {
                        auditResponseEntity.setAppendReason(BillAppendReasonEnum.STRATEGY_MODIFY_8.getCode() + "");
                    }
                    callParameter.setAuditResponseEntity(auditResponseEntity);
                } else {
                    DtmfLogger.error(sessionId, "auditStrategy query http code error:{}", http_code);
                }
                // bill.setSecurityControl(Integer.parseInt(numCheckResult));
            } catch (Exception e) {
                DtmfLogger.error(sessionId, "auditStrategy query check error! ", e);
            }
            triggerNextAction(callBinding);
        }, e -> triggerNextAction(callBinding));
    }

    private void triggerNextAction(BaseCallBinding callBinding) {
        if (concurrency.decrementAndGet() <= 0) {
            nextAction.apply(callBinding);
        }
    }

    private JSONObject initAuditSendJson(BaseCallBinding callBinding) {
        JSONObject jsonObject = new JSONObject();
        try {
            String callerNo = PhoneNumUtil.getPure11NumberWithout0or86(billNew.getCallerNumber());
            String calleeNo = PhoneNumUtil.getPure11NumberWithout0or86(callBinding.getOutboundCalleeNo());
            calleeNo = StringUtils.isNotBlank(calleeNo) ? calleeNo.replaceAll("[^0-9]", "") : "";
            String secretNo = PhoneNumUtil.getPure11NumberWithout0or86(billNew.getBizNumber());
            String displayNo = PhoneNumUtil.getPure11NumberWithout0or86(callBinding.getOutboundCallerNo());
            displayNo = StringUtils.isNotBlank(displayNo) ? displayNo.replaceAll("[^0-9]", "") : "";
            String userId = String.valueOf(viConf.getCompany().getId());
            // int platformId = dccAuthConf.getPlatformId();
            String appId = String.valueOf(viConf.getCustomer().getId());
            String location = AppConfig.getLocationId() + "";
            String asId = AppConfig.getServerId() + "";
            int accessPlatform = 0;
            // int numCheckConfig = dccAuthConf.isNeedNrtQc() ? 1 : 0;

            jsonObject.put("callerNo", callerNo);
            jsonObject.put("calleeNo", calleeNo);
            jsonObject.put("secretNo", secretNo);
            jsonObject.put("displayNo", displayNo);
            // jsonObject.put("platformId", platformId+"");
            jsonObject.put("userId", userId);
            jsonObject.put("appId", appId);
            jsonObject.put("location", location);
            jsonObject.put("asId", asId);
            jsonObject.put("accessPlatform", accessPlatform);
            jsonObject.put("billId", sessionId);
            jsonObject.put("handleStartTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(billNew.getHandleEndTime())); // yyyy-MM-dd HH:mi:ss
            // 只有accessPlatform为0，并且numCheckConfig不需要查询，才不会去质检
            // jsonObject.put("numCheckConfig", numCheckConfig); // 是否需要进行号码大数据查询：0 - 不需要 1 - 需要
        } catch (Exception e) {
            DtmfLogger.error(sessionId, "auditStrategy query check error! ", e);
            return null;
        }
        return jsonObject;
    }

}
