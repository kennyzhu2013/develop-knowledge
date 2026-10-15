package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.BillNew;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.recording.MediaHook;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.recording.mediaevent.CallBackEvent;
import com.cmic.recording.params.VomPlayParam;
import com.cmic.service.EventRocketMQService;
import com.cmic.util.CommonUtil;
import com.cmic.constant.VodConstant;
import com.dcc.config.model.BaseProdNum;
import lombok.Data;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class VodEventProcessor {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private Map<String, VodPlayData> vodPlayDataMap = new HashMap<>();
    private BaseProdNum prodNum;
    private BillNew billNew;
    private String sessionId;
    private MediaSession mediaSession;
    private CallParameter callParameter;
    private volatile STATUS status = STATUS.FREE;
    private int currentPlayTid;
    private ReentrantLock statusLock = new ReentrantLock();
    private ReentrantLock dataLock = new ReentrantLock();

    enum STATUS {
        PLAYING_VIDEO,
        PLAYING_AUDIO,
        FREE
    }

    /**
     * 1. VodPluginGroup判断direction是否相同，如果相同，则直接停止播放旧视频，并播放新视频。
     * 2. 当 media 播放新视频完成后，回调AS，VodPluginGroup 调 currentPlugin 的 complete 函数结束当前插件，同时调 processor 的 processVodEnd
     * 3. VodPluginGroup判断direction不同，则停止当前plugin，同时调 processor 的 stopVideo和 processVodEnd, 然后创建新的 VodPlugin, 进入新的插播流程
     */
    public VodEventProcessor(BillNew billNew, BaseProdNum prodNum, MediaSession mediaSession, CallParameter callParameter) {
        this.billNew = billNew;
        this.sessionId = billNew.getId();
        this.prodNum = prodNum;
        this.mediaSession = mediaSession;
        this.callParameter = callParameter;
    }


    public boolean isPlayingVideo() {
        return status.equals(STATUS.PLAYING_VIDEO);
    }

    public boolean isPlayingAudioOrVideo() {
        return status.equals(STATUS.PLAYING_VIDEO) || status.equals(STATUS.PLAYING_AUDIO);
    }

    public int playAudio(VodInfo vodInfo) {
        String filePath = vodInfo.getAudioFile().split("\\.")[0];
        if (filePath.endsWith("_wb")) {
            filePath = filePath.substring(0, filePath.length() - 3);
        }
        logger.info("[{}] playAudio filePath:{}", sessionId, filePath);
        int audioDirection = 0;
        if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_ONLY) {
            audioDirection = 1;
        } else if (vodInfo.getDirection() == VodConstant.VOD.TYPE.RIGHT_ONLY) {
            audioDirection = 2;
        } else if (vodInfo.getDirection() == VodConstant.VOD.TYPE.LEFT_RIGHT_BOTH) {
            audioDirection = 3;
        }
        currentPlayTid = mediaSession.play(filePath, audioDirection, 0, resp -> {
        }, MediaManager.HTTP_SERVER_BASE_URL + VodConstant.VOD.PLAY_BACK_URL,true);
        updateStatus(STATUS.PLAYING_AUDIO, currentPlayTid);
        PlayData playData = new PlayData(System.currentTimeMillis(), 0, false, vodInfo.getVodActivateSource(), vodInfo.getResId(), vodInfo.getUniCode());
        VodPlayData vodPlayData = new VodPlayData(playData, vodInfo);
        vodPlayDataMap.put(String.valueOf(currentPlayTid), vodPlayData);
        return currentPlayTid;
    }

    private void updateStatus(STATUS newStatus, int playTid) {
        statusLock.lock();
        try {
            currentPlayTid = playTid;
            status = newStatus;
        } finally {
            statusLock.unlock();
        }
        logger.info("[{}] new status:{}", sessionId, status);
    }

    private void stopCurrentPlay(boolean toStopMediaPlay) {
        if (toStopMediaPlay) {
            if (status.equals(STATUS.PLAYING_VIDEO)) {
                mediaSession.stopVomVideo();
            } else if (status.equals(STATUS.PLAYING_AUDIO)) {
                mediaSession.stopAudioPlay();
            } else {
                logger.warn("[{}] current status:{}, is not playing audio or video", sessionId, status);
            }
        }
        updateStatus(STATUS.FREE, 0);
    }

    /**
     * 通知xmedia 播放视频
     */
    public int playVideo(MediaHook<CallBackEvent> hook, VodInfo vodInfo) {
        String audioPlayFile = vodInfo.getAudioFile();
        String completeAudioPlayFile = CommonUtil.addPostfixToAudioFile(vodInfo.getVodAudioCode(), audioPlayFile);
        // vodInfo.setAudioFile(completeAudioPlayFile);
        VomPlayParam vomPlayParam = new VomPlayParam();
        vomPlayParam.setAudioCode(mediaSession.getMedia());
        vomPlayParam.setVideoCode("H264");
        vomPlayParam.setDirection(vodInfo.getDirection());
        vomPlayParam.setVideoFile(vodInfo.getVideoFile());
        vomPlayParam.setPlayTimes(vodInfo.getPlayTimes());
        if (vodInfo.getCalledVideoMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY) {
            vomPlayParam.setAudioFile(completeAudioPlayFile);
        }

        if (vodInfo.getLeftVideoParam() != null) {
            vomPlayParam.setLeftVideoIp(vodInfo.getLeftVideoParam().getVideoIp());
            vomPlayParam.setLeftVideoPort(vodInfo.getLeftVideoParam().getVideoPort());
        }

        if (vodInfo.getRightVideoParam() != null) {
            vomPlayParam.setRightVideoIp(vodInfo.getRightVideoParam().getVideoIp());
            vomPlayParam.setRightVideoPort(vodInfo.getRightVideoParam().getVideoPort());
        }

        vomPlayParam.setVideoPayload(vodInfo.getVideoPayload());
        vomPlayParam.setxRate(640);
        vomPlayParam.setyRate(480);
        vomPlayParam.setBackUrl(vodInfo.getVodPlayCallBackUrl());

        if(vodInfo.getVideoFile().startsWith("tmp/")){
            vomPlayParam.setNoCache(1);
        }

        logger.info("[{}] playVideo vodPlayParam:{}", sessionId, vomPlayParam.toString());
        //如果接通后的放音较长，则插播之前先确认提示音是否已经结束，若未结束，先 stop 再插播视频
        if (mediaSession.isPickupAudioPlaying()) {
            logger.info("[{}] Audio play after pickup is not finished yet! Will stop play audio first.", sessionId);
            mediaSession.stopAudioPlay();
        }
        // billNew.increVideoInsertTimes(1);
        long startTime = System.currentTimeMillis();
        currentPlayTid = mediaSession.playVomVideo(vomPlayParam, hook);
        updateStatus(STATUS.PLAYING_VIDEO, currentPlayTid);

        PlayData playData = new PlayData(startTime, 0, true, vodInfo.getVodActivateSource(), vodInfo.getResId(), vodInfo.getUniCode());
        VodPlayData vodPlayData = new VodPlayData(playData, vodInfo);
        vodPlayDataMap.put(String.valueOf(currentPlayTid), vodPlayData);
        return currentPlayTid;

    }

    public void commit() {
        stopCurrentPlay(true);
        mediaSession.commit();
    }

    private VodInfo getVodInfo(int playTid) {
        VodPlayData vodPlayData = vodPlayDataMap.get(String.valueOf(playTid));
        VodInfo vodInfo = null;
        if (vodPlayData == null) {
            logger.error("[{}] getVodInfo vodPlayData is null.", sessionId);
        } else {
            vodInfo = vodPlayData.getVodInfo();
        }
        return vodInfo;
    }

    private PlayData getPlayData(int playTid) {
        VodPlayData vodPlayData = vodPlayDataMap.get(String.valueOf(playTid));
        PlayData playData = null;
        if (vodPlayData == null) {
            logger.error("[{}] getPlayData vodPlayData is null.", sessionId);
        } else {
            playData = vodPlayData.getPlayData();
        }
        return playData;
    }

    public boolean terminateCurrentPlay(int reasonCode, VodInfo vodInfo) {
        PlayData playData = getPlayData(vodInfo.getPlayTid());
        return processCurrentPlayEnd(reasonCode, playData, vodInfo);
    }

    public void processVodCallback(Map<String, Object> dataMap) {
        String code = (String) dataMap.getOrDefault("code", "");
        int tid = (int) dataMap.getOrDefault("tid", "0");
        VodInfo vodInfo = getVodInfo(tid);
        PlayData playData = getPlayData(tid);
        if (code.equals(VodConstant.VOD.VOD_EVENT_CODE.RESOURCE_NOT_FOUND)) {
            processCurrentPlayEnd(VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND, playData, vodInfo);
        } else {
            processCurrentPlayEnd(VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE, playData, vodInfo);
        }
    }

    private boolean processCurrentPlayEnd(int reasonCode, PlayData playData, VodInfo vodInfo) {
        logger.info("[{}] processCurrentPlayEnd start! reason:{}, playData:{}, vodInfo:{}", sessionId, reasonCode, playData, vodInfo);
        if (vodInfo == null) {
            logger.warn("[{}] processCurrentPlayEnd vodInfo is null.", sessionId);
            return false;
        }
        if (playData == null) {
            logger.info("[{}] processCurrentPlayEnd playData is null.", sessionId);
            //notifyVodEvent(reasonCode,playData,vodInfo);
            buildVodEvent(reasonCode, playData, vodInfo);
            return false;
        }

        try {
            //如果是视频播放结束，需要计算播放时长，推送事件
            updateBillInfo(reasonCode, playData);
            //notifyVodEvent(reasonCode,playData,vodInfo);
            buildVodEvent(reasonCode, playData, vodInfo);
            //结束视频播放
            if (reasonCode == VodConstant.VOD.REASON_CODE.END_BY_BYE ||
                    reasonCode == VodConstant.VOD.REASON_CODE.END_BY_REQUEST ||
                    //reasonCode == VodConstant.VOD.REASON_CODE.END_BY_FORCE ||
                       reasonCode == VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE) {
                if (currentPlayTid == vodInfo.getPlayTid()) {
                    stopCurrentPlay(true);
                } else if (reasonCode == VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND ||
                        reasonCode == VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE) {
                    stopCurrentPlay(false);
                } else {
                    logger.info("[{}] processCurrentPlayEnd currentPlayTid not equal to playTid.currentPlayTid:{},playTid:{}", sessionId, currentPlayTid, vodInfo.getPlayTid());
                }
            }
            logger.info("[{}] processCurrentPlayEnd end. playData:{}", sessionId, playData);
        } catch (Exception e) {
            logger.error("[{}] processCurrentPlayEnd exception", sessionId, e);
            return false;
        }
        return true;
    }

    private void buildVodEvent(int reasonCode, PlayData playData, VodInfo vodInfo) {
        if (!callParameter.isNeedVideoInsertStatus()) {
            logger.info("[{}] buildVodEvent no need to notify vod event, reasonCode:{}", sessionId);
            return;
        }

        //如果是视频播放结束，需要计算播放时长，推送事件
        String eventCode = "";
        switch (reasonCode) {
            case VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.TERMINAL_NOT_SUPPORT_VOD;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_IN_START:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.RESOURCE_NOT_FOUND;
                break;
            case VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SUCCESS;
                break;
            case VodConstant.VOD.REASON_CODE.END_BY_REQUEST:
            //case VodConstant.VOD.REASON_CODE.END_BY_FORCE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_FROM_HTTP;
                break;
            case VodConstant.VOD.REASON_CODE.END_BY_BYE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_BY_BYE;
                break;
            case VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.OTHER_EXCEPTION;
                break;
            case VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SWITCH_TO_AUDIO_VIA_PHONE;
                break;
            default:
                break;
        }

        if (playData != null && !playData.isVideo() && vodInfo.getCalledVideoMode() != VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY) {
            eventCode = VodConstant.VOD.VOD_EVENT_CODE.RETURN_TO_PLAY_AUDIO;
        }

        logger.info("[{}] notify event eventCode:{}", sessionId, eventCode);
        dataLock.lock();
        try {
            //推送插播事件
            if (StringUtils.isNotBlank(eventCode)) {
                fillVodEvent(eventCode, playData, vodInfo);
            }
        } catch (Exception e) {
            logger.error("[{}] send vod event exception.", sessionId, e);
        } finally {
            dataLock.unlock();
        }
    }

    private void fillVodEvent(String eventCode, PlayData playData, VodInfo vodInfo) throws Exception {
        String resId = "";
        String uniCode = "";
        //如果已经生成过 event，表示已经推送过了，则不需要继续生成
        if (vodInfo.getVodEventDo() != null) {
            return;
        }
        if (playData == null) {
            resId = vodInfo.getResId();
            uniCode = vodInfo.getUniCode();
        } else {
            resId = playData.getResId();
            uniCode = playData.getUniCode();
        }
        // VodEventDo vodEventDo = new VodEventDo(String.valueOf(sessionId), billNew.getBindId(), resId, uniCode);
        VodEventDo vodEventDo = new VodEventDo(String.valueOf(sessionId), "", resId, uniCode);
        vodEventDo.setResultCode(eventCode);
        vodEventDo.setTimeStamp(System.currentTimeMillis());
        // vodEventDo.setDuration(billNew.getVideoInsertDurationS());
        vodEventDo.setCompanyId(prodNum.getUserId());
        vodEventDo.setCustomerId(prodNum.getAppId());
        if (playData != null && !playData.isEventSent()) {
            playData.setEventSent(true);
            vodInfo.setVodEventDo(vodEventDo);
            logger.info("[{}] createVodEvent vodEventDo:{}", sessionId, vodEventDo);
        } else if (playData == null) {
            vodInfo.setVodEventDo(vodEventDo);
        }
    }

    private void updateBillInfo(int reasonCode, PlayData playData) {
        boolean isPlayVideo = playData.isVideo();
        dataLock.lock();
        try {
            if (isPlayVideo && !playData.isPlayTimeCalculated()) {
                if (reasonCode == VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND) {
                    //这个 case 要测一测，返回资源不存在的话，response 会不会携带
                    // if (billNew.getVideoInsertTimes() > 0) {
                    //     billNew.increVideoInsertTimes(-1);
                    //     logger.info("[{}] VodEventProcessor updateBillInfo vodTimes decrease new value:{}", sessionId, billNew.getVideoInsertTimes());
                    // }
                }
                if (reasonCode == VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE ||
                        reasonCode == VodConstant.VOD.REASON_CODE.END_BY_BYE ||
                        reasonCode == VodConstant.VOD.REASON_CODE.END_BY_REQUEST ||
                        /*reasonCode == VodConstant.VOD.REASON_CODE.END_BY_FORCE ||*/
                        reasonCode == VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE) {
                    // logger.info("[{}] VodEventProcessor updateBillInfo bill vod times:{}", sessionId, billNew.getVideoInsertTimes());
                    //计算插播时长
                    long startTime = playData.getStartTime();
                    long endTime = playData.getEndTime();
                    if (startTime != 0 && endTime == 0) {
                        endTime = System.currentTimeMillis();
                        // billNew.setVideoInsertDuration(startTime, endTime);
                        playData.setEndTime(endTime);
                    }
                    playData.setPlayTimeCalculated(true);
                }
            }
        } finally {
            dataLock.unlock();
        }

    }

    private void notifyVodEvent(int reasonCode, PlayData playData, VodInfo vodInfo) {
        if (vodInfo.getVodActivateSource() == VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM || !callParameter.isNeedVideoInsertStatus()) {
            logger.info("[{}] notifyVodEvent no need to notify vod event, reasonCode:{}", sessionId, reasonCode);
            return;
        }

        //如果是视频播放结束，需要计算播放时长，推送事件
        String eventCode = "";
        boolean needToSendEvent = false;
        switch (reasonCode) {
            case VodConstant.VOD.REASON_CODE.FAIL_IN_NEGOTIATION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                needToSendEvent = true;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_BECUASE_OF_NOT_SUPPORT:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.TERMINAL_NOT_SUPPORT_VOD;
                needToSendEvent = true;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_IN_START:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.NEGOTIATION_FAIL;
                needToSendEvent = true;
                break;
            case VodConstant.VOD.REASON_CODE.FAIL_BECAUSE_OF_RESID_NOT_FOUND:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.RESOURCE_NOT_FOUND;
                needToSendEvent = true;
                break;
            case VodConstant.VOD.REASON_CODE.END_BECAUSE_OF_COMPLETE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SUCCESS;
                break;
            case VodConstant.VOD.REASON_CODE.END_BY_REQUEST:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_FROM_HTTP;
                break;
            case VodConstant.VOD.REASON_CODE.END_BY_BYE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.STOP_BY_BYE;
                break;
            case VodConstant.VOD.REASON_CODE.OTHER_EXCEPTION:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.OTHER_EXCEPTION;
                needToSendEvent = true;
                break;
            case VodConstant.VOD.REASON_CODE.REMOTE_VIDEO_CLOSE:
                eventCode = VodConstant.VOD.VOD_EVENT_CODE.SWITCH_TO_AUDIO_VIA_PHONE;
                break;
            default:
                break;
        }
        logger.info("[{}] notify event eventCode:{}", sessionId, eventCode);
        dataLock.lock();
        try {
            //推送插播事件
            if (StringUtils.isNotBlank(eventCode)) {
                if (needToSendEvent || !playData.isEventSent()) {
                    sendEventMsg(eventCode, playData, vodInfo);
                }
            }
        } catch (Exception e) {
            logger.error("[{}] send vod event exception.", sessionId, e);
        } finally {
            dataLock.unlock();
        }
    }

    private String sendEventMsg(String eventCode, PlayData playData, VodInfo vodInfo) throws Exception {
        String resId = "";
        String uniCode = "";
        if (playData == null) {
            resId = vodInfo.getResId();
            uniCode = vodInfo.getUniCode();
        } else {
            resId = playData.getResId();
            uniCode = playData.getUniCode();
        }
        // VodEventDo vodEventDo = new VodEventDo(String.valueOf(sessionId), billNew.getBindId(), resId, uniCode);
        VodEventDo vodEventDo = new VodEventDo(String.valueOf(sessionId), "", resId, uniCode);
        vodEventDo.setResultCode(eventCode);
        vodEventDo.setTimeStamp(System.currentTimeMillis());
        // vodEventDo.setDuration(billNew.getVideoInsertDurationS());
        vodEventDo.setCompanyId(prodNum.getUserId());
        vodEventDo.setCustomerId(prodNum.getAppId());
        logger.info("[{}] notify event vodEventDo:{}", sessionId, vodEventDo);
        JSONObject eventObject = JSONObject.fromObject(vodEventDo);
        String eventMsg = eventObject.toString();
        // EventRocketMQService.getInstance().sendVodEventMsg(eventMsg, sessionId);

        if (playData != null) {
            playData.setEventSent(true);
        }
        return eventMsg;
    }

    @Data
    class VodPlayData {
        private PlayData playData;
        private VodInfo vodInfo;

        public VodPlayData(PlayData playData, VodInfo vodInfo) {
            this.playData = playData;
            this.vodInfo = vodInfo;
        }

        public PlayData getPlayData() {
            return playData;
        }

        public void setPlayData(PlayData playData) {
            this.playData = playData;
        }

        public VodInfo getVodInfo() {
            return vodInfo;
        }

        public void setVodInfo(VodInfo vodInfo) {
            this.vodInfo = vodInfo;
        }
    }

    @Data
    static class PlayData {
        private long startTime = 0;
        private long endTime = 0;
        private boolean isEventSent = false;
        private boolean isVideo = false;
        private boolean isPlayTimeCalculated = false;
        private int activateSource;
        private String resId;
        private String uniCode;

        public PlayData(long startTime, long endTime, boolean isVideo, int activateSource, String resId, String uniCode) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.isVideo = isVideo;
            this.activateSource = activateSource;
            this.resId = resId;
            this.uniCode = uniCode;
        }

        @Override
        public String toString() {
            return "PlayData{" +
                    "startTime=" + startTime +
                    ", endTime=" + endTime +
                    ", isEventSent=" + isEventSent +
                    ", isVideo=" + isVideo +
                    ", isPlayTimeCalculated=" + isPlayTimeCalculated +
                    ", activateSource=" + activateSource +
                    ", resId='" + resId + '\'' +
                    ", uniCode='" + uniCode + '\'' +
                    '}';
        }
    }


}
