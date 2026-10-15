package com.cmic.bizcontroller.controllerImpl.vod.transactionholder;

import com.cmic.BillNew;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.message.Request;

public class UpdateHolder extends TransactionHolder {

    public String getName() {
        return this.name;
    }

    public UpdateHolder(SIPRequest request, BillNew billNew) {
        this.status = VodConstant.TRANSACTION_UPDATE.STARTED;
        this.name = Request.UPDATE;
        this.request = request;
        this.billNew = billNew;
    }

    public void switchStatus(SIPMessage sipMessage) throws Exception {
        this.status = VodConstant.TRANSACTION_UPDATE.RESPONSED;
        logger.info("[{}] InviteHolder switchStatus new status:{}", billNew.getId(), this.status);
    }

    public boolean isCompleted() {
        return this.status == VodConstant.TRANSACTION_UPDATE.RESPONSED;

    }

    @Override
    public long getSeqNum() {
        return request != null ? request.getCSeqHeader().getSeqNumber() : 0;
    }
}
