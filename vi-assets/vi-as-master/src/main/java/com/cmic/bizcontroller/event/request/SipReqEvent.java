package com.cmic.bizcontroller.event.request;

import com.cmic.bizcontroller.event.SipEvent;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.sip.leg.Leg;

public class SipReqEvent extends SipEvent {
    Leg leg;
    Leg oppositeLeg;
    SipRequestMessage sipRequestMessage;

    public SipReqEvent(Leg leg, Leg oppositeLeg, SipRequestMessage sipRequestMessage) {
        this.leg = leg;
        this.oppositeLeg = oppositeLeg;
        this.sipRequestMessage = sipRequestMessage;
    }

    public Leg getLeg() {
        return leg;
    }

    public Leg getOppositeLeg() {
        return oppositeLeg;
    }

    public SipRequestMessage getSipRequestMessage() {
        return sipRequestMessage;
    }
}
