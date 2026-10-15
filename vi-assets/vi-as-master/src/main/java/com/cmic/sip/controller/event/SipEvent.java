package com.cmic.sip.controller.event;

import com.cmic.sip.controller.event.Parameter;

public class SipEvent {

    //根据号码（主叫、中间号、被叫）选择控制流程, 默认使用标准b2b
    public Parameter doPrepareParameter() {
        return new Parameter(false);
    }

    public void doSendInvite() {
    }

    public void do180Invite() {
    }

    public void do200Invite() {
    }

    public void doCallTerminated() {
    }
}
