package com.cmic.event.handler;

import com.cmic.CallContext;
import com.cmic.config.ConstantConf;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.service.XmodePromExporterService;
import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPRequest;

import java.sql.Timestamp;

public class BillHandler implements Handler {
    CallContext callContext;
    boolean hasRecv180 = false;
    boolean hasRecv200 = false;

    public BillHandler(CallContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public Handler handleRequest(Leg leg, SipRequestMessage requestEvent) {
        SIPRequest request = requestEvent.getSipRequestEvent().getRequest();
        String method = request.getMethod();
        long seqNumer = request.getCSeq().getSeqNumber();
        if (method.equalsIgnoreCase(SIPRequest.BYE)) {
            callContext.getBillNew().setCallEndTime(new Timestamp(System.currentTimeMillis()));
        }

        //右腿的invite必定是reinvite，若是左腿的，大于initialSeq就是reinvite
        if (method.equalsIgnoreCase(SIPRequest.INVITE) && (!leg.isLeft() || seqNumer > callContext.getInitialSeq())) {
            callContext.getBillNew().setCallState(ConstantConf.CALL_STATE_REINVITE);
        }
        if (leg.isLeft() && method.equalsIgnoreCase(SIPRequest.ACK) && seqNumer == callContext.getInitialSeq()) {
            XmodePromExporterService.getInstance().incCall200Num();
            // 收到主叫ACK，设置通话状态为6-收到ACK（200-INVITE之后）
            callContext.getBillNew().setCallState(ConstantConf.CALL_STATE_ACK);
        }
        return null;
    }

    @Override
    public Handler handleResponse(Leg leg, SipResponseMessage responseEvent) {
        int statuscode = responseEvent.getSipResponseEvent().getResponse().getStatusCode();
        String method = responseEvent.getSipResponseEvent().getMethod();
        if (statuscode == 180 && !hasRecv180) {
            callContext.getBillNew().setRingTime(new Timestamp(System.currentTimeMillis()));
            if (callContext.getBillNew().getCallState() != ConstantConf.CALL_STATE_181) {
                callContext.getBillNew().setCallState(ConstantConf.CALL_STATE_RINGING);
            }
            hasRecv180 = true;
        }
        if (statuscode == 181) {
            callContext.getBillNew().setCallState(ConstantConf.CALL_STATE_181);
        }
        if (statuscode == 200 && method.equalsIgnoreCase(SIPRequest.INVITE)) {
            if (!hasRecv200) {
                callContext.getBillNew().setCallStartTime(new Timestamp(System.currentTimeMillis()));
                callContext.getBillNew().setCallState(ConstantConf.CALL_STATE_OK);
                hasRecv200 = true;
            }
        }
        return null;
    }
}
