package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import gov.nist.javax.sip.message.SIPRequest;

/**
 * 已振铃状态
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class RingedState extends AbstractCallState {
    public static final int RingedStateCode = 2;

    public RingedState(boolean isUac) {
        super(isUac, RingedStateCode);
    }

    @Override
    public AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc) {
        if (isUac) {
            boolean toPickUp = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.ACK));
            if (toPickUp) {
                return new PickUpState(isUac);
            }
            boolean isCancel = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.CANCEL));
            boolean isRecv45xx = sipMessageDesc.partOf(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.INVITE, 4, 5));
            boolean toTerminate = isCancel || (isRecv45xx && sipMessageDesc.getFirstStateCode() != 491);
            if (toTerminate) {
                return new TerminateState(isUac);
            }
            return this;
        }
        // UAS
        boolean toPickUp = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.ACK));
        if (toPickUp) {
            return new PickUpState(isUac);
        }
        boolean isCancel = sipMessageDesc.equals(SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.CANCEL));
        boolean isSend45xx = sipMessageDesc.partOf(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.INVITE, 4, 5));
        boolean toTerminate = isCancel || (isSend45xx && sipMessageDesc.getFirstStateCode() != 491);
        if (toTerminate) {
            return new TerminateState(isUac);
        }
        return this;
    }
}
