package com.cmic.pojo;

import com.cmic.sip.event.SipResponseEvent;

public class SipResponseMessage {
    SipResponseEvent sipResponseEvent;
    boolean isReject;

    public SipResponseMessage(SipResponseEvent event) {
        this.sipResponseEvent = event;
    }

    public SipResponseEvent getSipResponseEvent() {
        return sipResponseEvent;
    }

    public boolean isReject() {
        return isReject;
    }

    public void setReject(boolean reject) {
        isReject = reject;
    }
}
