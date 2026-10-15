package com.cmic.event.handler;

import com.cmic.CallContext;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.message.SIPResponse;

public class CallParamHandler implements Handler {
    CallContext callContext;

    public CallParamHandler(CallContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public Handler handleRequest(Leg leg, SipRequestMessage requestEvent) {
        return null;
    }

    @Override
    public Handler handleResponse(Leg leg, SipResponseMessage responseEvent) {
        SIPResponse response = responseEvent.getSipResponseEvent().getResponse();
        String method = responseEvent.getSipResponseEvent().getMethod();
        int statuscode = response.getStatusCode();
        if (statuscode == 183 && SipUtil.isRequirePrecondition(response)) {
            callContext.setRightIsRequirePrecondition(true);
        }
        return null;
    }
}
