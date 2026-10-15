package com.cmic.event.handler;

import com.cmic.CallContext;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.service.XmodePromExporterService;
import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPRequest;

public class MonitorHandler implements Handler {

    CallContext callContext;

    public MonitorHandler(CallContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public Handler handleRequest(Leg leg, SipRequestMessage requestEvent) {
        SIPRequest request = requestEvent.getSipRequestEvent().getRequest();
        String method = request.getMethod();
        long seqNumer = request.getCSeq().getSeqNumber();
        if (leg.isLeft() && method.equalsIgnoreCase(SIPRequest.ACK) && seqNumer == callContext.getInitialSeq()) {
            XmodePromExporterService.getInstance().incCall200Num();
        }
        return null;
    }

    @Override
    public Handler handleResponse(Leg leg, SipResponseMessage responseEvent) {
        return null;
    }
}
