package com.cmic.service;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.ConstantConf;
import com.cmic.constant.MediaConst;
import com.cmic.pojo.VideoParam;
import com.cmic.recording.IPPort;
import com.cmic.recording.MediaHook;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.recording.mediaevent.CallBackEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.util.DevHelp;
import com.cmic.util.DtmfLogger;
import com.cmic.util.PhoneNumUtil;
import com.discovery.ServiceNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nist.javax.sip.message.SIPRequest;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;


public class MediaService {


    public static MediaSession createMediaSession(CallParameter callParameter, BillNew billNew, SIPRequest initialRequest) throws JsonProcessingException {
        int recordMode = ConstantConf.MEDIA_LOGIC_FLAG_COMMON;
        // if (callParameter.isNeedEarly()) {
        //     bill.setNeedEarly(true);
        //     recordMode = ConstantConf.MEDIA_LOGIC_FLAG_NEED_EARLY;
        // }

        MediaSession tempMediaSession = new MediaSession("0.0.0.0", billNew.getId(), new IPPort("0.0.0.0", 0), 9999);
        tempMediaSession.setSupportedCodec(new HashSet<>(Arrays.asList(callParameter.getSupportedCodec())));
        byte[] sdp2Right = tempMediaSession.processLeftSDPV3(initialRequest);
        if(ifOnlyG729(billNew.getId(), sdp2Right)){
            callParameter.setOnlySupportG729(true);
            tempMediaSession.setActive(false);
            DtmfLogger.error(billNew.getId(), "no support code G729 forward");
            XmodePromExporterService.getInstance().incNoSupportCode();
            return tempMediaSession;
        }
        ServiceNode recordHost = MyRegistryService.getInstance().getRecordHost();
        DtmfLogger.info(billNew.getId(), "recordHost:{}", recordHost);
        DtmfLogger.info(billNew.getId(), "recordMode:{}", recordMode);
        ObjectNode recordParam = (ObjectNode)MediaManager.objectMapper.readTree(callParameter.getRecordParams().toString());
        if(recordHost == null){
            return MediaManager.createMediaSession(billNew.getId(),
                    null, 0, billNew.getAppId(), billNew.getCallerNumber()
                    , PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recordParam, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
        }
        MediaSession mediaSession = MediaManager.createMediaSession(billNew.getId(),
                "http://"+recordHost.getAddress(), Integer.parseInt(recordHost.getId()), billNew.getAppId(), billNew.getCallerNumber(),
                PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recordParam, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());

        if (!mediaSession.isActive()) {
            // 如果当前media服务不可用，应该获取一个新的media
            recordHost = MyRegistryService.getInstance().getRecordHost();
            DtmfLogger.info(billNew.getId(), "new recordHost:{}", recordHost);
            mediaSession = MediaManager.createMediaSession(billNew.getId(),
                    "http://"+recordHost.getAddress(), Integer.parseInt(recordHost.getId()),billNew.getAppId(), billNew.getCallerNumber(),
                    PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recordParam, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
        }
        mediaSession.setPreferMediaCodec(DevHelp.forceCodec);

        if (callParameter.isCanPassVideo()) {
            mediaSession.setRetainNetVideo(true);
        }
        return mediaSession;
    }

    public static boolean ifOnlyG729(String billId, byte[] sdp2Right){
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(sdp2Right));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            boolean have0or8;
            while (line != null) {
                if (line.contains("m=audio")){
                    String[] mLineParts = line.split(" ");
                    for(int i =0; i<mLineParts.length; i++){
                       if (i>2){
                           if (mLineParts[i].equals("0") || mLineParts[i].equals("8") ){
                               return false;
                           }
                       }
                    }
                    break;
                }
                line = br.readLine();
            }
            String sdp = new String(sdp2Right);
            return !(sdp.toUpperCase().contains("AMR-WB/") || sdp.toUpperCase().contains("AMR/"));

        } catch (Exception e){
            DtmfLogger.error(billId, "ifOnlyG729 process error forward");
            return true;
        }

    }

    /**
     * 播放早期媒体
     *
     * @param url
     * @param direc
     * @param isForce       - 是否强制播放嘟嘟音
     * @param hook
     * @param mediaSession
     * @param isCaller
     * @param optype
     * @param businessEvent
     */
    public static void xmPlayEarly(String url, int direc, boolean isForce, MediaHook<CallBackEvent> hook, MediaSession mediaSession, boolean isCaller, int optype, BusinessEvent businessEvent) {
        xmPlayEarly(url, direc, isForce, hook, mediaSession, isCaller, optype, businessEvent, false);
    }

    public static void stopPlayEarly(MediaSession mediaSession) {
        mediaSession.dccStopMedia();
    }

    /**
     * 挂机前播放早期媒体
     *
     * @param url
     * @param direc
     * @param isForce       - 是否强制播放嘟嘟音
     * @param hook
     * @param mediaSession
     * @param isCaller
     * @param optype
     * @param businessEvent
     */
    public static void xmPlayEarlyBeforeEnd(String url, int direc, boolean isForce, MediaHook<CallBackEvent> hook, MediaSession mediaSession, boolean isCaller, int optype, BusinessEvent businessEvent) {
        mediaSession.dccStopMedia();
        xmPlayEarly(url, direc, isForce, hook, mediaSession, isCaller, optype, businessEvent, false);
        mediaSession.setStopIsImportant(true);
        mediaSession.setActive(false);
    }

    public static void xmPlayEarly(String url, int direc, boolean isForce, MediaHook<CallBackEvent> hook, MediaSession mediaSession, boolean isCaller, int optype, BusinessEvent businessEvent, boolean preferRightRtp) {
        // recordPlayCodes(url, businessEvent.getBill(), true);
        // String realUrl = PlayCodeService.playCode2Url(url, businessEvent.getDccAuthConf().getUserId(), businessEvent.getDccAuthConf().getFileLocation(), isCaller, optype, businessEvent);
        // mediaSession.playearly(realUrl, direc, isForce, hook, preferRightRtp);
    }

    public static void xmPlay(String url, int direc, MediaHook<CallBackEvent> hook, MediaSession mediaSession, boolean isCaller, int optype, BusinessEvent businessEvent, String callBackUrl) {
        // recordPlayCodes(url, businessEvent.getBill(), false);
        // String realUrl = PlayCodeService.playCode2Url(url, businessEvent.getDccAuthConf().getUserId(), businessEvent.getDccAuthConf().getFileLocation(), isCaller, optype, businessEvent);
        // mediaSession.setPickupAudioPlaying(true);
        // if (hook != null) {
        //     mediaSession.play(realUrl, direc, 0, hook,callBackUrl,false);
        // } else {
        //     mediaSession.play(realUrl, direc,0);
        // }
    }


    public static void dccPlayMedia(String audioSource, String audioLoopSource,
                                    String videoSource, String videoLoopSource, String period, String priority, int direction,
                                    int recordMode, MediaHook<CallBackEvent> hook, MediaSession mediaSession, BusinessEvent businessEvent, boolean isNoBinding, int userId){
        // recordPlayCodes(audioSource, businessEvent.getBill(), StringUtils.equals(period,MediaConst.PLAY_PERIOD.EARLY));
        // String fullPathAudioSource = PlayCodeService.convertPlayCode2Url(audioSource, userId, businessEvent.getFileLocation(), direction, isNoBinding, businessEvent);
        // mediaSession.setPickupAudioPlaying(StringUtils.equals(period,MediaConst.PLAY_PERIOD.INCALL));
        // mediaSession.dccPlay(fullPathAudioSource, audioLoopSource, videoSource, videoLoopSource, period, priority, direction,
        //         recordMode,hook);
    }

    public static void dccPlayMedia(String audioSource, String audioLoopSource,
                                    String videoSource, String videoLoopSource, String period, String priority, int direction,
                                    int recordMode, MediaHook<CallBackEvent> hook, MediaSession mediaSession, BusinessEvent businessEvent, boolean isNoBinding, int userId, boolean isPassThrough){
       if (!isPassThrough){ //如果非透传给对向放音，接通后，如果为单边放音，给对向播放静音
           if (!audioSource.contains(";")){ //100100或0的情况是企业配置放音
               direction = MediaConst.PLAY_DIRECTION.BOTH_SAME;
           }
           String[] codes = audioSource.split(";");//企业配置的放音可能会让单向放音变为100301;100300,100301的形式
           String leftCode = "";
           String rightCode = "";
           if (codes.length > 1){
               leftCode = codes[0];
               rightCode = codes[1];
           }else {
               leftCode = codes[0];
           }
           if (direction == MediaConst.PLAY_DIRECTION.CALLEE && leftCode.isEmpty()){//过滤企业配置放音的情况
               audioSource = "Mute" + audioSource;// 放音为被叫方向，playUrl为“;100300”的形式，自带分号
               direction = MediaConst.PLAY_DIRECTION.BOTH_DIFFERENT;
           }
           else if (direction == MediaConst.PLAY_DIRECTION.CALLER && rightCode.isEmpty()){
               audioSource = audioSource + "Mute";// 放音为主叫方向，playUrl为“100300;”的形式，自带分号
               direction = MediaConst.PLAY_DIRECTION.BOTH_DIFFERENT;
           }
       }

       dccPlayMedia(audioSource, audioLoopSource, videoSource, videoLoopSource, period, priority,direction, recordMode,  hook,  mediaSession,  businessEvent,  isNoBinding, userId);

    }

    public static void playVideo(String url, boolean isCaller, MediaHook mediaHook, String videoIp, int videoPort, MediaSession mediaSession, BusinessEvent businessEvent, int optype) {
        // String realUrl = PlayCodeService.playCode2Url(url, businessEvent.getDccAuthConf().getUserId(), businessEvent.getDccAuthConf().getFileLocation(), isCaller, optype, businessEvent);
        // mediaSession.playvedio("H264", realUrl, 114, 640, 480, videoIp, videoPort, realUrl + ".h264");
    }

    public static String playVideoConsidering3gp(CallParameter callParameter, String url, boolean isCaller, MediaHook mediaHook, String videoIp, int videoPort, MediaSession mediaSession, BusinessEvent businessEvent, int optype, Hook<String> hook) {
        try {
            Map<String, String> fileMap = callParameter.getVideo3gpFileMap();
            if (fileMap != null && fileMap.size() > 0) {
                DtmfLogger.info(mediaSession.getSessionId() + "", " migu play3gp");
                String resolution = mediaSession.play3gpVideo("H264", fileMap, 114, videoIp, videoPort);
                String resKey = callParameter.getVideo3gpRecordMap().get(resolution);
                if (StringUtils.isBlank(resKey)) {
                    DtmfLogger.error(mediaSession.getSessionId() + "", " migu resolution key Blank!, resolution:{}", resolution);
                    return null;
                }
                if (hook != null) {
                    hook.apply(null);
                }
                return resKey;
            } else {
                playVideo(url, isCaller, mediaHook, videoIp, videoPort, mediaSession, businessEvent, optype);
                if (hook != null) {
                    hook.apply(null);
                }
                return null;
            }
        } catch (Exception e) {
            DtmfLogger.error(mediaSession.getSessionId() + "", " playVideoConsidering3gp error", e);
            return null;
        }
    }
}
