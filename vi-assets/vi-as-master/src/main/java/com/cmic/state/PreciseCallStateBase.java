package com.cmic.state;

import com.cmic.BillNew;
import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.pojo.BillAppendReasonEnum;
import com.cmic.sip.controller.base.Controller;
import com.util.PatternUtil;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.CSeqHeader;
import javax.sip.header.Header;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.ListIterator;

public abstract class PreciseCallStateBase {
    static final Logger logger = LoggerFactory.getLogger(FinishState.class);
    BillNew billNew;
    boolean hasRecv180Msg;
    boolean recv180MsgBefore181Msg;
    long lastRecv180MsgTime;
    long firstRespTime;
    long recvNonSDP183MsgTime;
    long lastUpdateMsgTime;
    int cause;
    int statusCode;
    SipMessageDesc causeByWhichSipMessage;
    String causeText;
    Contact causeContact;
    long initialSeq;
    boolean recvLeftBye;
    boolean recvRightBye;
    boolean recvCancel;
    boolean hasRecv181Msg;
    boolean hasRecvNonSDP183Msg;
    boolean isSystemExit;
    boolean isControllerTimeOut;
    boolean isCallWaiting;
    boolean hasRecv200OK = false;
    boolean hasRecvAck = false;
    boolean hasRecvInvite200 = false;
    StringBuilder sb = new StringBuilder();

    public PreciseCallStateBase(BillNew billNew) {
        this.billNew = billNew;
    }

    public PreciseCallStateBase() {

    }


    public long getFirstRespTime() {
        return firstRespTime;
    }

    public long getLastRecv180MsgTime() {
        return lastRecv180MsgTime;
    }

    public void setInitialSeq(long initialSeq) {
        this.initialSeq = initialSeq;
    }

    public long getInitialSeq() {
        return initialSeq;
    }

    public void setHasRecvAck(boolean hasRecvAck) {
        this.hasRecvAck = hasRecvAck;
    }

    public int getCause() {
        return cause;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public StringBuilder getSb() {
        return sb;
    }

    public boolean isHasRecv181Msg() {
        return hasRecv181Msg;
    }

    public boolean isRecvCancel() {
        return recvCancel;
    }

    public boolean isHasRecv180Msg() {
        return hasRecv180Msg;
    }

    public boolean isRecvLeftBye() {
        return recvLeftBye;
    }

    /**
     * 根据request类型和是否为主叫发起，判断是否是被叫拒接，同时更新记录关键事件的相关标志字段，同时判断是否是被叫拒接
     *
     * @param isLeft  　是否为主叫
     * @param request 　请求体（信令）
     * @return true:被叫拒接；false:非被叫拒接
     */
    public synchronized boolean extractRequestMsgPropertiesJudgeReject(boolean isLeft, SIPRequest request) {
        String method = request.getMethod();
        if (method.equalsIgnoreCase(SIPRequest.BYE)) {
            if (isLeft) {
                recvLeftBye = true;
            } else {
                recvRightBye = true;
            }
        } else if (method.equalsIgnoreCase(SIPRequest.CANCEL)) {
            recvCancel = true;
        } else if (method.equalsIgnoreCase(SIPRequest.UPDATE)) {
            if (!isLeft) {
                lastUpdateMsgTime = System.currentTimeMillis();
//                if (hasRecv180Msg) {
//                    return true;
//                }
                if (hasRecv180Msg) {
                    ListIterator reasonList = request.getHeaders(ReasonHeader.NAME);
                    //提取消息体的Q850字段
                    while (reasonList.hasNext()) {
                        ReasonHeader reasonHeader = (ReasonHeader) reasonList.next();
                        if ("Q.850".equalsIgnoreCase(reasonHeader.getProtocol()) && cause == 0) {
                            recordCause(reasonHeader, request);
                        }
                        if ("SIP".equalsIgnoreCase(reasonHeader.getProtocol()) && statusCode == 0) {
                            statusCode = reasonHeader.getCause();
                        }
                        sb.append(reasonHeader).append(",");
                    }
                    return true;
                }
            }
        } else if (method.equalsIgnoreCase(SIPRequest.ACK)) {
            // 记录是否收到主叫ACK
            if (isLeft) {
                hasRecvAck = true;
            }
        }

        return false;
    }

    /**
     * 如果已经收到错误提示，
     * 再收到180消息 不能放音
     **/
    public boolean canPlayearly() {
        if (hasRecv181Msg || isCallWaiting) {
            return false;
        }
        return true;
    }


    public synchronized boolean extractResponseMsgPropertiesJudgeReject(Response response) {
//        logger.info("==extractResponseMsgProperties==\n" + response.toString());
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
        String method = cseqHeader.getMethod();
        //如果180之后还有181和183 基本判断为被叫拒接
        boolean isReject;
        isReject = isReject(response);
        if (firstRespTime == 0) {
            firstRespTime = System.currentTimeMillis();
        }
        if (response.getStatusCode() == 200 && method.equalsIgnoreCase(Request.INVITE)) {
            hasRecv200OK = true;
        }
        if (response.getStatusCode() == 181) {
            hasRecv181Msg = true;
            Header historyInfoHeader = response.getHeader("History-Info");
            if (historyInfoHeader != null) {
                String historyInfo = historyInfoHeader.toString();
                String causeStr = PatternUtil.getCause(historyInfo);
                if (StringUtils.isNotEmpty(causeStr)) {
                    // bill.setTransferCode(causeStr);
                }
                sb = new StringBuilder(PatternUtil.getCause(historyInfo));
                recordCause(Integer.parseInt(causeStr), (SIPMessage) response);
                statusCode = 181;
            }
            if (hasRecv180Msg) {
                recv180MsgBefore181Msg = true;
            }
        } else if (response.getStatusCode() == 180) {
            hasRecv180Msg = true;
            if (lastRecv180MsgTime == 0) {
                lastRecv180MsgTime = System.currentTimeMillis();
            }
//            Alert-Info: <urn:alert:service:call-waiting>
            Header alterInfoHeader = response.getHeader("Alert-Info");
            if (alterInfoHeader != null) {
                String alterInfo = alterInfoHeader.toString();
                if (alterInfo.contains("call-waiting")) {
                    isCallWaiting = true;
                    sb.append("call-waiting").append(",");
                }
            }
        } else if (response.getStatusCode() == 183) {
            if (response.getContentLength().getContentLength() == 0) {
                hasRecvNonSDP183Msg = true;
                recvNonSDP183MsgTime = System.currentTimeMillis();
            }
        }
        //只记录invite的4**
        if (method.equalsIgnoreCase(Request.INVITE) && cseqHeader.getSeqNumber() == initialSeq && response.getStatusCode() >= 400 && response.getStatusCode() != 487) {
            statusCode = response.getStatusCode();
        }
        ListIterator reasonList = response.getHeaders(ReasonHeader.NAME);

        //提取消息体的Q850字段
        while (reasonList.hasNext()) {
            ReasonHeader reasonHeader = (ReasonHeader) reasonList.next();
            if ("Q.850".equalsIgnoreCase(reasonHeader.getProtocol())) {
                recordCause(reasonHeader, (SIPMessage) response);
            }
            if ("SIP".equalsIgnoreCase(reasonHeader.getProtocol())) {
                statusCode = reasonHeader.getCause();
            }
            sb.append(reasonHeader).append(",");
        }

        return isReject;

    }

    public synchronized boolean extractResponseMsgPropertiesJudgeReject(boolean isLeft, Response response) {
//        logger.info("==extractResponseMsgProperties==\n" + response.toString());
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
        String method = cseqHeader.getMethod();
        //如果180之后还有181和183 基本判断为被叫拒接
        boolean isReject;
        isReject = isReject(response);
        if (firstRespTime == 0) {
            firstRespTime = System.currentTimeMillis();
        }
        if (response.getStatusCode() == 200 && method.equalsIgnoreCase(Request.INVITE)) {
            hasRecv200OK = true;
        }
        if (response.getStatusCode() == 181) {
            hasRecv181Msg = true;
            Header historyInfoHeader = response.getHeader("History-Info");
            if (historyInfoHeader != null) {
                String historyInfo = historyInfoHeader.toString();
                String causeStr = PatternUtil.getCause(historyInfo);
                sb = new StringBuilder(PatternUtil.getCause(historyInfo));
                recordCause(Integer.parseInt(causeStr), (SIPMessage) response);
                statusCode = 181;
            }
            if (hasRecv180Msg) {
                recv180MsgBefore181Msg = true;
            }
        } else if (response.getStatusCode() == 180) {
            hasRecv180Msg = true;
            if (lastRecv180MsgTime == 0) {
                lastRecv180MsgTime = System.currentTimeMillis();
            }
//            Alert-Info: <urn:alert:service:call-waiting>
            Header alterInfoHeader = response.getHeader("Alert-Info");
            if (alterInfoHeader != null) {
                String alterInfo = alterInfoHeader.toString();
                if (alterInfo.contains("call-waiting")) {
                    isCallWaiting = true;
                    sb.append("call-waiting").append(",");
                }
            }
        } else if (response.getStatusCode() == 183) {
            if (response.getContentLength().getContentLength() == 0) {
                hasRecvNonSDP183Msg = true;
                recvNonSDP183MsgTime = System.currentTimeMillis();
            }
        } else if (!isLeft && ((SIPResponse) response).getCSeq().getMethod().equalsIgnoreCase(Request.INVITE) && response.getStatusCode() == 200) {
            hasRecvInvite200 = true;
        }
        //只记录invite的4**
        if (method.equalsIgnoreCase(Request.INVITE) && cseqHeader.getSeqNumber() == initialSeq && response.getStatusCode() >= 400 && response.getStatusCode() != 487) {
            statusCode = response.getStatusCode();
        }
        ListIterator reasonList = response.getHeaders(ReasonHeader.NAME);

        //提取消息体的Q850字段
        while (reasonList.hasNext()) {
            ReasonHeader reasonHeader = (ReasonHeader) reasonList.next();
            if ("Q.850".equalsIgnoreCase(reasonHeader.getProtocol())) {
                recordCause(reasonHeader, (SIPMessage) response);
            }
            if ("SIP".equalsIgnoreCase(reasonHeader.getProtocol())) {
                statusCode = reasonHeader.getCause();
            }
            sb.append(reasonHeader).append(",");
        }

        return isReject;

    }

    private void recordCause(ReasonHeader reasonHeader, SIPMessage sipMessage) {
        if (StringUtils.isBlank(causeText)) {
            causeText = reasonHeader.getText();
        }
        if (causeContact == null) {
            causeContact = sipMessage.getContactHeader();
        }
        recordCause(reasonHeader.getCause(), sipMessage);
    }

    private void recordCause(int cause, SIPMessage sipMessage) {
        this.cause = cause;
        if (sipMessage instanceof SIPRequest) {
            SIPRequest request = (SIPRequest) sipMessage;
            causeByWhichSipMessage = SipMessageDesc.getSipRequestInstance(SipMessageDesc.SendOrRecv.RECV, request.getMethod());
        } else {
            SIPResponse response = (SIPResponse) sipMessage;
            causeByWhichSipMessage = SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, response.getCSeq().getMethod(), response.getStatusCode());
        }

    }

    public boolean isReject(Response response) {
        if (hasRecv180Msg && (response.getStatusCode() == 181 || response.getStatusCode() == 183)) {
            return true;
        }
        return false;
    }

    public boolean isRecv181Reject(Response response) {
        if (hasRecv180Msg && response.getStatusCode() == 181) {
            return true;
        }
        return false;
    }

    public boolean isNoUserResponse() {
        if (lastRecv180MsgTime == 0 || (System.currentTimeMillis() - lastRecv180MsgTime < FinishState.OUT_TIME_DEFINCE)) {
            return false;
        }
        return true;
    }

    public synchronized int[] getFinalState() {
        logger.info("[{}] getFinalState status {} cause {}, cause by message:{} reason is {}", billNew.getId(), getStatusCode(), getCause(), causeByWhichSipMessage, getSb().toString());
        //如果收到181消息需要单独处理。因为语音小秘书各家做法不一致，采用早期媒体录音识别也不能完全做到
        //因为有些181转接之后主叫是200之后的放音。所以优先级在bye前面
        if (isSystemExit) {
            return buildFinishState(FinishType.SYSTEM_STOP, FinishState.SYSTEM_RESTART);
        }

        // 收到200-INVITE 但是没有收到对应的ACK时，状态为25-无法识别
        if (!hasRecvAck && hasRecv200OK) {
            if (recvLeftBye) {
                return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_BYE);
            } else if (recvRightBye) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE);
            } else if (recvCancel) {
                return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL);
            } else {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE);
            }
        }
        //然后考虑正常通话的状态
        if (recvLeftBye && hasRecv200OK) {
            return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_BYE);
        } else if (recvRightBye && hasRecv200OK) {
            return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE);
        }
        //若收到200OK且收到ACK且没收到bye为系统异常，兜底为被叫挂机 todo 优化这里
        if (hasRecvAck && hasRecv200OK) {
            return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE);
        }

        if (isControllerTimeOut) {
            if (recvCancel) {
                logger.info("[{}] trigger controller timeout but receive caller cancel.", billNew.getId());
                return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL);
            }
            return buildFinishState(FinishType.SYSTEM_STOP, FinishState.CONTROLLER_OUT_TIME);
        }
        if (hasRecv181Msg) {
            logger.info("[{}] has recv 181", billNew.getId());
            int finishState = FinishState.SECRETARY;
            if (cause > 0 || statusCode > 0) {
                finishState = FinishState.causeToFinishState(cause, hasRecv180Msg, statusCode);
            }
            //如果没有收到180先收到181小秘书消息，这里面的486需要判定为被叫忙
            if (!recv180MsgBefore181Msg) {
                if (cause == 486) {
                    finishState = FinishState.RIGHT_LEG_BUSY;
                }
            }
            //有振铃时间，有181的空号、不可及话单都归为呼叫转移
            if (hasRecv180Msg && (finishState == FinishState.RIGHT_LEG_NO_EXITS || finishState == FinishState.RIGHT_LEG_UNACCESSED)) {
                finishState = FinishState.SECRETARY;
            }
            return buildFinishState(FinishType.RIGHT_LEG_STOP, finishState);

        }

        //考虑已经振铃的情况
        long currentTime = System.currentTimeMillis();
        if (hasRecv180Msg) {
            logger.info("[{}] has Recv 180", billNew.getId());
            if (isCallWaiting) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BUSY);
            }
            //优先考虑是不是超时了
            if (currentTime - lastRecv180MsgTime >= FinishState.OUT_TIME_DEFINCE) {
                // 超时的情况下，有可能存在是超时之前拒接但是超时之后才发送了487的情况，这里特殊判断一下。按照Q850和对应SIP的文档，cause21和statusCode603表示的是挂机的情况，但是为了验证这个情况，增加一个匹配日志输出
                if (cause == 21 && statusCode == 603) {
                    // 1. 收到180后的183并且收到的时间在超时时间范围内
                    boolean recvNonSDP183InTime = hasRecvNonSDP183Msg && (recvNonSDP183MsgTime - lastRecv180MsgTime < FinishState.OUT_TIME_DEFINCE);
                    // 2. 收到180后的update，并且收到的时间在超时时间范围内
                    boolean recvUpdateAfter180InTime = (lastUpdateMsgTime - lastRecv180MsgTime < FinishState.OUT_TIME_DEFINCE) && (lastUpdateMsgTime - lastRecv180MsgTime > 0);
                    if (!recvNonSDP183InTime && !recvUpdateAfter180InTime) {
                        logger.info("billID:[{}], [reject_judge_wrong] cause21 and statusCode603 but seem not reject, , recvNonSDP183MsgTime:{}, lastUpdateMsgTime:{}", billNew.getId(), recvNonSDP183MsgTime, lastUpdateMsgTime);
                    }
                    return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT);
                }
            }
            //振铃之后收到update消息，证明重新协商媒体播放被叫忙宣告音
            if (lastUpdateMsgTime > lastRecv180MsgTime) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT);
            } else if (hasRecvNonSDP183Msg) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT);
            }

            if (cause == 17 && statusCode == 486) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BUSY);
            }
            // 超时无应答作为振铃后的兜底判断，先判断前面更准确的状态
            if (currentTime - lastRecv180MsgTime >= FinishState.OUT_TIME_DEFINCE) {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_RESPDND);
            }

            if (recvCancel) {
                return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL);
            }
            logger.info("[{}] need asr", billNew.getId());
            return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.NEED_ASR);
        }

        //如果没有振铃，这里分两种情况：1、主叫在振铃之前取消。2、被叫有异常主叫听到提示取消
        if (cause == 0 && statusCode == 0) {
            logger.info("[{}] no cause and no status Code", billNew.getId());
            if (hasRecvNonSDP183Msg) {
                logger.info("[{}] need asr", billNew.getId());
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.NEED_ASR);
            }
            if (recvCancel) {
                return buildFinishState(FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL);
            } else {
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.UNRECOGNIZED_STATE);
            }
        }

        int[] rejectState = imsReject();
        if (rejectState != null) {
            return rejectState;
        }

        int finishState = FinishState.causeToFinishState(cause, hasRecv180Msg, statusCode);
        return buildFinishState(FinishType.RIGHT_LEG_STOP, finishState);
    }

    private int[] buildFinishState(int finishType, int finishState) {
        logger.info("[{}] StateBase return: finishType:{}, finishState:{}", billNew.getId(), finishType, finishState);
        return new int[]{finishType, finishState};
    }

    private int[] imsReject() {
        if (StringUtils.isNotBlank(causeText) && (cause == 603 || statusCode == 603)) {
            if ("PSP Platform Rejection".equals(causeText) || "PSP,Platform,Rejection".equals(causeText)) {
                billNew.setAppendReason(BillAppendReasonEnum.IMS_FZ_INTERCEPT_12.getCode());
                logger.info("[{}] imsFZReject, set finishState STRATEGY_HIJACKING", billNew.getId());
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.STRATEGY_HIJACKING);
            }
        }
        //SipMessageDesc response486 = SipMessageDesc.getSipResponseInstance(SipMessageDesc.SendOrRecv.RECV, SIPRequest.INVITE, 486);
        if (cause != 21 && statusCode != 21 && cause != 18 && statusCode != 18) {
            return null;
        }
        Integer stateCode = Controller.THREAD_LAST_RESPONSE_STATECODE.get();
        if (stateCode == null || stateCode != 486) {
            return null;
        }
        if (!hasRecvNonSDP183Msg) {
            return null;
        }
        if (causeContact == null || (!causeContact.toString().contains("10.68") && !causeContact.toString().contains("10.107"))) {
            return null;
        }
        if (billNew.getCallForwardTime() != null) {
            long startTime = billNew.getCallForwardTime().getTime();
            long now = System.currentTimeMillis();
            if ((billNew.getCallEndTime() != null && billNew.getCallEndTime().getTime() - startTime <= 2000) || ( now - startTime <= 2000)) {
                billNew.setAppendReason(BillAppendReasonEnum.IMS_FREQUENCY_INTERCEPT_11.getCode());
                logger.info("[{}] imsReject, set finishState STRATEGY_HIJACKING", billNew.getId());
                return buildFinishState(FinishType.RIGHT_LEG_STOP, FinishState.STRATEGY_HIJACKING);
            }
            logger.info("[{}] imsFrequencyReject but over than 1s", billNew.getId());
        }
        return null;
    }

    public void setSystemExit(boolean systemExit) {
        this.isSystemExit = systemExit;
    }

    public void setControllerTimeOut(boolean isControllerTimeOut) {
        this.isControllerTimeOut = isControllerTimeOut;
    }

    public boolean isSystemExit() {
        return isSystemExit;
    }

    public boolean isRecv200OK() {
        return hasRecv200OK;
    }

    public void setRecv200OK(boolean hasRecv200OK) {
        this.hasRecv200OK = hasRecv200OK;
    }

    public boolean getRecvLeftBye() {
        return recvLeftBye;
    }

    public boolean getRecvCancel() {
        return recvCancel;
    }
}
