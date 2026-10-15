package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.callInfo.CallSession;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.recording.MediaSession;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.state.PreciseCallState;
import com.dcc.config.model.BaseProdNum;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.RAckHeader;
import javax.sip.message.Request;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象plugin类
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/19
 */
public abstract class AbstractPlugin implements Plugin {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, Hook> leftHookMap = new ConcurrentHashMap<>();
    private Map<String, Hook> rightHookMap = new ConcurrentHashMap<>();
    private final Set<String> leftHooksHasDeal = ConcurrentHashMap.newKeySet();
    private final Set<String> rightHooksHasDeal = ConcurrentHashMap.newKeySet();
    protected BillNew billNew;
    protected MediaSession mediaSession;
    protected LeftLeg leftLeg;
    protected RightLeg rightLeg;
    protected SIPRequest initialInviteRequest;
    protected SipEngine engine;
    protected BaseProdNum prodNum;
    protected CallParameter callParameter;
    protected BusinessEvent businessEvent;
    protected CallSession callSession;

    protected StringBuffer singalling;
    protected PreciseCallState preciseCallState;
    protected ControllerState controllerState;
    protected volatile boolean hasSend183 = false;
    protected boolean isNeedCacheSingallingLog = false;
    protected long initialRightSeqNo = 0;
    protected PluginModeAppendix pluginModeAppendix;
    protected SIPResponse lastRightResponse;
    protected SIPRequest lastLeftRequest;

    public SIPResponse getLastRightResponse() {
        return lastRightResponse;
    }

    public void setLastRightResponse(SIPResponse lastRightResponse) {
        this.lastRightResponse = lastRightResponse;
    }

    public SIPRequest getLastLeftRequest() {
        return lastLeftRequest;
    }

    public void setLastLeftRequest(SIPRequest lastLeftRequest) {
        this.lastLeftRequest = lastLeftRequest;
    }

    public long getInitialRightSeqNo() {
        return initialRightSeqNo;
    }

    public void setInitialRightSeqNo(long initialRightSeqNo) {
        this.initialRightSeqNo = initialRightSeqNo;
    }

    public boolean isHasSend183() {
        return hasSend183;
    }

    public void setHasSend183(boolean hasSend183) {
        this.hasSend183 = hasSend183;
    }

    public PreciseCallState getPreciseCallState() {
        return preciseCallState;
    }

    public void setPreciseCallState(PreciseCallState preciseCallState) {
        this.preciseCallState = preciseCallState;
    }

    public boolean isNeedCacheSingallingLog() {
        return isNeedCacheSingallingLog;
    }

    public void setNeedCacheSingallingLog(boolean needCacheSingallingLog) {
        isNeedCacheSingallingLog = needCacheSingallingLog;
    }

    public StringBuffer getSingalling() {
        return singalling;
    }

    public void setSingalling(StringBuffer singalling) {
        this.singalling = singalling;
    }

    public BusinessEvent getBusinessEvent() {
        return businessEvent;
    }

    public void setBusinessEvent(BusinessEvent businessEvent) {
        this.businessEvent = businessEvent;
    }

    public CallParameter getCallParameter() {
        return callParameter;
    }

    public void setCallParameter(CallParameter callParameter) {
        this.callParameter = callParameter;
    }


    public BillNew getBillNew() {
        return billNew;
    }

    public void setBillNew(BillNew billNew) {
        this.billNew = billNew;
    }

    public BaseProdNum getProdNum() {
        return prodNum;
    }

    public void setProdNum(BaseProdNum prodNum) {
        this.prodNum = prodNum;
    }

    public MediaSession getMediaSession() {
        return mediaSession;
    }

    public void setMediaSession(MediaSession mediaSession) {
        this.mediaSession = mediaSession;
    }

    public LeftLeg getLeftLeg() {
        return leftLeg;
    }

    public void setLeftLeg(LeftLeg leftLeg) {
        this.leftLeg = leftLeg;
    }

    public RightLeg getRightLeg() {
        return rightLeg;
    }

    public void setRightLeg(RightLeg rightLeg) {
        this.rightLeg = rightLeg;
    }

    public CallSession getCallSession() {
        return callSession;
    }

    public void setCallSession(CallSession callSession) {
        this.callSession = callSession;
    }

    public SIPRequest getInitialInviteRequest() {
        return initialInviteRequest;
    }

    public void setInitialInviteRequest(SIPRequest initialInviteRequest) {
        this.initialInviteRequest = initialInviteRequest;
    }

    public SipEngine getEngine() {
        return engine;
    }

    public void setEngine(SipEngine engine) {
        this.engine = engine;
    }

    @Override
    public PluginEventResultVo accept(PluginEvent event) throws Exception {
        return null;
    }

    public PluginModeAppendix getPluginModeAppendix() {
        return pluginModeAppendix;
    }

    public void setPluginModeAppendix(PluginModeAppendix pluginModeAppendix) {
        this.pluginModeAppendix = pluginModeAppendix;
    }

    @Override
    public PluginEventResultVo doRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        Hook<SipRequestEvent> requestHook = findRequestHook(leg, requestEvent.getRequest());
        if (requestHook != null) {
            requestHook.apply(requestEvent);
            return PluginEventResultVo.newCommonSuccVo();
        }
        logger.info("[{}] AbstractPlugin doRequest do not find any hook!", billNew.getId());
        return processRequest(leg, requestEvent);
    }


    @Override
    public PluginEventResultVo doResponse(Leg leg, SipResponseEvent response) throws Exception {
        Hook<SipResponseEvent> responseHook = findResponseHook(leg, response);
        if (responseHook != null) {
            responseHook.apply(response);
            leftHookMap.remove(response.getResponse().getTopmostViaHeader().getBranch());
            return PluginEventResultVo.newCommonSuccVo();
        }
        return processResponse(leg, response);
    }

    public abstract PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception;

    public abstract PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception;


    public void sendRequest(Leg leg, SIPRequest request, Hook<SipResponseEvent> hook) throws Exception {
        leg.sendRequest(request);
        if (leg.isLeft()) {
            leftHookMap.put(request.getTopmostViaHeader().getBranch(), hook);
        } else {
            rightHookMap.put(request.getTopmostViaHeader().getBranch(), hook);
        }

    }

    public void waitRequest(Leg leg, String method, Hook<SipRequestEvent> hook) throws Exception {
        if (leg.isLeft()) {
            this.leftHookMap.put(method, hook.addBefore((event) -> {
                this.leftHookMap.remove(method);
            }));
        } else {
            this.rightHookMap.put(method, hook.addBefore((event) -> {
                this.rightHookMap.remove(method);
            }));
        }

    }

    public Hook<SipResponseEvent> findResponseHook(Leg leg, SipResponseEvent response) {
        String branch = response.getResponse().getTopmostViaHeader().getBranch();
        Map<String, Hook> hookMap = null;
        Set<String> hooksHasDeal = null;
        if (leg.isLeft()) {
            hookMap = leftHookMap;
            hooksHasDeal = leftHooksHasDeal;
        } else {
            hookMap = rightHookMap;
            hooksHasDeal = rightHooksHasDeal;
        }
        Hook<SipResponseEvent> responseHook = hookMap.getOrDefault(branch, null);
        if (responseHook != null) {
            if (hooksHasDeal.contains(branch)) {
                return event -> {
                    logger.info("branch:{}, cseq:{} duplicate", branch, response.getResponse().getCSeq());
                    // 若已处理过，就返回一个空hook，既不传到as也不重复触发钩子
                };
            }
            hooksHasDeal.add(branch);
        }
        return responseHook;
    }

    public Hook findRequestHook(Leg leg, Request request) throws Exception {
        SIPRequest req = (SIPRequest) request;
        String method = req.getMethod();
        Map<String, Hook> hookMap = null;
        Set<String> hooksHasDeal = null;
        if (leg.isLeft()) {
            hookMap = leftHookMap;
            hooksHasDeal = leftHooksHasDeal;
        } else {
            hookMap = rightHookMap;
            hooksHasDeal = rightHooksHasDeal;
        }
        if (method.equalsIgnoreCase(Request.PRACK)) {
            RAckHeader rAckHeader = (RAckHeader) req.getHeader(RAckHeader.NAME);
            String hookkey = String.format("prack_%s", rAckHeader.getRSeqNumber());
            Hook hook = hookMap.getOrDefault(hookkey, null);
            if (hook != null) {
                if (hooksHasDeal.contains(hookkey)) {
                    return event -> {
                        //若已处理过，就返回一个空hook，既不传到as也不重复触发钩子
                    };
                }
                hooksHasDeal.add(hookkey);
                return hook;
            }
        }

        Hook hook = hookMap.getOrDefault(method, null);
        if (hook != null) return hook;

        return null;
    }

    public abstract boolean isComplete();

    public static boolean isPluginComplete(AbstractPlugin plugin) {
        return plugin != null && plugin.isComplete();
    }

    public Leg getOppositeLeg(Leg leg) {
        return leg.isLeft() ? this.rightLeg : this.leftLeg;
    }

    public void setControllerState(ControllerState controllerState) {
        this.controllerState = controllerState;
    }

    protected void refreshBaseState(Leg leg, SipMessageDesc sipMessageDesc, String transactionId) {
        controllerState.consumeSipMessage(leg, sipMessageDesc, transactionId);
    }

    public void copyBaseAttrFromCallInfo(ControllerState state) {
        setLeftLeg((LeftLeg) state.getLeftLeg());
        setRightLeg((RightLeg) state.getRightLeg());
        setBillNew(state.getCallInfo().getBillNew());
        setEngine(state.getCallInfo().getEngine());
        setInitialInviteRequest(state.getCallInfo().getInitialInviteRequest());
        setMediaSession(state.getCallInfo().getMediaSession());
        setProdNum(state.getCallInfo().getProdNum());
        setCallParameter(state.getCallInfo().getCallParameter());
        setBusinessEvent(state.getCallInfo().getBusinessEvent());
        setPreciseCallState(state.getCallInfo().getPreciseCallState());
        setControllerState(state);
        setNeedCacheSingallingLog(state.getCallInfo().isNeedCacheSignallingLog());
        setInitialRightSeqNo(state.getCallInfo().getInitialRightSeqNo());
    }

    public SIPRequest createRequestByCopy(Leg leg, SIPRequest request) throws Exception {
        SIPRequest newRequest = leg.createRequest(Request.INVITE);
        //给 Contact 头部加上 video
        newRequest.removeHeader(ContactHeader.NAME);
        newRequest.setHeader(this.engine.contactHeaderWithVideo);
        Header accessNetworkInfo = request.getHeader("P-Access-Network-Info");
        Header chargeVertor = request.getHeader("P-Charging-Vector");
        if (accessNetworkInfo != null) {
            newRequest.setHeader(accessNetworkInfo);
        }
        if (chargeVertor != null) {
            newRequest.setHeader(chargeVertor);
        }
        if (request.getRawContent() != null) {
            newRequest.setContent(request.getRawContent(), engine.contentTypeHeader);
        }
        return newRequest;
    }

    @Override
    public void processPluginTermination() throws Exception {
    }
}
