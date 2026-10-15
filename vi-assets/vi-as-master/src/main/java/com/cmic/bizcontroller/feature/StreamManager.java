package com.cmic.bizcontroller.feature;

import com.cmic.bizcontroller.callInfo.CallRecorder;
import com.cmic.sip.leg.Leg;
import com.cmic.util.DtmfLogger;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.message.Response;

public class StreamManager {

    private CallRecorder callRecorder;

    private boolean last180ResponseHasEarly = false;

    public StreamManager(CallRecorder callRecorder) {
        this.callRecorder = callRecorder;
    }

    public void handleStreamStartTime(Leg leg, int statusCode, String method, boolean hasSend180Event) {
        if (statusCode == Response.OK && method.equalsIgnoreCase(SIPRequest.INVITE)) {
            boolean ifStartCallStream = callRecorder.getCallParameter().getStreamSource() != 0 &&
                    (callRecorder.getCallParameter().getStreamType() == 2 || callRecorder.getCallParameter().getStreamType() == 3);
            if (ifStartCallStream) {
                if (callRecorder.getCallParameter().getStreamStartTime() == null || (hasSend180Event && !last180ResponseHasEarly)) {
                    if (callRecorder.getBillNew().getCallStartTime() != null) {
                        // 这里存在重复进入，但是每次都是通话开始时间
                        callRecorder.getCallParameter().setStreamStartTime(callRecorder.getBillNew().getCallStartTime().getTime());
                    }
                }
            } else if (callRecorder.getCallParameter().getStreamSource() != 0 && callRecorder.getCallParameter().getStreamType() == 1) {
                // 如果只需要早期，则要在收到200-INVITE的时候结束早期计费
                if (!callRecorder.getCallParameter().isStreamDurationWritten() && callRecorder.getCallParameter().getStreamStartTime() != null) {
                    callRecorder.getCallParameter().setStreamDurationWritten(true);
                    // callRecorder.getBillNew().setStreamDurationS(System.currentTimeMillis() - callRecorder.getCallParameter().getStreamStartTime().getTime());
                    // DtmfLogger.info(callRecorder.getBillNew().getBillId(), "call need early real-time stream only,stop for receiving 200-INVITE streamDuration: {} s", callRecorder.getBillNew().getStreamDurationS());
                }
            }
        } else {
//            DtmfLogger.info(callRecorder.getBill().getBillId(), "no-200-INVITE streamManager callRecorder:{}", callRecorder);
            boolean ifStartEarlyStream = callRecorder.getCallParameter().getStreamSource() != 0 &&
                    (callRecorder.getCallParameter().getStreamType() == 1 || callRecorder.getCallParameter().getStreamType() == 3) && callRecorder.getMediaSession().getFirstCommitTime() != null;
            if (ifStartEarlyStream) {
                // 如果启动时间还未初始化并且还未收到180-Ringing，启动时间等于首次UPDATE时间
                if (callRecorder.getCallParameter().getStreamStartTime() == null && !hasSend180Event) {
                    callRecorder.getCallParameter().setStreamStartTime(callRecorder.getMediaSession().getFirstCommitTime().getTime());
                }
                if (statusCode == Response.RINGING) {
                    boolean inactive = (leg.pearlyMedia.isEmpty() || "inactive".equalsIgnoreCase(leg.pearlyMedia)) || "supported".equalsIgnoreCase(leg.pearlyMedia);
                    // 如果未收到指示有早期媒体的180-Ringing
                    if (!last180ResponseHasEarly) {
                        // 如果180-Ringing指示有被叫早期媒体
                        if (!inactive) {
                            last180ResponseHasEarly = true;
                            callRecorder.getCallParameter().setStreamStartTime(System.currentTimeMillis());
                        } else {
                            // 180-Ringing指示无被叫侧早期媒体，则实时媒体流启动时间还原为未初始化状态
                            callRecorder.getCallParameter().setStreamStartTime(null);
                        }
                    }
                }
            }
        }
    }
}
