package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.state.EarlyState;
import com.cmic.bizcontroller.controllerImpl.vod.state.PickUpState;
import com.cmic.bizcontroller.controllerImpl.vod.state.RingedState;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.constant.CommonConstant;
import gov.nist.javax.sip.message.SIPRequest;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VodPluginGroupKeeper implements PluginKeeper {

    private static Logger logger = LoggerFactory.getLogger(VodPluginGroupKeeper.class);

    @Override
    public Plugin createPlugin(PluginEvent event, ControllerState state) throws Exception {
        VodPluginGroup voDPluginGroup = new VodPluginGroup();
        voDPluginGroup.setLeftLeg((LeftLeg) state.getLeftLeg());
        voDPluginGroup.setRightLeg((RightLeg) state.getRightLeg());
        logger.info("[{}], VodPluginGroupKeeper prepare to create plugin", state.getCallInfo().getBillNew().getId());
        voDPluginGroup.setBillNew(state.getCallInfo().getBillNew());
        voDPluginGroup.setEngine(state.getCallInfo().getEngine());
        voDPluginGroup.setInitialInviteRequest(state.getCallInfo().getInitialInviteRequest());
        voDPluginGroup.setMediaSession(state.getCallInfo().getMediaSession());
        voDPluginGroup.setProdNum(state.getCallInfo().getProdNum());
        voDPluginGroup.setCallParameter(state.getCallInfo().getCallParameter());
        logger.info("VodPluginGroupKeeper VodParam:{}", state.getCallInfo().getCallParameter().getVodParam());
        voDPluginGroup.setLastLeftRequest(state.getCallInfo().getLastLeftRequest());
        voDPluginGroup.setLastRightResponse(state.getCallInfo().getLastRightResponse());
        voDPluginGroup.setControllerState(state);
        voDPluginGroup.setPreciseCallState(state.getCallInfo().getPreciseCallState());
        voDPluginGroup.setControllerState(state);
        voDPluginGroup.setNeedToForwardAck(!state.getCallInfo().isSequenceMode());
        voDPluginGroup.setCallParameter(state.getCallInfo().getCallParameter());
        voDPluginGroup.setBusinessEvent(state.getCallInfo().getBusinessEvent());
        return voDPluginGroup;
    }

    @Override
    public boolean shouldTake(PluginEvent event, ControllerState state) {
        PluginEvent.EventTypeEnum eventType = event.getType();
        logger.info("[{}],VodPluginGroupKeeper shouldTake eventType:{}", state.getCallInfo().getBillNew().getId(), eventType);
        if (!eventType.equals(PluginEvent.EventTypeEnum.VOD) && !eventType.equals(PluginEvent.EventTypeEnum.SIP_REQUEST)) {
            logger.info("[{}], eventType is not vod", state.getCallInfo().getBillNew().getId());
            return false;
        }
        Leg leftLeg = state.getLeftLeg();

        if (eventType.equals(PluginEvent.EventTypeEnum.SIP_REQUEST)) {
            SipRequestEvent requestEvent = event.getRequestEvent();
            String method = requestEvent.getRequest().getMethod();
            if (!SIPRequest.ACK.equals(method) && !SIPRequest.INVITE.equals(method)) {
                logger.info("{}, VodPluginGroupKeeper not ack and not reinvite", state.getCallInfo().getBillNew().getId());
                return false;
            }
            if (!(state.getCurrentState(leftLeg) instanceof EarlyState) && !(state.getCurrentState(leftLeg) instanceof RingedState)) {
                logger.info("{}, VodPluginGroupKeeper state not fit", state.getCallInfo().getBillNew().getId());
                return false;
            }

            CallParameter callParameter = state.getCallInfo().getCallParameter();

            /*if(StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.NO_VOD)){
                logger.info("{}, VodPluginGroupKeeper calledVideoPlayMode=NO_VOD ", state.getCallInfo().getBill().getBillId());
                return false;
            }*/

            if (!StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.RESUME_CALL_AFTER_PLAY) &&
                    !StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.STOP_CALL_AFTER_PLAY)) {
                logger.info("[{}], VodPluginGroupKeeper, calledVideoPlayMode is neither 1 nor 2", state.getCallInfo().getBillNew().getId());
                return false;
            }

            if (callParameter.getVodParam() == null) {
                logger.info("{}, VodPluginGroupKeeper VodParam is null ", state.getCallInfo().getBillNew().getId());
                return false;
            }

            return state.isAllLegCommonRequestFinish();
        }

        boolean stateOk = (state.getCurrentState(leftLeg) instanceof PickUpState) && state.isAllLegCommonRequestFinish();
        logger.info("[{}], shouldTake stateOk:{}, current LeftLeg State:{}", state.getCallInfo().getBillNew().getId(), stateOk, state.getCurrentState(leftLeg));
        return stateOk;
    }


}
