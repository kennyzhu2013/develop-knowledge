package com.cmic.bizcontroller.callInfo;

import com.cmic.config.ConstantConf;
import com.cmic.bizcontroller.feature.StreamManager;
import com.cmic.service.MaxCallDurationService;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.param.CallForward;
import com.cmic.util.*;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Data;

import javax.sip.message.Response;
import java.sql.Timestamp;
import java.util.concurrent.ScheduledFuture;

/**
 * 监听、管理、触发与呼叫关键节点：振铃、呼转、接通等相关的拓展功能，区别于插件需要全程监听和介入
 * 即时拓展功能：呼叫事件推送、实时媒体流、接通后收号等
 */

@Data
public class CallSession {
    private CallRecorder callRecorder;

    private StreamManager streamManager;

    // private PickUpDtmfManager pickUpDtmfManager;

    private ScheduledFuture maxCallControlTask;

    private boolean isNeedRealTimeStream = true;

    private boolean isNeedCallingEvent = true;

    public void consumeResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        String method = responseEvent.getMethod();
        int statuscode = response.getStatusCode();
        if (statuscode / 100 == 1) {
            TerminalUtil.recordTerminateBrand(leg, response, callRecorder.getBillNew());
        }
        if (method.equalsIgnoreCase(SIPRequest.INVITE)) {
            if (statuscode == Response.OK) {
                process200Invite();
            } else if (statuscode == Response.RINGING) {
                process180();
            } else if (statuscode == Response.CALL_IS_BEING_FORWARDED) {
                process181(response);
            }/*else if(statuscode == 183){
                callRecorder.setRightIsRequirePrecondition(SipUtil.isRequirePrecondition(response));
            }*/
        }
    }

    public void process200Invite() throws Exception {
        if (!callRecorder.isHasRecv200ok()) {
            callRecorder.setHasRecv200ok(true);
            callRecorder.getBillNew().setCallState(ConstantConf.CALL_STATE_OK);
            callRecorder.getBillNew().setCallStartTime(new Timestamp(System.currentTimeMillis()));
            //callRecorder.getMediaSession().arriveInv200(callRecorder.getBill().getCallStartTime().getTime());
            callRecorder.getMediaSession().dccPickup(callRecorder.getBillNew().getCallStartTime().getTime());

            if (isNeedCallingEvent) {
                callRecorder.getBusinessEvent().do200Invite();
            }
            // AFEventService.getInstance().doPickUpEvent(callRecorder.getBillNew());
            if (isNeedRealTimeStream) {
                streamManager.handleStreamStartTime(callRecorder.getRightLeg(), Response.OK, SIPRequest.INVITE, callRecorder.isHasSend180Event());
            }
            // 通知媒体面拉起接通后收号
            // if (callRecorder.getCallParameter().isNeedPickUpDtmfEvent()) {
            //     if (pickUpDtmfManager == null) {
            //         pickUpDtmfManager = new PickUpDtmfManager(callRecorder);
            //     }
            //     pickUpDtmfManager.doPickUpDtmf();
            // }

            DtmfLogger.info(callRecorder.getBillNew().getId(),"max duration:{},last minute voice:{}",callRecorder.getCallParameter().getMaxDuration(),callRecorder.getCallParameter().getLastMinVoice());
            // 定时任务放音，放完音后延时1分钟关闭通话
            if (callRecorder.getCallParameter().getMaxDuration() > 0) {
                maxCallControlTask = MaxCallDurationService.maxCallDurationControl(callRecorder.getLeftLeg(), callRecorder.getRightLeg(), callRecorder.getCallParameter(), callRecorder.getBillNew(), callRecorder.getMediaSession(), callRecorder.getBusinessEvent());
            }
        }
    }

    public void process180() throws Exception {
        if (!callRecorder.isHasSend180Event()) {
            callRecorder.getBillNew().setRingTime(new Timestamp(System.currentTimeMillis()));
            if (callRecorder.getBillNew().getCallState() != ConstantConf.CALL_STATE_181) {
                callRecorder.getBillNew().setCallState(ConstantConf.CALL_STATE_RINGING);
            }
            if (isNeedCallingEvent) {
                callRecorder.getBusinessEvent().do180Invite();
            }
            callRecorder.setHasSend180Event(true);
            if (isNeedRealTimeStream) {
                streamManager.handleStreamStartTime(callRecorder.getRightLeg(), Response.RINGING, SIPRequest.INVITE, callRecorder.isHasSend180Event());
            }
        }

    }

    public void process181(SIPResponse response) {
        callRecorder.getBillNew().setCallState(ConstantConf.CALL_STATE_181);
        CallForward callForward = HistoryInfoParserUtil.extractCallForwardInfo(response,true);
        // callRecorder.getBusinessEvent().updateCalleeCallForwardInfo(callForward.getCallForwardNo(), callForward.getCallForwardCause(), callForward.isUnconditional());
    }

}
