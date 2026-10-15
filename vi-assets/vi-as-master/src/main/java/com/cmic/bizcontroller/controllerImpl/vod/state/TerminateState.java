package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;

/**
 * 销毁状态
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class TerminateState extends AbstractCallState {
    public static final int TerminateStateCode = 10;

    public TerminateState(boolean isUac) {
        super(isUac, TerminateStateCode);
    }

    @Override
    public AbstractCallState stateStepOn(SipMessageDesc sipMessageDesc) {
        return this;
    }
}
