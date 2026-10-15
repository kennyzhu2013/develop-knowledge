package com.cmic.event.state;

import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;

public interface StateController {
    void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception;

    void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception;

    void switchInState() throws Exception;

    void switchOutState() throws Exception;
}
