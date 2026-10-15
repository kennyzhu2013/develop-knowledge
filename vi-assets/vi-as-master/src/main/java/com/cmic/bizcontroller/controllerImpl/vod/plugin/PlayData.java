package com.cmic.bizcontroller.controllerImpl.vod.plugin;

public class PlayData {

    private long startTime = 0;
    private long endTime = 0;
    private boolean isEventSent = false;
    private boolean isVideo = false;
    private boolean isPlayTimeCalculated = false;

    public PlayData(long startTime, long endTime, boolean isVideo) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.isVideo = isVideo;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isEventSent() {
        return isEventSent;
    }

    public void setEventSent(boolean eventSent) {
        isEventSent = eventSent;
    }

    public boolean isVideo() {
        return isVideo;
    }

    public void setVideo(boolean video) {
        isVideo = video;
    }

    public boolean isPlayTimeCalculated() {
        return isPlayTimeCalculated;
    }

    public void setPlayTimeCalculated(boolean playTimeCalculated) {
        isPlayTimeCalculated = playTimeCalculated;
    }

    @Override
    public String toString() {
        return "PlayData{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", isEventSent=" + isEventSent +
                ", isVideo=" + isVideo +
                ", isPlayTimeCalculated=" + isPlayTimeCalculated +
                '}';
    }
}
