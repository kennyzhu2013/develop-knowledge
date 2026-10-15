package com.cmic.bizcontroller.controllerImpl.vod.transactionholder;

import com.cmic.BillNew;
import gov.nist.javax.sip.message.SIPRequest;

import javax.sip.message.Request;

public class TransactionHolderFactory {
    public static TransactionHolder createTransactionHolder(String type, SIPRequest request, BillNew billNew) {
        if (Request.INVITE.equalsIgnoreCase(type)) {
            return new InviteHolder(request, billNew);
        } else if (Request.UPDATE.equalsIgnoreCase(type)) {
            return new UpdateHolder(request, billNew);
        }
        return null;
    }

}
