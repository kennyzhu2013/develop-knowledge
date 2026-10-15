package com.cmic.event.handler;

import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;

public interface Handler {
    Handler handleRequest(Leg leg, SipRequestMessage requestEvent);

    Handler handleResponse(Leg leg, SipResponseMessage responseEvent);
}
