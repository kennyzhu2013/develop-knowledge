package com.cmic.bizcontroller.event.response;

import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;

public class SipInvite200Response extends SipRespEvent {

    public SipInvite200Response(Leg leg, Leg oppositeLeg, SipResponseMessage sipResponseMessage) {
        super(leg, oppositeLeg, sipResponseMessage);
    }
}
