package com.cmic.pojo;

import com.cmic.sip.event.SipRequestEvent;

public class SipRequestMessage {
    boolean isReject;
    SipRequestEvent sipRequestEvent;

    public SipRequestMessage(SipRequestEvent event) {
        this.sipRequestEvent = event;
    }

    public boolean isReject() {
        return isReject;
    }

    public void setReject(boolean reject) {
        isReject = reject;
    }

    public SipRequestEvent getSipRequestEvent() {
        return sipRequestEvent;
    }
}
