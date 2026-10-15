package com.cmic.event.leg;

import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.LeftLeg;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.*;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class SzLeftLeg extends LeftLeg {
    private Logger logger = LoggerFactory.getLogger(getClass());

    public SzLeftLeg(SipEngine engine, ServerTransaction firstInviteTransation, Dialog dialog) throws SipException {
        super(engine, firstInviteTransation, dialog);
    }

    //收到初始invite，协商状态默认是false
    private boolean onPrecondition = false;
    public Message nowSipmessage;


    public void acceptRequest(Request sipRequest) {
        if (sipRequest.getContent() != null) {
            onPrecondition = !onPrecondition;
            nowSipmessage = sipRequest;
        }


    }

    public void acceptResponse(Response sipResponse) {
        if (sipResponse.getContent() != null) {
            onPrecondition = !onPrecondition;
            nowSipmessage = sipResponse;
        }
    }

    @Override
    public void sendRequest(SIPRequest request, Hook<SipResponseEvent> hook) throws Exception {
        if (request.getContent() != null) {
            onPrecondition = !onPrecondition;
            nowSipmessage = request;
        }
        super.sendRequest(request, hook);
    }

    @Override
    public void sendResponse(SIPRequest linkedRequest, Response originResponse) throws Exception {
        if (originResponse.getContent() != null) {
            onPrecondition = !onPrecondition;
            nowSipmessage = originResponse;
        }
        super.sendResponse(linkedRequest, originResponse);
    }

    @Override
    public boolean prepareRequest(RequestEvent requestEvent) throws Exception {
        acceptRequest(requestEvent.getRequest());
        return super.prepareRequest(requestEvent);
    }

    @Override
    public boolean prepareResponse(ResponseEvent responseEvent) throws Exception {
        acceptResponse(responseEvent.getResponse());
        return super.prepareResponse(responseEvent);
    }

    @Override
    public void sendReliableProvisionalResponse(Response relResponse) throws Exception {
        if (relResponse.getContent() != null) {
            onPrecondition = !onPrecondition;
            nowSipmessage = relResponse;
        }
        super.sendReliableProvisionalResponse(relResponse);
    }

    public boolean isOnPrecondition() {
        return onPrecondition;
    }
}
