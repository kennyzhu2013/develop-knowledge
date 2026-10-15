package com.cmic.bizcontroller.controllerImpl;

import com.cmic.business.businessparam.CallParameter;
import com.cmic.recording.MediaSession;
import com.cmic.service.*;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.util.*;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.message.Request;

public class RejectController extends NewTwoLegController {


    private final Logger logger = LoggerFactory.getLogger(getClass());
    byte[] sdp183;

    public RejectController(String id, SipEngine engine) {
        super(id, new CallParameter(false), engine);
        isNeedCacheSingallingLog = false;
    }

    @Override
    public void start(SIPRequest sipRequest) throws Exception {

        //初始化左腿
        this.initialInviteRequest = sipRequest;
        LeftLeg leftLeg = this.engine.createLeftLeg(sipRequest);
        bindLeftLeg(leftLeg);
        XmodePromExporterService.getInstance().incLeftLegNum();
        SIPResponse response180 = leftLeg.createResponse(180, sipRequest);
        leftLeg.sendResponse(sipRequest, response180);
        // 回复602会重试，回复606、487终端会自动发起重试，回600终端会听到被叫忙音，回复503会有无法接通放音，目前测试回复603没有放音
        leftLeg.sendByeOrSpecificCodeResp(603, "caller no support here!");
    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {

        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        if (method.equals(Request.CANCEL)) {
            leg.sendResponse200(request);
            leg.sendResponse487();
        } else if (method.equals(Request.PRACK)) {
            leg.sendResponse200(request);
        } else if (method.equals(Request.UPDATE)) {
            PreconditionParam uacPreconditionParam = new PreconditionParam(request.getRawContent());
            PreconditionParam uasPreconditionParam = uacPreconditionParam.oppositeClone();
            byte[] sdpPrecondition = SdpUtil.processPrecondition(sdp183, uasPreconditionParam);
            leg.sendResponse200(request, message -> {
                message.setContent(sdpPrecondition, leg.engine.contentTypeHeader);
            });
        }

    }

    @Override
    public MediaSession getMediaSession() {
        return mediaSession;
    }

    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        preciseCallState.extractResponseMsgPropertiesJudgeReject(response);
    }

    @Override
    public void terminated() throws Exception {
        handleEnd();
    }

    private void handleEnd() {
        logger.info("[{}] RejectController handleEnd", sessionId);
    }


    @Override
    public void doAfterTerminate() {
        // do nothing
    }
}
