package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import gov.nist.javax.sip.message.SIPRequest;

/**
 * 摘机状态
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class PickUpState extends AbstractCallState {
    public static int PickUpStateCode = 3;

    public PickUpState(boolean isUac) {
        super(isUac, PickUpStateCode);
    }

    @Override
    public AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc) {
        if (isUac) {
            boolean isBye = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.BYE));
            boolean isRecv45xx = sipMessageDesc.partOf(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.INVITE, 4, 5));
            boolean toTerminate = isBye || (isRecv45xx && sipMessageDesc.getFirstStateCode() != 491);
            if (toTerminate) {
                return new TerminateState(isUac);
            }
            return this;
        }
        // UAS
        boolean isBye = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.BYE));
        boolean isSend45xx = sipMessageDesc.partOf(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.INVITE, 4, 5));
        boolean toTerminate = isBye || (isSend45xx && sipMessageDesc.getFirstStateCode() != 491);
        if (toTerminate) {
            return new TerminateState(isUac);
        }
        return this;
    }
}
