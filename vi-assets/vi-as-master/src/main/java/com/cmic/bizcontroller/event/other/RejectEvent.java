package com.cmic.bizcontroller.event.other;

import com.cmic.sip.leg.Leg;

public class RejectEvent {
    boolean isReject;
    Leg leg;

    public RejectEvent(boolean isReject) {
        this.isReject = isReject;
    }

    public void setReject(boolean reject) {
        isReject = reject;
    }

    public boolean isReject() {
        return isReject;
    }
}
