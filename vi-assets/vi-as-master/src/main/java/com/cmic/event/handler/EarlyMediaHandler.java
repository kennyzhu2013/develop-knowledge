package com.cmic.event.handler;

import com.cmic.CallContext;
import com.cmic.pojo.SipRequestMessage;
import com.cmic.pojo.SipResponseMessage;
import com.cmic.sip.leg.Leg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EarlyMediaHandler implements Handler {
    CallContext callContext;
    boolean isSendPlayearlyCommond;
    Logger logger = LoggerFactory.getLogger(getClass());

    public EarlyMediaHandler(CallContext callContext) {
        this.callContext = callContext;
    }

    @Override
    public Handler handleRequest(Leg leg, SipRequestMessage requestEvent) {
        return null;
    }

    @Override
    public Handler handleResponse(Leg leg, SipResponseMessage responseEvent) {
        int statuscode = responseEvent.getSipResponseEvent().getResponse().getStatusCode();
        if (isSendPlayearlyCommond && responseEvent.isReject()) {
            callContext.getMediaSession().dccStopMedia();
        }
        //双腿支持precondition才可以进行放音
        if (statuscode == 180 && callContext.isLeftIsSupportedPrecondition() && callContext.isRightIsRequirePrecondition() && callContext.getPreciseCallState().canPlayearly()) {
            if (!isSendPlayearlyCommond) {
                if (!callContext.getCallParameter().getCallerPlayUrl().isEmpty()) {
                    logger.info("leg.pearlyMedia====== " + leg.pearlyMedia);
                    //亲测还要加上supported，当180带supported时也是没有媒体声音
                    boolean isForce = (leg.pearlyMedia.isEmpty() || leg.pearlyMedia.equalsIgnoreCase("inactive")) || leg.pearlyMedia.equalsIgnoreCase("supported");
                    //左腿是ios且inactive的话不放音
                    if (!callContext.isLeftIsIOS() || !isForce) {
                        callContext.getMediaSession().playearly(callContext.getCallParameter().getCallerPlayUrl(), 1, isForce, null);
                    } else {
                        logger.info("======bill ID:{}===the case of that ios didn't play early media", callContext.getBillNew().getId());
                    }
                    isSendPlayearlyCommond = true;
                }
            }
        }
        return null;
    }
}
