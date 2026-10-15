package com.cmic.bizcontroller.event.response;

import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;

public class Sip183Response extends SipRespEvent {

    public Sip183Response(Leg leg, Leg oppositeLeg, SipResponseMessage sipResponseMessage) {
        super(leg, oppositeLeg, sipResponseMessage);
    }
}
