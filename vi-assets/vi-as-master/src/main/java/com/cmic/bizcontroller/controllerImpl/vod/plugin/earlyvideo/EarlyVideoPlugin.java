package com.cmic.bizcontroller.controllerImpl.vod.plugin.earlyvideo;

import com.cmic.config.ConstantConf;
import com.cmic.constant.CommonConstant;
import com.cmic.constant.EarlyVConstant;
import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.AbstractPlugin;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEventResultVo;
import com.cmic.pojo.VideoParam;
import com.cmic.recording.MediaManager;
import com.cmic.service.MediaService;
import com.cmic.service.ScheduleService;
import com.cmic.service.XmodePromExporterService;
import com.cmic.signaling.Signaling;
import com.cmic.sip.B2buaHelper;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.Leg;
import com.cmic.util.*;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.ServerTransaction;
import javax.sip.header.ContactHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EarlyVideoPlugin extends AbstractPlugin {
    private Logger logger = LoggerFactory.getLogger(getClass());
    public EarlyVideoPlugin.VideoControllerMachine videoControllerMachine = new EarlyVideoPlugin.VideoControllerMachine();
    String videoIp;
    int videoPort;
    boolean videoFlag = false;
    private byte[] lastRightSdpRequestOrResponseWithVideo;
    byte[] sdp183;
    SipResponseEvent last180Resp = null;
    private boolean hasRecv180 = false;
    boolean isPlayingEarlyVideo = false;
    private String vsdp = "";
    private boolean isLeftLegFirstAckRecv = false;
    private AtomicReference<Integer> pluginStatus = new AtomicReference<>(EarlyVConstant.STATUS.NONE);

    private boolean isCallerRejectEarlyVideo = false;

    private boolean is180SentToCaller = false;

    private boolean isNeedToDownToVoice = false;

    private boolean doesCalleeHasVideoRing = false;

    private SIPHeader earlyMediaOf180 = null;


    public boolean isDoesCalleeHasVideoRing() {
        return doesCalleeHasVideoRing;
    }

    public void setDoesCalleeHasVideoRing(boolean doesCalleeHasVideoRing) {
        this.doesCalleeHasVideoRing = doesCalleeHasVideoRing;
    }

    @Override
    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        // 收集消息用于判断精准话单，如果判断被叫拒接会返回true
        boolean isReject = preciseCallState.extractResponseMsgPropertiesJudgeReject(response);
        // 已拒接就降级
        if (isReject || preciseCallState.isHasRecv181Msg()) {
            logger.info("[{}],EarlyVideoPlugin receive reject response!", billNew.getId());
            if (isPlayingEarlyVideo) {
                mediaSession.dccStopMedia();
                isPlayingEarlyVideo = false;
            } else {
                videoControllerMachine.setDownVideoPlay(true);
            }
            notifyStatusUpdate(EarlyVConstant.STATUS.END);
        }
        return videoControllerMachine.processResponse(leg, responseEvent);
    }

    @Override
    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        boolean isReject = preciseCallState.extractRequestMsgPropertiesJudgeReject(leg.isLeft(), request);
        // 已拒接就降级
        if (isReject || method.equalsIgnoreCase(Request.CANCEL) && leg.isLeft()) {
            logger.info("[{}],EarlyVideoPlugin receive reject/cancel request!", billNew.getId());
            if (isPlayingEarlyVideo) {
                mediaSession.dccStopMedia();
                isPlayingEarlyVideo = false;
            } else {
                videoControllerMachine.setDownVideoPlay(true);
            }
        }
        return videoControllerMachine.processRequest(leg, requestEvent);
    }

    @Override
    public boolean isComplete() {
        return pluginStatus.get() == EarlyVConstant.STATUS.END;
    }

    @Override
    public PluginEventResultVo accept(PluginEvent event) {
        logger.info("[{}] EarlyVideoPlugin start, doesCalleeHasVideoRing:{}", billNew.getId(), doesCalleeHasVideoRing);
        if (pluginStatus.compareAndSet(EarlyVConstant.STATUS.NONE, EarlyVConstant.STATUS.INIT)) {
            logger.info("[{}] EarlyVideoPlugin start...", billNew.getId());
            try {
                vsdp = EarlyVConstant.VSDP;
                vsdp = vsdp.replace("VIDEO_PORT", String.valueOf(mediaSession.getVideoport()));
                videoControllerMachine.switchState(videoControllerMachine.videoPreconditionController);
                videoControllerMachine.processResponse(event.getLeg(), event.getResponseEvent());
                return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
            } catch (Exception e) {
                logger.error("[{}] EarlyVideoPlugin accept exception.", billNew.getId(), e);
                return PluginEventResultVo.newPluginFailVo(EarlyVConstant.RETURN_CODE.OTHER_REASON, EarlyVConstant.RETURN_MSG.OTHER_REASON);
            }
        } else {
            logger.warn("[{}] EarlyVideoPlugin is preparing.", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(EarlyVConstant.RETURN_CODE.NEGOTIATION_IN_PROGRESS, EarlyVConstant.RETURN_MSG.NEGOTIATION_IN_PROGRESS);
        }
    }


    private void notifyStatusUpdate(int newStatus) {
        synchronized (this) {
            pluginStatus.set(newStatus);
        }
    }

    private void voicePrecondition() throws Exception {
        if (!videoControllerMachine.isOnVoiceState() || isNeedToDownToVoice) {

            //重新协商主叫为语音通话模式
            Request rightReInvite = rightLeg.createRequest(Request.INVITE);
            logger.info("billId={},rightReInvite={}", billNew.getId(), rightReInvite);
            Hook<SipResponseEvent> successAction = reInviteResEvent -> {
                SIPRequest leftUpdate = leftLeg.createRequest(Request.UPDATE);
                leftUpdate.setContent(mediaSession.processRightSDP(reInviteResEvent.getResponse().getRawContent()), engine.contentTypeHeader);
                leftUpdate.removeHeader(ContactHeader.NAME);
                leftUpdate.setHeader(this.engine.contactHeaderWithVideo);
                logger.info("billId={},leftReInvite={}", billNew.getId(), leftUpdate);
                sendRequest(leftLeg, leftUpdate, leftUpdateResEvent -> {
                    logger.info("billId={},after rightleg send reinvite", billNew.getId());
                    SIPRequest rightAckRequest = (SIPRequest) reInviteResEvent.getDialog().createAck(((SIPResponse) reInviteResEvent.getResponse()).getCSeq().getSeqNumber());
                    rightAckRequest.setContent(mediaSession.processLeftSDP(leftUpdateResEvent.getResponse().getRawContent()), engine.contentTypeHeader);
                    mediaSession.commit();
                    rightLeg.sendRequest(rightAckRequest);
                    leftLeg.sendResponse200(leftLeg.getFirstInviteRequest());
                    mediaSession.processUponPickup(true);
                    callSession.process200Invite();
                });
            };
            Hook<SipResponseEvent> failAction = event -> {
                int statusCode = event.getResponse().getStatusCode();
                if (statusCode == 491) {
                    logger.error("billID {},voicePreconditionFail: have got 491-reinvite ", billNew.getId());
                    ScheduleService.getInstance().schedule(() -> {
                        try {
                            // 重新协商主叫为语音通话模式
                            Request rightReInvite2 = rightLeg.createRequest(Request.INVITE);
                            sendRequest((SIPRequest) rightReInvite2, rightLeg, successAction, event2 -> {
                                // 还失败则拆线
                                notifyStatusUpdate(EarlyVConstant.STATUS.END);
                                leftLeg.sendByeOr487();
                                rightLeg.sendByeOrCancel();
                            });
                        } catch (Exception e) {
                            logger.error("billID {},voicePreconditionFail: have got 491-reinvite ", billNew.getId());
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                } else {
                    notifyStatusUpdate(EarlyVConstant.STATUS.END);
                    if (statusCode == 488) {
                        logger.error("billID {},voicePreconditionFail: have got 488-reinvite ", billNew.getId());
                    }
                }
            };
            sendRequest((SIPRequest) rightReInvite, rightLeg, successAction, failAction);
        } else {
            //若已经协商成语音模式
            leftLeg.sendResponse200(leftLeg.getFirstInviteRequest());
            mediaSession.processUponPickup(true);
            //mediaSession.arriveInv200();
            //businessEvent.do200Invite();
            callSession.process200Invite();

        }
    }


    private void prepareVideoUpdateToCaller(Leg leg, Leg oppositeLeg, Hook hook) throws Exception {
        // 最终进行视频彩铃协商的设lastoptype为3
        Hook<SipResponseEvent> successAction = new Hook<>() {
            @Override
            public void apply(SipResponseEvent event1) throws Exception {
                VideoParam videoParam = SdpHelper.getVideoParam(event1.getResponse());
                videoFlag = true;
                // 如果双腿不是标准p流程就不修改P-Early-Media字段
                videoIp = videoParam.getVideoIp();
                videoPort = videoParam.getVideoPort();
                if (videoPort == 0 || videoParam.isInactive()) {
                    // videoPort为0的情况不设置视频降级，不退出视频彩铃处理流程，因为还需要将主叫协商为AMR
                    send180(leftLeg, last180Resp,true);
                    isCallerRejectEarlyVideo = true;
                    hook.apply(null);
                } else {
                    //PreconditionParam preconditionParam = new PreconditionParam(getVideoSdp(event1.getResponse().getRawContent()));
                    Map<String, PreconditionParam> preconditionParamMap = PreconditionParam.getPreconditionParam(event1.getResponse().getRawContent());
                    PreconditionParam preconditionParam = preconditionParamMap.get("video");
                    if (preconditionParam == null || (preconditionParam.currLocalDirection != null && (preconditionParam.currLocalDirection.equalsIgnoreCase(PreconditionParam.DIRECTION_RECV) || preconditionParam.currLocalDirection.equalsIgnoreCase(PreconditionParam.DIRECTION_SENDRECV)))) {
                        send180(leftLeg, last180Resp,false);
                        String videoFile = callParameter.getVideoFile();
                        playVideo(videoFile);
                        hook.apply(null);
                    } else {
                        waitRequest(leftLeg, Request.UPDATE, event2 -> {
                            mediaSession.processLeftSDP(event2.getRequest().getRawContent());
                            VideoParam leftVideoParam = SdpHelper.getVideoParam(event2.getRequest());
                            try {
                                leg.sendResponse200(event2.getRequest(), message -> {
                                    String lasteUpdate200Ok = "";
                                    if (leftVideoParam.getVideoPort() > 0 && !leftVideoParam.isInactive()) {
                                        lasteUpdate200Ok = new String(mediaSession.processVideoPlayRightSDP(event2.getRequest().getRawContent())) + vsdp;
                                    } else {
                                        lasteUpdate200Ok = new String(mediaSession.processVideoPlayRightSDP(event2.getRequest().getRawContent())) + EarlyVConstant.CLOSE_VIDEO_SDP;
                                    }
                                    PreconditionParam preconditionParam1 = new PreconditionParam(getVideoSdp(event2.getRequest().getRawContent()));
                                    lasteUpdate200Ok = lasteUpdate200Ok.replaceFirst("none", "sendrecv");
                                    if (!StringUtils.isEmpty(preconditionParam1.currLocalDirection)) {
                                        lasteUpdate200Ok = lasteUpdate200Ok.replaceFirst("none", preconditionParam1.currLocalDirection);
                                    }
                                    lasteUpdate200Ok = lasteUpdate200Ok.replace("Z0LAHtkAoD2hAAADAAEAAAMAPA8WLkg=,aMuAjLI=", getVideoSPS(event2.getRequest().getRawContent()));
                                    mediaSession.commit();
                                    message.setContent(lasteUpdate200Ok, leg.engine.contentTypeHeader);
                                    message.setHeader(engine.headerFactory.createRequireHeader("precondition"));
                                });
                                send180(leftLeg, last180Resp,false);
                                String videoFile = callParameter.getVideoFile();
                                VideoParam vp = SdpHelper.getVideoParam(event2.getRequest());
                                videoPort = vp.getVideoPort();
                                videoIp = vp.getVideoIp();
                                playVideo(videoFile);
                            } catch (Exception e) {
                                logger.error("bill id {} ====not accept update request ", billNew.getId(), e);
                                leg.sendResponse488(event2.getRequest());
                                // 继续给主叫发180，不退出插件，因为后面收到 200-invite 会语音回落
                                send180(leftLeg, last180Resp,true);
                                videoControllerMachine.setDownVideoPlay(true);
                                String videoFile = callParameter.getVideoFile();
                                boolean isForce = (leg.pearlyMedia.isEmpty() || leg.pearlyMedia.equalsIgnoreCase("inactive")) || leg.pearlyMedia.equalsIgnoreCase("supported");
                                //播放视频文件的语音
                                mediaSession.playearly(videoFile, 1, isForce, null);
                                isPlayingEarlyVideo = true;
                            }
                            /*send180(leftLeg, last180Resp);
                            String videoFile = callParameter.getVideoFile();
                            VideoParam vp = SdpHelper.getVideoParam(event2.getRequest());
                            videoPort = vp.getVideoPort();
                            videoIp = vp.getVideoIp();
                            playVideo(videoFile);*/
                            hook.apply(null);
                        });
                    }
                }
            }
        };
        Hook<SipResponseEvent> failAction = new Hook<SipResponseEvent>() {
            @Override
            public void apply(SipResponseEvent event) throws Exception {
                if (event.getResponse().getStatusCode() == 491) {
                    logger.error("billID {},preVideofail: have got 491-update ", billNew.getId());
                    ScheduleService.getInstance().schedule(() -> {
                        try {
                            SIPRequest update = oppositeLeg.createRequest(Request.UPDATE);
                            String newSdp = new String(mediaSession.processVideoPlayRightSDP(initialInviteRequest.getRawContent())) + vsdp;
                            lastRightSdpRequestOrResponseWithVideo = newSdp.getBytes();
                            update.setContent(newSdp, leg.engine.contentTypeHeader);
                            update.setHeader(engine.headerFactory.createRequireHeader("precondition"));
                            update.setHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
                            sendRequest(update, oppositeLeg, successAction, event3 -> {
                                // 还协商失败就降级
                                videoControllerMachine.setDownVideoPlay(true);
                                // 将180发回给主叫
                                send180(leftLeg, last180Resp,true);
                            });
                        } catch (Exception e) {
                            // 报错就降级
                            videoControllerMachine.setDownVideoPlay(true);
                            logger.error("billID {},preVideofail: ", billNew.getId(), e);
                        }
                    }, 1, TimeUnit.SECONDS);
                } else if (event.getResponse().getStatusCode() / 100 == 4) {
                    logger.error("billID {},preVideofail: have got {} ", billNew.getId(), event.getResponse().getStatusCode());
                    videoControllerMachine.setDownVideoPlay(true);
                    // 将180发回给主叫
                    send180(leftLeg, last180Resp,true);
                }

                hook.apply(null);

            }
        };
        try {
            SIPRequest update = oppositeLeg.createRequest(Request.UPDATE);
            String audioSdp = new String(mediaSession.processVideoPlayRightSDP(initialInviteRequest.getRawContent()));
            audioSdp = audioSdp.replaceFirst("remote none", "remote sendrecv");
            //audioSdp = SdpHelper.processAudioSdp(audioSdp.getBytes());
            String newSdp = audioSdp + vsdp;
            lastRightSdpRequestOrResponseWithVideo = newSdp.getBytes();
            update.setContent(newSdp, leg.engine.contentTypeHeader);
            //update.setHeader(engine.headerFactory.createRequireHeader("precondition"));
            update.setHeader(engine.headerFactory.createSupportedHeader("precondition"));
            update.setHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
            sendRequest(update, oppositeLeg, successAction, failAction);
        } catch (Exception e) {
            logger.error("[{}] prepareVideoUpdateToCaller exception.", billNew.getId(), e);
            send180(leftLeg, last180Resp,true);
            hook.apply(null);
        }

    }

    private void playVideo(String videoFile) {
        if (videoPort != 0) {
            String recordFile = MediaService.playVideoConsidering3gp(callParameter, videoFile, true, null, videoIp, videoPort, mediaSession, businessEvent, callParameter.getOpType(), resp -> {
                // billNew.setPlayVideo(true);
                // billNew.setVideoRingStartTime(System.currentTimeMillis());
            });
            //mediaSession.playvedio("H264", videoFile, 114, 640, 480, videoIp, videoPort, videoFile + ".h264");
            isPlayingEarlyVideo = true;
            if (StringUtils.isNotBlank(recordFile)) {
                // billNew.setMiguRingFile(recordFile);
            }
            //bill.setPlayVideo(true);
        }
    }

    private void send180(Leg leg, SipResponseEvent sipResponseEvent,boolean audioRingFallback) throws Exception {
        SIPResponse ringResponse = leg.createProvisionalResponse(180, false);
        ((ToHeader) ringResponse.getHeader(ToHeader.NAME)).setTag("cmic");
        if (sipResponseEvent != null) {
            B2buaHelper.inflateMessage(ringResponse, sipResponseEvent.getResponse());
        }
        if(earlyMediaOf180 != null && audioRingFallback){
            ringResponse.setHeader(earlyMediaOf180);
        }else{
            ringResponse.setHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
        }
        ServerTransaction st = (ServerTransaction) leg.getFirstInviteTransation();
        st.sendResponse(ringResponse);
        is180SentToCaller = true;
        if (isNeedCacheSingallingLog) {
            singalling.append(controllerState.getCallInfo().getOriginController().signalParam.setContent(ringResponse).toLogString(Signaling.getDirection(leg, Signaling.SEND), Signaling.RESPONSE));
        } else {
            Signaling.log(controllerState.getCallInfo().getOriginController().signalParam.setContent(ringResponse), Signaling.getDirection(leg, Signaling.SEND), Signaling.RESPONSE);
        }
    }

    private void sendRequest(SIPRequest request, Leg leg, Hook successAction, Hook failAction) throws Exception {
        sendRequest(leg, request, event -> {
            if (event.getResponse().getStatusCode() == 200) {
                if (successAction != null) {
                    successAction.apply(event);
                }
            } else {
                if (failAction != null) {
                    failAction.apply(event);
                }
            }
        });
    }

    public boolean ifSupportVideo(Request request) throws Exception {
        ListIterator list = request.getHeaders(ContactHeader.NAME);
        if (list == null) {
            return false;
        }
        while (list.hasNext()) {
            ContactHeader contactHeader = (ContactHeader) list.next();
            Iterator li = contactHeader.getParameterNames();
            while (li.hasNext()) {
                String parameter = (String) li.next();
                if (parameter.equalsIgnoreCase("video")) {
                    // 同时还要支持amr-wb才能支持视频
                    return supportAmrWb(request.getRawContent());
                }
            }
        }
        return false;
    }

    public boolean supportAmrWb(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        while (line != null) {
            if (line.contains("a=rtpmap")) {
                // a=rtpmap:18 G729/8000
                String[] parts = line.split(" ");
                String codeName = parts[1].split("/")[0].toLowerCase();
                if (codeName.equalsIgnoreCase("amr-wb")) {
                    return true;
                }
            }
            line = bf.readLine();
        }
        return false;
    }

    public String getVideoSPS(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        StringBuffer sb = new StringBuffer();
        while (line != null) {
            if (line.contains("sprop-parameter-sets")) {
                String[] sline = line.split(";");
                for (int i = 0; i < sline.length; i++) {
                    String s = sline[i];
                    if (s.contains("sprop-parameter-sets")) {
                        String[] a = s.split("sprop-parameter-sets=");
                        return a[1];
                    }
                }
            }
            line = bf.readLine();
        }
        return "";
    }

    public byte[] getVideoSdp(byte[] input) throws Exception {
        InputStreamReader isr = new InputStreamReader(new ByteArrayInputStream(input));
        BufferedReader bf = new BufferedReader(isr);
        String line = bf.readLine();
        boolean isNeedAppend = false;
        StringBuffer sb = new StringBuffer();
        while (line != null) {
            if (line.contains("m=video")) {
                isNeedAppend = true;
            }
            if (isNeedAppend) {
                sb.append(line).append("\r\n");
            }
            line = bf.readLine();
        }
        return sb.toString().getBytes();
    }

    interface VideoController {
        PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception;

        PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception;

        void switchInState() throws Exception;

        void switchOutState() throws Exception;
    }

    class VideoControllerMachine {
        EarlyVideoPlugin.VideoController videoPreconditionController;
        EarlyVideoPlugin.VideoController playVideoController;
        EarlyVideoPlugin.VideoController voicePreconditionController;
        private EarlyVideoPlugin.VideoController state;
        boolean hasRecv200 = false;
        SIPResponse lastRecv200;
        boolean downVideoPlay = false;
        boolean isOnVoiceState = false;

        public VideoControllerMachine() {
            videoPreconditionController = new EarlyVideoPlugin.VideoPreconditionController(this);
            playVideoController = new EarlyVideoPlugin.PlayVedioController(this);
            voicePreconditionController = new EarlyVideoPlugin.VoicePreconditionController(this);
            // state = videoPreconditionController;
        }

        public void setDownVideoPlay(boolean downVideoPlay) {
            logger.info("billId :{},downVideo ", billNew.getId());
            this.downVideoPlay = downVideoPlay;
        }

        public boolean isDownVideoPlay() {
            return downVideoPlay;
        }

        public void setHasRecv200(boolean hasRecv200) {
            this.hasRecv200 = hasRecv200;
        }

        public boolean isHasRecv200() {
            return hasRecv200;
        }

        public void setLastRecv200(SIPResponse lastRecv200) {
            this.lastRecv200 = lastRecv200;
        }

        public SIPResponse getLastRecv200() {
            return lastRecv200;
        }

        public void setState(EarlyVideoPlugin.VideoController state) {
            this.state = state;
        }

        public void setOnVoiceState(boolean onVoiceState) {
            isOnVoiceState = onVoiceState;
        }

        public boolean isOnVoiceState() {
            return isOnVoiceState;
        }

        public PluginEventResultVo processRequest(Leg var1, SipRequestEvent var2) throws Exception {
            return state.processRequest(var1, var2);
        }

        public PluginEventResultVo processResponse(Leg var1, SipResponseEvent var2) throws Exception {
            return state.processResponse(var1, var2);
        }

        public void switchState(EarlyVideoPlugin.VideoController state) throws Exception {
            state.switchOutState();
            setState(state);
            state.switchInState();
          /*  switch (state){
                case earlyController:
                    videoPreconditionController = new VideoPreconditionController(this);
                    setState(videoPreconditionController);
                    break;
                case videoPreconditionController:
                    playVideoController = new PlayVedioController(this);
                    setState(playVideoController);
                    break;
                case playVideoController:
                    voicePreconditionController = new VoicePreconditionController(this);
                    setState(voicePreconditionController);
                    break;
                case voicePreconditionController:
                    confirmedController = new ConfirmedController(this);
                    setState(confirmedController);
                    break;
            }*/
        }

    }

    class VideoPreconditionController implements EarlyVideoPlugin.VideoController {
        EarlyVideoPlugin.VideoControllerMachine videoMachine;

        public VideoPreconditionController(EarlyVideoPlugin.VideoControllerMachine machine) {
            videoMachine = machine;
        }

        @Override
        public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
            SIPRequest request = requestEvent.getRequest();
            //修复视频彩铃还未协商完成就收到主叫Cancel，未正确处理导致话单停留在 new 表
            if (request.getMethod().equalsIgnoreCase(Request.CANCEL) && leg.isLeft()) {
                notifyStatusUpdate(EarlyVConstant.STATUS.END);
                return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
            }

            String method = request.getMethod();
            // 在协商视频彩铃的时候若遇到Update则先回491，挂起请求
            if (method.equalsIgnoreCase(Request.UPDATE)) {
                leg.sendResponse(request, request.createResponse(491));
            } else {
                logger.info("billID {} VideoPrecondition receive request:{}", billNew.getId(), requestEvent.getRequest().toString());
            }
            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
        }

        @Override
        public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
            SIPResponse response = responseEvent.getResponse();
            String method = responseEvent.getMethod();
            int statuscode = response.getStatusCode();
            Leg oppositeLeg = getOppositeLeg(leg);

            logger.info("EarlyVideoPlugin VideoPreconditionController hasRecv180:{},hasSend183:{}", hasRecv180, hasSend183);

            if (statuscode == 180 && !hasRecv180) {
                earlyMediaOf180 = (SIPHeader) response.getHeader("P-Early-Media");
                if (SipUtil.isRequire100rel(response)) {
                    leg.sendPrack(response);
                }
                hasRecv180 = true;
                last180Resp = responseEvent;
                last180Resp.getResponse().removeHeader("Require");
                if (last180Resp.getResponse().getContentLength().getContentLength() != 0) {
                    last180Resp.getResponse().removeContent();
                }
                if (!hasSend183) {
                    SIPResponse relResponse = leftLeg.createProvisionalResponse(183, true);
                    relResponse.setHeader(engine.headerFactory.createRequireHeader("precondition"));
                    relResponse.setHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
                    sdp183 = mediaSession.processPlayRightSDP(initialInviteRequest.getRawContent());
                    relResponse.setContent(sdp183, engine.contentTypeHeader);
//            B2buaHelper.inflateMessage(relResponse,initialRequest);
                    leftLeg.sendReliableProvisionalResponse(relResponse);
                    waitRequest(leftLeg, Request.PRACK, sipRequestEvent -> {
                        leftLeg.sendResponse200(sipRequestEvent.getRequest());
                    });
                    waitRequest(leftLeg, Request.UPDATE, sipRequestEvent -> {
                        SIPRequest request = sipRequestEvent.getRequest();
                        PreconditionParam uacPreconditionParam = new PreconditionParam(request.getRawContent());
                        PreconditionParam uasPreconditionParam = uacPreconditionParam.oppositeClone();
                        byte[] sdpPrecondition = SdpUtil.processPrecondition(sdp183, uasPreconditionParam);
                        leg.sendResponse200(request, message -> {
                            message.setContent(sdpPrecondition, leg.engine.contentTypeHeader);
                        });
                        prepareVideoUpdateToCaller(leg, oppositeLeg, ev -> {
                            videoMachine.switchState(videoMachine.playVideoController);
                        });
                    });
                } else {
                    prepareVideoUpdateToCaller(leg, oppositeLeg, ev -> {
                        videoMachine.switchState(videoMachine.playVideoController);
                    });
                }
                /*bill.setRingTime(System.currentTimeMillis());
                bill.setCallState(ConstantConf.CALL_STATE_RINGING);
                businessEvent.do180Invite();*/
                callSession.consumeResponse(leg, responseEvent);
                return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
            }
            //重复回的180要回Prack，但是不触发其他业务逻辑
            if (statuscode == 180) {
                if (SipUtil.isRequire100rel(response)) {
                    leg.sendPrack(response);
                }
                return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
            }
            if (statuscode == 200 && method.equalsIgnoreCase(SIPRequest.INVITE)) {
                videoMachine.setHasRecv200(true);
                videoMachine.setLastRecv200(response);
                leg.sendAck();
                return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
            }
            logger.info("billID {} VideoPrecondition receive response:{}", billNew.getId(), responseEvent.getResponse().toString());
            oppositeLeg.forwardResponse(responseEvent, mediaSession, message -> {
                if (oppositeLeg.isLeft()) {
                    message.getToHeader().setTag("cmic");
                }
            }, false);
            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
        }

        @Override
        public void switchInState() throws Exception {
            notifyStatusUpdate(EarlyVConstant.STATUS.VIDEO_PREPARE);
        }

        @Override
        public void switchOutState() throws Exception {

        }

    }

    class PlayVedioController implements EarlyVideoPlugin.VideoController {
        EarlyVideoPlugin.VideoControllerMachine videoMachine;

        public PlayVedioController(EarlyVideoPlugin.VideoControllerMachine machine) {
            videoMachine = machine;
        }

        @Override
        public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
            SIPRequest request = requestEvent.getRequest();
            Leg oppositeLeg = getOppositeLeg(leg);
            String method = request.getMethod();
            // 收到右腿的update就设置为语音状态
            if (method.equalsIgnoreCase(Request.UPDATE) && !leg.isLeft()) {
                videoMachine.setOnVoiceState(true);
            }
            // TODO: 增加是否直接回200-UPDATE
            if (leg.isLeft() && method.equalsIgnoreCase(Request.UPDATE) && isCallerRejectEarlyVideo && is180SentToCaller && doesCalleeHasVideoRing) {
                String answerSdp = new String(mediaSession.processVideoPlayRightSDP(request.getRawContent(), false));
                answerSdp = answerSdp + EarlyVConstant.CLOSE_VIDEO_SDP;
                SIPResponse response = request.createResponse(200);
                response.setContent(answerSdp.getBytes(), engine.contentTypeHeader);
                leg.sendResponse(request, response);
                isNeedToDownToVoice = true;
            } else {
                oppositeLeg.forwardRequest(request, mediaSession, null, false);
            }

            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
        }

        @Override
        public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
            SIPResponse response = responseEvent.getResponse();
            String method = responseEvent.getMethod();
            int statuscode = response.getStatusCode();
            Leg oppositeLeg = getOppositeLeg(leg);
            if (method.equalsIgnoreCase(Request.INVITE) && statuscode == 200) {
                videoMachine.setHasRecv200(true);
                videoMachine.setLastRecv200(response);
                SIPRequest ack = leg.createAck(response);
                leg.sendRequest(ack);
                refreshBaseState(leg, SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.ACK), ack.getTransactionId());
                videoMachine.switchState(videoMachine.voicePreconditionController);
            } else {
                // 兼容重复发的180情况
                if (statuscode == 180) {
                    if (SipUtil.isRequire100rel(response)) {
                        leg.sendPrack(response);
                    }
                    return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
                } else if (method.equalsIgnoreCase(Request.UPDATE) && statuscode == 200 && is180SentToCaller && doesCalleeHasVideoRing && isCallerRejectEarlyVideo) {
                    String content = response.getMessageContent();
                    //String newContent = SdpHelper.addAudioQosLine(content.getBytes());
                    //response.setContent(newContent.getBytes(), engine.contentTypeHeader);
                    if (StringUtils.isNotBlank(content) && content.contains("qos local none")) {
                        //String newContent = SdpHelper.trimAudioQosLine(content.getBytes());
                        String newContent = content.replaceFirst("a=curr:qos local none", "a=curr:qos local sendrecv");
                        response.setContent(newContent.getBytes(), engine.contentTypeHeader);
                    }
                }/*else if(leg.isLeft() && method.equalsIgnoreCase(Request.UPDATE) && isCallerRejectEarlyVideo && is180SentToCaller && doesCalleeHasVideoRing){
                    String respSdp = responseEvent.getResponse().getMessageContent();
                    if(respSdp != null){
                        String newRespSdp = respSdp.replaceFirst("a=curr:qos local none","a=curr:qos local sendrecv");
                        responseEvent.getResponse().setContent(newRespSdp.getBytes(), engine.contentTypeHeader);
                    }
                }*/
                oppositeLeg.forwardResponse(responseEvent, mediaSession, message -> {
                    if (oppositeLeg.isLeft()) {
                        message.getToHeader().setTag("cmic");
                    }
                }, false);
            }
            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
        }

        @Override
        public void switchInState() throws Exception {
            if (videoMachine.isHasRecv200()) {
                videoMachine.switchState(videoMachine.voicePreconditionController);
            }
            notifyStatusUpdate(EarlyVConstant.STATUS.VIDEO_PLAYING);
        }

        @Override
        public void switchOutState() throws Exception {

        }


    }

    class VoicePreconditionController implements EarlyVideoPlugin.VideoController {
        EarlyVideoPlugin.VideoControllerMachine videoMachine;


        public VoicePreconditionController(EarlyVideoPlugin.VideoControllerMachine machine) {
            videoMachine = machine;
        }

        @Override
        public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
            logger.info("billID {} VoicePrecondition receive request:{}", billNew.getId(), requestEvent.getRequest().toString());
            SIPRequest request = requestEvent.getRequest();
            if (request.getMethod().equalsIgnoreCase(Request.CANCEL) && leg.isLeft()) {
                // leg.sendResponse(request, request.createResponse(491));
                notifyStatusUpdate(EarlyVConstant.STATUS.END);
                //leg.sendResponse200(request);
                return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
            }

            //修复收到 200-invite 之后语音回落期间，收到被叫 BYE 未及时透传引发话单存留在 new 表的问题
            if (request.getMethod().equalsIgnoreCase(Request.BYE)) {
                notifyStatusUpdate(EarlyVConstant.STATUS.END);
                return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
            }

            if (request.getMethod().equalsIgnoreCase(Request.ACK) && leg.isLeft() && !isLeftLegFirstAckRecv) {
                logger.info("[{}] VoicePreconditionController ack from left leg.", billNew.getId());
                billNew.setCallState(ConstantConf.CALL_STATE_ACK);
                preciseCallState.setHasRecvAck(true);
                isLeftLegFirstAckRecv = true;
                notifyStatusUpdate(EarlyVConstant.STATUS.END);
                //bill.setCallStartTime(System.currentTimeMillis());

                if (!callParameter.getCalledPlayUrl().isEmpty() && CommonConstant.PLAY_MODE.NO_VOD.equals(callParameter.getCalledVideoPlayMode())) {
                    //mediaSession.setPickupAudioPlaying(true);
                    //mediaSession.play(callParameter.getCalledPlayUrl(), 3);
                    MediaService.xmPlay(callParameter.getCalledPlayUrl(), 3, e -> {
                        mediaSession.setPickupAudioPlaying(false);
                    }, mediaSession, false, callParameter.getOpType(), businessEvent, MediaManager.CALL_BACK_URL);
                }
                return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.EVENT_PASS);
            }

            logger.info("[{}] VoicePreconditionController. method:{}, leg is left:{} ", billNew.getId(), request.getMethod(), leg.isLeft());
            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);
        }

        @Override
        public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
            logger.info("billID {} VoicePrecondition receive response:{}", billNew.getId(), responseEvent.getResponse().toString());
            Leg oppositeLeg = getOppositeLeg(leg);
            //修复视频彩铃处于播放状态时，同时收到右腿 200-invite 和 200-update ，状态机发生转变，导致 200-update 没有透传给主叫，主叫延迟很久才回 ACK 的问题
            oppositeLeg.forwardResponse(responseEvent, mediaSession, message -> {
                if (oppositeLeg.isLeft()) {
                    message.getToHeader().setTag("cmic");
                }
            }, false);
            return PluginEventResultVo.newDealSuccessVo(EarlyVConstant.RETURN_CODE.SUCCESS, EarlyVConstant.RETURN_MSG.SUCCESS);

        }

        @Override
        public void switchInState() throws Exception {
            // if (billNew.getVideoRingStartTime() != null) {
            //     billNew.setVideoRingEndTime(System.currentTimeMillis());
            // }
            notifyStatusUpdate(EarlyVConstant.STATUS.VOICE_PREPARE);
            if (videoMachine.isHasRecv200()) {
                XmodePromExporterService.getInstance().incCall200Num();
                XmodePromExporterService.getInstance().incInviteOKInfoCount(billNew);
                billNew.setCallState(ConstantConf.CALL_STATE_OK);
                voicePrecondition();
            }
        }

        @Override
        public void switchOutState() throws Exception {

        }


    }
}
