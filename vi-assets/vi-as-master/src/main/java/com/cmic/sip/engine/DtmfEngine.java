package com.cmic.sip.engine;

import com.cmic.BillNew;
import com.cmic.bizcontroller.controllerImpl.ErrorController;
import com.cmic.bizcontroller.controllerImpl.RejectController;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.AppConfig;
import com.cmic.bizcontroller.controllerImpl.B2BWithoutBillController;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.event.leg.SzLeftLeg;
import com.cmic.extension.AbstractCtExtension;
import com.cmic.extension.ExtensionManager;
import com.cmic.service.*;
import com.cmic.sip.StatelessSipMessageProxy;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.header.historyinfo.HiEntry;
import com.cmic.sip.header.historyinfo.HiParam;
import com.cmic.sip.header.historyinfo.HistoryInfoHeader;
import com.cmic.constant.CommonConstant;
import com.cmic.util.DevHelp;
import com.cmic.util.DtmfLogger;
import com.cmic.util.SipUtil;
import com.util.IPPort;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.NettyUDPMessageProxyChannel;
import lombok.Getter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.TimeoutEvent;
import javax.sip.header.Header;
import javax.sip.message.Request;
import java.io.File;
import java.io.FileInputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;

public class DtmfEngine extends SipEngine {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Getter
    private static final DtmfEngine instance = new DtmfEngine();

    public boolean checkHistoryMark(String billId, SIPRequest request) {
        try {
            Header historyInfo = request.getHeader("History-Info");
            if (historyInfo == null) {
                return false;
            } else {
                HistoryInfoHeader hih = SipUtil.parseHistoryInfo(historyInfo);
                List<HiEntry> hiEl = hih.getHiEntryList();
                return hiEl.stream().anyMatch(hiEntry -> hiEntry.getHiParamList().stream()
                        .anyMatch(hiParam -> hiParam.getName().equalsIgnoreCase(HiParam.CMIC_MARK)));
            }
        } catch (Exception e) {
            DtmfLogger.error(billId, "something error happen when judge HistoryMark", e);
        }
        return false;
    }

    @Override
    public Controller doBuildController(RequestEvent requestEvent) {
        String sessionId = getNextBillId();
        if(DevHelp.currentMode == 2) {
            DtmfLogger.info(sessionId, "startMode=2 deal with B2BWithoutBillController");
            B2BWithoutBillController controller = new B2BWithoutBillController(sessionId, this);
            controller.signalParam.setSessionId(sessionId);
            return controller;
        }
        SIPRequest request = (SIPRequest) requestEvent.getRequest();
        String originCallerNumber = SipUtil.getOriginalCallerNumber((SIPRequest) requestEvent.getRequest());
        String callerNum = SipUtil.getCallerNum(request);
        String calleeNum = SipUtil.getCalleeNum(request);
        String bizNum;
        String areaCode = "";
        // 如果是工作号主叫流程，则bizNum（业务登记号码）为主叫，否则为被叫(工作号被叫流程或者70014)
        if (checkIsGzhOrigByRegex(request) || calleeNum.startsWith("700")) {
            bizNum = callerNum;
        } else {
            // 工作号被叫流程，或者70014被叫
            bizNum = calleeNum;
        }
        if(calleeNum.startsWith("700")){
            // 此处从新获取requestLine的被叫号码，默认方法会去掉700号码的区号
            areaCode = SipUtil.getAreaCode(SipUtil.getRawPhoneNum(request.getRequestURI()));;
        }
        DtmfLogger.info(sessionId, "originalCallerNumber:{} callerNum:{} originalCalleeNum:{} bizNum:{}", originCallerNumber, callerNum, calleeNum, bizNum);
        // 12583定向语音呼叫
        if (bizNum.startsWith(CommonConstant.SPECIAL_SECRET.SECRET_125838) ||
                bizNum.startsWith(CommonConstant.SPECIAL_SECRET.SECRET_125836)) {
            bizNum = bizNum.substring(0, 6);
        }

        // 查缓存，获得：流程、基本控制参数（流程全局参数）
        BizNumAuthDo bizNumAuthDo = AuthConfigService.getInstance().getCallerNumAuthDo(bizNum);
        // todo是否保留
        if (checkHistoryMark(sessionId, request)) {
            DtmfLogger.info(sessionId, "callerNum={},   GZH entity second route to us", callerNum);
            return new B2BWithoutBillController(sessionId, this);
        }
        if (bizNumAuthDo == null) {
            // 判断是不是工作号域名
            DtmfLogger.info(sessionId, "prodNum=null,original callerNum={}, calleeNum={}", SipUtil.getCallerNum(request), SipUtil.getCalleeNum(request));
            if (checkIsGzhByRegexWithoutOrig(request)) {
                DtmfLogger.info(sessionId, "bizNum={}, auth=null, with GZH route dealt with B2BWithoutBillController", bizNum);
                XmodePromExporterService.getInstance().incAuthNumNullReject("GZH-FORWARD");
                B2BWithoutBillController controller = new B2BWithoutBillController(sessionId, this);
                controller.signalParam.setSessionId(sessionId);
                return controller;
            } else {
                DtmfLogger.warn(sessionId, "bizNum={}, auth=null, reject unrecognized number, request:{}", bizNum, request);
                XmodePromExporterService.getInstance().incAuthNumNullReject("700-REJECT");
                RejectController rejectController = new RejectController(sessionId, this);
                rejectController.signalParam.setSessionId(sessionId);
                return rejectController;
            }
        }

        BillNew billNew = new BillNew(sessionId);
        billNew.setBizNumber(bizNum);
        billNew.setCallIn700AreaCode(areaCode);
        billNew.setOriginalCallerNumber(originCallerNumber);
        // 记录一下原始To头域的号码
        String originToNumber = SipUtil.getOriginalPhoneNum(request.getToHeader().getAddress().getURI());
        billNew.setOriginToCallee(originToNumber);
        billNew.setCallerNumber(callerNum);
        billNew.setHandleStartTime(new Timestamp(System.currentTimeMillis() / 1000 * 1000));
        billNew.setOriginalCalleeNumber(calleeNum);
        billNew.setLeftLegInfo(request);
        AbstractCtExtension extension = ExtensionManager.getInstance().getExtension(1);
        Controller controller = extension.buildController(billNew, bizNumAuthDo, this, DevHelp.currentMode);
        controller.signalParam.update(sessionId, bizNumAuthDo.getCompanyId(), String.valueOf(bizNumAuthDo.getCustomerId()));
        return controller;

    }

    private String getNextBillId() {
        return BillIdService.getInstance().nextId();
    }


    public void init() throws Exception {
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream("config" + File.separator + "sip-stack-call.properties"));
            String[] ipAndPort = AppConfig.getAsListen().split(":");
            String ip = ipAndPort[0];
            String port = ipAndPort[1];
            this.initStack(ip, Integer.parseInt(port), properties);
            // StatelessSipMessageProxy.engine = this;
            // if (AppConfig.getStartMode() == 1) {
            //     NettyUDPMessageProxyChannel.setSipMessageProcessor(StatelessSipMessageProxy.proxyStatelessSipMessageProcess);
            // }
        } catch (Exception ex) {
            logger.error("", ex);
            System.exit(0);
        }
    }

    public final SzLeftLeg createSZLeg(SIPRequest inviteRequest) throws Exception {
        ServerTransaction firstInviteTransaction = (ServerTransaction) inviteRequest.getTransaction();
        if (firstInviteTransaction == null) {
            firstInviteTransaction = sipProvider.getNewServerTransaction(inviteRequest);
        }
        if (firstInviteTransaction.getDialog() == null) {
            sipProvider.getNewDialog(firstInviteTransaction);
        }
        return new SzLeftLeg(this, firstInviteTransaction, firstInviteTransaction.getDialog());
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        if (timeoutEvent.isServerTransaction()) {
            logger.info("ServerTransactionProcessTimeout thread={}: {}", Thread.currentThread().getId(), timeoutEvent.getServerTransaction().getRequest());
            XmodePromExporterService.getInstance().incAsProcessTimeout(XmodePromExporterService.SERVER_TRANSACTION_TIMEOUT);
        } else {
            String method = timeoutEvent.getClientTransaction().getRequest().getMethod();
            if (Request.INVITE.equalsIgnoreCase(method)) {
                logger.info("INVITEProcessTimeout thread={}: {}", Thread.currentThread().getId(), timeoutEvent.getClientTransaction().getRequest());
                XmodePromExporterService.getInstance().incAsProcessTimeout(XmodePromExporterService.CLIENT_TRANSACTION_INVITE_TIMEOUT);
            } else {
                logger.info("NONINIVTEProcessTimeout thread={}: {}", Thread.currentThread().getId(), timeoutEvent.getClientTransaction().getRequest());
                XmodePromExporterService.getInstance().incAsProcessTimeout(XmodePromExporterService.CLIENT_TRANSACTION_NO_INVITE_TIMEOUT);
            }
        }
    }


    /**
     * 是否应该拦截呼叫，主要做呼叫限流
     *
     * @param requestEvent 初始INVITE
     * @return 返回是否应该拦截呼叫 true=拦截 false=不拦截
     */
    @Override
    public boolean shouldSessionBlock(RequestEvent requestEvent) {
        SIPRequest sipRequest = (SIPRequest) requestEvent.getRequest();
        try {
            if (ControllerManager.getInstance().getTotalCount() > DevHelp.maxSession) {
                logger.error("DtmfEngine-shouldSessionBlock, session max reach, reject it, callID:{}", sipRequest.getCallId());
                XmodePromExporterService.getInstance().incCallRejectDueToTooMuch();
                XmodePromExporterService.getInstance().incASDowngrade();
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("DtmfEngine-shouldSessionBlock, session max reach but exception happened, callId:{}", sipRequest.getCallId());
            return false;
        }
    }

    @Override
    public IPPort getSipProxy() {
        return MyRegistryService.getInstance().getSipProxyHost();
    }

    /**
     * 根据请求消息判断是否属于工作号业务的主叫流程
     *
     * @param request 请求消息
     * @return 是否属于工作号业务并且是主叫流程
     */
    public boolean checkIsGzhOrigByRegex(SIPRequest request) {
        // 判断请求中Route头域是否包含符合工作号域名规则的域名，有则返回域名，无则返回空字符串
        String routeAddress = SipUtil.getSpecialRouteValueByRegex(request, AuthConfigService.getInstance().getGzhDomainRegexList());
        // 域名中需要包含orig
        return routeAddress.contains("orig");
    }

    /**
     * 根据请求消息判断是否属于工作号业务
     *
     * @param request 请求消息
     * @return 是否属于工作号业务
     */
    public boolean checkIsGzhByRegexWithoutOrig(SIPRequest request) {
        // 判断请求中Route头域是否包含符合工作号域名规则的域名，有则返回域名，无则返回空字符串
        String routeAddress = SipUtil.getSpecialRouteValueByRegex(request, AuthConfigService.getInstance().getGzhDomainRegexList());
        // 包含工作号域名回true
        if (!StringUtils.isBlank(routeAddress)) {
            return true;
        }
        return false;
    }

    public String switchStartMode(int startMode){
        // 如果要改成0，但是初始startMode不是0，则改AS没有加载业务依赖中间件，不能改
        String msg;
        if(startMode == 0 && DevHelp.initStartMode != 0){
            msg = "initStartMode=" + DevHelp.initStartMode + " can not change to startMode=" + startMode;
            logger.info("switchStartMode {}", msg);
            return msg;
        } else {
            msg = "startMode=" + DevHelp.currentMode  + " change to startMode=" + startMode;
            DevHelp.currentMode = startMode;
            logger.info("switchStartMode {}", msg);
            return msg;
        }
    }
}
