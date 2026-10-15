package com.cmic.sip.leg;

import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.signaling.Signaling;
import com.cmic.util.AbnormalBillAnalyser;
import com.cmic.util.CustomBase64;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.header.extensions.SessionExpires;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.SipException;
import javax.sip.Transaction;

public class RightLeg extends Leg {
    private Logger logger = LoggerFactory.getLogger(getClass());


    public RightLeg(SipEngine engine, ClientTransaction firstInviteTransation, Dialog dialog) throws Exception {
        this.engine = engine;
        this.firstInviteTransation = firstInviteTransation;
        this.bindDialog(dialog);
        //        firstInviteTransation = (ClientTransaction) invite.getTransaction();
//        if (firstInviteTransation == null) {
//            firstInviteTransation = CallSipEngine.getInstance().sipProvider.getNewClientTransaction(invite);
//        }
//        if (firstInviteTransation.getDialog() == null) {
//            CallSipEngine.getInstance().sipProvider.getNewDialog(firstInviteTransation);
//        }
//        this.bindDialog(firstInviteTransation.getDialog());
    }

    public RightLeg(SipEngine engine, ClientTransaction firstInviteTransation, Dialog dialog, boolean fixTimer) throws Exception {
        this.engine = engine;
        this.firstInviteTransation = firstInviteTransation;
        this.bindDialog(dialog);
        if (fixTimer) {
            // 初始INVITE的默认值，由被叫刷新会话
            sessionExpires = new SessionExpires();
            sessionExpires.setExpires(1800);
            // debug for timer
//            sessionExpires.setExpires(600);
            sessionExpires.setRefresher("uac");
        }
        this.fixTimer = fixTimer;
    }

    public void sendInitialInvite() throws Exception {
        if(this.controller == null){
            throw new Exception("This leg not bind to controller!");
        }
        SIPRequest inviteRequest = (SIPRequest) firstInviteTransation.getRequest();
        processTimerForRequest(inviteRequest);
        processMidOrigMsgHeader(inviteRequest);
        this.controller.getAbnormalBillAnalyser().analyse(inviteRequest,
                AbnormalBillAnalyser.Type.SEND_INITIAL_INVITE, System.currentTimeMillis());
        if ( this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent(inviteRequest).toLogString(Signaling.getDirection(this,Signaling.SEND),Signaling.REQUEST));
        } else {
            Signaling.log(controller.signalParam.setContent(inviteRequest),Signaling.getDirection(this,Signaling.SEND),Signaling.REQUEST);
        }
        ((ClientTransaction) firstInviteTransation).sendRequest();
    }


    private void processMidOrigMsgHeader(SIPRequest request) {
        if(this.controller.infoCollector == null) return;
        if (!this.controller.infoCollector.isNeedMidOrigMsgHeader()) {
            return;
        }
        for(Leg leg : this.controller.getLegs()){
            // 如果是左腿则认为这个是初始
            if (leg.isLeft()){
                SIPRequest leftFirstInvite = (SIPRequest)leg.getFirstInviteRequest();
                String originCaller = SipUtil.getCallerNum(leftFirstInvite);
                String forwardCaller = SipUtil.getCallerNum(this.getFirstInviteRequest());
                if (leftFirstInvite.getHeader("Mid-Orig-Msg") != null){
                    logger.info("[{}] forward last Mid-Orig-Msg header {}", controller.sessionId, leftFirstInvite.getHeader("Mid-Orig-Msg"));
                    return;
                }
                // 如果相等的话证明是主叫透传，不添加Mid-Orig-Msg私有头部
                if(!originCaller.equalsIgnoreCase(forwardCaller)){
                    // 对主叫号码做加密
                    try {
                        request.addHeader(this.engine.headerFactory.createHeader("Mid-Orig-Msg", CustomBase64.encode(originCaller.getBytes())));
                    } catch (Exception e){
                        logger.error("add Mid-Orig-Msg header error: ", e);
                    }
                }
                // 只做一次, 理论上也只有一次
                break;
            }
        }

    }

    public void sendInitialInvite(Hook<SipResponseEvent> action) throws SipException {
        SIPRequest inviteRequest = (SIPRequest) firstInviteTransation.getRequest();
        if ( this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent(inviteRequest).toLogString(Signaling.getDirection(this,Signaling.SEND),Signaling.REQUEST));
        } else {
            Signaling.log(controller.signalParam.setContent(inviteRequest),Signaling.getDirection(this,Signaling.SEND),Signaling.REQUEST);

        }
        ((ClientTransaction) firstInviteTransation).sendRequest();
        hooks.put(inviteRequest.getTopmostViaHeader().getBranch(), action);
    }


    @Override
    public boolean isLeft() {
        return false;
    }

    @Override
    public Transaction getFirstInviteTransation() {
        return this.firstInviteTransation;
    }
}
