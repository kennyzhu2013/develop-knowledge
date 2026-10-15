package com.cmic.bizcontroller.controllerImpl.videooutbound;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.AbstractPlugin;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEventResultVo;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.VodEventDo;
import com.cmic.bizcontroller.controllerImpl.vod.transactionholder.TransactionHolder;
import com.cmic.bizcontroller.controllerImpl.vod.transactionholder.TransactionHolderFactory;
import com.cmic.pojo.VideoParam;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.recording.params.VomPlayParam;
import com.cmic.service.ScheduleService;
import com.cmic.service.XmodePromExporterService;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.Leg;
import com.cmic.constant.CommonConstant;
import com.cmic.constant.OBVConstant;
import com.cmic.util.SdpHelper;
import com.cmic.constant.VodConstant;
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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 插播视频插件
 * Author: chenxiuhong
 * Date: 2022/10/19
 */
public class ObvPlayPlugin extends AbstractPlugin {

    private volatile int status = OBVConstant.STATUS.NONE;
    private volatile boolean needToRetreate = false;
    private volatile boolean isAudioPlayed = false;
    private int leftNegotiation = OBVConstant.NEGOTIATION.UNKNOW;
    private int rightNegotiation = OBVConstant.NEGOTIATION.UNKNOW;
    private ObvPlayInfo obvPlayInfo;
    private String vsdp = "";
    private VideoParam leftVideoParam;
    private VideoParam rightVideoParam;
    private byte[] lastSdpFromNonVideo;
    private String vodPlayCallBackUrl;
    private String oldMedia;
    private volatile long startTime = 0;
    private volatile long endTime = 0;
    private AtomicInteger vodTimes = new AtomicInteger(0);
    private int videoPayload = 0;
    private String amrwbSDP = "";
    private String[] amrwbPayload = new String[2];
    private volatile boolean isSendRecvTried = false;
    private volatile int playType = 0;

    private volatile ConcurrentHashMap<String, TransactionHolder> leftTranHolderMap = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, TransactionHolder> rightTranHolderMap = new ConcurrentHashMap<>();

    public void init(ObvPlayInfo obvPlayInfo) {
        this.obvPlayInfo = obvPlayInfo;
        notifyStatusUpdate(OBVConstant.REASON_CODE.INIT, null);
    }

    public PluginEventResultVo accept(PluginEvent event) {
        logger.info("[{}] video play plugin accept start", billNew.getId());
        synchronized (this) {
            if (status == OBVConstant.STATUS.INIT) {
                notifyStatusUpdate(OBVConstant.REASON_CODE.PREPARE, null);
                XmodePromExporterService.getInstance().incAiDouTotal();
                try {
                    if (event.getRequestEvent() != null) {
                        SIPRequest request = event.getRequestEvent().getRequest();
                        rightLeg.forwardRequest(request, mediaSession, false);
                        refreshBaseState(rightLeg, SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.SEND, SIPRequest.ACK), request.getTransactionId());
                    }
                    vodPlayCallBackUrl = MediaManager.HTTP_SERVER_BASE_URL + OBVConstant.PLAY_BACK_URL;
                    Integer[] payloadArray = SdpHelper.generateDynamicPayload(initialInviteRequest.getRawContent(), 3);
                    if (payloadArray.length >= 3) {
                        vsdp = OBVConstant.OBV_VIDEO;
                        videoPayload = payloadArray[0];
                        vsdp = vsdp.replaceFirst("VIDEO_PORT", String.valueOf(mediaSession.getVideoport()));
                        vsdp = vsdp.replaceFirst("MEDIA_IP", String.valueOf(mediaSession.getProxyEndpoint().getIp()));
                        vsdp = vsdp.replaceAll("PAYLOAD", String.valueOf(videoPayload));
                        amrwbPayload[0] = String.valueOf(payloadArray[1]);
                        amrwbPayload[1] = String.valueOf(payloadArray[2]);
                        amrwbSDP = OBVConstant.AMRWB_SDP;
                        amrwbSDP = amrwbSDP.replaceAll("AMRWB_1", String.valueOf(amrwbPayload[0]));
                        amrwbSDP = amrwbSDP.replaceAll("AMRWB_2", String.valueOf(amrwbPayload[1]));
                    } else {
                        logger.error("[{}] video play plugin accept fail to get dynamic payload.", billNew.getId());
                        notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
                        return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.OTHER_REASON, OBVConstant.RETURN_MSG.OTHER_REASON);
                    }
                    oldMedia = mediaSession.getMedia();
                    int index = oldMedia.indexOf("-octet");
                    if (index > 0) {
                        oldMedia = oldMedia.substring(0, index);
                    }
                    return activateVod();
                } catch (Exception e) {
                    logger.error("[{}] video play plugin VodPlugin accept exception.", billNew.getId(), e);
                    notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
                    return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.OTHER_REASON, OBVConstant.RETURN_MSG.OTHER_REASON);
                }
            } else if (status == OBVConstant.STATUS.ACTIVE) {
                return vodComplete(event.getMapObject());
            } else {
                logger.warn("[{}] video play plugin Vod play is preparing.", billNew.getId());
                return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.NEGOTIATION_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.NEGOTIATION_IS_IN_PROGRESS);
            }


        }

    }


    private PluginEventResultVo activateVod() throws Exception {
        //logger.info("activateVod start");
        if (status == OBVConstant.STATUS.PREPARE) {
            logger.info("[{}] video play plugin activateVod start", billNew.getId());
            startVodPlayAsynly();
            return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
        } else if (status == OBVConstant.STATUS.ACTIVE) {
            logger.warn("[{}] video play plugin It is playing video already!", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.VOD_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.VOD_IS_IN_PROGRESS);
        } else {
            logger.warn("[{}] video play plugin Current status is suitable to activate vod", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.NEGOTIATION_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.NEGOTIATION_IS_IN_PROGRESS);
        }

    }

    private void startVodPlayAsynly() {
        logger.info("[{}] video play plugin startVodPlayAsynly start", billNew.getId());
        ScheduleService.getInstance().schedule(() -> {
            startVodPlay();
        }, 0, TimeUnit.MILLISECONDS);
    }

    private void startVodPlay() {
        try {
            logger.info("[{}] video play plugin startVodPlay voDInfo:{},current status:{}", billNew.getId(), obvPlayInfo.toString(), this.status);
            if (obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY) {
                playVideoToRightOnly();
            } else if (obvPlayInfo.getDirection() == OBVConstant.TYPE.LEFT_ONLY) {
                playVideoToLeftOnly();
            } else {
                logger.error("[{}] video play plugin invalid direction. direction:{}", billNew.getId(), obvPlayInfo.getDirection());
                notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
            }
        } catch (Exception e) {
            logger.error("[{}] video play plugin start vod play exception. ", billNew.getId(), e);
            notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
        }

    }

    /**
     * 向主叫播放视频
     */
    private void playVideoToLeftOnly() {
        try {
            logger.info("[{}] video play plugin playVideoToLeftOnly start", billNew.getId());
            //给被叫发送不携带SDP的 reinvite
            Request rightReInvite = rightLeg.createRequest(Request.INVITE);
            //给 Contact 头部加上 video
            rightReInvite.removeHeader(ContactHeader.NAME);
            rightReInvite.setHeader(this.engine.contactHeaderWithVideo);
            //初始阶段，向不播放视频的一侧发送不携带SDP的 reinvite, 由于媒体协商未开始，因此若失败了不需要更新 status
            sendReinvite((SIPRequest) rightReInvite, rightLeg, handleFirstInviteRespForSinglePlay(rightLeg), getNotifyFailInStartHook(), getRetryVodUpon491Hook(rightLeg));

        } catch (Exception e) {
            logger.error("[{}] video play plugin play video to left only exception.", billNew.getId(), e);
            notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
        }
    }

    /**
     * 向被叫播放视频
     */
    private void playVideoToRightOnly() {
        try {
            logger.info("[{}] video play plugin playVideoToRightOnly start", billNew.getId());
            //给主叫发送不携带SDP的 reinvite
            Request leftReInvite = leftLeg.createRequest(Request.INVITE);
            //给 Contact 头部加上 video
            leftReInvite.removeHeader(ContactHeader.NAME);
            leftReInvite.setHeader(this.engine.contactHeaderWithVideo);

            //初始阶段，向不播放视频的一侧发送不携带SDP的 reinvite, 由于媒体协商未开始，因此若失败了不需要更新 status
            sendReinvite((SIPRequest) leftReInvite, leftLeg, handleFirstInviteRespForSinglePlay(leftLeg), getNotifyFailInStartHook(), getRetryVodUpon491Hook(leftLeg));
        } catch (Exception e) {
            logger.error("[{}] video play plugin play video to right only exception.", billNew.getId(), e);
            notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
        }

    }

    private long getSessionVersion() {
        long currSessionVersion = 0;
        if (obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY) {
            currSessionVersion = mediaSession.getSdpSession().getRightLocalSessionVersion();
        } else {
            currSessionVersion = mediaSession.getSdpSession().getLeftLocalSessionVersion();
        }
        return currSessionVersion;
    }

    private void increaseSessionVersion() {
        if (obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY) {
            mediaSession.getSdpSession().increaseRightLocalSessionVersion();
        } else {
            mediaSession.getSdpSession().increaseLeftLocalSessionVersion();
        }
    }

    /**
     * 单向视频插播：收到不插播一侧带offer(audio) 的 response 之后向插播的一侧发送 offer （audio+video)以拉起视频
     *
     * @param respfromLeg 收到此 response 的腿
     * @return
     */
    private Hook<SipResponseEvent> handleFirstInviteRespForSinglePlay(Leg respfromLeg) {
        Hook<SipResponseEvent> responseHook = responseEvent -> {
            SIPResponse response = responseEvent.getResponse();
            byte[] rawContent = response.getRawContent();
            if (rawContent == null || rawContent.length <= 0) {
                notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_START, responseEvent);
                logger.info("[{}] video play plugin handleFirstInviteRespForSinglePlay rawContent is empty", billNew.getId());
                return;
            }

            Hook<SipResponseEvent> inviteToVodRespFailHook = respEvent -> {
                /**
                 * 发给插播的一侧返回非200的 response，即插播协商失败，需给发空reinvite的一侧（即不插播的一侧）回ACK
                 * 这里回ACK因为是带了answer的，需将 media 回滚到原先语音通话的 media
                 */
                logger.warn("[{}] video play plugin Single play！Second invite get non-2xx response.statusCode:{}", billNew.getId(), respEvent.getResponse().getStatusCode());
                SIPRequest ackRequest = respfromLeg.createAck(response);
                try {
                    String ackSdp = mediaSession.filterSDPWithSpecifiedCode(rawContent, oldMedia, "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    if (StringUtils.isNotBlank(ackSdp)) {
                        ackRequest.setContent(ackSdp, engine.contentTypeHeader);
                    }
                } catch (Exception e) {
                    logger.error("[{}] video play plugin handleFirstInviteRespForSinglePlay fail exception.", billNew.getId(), e);
                }
                respfromLeg.sendRequest(ackRequest);
                notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, respEvent);
            };

            try {
                Leg oppositeLeg = getOppositeLeg(respfromLeg);
                notifyStatusUpdate(OBVConstant.REASON_CODE.START, null);
                //向视频插播的一侧发送携带 audio+video offer 的 reinvite
                String offerWithoutVideo = "";
                long lastSessionVersion = getSessionVersion();
                if (obvPlayInfo.isInteractive()) {
                    offerWithoutVideo = mediaSession.filterSDPWithSpecifiedCode(rawContent, "amr-wb", respfromLeg.isLeft() ? "left" : "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                } else {
                    String rawReinviteSdp = SdpHelper.generateReinviteSdp(rawContent, amrwbPayload, amrwbSDP, true);
                    offerWithoutVideo = mediaSession.filterSDPWithSpecifiedCode(rawReinviteSdp.getBytes(), "amr-wb", respfromLeg.isLeft() ? "left" : "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                }

                if (StringUtils.isBlank(offerWithoutVideo)) {
                    logger.error("[{}] video play plugin handleFirstInviteRespForSinglePlay offerWithoutVideo blank!", billNew.getId());
                    SIPRequest ackRequest = respfromLeg.createAck(response);
                    respfromLeg.sendRequest(ackRequest);
                    notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, null);
                    return;
                }
                long currSessionVersion = getSessionVersion();
                logger.info("[{}] lastSessionVersion:{},currSessionVersion:{}", billNew.getId(), lastSessionVersion, currSessionVersion);
                if (currSessionVersion == lastSessionVersion) {
                    increaseSessionVersion();
                    offerWithoutVideo = new String(SdpHelper.increaseSessionVersion(offerWithoutVideo.getBytes()));
                }

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
                    logger.info("[{}] video play plugin Receive 491 for invite to right. Will try again!", billNew.getId());
                    ScheduleService.getInstance().schedule(() -> {
                        try {
                            SIPRequest retryReInvite = createRequestByCopy(oppositeLeg, oppositeReInvite);
                            sendReinvite(retryReInvite, oppositeLeg, handleSecondInviteRespForSinglePlay(oppositeLeg, response), inviteToVodRespFailHook, null);
                        } catch (Exception e) {
                            logger.error("[{}] video play plugin handleFirstInviteRespForSinglePlay exception.", billNew.getId(), e);
                            try {
                                inviteToVodRespFailHook.apply(responseEvent);
                            } catch (Exception exception) {
                                logger.error("[{}] video play plugin handleFirstInviteRespForSinglePlay send ack to non-vod side exception.", billNew.getId(), e);
                            }
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                });
            } catch (Exception e) {
                logger.error("[{}] video play plugin handleFirstInviteRespForSinglePlay exception.", billNew.getId(), e);
                inviteToVodRespFailHook.apply(responseEvent);
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
            lastSdpFromNonVideo = previousResponse.getRawContent();
            byte[] rawContent = response.getRawContent();
            if (rawContent == null || rawContent.length <= 0) {
                notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
                return;
            }

            Leg oppositeLeg = getOppositeLeg(legOfCurrentResp);
            boolean isFirstInviteAck = false;

            try {
                int remoteVideoPort = 0; //记录 remote 的 video port 是否为0，若为0，表示关闭视频
                if (legOfCurrentResp.isLeft()) {
                    leftVideoParam = SdpHelper.getRemoteVideoInfo(rawContent);
                    remoteVideoPort = leftVideoParam.getVideoPort();
                } else {
                    rightVideoParam = SdpHelper.getRemoteVideoInfo(rawContent);
                    remoteVideoPort = rightVideoParam.getVideoPort();
                }
                String sdpFromCurrentResp = "";
                //单向向主叫插播视频
                if (legOfCurrentResp.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.LEFT_ONLY) {
                    //如果只向主叫播放视频，在这里先将发给被叫的 answer sdp 去掉 video
                    sdpFromCurrentResp = mediaSession.filterSDPWithSpecifiedCode(rawContent, "amr-wb", "left", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    if (!obvPlayInfo.isInteractive()) {
                        sdpFromCurrentResp = SdpHelper.createG711Answer(lastSdpFromNonVideo, mediaSession.getProxyEndpoint().getIp(), mediaSession.getProxyEndpoint().getPort());
                    }
                }

                //单向向被叫插播视频
                if (!legOfCurrentResp.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY) {
                    //如果只向被叫播放视频，在这里先将发给主叫的 answer sdp 去掉 video
                    sdpFromCurrentResp = mediaSession.filterSDPWithSpecifiedCode(rawContent, "amr-wb", "right", MediaSession.VIDEO_MODE.REMOVE_VIDEO_SDP);
                    if (!obvPlayInfo.isInteractive()) {
                        sdpFromCurrentResp = SdpHelper.createG711Answer(lastSdpFromNonVideo, mediaSession.getProxyEndpoint().getIp(), mediaSession.getProxyEndpoint().getPort());
                    }
                }
                if (StringUtils.isNotBlank(sdpFromCurrentResp)) {
                    SIPRequest firstLegAck = oppositeLeg.createAck(previousResponse);
                    //如果不播放视频的一端提供的 offer 中带了 video 行，则应在 answer 中也带上 video 行，只是 video 端口为0，表示关闭
                    firstLegAck.setContent(sdpFromCurrentResp.getBytes(), engine.contentTypeHeader);
                    oppositeLeg.sendRequest(firstLegAck);
                    isFirstInviteAck = true;
                    mediaSession.commit();
                    if (remoteVideoPort > 0) {
                        notifyStatusUpdate(OBVConstant.REASON_CODE.ACTIVE, responseEvent);
                        notifyMediaPlayVideo();
                        oppositeLeg.controller.resetLastUpdateTime();
                    } else {
                        if (!isSendRecvTried && obvPlayInfo.isAggressiveVod()) {
                            logger.info("[{}] video play plugin remote reject video,port is 0,will try sendrecv!", billNew.getId());
                            isSendRecvTried = true;
                            vsdp = vsdp.replaceFirst("a=sendonly", "a=sendrecv");
                            startVodPlayAsynly();
                        } else {
                            notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT, responseEvent);
                        }
                    }
                } else {
                    logger.info("[{}] video play plugin SDP of answer from vod terminal is blank, will send black ack to non-vod terminal.", billNew.getId());
                    notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
                    //插播一侧返回空SDP，也需要给不插播的一侧回ACK，这里暂时设置成回空ACK
                    SIPRequest ackRequest = oppositeLeg.createAck(previousResponse);
                    oppositeLeg.sendRequest(ackRequest);
                    isFirstInviteAck = true;
                }
            } catch (Exception e) {
                logger.error("[{}] video play plugin handleSecondInviteRespForSinglePlay exception.", billNew.getId(), e);
                if (!isFirstInviteAck) {
                    SIPRequest ackRequest = oppositeLeg.createAck(previousResponse);
                    oppositeLeg.sendRequest(ackRequest);
                }
                notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
            }
        };
        return reInviteToLeftSuccessAction;
    }

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
        //boolean needSendVideoEvent = false;
        boolean needToStopVideo = false;
        boolean needToPlayAudio = false;
        if (reasonCode == OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION && responseEvent != null) {
            if (responseEvent.getLeg().isLeft()) {
                this.leftNegotiation = OBVConstant.NEGOTIATION.FAIL;
            } else {
                this.rightNegotiation = OBVConstant.NEGOTIATION.FAIL;
            }
        }
        //logger.info("[{}] video play plugin notifyStatusUpdate old status:{}",bill.getBillId(),this.status);
        synchronized (this) {
            //正常退出插播的情况，需计算插播时长
            if (this.status == OBVConstant.STATUS.ACTIVE && (reasonCode == OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE ||
                    reasonCode == OBVConstant.REASON_CODE.END_BY_BYE || reasonCode == OBVConstant.REASON_CODE.END_BY_REQUEST ||
                    reasonCode == OBVConstant.REASON_CODE.REMOTE_VIDEO_CLOSE)) {
                // billNew.setVideoInsertTimes(vodTimes.intValue() + billNew.getVideoInsertTimes());
                if (startTime != 0 && endTime == 0 && this.status == OBVConstant.STATUS.ACTIVE) {
                    endTime = System.currentTimeMillis();
                    // billNew.setVideoInsertDuration(startTime, endTime);
                }

                if (reasonCode == OBVConstant.REASON_CODE.END_BY_BYE || reasonCode == OBVConstant.REASON_CODE.END_BY_REQUEST ||
                        reasonCode == OBVConstant.REASON_CODE.REMOTE_VIDEO_CLOSE) {
                    needToStopVideo = true;
                }
            }

            switch (reasonCode) {
                case OBVConstant.REASON_CODE.ACTIVE:
                    this.status = OBVConstant.STATUS.ACTIVE;
                    break;
                case OBVConstant.REASON_CODE.PREPARE:
                    this.status = OBVConstant.STATUS.PREPARE;
                    break;
                case OBVConstant.REASON_CODE.START:
                    this.status = OBVConstant.STATUS.START;
                    break;
                case OBVConstant.REASON_CODE.INIT:
                    this.status = OBVConstant.STATUS.INIT;
                    break;
                case OBVConstant.REASON_CODE.REMOTE_VIDEO_CLOSE:
                    this.status = OBVConstant.STATUS.END;
                    this.needToRetreate = true;
                    break;
                case OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE:
                case OBVConstant.REASON_CODE.END_BY_REQUEST:
                case OBVConstant.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND:
                    this.needToRetreate = true;
                    this.status = OBVConstant.STATUS.END;
                    break;
                case OBVConstant.REASON_CODE.END_BY_BYE:
                    this.status = OBVConstant.STATUS.END;
                    this.needToRetreate = false;
                    break;
                case OBVConstant.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT:
                case OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION:
                case OBVConstant.REASON_CODE.FAIL_IN_START:
                case OBVConstant.REASON_CODE.OTHER_EXCEPTION:
                    needToPlayAudio = true;
                    break;
                default:
                    break;

            }

            if (!isAudioPlayed && needToPlayAudio) {
                isAudioPlayed = true;
                needToPlayAudio = true;
            } else {
                needToPlayAudio = false;
            }
        }

        logger.info("[{}] video play plugin notifyStatusUpdate new status:{},needToStopVideo:{},needToPlayAudio:{},needToRetreate:{}", billNew.getId(), this.status, needToStopVideo, needToPlayAudio, needToRetreate);

        //兜底播放语音。进入播放语音状态，将状态设置为 active
        if (needToPlayAudio) {
            try {
                this.status = OBVConstant.STATUS.ACTIVE;
                notifyMediaPlayAudio();
            } catch (Exception e) {
                logger.error("[{}] video play plugin notifyMediaPlayVideo exception. ", billNew.getId(), e);
            }
        } else if (needToStopVideo) {
            mediaSession.stopVomVideo();
        }

        if (this.status == OBVConstant.STATUS.END) {
            if (StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.STOP_CALL_AFTER_PLAY)) {
                needToRetreate = false;
                if (reasonCode != OBVConstant.REASON_CODE.END_BY_BYE) {
                    completeCallByBye();
                }
            }
            handleVodTermination(reasonCode);
        }
    }

    private void handleVodTermination(int reasonCode) {
        if (!callParameter.isNeedVideoInsertStatus()) {
            logger.info("[{}] notifyVodEvent no need to notify vod event, reasonCode:{}", billNew.getId(), reasonCode);
            return;
        }

        //如果是视频播放结束，需要计算播放时长，推送事件
        String eventCode = "";
        switch (reasonCode) {
            case OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                break;
            case OBVConstant.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.TERMINAL_NOT_SUPPORT_VOD;
                break;
            case OBVConstant.REASON_CODE.FAIL_IN_START:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                break;
            case OBVConstant.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.RESOURCE_NOT_FOUND;
                break;
            case OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SUCCESS;
                break;
            case OBVConstant.REASON_CODE.END_BY_REQUEST:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_FROM_HTTP;
                break;
            case OBVConstant.REASON_CODE.END_BY_BYE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_BY_BYE;
                break;
            case OBVConstant.REASON_CODE.OTHER_EXCEPTION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.OTHER_EXCEPTION;
                break;
            case OBVConstant.REASON_CODE.REMOTE_VIDEO_CLOSE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SWITCH_TO_AUDIO_VIA_PHONE;
                break;
            default:
                break;
        }

        if (playType == OBVConstant.PLAY_TYPE.PLAY_AUDIO) {
            eventCode = VodConstant.VOD.VOD_EVENT_CODE.RETURN_TO_PLAY_AUDIO;
        }

        logger.info("[{}] notify event eventCode:{}", billNew.getId(), eventCode);
        try {
            //推送插播事件
            if (StringUtils.isNotBlank(eventCode)) {
                createVodEventDo(eventCode);
            }
        } catch (Exception e) {
            logger.error("[{}] send vod event exception.", billNew.getId(), e);
        }
    }

    private void createVodEventDo(String eventCode) throws Exception {
        // VodEventDo vodEventDo = new VodEventDo(String.valueOf(billNew.getId()), billNew.getBindId(), obvPlayInfo.getVideoPlayCode(), "");
        VodEventDo vodEventDo = new VodEventDo(String.valueOf(billNew.getId()), "", obvPlayInfo.getVideoPlayCode(), "");
        vodEventDo.setResultCode(eventCode);
        vodEventDo.setTimeStamp(System.currentTimeMillis());
        // vodEventDo.setDuration(billNew.getVideoInsertDurationS());
        vodEventDo.setCompanyId(prodNum.getUserId());
        vodEventDo.setCustomerId(prodNum.getAppId());
        obvPlayInfo.setVodEventDo(vodEventDo);
        logger.info("[{}] notify event vodEventDo:{}", billNew.getId(), vodEventDo);
    }

    public ObvPlayInfo getObvPlayInfo() {
        return obvPlayInfo;
    }

    private Hook<SipResponseEvent> getNotifyFailInStartHook() {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] video play plugin Fail in negotiation start!", billNew.getId());
            notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_START, responseEvent);
        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> getNotifyFailInProgressHook() {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] Fail in negotiation process!", billNew.getId());
            notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_IN_NEGOTIATION, responseEvent);
        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> getRetryVodUpon491Hook(Leg leg) {
        Hook<SipResponseEvent> responseEventHook = responseEvent -> {
            logger.error("[{}] video play plugin Retry upon 491 in vod activation!", billNew.getId());
            //leg.sendAck();
            ScheduleService.getInstance().schedule(() -> {
                try {
                    //activateVod();
                    startVodPlay();
                } catch (Exception e) {
                    logger.error("[{}] video play plugin VodPlugin accept exception.", billNew.getId(), e);
                    notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
                }
            }, 500, TimeUnit.MILLISECONDS);
        };
        return responseEventHook;
    }

    private Hook<SipResponseEvent> getResponseBaseHook(Hook<SipResponseEvent> actionUponSuccess, Hook<SipResponseEvent> actionUponFail, Hook<SipResponseEvent> actionUpon491) {
        Hook<SipResponseEvent> responseHook = responseEvent -> {
            int statusCode = responseEvent.getResponse().getStatusCode();
            logger.info("[{}] video play plugin getResponseBaseHook response method:{}, statusCode:{}", billNew.getId(), responseEvent.getMethod(), statusCode);
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

    private void notifyMediaPlayAudio() throws Exception {
        if (StringUtils.isBlank(obvPlayInfo.getAudioFile())) {
            logger.error("[{}] video play plugin play audio: video file is null or empty!", billNew.getId());
            notifyStatusUpdate(OBVConstant.REASON_CODE.OTHER_EXCEPTION, null);
            return;
        }
        /*
        String filePath = obvPlayInfo.getVideoFile().split("\\.")[0];
        mediaSession.play(filePath, 2, resp -> {},MediaManager.HTTP_SERVER_BASE_URL +OBVConstant.PLAY_BACK_URL);
        */

        String filePath = obvPlayInfo.getAudioFile().split("\\.")[0];
        if (filePath.endsWith("_wb")) {
            filePath = filePath.substring(0, filePath.length() - 3);
        }
        logger.info("[{}] notifyMediaPlayAudio filePath:{}", billNew.getId(), filePath);
        mediaSession.play(filePath, 2, 0, resp -> {
        }, MediaManager.HTTP_SERVER_BASE_URL + OBVConstant.PLAY_BACK_URL,true);
        playType = OBVConstant.PLAY_TYPE.PLAY_AUDIO;
        XmodePromExporterService.getInstance().incAiDouAudioPlayNum();

    }

    /**
     * 通知xmedia 播放视频
     */
    private void notifyMediaPlayVideo() throws Exception {
        if (StringUtils.isBlank(obvPlayInfo.getVideoFile())) {
            logger.error("[{}] video play plugin video file is null or empty!", billNew.getId());
            return;
        }
        VomPlayParam vomPlayParam = new VomPlayParam();
        vomPlayParam.setAudioCode(mediaSession.getMedia());
        vomPlayParam.setVideoCode("H264");
        vomPlayParam.setDirection(obvPlayInfo.getDirection());

        //拼接音频文件后缀
        /*String filePath = obvPlayInfo.getVideoFile().split("\\.")[0];
        if (mediaSession.getMedia().startsWith("amr-wb")) {
            filePath = filePath + "_wb.amr";
        } else if (mediaSession.getMedia().startsWith("amr")) {
            filePath = filePath + ".amr";
        } else {
            filePath = filePath + ".wav";
        }

        vomPlayParam.setAudioFile(filePath);
        vomPlayParam.setVideoFile(obvPlayInfo.getVideoFile() + ".h264");*/

        vomPlayParam.setVideoFile(obvPlayInfo.getVideoFile());
        vomPlayParam.setAudioFile(obvPlayInfo.getAudioFile());
        vomPlayParam.setPlayTimes(obvPlayInfo.getPlayTimes());
        if (leftVideoParam != null) {
            vomPlayParam.setLeftVideoIp(leftVideoParam.getVideoIp());
            vomPlayParam.setLeftVideoPort(leftVideoParam.getVideoPort());
        }

        if (rightVideoParam != null) {
            vomPlayParam.setRightVideoIp(rightVideoParam.getVideoIp());
            vomPlayParam.setRightVideoPort(rightVideoParam.getVideoPort());
        }

        vomPlayParam.setVideoPayload(videoPayload);
        vomPlayParam.setxRate(640);
        vomPlayParam.setyRate(480);
        vomPlayParam.setBackUrl(vodPlayCallBackUrl);
        logger.info("[{}] video play plugin notifyMediaPlayVideo vodPlayParam:{}", billNew.getId(), vomPlayParam.toString());
        //如果接通后的放音较长，则插播之前先确认提示音是否已经结束，若未结束，先 stop 再插播视频
        if (mediaSession.isPickupAudioPlaying()) {
            logger.info("[{}] video play plugin Audio play after pickup is not finished yet! Will stop play audio first.", billNew.getId());
            mediaSession.stopAudioPlay();
        }
        vodTimes.incrementAndGet();
        startTime = System.currentTimeMillis();
        mediaSession.playVomVideo(vomPlayParam, null);
        playType = OBVConstant.PLAY_TYPE.PLAY_VIDEO;
        if (isSendRecvTried) {
            XmodePromExporterService.getInstance().incAiDouSendrecvSuccNum();
        } else {
            XmodePromExporterService.getInstance().incAiDouSendonlySuccNum();
        }

        XmodePromExporterService.getInstance().incAiDouVideoPlayingNum();
    }

    /**
     * xmedia 回调播放视频播放完成
     */
    public PluginEventResultVo vodComplete(Map<String, Object> dataMap) {
        logger.info("[{}] video play plugin vodComplete Video play done. Xmedia return result! dataMap:{}", billNew.getId(), dataMap.toString());
        String code = (String) dataMap.getOrDefault("code", "");
        if (code.equals(OBVConstant.EVENT_CODE.RESOURCE_NOT_FOUND)) {
            if (vodTimes.intValue() > 0) {
                vodTimes.decrementAndGet();
            }
            notifyStatusUpdate(OBVConstant.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND, null);
            logger.warn("[{}] video play plugin vodComplete resId not found.", billNew.getId());
        } else if (this.status != OBVConstant.STATUS.END) {
            notifyStatusUpdate(OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE, null);
            // logger.info("[{}] video play plugin vodComplete Vod play duration：{} times，{} seconds,{} minutes", billNew.getId(), billNew.getVideoInsertTimes(), billNew.getVideoInsertDurationS(), billNew.getVideoInsertDurationM());
        } else {
            notifyStatusUpdate(OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE, null);
        }
        return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
    }

    private void completeCallByBye() {
        logger.info("[{}] video play plugin complete call by bye start", billNew.getId());
        try {
            SIPRequest byeRequest = leftLeg.createRequest("BYE");
            preciseCallState.extractRequestMsgPropertiesJudgeReject(true, byeRequest);
            leftLeg.sendByeOr487();
            rightLeg.sendByeOr487();
        } catch (Exception e) {
            logger.error("[{}] VideoPlayPlugin completeCallByBye exception", billNew.getId(), e);
        }
    }


    private PluginEventResultVo processVodStop(int reasonCode) {
        logger.info("[{}] video play plugin User trigger request to stop video play!", billNew.getId());
        if (this.status == OBVConstant.STATUS.ACTIVE) {
            if (reasonCode != OBVConstant.REASON_CODE.REMOTE_VIDEO_CLOSE) {
                if (leftTranHolderMap.size() > 0 || rightTranHolderMap.size() > 0) {
                    logger.error("[{}] video play plugin Cannot stop video play currently. current status is:{}", billNew.getId(), this.status);
                    return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.NEGOTIATION_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.NEGOTIATION_IS_IN_PROGRESS);
                }
            }
            notifyStatusUpdate(reasonCode, null);
            // logger.info("[{}] video play plugin processVodStop Vod play duration：{} times，{} seconds,{} minutes", billNew.getId(), billNew.getVideoInsertTimes(), billNew.getVideoInsertDurationS(), billNew.getVideoInsertDurationM());
            return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
        }
        return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.NO_VOD_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.NO_VOD_IS_IN_PROGRESS);
    }

    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();
        String method = responseEvent.getMethod();
        logger.info("[{}] video play plugin processResponse status:{}, receive response:{}", billNew.getId(), status, method);
        Leg oppositeLeg = getOppositeLeg(leg);
        if (status == OBVConstant.STATUS.ACTIVE && statusCode == 200 &&
                (method.equalsIgnoreCase(SIPRequest.INVITE) || method.equalsIgnoreCase(SIPRequest.UPDATE))) {
            // 已完成视频协商，视频插播进行中
            byte[] respRawContent = response.getRawContent();
            if (respRawContent != null && respRawContent.length > 0) {
                String newContent = "";
                if ((leg.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.LEFT_ONLY) ||
                        (!leg.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY)) {
                    //单向插播，响应来自于插播的腿: 因另一条腿没有视频，应将SDP中video删减
                    newContent = SdpHelper.trimVideoLines(respRawContent);
                } else {
                    //单向插播，响应来自不插播的一条腿: 因另一条腿有视频，应在SDP中添加video
                    newContent = new String(respRawContent) + vsdp;
                }
                response.setContent(newContent, engine.contentTypeHeader);
            }
        }
        oppositeLeg.forwardResponse(responseEvent, mediaSession, null, false);
        checkAndClearTransactionHolder(oppositeLeg, response);
        return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
    }

    private String buildCloseVideoSdp(String sdp) {
        return sdp + "m=video 0 RTP/AVP " + videoPayload + "\n";
    }

    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        Leg oppositeLeg = getOppositeLeg(leg);
        String transactionId = request.getTransactionId();
        logger.info("[{}] video play plugin processRequest Vod status:{}, receive request:{}", billNew.getId(), status, method);

        if (method.equalsIgnoreCase(SIPRequest.BYE)) {
            //如果是bye请求，就先停止插播并更新状态，然后返回给上一层处理
            notifyStatusUpdate(OBVConstant.REASON_CODE.END_BY_BYE, null);
            return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
        }

        if ((status == OBVConstant.STATUS.START || status == OBVConstant.STATUS.PREPARE) &&
                !Request.ACK.equalsIgnoreCase(method)) {
            // 正在视频插播协商
            logger.info("[{}] video play plugin is processing vod negotiation, will return 500", billNew.getId());
            SIPResponse response491 = leg.createResponse(500, request);
            leg.sendResponse(request, response491);
            return PluginEventResultVo.newPluginFailVo(OBVConstant.RETURN_CODE.NEGOTIATION_IS_IN_PROGRESS, OBVConstant.RETURN_MSG.NEGOTIATION_IS_IN_PROGRESS);
        } else if (status == OBVConstant.STATUS.ACTIVE &&
                (method.equalsIgnoreCase(SIPRequest.INVITE) || method.equalsIgnoreCase(SIPRequest.UPDATE))) {
            // 已完成视频协商，视频插播进行中
            byte[] reqRawContent = request.getRawContent();
            if (reqRawContent != null && reqRawContent.length > 0) {
                String newContent = "";
                VideoParam videoParam = SdpHelper.getRemoteVideoInfo(reqRawContent);
                if ((leg.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.LEFT_ONLY) ||
                        (!leg.isLeft() && obvPlayInfo.getDirection() == OBVConstant.TYPE.RIGHT_ONLY)) {
                    //单向插播，请求来自于插播的腿
                    //如果video端口为0 或 非语音透传模式，则AS直接回response，根据 afterPlay 决定是否触发语音回落
                    if (videoParam.getVideoPort() <= 0 || !obvPlayInfo.isInteractive()) {
                        leg.sendResponse200(request, response -> {
                            String responseSdp = SdpHelper.generateReinviteSdp(reqRawContent, amrwbPayload, amrwbSDP, false);
                            response.setContent(responseSdp, engine.contentTypeHeader);
                        });
                        leg.waitRequest(Request.ACK, req -> {
                            processVodStop(OBVConstant.REASON_CODE.END_BY_REQUEST);
                        });
                        return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
                    }
                    //单向插播，语音透传。因另一条腿没有视频，应将SDP中video删减
                    newContent = SdpHelper.trimVideoLines(reqRawContent);
                } else {
                    //单向插播，请求来自不插播的一条腿: 因另一条腿有视频，应在SDP中添加video
                    if (obvPlayInfo.isInteractive()) {
                        //语音透传
                        if (videoParam.getVideoPort() <= 0) {
                            newContent = new String(reqRawContent) + vsdp;
                        } else {
                            //如果不插播的一条腿拉起视频通话请求，则返回406 Not Acceptable
                            logger.warn("[{}] video play plugin Terminal try to trigger video call, AS return 406", billNew.getId());
                            SIPResponse response406 = leg.createResponse(406, request);
                            leg.sendResponse(request, response406);
                            return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
                        }
                    } else {
                        //非语音透传
                        leg.sendResponse200(request, response -> {
                            String responseSdp = SdpHelper.createG711Answer(reqRawContent, mediaSession.getProxyEndpoint().getIp(), mediaSession.getProxyEndpoint().getPort());
                            response.setContent(responseSdp, engine.contentTypeHeader);
                        });
                        return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
                    }
                }
                request.setContent(newContent, engine.contentTypeHeader);
            }
        }
        oppositeLeg.forwardRequest(request, mediaSession, null, false);
        //检查并清理 transaction holder
        if (Request.ACK.equalsIgnoreCase(method)) {
            checkAndClearTransactionHolder(leg, request);
            if (leftTranHolderMap.size() <= 0 && rightTranHolderMap.size() <= 0 && status == OBVConstant.STATUS.SWITCH_TO_AUDIO) {
                notifyStatusUpdate(OBVConstant.REASON_CODE.END_BECAUSE_OF_COMPLETE, null);
            }
        } else {
            TransactionHolder newTranHolder = TransactionHolderFactory.createTransactionHolder(method, request, billNew);
            if (newTranHolder != null) {
                getTransHolderMap(leg).put(transactionId, newTranHolder);
            }
        }

        return PluginEventResultVo.newDealSuccessVo(OBVConstant.RETURN_CODE.SUCCESS, OBVConstant.RETURN_MSG.SUCCESS);
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
                    logger.info("[{}] video play plugin message remove. method:{},CSeq:{}", billNew.getId(), response.getCSeq().getMethod(), response.getCSeq().getSeqNumber());
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
                            logger.info("[{}] video play plugin request message is remove. method:{},CSeq:{}", billNew.getId(), request.getMethod(), request.getCSeq().getSeqNumber());
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
    }

    @Override
    public boolean isComplete() {
        if (this.status == OBVConstant.STATUS.END) {
            mediaSession.clearVideoData();
            logger.info("[{}] video play plugin is completed!!", billNew.getId());
            return true;
        } else {
            logger.info("[{}] video play plugin is not completed yet!", billNew.getId());
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

        String s = "ziyan.wav";
        String[] a = s.split("\\.");
        System.out.println(a.length);
        for (String b : a) {
            System.out.println(b);
        }

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


}
