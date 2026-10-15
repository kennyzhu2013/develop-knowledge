package com.cmic.bizcontroller.event.response;

import com.cmic.bizcontroller.event.SipEvent;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;

public class SipRespEvent extends SipEvent {
    Leg leg;
    Leg oppositeLeg;
    SipResponseMessage sipResponseMessage;

    public SipRespEvent(Leg leg, Leg oppositeLeg, SipResponseMessage sipResponseMessage) {
        this.leg = leg;
        this.oppositeLeg = oppositeLeg;
        this.sipResponseMessage = sipResponseMessage;
    }

    public Leg getLeg() {
        return leg;
    }

    public Leg getOppositeLeg() {
        return oppositeLeg;
    }

    public SipResponseMessage getSipResponseMessage() {
        return sipResponseMessage;
    }
}
