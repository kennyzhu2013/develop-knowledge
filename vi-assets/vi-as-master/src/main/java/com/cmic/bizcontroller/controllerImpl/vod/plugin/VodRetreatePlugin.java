package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.recording.MediaSession;
import com.cmic.service.ScheduleService;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import com.cmic.util.StringUtil;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;

import java.util.concurrent.TimeUnit;

import static com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent.EventTypeEnum.VODCOMPLETE;
import static com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent.EventTypeEnum.VODRE;

/**
 * 回落语音插件
 * Author: luohuanfa
 * Date: 2022/9/28
 */
public class VodRetreatePlugin extends AbstractPlugin {
    private String lastAudio;

    private boolean complete = false;

    @Override
    public PluginEventResultVo accept(PluginEvent event) throws Exception {
        logger.info("[{}], [VodRetreatePlugin-accept], retreate plugin", billNew.getId());
        if (!event.getType().equals(VODRE) && !event.getType().equals(VODCOMPLETE)) {
            logger.error("[{}], [VodRetreatePlugin-accept], vodre recv but not vodre event", billNew.getId());
            // return PluginEventResultVo.newDealResultVo(false, "vodre recv but not vodre event");
            return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        }
        SIPRequest reInvite = leftLeg.createRequest(SIPRequest.INVITE);
        sendInitialReInvite(reInvite);
        mediaSession.stopVomVideo();
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    private void sendInitialReInvite(SIPRequest reInvite) throws Exception {
        String billId = billNew.getId();
        sendRequest(leftLeg, reInvite, firstLeftReInviteResponseEvent -> {
            logger.info("[{}] VodRetreatePlugin sendInitialReInvite  CSeq:{}", billId, reInvite.getCSeqHeader().getSeqNumber());
            SIPResponse firstLeftReInviteResponse = firstLeftReInviteResponseEvent.getResponse();
            if (firstLeftReInviteResponse.getStatusCode() == 200) {
                logger.info("[{}], VodRetreatePlugin sendInitialReInvite recv 200", billId);
                String audio = StringUtil.isBlankOrNull(lastAudio) ? "amr-wb" : lastAudio;
                String firstLeftReInviteResponseSdp = mediaSession.filterSDPWithSpecifiedCode(firstLeftReInviteResponse.getRawContent(), audio, "left", MediaSession.VIDEO_MODE.CLOSE);
                // 2023/1/30 如果只是给一边放则有可能过来的没有video，所以得手动加上
                if (!StringUtils.isBlank(firstLeftReInviteResponseSdp) && !firstLeftReInviteResponseSdp.contains("m=video")) {
                    firstLeftReInviteResponseSdp = firstLeftReInviteResponseSdp + "m=video 0 RTP/AVPF 114\n" +
                            "a=rtpmap:114 H264/90000\n" +
                            "a=fmtp:114 profile-level-id=42C01F;packetization-mode=1;sar-understood=16;sar-supported=1;sprop-parameter-sets=Z0LAH9oC0ChoBtChNQ==,aM4G4g==\n" +
                            "a=recvonly\n";
                }
                SIPRequest rightReInvite = rightLeg.createRequest(SIPRequest.INVITE);
                rightReInvite.setContent(firstLeftReInviteResponseSdp, engine.contentTypeHeader);
                sendInitialRightReInvite(rightReInvite, firstLeftReInviteResponse);
                return;
            } else if (firstLeftReInviteResponse.getStatusCode() == 491) {
                logger.error("{}, VodRetreatePlugin sendInitialReInvite recv 491, cseq:{}", billId, firstLeftReInviteResponse.getCSeq());
                // leftLeg.sendAck();
                logger.error("{}, ScheduleService", billId);
                ScheduleService.getInstance().schedule(() -> {
                    try {
                        logger.error("[{}], resent reInvite", billId);
                        SIPRequest reInviteTemp = leftLeg.createRequest(SIPRequest.INVITE);
                        reInvite.setVia(reInviteTemp.getViaHeaders());
                        reInvite.setCSeq(reInviteTemp.getCSeq());
                        sendInitialReInvite(reInvite);
                    } catch (Exception e) {
                        logger.error("[{}], sendReInvite error when 491", billId, e);
                        throw new RuntimeException(e);
                    }
                }, 300, TimeUnit.MILLISECONDS);
                return;
            }
            logger.error("[{}], VodRetreatePlugin sendInitialReInvite recv error response, cseq:{}", billId, firstLeftReInviteResponseEvent.getResponse().getCSeq());
        });
        leftLeg.controller.resetLastUpdateTime();
    }

    private void sendInitialRightReInvite(SIPRequest rightReInvite, SIPResponse firstLeftReInviteResponse) throws Exception {
        String billId = billNew.getId();
        sendRequest(rightLeg, rightReInvite, rightReInviteResponseEvent -> {
            SIPResponse rightReInviteResponse = rightReInviteResponseEvent.getResponse();
            if (rightReInviteResponse.getStatusCode() == 200) {
                logger.info("{}, VodRetreatePlugin sendInitialRightReInvite recv 200", billId);
                SIPRequest firstLeftReInviteAck = leftLeg.createAck(firstLeftReInviteResponse);
                String audio = StringUtil.isBlankOrNull(lastAudio) ? "amr-wb" : lastAudio;
                String firstLeftReInviteAckSdp = mediaSession.filterSDPWithSpecifiedCode(rightReInviteResponse.getRawContent(), audio, "right", MediaSession.VIDEO_MODE.CLOSE);
                firstLeftReInviteAck.setContent(firstLeftReInviteAckSdp, engine.contentTypeHeader);
                leftLeg.sendRequest(firstLeftReInviteAck);
                rightLeg.sendAck();
                mediaSession.commit();
                complete = true;
            } else if (rightReInviteResponse.getStatusCode() == 491) {
                logger.error("{}, VodRetreatePlugin sendInitialRightReInvite recv 491, cseq:{}", billId, rightReInviteResponse.getCSeq());
                // rightLeg.sendAck();
                ScheduleService.getInstance().schedule(() -> {
                    try {
                        SIPRequest reInviteTemp = rightLeg.createRequest(SIPRequest.INVITE);
                        rightReInvite.setVia(reInviteTemp.getViaHeaders());
                        rightReInvite.setCSeq(reInviteTemp.getCSeq());
                        sendInitialRightReInvite(rightReInvite, firstLeftReInviteResponse);
                    } catch (Exception e) {
                        logger.error("[{}], sendInitialRightReInvite() error when resend reInvite in response 491", billId);
                        throw new RuntimeException(e);
                    }
                }, 300, TimeUnit.MILLISECONDS);
            } else {
                complete = true;
                logger.error("{}, VodRetreatePlugin sendInitialRightReInvite recv error response that can not deal, set complete, cseq:{}", billId, rightReInviteResponse.getCSeq());
            }
        });
        leftLeg.controller.resetLastUpdateTime();
    }

    @Override
    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) {
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    @Override
    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        if (request.getMethod().equals(SIPRequest.BYE)) {
            this.complete = true;
            return PluginEventResultVo.newPluginEventPassOn(PluginEventResultVo.SignalPassMode.SIGNAL_PASS);
        }
        SIPResponse response = leg.createResponse(491, request);
        leg.sendResponse(request, response);
        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    @Override
    public boolean isComplete() {
        return complete;
    }


    public void setLastAudio(String lastAudio) {
        this.lastAudio = lastAudio.contains("-octet") ? lastAudio.substring(0, lastAudio.indexOf("-octet")) : lastAudio;
    }

    public static void main(String[] args) {
        System.out.println("1234-octet");
        System.out.println("1234-octet".indexOf("-octet"));
        System.out.println("1234-octet".substring(0, "1234-octet".indexOf("-octet")));
    }
}
