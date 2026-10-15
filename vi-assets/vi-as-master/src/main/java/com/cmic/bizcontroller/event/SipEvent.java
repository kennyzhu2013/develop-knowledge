package com.cmic.bizcontroller.event;

public class SipEvent {

    boolean isDealWith = false;

    boolean isTransfer = false;

    public void setDealWith(boolean dealWith) {
        isDealWith = dealWith;
    }

    public void setTransfer(boolean transfer) {
        isTransfer = transfer;
    }

    public boolean isTransfer() {
        return isTransfer;
    }

    public boolean isDealWith() {
        return isDealWith;
    }


}
