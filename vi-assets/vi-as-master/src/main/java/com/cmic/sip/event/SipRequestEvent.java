package com.cmic.sip.event;

import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;

public class SipRequestEvent {
    public RequestEvent event;

    public SipRequestEvent(RequestEvent event) {
        this.event = event;
    }

    public SIPRequest getRequest() {
        return (SIPRequest) event.getRequest();
    }

    public ServerTransaction getServerTransaction() {
        return event.getServerTransaction();
    }

    public Dialog getDialog() {
        return event.getDialog();
    }

    public Object getSource() {
        return event.getSource();
    }

    public Leg getLeg() {
        return (Leg) event.getDialog().getApplicationData();
    }
}
