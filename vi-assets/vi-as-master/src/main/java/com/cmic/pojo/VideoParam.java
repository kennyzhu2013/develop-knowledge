package com.cmic.pojo;

public class VideoParam {
    String videoIp;
    int videoPort;

    boolean isInactive = false;

    public VideoParam() {

    }

    public VideoParam(String videoIp, int videoPort) {
        this.videoIp = videoIp;
        this.videoPort = videoPort;
    }

    public VideoParam(String videoIp, int videoPort, boolean isInactive) {
        this.videoIp = videoIp;
        this.videoPort = videoPort;
        this.isInactive = isInactive;
    }

    public String getVideoIp() {
        return videoIp;
    }

    public void setVideoIp(String videoIp) {
        this.videoIp = videoIp;
    }

    public int getVideoPort() {
        return videoPort;
    }

    public void setVideoPort(int videoPort) {
        this.videoPort = videoPort;
    }

    public boolean isInactive() {
        return isInactive;
    }

    public void setInactive(boolean inactive) {
        isInactive = inactive;
    }

    public String toString() {
        return "videoIp:" + videoIp + " videoPort:" + videoPort + " isInactive:" + isInactive;
    }
}
