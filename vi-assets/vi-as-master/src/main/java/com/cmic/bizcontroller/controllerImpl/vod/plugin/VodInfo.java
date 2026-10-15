package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.pojo.VideoParam;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

public class VodInfo {
    private int direction;       // 通话方向：1-主叫方向 2-被叫方向 3-主被叫双向;
    private int calledVideoMode; // 视频插播播放模式：1-音视频播放；2-仅视频，通话语音透传；3-语音插播
    private String videoFile;
    private String audioFile;
    private String resId;
    private String uniCode;
    private int playTid;         //若本次插播请求成功播放（语音或视频），则 playTid 为本次播放的id，否则为0
    //private boolean isAggressiveVod = false;

    private int aggressiveVod;
    private boolean isVodEventSent = false;

    SIPResponse lastRightResponse;
    SIPRequest lastLeftRequest;
    private String vodPlayCallBackUrl;
    private int vodActivateSource;
    private VideoParam leftVideoParam;
    private VideoParam rightVideoParam;
    private int videoPayload;
    private VodEventDo vodEventDo;

    //播放模式：1-音视频播放; 2-仅视频，通话语音透传; 3-仅播放音频
    private int mode;

    private String busCode;

    private int playTimes;

    private String vodAudioCode;


    public VideoParam getLeftVideoParam() {
        return leftVideoParam;
    }

    public void setLeftVideoParam(VideoParam leftVideoParam) {
        this.leftVideoParam = leftVideoParam;
    }

    public VideoParam getRightVideoParam() {
        return rightVideoParam;
    }

    public void setRightVideoParam(VideoParam rightVideoParam) {
        this.rightVideoParam = rightVideoParam;
    }

    public int getVideoPayload() {
        return videoPayload;
    }

    public void setVideoPayload(int videoPayload) {
        this.videoPayload = videoPayload;
    }

    public int getPlayTid() {
        return playTid;
    }

    public void setPlayTid(int playTid) {
        this.playTid = playTid;
    }

    public int getCalledVideoMode() {
        return calledVideoMode;
    }

    public void setCalledVideoMode(int calledVideoMode) {
        this.calledVideoMode = calledVideoMode;
    }

    public int getVodActivateSource() {
        return vodActivateSource;
    }

    public void setVodActivateSource(int vodActivateSource) {
        this.vodActivateSource = vodActivateSource;
    }

    public String getResId() {
        return resId;
    }

    public void setResId(String resId) {
        this.resId = resId;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public String getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(String videoFile) {
        this.videoFile = videoFile;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public SIPResponse getLastRightResponse() {
        return lastRightResponse;
    }

    public void setLastRightResponse(SIPResponse lastRightResponse) {
        this.lastRightResponse = lastRightResponse;
    }

    public SIPRequest getLastLeftRequest() {
        return lastLeftRequest;
    }

    public void setLastLeftRequest(SIPRequest lastLeftRequest) {
        this.lastLeftRequest = lastLeftRequest;
    }

    public String getUniCode() {
        return uniCode;
    }

    public void setUniCode(String uniCode) {
        this.uniCode = uniCode;
    }

    public String getVodPlayCallBackUrl() {
        return vodPlayCallBackUrl;
    }

    public void setVodPlayCallBackUrl(String vodPlayCallBackUrl) {
        this.vodPlayCallBackUrl = vodPlayCallBackUrl;
    }

//    public boolean isAggressiveVod() {
//        return isAggressiveVod;
//    }
//
//    public void setAggressiveVod(boolean aggressiveVod) {
//        isAggressiveVod = aggressiveVod;
//    }

    public VodEventDo getVodEventDo() {
        return vodEventDo;
    }

    public void setVodEventDo(VodEventDo vodEventDo) {
        this.vodEventDo = vodEventDo;
    }

    public boolean isVodEventSent() {
        return isVodEventSent;
    }

    public void setVodEventSent(boolean vodEventSent) {
        isVodEventSent = vodEventSent;
    }

    public int getPlayTimes() {
        return playTimes;
    }

    public void setPlayTimes(int playTimes) {
        this.playTimes = playTimes;
    }

    public String getVodAudioCode() {
        return vodAudioCode;
    }

    public void setVodAudioCode(String vodAudioCode) {
        this.vodAudioCode = vodAudioCode;
    }

    public int getAggressiveVod() {
        return aggressiveVod;
    }

    public void setAggressiveVod(int aggressiveVod) {
        this.aggressiveVod = aggressiveVod;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getBusCode() {
        return busCode;
    }

    public void setBusCode(String busCode) {
        this.busCode = busCode;
    }

    @Override
    public String toString() {
        return "VodInfo{" +
                "direction=" + direction +
                ", calledVideoMode=" + calledVideoMode +
                ", videoFile='" + videoFile + '\'' +
                ", audioFile='" + audioFile + '\'' +
                ", resId='" + resId + '\'' +
                ", uniCode='" + uniCode + '\'' +
                ", playTid=" + playTid +
                ", aggressiveVod=" + aggressiveVod +
                ", isVodEventSent=" + isVodEventSent +
                //", lastRightResponse=" + lastRightResponse +
                //", lastLeftRequest=" + lastLeftRequest +
                ", vodPlayCallBackUrl='" + vodPlayCallBackUrl + '\'' +
                ", vodActivateSource=" + vodActivateSource +
                ", leftVideoParam=" + leftVideoParam +
                ", rightVideoParam=" + rightVideoParam +
                ", videoPayload=" + videoPayload +
                ", vodEventDo=" + vodEventDo +
                ", mode=" + mode +
                ", playTimes=" + playTimes +
                ", vodAudioCode='" + vodAudioCode + '\'' +
                ", busCode='" + busCode + '\'' +
                '}';
    }
}
