package com.cmic.business.businessparam;

public class VodParam {
    private String videoPlayCode;                 // 视频插播的视频资源编码
    private String calledVideoPlayFile;           // 视频插播的视频播放文件
    private String calledAudioPlayFile;           //视频插播的音频播放文件
    private int calledVideoDirection;          // 通话方向：1-主叫方向 2-被叫方向 3-主被叫双向; 当calledVideoPlayMode的值为2时，此参数必须传2
    private int calledVideoMode;               // 视频插播播放模式：1-音视频播放；2-仅视频，通话语音透传
    private int aggressiveVod;           // 是否尽量播放视频
    private String resId;
    private String uniCode;
    private int playTimes;

    public String getCalledAudioPlayFile() {
        return calledAudioPlayFile;
    }

    public void setCalledAudioPlayFile(String calledAudioPlayFile) {
        this.calledAudioPlayFile = calledAudioPlayFile;
    }

    public String getCalledVideoPlayFile() {
        return calledVideoPlayFile;
    }

    public void setCalledVideoPlayFile(String calledVideoPlayFile) {
        this.calledVideoPlayFile = calledVideoPlayFile;
    }

    public int getCalledVideoDirection() {
        return calledVideoDirection;
    }

    public void setCalledVideoDirection(int calledVideoDirection) {
        this.calledVideoDirection = calledVideoDirection;
    }

    public int getCalledVideoMode() {
        return calledVideoMode;
    }

    public void setCalledVideoMode(int calledVideoMode) {
        this.calledVideoMode = calledVideoMode;
    }

    public String getVideoPlayCode() {
        return videoPlayCode;
    }

    public void setVideoPlayCode(String videoPlayCode) {
        this.videoPlayCode = videoPlayCode;
    }

    public boolean isAggressiveVodForObv() {
        return aggressiveVod == 1;
    }

    public void setAggressiveVod(int aggressiveVod) {
        this.aggressiveVod = aggressiveVod;
    }

    public int getAggressiveVod() {
        return aggressiveVod;
    }

    public String getResId() {
        return resId;
    }

    public void setResId(String resId) {
        this.resId = resId;
    }

    public String getUniCode() {
        return uniCode;
    }

    public void setUniCode(String uniCode) {
        this.uniCode = uniCode;
    }

    public int getPlayTimes() {
        return playTimes;
    }

    public void setPlayTimes(int playTimes) {
        this.playTimes = playTimes;
    }

    @Override
    public String toString() {
        return "VodParam{" +
                "videoPlayCode='" + videoPlayCode + '\'' +
                ", calledVideoPlayFile='" + calledVideoPlayFile + '\'' +
                ", calledAudioPlayFile='" + calledAudioPlayFile + '\'' +
                ", calledVideoDirection=" + calledVideoDirection +
                ", calledVideoMode=" + calledVideoMode +
                ", aggressiveVod=" + aggressiveVod +
                ", resId='" + resId + '\'' +
                ", uniCode='" + uniCode + '\'' +
                '}';
    }
}
