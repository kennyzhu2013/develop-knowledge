package com.cmic.recording.params;

public class VomPlayParam {
    //音频文件路径
    private String audioFile;

    //音频编码：amr-wb、amr-wb-octet
    private String audioCode;

    //视频文件路径
    private String videoFile;

    //视频编码：H264
    private String videoCode;

    //视频载荷类型
    private int videoPayload;

    //主叫视频Ip
    private String leftVideoIp;

    //主叫视频端口
    private int leftVideoPort;

    //被叫视频Ip
    private String rightVideoIp;

    //被叫视频端口
    private int rightVideoPort;

    //播放方向：1、主叫；2、被叫；3、主被叫
    private int direction;

    //视频分辨率，长
    private int xRate;

    //视频分辨率，宽
    private int yRate;

    //播放回调接口
    private String backUrl;

    private int playTimes;

    private int noCache;

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public String getAudioCode() {
        return audioCode;
    }

    public void setAudioCode(String audioCode) {
        this.audioCode = audioCode;
    }

    public String getVideoFile() {
        return videoFile;
    }

    public void setVideoFile(String videoFile) {
        this.videoFile = videoFile;
    }

    public String getVideoCode() {
        return videoCode;
    }

    public void setVideoCode(String videoCode) {
        this.videoCode = videoCode;
    }

    public int getVideoPayload() {
        return videoPayload;
    }

    public void setVideoPayload(int videoPayload) {
        this.videoPayload = videoPayload;
    }

    public String getLeftVideoIp() {
        return leftVideoIp;
    }

    public void setLeftVideoIp(String leftVideoIp) {
        this.leftVideoIp = leftVideoIp;
    }

    public int getLeftVideoPort() {
        return leftVideoPort;
    }

    public void setLeftVideoPort(int leftVideoPort) {
        this.leftVideoPort = leftVideoPort;
    }

    public String getRightVideoIp() {
        return rightVideoIp;
    }

    public void setRightVideoIp(String rightVideoIp) {
        this.rightVideoIp = rightVideoIp;
    }

    public int getRightVideoPort() {
        return rightVideoPort;
    }

    public void setRightVideoPort(int rightVideoPort) {
        this.rightVideoPort = rightVideoPort;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getxRate() {
        return xRate;
    }

    public void setxRate(int xRate) {
        this.xRate = xRate;
    }

    public int getyRate() {
        return yRate;
    }

    public void setyRate(int yRate) {
        this.yRate = yRate;
    }

    public String getBackUrl() {
        return backUrl;
    }

    public void setBackUrl(String backUrl) {
        this.backUrl = backUrl;
    }


    public int getPlayTimes() {
        return playTimes;
    }

    public void setPlayTimes(int playTimes) {
        this.playTimes = playTimes;
    }

    public int getNoCache() {
        return noCache;
    }

    public void setNoCache(int noCache) {
        this.noCache = noCache;
    }

    @Override
    public String toString() {
        return "VomPlayParam{" +
                "audioFile='" + audioFile + '\'' +
                ", audioCode='" + audioCode + '\'' +
                ", videoFile='" + videoFile + '\'' +
                ", videoCode='" + videoCode + '\'' +
                ", videoPayload=" + videoPayload +
                ", leftVideoIp='" + leftVideoIp + '\'' +
                ", leftVideoPort=" + leftVideoPort +
                ", rightVideoIp='" + rightVideoIp + '\'' +
                ", rightVideoPort=" + rightVideoPort +
                ", direction=" + direction +
                ", xRate=" + xRate +
                ", yRate=" + yRate +
                ", backUrl='" + backUrl + '\'' +
                ", noCache='" + noCache + '\'' +
                '}';
    }
}
