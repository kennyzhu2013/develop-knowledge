package com.cmic.bizcontroller.controllerImpl.vod.plugin;

public class VodEventDo {

    private String callId;
    private String bindId;
    private String resId;
    private String uniCode;
    private long timeStamp;
    private String resultCode;
    private int duration;
    private int companyId;
    private String customerId;


    public VodEventDo(String callId, String bindId, String resId, String uniCode) {
        this.callId = callId;
        this.bindId = bindId;
        this.resId = resId;
        this.uniCode = uniCode;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getBindId() {
        return bindId;
    }

    public void setBindId(String bindId) {
        this.bindId = bindId;
    }

    public String getResId() {
        return resId;
    }

    public void setResId(String resId) {
        this.resId = resId;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getUniCode() {
        return uniCode;
    }

    public void setUniCode(String uniCode) {
        this.uniCode = uniCode;
    }

    @Override
    public String toString() {
        return "VodEventDo{" +
                "callId='" + callId + '\'' +
                ", bindId='" + bindId + '\'' +
                ", resId='" + resId + '\'' +
                ", timeStamp=" + timeStamp +
                ", resultCode='" + resultCode + '\'' +
                ", duration=" + duration +
                ", companyId=" + companyId +
                ", customerId=" + customerId +
                ", uniCode=" + uniCode +
                '}';
    }
}
