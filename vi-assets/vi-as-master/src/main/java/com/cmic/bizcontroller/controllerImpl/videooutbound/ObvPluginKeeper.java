// package com.cmic.bizcontroller.controllerImpl.videooutbound;
//
// import com.cmic.business.BusinessEvent;
// import com.cmic.business.businessparam.CallParameter;
// import com.cmic.business.businessparam.VodParam;
// import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
// import com.cmic.bizcontroller.controllerImpl.vod.plugin.Plugin;
// import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent;
// import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginKeeper;
// import com.cmic.bizcontroller.controllerImpl.vod.state.EarlyState;
// import com.cmic.bizcontroller.controllerImpl.vod.state.RingedState;
// import com.cmic.sip.event.SipRequestEvent;
// import com.cmic.sip.leg.Leg;
// import com.cmic.constant.CommonConstant;
// import com.cmic.constant.VodConstant;
// import com.dcc.config.model.BaseProdNum;
// import gov.nist.javax.sip.message.SIPRequest;
// import org.apache.commons.lang.StringUtils;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// public class ObvPluginKeeper implements PluginKeeper {
//
//     private static Logger logger = LoggerFactory.getLogger(ObvPluginKeeper.class);
//
//     @Override
//     public Plugin createPlugin(PluginEvent event, ControllerState state) throws Exception {
//         logger.info("{}, ObvPluginKeeper create idolCallPlugin", state.getCallInfo().getBillNew());
//         //AuthNum authNum = state.getCallInfo().getAuthNum();
//         //AuthCompany authCompany = state.getCallInfo().getAuthCompany();
//         BaseProdNum prodNum = state.getCallInfo().getProdNum();
//         BusinessEvent businessEvent = state.getCallInfo().getBusinessEvent();
//         ObvPluginGroup obvPluginGroup = new ObvPluginGroup();
//         obvPluginGroup.setProdNum(prodNum);
//         obvPluginGroup.setBillNew(state.getCallInfo().getBillNew());
//         obvPluginGroup.setEngine(state.getCallInfo().getEngine());
//         obvPluginGroup.setLeftLeg(state.getCallInfo().getLeftLeg());
//         obvPluginGroup.setRightLeg(state.getCallInfo().getRightLeg());
//         obvPluginGroup.setInitialInviteRequest(state.getCallInfo().getInitialInviteRequest());
//         obvPluginGroup.setMediaSession(state.getCallInfo().getMediaSession());
//         obvPluginGroup.setControllerState(state);
//         obvPluginGroup.setPreciseCallState(state.getCallInfo().getPreciseCallState());
//         obvPluginGroup.setCallParameter(state.getCallInfo().getCallParameter());
//         //videoPlayPluginGroup.setPluginModeAppendix(state.getCallInfo().getCallParameter().getPluginModeAppendix());
//         //videoPlayPluginGroup.setAuthCompany(authCompany);
//         obvPluginGroup.setBusinessEvent(businessEvent);
//         return obvPluginGroup;
//     }
//
//     @Override
//     public boolean shouldTake(PluginEvent event, ControllerState state) {
//         PluginEvent.EventTypeEnum eventType = event.getType();
//         if (!eventType.equals(PluginEvent.EventTypeEnum.SIP_REQUEST)) {
//             return false;
//         }
//         SipRequestEvent requestEvent = event.getRequestEvent();
//         String method = requestEvent.getRequest().getMethod();
//         Leg leftLeg = state.getLeftLeg();
//
//         if (!SIPRequest.ACK.equals(method) && !SIPRequest.INVITE.equals(method)) {
//             logger.info("{}, ObvPluginKeeper not ack and not reinvite", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//         if (!(state.getCurrentState(leftLeg) instanceof EarlyState) && !(state.getCurrentState(leftLeg) instanceof RingedState)) {
//             logger.info("{}, ObvPluginKeeper state not fit", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//         // 判断转接查询是否有爱豆的信息
//         /*PluginModeAppendix pluginModeAppendix = state.getCallInfo().getCallParameter().getPluginModeAppendix();
//         if (pluginModeAppendix == null ) {
//             logger.info("[{}], check OutboundVideoCallPluginKeeper, pluginModeAppendix null", state.getCallInfo().getBill().getBillId());
//             return false;
//         }*/
//         String calledVideoPlayMode = state.getCallInfo().getCallParameter().getCalledVideoPlayMode();
//         if (!StringUtils.equals(calledVideoPlayMode, CommonConstant.PLAY_MODE.RESUME_CALL_AFTER_PLAY) &&
//                 !StringUtils.equals(calledVideoPlayMode, CommonConstant.PLAY_MODE.STOP_CALL_AFTER_PLAY)) {
//             logger.info("[{}], check ObvPluginKeeper, calledVideoPlayMode is neither 1 nor 2", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         /*CallParameter callParameter = state.getCallInfo().getCallParameter();
//         if(StringUtils.equals(callParameter.getCalledVideoPlayMode(), CommonConstant.PLAY_MODE.NO_VOD)){
//             logger.info("{}, VodPluginGroupKeeper calledVideoPlayMode=NO_VOD ", state.getCallInfo().getBill().getBillId());
//             return false;
//         }*/
//
//         CallParameter callParameter = state.getCallInfo().getCallParameter();
//         if (callParameter.getVodParam() == null) {
//             logger.info("{}, ObvPluginKeeper VodParam is null ", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         if (StringUtils.isBlank(callParameter.getVodParam().getVideoPlayCode())) {
//             logger.info("{}, ObvPluginKeeper VodParam is null ", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         VodParam vodParam = callParameter.getVodParam();
//
//         int direction = vodParam.getCalledVideoDirection();
//         if (direction != VodConstant.VOD.TYPE.LEFT_ONLY && direction != VodConstant.VOD.TYPE.RIGHT_ONLY && direction != VodConstant.VOD.TYPE.LEFT_RIGHT_BOTH) {
//             logger.info("{}, ObvPluginKeeper VodParam direction invalid .", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         int mode = vodParam.getCalledVideoMode();
//         if (mode != VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY && mode != VodConstant.VOD.PLAY_TYPE.VIDEO_ONLY) {
//             logger.info("{}, ObvPluginKeeper VodParam mode invalid.", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         /*if (!pluginModeAppendix.getModeEnum().equals(PluginModeAppendix.ModeEnum.VOD_IDOL)){
//             logger.info("{}, check OutboundVideoCallPluginKeeper, plugin mode invalid", state.getCallInfo().getBill().getBillId());
//             return false;
//         }
//
//         JSONObject jsonResp = pluginModeAppendix.getJsonResp();
//         String videoPlayCode = PluginKeeperUtil.getStringFromJsonWithDefault(jsonResp, "videoPlayCode");
//         if(StringUtils.isBlank(videoPlayCode)){
//             logger.info("{}, check OutboundVideoCallPluginKeeper, videoPlayCode is blank.", state.getCallInfo().getBill().getBillId());
//             return false;
//         }*/
//
//         boolean stateOk = state.isAllLegCommonRequestFinish();
//         logger.info("{}, ObvPluginKeeper shouldTake stateOk:{}", state.getCallInfo().getBill().getBillId(), stateOk);
//         return stateOk;
//     }
// }
