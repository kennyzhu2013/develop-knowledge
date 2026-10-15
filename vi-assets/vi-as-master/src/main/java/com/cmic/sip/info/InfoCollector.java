package com.cmic.sip.info;

import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Getter;
import lombok.Setter;

public abstract class InfoCollector {
    public String leftMediaDirection = "";
    public String rightMediaDirection = "";

    //public abstract int getMediaLDirection();

    //public abstract int getMediaRDirection();
    @Getter
    @Setter
    protected boolean needMidOrigMsgHeader = false;

    public abstract void saveMediaDirection(Leg leg, SIPMessage sipMessage);

    public abstract void checkCalleeTransfer(SIPResponse sipResponse);

    public abstract void saveQ850AndFirstRspTime(Leg leg,SIPResponse response);

}
