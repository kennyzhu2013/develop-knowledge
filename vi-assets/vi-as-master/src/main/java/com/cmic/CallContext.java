package com.cmic;

import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.recording.MediaSession;
import com.cmic.state.PreciseCallState;
import lombok.Data;

@Data
public class CallContext {
    // 左腿是否是precondition
    boolean leftIsSupportedPrecondition;
    // 右腿是否是precondition
    boolean rightIsRequirePrecondition;
    BillNew billNew;
    long initialSeq;
    PreciseCallState preciseCallState;
    boolean leftIsIOS;
    MediaSession mediaSession;
    BusinessEvent businessEvent;
    CallParameter callParameter;

    public CallContext(BillNew billNew, PreciseCallState preciseCallState, BusinessEvent businessEvent, CallParameter callParameter) {
        this.billNew = billNew;
        this.preciseCallState = preciseCallState;
        this.businessEvent = businessEvent;
        this.callParameter = callParameter;
    }
}
