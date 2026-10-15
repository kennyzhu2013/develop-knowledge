package com.cmic.state;

public class TransferStateJudge {
    private boolean isIfReject;
    private boolean isIfBusy;
    private boolean isIfNoResponse;
    private boolean isIfNoRinging;
    private int noResponseTime = 30;


    public TransferStateJudge(boolean isIfReject, boolean isIFBusy, boolean isIFNoResponse, boolean isIFNoRinging, int noResponseTime) {
        this.isIfReject = isIfReject;
        this.isIfBusy = isIFBusy;
        this.isIfNoRinging = isIFNoRinging;
        this.isIfNoResponse = isIFNoResponse;
        if (noResponseTime != 0) {
            this.noResponseTime = noResponseTime;
        }
    }

    //判断是否可以顺振
    public boolean judgeIfTransfer(PreciseCallStateBase preciseCallState) {
        //有传被叫忙条件下
        if (isIfBusy) {
            //被叫忙条件下如果收到181则触发顺振
            if (preciseCallState.hasRecv181Msg)
                return true;
        }
        //有传被叫拒接条件下
        if (isIfReject) {
            //如果此时收到q850是17、21，或者statusCode是486或者603也是触发顺振
            if (preciseCallState.getCause() == 17 || preciseCallState.getStatusCode() == 486 || preciseCallState.getCause() == 21 || preciseCallState.getStatusCode() == 603)
                return true;
        }
        //无振铃的情况：   不是上述两种情况的同时，也没有正常接通。
        if (isIfNoRinging) {
            //无振铃，但同时q850是17、21,statusCode是486、603则不触发顺振（能进来这里，说明isIfReject是false，收到上诉几个条件不能触发顺振），其他条件触发顺振
            if (preciseCallState.hasRecv181Msg || preciseCallState.getCause() == 17 || preciseCallState.getStatusCode() == 486 || preciseCallState.getCause() == 21 || preciseCallState.getStatusCode() == 603)
                return false;
            if (preciseCallState.getCause() != 0 || preciseCallState.getStatusCode() != 0)
                return true;
        }
        return false;
    }

    //是否可因超时顺振
    public boolean canTransferByTimeOut(PreciseCallStateBase preciseCallState) {
        //收到181 并且 条件没有传被叫忙条件，则不可顺振
        if (preciseCallState.hasRecv181Msg && !isIfBusy) {
            return false;
        }
        //如果q850为17,21 或者statusCode为486、603，如果没有传拒接条件，则不可顺振
        if (preciseCallState.getCause() == 17 || preciseCallState.getStatusCode() == 486 || preciseCallState.getCause() == 21 || preciseCallState.getStatusCode() == 603) {
            if (!isIfReject)
                return false;

        } else if (preciseCallState.getCause() != 0 || preciseCallState.getStatusCode() != 0) {
            //如果此时q850和statuscode不为空也非上述的值
            //判断是否传了振铃条件，没传就返回false
            if (!isIfNoRinging)
                return false;
        } else {
            //如果没有q850和statuscode，传了noResponse则返回true，否则返回false
            if (isIfNoResponse)
                return true;
        }
        return false;
    }

    public int getNoResponseTime() {
        return noResponseTime;
    }

    @Override
    public String toString() {
        return "TransferStateJudge{" +
                "isIfReject=" + isIfReject +
                ", isIfBusy=" + isIfBusy +
                ", isIfNoResponse=" + isIfNoResponse +
                ", isIfNoRinging=" + isIfNoRinging +
                ", noResponseTime=" + noResponseTime +
                '}';
    }
}
