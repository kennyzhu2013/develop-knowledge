package com.cmic.bizcontroller;

import com.cmic.business.businessparam.CallParameter;

public interface ControllerAction {
    public void setCallParameter(CallParameter callParameter);

    public void setCollectNumLength(int maxLength, int minLength, int collectWaitTime);
}
