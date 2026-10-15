package com.cmic.util;

import com.cmic.config.AppConfig;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.SipProvider;
import javax.sip.address.Hop;
import java.net.InetAddress;

/**
 * 信令发送util类
 * <p>
 * Author: lyh
 * Date: 2026/6/08
 * <p>
 */
public class SipMessageSendUtil {
    private static final Logger logger = LoggerFactory.getLogger(SipMessageSendUtil.class);

    public static void sendSipMessageWithoutDialog(SipProvider sipProvider, SIPMessage sipMessage) {
        String callId = sipMessage.getCallId().getCallId();
        if (sipMessage instanceof SIPRequest) {
            SIPRequest request = (SIPRequest) sipMessage;
            Hop hop;
            InetAddress hopAddr;
            try {
                hop = ((SipStackImpl) sipProvider.getSipStack()).getRouter(request).getNextHop(request);
                if (hop == null) {
                    logger.error("{}, hop null, ignore it", callId);
                    return;
                }
                if (isSelfRoutingMessage(hop.getHost(), hop.getPort(), callId)) {
                    logger.error("callId:{} , send to self, ignore", callId);
                    return;
                }
                sipProvider.sendRequest(request);
            } catch (Exception e) {
                logger.error("{}, parsing hop error", callId);
                return;
            }
            return;
        }

        SIPResponse response = (SIPResponse) sipMessage;

        Via via = response.getTopmostVia();
        if (via == null) {
            logger.error("{}, via null, ignore it", callId);
            return;
        }
        String transport = via.getTransport();
        String host = via.getReceived();
        if (host == null) {
            host = via.getHost();
        }
        // Symmetric nat support
        int port = via.getRPort();
        if (port == -1) {
            port = via.getPort();
            if (port == -1) {
                if (transport.equalsIgnoreCase("TLS") || transport.equalsIgnoreCase("SCTP-TLS")) {
                    port = 5061;
                } else {
                    port = 5060;
                }
            }
        }
        try {
            if (isSelfRoutingMessage(host, port, callId)) {
                logger.error("callId:{} , send to self, ignore", callId);
                return;
            }
            sipProvider.sendResponse(response);
        } catch (Exception e) {
            logger.error("{}, sendSipMessageWithoutDialog response error", callId);
            throw new RuntimeException(e);
        }
    }


    private static boolean isSelfRoutingMessage(String host, int port, String callId) {
        String[] ipAndPort = AppConfig.getAsListen().split(":");
        String ip = ipAndPort[0];
        return host.contains(ip);
    }
}
