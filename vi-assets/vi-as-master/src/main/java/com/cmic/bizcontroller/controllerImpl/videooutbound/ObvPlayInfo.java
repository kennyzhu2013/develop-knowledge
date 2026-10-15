package com.cmic.bizcontroller.controllerImpl.videooutbound;

import com.cmic.bizcontroller.controllerImpl.vod.plugin.VodEventDo;

public class ObvPlayInfo {
    private String videoPlayCode;           //视频插播的视频资源编码
    private String videoFile;               //视频文件的全路径
    private String audioFile;               //音频文件的全路径
    private int direction;                  //通话方向：1-主叫方向； 2-被叫方向； 3-主被叫双向
    private int mode;                       //播放模式：1-音视频播放；2-仅视频，通话语音透传
    //private int afterPlay;                  //0-挂断通话   1-主被叫正常通话
    private boolean isInteractive;          //插播时是否可透传主被叫语音
    private boolean isAggressiveVod = false;        //是否尽量播放视频
    private String uniCode;
    private VodEventDo vodEventDo;
    private boolean isVodEventSent = false;
    private int playTimes;

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

    public String getVideoPlayCode() {
        return videoPlayCode;
    }

    public void setVideoPlayCode(String videoPlayCode) {
        this.videoPlayCode = videoPlayCode;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public boolean isAggressiveVod() {
        return isAggressiveVod;
    }

    public void setAggressiveVod(boolean aggressiveVod) {
        isAggressiveVod = aggressiveVod;
    }

    public boolean isInteractive() {
        return isInteractive;
    }

    public void setInteractive(boolean interactive) {
        isInteractive = interactive;
    }


    public String getUniCode() {
        return uniCode;
    }

    public void setUniCode(String uniCode) {
        this.uniCode = uniCode;
    }

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

    @Override
    public String toString() {
        return "ObvPlayInfo{" +
                "videoPlayCode='" + videoPlayCode + '\'' +
                ", videoFile='" + videoFile + '\'' +
                ", audioFile='" + audioFile + '\'' +
                ", direction=" + direction +
                ", mode=" + mode +
                ", isInteractive=" + isInteractive +
                ", isAggressiveVod=" + isAggressiveVod +
                ", uniCode='" + uniCode + '\'' +
                ", vodEventDo=" + vodEventDo +
                ", isVodEventSent=" + isVodEventSent +
                '}';
    }
}
