package com.cmic.sip.controller.base;

import com.cmic.recording.MediaSession;
import com.cmic.signaling.SignalParam;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.HttpRequestEvent;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.info.InfoCollector;
import com.cmic.util.AbnormalBillAnalyser;
import com.cmic.util.AnalyseLog;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.Dialog;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Controller {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    /**正常通话每隔15MIN就会发update做心跳**/
    private static final int CONTROLLER_OUT_TIME = 18*60*1000;
    public SipEngine engine;
    public String sessionId;
    public boolean isNeedCacheSingallingLog = true;
    public StringBuffer singalling = new StringBuffer();
    public long lastReciveUpdateTime ;
    public AtomicBoolean hasSendDialogTemrinate ;
    public InfoCollector infoCollector;
    public boolean canTerminate = true;

    public SignalParam signalParam = new SignalParam();

    // 保存需交由Controller管理的leg, 用于后续自动拆线，保证Controller的正确释放
    private final Set<Leg> legs = new HashSet<Leg>();

    private AnalyseLog analyseLogFlag = new AnalyseLog();

    private AbnormalBillAnalyser abnormalBillAnalyser;

    // 用于注册监听recording的http事件
    public final Map<String, Hook> httphooks = new ConcurrentHashMap<String, Hook>();
    public static ThreadLocal<Integer> THREAD_LAST_RESPONSE_STATECODE = new ThreadLocal<>();


    public Controller(String sessionId, SipEngine engine) {
        this.sessionId = sessionId;
        this.engine = engine;
        this.lastReciveUpdateTime = System.currentTimeMillis();
        hasSendDialogTemrinate = new AtomicBoolean(false);
        this.abnormalBillAnalyser = new AbnormalBillAnalyser(sessionId);
    }

    // controller与leg建立绑定关系
    public void bindLeg(Leg leg) {
//        synchronized (this.legs) {
            leg.controller = this;
            this.legs.add(leg);
//        }
    }

    public void unbindLeg(Leg leg) {
//        synchronized (this.legs) {
            //不能双向解绑， 因为processDialogTerminated是在487-invite/200-bye前执行，双向解绑会导致487-invite/200-bye进来时找不到controller
            //leg.controller = null;
            this.legs.remove(leg);
//        }
    }

    public Leg[] getLegs() {
        return legs.toArray(new Leg[0]);
    }

    public void sendByeToLegs() throws Exception {
        onSystemExitEvent();
        for (Leg leg : getLegs()) {
            if (leg.isLeft()) {
                leg.sendByeOr487();
            } else {
                leg.sendByeOrCancel();
            }

        }
    }

    public int sizeOfLegs() {
        return this.legs.size();
    }

    public Hook<HttpRequestEvent> findHttpRequestHook(String transationId) {
        return httphooks.getOrDefault(transationId, null);
    }

    public abstract void start(SIPRequest initialRequest) throws Exception;

    public abstract void terminated() throws Exception;

    public abstract void processRequest(Leg leg, SipRequestEvent evt) throws Exception;

    public abstract void processResponse(Leg leg, SipResponseEvent evt) throws Exception;

    public abstract void onSystemExitEvent() throws Exception;

    public abstract void onCleanOutEvent() throws Exception;

    public void stateAbnormalCallInfo(){

    }

    public boolean isAlive() {
        long now = System.currentTimeMillis();
        return now - lastReciveUpdateTime < CONTROLLER_OUT_TIME;
    }

    public void resetLastUpdateTime()
    {
        lastReciveUpdateTime = System.currentTimeMillis();
    }

    public void onHttpRequest(HttpRequestEvent evt) {

    }

    public MediaSession getMediaSession() {
        return null;
    }

    public boolean isCallForwardAllow(SIPResponse response){
        return true;
    }

    public void doCallForwardBlock(SIPResponse response) throws Exception{
        sendByeToLegs();
        logger.error("[{}], [Controller-do181Block], do 181 block but without other setting", sessionId);
    }

    public AnalyseLog getAnalyseLogFlag() {
        return analyseLogFlag;
    }

    public AbnormalBillAnalyser getAbnormalBillAnalyser() {
        return abnormalBillAnalyser;
    }

    public void doAfterTerminate(){

    }

    public void recordAndTerminated(Dialog dialog) throws Exception {
        if (dialog != null) {
            try {
                SIPDialog sipDialog = (SIPDialog) dialog;
                Integer lastResponseStatusCode = sipDialog.getLastResponseStatusCode();
                THREAD_LAST_RESPONSE_STATECODE.set(lastResponseStatusCode);
            } catch (Exception e) {
                logger.error("[{}], recordLastResponseState error", sessionId, e);
            }
        }
        terminated();
    }
}
