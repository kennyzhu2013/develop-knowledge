// package com.cmic.state;
//
// import com.util.PatternUtil;
// import gov.nist.javax.sip.message.SIPRequest;
// import gov.nist.javax.sip.message.SIPResponse;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// import javax.sip.header.CSeqHeader;
// import javax.sip.header.Header;
// import javax.sip.header.ReasonHeader;
// import javax.sip.message.Request;
// import javax.sip.message.Response;
// import java.util.ListIterator;
//
// //Todo:等自研分机号上线后可以删掉
// public class DtmfPreciseCallState {
//     private static final Logger logger = LoggerFactory.getLogger(FinishState.class);
//
//     private boolean hasRecv180Msg;
//     private boolean recv180MsgBefore181Msg;
//     private long lastRecv180MsgTime;
//     private long lastUpdateMsgTime;
//     private int cause;
//     private int statusCode;
//     private boolean recvLeftBye;
//     private boolean recvRightBye;
//     private boolean recvCancel;
//     private boolean hasRecv181Msg;
//     private boolean hasRecvNonSDP183Msg;
//     private boolean isSystemExit;
//     private boolean isControllerTimeOut;
//     private boolean isCallWaiting;
//     private boolean hasRecvInvite200;
//     private long initialSeq;
//     private StringBuilder sb = new StringBuilder();
//     private Bill bill;
//     private long recvNonSDP183MsgTime;
//     private boolean hasRecvAck;
//
//     public DtmfPreciseCallState(Bill bill) {
//         this.bill = bill;
//     }
//
//
//     public void setInitialSeq(long initialSeq) {
//         this.initialSeq = initialSeq;
//     }
//
//     public long getInitialSeq() {
//         return initialSeq;
//     }
//
//     public void setHasRecvAck(boolean hasRecvAck) {
//         this.hasRecvAck = hasRecvAck;
//     }
//
//     /**
//      * 根据request类型和是否为主叫发起，判断是否是被叫拒接，同时更新记录关键事件的相关标志字段，同时判断是否是被叫拒接
//      *
//      * @param isLeft  　是否为主叫
//      * @param request 　请求体（信令）
//      * @return true:被叫拒接；false:非被叫拒接
//      */
//     public boolean extractRequestMsgPropertiesJudgeReject(boolean isLeft, SIPRequest request) {
//         String method = request.getMethod();
//         if (method.equalsIgnoreCase(SIPRequest.BYE)) {
//             if (isLeft) {
//                 recvLeftBye = true;
//             } else {
//                 recvRightBye = true;
//             }
//         } else if (method.equalsIgnoreCase(SIPRequest.CANCEL)) {
//             recvCancel = true;
//         } else if (method.equalsIgnoreCase(SIPRequest.UPDATE)) {
//             if (!isLeft) {
//                 lastUpdateMsgTime = System.currentTimeMillis();
//                 if (hasRecv180Msg) {
//                     return true;
//                 }
//             }
//         }
//         return false;
//     }
//
//     /**
//      * 如果已经收到错误提示，
//      * 再收到180消息 不能放音
//      **/
//     public boolean canPlayearly() {
//         if (hasRecv181Msg || isCallWaiting) {
//             return false;
//         }
//         return true;
//     }
//
//     public boolean extractResponseMsgPropertiesJudgeReject(boolean isLeft, boolean hasRight, Response response) {
// //        logger.info("==extractResponseMsgProperties==\n" + response.toString());
//         //如果180之后还有181和183 基本判断为被叫拒接
//         boolean isReject = false;
//         CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
//         String method = cseqHeader.getMethod();
//         if (hasRecv180Msg && (response.getStatusCode() == 181 || response.getStatusCode() == 183)) {
//             isReject = true;
//         }
//         if (response.getStatusCode() == 181) {
//             hasRecv181Msg = true;
//             Header historyInfoHeader = response.getHeader("History-Info");
//             if (historyInfoHeader != null) {
//                 String historyInfo = historyInfoHeader.toString();
//                 String causeStr = PatternUtil.getCause(historyInfo);
//                 sb = new StringBuilder(PatternUtil.getCause(historyInfo));
//                 cause = Integer.valueOf(causeStr);
//                 statusCode = 181;
//             }
//             if (hasRecv180Msg) {
//                 recv180MsgBefore181Msg = true;
//             }
//         } else if (response.getStatusCode() == 180) {
//             hasRecv180Msg = true;
//             if (lastRecv180MsgTime == 0) {
//                 lastRecv180MsgTime = System.currentTimeMillis();
//             }
// //            Alert-Info: <urn:alert:service:call-waiting>
//             Header alterInfoHeader = response.getHeader("Alert-Info");
//             if (alterInfoHeader != null) {
//                 String alterInfo = alterInfoHeader.toString();
//                 if (alterInfo.indexOf("call-waiting") != -1) {
//                     isCallWaiting = true;
//                     sb.append("call-waiting").append(",");
//                 }
//             }
//         } else if (response.getStatusCode() == 183) {
//             if (response.getContentLength().getContentLength() == 0) {
//                 hasRecvNonSDP183Msg = true;
//                 recvNonSDP183MsgTime = System.currentTimeMillis();
//             }
//         } else if (!isLeft && hasRight && ((SIPResponse) response).getCSeq().getMethod().equalsIgnoreCase(Request.INVITE) && response.getStatusCode() == 200) {
//             hasRecvInvite200 = true;
//         }
//         if (method.equalsIgnoreCase(Request.INVITE) && cseqHeader.getSeqNumber() == initialSeq && response.getStatusCode() >= 400 && response.getStatusCode() != 487) {
//             statusCode = response.getStatusCode();
//         }
//         ListIterator reasonList = response.getHeaders(ReasonHeader.NAME);
//
//         //提取消息体的Q850字段
//         while (reasonList.hasNext()) {
//             //如果有新的reasonList,将cause,statusCode重置，取最新的reasonList
//
//             ReasonHeader reasonHeader = (ReasonHeader) reasonList.next();
//             if (reasonHeader.getProtocol().equalsIgnoreCase("Q.850")) {
//                 cause = reasonHeader.getCause();
//             }
//             if (reasonHeader.getProtocol().equalsIgnoreCase("SIP")) {
//                 statusCode = reasonHeader.getCause();
//             }
//             sb.append(reasonHeader).append(",");
//         }
//
//
//         return isReject;
//
//     }
//
//     public int[] getFinalState() {
//         //如果收到181消息需要单独处理。因为语音小秘书各家做法不一致，采用早期媒体录音识别也不能完全做到
//         //因为有些181转接之后主叫是200之后的放音。所以优先级在bye前面
//         if (isSystemExit) {
//             return new int[]{FinishType.SYSTEM_STOP, FinishState.SYSTEM_RESTART};
//         }
//
//         logger.info(bill.getBillId() + " getFinalState status {} cause {} reason is {}", statusCode, cause, sb.toString());
//         //然后考虑正常通话的状态
//         if (hasRecvInvite200) {
//             if (recvLeftBye) {
//                 return new int[]{FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_BYE};
//             } else if (recvRightBye) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE};
//             }
//         }
//
//         //若收到200OK且收到ACK且没收到bye为系统异常，兜底为被叫挂机
//         if (hasRecvAck && hasRecvInvite200) {
//             return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BYE};
//         }
//         if (isControllerTimeOut) {
//             return new int[]{FinishType.SYSTEM_STOP, FinishState.CONTROLLER_OUT_TIME};
//         }
//         if (hasRecv181Msg) {
//             int finishState = FinishState.SECRETARY;
//             if (cause > 0 || statusCode > 0) {
//                 finishState = FinishState.causeToFinishState(cause, hasRecv180Msg, statusCode);
//             }
//             //如果没有收到180先收到181小秘书消息，这里面的486需要判定为被叫忙
//             if (!recv180MsgBefore181Msg) {
//                 if (cause == 486) {
//                     finishState = FinishState.RIGHT_LEG_BUSY;
//                 }
//             }
//             //有振铃时间，有181的空号、不可及话单都归为呼叫转移
//             if (hasRecv180Msg && (finishState == FinishState.RIGHT_LEG_NO_EXITS || finishState == FinishState.RIGHT_LEG_UNACCESSED)) {
//                 finishState = FinishState.SECRETARY;
//             }
//             return new int[]{FinishType.RIGHT_LEG_STOP, finishState};
//
//         }
//
//         long currentTime = System.currentTimeMillis();
//         //考虑已经振铃的情况
//         if (hasRecv180Msg) {
//             if (isCallWaiting) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_BUSY};
//             }
//             //优先考虑是不是超时了
//             if (currentTime - lastRecv180MsgTime >= FinishState.OUT_TIME_DEFINCE) {
//                 // 超时的情况下，有可能存在是超时之前拒接但是超时之后才发送了487的情况，这里特殊判断一下。按照Q850和对应SIP的文档，cause21和statusCode603表示的是挂机的情况，但是为了验证这个情况，增加一个匹配日志输出
//                 if (cause == 21 && statusCode == 603) {
//                     // 1. 收到180后的183并且收到的时间在超时时间范围内
//                     boolean recvNonSDP183InTime = hasRecvNonSDP183Msg && (recvNonSDP183MsgTime - lastRecv180MsgTime < FinishState.OUT_TIME_DEFINCE);
//                     // 2. 收到180后的update，并且收到的时间在超时时间范围内
//                     boolean recvUpdateAfter180InTime = (lastUpdateMsgTime - lastRecv180MsgTime < FinishState.OUT_TIME_DEFINCE) && (lastUpdateMsgTime - lastRecv180MsgTime > 0);
//                     if (!recvNonSDP183InTime && !recvUpdateAfter180InTime) {
//                         logger.info("billID:[{}], [reject_judge_wrong] cause21 and statusCode603 but seem not reject, , recvNonSDP183MsgTime:{}, lastUpdateMsgTime:{}", bill.getBillId(), recvNonSDP183MsgTime, lastUpdateMsgTime);
//                     }
//                     return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT};
//                 }
//             }
//             //振铃之后收到update消息，证明重新协商媒体播放被叫忙宣告音
//             if (lastUpdateMsgTime > lastRecv180MsgTime) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT};
//             } else if (hasRecvNonSDP183Msg) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_REJECT};
//             }
//             //先判断是否有sip.cause和cause
//             if (cause != 0 || statusCode != 0) {
//                 int finishState = FinishState.causeToFinishState(cause, hasRecv180Msg, statusCode);
//                 return new int[]{FinishType.RIGHT_LEG_STOP, finishState};
//             }
//             // 超时无应答作为振铃后的兜底判断，先判断前面更准确的状态
//             if (currentTime - lastRecv180MsgTime >= FinishState.OUT_TIME_DEFINCE) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.RIGHT_LEG_RESPDND};
//             }
//             if (recvLeftBye) {
//                 return new int[]{FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL};
//             }
//             return new int[]{FinishType.LEFT_LEG_STOP, FinishState.NEED_ASR};
//
//         }
//
//         //如果没有振铃，这里分两种情况：1、主叫在振铃之前取消。2、被叫有异常主叫听到提示取消
//         if (cause == 0 && statusCode == 0) {
//             if (hasRecvNonSDP183Msg) {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.NEED_ASR};
//             }
//             if (recvLeftBye) {
//                 return new int[]{FinishType.LEFT_LEG_STOP, FinishState.LEFT_LEG_CANCEL};
//             } else {
//                 return new int[]{FinishType.RIGHT_LEG_STOP, FinishState.UNRECOGNIZED_STATE};
//             }
//
//         }
//
//         int finishState = FinishState.causeToFinishState(cause, hasRecv180Msg, statusCode);
//         return new int[]{FinishType.RIGHT_LEG_STOP, finishState};
//     }
//
//     public void setSystemExit(boolean systemExit) {
//         this.isSystemExit = systemExit;
//     }
//
//     public boolean getRecvLeftBye() {
//         return recvLeftBye;
//     }
//
//     public boolean getRecvCancel() {
//         return recvCancel;
//     }
//
//     public void setControllerTimeOut(boolean isControllerTimeOut) {
//         this.isControllerTimeOut = isControllerTimeOut;
//     }
//
//     public boolean isSystemExit() {
//         return isSystemExit;
//     }
// }
