package com.cmic.bizcontroller.callInfo;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.feature.CallStateManager;
import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.recording.MediaSession;
import com.cmic.recording.SdpSession;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.state.PreciseCallState;
import com.dcc.config.model.BaseProdNum;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Data;

import java.util.concurrent.ScheduledFuture;

@Data
public class BaseCallContext {
    private PreciseCallState preciseCallState;
    private BillNew billNew;
    private BusinessEvent businessEvent;
    private CallParameter callParameter;
    private MediaSession mediaSession;
    private SdpSession sdpSession;
    private LeftLeg leftLeg;
    private RightLeg rightLeg;
    private SipEngine sipEngine;

    public SIPRequest initialInviteRequest;

    private CallStateManager callStateManager;

    private ControllerState controllerState;

    private CallRecorder callRecorder;

    //private AuthNum authNum;

    private BizNumAuthDo bizNumAuthDo;

    private NewTwoLegController controller;

    private long initialRightSeqNo;

    private ScheduledFuture lastTimeOutTask;

    public BaseCallContext(NewTwoLegController controller) {
        this.preciseCallState = controller.getPreciseCallState();
        this.callParameter = controller.getCallParameter();
        this.billNew = controller.getBillNew();
        this.businessEvent = controller.getBusinessEvent();
        this.initialInviteRequest = controller.getInitialInviteRequest();
        this.initialRightSeqNo = controller.getInitialRightSeqNo();
        this.mediaSession = controller.getMediaSession();
        this.controller = controller;
        this.leftLeg = controller.getLeftLeg();
        this.rightLeg = controller.getRightLeg();
        this.sipEngine = controller.getSipEngine();
        this.bizNumAuthDo = controller.getBizNumAuthDo();
    }
}
