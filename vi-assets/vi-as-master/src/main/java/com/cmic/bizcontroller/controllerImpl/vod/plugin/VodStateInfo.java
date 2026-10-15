package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.constant.VodConstant;

public class VodStateInfo {

    private int callerSupportWb = -1;
    private int calleeSupportWb = -1;
    private int callerContactSupprtVideo = -1;
    private int calleeContactSupportVideo = -1;
    private int leftNegotiation = VodConstant.VOD.NEGOTIATION.UNKNOW;
    private int rightNegotiation = VodConstant.VOD.NEGOTIATION.UNKNOW;


    public int getLeftNegotiation() {
        return leftNegotiation;
    }

    public void setLeftNegotiation(int leftNegotiation) {
        this.leftNegotiation = leftNegotiation;
    }

    public int getRightNegotiation() {
        return rightNegotiation;
    }

    public void setRightNegotiation(int rightNegotiation) {
        this.rightNegotiation = rightNegotiation;
    }

    public int getCallerSupportWb() {
        return callerSupportWb;
    }

    public void setCallerSupportWb(int callerSupportWb) {
        this.callerSupportWb = callerSupportWb;
    }

    public int getCalleeSupportWb() {
        return calleeSupportWb;
    }

    public void setCalleeSupportWb(int calleeSupportWb) {
        this.calleeSupportWb = calleeSupportWb;
    }

    public int getCallerContactSupprtVideo() {
        return callerContactSupprtVideo;
    }

    public void setCallerContactSupprtVideo(int callerContactSupprtVideo) {
        this.callerContactSupprtVideo = callerContactSupprtVideo;
    }

    public int getCalleeContactSupportVideo() {
        return calleeContactSupportVideo;
    }

    public void setCalleeContactSupportVideo(int calleeContactSupportVideo) {
        this.calleeContactSupportVideo = calleeContactSupportVideo;
    }
}
