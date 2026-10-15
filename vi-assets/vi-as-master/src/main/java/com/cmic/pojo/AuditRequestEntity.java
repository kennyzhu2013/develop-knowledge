package com.cmic.pojo;

import com.cmic.util.AnalyseLog;

/**
 * 策略运营平台的请求对象
 * <p>
 * Author: luohuanfa
 * Date: 2024/4/19
 */
public class AuditRequestEntity {

    private String callerNo;
    private String calleeNo;
    private String secretNo;
    private String displayNo;
    private String companyId;
    private String customerId;
    private String provinceId;
    private String cityId;
    private String location;
    private String asId;
    private AnalyseLog analyseLog;


    public AuditRequestEntity() {
    }


    public String getCallerNo() {
        return callerNo;
    }

    public void setCallerNo(String callerNo) {
        this.callerNo = callerNo;
    }

    public String getCalleeNo() {
        return calleeNo;
    }

    public void setCalleeNo(String calleeNo) {
        this.calleeNo = calleeNo;
    }

    public String getSecretNo() {
        return secretNo;
    }

    public void setSecretNo(String secretNo) {
        this.secretNo = secretNo;
    }

    public String getDisplayNo() {
        return displayNo;
    }

    public void setDisplayNo(String displayNo) {
        this.displayNo = displayNo;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProvinceId() {
        return provinceId;
    }

    public void setProvinceId(String provinceId) {
        this.provinceId = provinceId;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getAsId() {
        return asId;
    }

    public void setAsId(String asId) {
        this.asId = asId;
    }

    public AnalyseLog getAnalyseLog() {
        return analyseLog;
    }

    public void setAnalyseLog(AnalyseLog analyseLog) {
        this.analyseLog = analyseLog;
    }
}
