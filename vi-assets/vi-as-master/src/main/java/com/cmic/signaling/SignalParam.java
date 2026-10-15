package com.cmic.signaling;

import gov.nist.javax.sip.message.SIPMessage;

public class SignalParam {

    private String sessionId;
    private int companyId;
    private String appId;
    private int cityId;
    private int provinceId;

    private SIPMessage content;

    public SignalParam update(String sessionId,int companyId, String appId,int provinceId,int cityId){
        this.sessionId = sessionId;
        this.companyId = companyId;
        this.appId = appId;
        this.provinceId = provinceId;
        this.cityId = cityId;
        return this;
    }

    public SignalParam update(String sessionId,int companyId,String appId){
        this.sessionId = sessionId;
        this.companyId = companyId;
        this.appId = appId;
        return this;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SignalParam setSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public int getCompanyId() {
        return companyId;
    }

    public SignalParam setCompanyId(int companyId) {
        this.companyId = companyId;
        return this;
    }

    public String getAppId() {
        return appId;
    }

    public SignalParam setAppId(String appId) {
        this.appId = appId;
        return this;
    }

    public int getCityId() {
        return cityId;
    }

    public SignalParam setCityId(int cityId) {
        this.cityId = cityId;
        return this;
    }

    public int getProvinceId() {
        return provinceId;
    }

    public SignalParam setProvinceId(int provinceId) {
        this.provinceId = provinceId;
        return this;
    }

    public SIPMessage getContent() {
        return content;
    }

    public SignalParam setContent(SIPMessage content) {
        this.content = content;
        content.setHaveSignalPrinted(true);
        return this;
    }


    @Override
    public String toString() {
        return String.format("[%s][%d][%d][%d][%d][signaling][%s]", sessionId, companyId, appId, provinceId, cityId, content);
    }

    public String toLogString(int direction,int requestOrResonse) {
        return String.format("[%s][%d][%s][%d][%d][%d][%d][signaling][%s]", sessionId, companyId, appId, provinceId, cityId,direction, requestOrResonse,content);
    }

    public static void main(String[] args) {
        SignalParam param = new SignalParam();
        param.update("948098",1,"2",3,4);
        //param.setContent("message content");
        System.out.println(param.toString());
    }

}
