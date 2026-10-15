package com.cmic.util;

public class HistoryInfo {
    private String xmsNum;
    private String phoneNum;
    //    private String cause;
    private String text;
    String history_text;

    public HistoryInfo(String xmsNum, String phoneNum, String text) throws Exception {
        this.xmsNum = xmsNum;
        this.phoneNum = phoneNum;
//        this.cause = cause;
        this.text = text;
        buildHistory();
    }

    public String getHistory_text() {
        return history_text;
    }

    void buildHistory() {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<sip:+86");
//        stringBuffer.append("<");
        stringBuffer.append(phoneNum);
        stringBuffer.append("@bj.ims.mnc000.mcc460.3gppnetwork.org?reason=SIP%3Bcause%3D");
//        stringBuffer.append("@gd.ims.mnc000.mcc460.3gppnetwork.org");
        stringBuffer.append(text);
//        stringBuffer.append("%>;index=1");
        stringBuffer.append(">;index=1");
        stringBuffer.append(",");
        stringBuffer.append("<sip:+86");
//        stringBuffer.append("<");
        stringBuffer.append(xmsNum);
        stringBuffer.append("@bj.ims.mnc000.mcc460.3gppnetwork.org;cause=");
        stringBuffer.append(text);
        stringBuffer.append(">;index=1.1");
        history_text = stringBuffer.toString();
    }
}
