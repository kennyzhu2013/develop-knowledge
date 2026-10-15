package com.cmic.sip.event;

import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.ResponseEvent;
import javax.sip.header.CSeqHeader;

public class SipResponseEvent {
    public ResponseEvent event;

    public SipResponseEvent(ResponseEvent event) {
        this.event = event;
    }

    public SIPResponse getResponse() {
        return (SIPResponse) event.getResponse();
    }

    public ClientTransaction getClientTransaction() {
        return event.getClientTransaction();
    }

    public Dialog getDialog() {
        return event.getDialog();
    }

    public Object getSource() {
        return event.getSource();
    }

    public String getMethod() {
        CSeqHeader cseqHeader = (CSeqHeader) getResponse().getHeader("CSeq");
        return cseqHeader.getMethod();
    }

    public boolean isFirst183() {

        //todo: prepareRequst prepareResponse 中进行信令打标签
        return true;
    }

    public Leg getLeg() {
        return (Leg) event.getDialog().getApplicationData();
    }
}
