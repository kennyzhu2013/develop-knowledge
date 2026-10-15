package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.constant.CommonConstant;
import com.cmic.constant.OBVConstant;
import com.cmic.constant.VodConstant;
import com.cmic.bizcontroller.controllerImpl.vod.state.PickUpState;
import com.cmic.bizcontroller.controllerImpl.vod.transactionholder.TransactionHolder;
import com.cmic.bizcontroller.controllerImpl.vod.transactionholder.TransactionHolderFactory;
import com.cmic.pojo.VideoParam;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.service.ScheduleService;
import com.cmic.service.XmodePromExporterService;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.Leg;
import com.cmic.util.*;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;

import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.message.Request;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 插播视频插件
 * Author: chenxiuhong
 * Date: 2022/10/19
 */
public class VodPlugin extends AbstractPlugin {

    private volatile int status = VodConstant.VOD.STATUS.NONE;
    private volatile boolean needToRetreate = false;
    private volatile boolean isVodEventSent = false;
    private int leftNegotiation = VodConstant.VOD.NEGOTIATION.UNKNOW;
    private int rightNegotiation = VodConstant.VOD.NEGOTIATION.UNKNOW;
    private VodInfo vodInfo;
    private String vsdp = "";
    //private VideoParam leftVideoParam;
    //private VideoParam rightVideoParam;
    //private String vodPlayCallBackUrl;
    private String oldMedia;
    //private volatile long startTime = 0;
    //private volatile long endTime = 0;
    //private VodEventDo vodEventDo;
    //private AtomicInteger vodTimes = new AtomicInteger(0);
    //private int videoPayload = 0;
    private volatile boolean isAudioPlayed = false;
    private volatile boolean isSendRecvTried = false;
    private VodEventProcessor vodEventProcessor;
    private int playTid = -1;

    boolean needToForwardAck = true;

    boolean hasInitVodMedias = false;

    private volatile ConcurrentHashMap<String, TransactionHolder> leftTranHolderMap = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, TransactionHolder> rightTranHolderMap = new ConcurrentHashMap<>();

    ArrayList<String> vodMedias = new ArrayList<>();

    public VodInfo getVodInfo() {
        return vodInfo;
    }

    public void replaceNewVodInfo(VodInfo vodInfo) {
        VodInfo oldVodInfo = this.vodInfo;
        this.vodInfo = vodInfo;
        this.vodInfo.setLeftVideoParam(oldVodInfo.getLeftVideoParam());
        this.vodInfo.setRightVideoParam(oldVodInfo.getRightVideoParam());
        this.vodInfo.setVideoPayload(oldVodInfo.getVideoPayload());
        this.vodInfo.setVodPlayCallBackUrl(oldVodInfo.getVodPlayCallBackUrl());
        this.vodInfo.setVodAudioCode(oldVodInfo.getVodAudioCode());
    }

    public void init(VodEventProcessor vodEventProcessor, VodInfo vodInfo) {
        this.vodEventProcessor = vodEventProcessor;
        this.vodInfo = vodInfo;
        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.INIT, null);
        //vodEventDo = new VodEventDo(String.valueOf(bill.getBillId()),bill.getBindId(),voDInfo.getResId(),voDInfo.getUniCode());
    }

    public PluginEventResultVo accept(PluginEvent event) {
        //DevHelp.setVodSupportMultipleMedia(true); //todo: 能力部联调完后删除
        synchronized (this) {
            if (status == VodConstant.VOD.STATUS.INIT) {
                //status = Constants.VOD.STATUS.PREPARE;
                if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
                    XmodePromExporterService.getInstance().incLightScreenTotal();
                } else {
                    XmodePromExporterService.getInstance().incVodTotal();
                }
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.PREPARE, null);
                logger.info("[{}] VodPlugin accept start. VodInfo:{}", billNew.getId(),vodInfo.toString());
                try {
                    if (event.getRequestEvent() != null) {
                        //如果右腿已经 ACK，说明右腿已经处于接通状态，不需要发送 ACK
                        logger.info("[{}] VodPlugin controllerState rightleg stateIndex:{}", billNew.getId(), controllerState.getCurrentState(rightLeg).getStateIndex());
                        if (controllerState.getCurrentState(rightLeg).getStateIndex() < PickUpState.PickUpStateCode && needToForwardAck) {
                            logger.info("[{}] VodPlugin start: right leg is confirmed already, no need to send ack to right again.", billNew.getId());
                            SIPRequest request = event.getRequestEvent().getRequest();
                            rightLeg.forwardRequest(request, mediaSession, false);
                        }
                    }
                    //vodEventProcessor.setVodPlayCallBackUrl(MediaManager.HTTP_SERVER_BASE_URL + VodConstant.VOD.PLAY_BACK_URL);
                    vodInfo.setVodPlayCallBackUrl(MediaManager.HTTP_SERVER_BASE_URL + VodConstant.VOD.PLAY_BACK_URL);

                    //如果是语音插播，则不需要视频协商，直接播放语音
                    if(vodInfo.getCalledVideoMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY){
                        return activateAudioPlay();
                    }

                    Integer[] payloadArray = SdpHelper.generateDynamicPayload(initialInviteRequest.getRawContent(), 2);
                    if (payloadArray.length >= 2) {
                        vsdp = VodConstant.VOD.YONG_TAO_VIDEO;
                        //vodEventProcessor.setVideoPayload(payloadArray[0]);
                        vodInfo.setVideoPayload(payloadArray[0]);
                        vsdp = vsdp.replaceFirst("VIDEO_PORT", String.valueOf(mediaSession.getVideoport()));
                        vsdp = vsdp.replaceFirst("MEDIA_IP", String.valueOf(mediaSession.getProxyEndpoint().getIp()));
                        //vsdp = vsdp.replaceAll("PAYLOAD",String.valueOf(vodEventProcessor.getVideoPayload()));
                        vsdp = vsdp.replaceAll("PAYLOAD", String.valueOf(vodInfo.getVideoPayload()));
                    } else {
                        logger.error("[{}] VodPlugin accept fail to get dynamic payload.", billNew.getId());
                        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
                        return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
                    }
                    oldMedia = mediaSession.getMedia();
                    int index = oldMedia.indexOf("-octet");
                    if (index > 0) {
                        oldMedia = oldMedia.substring(0, index);
                    }
                    return activateVod();
                } catch (Exception e) {
                    logger.error("[{}] VodPlugin accept exception.", billNew.getId(), e);
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
                    return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
                }
            } else {
                logger.warn("[{}] Vod play is preparing.", billNew.getId());
                return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
            }


        }

    }


    private PluginEventResultVo activateVod() throws Exception {
        if (status == VodConstant.VOD.STATUS.PREPARE) {
            startVodPlayAsynly();
            return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
        } else if (status == VodConstant.VOD.STATUS.ACTIVE) {
            logger.warn("[{}] It is playing video already!", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.VOD_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.VOD_IS_IN_PROGRESS);
        } else {
            logger.warn("[{}] Current status is not suitable to activate vod", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
        }

    }

    private PluginEventResultVo activateAudioPlay(){
        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.START, null);
        notifyMediaPlayAudio();
        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.ACTIVE, null);
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    private void startVodPlayAsynly() {
        int delayMs = 0;
        if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
            delayMs = 100;
        }
        ScheduleService.getInstance().schedule(() -> {
            startVodPlay();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void startVodPlay() {
        //logger.info("startVodPlay voDInfo:{},current status:{}", voDInfo.toString(),this.status);
        if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_RIGHT_BOTH) {
            playVideoToBoth();
        } else if (vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY) {
            playVideoToRightOnly();
        } else if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY) {
            playVideoToLeftOnly();
        } else {
            logger.error("[{}] invalid direction. direction:{}", billNew.getId(), vodInfo.getDirection());
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
        }
    }

    private Hook<SipResponseEvent> getRetryVodUpon491Hook(Leg leg, SIPRequest request, Hook successActionHook) {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] Retry upon 491 in vod activation!", billNew.getId());
            ScheduleService.getInstance().schedule(() -> {
                try {
                    SIPRequest retryReInvite = createRequestByCopy(leg, request);
                    sendReinvite(retryReInvite, leg, successActionHook, getNotifyFailInStartHook(), null);
                } catch (Exception e) {
                    logger.error("[{}] VodPlugin accept exception.", billNew.getId(), e);
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
                }
            }, 500, TimeUnit.MILLISECONDS);
        };
        return responseEventHook;
    }


    /**
     * 向主叫播放视频
     */
    private void playVideoToLeftOnly() {
        try {
            logger.info("[{}] VodPlugin playVideoToLeftOnly start", billNew.getId());
            //给被叫发送不携带SDP的 reinvite
            SIPRequest rightReInvite = rightLeg.createRequest(Request.INVITE);
            //给 Contact 头部加上 video
            rightReInvite.removeHeader(ContactHeader.NAME);
            rightReInvite.setHeader(this.engine.contactHeaderWithVideo);
            //初始阶段，向不播放视频的一侧发送不携带SDP的 reinvite, 由于媒体协商未开始，因此若失败了不需要更新 status
            sendReinvite(rightReInvite, rightLeg, handleFirstInviteRespForSinglePlay(rightLeg), getNotifyFailInStartHook(), getRetryVodUpon491Hook(rightLeg, rightReInvite, handleFirstInviteRespForSinglePlay(rightLeg)));
        } catch (Exception e) {
            logger.error("[{}] play video to left only exception.", billNew.getId(), e);
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
        }
    }

    /**
     * 向被叫播放视频
     */
    private void playVideoToRightOnly() {
        try {
            logger.info("[{}] VodPlugin playVideoToRightOnly start", billNew.getId());
            //给主叫发送不携带SDP的 reinvite
            SIPRequest leftReInvite = leftLeg.createRequest(Request.INVITE);
            //给 Contact 头部加上 video
            leftReInvite.removeHeader(ContactHeader.NAME);
            leftReInvite.setHeader(this.engine.contactHeaderWithVideo);

            //初始阶段，向不播放视频的一侧发送不携带SDP的 reinvite, 由于媒体协商未开始，因此若失败了不需要更新 status
            sendReinvite(leftReInvite, leftLeg, handleFirstInviteRespForSinglePlay(leftLeg), getNotifyFailInStartHook(), getRetryVodUpon491Hook(leftLeg, leftReInvite, handleFirstInviteRespForSinglePlay(leftLeg)));
        } catch (Exception e) {
            logger.error("[{}] play video to right only exception.", billNew.getId(), e);
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
        }

    }

    /**
     * 向主被叫播放视频
     */
    private void playVideoToBoth() {
        boolean isSdpProcessSucess = false;
        try {
            logger.info("[{}] VodPlugin playVideoToBoth start", billNew.getId());
            //给主叫发送携带 video SDP 的 reinvite
            SIPRequest leftReInvite = leftLeg.createRequest(Request.INVITE);
            //给 Contact 头部加上 video
            leftReInvite.removeHeader(ContactHeader.NAME);
            leftReInvite.setHeader(this.engine.contactHeaderWithVideo);
            if (vodInfo.getLastRightResponse() != null) {
                Header accessNetworkInfo = vodInfo.getLastRightResponse().getHeader("P-Access-Network-Info");
                if (accessNetworkInfo != null) {
                    leftReInvite.setHeader(accessNetworkInfo);
                }
            }
            if (vodInfo.getLastRightResponse() != null) {
                Header chargeVertor = vodInfo.getLastRightResponse().getHeader("P-Charging-Vector");
                if (chargeVertor != null) {
                    leftReInvite.setHeader(chargeVertor);
                }
            }

            //start: 如果需要全程收号，则选择一个合适的 media code: 优先找到一个跟 telephone-event clock 相同的 media code
            checkAndUpdateVodAudioCode(initialInviteRequest.getRawContent());
            if(StringUtils.isBlank(vodInfo.getVodAudioCode())){
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, null);
                return;
            }
            //end: 如果需要全程收号，则选择一个合适的 media code: 优先找到一个跟 telephone-event clock 相同的 media code

            String offerWithoutVideo = mediaSession.filterSDPWithSpecifiedCode(initialInviteRequest.getRawContent(), vodInfo.getVodAudioCode(), null, MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
            isSdpProcessSucess = true;
            String offerWithVideo = offerWithoutVideo + vsdp;
            offerWithVideo = SdpHelper.replaceBLineBeforeMedia(offerWithVideo);
            leftReInvite.setContent(offerWithVideo.getBytes(), engine.contentTypeHeader);
            //初始阶段，向主叫侧发送携带SDP(audio+video)的 reinvite, 由于媒体协商未成功，因此若失败了不需要更新 status
            sendReinvite(leftReInvite, leftLeg, handleFirstInviteRespForPlayBoth(leftLeg), getNon2XXResponseHandler(true), getRetryVodUpon491Hook(leftLeg, leftReInvite, handleFirstInviteRespForPlayBoth(leftLeg)));
        } catch (Exception e) {
            logger.error("[{}] play video to both exception.", billNew.getId(), e);
            if(!isSdpProcessSucess){
                if(vodMedias.size()>0){
                    updateAudioCode(vodMedias.remove(0));
                    playVideoToBoth();
                }else{
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
                }
            }else{
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
            }
        }
    }

    private void sendAckUponResponse(Leg respfromLeg, SipResponseEvent responseEvent, byte[] rawContent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        if (rawContent == null || rawContent.length <= 0) {
            logger.info("[{}] sendAckUponResponse rawContent is empty", billNew.getId());
            respfromLeg.sendAck(response);
            return;
        }
        SIPRequest ackRequest = respfromLeg.createAck(response);
        String ackSdp = mediaSession.filterSDPWithSpecifiedCode(rawContent, oldMedia, null, MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
        if (StringUtils.isNotBlank(ackSdp)) {
            ackRequest.setContent(ackSdp, engine.contentTypeHeader);
        }
        respfromLeg.sendRequest(ackRequest);
    }

    private void checkAndUpdateVodAudioCode(byte[] rawContent){
        if(hasInitVodMedias){
            return;
        }
        logger.info("[{}] checkAndUpdateVodAudioCode isNeedPickUpDtmfEvent:{}", billNew.getId(),callParameter.isNeedPickUpDtmfEvent());
        if(callParameter.isNeedPickUpDtmfEvent()){
            Map<String, Object> resultMap = SdpHelper.getVodMedias(rawContent,DevHelp.getSupportedCodec());
            logger.info("[{}] checkAndUpdateVodAudioCode include dtmf,resultMap:{}", billNew.getId(),resultMap);
            int code = (int) resultMap.get("code");
            if(code == 0 || code == -2){
                vodMedias = (ArrayList<String>) resultMap.get("media");
                logger.info("[{}] checkAndUpdateVodAudioCode vodMedias:{}", billNew.getId(),vodMedias);
                if(vodMedias.size() > 0){
                    updateAudioCode(vodMedias.remove(0));
                }
            }
        }else{
            if(StringUtils.isNotBlank(vodInfo.getVodAudioCode())){
                vodMedias.add(vodInfo.getVodAudioCode());
            }else{
                vodMedias.add("amr-wb");
            }
            if(!vodMedias.contains(oldMedia)){
                vodMedias.add(oldMedia);
            }
            logger.info("[{}] checkAndUpdateVodAudioCode no dtmf, vodMedias:{}", billNew.getId(),vodMedias);
            updateAudioCode(vodMedias.remove(0));
        }
        hasInitVodMedias = true;
        logger.info("[{}] checkAndUpdateVodAudioCode vod audio:{}", billNew.getId(),vodInfo.getVodAudioCode());

    }


    private void sendAckUponResponse(Leg respfromLeg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        byte[] rawContent = response.getRawContent();
        SIPRequest ackRequest = respfromLeg.createAck(response);
        try {
            String ackSdp = mediaSession.filterSDPWithSpecifiedCode(rawContent, oldMedia, null, MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
            if (StringUtils.isNotBlank(ackSdp)) {
                ackRequest.setContent(ackSdp, engine.contentTypeHeader);
            }
        } catch (Exception e) {
            logger.error("[{}] sendAckUponResponse fail exception.", billNew.getId(), e);
        }
        respfromLeg.sendRequest(ackRequest);
    }


    /**
     * 单向视频插播：收到不插播一侧带offer(audio) 的 response 之后向插播的一侧发送 offer （audio+video)以拉起视频
     *
     * @param respfromLeg 收到此 response 的腿
     * @return
     */
    private Hook<SipResponseEvent> handleFirstInviteRespForSinglePlay(Leg respfromLeg) {
        Hook<SipResponseEvent> responseHook = responseEvent -> {
            logger.info("[{}] *** handleFirstInviteRespForSinglePlay handle responseEvent start *** ", billNew.getId());
            SIPResponse response = responseEvent.getResponse();
            byte[] rawContent = response.getRawContent();
            if (rawContent == null || rawContent.length <= 0) {
                respfromLeg.sendAck(response);
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_START, responseEvent);
                logger.info("[{}] handleFirstInviteRespForSinglePlay rawContent is empty", billNew.getId());
                return;
            }

            Hook<SipResponseEvent> inviteToVodRespFailHook = respEvent -> {
                /**
                 * 发给插播的一侧返回非200的 response，即插播协商失败，需给发空reinvite的一侧（即不插播的一侧）回ACK
                 * 这里回ACK因为是带了answer的，需将 media 回滚到原先语音通话的 media
                 */
                logger.warn("[{}] Single play！Second invite get non-2xx response.statusCode:{}", billNew.getId(), respEvent.getResponse().getStatusCode());
                SIPRequest ackRequest = respfromLeg.createAck(response);
                try {
                    String ackSdp = mediaSession.filterSDPWithSpecifiedCode(rawContent, oldMedia, null, MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    if (StringUtils.isNotBlank(ackSdp)) {
                        ackRequest.setContent(ackSdp, engine.contentTypeHeader);
                    }
                } catch (Exception e) {
                    logger.error("[{}] handleFirstInviteRespForSinglePlay fail exception.", billNew.getId(), e);
                }
                respfromLeg.sendRequest(ackRequest);
                int statusCode = respEvent.getResponse().getStatusCode();
                if (statusCode == 488 && vodMedias.size()>0) {
                    //488 Not Acceptable Here
                    updateAudioCode(vodMedias.remove(0));
                    startVodPlay();
                } else {
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, respEvent);
                }
            };
            boolean isFirstRespProcessSucc = false;
            try {
                Leg oppositeLeg = getOppositeLeg(respfromLeg);
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.START, null);

                //start: 选择一个合适的 media code: 优先找到一个跟 telephone-event clock 相同的 media code
                checkAndUpdateVodAudioCode(rawContent);
                if(StringUtils.isBlank(vodInfo.getVodAudioCode())){
                    sendAckUponResponse(respfromLeg, responseEvent);
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, null);
                    return;
                }
                //end: 选择一个合适的 media code: 优先找到一个跟 telephone-event clock 相同的 media code

                //向视频插播的一侧发送携带 audio+video offer 的 reinvite
                logger.info("[{}] before filterSDPWithSpecifiedCode vod audio code:{}", billNew.getId(),vodInfo.getVodAudioCode());
                String offerWithoutVideo = mediaSession.filterSDPWithSpecifiedCode(rawContent, vodInfo.getVodAudioCode(), respfromLeg.isLeft() ? "left" : "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);

                if(StringUtils.isBlank(mediaSession.getMediaTemp())){
                    //优选amr-wb没有成功，则改成之前的编码
                    logger.info("[{}] media temp is empty !!!!!!", billNew.getId());
                    String nextAudioCode = "";
                    if(vodMedias.size()>0){
                        nextAudioCode = vodMedias.remove(0);
                    }
                    if(StringUtils.isNotBlank(nextAudioCode)){
                        updateAudioCode(nextAudioCode);
                        offerWithoutVideo = mediaSession.filterSDPWithSpecifiedCode(rawContent, vodInfo.getVodAudioCode(), respfromLeg.isLeft() ? "left" : "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    }
                }

                isFirstRespProcessSucc = true;
                String offerWithVideo = offerWithoutVideo + vsdp;
                offerWithVideo = SdpHelper.replaceBLineBeforeMedia(offerWithVideo);

                SIPRequest oppositeReInvite = oppositeLeg.createRequest(Request.INVITE);
                //给 Contact 头部加上 video
                oppositeReInvite.removeHeader(ContactHeader.NAME);
                oppositeReInvite.setHeader(this.engine.contactHeaderWithVideo);

                Header accessNetworkInfo = response.getHeader("P-Access-Network-Info");
                Header chargeVertor = response.getHeader("P-Charging-Vector");
                if (accessNetworkInfo != null) {
                    oppositeReInvite.setHeader(accessNetworkInfo);
                }
                if (chargeVertor != null) {
                    oppositeReInvite.setHeader(chargeVertor);
                }

                oppositeReInvite.setContent(offerWithVideo.getBytes(), engine.contentTypeHeader);
                sendReinvite(oppositeReInvite, oppositeLeg, handleSecondInviteRespForSinglePlay(oppositeLeg, response), inviteToVodRespFailHook, resp -> {
                    logger.info("[{}] Receive 491 for invite to right. Will try again!", billNew.getId());
                    //oppositeLeg.sendAck();
                    ScheduleService.getInstance().schedule(() -> {
                        try {
                            SIPRequest retryReInvite = createRequestByCopy(rightLeg, oppositeReInvite);
                            sendReinvite(retryReInvite, oppositeLeg, handleSecondInviteRespForSinglePlay(oppositeLeg, response), inviteToVodRespFailHook, null);
                        } catch (Exception e) {
                            logger.error("[{}] handleFirstInviteRespForSinglePlay exception.", billNew.getId(), e);
                            try {
                                inviteToVodRespFailHook.apply(responseEvent);
                            } catch (Exception exception) {
                                logger.error("[{}] handleFirstInviteRespForSinglePlay send ack to non-vod side exception.", billNew.getId(), e);
                            }
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                });
            } catch (Exception e) {
                logger.error("[{}] isFirstRespProcessSucc:{}, handleFirstInviteRespForSinglePlay exception.", billNew.getId(), isFirstRespProcessSucc, e);
                if(!isFirstRespProcessSucc){
                    if(vodMedias.size()>0){
                        //respfromLeg.sendAck(response);
                        sendAckUponResponse(respfromLeg, responseEvent);
                        updateAudioCode(vodMedias.remove(0));
                        startVodPlay();
                    }else{
                        inviteToVodRespFailHook.apply(responseEvent);
                    }
                }else{
                    inviteToVodRespFailHook.apply(responseEvent);
                }

                /*if (!isFirstRespProcessSucc && !vodInfo.getVodAudioCode().equalsIgnoreCase(oldMedia) && DevHelp.isVodSupportMultipleMedia()) {
                    respfromLeg.sendAck(response);
                    updateAudioCode(oldMedia);
                    startVodPlay();
                } else {
                    inviteToVodRespFailHook.apply(responseEvent);
                }*/

            }

            return;
        };
        return responseHook;

    }

    /**
     * 单向视频插播：
     * 收到一条腿 video 重协商的 response 之后，给另一条腿返回携带 answer 的 ACK response
     * 若本次插播只向被叫播放，则将 response sdp 的 video 删减作为 answer 发给主叫；
     * 若本次插播只向主叫播放，则将 response sdp 的 video 删减作为 answer 发给被叫；
     *
     * @param legOfCurrentResp 返回当前 response 的腿
     * @param previousResponse 上一次 response，理论上应该来自另一条腿
     * @return
     */
    private Hook<SipResponseEvent> handleSecondInviteRespForSinglePlay(Leg legOfCurrentResp, SIPResponse previousResponse) {
        Hook<SipResponseEvent> reInviteToLeftSuccessAction = responseEvent -> {
            legOfCurrentResp.sendAck();
            SIPResponse response = responseEvent.getResponse();
            //lastSdpFromNonVideo = previousResponse.getRawContent();
            byte[] rawContent = response.getRawContent();
            if (rawContent == null || rawContent.length <= 0) {
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
                return;
            }

            Leg oppositeLeg = getOppositeLeg(legOfCurrentResp);
            boolean isFirstInviteAck = false;

            try {
                int remoteVideoPort = 0; //记录 remote 的 video port 是否为0，若为0，表示关闭视频
                if (legOfCurrentResp.isLeft()) {
                    vodInfo.setLeftVideoParam(SdpHelper.getRemoteVideoInfo(rawContent));
                    remoteVideoPort = vodInfo.getLeftVideoParam().getVideoPort();
                } else {
                    vodInfo.setRightVideoParam(SdpHelper.getRemoteVideoInfo(rawContent));
                    remoteVideoPort = vodInfo.getRightVideoParam().getVideoPort();
                }
                String sdpFromCurrentResp = "";
                //单向向主叫插播视频
                if (legOfCurrentResp.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY) {
                    //如果只向主叫播放视频，在这里先将发给被叫的 answer sdp 去掉 video
                    sdpFromCurrentResp = mediaSession.filterSDPWithSpecifiedCode(rawContent, vodInfo.getVodAudioCode(), "left", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    //lastSdpFromLeft = sdpFromCurrentResp.getBytes();
                }

                //单向向被叫插播视频
                if (!legOfCurrentResp.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY) {
                    //如果只向被叫播放视频，在这里先将发给主叫的 answer sdp 去掉 video
                    sdpFromCurrentResp = mediaSession.filterSDPWithSpecifiedCode(rawContent, vodInfo.getVodAudioCode(), "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    //lastSdpFromRight = sdpFromCurrentResp.getBytes();
                }
                if (StringUtils.isNotBlank(sdpFromCurrentResp)) {
                    SIPRequest ackRequest = oppositeLeg.createAck(previousResponse);
                    //如果不播放视频的一端提供的 offer 中带了 video 行，则应在 answer 中也带上 video 行，只是 video 端口为0，表示关闭
                    String newContent = sdpFromCurrentResp + SdpHelper.getCloseVideoLine(previousResponse.getRawContent()) + "\r\n";
                    ackRequest.setContent(newContent.getBytes(), engine.contentTypeHeader);
                    oppositeLeg.sendRequest(ackRequest);
                    isFirstInviteAck = true;
                    //mediaSession.commit();
                    vodEventProcessor.commit();
                    if (remoteVideoPort > 0) {
                        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.ACTIVE, responseEvent);
                        notifyMediaPlayVideo();
                        oppositeLeg.controller.resetLastUpdateTime();
                    } else {
                        logger.info("[{}] remote reject video,port is 0", billNew.getId());
                        if (!isSendRecvTried &&
                                (vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.SENDRECV_AND_FALLBACK_AUDIO_CALL
                                        || vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.SENDRECV_AND_FALLBACK_PLAY_AUDIO)) {
                            logger.info("[{}] video play plugin remote reject video,port is 0,will try sendrecv!", billNew.getId());
                            isSendRecvTried = true;
                            vsdp = vsdp.replaceFirst("a=sendonly", "a=sendrecv");
                            startVodPlay();
                        } else {
                            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT, responseEvent);
                        }


                    }
                } else {
                    logger.info("[{}] SDP of answer from vod terminal is blank, will send black ack to non-vod terminal.", billNew.getId());
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
                    //插播一侧返回空SDP，也需要给不插播的一侧回ACK，这里暂时设置成回空ACK
                    SIPRequest ackRequest = oppositeLeg.createAck(previousResponse);
                    oppositeLeg.sendRequest(ackRequest);
                    isFirstInviteAck = true;
                }
            } catch (Exception e) {
                logger.error("[{}] handleSecondInviteRespForSinglePlay exception.", billNew.getId(), e);
                if (!isFirstInviteAck) {
                    SIPRequest ackRequest = oppositeLeg.createAck(previousResponse);
                    oppositeLeg.sendRequest(ackRequest);
                }
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
            }
        };
        return reInviteToLeftSuccessAction;
    }

    /**
     * 两侧都播放视频的模式中，先向主叫发送携带 offer(audio+video)的 reinvite,收到 answer 后向被叫发送 携带 offer(audio+video)的 reinvite
     *
     * @param fromLeg
     * @return
     */
    private Hook<SipResponseEvent> handleFirstInviteRespForPlayBoth(Leg fromLeg) {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            //向左腿发 ACK
            fromLeg.sendAck();

            SIPResponse responseFromLeft = responseEvent.getResponse();
            byte[] rawContent = responseFromLeft.getRawContent();
            if (rawContent == null || rawContent.length <= 0) {
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_START, responseEvent);
                return;
            }

            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.START, responseEvent);

            try {
                vodInfo.setLeftVideoParam(SdpHelper.getRemoteVideoInfo(responseFromLeft.getRawContent()));

                int leftVideoPort = vodInfo.getLeftVideoParam().getVideoPort();
                if (leftVideoPort <= 0) {
                    //视频端口不大于0，说明视频协商失败，不继续向右腿发 offer(audio+video)
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT, responseEvent);
                    return;
                }

                //向右腿发送携带 audio+video offer 的 reinvite
                Leg rightLeg = getOppositeLeg(fromLeg);
                SIPRequest reInviteToRight = rightLeg.createRequest(Request.INVITE);

                //给 Contact 头部加上 video
                reInviteToRight.removeHeader(ContactHeader.NAME);
                reInviteToRight.setHeader(this.engine.contactHeaderWithVideo);

                //加上 P-Access-Network-Info 头部
                Header accessNetworkInfo = responseFromLeft.getHeader("P-Access-Network-Info");
                if (accessNetworkInfo != null) {
                    reInviteToRight.setHeader(accessNetworkInfo);
                }

                //加上 P-Charging-Vector 头部
                Header chargeVertor = responseFromLeft.getHeader("P-Charging-Vector");
                if (chargeVertor != null) {
                    reInviteToRight.setHeader(chargeVertor);
                }

                //向被叫发送 offer (audio+video)
                String firstOfferToRight = mediaSession.filterSDPWithSpecifiedCode(rawContent, vodInfo.getVodAudioCode(), fromLeg.isLeft() ? "left" : "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                firstOfferToRight = firstOfferToRight + vsdp;
                reInviteToRight.setContent(firstOfferToRight.getBytes(), engine.contentTypeHeader);
                sendReinvite(reInviteToRight, rightLeg, handleSecondInviteRespForPlayBoth(), getNon2XXResponseHandler(false), resp -> {
                    SipResponseEvent respEvent = (SipResponseEvent) resp;
                    logger.info("[{}] Get 491 response when send reinvite to vod side.Will retry.", billNew.getId());
                    ScheduleService.getInstance().schedule(() -> {
                        try {
                            SIPRequest retryReInvite = createRequestByCopy(rightLeg, reInviteToRight);
                            sendReinvite(retryReInvite, rightLeg, handleSecondInviteRespForPlayBoth(), getNotifyFailInProgressHook(), null);
                        } catch (Exception e) {
                            logger.error("[{}] handleFirstInviteRespForPlayBoth exception.", billNew.getId(), e);
                            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, respEvent);
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                });
            } catch (Exception e) {
                logger.error("[{}] Negotiation exception for vod play to left and right!", billNew.getId(), e);
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
            }


        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> handleSecondInviteRespForPlayBoth() {
        Hook<SipResponseEvent> respFromRightSucceedAction = rightInviteResponseEvent -> {
            rightLeg.sendAck();
            try {
                SIPResponse rightResponse = rightInviteResponseEvent.getResponse();
                vodInfo.setRightVideoParam(SdpHelper.getRemoteVideoInfo(rightResponse.getRawContent()));
                mediaSession.filterSDPWithSpecifiedCode(rightResponse.getRawContent(), vodInfo.getVodAudioCode(), "right", MediaSession.VIDEO_MODE.THROUGH_XMEDIA);
                //mediaSession.commit();
                vodEventProcessor.commit();
                if (vodInfo.getRightVideoParam().getVideoPort() > 0) {
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.ACTIVE, rightInviteResponseEvent);
                    notifyMediaPlayVideo();
                    rightLeg.controller.resetLastUpdateTime();
                } else {
                    //视频端口不大于0，说明视频协商失败
                    if (!isSendRecvTried && (vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.SENDRECV_AND_FALLBACK_AUDIO_CALL
                            || vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.SENDRECV_AND_FALLBACK_PLAY_AUDIO)) {
                        logger.info("[{}] video play plugin remote reject video,port is 0,will try sendrecv!", billNew.getId());
                        isSendRecvTried = true;
                        vsdp = vsdp.replaceFirst("a=sendonly", "a=sendrecv");
                        startVodPlay();
                    } else {
                        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT, rightInviteResponseEvent);
                        return;
                    }


                }
            } catch (Exception e) {
                logger.error("[{}] Negotiation exception in right for vod play to left and right!", billNew.getId(), e);
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, rightInviteResponseEvent);
            }

        };
        return respFromRightSucceedAction;
    }

    /*public final Leg getOppositeLeg(Leg leg) {
        return leg.isLeft() ? this.rightLeg : this.leftLeg;
    }*/

    /**
     * 更新插播状态为失败
     * INIT :初始状态
     * START: 已开始视频的媒体协商
     * ACTIVE: 视频媒体协商完成
     * END: 正常结束状态
     * FAIL_IN_PROGRESS: 失败状态，表示协商过程中失败
     * FAIL_IN_START ： 失败状态，表示协商未正式开始就失败
     */
    private void notifyStatusUpdate(int reasonCode, SipResponseEvent responseEvent) {
        boolean needToProcessVodEnd = false;
        boolean needToStopVideo = false;
        boolean needToPlayAudio = false;
        if (reasonCode == VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION && responseEvent != null) {
            if (responseEvent.getLeg().isLeft()) {
                this.leftNegotiation = VodConstant.VOD.NEGOTIATION.FAIL;
            } else {
                this.rightNegotiation = VodConstant.VOD.NEGOTIATION.FAIL;
            }
        }
        //logger.info("[{}] notifyStatusUpdate old status:{}",bill.getBillId(),this.status);
        synchronized (this) {
            switch (reasonCode) {
                case VodConstant.VOD.REASON_CODE.ACTIVE:
                    this.status = VodConstant.VOD.STATUS.ACTIVE;
                    break;
                case VodConstant.VOD.REASON_CODE.PREPARE:
                    this.status = VodConstant.VOD.STATUS.PREPARE;
                    break;
                case VodConstant.VOD.REASON_CODE.START:
                    this.status = VodConstant.VOD.STATUS.START;
                    break;
                case VodConstant.VOD.REASON_CODE.INIT:
                    this.status = VodConstant.VOD.STATUS.INIT;
                    break;
                case VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE:
                    this.status = VodConstant.VOD.STATUS.SWITCH_TO_AUDIO;
                    needToProcessVodEnd = true;
                    break;
                case VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE:
                    if (this.status != VodConstant.VOD.STATUS.SWITCH_TO_AUDIO) {
                        this.needToRetreate = true;
                    }
                    this.status = VodConstant.VOD.STATUS.END;
                    needToProcessVodEnd = true;
                    break;
                case VodConstant.VOD.REASON_CODE.END_BY_BYE:
                    this.status = VodConstant.VOD.STATUS.END;
                    needToProcessVodEnd = true;
                    this.needToRetreate = false;
                    break;
                case VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND:
                case VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT:
                case VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION:
                case VodConstant.VOD.REASON_CODE.FAIL_IN_START:
                case VodConstant.VOD.REASON_CODE.END_BY_REQUEST:
                case VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION:
                    this.status = VodConstant.VOD.STATUS.END;
                    this.needToRetreate = true;
                    needToProcessVodEnd = true;
                    needToPlayAudio = true;
                    if (reasonCode != VodConstant.VOD.REASON_CODE.END_BY_REQUEST) {
                        if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
                            XmodePromExporterService.getInstance().incLightScreenFailNum();
                        } else {
                            XmodePromExporterService.getInstance().incVodFailNum();
                        }
                    }
                    break;
                /*case VodConstant.VOD.REASON_CODE.END_BY_FORCE:
                    this.status = VodConstant.VOD.STATUS.END;
                    this.needToRetreate = true;
                    needToProcessVodEnd = true;*/
                default:
                    break;

            }

            if ((vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.SENDRECV_AND_FALLBACK_PLAY_AUDIO
                    || vodInfo.getAggressiveVod() == VodConstant.VOD.AGGRESSIVE_VOD.FALLBACK_PLAY_AUDIO)
                    && !isAudioPlayed && needToPlayAudio) {
                isAudioPlayed = true;
                needToPlayAudio = true;
            } else {
                needToPlayAudio = false;
            }
        }

        logger.info("[{}] notifyStatusUpdate new status:{},needToStopVideo:{}, needToProcessVodEnd:{},needToPlayAudio:{}", billNew.getId(), this.status, needToStopVideo, needToProcessVodEnd, needToPlayAudio);

        //兜底播放语音。进入播放语音状态，将状态设置为 active
        if (needToPlayAudio) {
            try {
                this.status = OBVConstant.STATUS.ACTIVE;
                needToRetreate = false;
                notifyMediaPlayAudio();
            } catch (Exception e) {
                logger.error("[{}] video play plugin notifyMediaPlayVideo exception. ", billNew.getId(), e);
            }
        } else if (needToProcessVodEnd) {
            vodEventProcessor.terminateCurrentPlay(reasonCode, vodInfo);
        }

        if (this.status == VodConstant.VOD.STATUS.END && vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
            /*if(!isAudioPlayed){
                XmodePromExporterService.getInstance().decLightScreenVideoPlayingNum();
            }*/
            if (StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.STOP_CALL_AFTER_PLAY)) {
                needToRetreate = false;
                if (reasonCode != VodConstant.VOD.REASON_CODE.END_BY_BYE) {
                    completeCallByBye();
                }
            }
        }
        if(vodInfo.getCalledVideoMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY){
            this.needToRetreate = false;
        }
    }

    private void completeCallByBye() {
        logger.info("[{}] Vod plugin complete call by bye start", billNew.getId());
        try {
            SIPRequest byeRequest = leftLeg.createRequest("BYE");
            preciseCallState.extractRequestMsgPropertiesJudgeReject(true, byeRequest);
            leftLeg.sendByeOr487();
            rightLeg.sendByeOr487();
        } catch (Exception e) {
            logger.error("[{}] VodPlugin completeCallByBye exception", billNew.getId(), e);
        }
    }

    private Hook<SipResponseEvent> getNotifyFailInStartHook() {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] Fail in negotiation start!", billNew.getId());
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_START, responseEvent);
        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> getNon2XXResponseHandler(boolean failInStart) {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            int statusCode = responseEvent.getResponse().getStatusCode();
            if (statusCode/100 >= 4 && vodMedias.size()>0) {
                updateAudioCode(vodMedias.remove(0));
                startVodPlay();
            } else {
                if (failInStart) {
                    logger.error("[{}] Fail in negotiation start!", billNew.getId());
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_START, responseEvent);
                } else {
                    logger.error("[{}] Fail in negotiation process!", billNew.getId());
                    notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
                }

            }
        };
        return responseEventHook;
    }


    private Hook<SipResponseEvent> getNotifyFailInProgressHook() {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] Fail in negotiation process!", billNew.getId());
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> getResponseBaseHook(Hook<SipResponseEvent> actionUponSuccess, Hook<SipResponseEvent> actionUponFail, Hook<SipResponseEvent> actionUpon491) {
        Hook<SipResponseEvent> responseHook = responseEvent -> {
            int statusCode = responseEvent.getResponse().getStatusCode();
            logger.info("[{}] getResponseBaseHook response method:{}, statusCode:{}", billNew.getId(), responseEvent.getMethod(), statusCode);
            if (statusCode == 200) {
                if (actionUponSuccess != null) {
                    actionUponSuccess.apply(responseEvent);
                }
            } else {
                if ((statusCode == 491 || statusCode == 500) && actionUpon491 != null) {
                    actionUpon491.apply(responseEvent);
                } else if (actionUponFail != null) {
                    actionUponFail.apply(responseEvent);
                }
            }
        };
        return responseHook;
    }

    public void sendReinvite(SIPRequest request, Leg leg, Hook actionUponSuccess, Hook actionUponFail, Hook actionUpon491) throws Exception {
        sendRequest(leg, request, getResponseBaseHook(actionUponSuccess, actionUponFail, actionUpon491));

    }

    private void notifyMediaPlayAudio() {
        if (StringUtils.isBlank(vodInfo.getAudioFile())) {
            logger.error("[{}] video play plugin play audio: video file is null or empty!", billNew.getId());
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION, null);
            return;
        }
        int playTid = vodEventProcessor.playAudio(vodInfo);
        vodInfo.setPlayTid(playTid);
        if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
            XmodePromExporterService.getInstance().incLightScreenAudioPlayNum();
        } else {
            XmodePromExporterService.getInstance().incVodAudioPlayNum();
        }

    }

    /**
     * 通知xmedia 播放视频
     */
    private void notifyMediaPlayVideo() {
        /*String audioPlayFile = vodInfo.getAudioFile();
        String completeAudioPlayFile = CommonUtil.addPostfixToAudioFile(vodInfo.getVodAudioCode(),audioPlayFile);
        vodInfo.setAudioFile(completeAudioPlayFile);*/
        int playTid = vodEventProcessor.playVideo(null, vodInfo);
        vodInfo.setPlayTid(playTid);
        if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM) {
            XmodePromExporterService.getInstance().incLightScreenVideoPlayingNum();
            if (isSendRecvTried) {
                XmodePromExporterService.getInstance().incLightScreenSendrecvSuccNum();
            } else {
                XmodePromExporterService.getInstance().incLightScreenSendonlySuccNum();
            }
        } else {
            XmodePromExporterService.getInstance().incVodVideoPlayingNum();
            if (isSendRecvTried) {
                XmodePromExporterService.getInstance().incVodSendrecvSuccNum();
            } else {
                XmodePromExporterService.getInstance().incVodSendonlySuccNum();
            }
        }
    }


    /**
     * xmedia 回调播放视频播放完成
     */
    public PluginEventResultVo vodComplete(Map<String, Object> dataMap) {
        logger.info("[{}] vodComplete xmedia return dataMap:{}", billNew.getId(), dataMap.toString());
        String code = (String) dataMap.getOrDefault("code", "");
        int tid = (int) dataMap.getOrDefault("tid", "");
        /*if(playTid == -1 || tid != playTid){
            logger.info("[{}] vodComplete forward to process vod callback.",bill.getBillId());
            vodEventProcessor.processVodCallback(dataMap);
            return PluginEventResultVo.newDealSuccessVo(VodConstants.VOD.VOD_HTTP_CODE.SUCCESS, VodConstants.VOD.VOD_HTTP_MSG.SUCCESS);
        }*/
        if (code.equals(VodConstant.VOD.VOD_EVENT_CODE.RESOURCE_NOT_FOUND)) {
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND, null);
            logger.warn("[{}] vodComplete resId not found.", billNew.getId());
        } else if (this.status != VodConstant.VOD.STATUS.SWITCH_TO_AUDIO && this.status != VodConstant.VOD.STATUS.END) {
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE, null);
            // logger.info("[{}] vodComplete Vod play duration：{} times，{} seconds,{} minutes", billNew.getId(), billNew.getVideoInsertTimes(), billNew.getVideoInsertDurationS(), billNew.getVideoInsertDurationM());
        }
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    /*public PluginEventResultVo terminatePlugin(){
        notifyStatusUpdate(VodConstant.VOD.REASON_CODE.END_BY_FORCE, null);
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }*/


    /**
     * App 用户触发停止插播
     */
    public PluginEventResultVo completeStopVod(Map<String, Object> paramMap) {
        String uniCode = (String) paramMap.getOrDefault("uniCode", "");
        if (!StringUtils.equals(uniCode, vodInfo.getUniCode())) {
            logger.warn("[{}] stopVodPlay stop uniCode is different from play's uniCode", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.PARAM_INVALID, VodConstant.VOD.VOD_HTTP_MSG.PARAM_INVALID);
        }
        return processVodStop(VodConstant.VOD.REASON_CODE.END_BY_REQUEST);
    }


    private PluginEventResultVo processVodStop(int reasonCode) {
        logger.info("[{}] User trigger request to stop video play!", billNew.getId());
        if (this.status == VodConstant.VOD.STATUS.ACTIVE) {
            if (reasonCode != VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE) {
                if (leftTranHolderMap.size() > 0 || rightTranHolderMap.size() > 0) {
                    logger.error("[{}] Cannot stop video play currently. current status is:{}", billNew.getId(), this.status);
                    return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
                }
            }
            notifyStatusUpdate(reasonCode, null);
            // logger.info("[{}] processVodStop Vod play duration：{} times，{} seconds,{} minutes", billNew.getId(), billNew.getVideoInsertTimes(), billNew.getVideoInsertDurationS(), billNew.getVideoInsertDurationM());
            return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
        }
        return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NO_VOD_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NO_VOD_IS_IN_PROGRESS);
    }

    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();
        String method = responseEvent.getMethod();
        logger.info("[{}] processResponse VodPlugin Vod status:{}, receive response:{}", billNew.getId(), status, method);
        Leg oppositeLeg = getOppositeLeg(leg);
        if ((status == VodConstant.VOD.STATUS.ACTIVE || status == VodConstant.VOD.STATUS.SWITCH_TO_AUDIO)
                && statusCode == 200 && (method.equalsIgnoreCase(SIPRequest.INVITE) || method.equalsIgnoreCase(SIPRequest.UPDATE))) {
            // 已完成视频协商，视频插播进行中
            byte[] respRawContent = response.getRawContent();
            if (respRawContent != null && respRawContent.length > 0) {
                String newContent = "";
                if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY ||
                        vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY) {
                    if ((leg.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY) ||
                            (!leg.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY)) {
                        //单向插播，响应来自于插播的腿: 因另一条腿没有视频，应将SDP中video删减
                        newContent = SdpHelper.trimVideoLines(respRawContent);
                    } else {
                        //单向插播，响应来自不插播的一条腿: 因另一条腿有视频，应在SDP中添加video
                        if (status == VodConstant.VOD.STATUS.SWITCH_TO_AUDIO) {
                            newContent = buildCloseVideoSdp(new String(respRawContent));
                        } else {
                            //newContent = new String(respRawContent) + vsdp;
                            newContent = SdpHelper.trimVideoLines(respRawContent) + vsdp;
                        }
                    }
                    response.setContent(newContent, engine.contentTypeHeader);
                } else {
                    //两边都支持视频
                    if (!SdpHelper.isRemoteVideoClose(respRawContent)) {
                        newContent = SdpHelper.trimVideoLines(respRawContent) + vsdp;
                        response.setContent(newContent, engine.contentTypeHeader);
                    } else {
                        processVodStop(VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE);
                    }
                }
            }
        }
        oppositeLeg.forwardResponse(responseEvent, mediaSession, null, false);
        checkAndClearTransactionHolder(oppositeLeg, response);
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    private String buildCloseVideoSdp(String sdp) {
        return sdp + "m=video 0 RTP/AVP " + vodInfo.getVideoPayload() + "\n";
    }

    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        Leg oppositeLeg = getOppositeLeg(leg);
        String transactionId = request.getTransactionId();
        logger.info("[{}] VodPlugin processRequest Vod status:{}, receive request:{}", billNew.getId(), status, method);

        if (method.equalsIgnoreCase(SIPRequest.BYE)) {
            //如果是bye请求，就先停止插播并更新状态，然后返回给上一层处理
            //if(this.status == Constants.VOD.STATUS.ACTIVE){
            notifyStatusUpdate(VodConstant.VOD.REASON_CODE.END_BY_BYE, null);
            //}
            return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
        }

        if ((status == VodConstant.VOD.STATUS.START || status == VodConstant.VOD.REASON_CODE.PREPARE) &&
                !Request.ACK.equalsIgnoreCase(method)) {
            // 正在视频插播协商
            logger.info("[{}] is processing vod negotiation, will return 500", billNew.getId());
            SIPResponse response491 = leg.createResponse(500, request);
            leg.sendResponse(request, response491);
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
        } else if ((status == VodConstant.VOD.STATUS.ACTIVE || status == VodConstant.VOD.STATUS.SWITCH_TO_AUDIO)
                && (method.equalsIgnoreCase(SIPRequest.INVITE) || method.equalsIgnoreCase(SIPRequest.UPDATE))) {
            // 已完成视频协商，视频插播进行中
            byte[] reqRawContent = request.getRawContent();
            if (reqRawContent != null && reqRawContent.length > 0) {
                String newContent = "";
                if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY ||
                        vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY) {
                    VideoParam videoParam = SdpHelper.getRemoteVideoInfo(reqRawContent);
                    if ((leg.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY) ||
                            (!leg.isLeft() && vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY)) {
                        //单向插播，请求来自于插播的腿: 因另一条腿没有视频，应将SDP中video删减
                        newContent = SdpHelper.trimVideoLines(reqRawContent);
                        if (videoParam.getVideoPort() <= 0) {
                            processVodStop(VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE);
                        }
                    } else {
                        //单向插播，请求来自不插播的一条腿: 因另一条腿有视频，应在SDP中添加video
                        //VideoParam videoParam = SdpHelper.getRemoteVideoInfo(reqRawContent);
                        if (videoParam.getVideoPort() <= 0) {
                            if (status == VodConstant.VOD.STATUS.SWITCH_TO_AUDIO) {
                                newContent = buildCloseVideoSdp(new String(reqRawContent));
                            } else {
                                newContent = new String(reqRawContent) + vsdp;
                            }
                        } else {
                            //如果不插播的一条腿拉起视频通话请求，则返回406 Not Acceptable
                            logger.warn("[{}] Terminal try to trigger video call, AS return 406", billNew.getId());
                            SIPResponse response406 = leg.createResponse(406, request);
                            leg.sendResponse(request, response406);
                            return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
                        }

                    }
                    request.setContent(newContent, engine.contentTypeHeader);
                } else {
                    //两边都支持视频
                    if (!SdpHelper.isRemoteVideoClose(reqRawContent)) {
                        newContent = SdpHelper.trimVideoLines(reqRawContent) + vsdp;
                        request.setContent(newContent, engine.contentTypeHeader);
                    }
                }
            }
        }
        oppositeLeg.forwardRequest(request, mediaSession, null, false);

        //检查并清理 transaction holder
        if (Request.ACK.equalsIgnoreCase(method)) {
            checkAndClearTransactionHolder(leg, request);
            if (leftTranHolderMap.size() <= 0 && rightTranHolderMap.size() <= 0 && status == VodConstant.VOD.STATUS.SWITCH_TO_AUDIO) {
                notifyStatusUpdate(VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE, null);
            }
        } else {
            TransactionHolder newTranHolder = TransactionHolderFactory.createTransactionHolder(method, request, billNew);
            if (newTranHolder != null) {
                getTransHolderMap(leg).put(transactionId, newTranHolder);
            }
        }

        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    private void checkAndClearTransactionHolder(Leg leg, SIPMessage message) throws Exception {
        if (message instanceof SIPResponse) {
            SIPResponse response = (SIPResponse) message;
            String transactionId = response.getTransactionId();
            ConcurrentHashMap<String, TransactionHolder> tranHolderMap = getTransHolderMap(leg);
            TransactionHolder transactionHolder = tranHolderMap.get(transactionId);
            if (transactionHolder != null) {
                transactionHolder.switchStatus(response);
                if (transactionHolder.isCompleted()) {
                    tranHolderMap.remove(transactionId);
                    logger.info("[{}] message remove. method:{},CSeq:{}", billNew.getId(), response.getCSeq().getMethod(), response.getCSeq().getSeqNumber());
                }
            }
        } else {
            SIPRequest request = (SIPRequest) message;
            if (Request.ACK.equalsIgnoreCase(request.getMethod())) {
                ConcurrentHashMap<String, TransactionHolder> tranHolderMap = getTransHolderMap(leg);
                Iterator<Map.Entry<String, TransactionHolder>> iterator = tranHolderMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, TransactionHolder> map = iterator.next();
                    TransactionHolder transactionHolder = map.getValue();
                    if (Request.INVITE.equalsIgnoreCase(transactionHolder.getName())) {
                        transactionHolder.switchStatus(request);
                        if (transactionHolder.isCompleted()) {
                            tranHolderMap.remove(map.getKey());
                            logger.info("[{}] request message is remove. method:{},CSeq:{}", billNew.getId(), request.getMethod(), request.getCSeq().getSeqNumber());
                            break;
                        }
                    }
                }
            }
        }
    }

    private ConcurrentHashMap getTransHolderMap(Leg leg) {
        ConcurrentHashMap<String, TransactionHolder> tranHolderMap = null;
        if (leg.isLeft()) {
            tranHolderMap = leftTranHolderMap;
        } else {
            tranHolderMap = rightTranHolderMap;
        }
        return tranHolderMap;
    }


    public boolean isNeedVodRetreate() {
        return needToRetreate;
        /*if(status == Constants.VOD.STATUS.INIT || status == Constants.VOD.STATUS.END_BY_BYE){
            return false;
        }else{
            return true;
        }*/
    }

    @Override
    public boolean isComplete() {
        if (this.status == VodConstant.VOD.STATUS.END) {
            mediaSession.clearVideoData();
            logger.info("[{}] vod is completed!!", billNew.getId());
            return true;
        } else {
            logger.info("[{}] vod is not completed yet!", billNew.getId());
            return false;
        }
    }

    public int getLeftNegotiation() {
        return leftNegotiation;
    }

    public int getRightNegotiation() {
        return rightNegotiation;
    }


    public static void main(String[] args) {
        String sdp = "v=0\n" +
                "o=- 1666681639 1666681643 IN IP4 10.188.179.100\n" +
                "s=SBC call\n" +
                "c=IN IP4 10.188.28.107\n" +
                "t=0 0\n" +
                "m=audio 58548 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0\n" +
                "b=AS:82\n" +
                "b=RS:612\n" +
                "b=RR:1837\n" +
                "a=rtpmap:104 AMR-WB/16000\n" +
                "a=fmtp:104 max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:110 AMR-WB/16000\n" +
                "a=fmtp:110 octet-align=1;max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:102 AMR/8000\n" +
                "a=fmtp:102 max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:108 AMR/8000\n" +
                "a=fmtp:108 octet-align=1;max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:105 telephone-event/16000\n" +
                "a=fmtp:105 0-15\n" +
                "a=rtpmap:100 telephone-event/8000\n" +
                "a=fmtp:100 0-15\n" +
                "a=ptime:20\n" +
                "a=maxptime:20\n" +
                "a=sendrecv\n" +
                "a=rtpmap:111 EVS/16000\n" +
                "a=fmtp:111 br=9.6-64;bw=swb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:106 EVS/16000\n" +
                "a=fmtp:106 br=5.9-64;bw=wb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:18 G729/8000\n" +
                "a=fmtp:18 annexb=no\n" +
                "a=rtpmap:8 PCMA/8000\n" +
                "a=rtpmap:0 PCMU/8000\n";

        String sdp1 = "abce\nseseqw";
        VodPlugin voDPlugin = new VodPlugin();
        String resultSdp = voDPlugin.createInvalidSdp(sdp.getBytes());
        System.out.println("******************************************");
        System.out.println(resultSdp);
    }

    private String createInvalidSdp(byte[] rawContent) {
        try {
            if (rawContent != null) {
                InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
                BufferedReader br = new BufferedReader(bufferedReader);
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    System.out.println(line);
                    if (line.startsWith("m=audio")) {
                        line = "m=audio " + "9908" + " RTP/AVP 12";
                    } else if (line.startsWith("a=rtpmap") || line.startsWith("a=fmtp")) {
                        line = br.readLine();
                        continue;
                    }
                    sb.append(line).append("\r\n");
                    line = br.readLine();
                }
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public boolean isNeedToForwardAck() {
        return needToForwardAck;
    }

    public void setNeedToForwardAck(boolean needToForwardAck) {
        this.needToForwardAck = needToForwardAck;
    }

    private void updateAudioCode(String newAudioCode) {
        synchronized (this) {
            vodInfo.setVodAudioCode(newAudioCode);
            logger.info("[{}] update audio code as {},vodMedias:{}", billNew.getId(), newAudioCode,vodMedias);
        }
    }

}
