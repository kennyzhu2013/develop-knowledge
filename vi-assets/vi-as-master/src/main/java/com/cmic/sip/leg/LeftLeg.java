package com.cmic.sip.leg;

import com.cmic.sip.engine.SipEngine;

import javax.sip.Dialog;
import javax.sip.ServerTransaction;
import javax.sip.SipException;

public class LeftLeg extends Leg {
    public LeftLeg(SipEngine engine, ServerTransaction firstInviteTransation, Dialog dialog) throws SipException {
        this.engine = engine;
        this.firstInviteTransation = firstInviteTransation;
        this.bindDialog(dialog);
        setStandbyToTag("cmic");
    }

    public LeftLeg(SipEngine engine, ServerTransaction firstInviteTransation, Dialog dialog, boolean fixTimer) throws SipException {
        this.engine = engine;
        this.firstInviteTransation = firstInviteTransation;
        this.bindDialog(dialog);
        this.fixTimer = fixTimer;
        setStandbyToTag("cmic");
    }

    @Override
    public boolean isLeft() {
        return true;
    }
}
