package com.cmic.event.handler;

import com.cmic.CallContext;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHandler implements Handler {
    CallContext callContext;
    Logger logger = LoggerFactory.getLogger(getClass());
    boolean hasSend180Event = false;
    boolean hasSend200Event = false;

    public EventHandler(CallContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public Handler handleRequest(Leg leg, SipRequestMessage requestEvent) {
        return null;
    }

    @Override
    public Handler handleResponse(Leg leg, SipResponseMessage responseEvent) {
        if (!hasSend180Event) {
            try {
                callContext.getBusinessEvent().do180Invite();
            } catch (Exception e) {
                // logger.error("Bill id is {} , send180Event error {}", callContext.getBill().getBillId(), e.getMessage());
            }
            hasSend180Event = true;
            // AFEventService.getInstance().doRingingEvent(callContext.getBill());
        }
        if (!hasSend200Event) {
            try {
                callContext.getMediaSession().arriveInv200();
                callContext.getBusinessEvent().do200Invite();
            } catch (Exception e) {
                // logger.error("Bill id is {} , send200Event error {}", callContext.getBill().getBillId(), e.getMessage());
            }
            hasSend200Event = true;
            // AFEventService.getInstance().doPickUpEvent(callContext.getBill());
        }

        return null;
    }
}
