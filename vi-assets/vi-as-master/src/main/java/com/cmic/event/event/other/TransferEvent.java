package com.cmic.event.event.other;

import com.cmic.event.event.SipEvent;
import com.cmic.sip.leg.Leg;

public class TransferEvent {
    int reason;
    Leg leg;
    SipEvent sipEvent;

    public TransferEvent(int reason, Leg leg, SipEvent sipEvent) {
        this.reason = reason;
        this.leg = leg;
        this.sipEvent = sipEvent;
    }

    public SipEvent getSipEvent() {
        return sipEvent;
    }

    public int getReason() {
        return reason;
    }

    public Leg getLeg() {
        return leg;
    }
}
