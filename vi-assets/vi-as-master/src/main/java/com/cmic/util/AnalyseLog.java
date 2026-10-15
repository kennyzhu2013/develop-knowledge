package com.cmic.util;


import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.header.ims.PChargingVector;
import gov.nist.javax.sip.header.ims.PrivacyHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.parser.StringMsgParser;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.Header;
import java.text.ParseException;


public class AnalyseLog {

    public static final Logger logger = LoggerFactory.getLogger("analyselog");
    public static final Logger loggerNorm = LoggerFactory.getLogger(AnalyseLog.class);

    private static final String CALL_TYPE_CALLER = "0";
    private static final String CALL_TYPE_CALLEE = "1";

    private boolean havePrintLeftImei;
    private String leftImei;
    private boolean havePrintLeftCallId;
    private boolean havePrintLeftCellId;
    private String leftCellId;
    private boolean havePrintRightImei;
    private String rightImei;
    private boolean havePrintRightCallId;
    private boolean havePrintRightCellId;
    private String rightCellId;
    private boolean havePrintLeftPrintPChargingVector;
    private String leftPChargingVector;
    private String leftHistoryInfoIndex1Phone;
    private String leftPrivacy;
    private String leftUserAgent;

    private String rightUserAgent;

    private boolean havePrintRightUserAgent;
    private boolean havePrintRightPrintPChargingVector;
    private String rightPChargingVector;
    private Long sendLeft200InvTime;
    private Long recvLeftACKTime;
    private Long sendRightFirstUpdateTime;
    private Long recvRightFirst200UpdateTime;

    private String earlyPlayCodes;
    private String answeredPlayCodes;
    private String customerId;
    private String companyId;

    public enum MessageTypeEnum {
        FIRST_INVITE, RECV_REQ, RECV_RESP, SEND_REQ, SEND_RESP, TERMINATE;
    }

    /**
     * 记录日志分析信息
     *
     * @param message
     * @param messageType 0: 初始invite， 1：recv-请求 2：recv-响应 3: send-请求 4: send-响应
     */
    public static void logMsg(Leg leg, Controller controller, SIPMessage message, MessageTypeEnum messageType) {
        try {
            if (controller == null){
                return;
            }
            String billId = String.valueOf(controller.sessionId);
            switch (messageType) {
                case FIRST_INVITE:
                    logRecvFirstInvite(controller, (SIPRequest) message, billId);
                    break;
                case RECV_REQ:
                    if (message instanceof SIPRequest) {
                        logRecvReq(leg, controller, (SIPRequest) message, billId);
                    }
                    break;
                case RECV_RESP:
                    if (message instanceof SIPResponse) {
                        logRecvResp(leg, controller, (SIPResponse) message, billId);
                    }
                    break;
                case SEND_REQ:
                    if (message instanceof SIPRequest) {
                        logSendReq(leg, controller, (SIPRequest) message, billId);
                    }
                    break;
                case SEND_RESP:
                    if (message instanceof SIPResponse) {
                        logSendResp(leg, controller, (SIPResponse) message, billId);
                    }
                    break;
                case TERMINATE:
                    logTerminate(billId, controller);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.error("[{}] error happened when log analyze!", controller.sessionId, e);
        }
    }

    private static void logTerminate(String billId, Controller controller) {
        AnalyseLog controllerAnalyseLogFlag = controller.getAnalyseLogFlag();
        logger.info("[early_play_codes] [{}] [{}] [{}] [{}]", billId, controllerAnalyseLogFlag.getCompanyId(),
                controllerAnalyseLogFlag.getCustomerId(), controllerAnalyseLogFlag.getEarlyPlayCodes());
        logger.info("[answered_play_codes] [{}] [{}] [{}] [{}]", billId, controllerAnalyseLogFlag.getCompanyId(),
                controllerAnalyseLogFlag.getCustomerId(), controller.getAnalyseLogFlag().getAnsweredPlayCodes());
    }

    private static void logSendResp(Leg leg, Controller controller, SIPResponse message, String billId) {
        String method = message.getCSeq().getMethod();
        int statusCode = message.getStatusCode();
        if (!leg.isLeft()) {
            return;
        }
        if (controller.getAnalyseLogFlag().getSendLeft200InvTime() == null && method.equals(SIPRequest.INVITE) && statusCode == 200) {
            controller.getAnalyseLogFlag().setSendLeft200InvTime(System.currentTimeMillis());
        }
    }

    private static void logSendReq(Leg leg, Controller controller, SIPRequest message, String billId) {
        String method = message.getCSeq().getMethod();
        if (leg.isLeft()) {
            return;
        }
        if (controller.getAnalyseLogFlag().getSendRightFirstUpdateTime() == null && method.equals(SIPRequest.UPDATE)) {
            controller.getAnalyseLogFlag().setSendRightFirstUpdateTime(System.currentTimeMillis());
        }
    }

    private static void logRecvFirstInvite(Controller controller, SIPRequest request, String billId) {
        String fromPhone = getFromPhone(request);
        ImmutablePair<Boolean, String> imeiPair = printImei(request, billId, CALL_TYPE_CALLER, fromPhone);
        if (StringUtils.isNotBlank(imeiPair.right)){
            controller.getAnalyseLogFlag().setLeftImei(imeiPair.right);
        }
        ImmutablePair<Boolean, String> cellIdPair = printCellId(request, billId, CALL_TYPE_CALLER);
        if (StringUtils.isNotBlank(cellIdPair.right)){
            controller.getAnalyseLogFlag().setLeftCellId(cellIdPair.right);
        }
        printCallId(request, billId, CALL_TYPE_CALLER);
        printPChargingVector(request, billId, CALL_TYPE_CALLER);
        ImmutablePair<Boolean, String> historyInfoPair = printHistoryInfo(request, billId, CALL_TYPE_CALLER);
        if (StringUtils.isNotBlank(historyInfoPair.right)){
            controller.getAnalyseLogFlag().setLeftHistoryInfoIndex1Phone(historyInfoPair.right);
        }
        ImmutablePair<Boolean, String> privacyPair = printPrivacy(request, billId, CALL_TYPE_CALLER);
        if (StringUtils.isNotBlank(privacyPair.right)){
            controller.getAnalyseLogFlag().setLeftPrivacy(privacyPair.right);
        }
        ImmutablePair<Boolean, String> userAgentPair = printUserAgent(request, billId, CALL_TYPE_CALLER);
        if (StringUtils.isNotBlank(userAgentPair.right)){
            controller.getAnalyseLogFlag().setLeftUserAgent(userAgentPair.right);
        }

    }

    private static ImmutablePair<Boolean, String> printPrivacy(SIPMessage request, String billId, String callTypeCaller) {
        String privacyStr;
        try {
            Header privacy = request.getHeader("Privacy");
            if (privacy == null){
                return ImmutablePair.of(false, "");
            }
            PrivacyHeader privacyHeader = (PrivacyHeader) privacy;
            privacyStr = privacyHeader.getPrivacy();
            logger.info("[privacy] [{}] [{}] [{}]", billId, privacyStr, callTypeCaller);
        }catch (Exception e){
            loggerNorm.error("[privacy], {}, error happened!", billId, e );
            return ImmutablePair.of(false, "");
        }
        return ImmutablePair.of(true, privacyStr);
    }

    private static ImmutablePair<Boolean, String> printUserAgent(SIPMessage message, String billId, String callTypeCaller) {
        String userAgent = "";
        try {
            userAgent = SipUtil.getUserAgent(message);
            if (StringUtils.isBlank(userAgent)){
                return ImmutablePair.of(false, "");
            }
            logger.info("[userAgent] [{}] [{}] [{}]", billId, userAgent, callTypeCaller);
        }catch (Exception e){
            loggerNorm.error("[userAgent], {}, error happened!", billId, e );
            return ImmutablePair.of(false, "");
        }
        return ImmutablePair.of(true, userAgent);
    }

    private static void logRecvResp(Leg leg, Controller controller, SIPResponse response, String billId) {
        if (leg == null || leg.isLeft()) {
            return;
        }
        String method = response.getCSeq().getMethod();
        int statusCode = response.getStatusCode();
        if (!controller.getAnalyseLogFlag().isHavePrintRightImei()) {
            String toPhone = getToPhone(response);
            boolean havePrintTac8 = printImei(response, billId, CALL_TYPE_CALLEE, toPhone).left;
            if (havePrintTac8) {
                controller.getAnalyseLogFlag().setHavePrintRightImei(true);
            }
        }
        if (!controller.getAnalyseLogFlag().isHavePrintRightCellId()) {
            boolean havePrintCellId = printCellId(response, billId, CALL_TYPE_CALLEE).left;
            if (havePrintCellId) {
                controller.getAnalyseLogFlag().setHavePrintRightCellId(true);
            }
        }
        if (!controller.getAnalyseLogFlag().isHavePrintRightCallId()) {
            boolean havePrintCallId = printCallId(response, billId, CALL_TYPE_CALLEE);
            if (havePrintCallId) {
                controller.getAnalyseLogFlag().setHavePrintRightCallId(true);
            }
        }
        if (!controller.getAnalyseLogFlag().isHavePrintRightPrintPChargingVector()) {
            boolean havePrintPChargingVector = printPChargingVector(response, billId, CALL_TYPE_CALLEE);
            if (havePrintPChargingVector) {
                controller.getAnalyseLogFlag().setHavePrintRightPrintPChargingVector(true);
            }
        }
        if (controller.getAnalyseLogFlag().getRecvRightFirst200UpdateTime() == null && method.equals(SIPRequest.UPDATE) && statusCode == 200) {
            long endTime = System.currentTimeMillis();
            controller.getAnalyseLogFlag().setRecvRightFirst200UpdateTime(endTime);
            Long startTime = controller.getAnalyseLogFlag().getSendRightFirstUpdateTime();
            if (startTime == null) {
                logger.info("[right-200-update-delay] [{}] [{}]", billId, "noRight200UpdTime");
            } else {
                logger.info("[right-200-update-delay] [{}] [{}]", billId, endTime - startTime);
            }
        }
        if(!controller.getAnalyseLogFlag().isHavePrintRightUserAgent() && !leg.isLeft() && statusCode == 183){
            ImmutablePair<Boolean, String> userAgentPair = printUserAgent(response, billId, CALL_TYPE_CALLEE);
            if (StringUtils.isNotBlank(userAgentPair.right)){
                controller.getAnalyseLogFlag().setRightUserAgent(userAgentPair.right);
                controller.getAnalyseLogFlag().setHavePrintRightUserAgent(true);
            }
        }
    }

    private static void logRecvReq(Leg leg, Controller controller, SIPRequest request, String billId) {
        String method = request.getCSeq().getMethod();
        if (!leg.isLeft()) {
            return;
        }
        if (controller.getAnalyseLogFlag().getRecvLeftACKTime() == null && method.equals(SIPRequest.ACK)) {
            controller.getAnalyseLogFlag().setRecvLeftACKTime(System.currentTimeMillis());
            if (controller.getAnalyseLogFlag().getSendLeft200InvTime() == null) {
                logger.info("[left-ack-delay] [{}] [{}]", billId, "noLeft200InvTime");
            } else {
                logger.info("[left-ack-delay] [{}] [{}]", billId, controller.getAnalyseLogFlag().getRecvLeftACKTime() - controller.getAnalyseLogFlag().getSendLeft200InvTime());
            }
        }
    }

    public static void logAITalkerMsg(String billId,String msg, int companyId, String customerId){
        logger.info("[AI Talker] [{}] [{}] [{}] [{}]", billId, msg, companyId, customerId);
    }

    private static ImmutablePair<Boolean, String> printImei(SIPMessage message, String billId, String callType, String phone) {
        String imei = "";
        Contact contactHeader = message.getContactHeader();
        if (contactHeader != null) {
            String contactStr = contactHeader.toString();
            int i = contactStr.indexOf("urn:gsma:imei:");
            if (i != -1 && (contactStr.length() > i + 22)) {
                int imeiEnd = contactStr.indexOf(">", i + 14);
                if (imeiEnd != -1 && imeiEnd > i + 14){
                    imei = contactStr.substring(i + 14, imeiEnd);
                }
            }
        }
        if (!StringUtils.isBlank(imei)) {
            String singlingType = getSinglingType(message);
            logger.info("[imei] [{}] [{}] [{}] [{}] [{}]", billId, phone, imei, singlingType, callType);
            return ImmutablePair.of(true, imei);
        }
        return ImmutablePair.of(false, imei);
    }
    private static ImmutablePair<Boolean, String> printCellId(SIPMessage message, String billId, String callType) {
        SIPHeader header = (SIPHeader) message.getHeader("P-Access-Network-Info");
        String nettype = "";
        String cellid = "";
        String ueip = "";
        if (header != null) {
            String paniStr = header.getHeaderValue();
            int i = paniStr.indexOf("utran-cell-id-3gpp");
            if (i != -1) {
                String[] tokenArray = paniStr.split(";");
                nettype = tokenArray[0];
                for (String token : tokenArray) {
                    String[] tokenKeyValue = token.trim().split("=");
                    String tokenFirst = tokenKeyValue[0].trim();
                    if ("utran-cell-id-3gpp".equals(tokenFirst) && tokenKeyValue.length > 1) {
                        cellid = tokenKeyValue[1].trim();
                        continue;
                    }
                    if ("ue-ip".equals(tokenFirst) && tokenKeyValue.length > 1) {
                        String secondValue = tokenKeyValue[1].trim();
                        ueip = secondValue.substring(1, secondValue.length() - 1);
                        continue;
                    }
                }
            }
        }
        String singlingType = getSinglingType(message);
        if (StringUtils.isNotBlank(cellid)) {
            logger.info("[cellid] [{}] [{}] [{}] [{}] [{}] [{}]", billId, cellid, nettype, ueip, singlingType, callType);
            return ImmutablePair.of(true, cellid);
        }
        return ImmutablePair.of(false, cellid);
    }

    private static boolean printCallId(SIPMessage message, String billId, String callType) {
        String callId = ((SIPHeader) message.getCallId()).getHeaderValue();
        String signallingType = getSinglingType(message);
        logger.info("[callid] [{}] [{}] [{}] [{}]", billId, callId, signallingType, callType);
        return true;
    }

    private static boolean printPChargingVector(SIPMessage message, String billId, String callType) {
        PChargingVector header = (PChargingVector) message.getHeader("P-Charging-Vector");
        if (header == null) {
            return false;
        }
        String signallingType = getSinglingType(message);
        logger.info("[P-Charging-Vector] [{}] [{}] [{}] [{}] [{}]", billId, header.getOriginatingIOI(), header.getTerminatingIOI(), signallingType, callType);
        return true;
    }

    private static ImmutablePair<Boolean, String> printHistoryInfo(SIPMessage message, String billId, String callType) {
        String historyInfoIndex1PhoneNum = SipUtil.getHistoryInfoIndex1PhoneNum(message);
        if (StringUtils.isBlank(historyInfoIndex1PhoneNum)){
            return ImmutablePair.of(false, historyInfoIndex1PhoneNum);
        }
        logger.info("[history-info] [{}] [{}] [{}]", billId, historyInfoIndex1PhoneNum, callType);
        return ImmutablePair.of(true, historyInfoIndex1PhoneNum);
    }

    public static void printBusinessLog(String businessTypeName, String format, Object... businessArge){
        logger.info("[" + businessTypeName + "] " + format, businessArge);
    }

    public static void logAudioCode(String billId,String audioCode,int calledRecordIndex){
        logger.info("[audio-code] [{}] [{}] [{}]", billId, audioCode,calledRecordIndex);
    }

    private static String getToPhone(SIPMessage message) {
        URI uri = message.getTo().getAddress().getURI();
        return getPure11NumberWithout0or86(uri);
    }

    private static String getFromPhone(SIPMessage message) {
        URI uri = message.getFrom().getAddress().getURI();
        return getPure11NumberWithout0or86(uri);
    }

    private static String getSinglingType(SIPMessage message) {
        if (message instanceof SIPRequest) {
            SIPRequest request = (SIPRequest) message;
            return request.getMethod();
        } else {
            SIPResponse response = (SIPResponse) message;
            String method = response.getCSeq().getMethod();
            return response.getStatusCode() + "-" + method;
        }
    }

    public static String getPure11NumberWithout0or86(URI uri) {
        String number = null;
        if (uri.isSipURI()) {
            number = ((SipURI) uri).getUser().trim();
        } else {
            number = ((TelURL) uri).getPhoneNumber().trim();
        }

        int posofcolon = number.indexOf(";");
        if (posofcolon > -1) {
            number = number.substring(0, posofcolon);
        }

        String pureNumber = number.replaceAll("[^0-9]", "");
        return getPure11NumberWithout0or86(pureNumber);
    }

    public static String getPure11NumberWithout0or86(String number) {
        if (number == null) {
            return "";
        }
        if (number.startsWith("sip:") || number.startsWith("tel:")) {
            number = number.substring(4);
        }
        if (number.startsWith("+")) {
            number = number.substring(1);
        }
        if (number.startsWith("00")) {
            number = number.substring(2);
        }
        if (number.startsWith("0")) {
            number = number.substring(1);
        }
        if (number.startsWith("86")) {
            number = number.substring(2);
        }
        if (number.startsWith("0")) {
            number = number.substring(1);
        }
        return number;
    }


    public boolean isHavePrintLeftImei() {
        return havePrintLeftImei;
    }

    public void setHavePrintLeftImei(boolean havePrintLeftImei) {
        this.havePrintLeftImei = havePrintLeftImei;
    }

    public boolean isHavePrintLeftCallId() {
        return havePrintLeftCallId;
    }

    public void setHavePrintLeftCallId(boolean havePrintLeftCallId) {
        this.havePrintLeftCallId = havePrintLeftCallId;
    }

    public boolean isHavePrintLeftCellId() {
        return havePrintLeftCellId;
    }

    public void setHavePrintLeftCellId(boolean havePrintLeftCellId) {
        this.havePrintLeftCellId = havePrintLeftCellId;
    }

    public boolean isHavePrintRightImei() {
        return havePrintRightImei;
    }

    public void setHavePrintRightImei(boolean havePrintRightImei) {
        this.havePrintRightImei = havePrintRightImei;
    }

    public boolean isHavePrintRightCallId() {
        return havePrintRightCallId;
    }

    public void setHavePrintRightCallId(boolean havePrintRightCallId) {
        this.havePrintRightCallId = havePrintRightCallId;
    }

    public boolean isHavePrintRightCellId() {
        return havePrintRightCellId;
    }

    public void setHavePrintRightCellId(boolean havePrintRightCellId) {
        this.havePrintRightCellId = havePrintRightCellId;
    }

    public boolean isHavePrintLeftPrintPChargingVector() {
        return havePrintLeftPrintPChargingVector;
    }

    public boolean isHavePrintRightPrintPChargingVector() {
        return havePrintRightPrintPChargingVector;
    }

    public void setHavePrintLeftPrintPChargingVector(boolean havePrintLeftPrintPChargingVector) {
        this.havePrintLeftPrintPChargingVector = havePrintLeftPrintPChargingVector;
    }

    public void setHavePrintRightPrintPChargingVector(boolean havePrintRightPrintPChargingVector) {
        this.havePrintRightPrintPChargingVector = havePrintRightPrintPChargingVector;
    }

    public Long getSendLeft200InvTime() {
        return sendLeft200InvTime;
    }

    public void setSendLeft200InvTime(Long sendLeft200InvTime) {
        this.sendLeft200InvTime = sendLeft200InvTime;
    }

    public Long getRecvLeftACKTime() {
        return recvLeftACKTime;
    }

    public void setRecvLeftACKTime(Long recvLeftACKTime) {
        this.recvLeftACKTime = recvLeftACKTime;
    }

    public Long getSendRightFirstUpdateTime() {
        return sendRightFirstUpdateTime;
    }

    public void setSendRightFirstUpdateTime(Long sendRightFirstUpdateTime) {
        this.sendRightFirstUpdateTime = sendRightFirstUpdateTime;
    }

    public Long getRecvRightFirst200UpdateTime() {
        return recvRightFirst200UpdateTime;
    }

    public void setRecvRightFirst200UpdateTime(Long recvRightFirst200UpdateTime) {
        this.recvRightFirst200UpdateTime = recvRightFirst200UpdateTime;
    }

    public String getLeftImei() {
        return leftImei;
    }

    public void setLeftImei(String leftImei) {
        this.leftImei = leftImei;
    }

    public String getLeftCellId() {
        return leftCellId;
    }

    public void setLeftCellId(String leftCellId) {
        this.leftCellId = leftCellId;
    }

    public String getRightImei() {
        return rightImei;
    }

    public void setRightImei(String rightImei) {
        this.rightImei = rightImei;
    }

    public String getRightCellId() {
        return rightCellId;
    }

    public void setRightCellId(String rightCellId) {
        this.rightCellId = rightCellId;
    }

    public String getLeftPChargingVector() {
        return leftPChargingVector;
    }

    public void setLeftPChargingVector(String leftPChargingVector) {
        this.leftPChargingVector = leftPChargingVector;
    }

    public String getLeftHistoryInfoIndex1Phone() {
        return leftHistoryInfoIndex1Phone;
    }

    public void setLeftHistoryInfoIndex1Phone(String leftHistoryInfoIndex1Phone) {
        this.leftHistoryInfoIndex1Phone = leftHistoryInfoIndex1Phone;
    }

    public String getRightPChargingVector() {
        return rightPChargingVector;
    }

    public void setRightPChargingVector(String rightPChargingVector) {
        this.rightPChargingVector = rightPChargingVector;
    }

    public String getLeftPrivacy() {
        return leftPrivacy;
    }

    public void setLeftPrivacy(String leftPrivacy) {
        this.leftPrivacy = leftPrivacy;
    }

    public String getLeftUserAgent() {
        return leftUserAgent;
    }

    public void setLeftUserAgent(String leftUserAgent) {
        this.leftUserAgent = leftUserAgent;
    }

    public String getRightUserAgent() {
        return rightUserAgent;
    }

    public void setRightUserAgent(String rightUserAgent) {
        this.rightUserAgent = rightUserAgent;
    }

    public boolean isHavePrintRightUserAgent() {
        return havePrintRightUserAgent;
    }

    public void setHavePrintRightUserAgent(boolean havePrintRightUserAgent) {
        this.havePrintRightUserAgent = havePrintRightUserAgent;
    }

    public String getEarlyPlayCodes() {
        return earlyPlayCodes;
    }

    public void setEarlyPlayCodes(String earlyPlayCodes) {
        this.earlyPlayCodes = earlyPlayCodes;
    }

    public String getAnsweredPlayCodes() {
        return answeredPlayCodes;
    }

    public void setAnsweredPlayCodes(String answeredPlayCodes) {
        this.answeredPlayCodes = answeredPlayCodes;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public static void main(String[] args) throws ParseException {
        String sip = "INVITE sip:+8618400577695@sn.ims.mnc000.mcc460.3gppnetwork.org SIP/2.0\n" +
                "Via: SIP/2.0/UDP 10.189.13.167:5060;branch=z9hG4bK689z6hufeq85e7tzss6hrza7u;role=3;dpt=7d24_16,SIP/2.0/UDP 10.193.106.52:5060;branch=z9hG4bK201-22611-12548145552131866139\n" +
                "Route: <sip:neibubfhdhzy2cshaeb.hdh.chinamobile.com;lr>,<sip:10.189.13.167;lr;ORGDLGID=10576-10;Dpt=7d24_6>\n" +
                "Record-Route: <sip:10.189.13.167;lr;Dpt=7d24_116;Role=3;CxtId=4;spln=S;X-HwB2bUaCookie=1317>\n" +
                "Call-ID: e-119-0-522-1530764098@10.193.106.52\n" +
                "From: <tel:+8613802885063>;tag=119-202-004815\n" +
                "To: <tel:18400577695;phone-context=ims.mnc000.mcc460.3gppnetwork.org>\n" +
                "CSeq: 4816 INVITE\n" +
                "Allow: INVITE,UPDATE,BYE,PRACK,INFO,OPTIONS,CANCEL,SUBSCRIBE,ACK,REFER,NOTIFY,REGISTER,PUBLISH,MESSAGE\n" +
                "Contact: <sip:10.193.106.52:5060;transport=udp;Dpt=ec8a-200>;+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\";audio;+sip.instance=\"<urn:gsma:imei:35583708-343768-0>\"\n" +
                "Max-Forwards: 63\n" +
                "Supported: timer,100rel,path,precondition,replaces\n" +
                "Session-Expires: 1800\n" +
                "Min-SE: 90\n" +
                "P-Asserted-Identity: \"+8613802885063\" <sip:+8613802885063@gd.ims.mnc000.mcc460.3gppnetwork.org>,\"+8613802885063\" <tel:+8613802885063>\n" +
                "P-Visited-Network-ID: \"gzpsbc9bhw.gd.ims.mnc000.mcc460.3gppnetwork.org\"\n" +
                "P-Access-Network-Info: 3GPP-E-UTRAN; utran-cell-id-3gpp=460002855CB6869D;network-provided;sbc-domain=sbc03.020.000.gd.chinamobile.com;ue-ip=[2409:8158:C0C:1FB1:1053:215E:C3D6:C1DF];ue-port=50832\n" +
                "P-Charging-Vector: icid-value=\"gzpsbc9bhw.1a2.a7be.20230206073220\";orig-ioi=gd.chinamobile.com;term-ioi=sn.chinamobile.com\n" +
                "P-Early-Media: supported\n" +
                "Session-ID: 13a1b14fd8304797cecb0bbeba1cd288\n" +
                "Accept-Contact: *;+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"\n" +
                "Content-Type: application/sdp\n" +
                "Content-Length: 1056\n" +
                "\n" +
                "v=0\n" +
                "o=- 1675668740 1675668740 IN IP4 10.188.179.100\n" +
                "s=SBC call\n" +
                "c=IN IP4 10.188.30.149\n" +
                "t=0 0\n" +
                "m=audio 22682 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0\n" +
                "b=AS:82\n" +
                "b=RS:612\n" +
                "b=RR:1837\n" +
                "a=rtpmap:104 AMR-WB/16000\n" +
                "a=fmtp:104 max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:110 AMR-WB/16000\n" +
                "a=fmtp:110 octet-align=1;max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:102 AMR/8000\n" +
                "a=fmtp:102 max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:108 AMR/8000\n" +
                "a=fmtp:108 octet-align=1;max-red=220;mode-change-capability=2\n" +
                "a=rtpmap:105 telephone-event/16000\n" +
                "a=fmtp:105 0-15\n" +
                "a=rtpmap:100 telephone-event/8000\n" +
                "a=fmtp:100 0-15\n" +
                "a=ptime:20\n" +
                "a=maxptime:240\n" +
                "a=sendrecv\n" +
                "a=des:qos mandatory local sendrecv\n" +
                "a=curr:qos local none\n" +
                "a=des:qos optional remote sendrecv\n" +
                "a=curr:qos remote none\n" +
                "a=rtpmap:111 EVS/16000\n" +
                "a=fmtp:111 br=9.6-64;bw=swb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:106 EVS/16000\n" +
                "a=fmtp:106 br=5.9-64;bw=wb;mode-change-capability=2;cmr=0;dtx=0;ch-aw-recv=-1;max-red=0\n" +
                "a=rtpmap:18 G729/8000\n" +
                "a=fmtp:18 annexb=no\n" +
                "a=rtpmap:8 PCMA/8000\n" +
                "a=rtpmap:0 PCMU/8000\n";
        StringMsgParser smp = new StringMsgParser();
        SIPMessage message = smp.parseSIPMessage(sip.getBytes(), true, false, null);
        Contact contactHeader = message.getContactHeader();
        String tac8 = "";
        if (contactHeader != null) {
            String contactStr = contactHeader.toString();
            int i = contactStr.indexOf("urn:gsma:imei:");
            if (i != -1 && (contactStr.length() > i + 22)) {
                int imeiEnd = contactStr.indexOf(">", i + 14);
                if (imeiEnd != -1){
                    String imei = contactStr.substring(i + 14, imeiEnd);
                    System.out.println("imei:"+imei);
                }
                tac8 = contactStr.substring(i + 14, i + 22);
                System.out.println("tac8:"+tac8);
            }
        }
    }
}
