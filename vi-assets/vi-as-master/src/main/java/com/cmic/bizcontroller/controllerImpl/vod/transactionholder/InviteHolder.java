package com.cmic.bizcontroller.controllerImpl.vod.transactionholder;

import com.cmic.BillNew;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;

import javax.sip.message.Request;

public class InviteHolder extends TransactionHolder {
    public String getName() {
        return this.name;
    }

    public InviteHolder(SIPRequest request, BillNew billNew) {
        this.status = VodConstant.TRANSACTION_INVITE.STARTED;
        name = Request.INVITE;
        this.request = request;
        this.billNew = billNew;
    }

    public void switchStatus(SIPMessage sipMessage) throws Exception {
        if (sipMessage instanceof SIPResponse) {
            int statusCode = ((SIPResponse) sipMessage).getStatusCode();
            if (statusCode > 299 || statusCode < 200) {
                this.status = VodConstant.TRANSACTION_INVITE.ENDED;
            } else {
                this.status = VodConstant.TRANSACTION_INVITE.RESPONSED;
            }
            logger.info("[{}] InviteHolder switchStatus statusCode:{},new status:{}", billNew.getId(), statusCode, this.status);
        } else {
            SIPRequest request = (SIPRequest) sipMessage;
            long cseq = request.getCSeqHeader().getSeqNumber();
            if (request.getMethod().equalsIgnoreCase(Request.ACK) && cseq == getSeqNum()) {
                this.status = VodConstant.TRANSACTION_INVITE.ACKED;
            }
            logger.info("[{}] InviteHolder switchStatus cseq:{}, method:{}, new status:{}", billNew.getId(), cseq, request.getMethod(), this.status);
        }

    }

    public boolean isCompleted() {
        if (this.status == VodConstant.TRANSACTION_INVITE.ACKED ||
                this.status == VodConstant.TRANSACTION_INVITE.ENDED) {
            return true;
        }
        return false;
    }

    @Override
    public long getSeqNum() {
        return request != null ? request.getCSeqHeader().getSeqNumber() : 0;
    }
}
