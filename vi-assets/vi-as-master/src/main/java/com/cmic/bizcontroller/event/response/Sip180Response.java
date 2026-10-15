package com.cmic.bizcontroller.event.response;

import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;

public class Sip180Response extends SipRespEvent {
    public Sip180Response(Leg leg, Leg oppositeLeg, SipResponseMessage sipResponseMessage) {
        super(leg, oppositeLeg, sipResponseMessage);
    }
}
