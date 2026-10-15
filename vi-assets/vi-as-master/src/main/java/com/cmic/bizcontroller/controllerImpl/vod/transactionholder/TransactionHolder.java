package com.cmic.bizcontroller.controllerImpl.vod.transactionholder;

import com.cmic.BillNew;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TransactionHolder {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected String name;
    protected SIPRequest request;
    protected int status;
    protected BillNew billNew;

    public abstract String getName();

    public abstract void switchStatus(SIPMessage sipMessage) throws Exception;

    public abstract boolean isCompleted();

    public abstract long getSeqNum();

    public void setBill(BillNew billNew) {
        this.billNew = billNew;
    }
}
