package com.cmic.bizcontroller.event.request;

import com.cmic.pojo.SipRequestMessage;
import com.cmic.sip.leg.Leg;

public class SipByeRequest extends SipReqEvent {
    public SipByeRequest(Leg leg, Leg oppositeLeg, SipRequestMessage sipRequestMessage) {
        super(leg, oppositeLeg, sipRequestMessage);
    }
}
