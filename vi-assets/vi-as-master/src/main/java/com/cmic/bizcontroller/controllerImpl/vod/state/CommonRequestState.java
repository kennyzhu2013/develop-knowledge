package com.cmic.bizcontroller.controllerImpl.vod.state;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.CallInfo;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: luohuanfa
 * Date: 2022/10/9
 */
public class CommonRequestState {
    private Logger logger = LoggerFactory.getLogger(getClass());
    private SipMessageDesc requestDesc;
    private CommonStateEnum stateEnum;
    private final CallInfo callInfo;

    public CommonRequestState(CallInfo callInfo) {
        this.callInfo = callInfo;
        stateEnum = CommonStateEnum.START;
    }

    public void stateStepOn(SipMessageDesc sipMessageDesc) {
        if (sipMessageDesc == null) {
            return;
        }
        if (sipMessageDesc.getMethod().equals(SIPRequest.CANCEL) || sipMessageDesc.getMethod().equals(SIPRequest.BYE)) {
            return;
        }
        switch (stateEnum) {
            case START:
                if (sipMessageDesc.getRequestOrResponse().equals(SipMessageDesc.RequestOrResponse.REQUEST)) {
                    requestDesc = sipMessageDesc;
                    stateEnum = CommonStateEnum.REQUEST;
                } else {
                    logger.error("[{}], [CommonRequestState-stateStepOn], start state meet:{}", callInfo.getBillNew().getId(), sipMessageDesc);
                }
                break;
            case REQUEST:
                if (sipMessageDesc.getRequestOrResponse().equals(SipMessageDesc.RequestOrResponse.RESPONSE) &&
                        !sipMessageDesc.getSendOrRecv().equals(requestDesc.getSendOrRecv())) {
                    if (requestDesc.getMethod().equals(SIPRequest.INVITE)) {
                        stateEnum = CommonStateEnum.RESPONSE;
                    } else {
                        stateEnum = CommonStateEnum.FINISH;
                    }
                } else {
                    logger.info("[{}], [CommonRequestState-stateStepOn], request state meet:{}", callInfo.getBillNew().getId(), sipMessageDesc);
                }
                break;
            case RESPONSE:
                if (sipMessageDesc.getRequestOrResponse().equals(SipMessageDesc.RequestOrResponse.REQUEST) &&
                        sipMessageDesc.getMethod().equals(SIPRequest.ACK) &&
                        sipMessageDesc.getSendOrRecv().equals(requestDesc.getSendOrRecv())) {
                    requestDesc = null;
                    stateEnum = CommonStateEnum.FINISH;
                } else {
                    logger.info("[{}], [CommonRequestState-stateStepOn], response state meet:{}", callInfo.getBillNew().getId(), sipMessageDesc);
                }
                break;
            default:
                break;
        }
    }


    public CommonStateEnum getStateEnum() {
        return stateEnum;
    }

    public enum CommonStateEnum {
        // 开始进入的状态
        START,
        // 发出/收到请求
        REQUEST,
        // 发出/收到响应
        RESPONSE,
        FINISH
    }

    public SipMessageDesc getRequestDesc() {
        return requestDesc;
    }
}
