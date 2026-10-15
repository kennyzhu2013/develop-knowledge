package com.cmic.recording.params;


public class MediaEnhanceParam {
    //音频资源： Live:Url、TTS:File:Path:Content
    private String audioSource;
   // private String audioCode;
    private String videoSource;
    private String videoCode;
    private String videoLevel;
    private String videoRate;
    private String leftVideoIp;

    private String rightVideoIp;

    private AudioConfig audioConfig;

    private String eventUrl;
    private String backUrl;
    private String dtmfEndSign;
    private String dtmfBackSign;
    private int dtmfMode;
    private int dtmfPType;
    private int dtmfExpectLen;
    private int dtmfTimeOut;
    private int dtmfIntervalTimeout;
    private int dtmfTimingMode;
    private int dtmfDirection;
    private int leftVideoPort;
    private int rightVideoPort;
    private int audioDirection;
    private int videoDirection;
    private int videoPType;
    private int playTimes;
    private int recMode;


    public String getAudioSource() {
        return audioSource;
    }

    public void setAudioSource(String audioSource) {
        this.audioSource = audioSource;
    }

    public String getVideoSource() {
        return videoSource;
    }

    public void setVideoSource(String videoSource) {
        this.videoSource = videoSource;
    }

    public String getVideoCode() {
        return videoCode;
    }

    public void setVideoCode(String videoCode) {
        this.videoCode = videoCode;
    }

    public String getVideoLevel() {
        return videoLevel;
    }

    public void setVideoLevel(String videoLevel) {
        this.videoLevel = videoLevel;
    }

    public String getVideoRate() {
        return videoRate;
    }

    public void setVideoRate(String videoRate) {
        this.videoRate = videoRate;
    }

    public String getLeftVideoIp() {
        return leftVideoIp;
    }

    public void setLeftVideoIp(String leftVideoIp) {
        this.leftVideoIp = leftVideoIp;
    }

    public String getRightVideoIp() {
        return rightVideoIp;
    }

    public void setRightVideoIp(String rightVideoIp) {
        this.rightVideoIp = rightVideoIp;
    }

    public String getEventUrl() {
        return eventUrl;
    }

    public void setEventUrl(String eventUrl) {
        this.eventUrl = eventUrl;
    }

    public String getBackUrl() {
        return backUrl;
    }

    public void setBackUrl(String backUrl) {
        this.backUrl = backUrl;
    }

    public int getLeftVideoPort() {
        return leftVideoPort;
    }

    public void setLeftVideoPort(int leftVideoPort) {
        this.leftVideoPort = leftVideoPort;
    }

    public int getRightVideoPort() {
        return rightVideoPort;
    }

    public void setRightVideoPort(int rightVideoPort) {
        this.rightVideoPort = rightVideoPort;
    }

    public int getAudioDirection() {
        return audioDirection;
    }

    public void setAudioDirection(int audioDirection) {
        this.audioDirection = audioDirection;
    }

    public int getVideoDirection() {
        return videoDirection;
    }

    public void setVideoDirection(int videoDirection) {
        this.videoDirection = videoDirection;
    }

    public int getVideoPType() {
        return videoPType;
    }

    public void setVideoPType(int videoPType) {
        this.videoPType = videoPType;
    }

    public int getPlayTimes() {
        return playTimes;
    }

    public void setPlayTimes(int playTimes) {
        this.playTimes = playTimes;
    }

    public String getDtmfEndSign() {
        return dtmfEndSign;
    }

    public void setDtmfEndSign(String dtmfEndSign) {
        this.dtmfEndSign = dtmfEndSign;
    }

    public String getDtmfBackSign() {
        return dtmfBackSign;
    }

    public void setDtmfBackSign(String dtmfBackSign) {
        this.dtmfBackSign = dtmfBackSign;
    }

    public int getDtmfMode() {
        return dtmfMode;
    }

    public void setDtmfMode(int dtmfMode) {
        this.dtmfMode = dtmfMode;
    }

    public int getDtmfPType() {
        return dtmfPType;
    }

    public void setDtmfPType(int dtmfPType) {
        this.dtmfPType = dtmfPType;
    }

    public int getDtmfExpectLen() {
        return dtmfExpectLen;
    }

    public void setDtmfExpectLen(int dtmfExpectLen) {
        this.dtmfExpectLen = dtmfExpectLen;
    }

    public int getDtmfTimeOut() {
        return dtmfTimeOut;
    }

    public void setDtmfTimeOut(int dtmfTimeOut) {
        this.dtmfTimeOut = dtmfTimeOut;
    }

    public int getDtmfIntervalTimeout() {
        return dtmfIntervalTimeout;
    }

    public void setDtmfIntervalTimeout(int dtmfIntervalTimeout) {
        this.dtmfIntervalTimeout = dtmfIntervalTimeout;
    }

    public int getDtmfTimingMode() {
        return dtmfTimingMode;
    }

    public void setDtmfTimingMode(int dtmfTimingMode) {
        this.dtmfTimingMode = dtmfTimingMode;
    }

    public int getDtmfDirection() {
        return dtmfDirection;
    }

    public void setDtmfDirection(int dtmfDirection) {
        this.dtmfDirection = dtmfDirection;
    }

    public AudioConfig getAudioConfig() {
        return audioConfig;
    }

    public void setAudioConfig(AudioConfig audioConfig) {
        this.audioConfig = audioConfig;
    }

    public int getRecMode() {
        return recMode;
    }

    public void setRecMode(int recMode) {
        this.recMode = recMode;
    }



    @Override
    public String toString() {
        return "MediaEnhanceParam{" +
                "audioSource='" + audioSource + '\'' +
                ", videoSource='" + videoSource + '\'' +
                ", videoCode='" + videoCode + '\'' +
                ", videoLevel='" + videoLevel + '\'' +
                ", videoRate='" + videoRate + '\'' +
                ", leftVideoIp='" + leftVideoIp + '\'' +
                ", rightVideoIp='" + rightVideoIp + '\'' +
                ", audioConfig=" + audioConfig +
                ", eventUrl='" + eventUrl + '\'' +
                ", backUrl='" + backUrl + '\'' +
                ", dtmfEndSign='" + dtmfEndSign + '\'' +
                ", dtmfBackSign='" + dtmfBackSign + '\'' +
                ", dtmfMode=" + dtmfMode +
                ", dtmfPType=" + dtmfPType +
                ", dtmfExpectLen=" + dtmfExpectLen +
                ", dtmfTimeOut=" + dtmfTimeOut +
                ", dtmfIntervalTimeout=" + dtmfIntervalTimeout +
                ", dtmfTimingMode=" + dtmfTimingMode +
                ", dtmfDirection=" + dtmfDirection +
                ", leftVideoPort=" + leftVideoPort +
                ", rightVideoPort=" + rightVideoPort +
                ", audioDirection=" + audioDirection +
                ", videoDirection=" + videoDirection +
                ", videoPType=" + videoPType +
                ", playTimes=" + playTimes +
                ", recMode=" + recMode +
                '}';
    }

    public static class AudioConfig {
        //指定外语方向：1、主叫；2、被叫
        private int foreignDirection;
        private String translateMode;


        public int getForeignDirection() {
            return foreignDirection;
        }

        public void setForeignDirection(int foreignDirection) {
            this.foreignDirection = foreignDirection;
        }

        public String getTranslateMode() {
            return translateMode;
        }

        public void setTranslateMode(String translateMode) {
            this.translateMode = translateMode;
        }


        @Override
        public String toString() {
            return "AudioConfig{" +
                    "foreignDirection=" + foreignDirection +
                    ", translateMode=" + translateMode +
                    '}';
        }
    }


}
