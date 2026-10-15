package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import gov.nist.javax.sip.message.SIPRequest;

/**
 * 开始状态
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class StartState extends AbstractCallState {
    public static final int StartStateCode = 0;

    public StartState(boolean isUac) {
        super(isUac, StartStateCode);
    }

    @Override
    public AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc) {
        if (sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.INVITE)) ||
                sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.INVITE))) {
            return new EarlyState(isUac);
        }
        return this;
    }
}
