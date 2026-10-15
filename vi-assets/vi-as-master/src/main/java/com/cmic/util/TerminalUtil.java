package com.cmic.util;

import com.cmic.BillNew;
import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 终端识别相关util
 * <p>
 * Author: luohuanfa
 * Date: 2023/6/28
 */
public class TerminalUtil {
    private static Logger logger = LoggerFactory.getLogger(TerminalUtil.class);

    public static void recordTerminateBrand(Leg leg, SIPMessage message, BillNew billNew) throws Exception {
        try {
            // 刷新左腿手机品牌状态
            if (leg.isLeft()) {
                if (!(message instanceof SIPRequest)) {
                    return;
                }
                SIPRequest request = (SIPRequest) message;
                if (!request.getMethod().equals(SIPRequest.INVITE)) {
                    return;
                }
                String tac8 = getTac8(request);
                if (!StringUtils.isBlank(tac8)) {
                    logger.info("[{}] [xdtmf] [imei:{}] [from:{}] [{}]", billNew.getId(), tac8, billNew.getCallerNumber(), "N");
                    // bill.setCallerImei(tac8);
                }
                // if (bill.getCallerSystem() != null) {
                //     return;
                // }
                String userAgent = SipUtil.getUserAgent(request);
                if (StringUtils.isBlank(userAgent)) {
                    return;
                }
                boolean isIOS = userAgent.contains("iOS") || userAgent.contains("iPhone");
                // bill.setCallerSystem(isIOS ? 1 : 0);
                return;
            }
            // 刷新右腿的手机品牌
            if (!(message instanceof SIPResponse)) {
                return;
            }
            SIPResponse response = (SIPResponse) message;
            if (!response.getCSeq().getMethod().equals(SIPRequest.INVITE)) {
                return;
            }
            String tac8 = getTac8(response);
            if (!StringUtils.isBlank(tac8)) {
                logger.info("[{}] [xdtmf] [imei:{}] [to:{}] [{}]", billNew.getId(), tac8, billNew.getCalleeNumber(), "N");
                // bill.setCalleeImei(tac8);
            }
            // if (bill.getCalleeSystem() != null) {
            //     return;
            // }
            String userAgent = SipUtil.getUserAgent(response);
            if (StringUtils.isBlank(userAgent)) {
                return;
            }
            boolean isIOS = userAgent.contains("iOS") || userAgent.contains("iPhone");
            // bill.setCalleeSystem(isIOS ? 1 : 0);
        } catch (Exception e) {
            logger.error("[{}], [TerminalUtil-recordTerminateBrand], record error", billNew.getId());
        }
    }

    private static String getTac8(SIPMessage message) {
        String tac8 = "";
        Contact contactHeader = message.getContactHeader();
        if (contactHeader != null) {
            String contactStr = contactHeader.toString();
            int i = contactStr.indexOf("urn:gsma:imei:");
            if (i != -1 && (contactStr.length() > i + 22)) {
                tac8 = contactStr.substring(i + 14, i + 22);
            }
        }
        return tac8;
    }

}
