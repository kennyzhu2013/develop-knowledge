package com.cmic.bizcontroller.controllerImpl.vod;

import com.cmic.bizcontroller.controllerImpl.vod.plugin.CallInfo;
import com.cmic.bizcontroller.controllerImpl.vod.state.*;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.util.TransactionIdUtil;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.ClientTransaction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Date: 2022/9/28
 *
 * @author luohuanfa
 */
public class ControllerState {
    private Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 简单版state:
     * 0 : start(when init)
     * 1 : early(receive/send invite)
     * 2 : ringed(receive/send 180)
     * 3 : pickUp(receive/send ack)
     * 4 : stop
     */
    Map<Leg, AbstractCallState> stateMap = new ConcurrentHashMap<>();
    Map<Leg, Map<String, CommonRequestState>> commonRequestStateMap = new ConcurrentHashMap<>();

    private CallInfo callInfo = new CallInfo();

    public void initEnvironment(CallInfo callInfo) {
        this.callInfo = callInfo;
    }

    public CallInfo getCallInfo() {
        return callInfo;
    }

    public void setCallInfo(CallInfo callInfo) {
        this.callInfo = callInfo;
    }

    public Map<Leg, AbstractCallState> getStateMap() {
        return stateMap;
    }

    public void setStateMap(Map<Leg, AbstractCallState> stateMap) {
        this.stateMap = stateMap;
    }


    public void consumeFirstInviteRequest(Leg leg) {
        consumeSipMessage(leg, SipMessageDesc.getSipRequestInstance(leg.isLeft() ? SipMessageDesc.SendOrRecv.RECV : SipMessageDesc.SendOrRecv.SEND, SIPRequest.INVITE), null);
    }


    /**
     * 消费Sip消息，根据消息推动状态机的改变
     *
     * @param leg
     */
    public void consumeSipMessage(Leg leg, SipMessageDesc sipMessageDesc, String transactionId) {
        String fixTransactionId = TransactionIdUtil.fixZ9hg4bk(transactionId);
        refreshLeg(leg);
        // 不含有的话就是初始Invite
        if (!stateMap.containsKey(leg)) {
            boolean isUac = !leg.isLeft();
            AbstractCallState abstractCallState = new StartState(isUac).stateStepOn(sipMessageDesc);
            stateMap.put(leg, abstractCallState);
            return;
        }
        AbstractCallState previousCallState = stateMap.get(leg);
        // 初始Invite的ack也特殊对待，因为transactionId可能不一样
        boolean firstInviteAck = sipMessageDesc.getMethod().equals(SIPRequest.ACK) && (previousCallState instanceof EarlyState || previousCallState instanceof RingedState);
        boolean belongToFirstInvite = TransactionIdUtil.fixZ9hg4bk(leg.firstInviteTransation.getBranchId()).equals(fixTransactionId) ||
                sipMessageDesc.getMethod().equals(SIPRequest.BYE) || firstInviteAck;
        if (belongToFirstInvite) {
            AbstractCallState stepState = previousCallState.stateStepOnNext(sipMessageDesc);
            // 变更state
            if (stepState != previousCallState) {
                stateMap.put(leg, stepState);
            }
            return;
        }

        // 如果不是初始Invite，则是普通的请求
        CommonRequestState state;
        Map<String, CommonRequestState> commonRequestState;
        if (commonRequestStateMap.containsKey(leg)) {
            commonRequestState = commonRequestStateMap.get(leg);
        } else {
            commonRequestState = new LinkedHashMap<>();
            commonRequestStateMap.put(leg, commonRequestState);
        }
        if (commonRequestState.containsKey(fixTransactionId)) {
            state = commonRequestState.get(fixTransactionId);
        } else {
            if (sipMessageDesc.getMethod().equals(SIPRequest.ACK)) {
                Map.Entry<String, CommonRequestState> lastReInviteState = getLastReInviteState(commonRequestState);
                if (lastReInviteState == null) {
                    logger.error("[{}] lastReInviteState is null", callInfo.getBillNew().getId());
                    return;
                }
                fixTransactionId = lastReInviteState.getKey();
                state = lastReInviteState.getValue();
                if (state == null) {
                    logger.error("[{}] ack with no inivte", callInfo.getBillNew().getId());
                    return;
                }
            } else {
                // 没有记录到Request的Response,判为异常信令
                if (sipMessageDesc.getRequestOrResponse().equals(SipMessageDesc.RequestOrResponse.RESPONSE)) {
                    // logger.error("");
                    return;
                }
                state = new CommonRequestState(callInfo);
                commonRequestState.put(fixTransactionId, state);
            }
        }
        state.stateStepOn(sipMessageDesc);
        //logger.info("****** ControllerState sipMessageDesc:{}, transactionId:{},state:{}",sipMessageDesc,transactionId,state.getStateEnum());
        if (state.getStateEnum().equals(CommonRequestState.CommonStateEnum.FINISH)) {
            commonRequestState.remove(fixTransactionId);
        }
    }

    public void clearCommonRequestState() {
        if (commonRequestStateMap.size() > 0) {
            commonRequestStateMap = new ConcurrentHashMap<>();
        }
    }

    private Map.Entry<String, CommonRequestState> getLastReInviteState(Map<String, CommonRequestState> commonRequestState) {
        for (Map.Entry<String, CommonRequestState> stateEntry : commonRequestState.entrySet()) {
            if (stateEntry.getValue().getRequestDesc().getMethod().equals(SIPRequest.INVITE)) {
                return stateEntry;
            }
        }
        return null;
    }


    private void refreshLeg(Leg leg) {
        if (leg instanceof LeftLeg) {
            callInfo.setLeftLeg((LeftLeg) leg);
        }
        if (leg instanceof RightLeg) {
            callInfo.setRightLeg((RightLeg) leg);
        }
    }

    public AbstractCallState getCurrentState(Leg leg) {
        return stateMap.get(leg);
    }

    public Map<String, CommonRequestState> getCommonRequestState(Leg leg) {
        return commonRequestStateMap.get(leg);
    }

    public boolean isCommonRequestFinish(Leg leg) {
        return commonRequestStateMap.get(leg) == null || commonRequestStateMap.get(leg).size() == 0;
    }

    public boolean isAllLegCommonRequestFinish() {
        for (Map.Entry<Leg, Map<String, CommonRequestState>> commonEntry : commonRequestStateMap.entrySet()) {
            if (commonEntry.getValue().size() > 0) {
                logger.info("==============isAllLegCommonRequestFinish===============");
                logger.info("[{}], [isAllLegCommonRequestFinish], commonEntry:{}", callInfo.getBillNew().getId(), commonEntry.getValue());
                return false;
            }
        }
        return true;
    }


    public Leg getLeftLeg() {
        return callInfo.getLeftLeg();
    }

    public Leg getRightLeg() {
        return callInfo.getRightLeg();
    }


    public final String getForwardTransactionId(SipResponseEvent responseEvent) throws Exception {
        ClientTransaction clientTransaction = responseEvent.getClientTransaction();
        if (clientTransaction == null) {
            return null;
        }

        SIPRequest request = (SIPRequest) clientTransaction.getRequest();
        if (request == null || request.getApplicationData() == null) {
            return null;
        }
        return ((SIPRequest) request.getApplicationData()).getTransactionId();
    }

    public final String getForwardTransactionId(SIPRequest request) throws Exception {
        SIPRequest forwardRequest = (SIPRequest) request.getApplicationData();
        if (forwardRequest == null) {
            logger.error("[{}], [ControllerState-getForwardTransactionId], forwardRequest null", callInfo.getBillNew().getId());
        }
        return forwardRequest.getTransactionId();
    }
}
