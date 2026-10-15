package com.cmic.business;

import com.cmic.BillNew;
import com.cmic.Java11HttpClient;
import com.cmic.business.businessparam.*;
import com.cmic.business.query.AuditStrategyQuery;
import com.cmic.business.query.QueryStrategy;
import com.cmic.business.record.Record;
import com.cmic.config.ConstantConf;
import com.cmic.constant.*;
import com.cmic.bizcontroller.DoPrepareParameterCallBack;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.pojo.*;
import com.cmic.service.*;
import com.cmic.state.FinishState;
import com.cmic.state.FinishType;
import com.cmic.util.*;
import com.dcc.config.service.BlackWhiteService;
import com.dcc.config.util.PhoneUtil;
import com.discovery.RegistryService;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.net.http.HttpClient;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.cmic.util.JSONObjectUtil.getStringFromJsonWithDefault;


public abstract class BusinessEvent {
    @Getter
    protected BillNew billNew;
    public String sessionId;
    @Setter
    @Getter
    protected ViConf viConf;
    protected BizNumAuthDo bizNumAuthDo;
    public QueryStrategy queryStrategy;
    @Getter
    protected String eventName;
    // 配置了与extId相关的控制逻辑
    public ExtensionCtrlParam extensionCtrlParam;
    
    public BusinessEvent() {
        this.eventName = this.getClass().getSimpleName();
    }

    /**
     *
     * @param callParameter 呼叫功能控制
     */
    public void processCallParameter(CallParameter callParameter){

        // todo 先默认填1，后需可以在此处校验拦截
        callParameter.setOpType(1);
        callParameter.setSupportedCodec(DevHelp.getSupportedCodec());
        setRecordParams(callParameter);
        callParameter.setRecordParams(Record.getInstance().buildRecordParam(callParameter));
        // 如果为70014路由码进入，则真实被叫为去掉前缀后的号码
        String forwardCallee;
        // 用requestLine里面的号码判断是否存在前缀路由码
        if(billNew.getOriginalCalleeNumber().startsWith("700")){
            String originToCallee = billNew.getOriginToCallee();
            // 如果To里面的号码是国际号码，后续就用To号码作为被叫号码
            if (originToCallee.startsWith("+") && !originToCallee.startsWith("+86") || originToCallee.startsWith("00") && !originToCallee.startsWith("0086")) {
                callParameter.setForwardCallee(originToCallee);
            } else{
                forwardCallee = PhoneNumUtil.getPure11NumberWithout0or86(billNew.getOriginalCalleeNumber()).substring(5);
                callParameter.setForwardCallee(forwardCallee);
            }
        } else {
            // 保持原始被叫不变
            forwardCallee = billNew.getOriginalCalleeNumber();
            callParameter.setForwardCallee(forwardCallee);
        }
    }

    public void fillBillNew(CallParameter callParameter){
        billNew.setCalleeNumber(callParameter.getForwardCallee());
        billNew.setRightLegInfo(billNew.getCalleeNumber());
        billNew.setMobileId(viConf.getChannel().getParentId());
        billNew.setChannelId(viConf.getCompany().getParentId());
        billNew.setCompanyId(bizNumAuthDo.getCompanyId());
        billNew.setCustomerId(bizNumAuthDo.getCustomerId());
        billNew.setAppId(viConf.getCustomer().getAppId());
        // 默认就是"wav"
        billNew.setRecordType(1);
        billNew.setAccountType(viConf.getAccountType());
    }

    public void doStrategyQuery(CallParameter callParameter, Java11HttpClient.httpCallback<JSONObject> action){
        String strategyHost = RegistryService.GetServiceNode(ConstantConf.GO_STRATEGY_SERVICE_NAME);
        // 如果从ETCD没有获取到对应的服务
        if (StringUtils.isBlank(strategyHost) || DevHelp.auditStrategyAllow == 0) {
            DtmfLogger.info(billNew.getId(), "skip auditStrategy, strategyHost:{}, auditStrategyAllow:{}", strategyHost, DevHelp.auditStrategyAllow);
            action.apply(null);
            return;
        }
        String url = strategyHost + "/strategy";
        long startTime = System.currentTimeMillis();
        JSONObject jsonObject = initAuditSendJson();
        DtmfLogger.info(billNew.getId(), "prepare auditStrategy check, url:{}, content:{}", url, jsonObject);
        new AuditStrategyQuery().query(billNew.getId(), url, initAuditSendJson(), checkResp -> {
            try {
                long endTime = System.currentTimeMillis();
                DtmfLogger.info(billNew.getId(), "time use:{}ms, auditStrategy check resp {}", (endTime - startTime), checkResp.toString());
                int http_code = checkResp.getInt("http_code");
                if (http_code == 200) {
                    String opType = getStringFromJsonWithDefault(checkResp, "opType");
                    String callNoPlayCode = getStringFromJsonWithDefault(checkResp, "callNoPlayCode");
                    String calledPlayCode = getStringFromJsonWithDefault(checkResp, "calledPlayCode");
                    String appendReason = getStringFromJsonWithDefault(checkResp, "appendReason");

                    AuditResponseEntity auditResponseEntity = new AuditResponseEntity(opType, callNoPlayCode, calledPlayCode, appendReason);
                    if (auditResponseEntity.passButChangeResponse()) {
                        auditResponseEntity.setAppendReason(BillAppendReasonEnum.STRATEGY_MODIFY_8.getCode() + "");
                    }
                    callParameter.setAuditResponseEntity(auditResponseEntity);
                    // 可能修改接续要求和appendReason
                    SessionLimitUtil.auditLimitIntercept(billNew.getCallerNumber(), billNew.getCalleeNumber(), callParameter, billNew.getId());
                } else {
                    DtmfLogger.error(billNew.getId(), "auditStrategy query http code error:{}", http_code);
                }
            } catch (Exception e) {
                DtmfLogger.error(billNew.getId(), "auditStrategy query check error! ", e);
            }
            action.apply(null);
        }, checkResp -> {
            action.apply(null);
        });
    }

    private JSONObject initAuditSendJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            String companyId = String.valueOf(viConf.getCompany().getId());
            String customerId = String.valueOf(viConf.getCustomer().getId());
            jsonObject.put("billId", billNew.getId());
            jsonObject.put("callerNo", billNew.getCallerNumber());
            jsonObject.put("calleeNo", billNew.getCalleeNumber());
            jsonObject.put("bizNo", billNew.getBizNumber());
            jsonObject.put("companyId", companyId);
            jsonObject.put("customerId", customerId);
            jsonObject.put("callerProvinceId", String.valueOf(billNew.getCallInProvinceId()));
            jsonObject.put("calleeProvinceId", String.valueOf(billNew.getCallOutProvinceId()));
            jsonObject.put("callerCityId", billNew.getCallInAreaCode());
            jsonObject.put("calleeCityId", billNew.getCallOutAreaCode());

            jsonObject.put("handleStartTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(billNew.getHandleStartTime())); // yyyy-MM-dd HH:mi:ss
        } catch (Exception e) {
            DtmfLogger.error(billNew.getId(), "auditStrategy query check error! ", e);
            return null;
        }
        return jsonObject;
    }

    public void blackCallerIntercept(String caller, CallParameter callParameter) {
        if (hitCallerBlackList(caller, String.valueOf(bizNumAuthDo.getId()), String.valueOf(bizNumAuthDo.getCustomerId()))) {
            // 判断是否需要入库原始主叫——有可能修改话单中的主叫字段
            // changeCallerIfInternational();
            callParameter.onDisconnect("Caller hitBlackList", PlayCodeService.PLAYCODE_HIJACKING,
                    FinishState.BLACK_LIST_INTERCEPT, FinishType.SYSTEM_STOP);
            callParameter.setOptypeAppendReason(BillAppendReasonEnum.CALLER_BLACKLIST_BLOCK_2.getCode());
            DtmfLogger.warn(String.valueOf(sessionId), "userId={} rawCallerNumber={} hitCallerBlackList ",
                    String.valueOf(bizNumAuthDo.getId()), caller);
        }
    }

    public abstract JSONObject buildMQEvent(String eventName);

    // 获取默认的挂断提示音,opType为0时
    // public String getDefaultErrorNotify() {
    //     if (!StringUtils.isEmpty(dccAuthConf.getNoBindAudioCode())) {
    //         return dccAuthConf.getNoBindAudioCode();
    //     }
    //     return PlayCodeService.ERROR_NOTICE_URL;
    // }

    // public String getDefaultNoBindPlayCode() {
    //     return dccAuthConf.getNoBindAudioCode();
    // }

    // 录音参数调整
    public void setRecordParams(CallParameter callParameter) {
        callParameter.setNeedEarly(false);
        callParameter.setNeedRecord(true);
        callParameter.setSuffix("wav");
        callParameter.setSeparateLeg(false);
        // todo 默认false
        callParameter.setSeparateEarly(false);

        // 声道翻转的参数
        callParameter.setReverseLeg(false);
        callParameter.setMediaChannelReversed(false);
        callParameter.setRecordParams(Record.getInstance().buildRecordParam(callParameter));
    }

    public CallParameter buildDefaultCallParameter() {
        return new CallParameter(false, "", 0, "", "", "");
    }

    /**
     * 判断主叫号码是否命中主叫黑名单策略，国际主叫拦截目前为主叫策略其中一条
     *
     * @param originCallerNumber 原始主叫号码
     * @return true : 命中主叫黑名单，false : 未命中
     */
    public boolean hitCallerBlackList(String originCallerNumber, String companyId, String appId) {
        boolean isHitCallerBlackList = false;
        // 此开关含义目前为全局主叫黑名单
        // if (DevHelp.isGlobalCallerInterceptSwitch()) {
            isHitCallerBlackList = BlackWhiteService.isInCallerBlackList(originCallerNumber, companyId);
        // }
        isHitCallerBlackList = isHitCallerBlackList || BlackWhiteService.isInCustomerCallerBlackList(originCallerNumber, appId, String.valueOf(sessionId));
        return isHitCallerBlackList;
    }

    // /**
    //  * 判断是否此extId是否需要在国际主叫拦截的情况下入库原始主叫号码{@link com.cmic.Bill#getOriginalCallerNumber}
    //  * 如若需要入库原始主叫号码{@link com.cmic.Bill#setCallerNum}，并且原始主叫号码以"+"开头，替换"+"为"00"
    //  */
    // public void changeCallerIfInternational() {
    //     // 如果此extId需要在国际主叫拦截情况下入库原始主叫
    //     if (extensionCtrlParam != null && extensionCtrlParam.isOrigCallerNum4InternationalIntercept()) {
    //         String originCaller = billNew.getOriginalCallerNumber();
    //         if (originCaller.startsWith("+") && !originCaller.startsWith("+86") || originCaller.startsWith("00") && !originCaller.startsWith("0086")) {
    //             if (originCaller.startsWith("+")) {
    //                 originCaller = originCaller.replaceFirst("\\+", "00");
    //             }
    //             DtmfLogger.info(sessionId, "{} hitCallerBlackList for international caller, change billNew caller: {} to originCaller: {}", this.getClass().getSimpleName(), billNew.getCallerNum(), originCaller);
    //             billNew.setCallerNum(originCaller);
    //         }
    //     }
    // }


    public void doRecvInvite() throws Exception {

    }

    public void doSendInvite() throws Exception {
    }

    public void do180Invite() throws Exception {
    }

    public void do200Invite() throws Exception {
    }

    public void doHangUpEvent() throws Exception {
    }

    public void doCallTerminated() throws Exception {
    }

    public static void main(String[] args) {
        String respStr = "{\"success\":true,\"errorCode\":\"0\",\"errorMsg\":\"成功\",\"result\":\"2161541233\",\"http_code\":200}";
        JSONObject resp = JSONObject.fromObject(respStr);
        System.out.println(resp.toString());
    }

    // public String getConfirmCalledNoPlayCode(String calleePlayCode) {
    //     String answerPlayCode = dccAuthConf.getAnsweredAudioCode();
    //     String confirmCalledNoPlayCode = CommonUtil.addStrings(answerPlayCode, calleePlayCode);
    //     // 企业配置中接通后强制放音为空时并且转接查询返回非空时，需要校验转接查询返回的放音是否在企业已登记放音列表中
    //     if (StringUtils.isBlank(answerPlayCode) && StringUtils.isNotBlank(calleePlayCode)) {
    //         boolean isValidCode = CallService.isValidCustomPlayCode(dccAuthConf.getAppId(), new ArrayList<>(Arrays.asList(calleePlayCode.trim().split("[,;]"))));
    //         if (!isValidCode) {
    //             DtmfLogger.warn(String.valueOf(sessionId), "calleePlayCode : {} is not in valid list", calleePlayCode);
    //             if (StringUtils.isNotBlank(dccAuthConf.getDefaultAnsweredAudioCode())) {
    //                 confirmCalledNoPlayCode = "";
    //             } else {
    //                 DtmfLogger.warn(String.valueOf(sessionId), "empty answerPlayCode and defaultAnsweredPlayCode");
    //             }
    //         }
    //     }
    //     if (StringUtils.isBlank(confirmCalledNoPlayCode))
    //         confirmCalledNoPlayCode = dccAuthConf.getDefaultAnsweredAudioCode();
    //
    //     return confirmCalledNoPlayCode;
    // }
}
