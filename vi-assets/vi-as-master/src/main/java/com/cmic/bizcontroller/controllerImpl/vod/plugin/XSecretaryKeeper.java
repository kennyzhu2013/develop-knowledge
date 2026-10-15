// package com.cmic.bizcontroller.controllerImpl.vod.plugin;
//
// import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
// import com.cmic.bizcontroller.controllerImpl.vod.plugin.secretary.XSecretaryInfo;
// import com.cmic.bizcontroller.controllerImpl.vod.plugin.secretary.XSecretaryPlugin;
// import com.cmic.bizcontroller.controllerImpl.vod.state.EarlyState;
// import com.cmic.state.PreciseCallState;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// public class XSecretaryKeeper implements PluginKeeper {
//     Logger logger = LoggerFactory.getLogger(XSecretaryKeeper.class);
//
//     @Override
//     public Plugin createPlugin(PluginEvent event, ControllerState state) throws Exception {
//         XSecretaryPlugin xSecretaryPlugin = new XSecretaryPlugin();
//         XSecretaryInfo xSecretaryInfo = new XSecretaryInfo();
//         xSecretaryInfo.setInitialRequest(state.getCallInfo().getInitialInviteRequest());
//         xSecretaryInfo.setIfSupportedVideo(state.getCallInfo().isCallerSupportedVideo());
//         xSecretaryInfo.setHasSend180Event(state.getCallInfo().isHasSend180());
//         xSecretaryInfo.setOriginTelURL(state.getCallInfo().getOriginRightTelUrl());
//         xSecretaryInfo.setReason(state.getCallInfo().getReason());
//         xSecretaryPlugin.copyBaseAttrFromCallInfo(state);
//         xSecretaryInfo.setController(state.getCallInfo().getOriginController());
//         xSecretaryPlugin.setxSecretaryInfo(xSecretaryInfo);
//         xSecretaryPlugin.init();
//         return xSecretaryPlugin;
//     }
//
//     @Override
//     public boolean shouldTake(PluginEvent event, ControllerState state) {
//         if (!state.getCallInfo().getCallParameter().isNeedTransferSecretary()) {
//             return false;
//         }
//         if (state.getCallInfo().getCallParameter().isNeedEarly()) {
//             return false;
//         }
//         PreciseCallState preciseCallState = state.getCallInfo().getPreciseCallState();
//         if (state.getCurrentState(state.getLeftLeg()) instanceof EarlyState) {
//             if (preciseCallState.getCause() == 0 && preciseCallState.getStatusCode() == 0) {
//                 return false;
//             }
//             if (!preciseCallState.isHasRecv181Msg() && !preciseCallState.isNoUserResponse() && !preciseCallState.isRecvCancel()) {
//                 return true;
//             } else {
//                 logger.info("{}, trigger XSecretary but not meet necessary conditions", state.getCallInfo().getBill().getBillId());
//             }
//         }
//         return false;
//     }
// }
