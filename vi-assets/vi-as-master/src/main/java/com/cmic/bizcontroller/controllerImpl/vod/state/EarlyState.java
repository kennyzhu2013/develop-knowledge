package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import gov.nist.javax.sip.message.SIPRequest;

/**
 * 早期状态
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class EarlyState extends AbstractCallState {
    public static final int EarlyStateCode = 1;

    public EarlyState(boolean isUac) {
        super(isUac, EarlyStateCode);
    }

    @Override
    public AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc) {
        if (isUac) {
            boolean toRinged = sipMessageDesc.equals(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.INVITE, 180));
            if (toRinged) {
                return new RingedState(isUac);
            }
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
        boolean toRinged = sipMessageDesc.equals(SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.INVITE, 180));
        if (toRinged) {
            return new RingedState(isUac);
        }
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
