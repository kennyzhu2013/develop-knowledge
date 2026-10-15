package com.cmic.bizcontroller.callInfo;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginManager;
import com.cmic.recording.MediaSession;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.RightLeg;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Data;

/**
 * 记录呼叫过程中的主被叫能力信息、呼叫状态信息等
 */
@Data
public class CallRecorder {
    private boolean hasRecv200ok = false;

    private boolean leftIsIOS = false;

    private boolean hasSend180Event = false;

    // 左腿是否是precondition
    private boolean leftIsSuppotedPrecondition;
    // 右腿是否是precondition
    private boolean rightIsRequirePrecondition;

    private int callerSupportWb = -1;
    private int calleeSupportWb = -1;
    private int callerContactSupprtVideo = -1;
    private int calleeContactSupportVideo = -1;
    private boolean isCallerSupportVideo = false;

    private boolean isCallStartWithVideo = false;

    private SIPResponse lastRightResponse;
    private SIPRequest lastLeftRequest;

    private ControllerState controllerState;
    private PluginManager pluginManager;

    private CallParameter callParameter;
    private BillNew billNew;

    private MediaSession mediaSession;


    private LeftLeg leftLeg;
    private RightLeg rightLeg;

    private BusinessEvent businessEvent;


}
