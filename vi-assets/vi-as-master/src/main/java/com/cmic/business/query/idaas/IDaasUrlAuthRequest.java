package com.cmic.business.query.idaas;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class IDaasUrlAuthRequest {
    private String bizExtraData;
    private String businessType;
    private String cancelButton;
    private String channelAppId;
    private String credibleTime;
    private String encryptNonce;
    private String isDisplayCertNo;
    private String isMegviiFace;
    private String isQRCodeBase64;
    private String nonce;
    private String otherNotifyUrl;
    private String phoneCheckType;
    private String phoneNo;
    private String referer;
    private String returnUrl;
    private String sendSmsFlag;
    private String smsTemplateId;
    private String msgLevel;
    private long timestamp;
    private String topButton;
    private String ywSendSmsFlag;


    @Override
    public String toString() {
        ToStringBuilder toStringBuilder = new ToStringBuilder(this, ToStringStyle.JSON_STYLE);
        if (StringUtils.isNotBlank(bizExtraData)) {
            toStringBuilder.append("bizExtraData", bizExtraData);
        }

        if (StringUtils.isNotBlank(businessType)) {
            toStringBuilder.append("businessType", businessType);
        }

        if (StringUtils.isNotBlank(cancelButton)) {
            toStringBuilder.append("cancelButton", cancelButton);
        }

        if (StringUtils.isNotBlank(channelAppId)) {
            toStringBuilder.append("channelAppId", channelAppId);
        }

        if (StringUtils.isNotBlank(credibleTime)) {
            toStringBuilder.append("credibleTime", credibleTime);
        }

        if (StringUtils.isNotBlank(encryptNonce)) {
            toStringBuilder.append("encryptNonce", encryptNonce);
        }

        if (StringUtils.isNotBlank(isDisplayCertNo)) {
            toStringBuilder.append("isDisplayCertNo", isDisplayCertNo);
        }

        if (StringUtils.isNotBlank(isMegviiFace)) {
            toStringBuilder.append("isMegviiFace", isMegviiFace);
        }

        if (StringUtils.isNotBlank(isQRCodeBase64)) {
            toStringBuilder.append("isQRCodeBase64", isQRCodeBase64);
        }

        if (StringUtils.isNotBlank(msgLevel)) {
            toStringBuilder.append("msgLevel", msgLevel);
        }

        if (StringUtils.isNotBlank(nonce)) {
            toStringBuilder.append("nonce", nonce);
        }

        if (StringUtils.isNotBlank(otherNotifyUrl)) {
            toStringBuilder.append("otherNotifyUrl", otherNotifyUrl);
        }

        if (StringUtils.isNotBlank(phoneCheckType)) {
            toStringBuilder.append("phoneCheckType", phoneCheckType);
        }

        if (StringUtils.isNotBlank(phoneNo)) {
            toStringBuilder.append("phoneNo", phoneNo);
        }

        if (StringUtils.isNotBlank(referer)) {
            toStringBuilder.append("referer", referer);
        }

        if (StringUtils.isNotBlank(returnUrl)) {
            toStringBuilder.append("returnUrl", returnUrl);
        }

        if (StringUtils.isNotBlank(sendSmsFlag)) {
            toStringBuilder.append("sendSmsFlag", sendSmsFlag);
        }

        if (StringUtils.isNotBlank(smsTemplateId)) {
            toStringBuilder.append("smsTemplateId", smsTemplateId);
        }

        toStringBuilder.append("timestamp", timestamp);

        if (StringUtils.isNotBlank(topButton)) {
            toStringBuilder.append("topButton", topButton);
        }
        if (StringUtils.isNotBlank(ywSendSmsFlag)) {
            toStringBuilder.append("ywSendSmsFlag", ywSendSmsFlag);
        }
        return toStringBuilder.toString();
    }

    public String getBizExtraData() {
        return bizExtraData;
    }

    public void setBizExtraData(String bizExtraData) {
        this.bizExtraData = bizExtraData;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getCancelButton() {
        return cancelButton;
    }

    public void setCancelButton(String cancelButton) {
        this.cancelButton = cancelButton;
    }

    public String getChannelAppId() {
        return channelAppId;
    }

    public void setChannelAppId(String channelAppId) {
        this.channelAppId = channelAppId;
    }

    public String getCredibleTime() {
        return credibleTime;
    }

    public void setCredibleTime(String credibleTime) {
        this.credibleTime = credibleTime;
    }

    public String getIsDisplayCertNo() {
        return isDisplayCertNo;
    }

    public void setIsDisplayCertNo(String isDisplayCertNo) {
        this.isDisplayCertNo = isDisplayCertNo;
    }

    public String getIsMegviiFace() {
        return isMegviiFace;
    }

    public void setIsMegviiFace(String isMegviiFace) {
        this.isMegviiFace = isMegviiFace;
    }

    public String getIsQRCodeBase64() {
        return isQRCodeBase64;
    }

    public void setIsQRCodeBase64(String isQRCodeBase64) {
        this.isQRCodeBase64 = isQRCodeBase64;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getOtherNotifyUrl() {
        return otherNotifyUrl;
    }

    public void setOtherNotifyUrl(String otherNotifyUrl) {
        this.otherNotifyUrl = otherNotifyUrl;
    }

    public String getPhoneCheckType() {
        return phoneCheckType;
    }

    public void setPhoneCheckType(String phoneCheckType) {
        this.phoneCheckType = phoneCheckType;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getReferer() {
        return referer;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getSendSmsFlag() {
        return sendSmsFlag;
    }

    public void setSendSmsFlag(String sendSmsFlag) {
        this.sendSmsFlag = sendSmsFlag;
    }

    public String getSmsTemplateId() {
        return smsTemplateId;
    }

    public void setSmsTemplateId(String smsTemplateId) {
        this.smsTemplateId = smsTemplateId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTopButton() {
        return topButton;
    }

    public void setTopButton(String topButton) {
        this.topButton = topButton;
    }

    public String getEncryptNonce() {
        return encryptNonce;
    }

    public void setEncryptNonce(String encryptNonce) {
        this.encryptNonce = encryptNonce;
    }

    public String getMsgLevel() {
        return msgLevel;
    }

    public void setMsgLevel(String msgLevel) {
        this.msgLevel = msgLevel;
    }

    public String getYwSendSmsFlag() {
        return ywSendSmsFlag;
    }

    public void setYwSendSmsFlag(String ywSendSmsFlag) {
        this.ywSendSmsFlag = ywSendSmsFlag;
    }
}
