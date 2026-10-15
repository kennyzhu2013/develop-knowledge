package com.cmic.bizcontroller.controllerImpl.vod.plugin.earlyvideo;

import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.Plugin;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginKeeper;
import com.cmic.bizcontroller.controllerImpl.vod.state.EarlyState;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 判断是否处理SipEvent
 * <p>
 * Author: luohuanfa
 * Date: 2023/1/4
 */
public class VideoRBTKeeper implements PluginKeeper {
    private static final Logger logger = LoggerFactory.getLogger(VideoRBTKeeper.class);
    AtomicBoolean haveRecv180 = new AtomicBoolean(false);

    @Override
    public Plugin createPlugin(PluginEvent event, ControllerState state) throws Exception {
        EarlyVideoPlugin earlyVideoPlugin = new EarlyVideoPlugin();
        earlyVideoPlugin.setLeftLeg((LeftLeg) state.getLeftLeg());
        earlyVideoPlugin.setRightLeg((RightLeg) state.getRightLeg());
        earlyVideoPlugin.setBillNew(state.getCallInfo().getBillNew());
        earlyVideoPlugin.setEngine(state.getCallInfo().getEngine());
        earlyVideoPlugin.setInitialInviteRequest(state.getCallInfo().getInitialInviteRequest());
        earlyVideoPlugin.setMediaSession(state.getCallInfo().getMediaSession());
        earlyVideoPlugin.setProdNum(state.getCallInfo().getProdNum());
        earlyVideoPlugin.setCallParameter(state.getCallInfo().getCallParameter());
        earlyVideoPlugin.setBusinessEvent(state.getCallInfo().getBusinessEvent());
        earlyVideoPlugin.setPreciseCallState(state.getCallInfo().getPreciseCallState());
        earlyVideoPlugin.setControllerState(state);
        earlyVideoPlugin.setNeedCacheSingallingLog(state.getCallInfo().isNeedCacheSignallingLog());
        earlyVideoPlugin.setInitialRightSeqNo(state.getCallInfo().getInitialRightSeqNo());
        earlyVideoPlugin.setHasSend183(state.getCallInfo().isHasSend183());
        earlyVideoPlugin.setCallSession(state.getCallInfo().getCallSession());
        earlyVideoPlugin.setDoesCalleeHasVideoRing(state.getCallInfo().isCalleeHasVideoRing());

        logger.info("VideoRBTKeeper state.getCallInfo().getBill():{}", state.getCallInfo().getBillNew());
        return earlyVideoPlugin;
    }

    @Override
    public boolean shouldTake(PluginEvent event, ControllerState state) {
        // 首先判断是否需要播放视频彩铃
        PluginEvent.EventTypeEnum eventType = event.getType();
        if (!eventType.equals(PluginEvent.EventTypeEnum.SIP_RESPONSE) || event.getLeg().isLeft()) {
            return false;
        }
        CallParameter callParameter = state.getCallInfo().getCallParameter();
        // 20231108新增判断，视频彩铃播放还需要判断是否要放咪咕视频。
        if (callParameter.noVideoFileToPlay()) {
            return false;
        }
        SIPResponse response = event.getResponseEvent().getResponse();
        if (response.getStatusCode() != 180) {
            return false;
        }
        if (BooleanUtils.isFalse(state.getCallInfo().getCallerContactSupportVideo())) {
            return false;
        }
        if (!haveRecv180.getAndSet(true)) {
            Leg leftLeg = state.getLeftLeg();
            logger.info("[{}] VideoRBTKeeper shouldTake left leg state:{},isAllLegCommonRequestFinish:{}", state.getCallInfo().getBillNew().getId(), state.getCurrentState(leftLeg), state.isAllLegCommonRequestFinish());
            boolean stateOk = false;
            int tryCount = 2;
            for (int i = 0; i < tryCount; i++) {
                boolean isAllTransactionFinished = state.isAllLegCommonRequestFinish();
                stateOk = (state.getCurrentState(leftLeg) instanceof EarlyState) && isAllTransactionFinished;
                if (stateOk) {
                    break;
                }
                logger.info("{}, can not accept the Video RBT event, commonRequestFinish:{}", state.getCallInfo().getBillNew().getId(), state.isAllLegCommonRequestFinish());
                if (!isAllTransactionFinished && i < tryCount - 1) {
                    try {
                        Controller originController = state.getCallInfo().getOriginController();
                        if (originController instanceof NewTwoLegController && ((NewTwoLegController) originController).pluginManager != null) {
                            NewTwoLegController twoLegController = (NewTwoLegController) originController;
                            twoLegController.pluginManager.businessAwait(200);
                        } else {
                            logger.error("[{}] VideoRBTKeeper not NewTwoLegController or pluginManager null", state.getCallInfo().getBillNew().getId());
                        }
                    } catch (InterruptedException e) {
                        logger.error("[{}] VideoRBTKeeper shouldTake retry exception.", state.getCallInfo().getBillNew().getId(), e);
                    }
                } else {
                    break;
                }
            }
            return stateOk;
        } else {
            logger.info("{}, duplicate 180 in shouldTake", state.getCallInfo().getBillNew().getId());
        }
        return false;
    }

}
