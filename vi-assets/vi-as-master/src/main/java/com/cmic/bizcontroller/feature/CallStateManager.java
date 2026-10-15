package com.cmic.bizcontroller.feature;

import com.cmic.bizcontroller.callInfo.CallRecorder;
import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.CallInfo;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEventResultVo;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import com.cmic.util.SdpHelper;
import com.cmic.util.SipUtil;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

public class CallStateManager {

    private CallRecorder callRecorder = null;

    public CallStateManager(CallRecorder callRecorder) {
        this.callRecorder = callRecorder;
    }

    private void pluginRecordRequest(Leg leg, SipRequestEvent requestEvent) {
        SIPRequest request = requestEvent.getRequest();
        if (leg.isLeft()) {
            callRecorder.setLastLeftRequest(request);
            callRecorder.getControllerState().getCallInfo().setLastLeftRequest(request);
        }
    }

    public PluginEventResultVo pluginTakeRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        pluginRecordRequest(leg, requestEvent);
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        SipMessageDesc recvDesc = SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, method);
        return callRecorder.getPluginManager().tackOverSipRequest(leg, requestEvent, recvDesc);
    }

    public PluginEventResultVo pluginTakeResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        pluginRecordResponse(leg, responseEvent);
        SipMessageDesc responseDesc = SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, responseEvent.getMethod(), responseEvent.getResponse().getStatusCode());
        // 通过Plugin过滤器，如果被Plugin消费则返回
        return callRecorder.getPluginManager().tackOverSipResponse(leg, responseEvent, responseDesc);
    }

    private void pluginRecordResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        CallInfo callInfo = callRecorder.getControllerState().getCallInfo();
        if (callRecorder.getCalleeContactSupportVideo() == -1) {
            if (SipUtil.ifSupportVideo(response)) {
                callRecorder.setCalleeContactSupportVideo(VodConstant.VOD.CONTACT_HEADER.CONTAIN_VIDEO);
                callInfo.setCalleeContactSupportVideo(true);
            } else {
                callRecorder.setCalleeContactSupportVideo(VodConstant.VOD.CONTACT_HEADER.NOT_CONTAIN_VIDEO);
                callInfo.setCalleeContactSupportVideo(false);
            }
        }

        if (!leg.isLeft() && callRecorder.getCalleeSupportWb() == -1) {
            byte[] rawContent = response.getRawContent();
            if (rawContent != null && rawContent.length > 0) {
                if (SdpHelper.isSDPMediaContainAmrWb(rawContent)) {
                    callRecorder.setCalleeSupportWb(VodConstant.VOD.SDP.CONTAIN_AMRWB);
                } else {
                    callRecorder.setCalleeSupportWb(VodConstant.VOD.SDP.NOT_CONTAIN_AMRWB);
                }
            }
        }

        if (!leg.isLeft()) {
            callRecorder.setLastRightResponse(response);
            callRecorder.getControllerState().getCallInfo().setLastRightResponse(response);
        }
    }


    public void pluginForwardStateFlushed(Leg oppositeLeg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        int statuscode = response.getStatusCode();
        String method = responseEvent.getMethod();
        callRecorder.getControllerState().consumeSipMessage(oppositeLeg, SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.SEND, method, statuscode), callRecorder.getControllerState().getForwardTransactionId(responseEvent));
    }

    public void pluginForwardStateFlushed(Leg oppositeLeg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        callRecorder.getControllerState().consumeSipMessage(oppositeLeg, SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, method), callRecorder.getControllerState().getForwardTransactionId(request));
    }

    public void flushStateUponResponse(Leg leg, int statusCode, String method, String transactionId) throws Exception {
        callRecorder.getControllerState().consumeSipMessage(leg, SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.SEND, method, statusCode), transactionId);
    }

    public void pluginRecordStart(SIPRequest initialRequest) throws Exception {
        callRecorder.setCallerSupportVideo(SipUtil.ifSupportVideo(initialRequest));
        if (callRecorder.isCallerSupportVideo()) {
            callRecorder.setCallerContactSupprtVideo(VodConstant.VOD.CONTACT_HEADER.CONTAIN_VIDEO);
        } else {
            callRecorder.setCallerContactSupprtVideo(VodConstant.VOD.CONTACT_HEADER.NOT_CONTAIN_VIDEO);
        }

        byte[] rawContent = initialRequest.getRawContent();
        if (rawContent != null && rawContent.length > 0) {
            if (SdpHelper.isSDPMediaContainAmrWb(rawContent)) {
                callRecorder.setCallerSupportWb(VodConstant.VOD.SDP.CONTAIN_AMRWB);
            } else {
                callRecorder.setCallerSupportWb(VodConstant.VOD.SDP.NOT_CONTAIN_AMRWB);
            }
        }

    }
}
