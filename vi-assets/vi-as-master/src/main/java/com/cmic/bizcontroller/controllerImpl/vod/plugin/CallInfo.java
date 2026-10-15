package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.callInfo.CallSession;
import com.cmic.recording.MediaSession;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.state.PreciseCallState;
import com.dcc.config.model.BaseProdNum;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Data;

import javax.sip.address.URI;

@Data
public class CallInfo {
    private BillNew billNew;
    private MediaSession mediaSession;
    private LeftLeg leftLeg;
    private RightLeg rightLeg;
    private SIPRequest initialInviteRequest;
    private SipEngine engine;
    private CallParameter callParameter;
    private BusinessEvent businessEvent;
    private boolean hasSend183;
    private boolean isNeedCacheSignallingLog;
    private long initialRightSeqNo;
    private PreciseCallState preciseCallState;
    private boolean callerSupportedVideo;
    private boolean hasSend180;
    private URI originRightTelUrl;
    private int reason;
    private Controller originController;
    private CallSession callSession;
    private String vodAudioCode;
    private boolean isSequenceMode = false;
    private boolean calleeHasVideoRing = false;
    private BaseProdNum prodNum;
    private Boolean callerContactSupportVideo;
    private Boolean calleeContactSupportVideo;
    private SIPResponse lastRightResponse;
    private SIPRequest lastLeftRequest;
}
