package com.cmic.util;

import com.cmic.config.AbnormalBillAnalyserConfig;
import gov.nist.javax.sip.header.Require;
import gov.nist.javax.sip.header.ims.PAssertedIdentity;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.ToHeader;

public class AbnormalBillAnalyser {

    private static final Logger logger = LoggerFactory.getLogger(AbnormalBillAnalyser.class);

    private String sessionId;

    private String leftCallId = "";

    private String rightCallId = "";

    private long now;

    /**
     * case 1
     */
    private boolean isCase1Pass = true;

    private boolean isPrecondition = false;

    private long preconditionTime = 0L;
    
    private long updateAfterPreconditionTime = 0L;

    private static final long CASE1_THRESHOLD = 6 * 1000L;

    private boolean isSendReliProResp = false;

    /**
     * case 2
     */
    private boolean isInviteToCalled = false;

    private boolean isCase2Pass = true;

    private long inviteToCalledTime = 0L;

    private static final long CASE2_THRESHOLD = 6 * 1000L;

    /**
     * case 3
     */
    private boolean isCase3Pass = true;
    
    private boolean isRecv100or183 = false;

    private boolean isRecv180or200 = false;

    /**
     * case4
     */
    private boolean isCase4Pass = true;

    private boolean isRecv200Invite = false;

    private boolean isRecvAck = false;

    private Address a = null;
    
    private Address x1 = null;
    
    private Address x2 = null;

    private Address b = null;

    private String sendReInviteUser = ""; // caller/called

    /**
     * case5
     */
    private boolean isCase5Pass = true;

    private long lastRecvTime = 0L;

    private long maxDelayTime = 0L;

    private static final long CASE5_THRESHOLD = 6 * 1000L;

    /**
     * case6
     */
    private boolean isCase6Pass = true;

    private String initInvitePAI = "";

    private String initInviteFrom = "";

    /**
     * case7
     */
    private boolean isCase7Pass = true;

    private String resp200InvitePAI = "";

    private String resp200InviteTo = "";

    private static final String[] ABNORMAL_MESSAGE = new String[]{
            // case1
            // param: seconds
            "[{}],[abnormal bill],[case1] After the 183 response with header \"Requirement: precondition\", " +
                    "the caller did not send an UPDATE SIP signal " +
                    "within " + CASE1_THRESHOLD / 1000 + " seconds before the call was hung up.",

            // case2
            "[{}],[abnormal bill],[case2] Sent initial INVITE to the called number, " +
                    "but did not receive 18x (183, 180, etc.) or 200 INVITE returns.",

            // case3
            "[{}],[abnormal bill],[case3] An initial INVITE was sent to the called number, " +
                    "but it did not return 100 or 183, but instead returned 180 or 200 INVITE directly.",

            // case4
            // param: caller, called
            "[{}],[abnormal bill],[case4] After the call is connected, the {} number sends a REINVITE for " +
                    "renegotiation.",

            // case5
            // param: threshold second, max delay second
            "[{}],[abnormal bill],[case5] The SIP signal delay of the bill is greater than the threshold ("
                    + CASE5_THRESHOLD / 1000 + " seconds), "
                    + "with a maximum delay ({} milliseconds).",

            // case6
            "[{}],[abnormal bill],[case6] The initial INVITE P-Asserted-Identity ({}) " +
                    "and from ({}) of the caller are different.",

            // case7
            "[{}],[abnormal bill],[case7] The 200-INVITE P-Asserted-Identity ({}) " +
                    "and to ({}) of the called are different."
    };

    public enum Type {
        FIRST_INVITE,

        SEND_INITIAL_INVITE,

        SEND_RELIABLE_PROVISIONAL_RESPONSE,

        RECV_REQUEST,

        RECV_RESPONSE,

        TERMINATE
    }

    public void analyse(SIPMessage message, Type type, long now) {
        if (!AbnormalBillAnalyserConfig.isOpenAnalyse()) {
            return;
        }
        this.now = now;
        try {
            switch (type) {
                case FIRST_INVITE:
                    parseFirstInvite((SIPRequest) message);
                    break;
                case RECV_REQUEST:
                    SIPRequest request = (SIPRequest) message;
                    parseRequestForCase1(request);
                    parseRequestForCase4(request);
                    parseTimeForCase5();
                    break;
                case RECV_RESPONSE:
                    SIPResponse response = (SIPResponse) message;
                    parseResponseForCase1(response);
                    parseResponseForCase2(response);
                    parseResponseForCase3(response);
                    parseResponseForCase4(response);
                    parseTimeForCase5();
                    parseResponseForCase7(response);
                    break;
                case SEND_INITIAL_INVITE:
                    parseSendInitialInvite((SIPRequest) message);
                    break;
                case SEND_RELIABLE_PROVISIONAL_RESPONSE:
                    // 分机号模式，X主动发可靠183
                    parseSendReliProRespForCase1((SIPResponse) message);
                    break;
                case TERMINATE:
                    terminate();
                    break;
            }
        } catch (Exception e) {
            logger.error("[{}] error happened when abnormal bill analyse", sessionId, e);
        }
    }

    public AbnormalBillAnalyser(String sessionId) {
        this.sessionId = sessionId;
    }

    private void parseFirstInvite(SIPRequest sipRequest) {
        if (StringUtils.isEmpty(leftCallId) && sipRequest.getMethod().equals("INVITE")) {
            leftCallId = sipRequest.getCallId().getCallId();
            FromHeader from = sipRequest.getFrom();
            a = from.getAddress();
            x1 = sipRequest.getTo().getAddress();
            PAssertedIdentity pai = (PAssertedIdentity)sipRequest.getHeader(PAssertedIdentity.NAME);
            if (pai != null) {
                initInvitePAI = SipUtil.GetPhoneNumber(pai.getAddress());
                initInviteFrom = SipUtil.GetPhoneNumber(from.getAddress());
                if (!StringUtils.equals(initInvitePAI, initInviteFrom)) {
                    isCase6Pass = false;
                }
            }
        }
        lastRecvTime = now;
    }

    private void parseSendReliProRespForCase1(SIPResponse response) {
        if (isSendReliProResp) {
            return;
        }
        if (response.getStatusCode() == 183) {
            isSendReliProResp = true;
            if (SipUtil.isRequirePrecondition(response)) {
                isPrecondition = true;
                preconditionTime = now;
                isCase1Pass = false;
            }
        }
    }

    private void parseRequestForCase1(SIPRequest request) {
        if (isCase1Pass) {
            return;
        }
        // precondition之后，主叫发起UPDATE
        if (updateAfterPreconditionTime == 0L
                && request.getCallId().getCallId().equals(leftCallId)
                && request.getMethod().equals("UPDATE")) {
            updateAfterPreconditionTime = now;
            if (updateAfterPreconditionTime - preconditionTime < CASE1_THRESHOLD) {
                isCase1Pass = true;
            }
        }

        // CANCEL导致的提前结束
        if (request.getMethod().equals("CANCEL")) {
            if (now - preconditionTime < CASE1_THRESHOLD) {
                isCase1Pass = true;
            }
        }
    }

    private void parseResponseForCase1(SIPResponse response) {
        if (isPrecondition) {
            return;
        }
        if (SipUtil.isRequirePrecondition(response)) {
            isPrecondition = true;
            preconditionTime = now;
            isCase1Pass = false;
        }
    }

    private void parseResponseForCase2(SIPResponse response) {
        if (isCase2Pass) {
            return;
        }
        if (!response.getCallId().getCallId().equals(rightCallId)) {
            return;
        }
        // 在一定时间内收到指定响应，才能判为成功
        if (now -  inviteToCalledTime > CASE2_THRESHOLD) {
            return;
        }
        String method = response.getCSeq().getMethod();
        int statusCode = response.getStatusCode();
        if ("INVITE".equals(method)
                && (statusCode / 10 == 18 || statusCode == 200)) {
            isCase2Pass = true;
        }
        if ("CANCEL".equals(method) && statusCode == 200) {
            isCase2Pass = true;
        }
    }

    private void parseResponseForCase3(SIPResponse response) {
        if (isCase3Pass || isRecv180or200) {
            return;
        }
        if (!response.getCallId().getCallId().equals(rightCallId)) {
            return;
        }
        String method = response.getCSeq().getMethod();
        int statusCode = response.getStatusCode();
        if ("INVITE".equals(method) && (statusCode == 100 || statusCode == 183)) {
            isRecv100or183 = true;
            isCase3Pass = true;
        }
        if (!isRecv100or183 && "INVITE".equals(method) && (statusCode == 180 || statusCode == 200)) {
            isCase3Pass = false;
            isRecv180or200 = true;
        }
        // 未收到有问题的180 200-INVITE，但提前挂机CANCEL，也允许
        if (!isRecv180or200 && "CANCEL".equals(method) && statusCode == 200) {
            isCase3Pass = true;
        }
    }

    private void parseResponseForCase4(SIPResponse response) {
        if (isRecv200Invite) {
            return;
        }
        if (!response.getCallId().getCallId().equals(rightCallId)) {
            return;
        }
        if (response.getStatusCode() == 200 && response.getCSeq().getMethod().equals("INVITE")) {
            isRecv200Invite = true;
        }
    }

    private void parseResponseForCase7(SIPResponse response) {
        if (isCase7Pass) {
            if (response.getStatusCode() != 200 || !response.getCSeq().getMethod().equals(SIPRequest.INVITE)){
                return;
            }
            PAssertedIdentity pai = (PAssertedIdentity)response.getHeader(PAssertedIdentity.NAME);
            if (pai == null){
                return;
            }
            resp200InvitePAI = SipUtil.GetPhoneNumber(pai.getAddress());
            ToHeader to = response.getTo();
            resp200InviteTo = SipUtil.GetPhoneNumber(to.getAddress());
            if (!StringUtils.equals(resp200InvitePAI, resp200InviteTo)) {
                isCase7Pass = false;
            }
        }
    }

    private void parseRequestForCase4(SIPRequest request) {
        if (!isRecvAck) {
            if (!request.getCallId().getCallId().equals(leftCallId)) {
                return;
            }
            if (request.getMethod().equals("ACK")) {
                isRecvAck = true;
                isCase4Pass = true;
            }
        } else {
            if (!isCase4Pass) {
                return;
            }
            if (!request.getMethod().equals("INVITE")) {
                return;
            }
            if (request.getFrom().getAddress().equals(a)) {
                isCase4Pass = false;
                sendReInviteUser = "caller";
            } else if (request.getFrom().getAddress().equals(b)) {
                isCase4Pass = false;
                sendReInviteUser = "called";
            }
        }
    }

    private void parseTimeForCase5() {
        if (isRecvAck) {
            return;
        }
        if (now - lastRecvTime > CASE5_THRESHOLD) {
            isCase5Pass = false;
            maxDelayTime = Math.max(maxDelayTime, now - lastRecvTime);
        }
        lastRecvTime = now;
    }

    private void parseSendInitialInvite(SIPRequest request) {
        if (isInviteToCalled) {
            return;
        }
        if (StringUtils.isEmpty(rightCallId)) {
            String callId = request.getCallId().getCallId();
            if (!StringUtils.equals(leftCallId, callId)) {
                rightCallId = callId;
            }
        }
        x2 = request.getFrom().getAddress();
        b = request.getTo().getAddress();
        isInviteToCalled = true;
        isCase2Pass = false;
        isCase3Pass = false;
        inviteToCalledTime = now;
    }

    private void terminate() {
        if (!isCase1Pass && AbnormalBillAnalyserConfig.case1Switch()) {
            logger.warn(ABNORMAL_MESSAGE[0], sessionId);
        }
        if (!isCase2Pass && AbnormalBillAnalyserConfig.case2Switch()) {
            logger.warn(ABNORMAL_MESSAGE[1], sessionId);
        }
        if (!isCase3Pass && AbnormalBillAnalyserConfig.case3Switch()) {
            logger.warn(ABNORMAL_MESSAGE[2], sessionId);
        }
        if (!isCase4Pass && AbnormalBillAnalyserConfig.case4Switch()) {
            logger.warn(ABNORMAL_MESSAGE[3], sessionId, sendReInviteUser);
        }
        if (!isCase5Pass && AbnormalBillAnalyserConfig.case5Switch()) {
            logger.warn(ABNORMAL_MESSAGE[4], sessionId, maxDelayTime);
        }
        if (!isCase6Pass && AbnormalBillAnalyserConfig.case6Switch()) {
            logger.warn(ABNORMAL_MESSAGE[5], sessionId, initInvitePAI, initInviteFrom);
        }
        if (!isCase7Pass && AbnormalBillAnalyserConfig.case7Switch()) {
            logger.warn(ABNORMAL_MESSAGE[6], sessionId, resp200InvitePAI, resp200InviteTo);
        }
    }

    public String getCaseResult() {
        if (!isCase1Pass && AbnormalBillAnalyserConfig.case1Switch()) {
            return "case1";
        }
        if (!isCase2Pass && AbnormalBillAnalyserConfig.case2Switch()) {
            return "case2";
        }
        if (!isCase3Pass && AbnormalBillAnalyserConfig.case3Switch()) {
            return "case3";
        }
        if (!isCase4Pass && AbnormalBillAnalyserConfig.case4Switch()) {
            if (StringUtils.equals(sendReInviteUser, "caller")) {
                return "case4_caller";
            } else if (StringUtils.equals(sendReInviteUser, "called")) {
                return "case4_called";
            } else {
                return "case4";
            }
        }
        if (!isCase5Pass && AbnormalBillAnalyserConfig.case5Switch()) {
            return "case5";
        }
        if (!isCase6Pass && AbnormalBillAnalyserConfig.case6Switch()) {
            return "case6";
        }
        if (!isCase7Pass && AbnormalBillAnalyserConfig.case7Switch()) {
            return "case7";
        }
        return "";
    }
}
