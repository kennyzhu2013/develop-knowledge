package com.cmic.bizcontroller.controllerImpl.videooutbound;

import com.cmic.business.query.EventPush;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.AbstractPlugin;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEvent;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEventResultVo;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.VodRetreatePlugin;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import com.cmic.util.StringUtil;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPRequest;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 组合视频外呼中发起视频和回落插件
 * <p>
 * Author: luohuanfa
 * Date: 2023/2/23
 */
public class ObvPluginGroup extends AbstractPlugin {
    ObvPlayPlugin obvPlayPlugin;
    VodRetreatePlugin vodRetreatePlugin;
    AbstractPlugin currentPlugin;
    private volatile String lastAudio;
    private boolean complete = false;
    ObvPlayInfo obvPlayInfo;

    private AtomicBoolean isEventOn = new AtomicBoolean(false);
    private AtomicInteger eventLock = new AtomicInteger(0);


    @Override
    public PluginEventResultVo accept(PluginEvent event) throws Exception {
        if (StringUtil.isBlankOrNull(lastAudio)) {
            lastAudio = mediaSession.getMedia();
        }
        PluginEventResultVo resultEvent = PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        if (!isEventOn.compareAndSet(false, true)) {
            logger.warn("billId:{}, plugin processing event, could not process this event:{}", billNew.getId(), event);
            return resultEvent;
        }
        try {
            if (event.getType().equals(PluginEvent.EventTypeEnum.SIP_REQUEST) && event.getRequestEvent().getRequest().getMethod().equals(SIPRequest.ACK)) {
                obvPlayPlugin = new ObvPlayPlugin();
                currentPlugin = obvPlayPlugin;
                initVideoPlayPlugin(obvPlayPlugin);
                resultEvent = obvPlayPlugin.accept(event);
            } else if (event.getType().equals(PluginEvent.EventTypeEnum.VODCOMPLETE) && currentPlugin instanceof ObvPlayPlugin) {
                resultEvent = doVodComplete(event);
            } else {
                logger.error("[{}], [VideoPlayPluginGroup-accept], invalid accept, event:{}, currentPlugin:{}", billNew.getId(), event, currentPlugin);
            }
        } finally {
            isEventOn.set(false);
        }
        return resultEvent;
    }

    private PluginEventResultVo doVodComplete(PluginEvent event) throws Exception {
        obvPlayPlugin.vodComplete(event.getMapObject());
        if (obvPlayPlugin.isComplete()) {
            if (obvPlayPlugin.isNeedVodRetreate()) {
                vodRetreatePlugin = new VodRetreatePlugin();
                initVodRetreatePlugin(vodRetreatePlugin);
                currentPlugin = vodRetreatePlugin;
                return vodRetreatePlugin.accept(event);
            }
            return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
        }
        return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.VOD_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.VOD_IS_IN_PROGRESS);
    }

    private void initVodRetreatePlugin(VodRetreatePlugin voDRetreatePlugin) {
        voDRetreatePlugin.setBillNew(billNew);
        voDRetreatePlugin.setEngine(engine);
        voDRetreatePlugin.setLeftLeg(leftLeg);
        voDRetreatePlugin.setRightLeg(rightLeg);
        voDRetreatePlugin.setMediaSession(mediaSession);
        voDRetreatePlugin.setInitialInviteRequest(initialInviteRequest);
        voDRetreatePlugin.setLastAudio(lastAudio);
    }

    private void initVideoPlayPlugin(ObvPlayPlugin obvPlayPlugin) {
        obvPlayPlugin.setProdNum(prodNum);
        obvPlayPlugin.setBillNew(billNew);
        obvPlayPlugin.setEngine(engine);
        obvPlayPlugin.setLeftLeg(leftLeg);
        obvPlayPlugin.setRightLeg(rightLeg);
        obvPlayPlugin.setInitialInviteRequest(initialInviteRequest);
        obvPlayPlugin.setMediaSession(mediaSession);
        obvPlayPlugin.setControllerState(controllerState);
        obvPlayPlugin.setPreciseCallState(preciseCallState);
        obvPlayPlugin.setCallParameter(this.callParameter);

        obvPlayInfo = new ObvPlayInfo();
        obvPlayInfo.setDirection(callParameter.getVodParam().getCalledVideoDirection());
        String calledVideoPlayFile = callParameter.getVodParam().getCalledVideoPlayFile();
        obvPlayInfo.setVideoFile(calledVideoPlayFile);
        obvPlayInfo.setPlayTimes(callParameter.getVodParam().getPlayTimes());
        obvPlayInfo.setAudioFile(callParameter.getVodParam().getCalledAudioPlayFile());
        obvPlayInfo.setInteractive(false);
        obvPlayInfo.setVideoPlayCode(callParameter.getVodParam().getVideoPlayCode());
        obvPlayInfo.setAggressiveVod(callParameter.getVodParam().isAggressiveVodForObv());
        obvPlayPlugin.init(obvPlayInfo);

    }

    @Override
    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        PluginEventResultVo resultVo = currentPlugin.doResponse(leg, responseEvent);
        PluginEventResultVo dealResult = dealSubPluginComplete();
        // 回落插件的结果会覆盖原结果
        return dealResult == null ? resultVo : dealResult;
    }

    @Override
    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        PluginEventResultVo resultVo = currentPlugin.doRequest(leg, requestEvent);
        PluginEventResultVo dealResult = dealSubPluginComplete();
        // 回落插件的结果会覆盖原结果
        return dealResult == null ? resultVo : dealResult;
    }


    private PluginEventResultVo dealSubPluginComplete() throws Exception {
        if (currentPlugin instanceof ObvPlayPlugin) {
            if (obvPlayPlugin.isComplete()) {
                if (obvPlayPlugin.isNeedVodRetreate()) {
                    vodRetreatePlugin = new VodRetreatePlugin();
                    initVodRetreatePlugin(vodRetreatePlugin);
                    currentPlugin = vodRetreatePlugin;
                    return currentPlugin.accept(PluginEvent.EventTypeEnum.VODRE.wrapMapToEvent(null));
                } else {
                    complete = true;
                }
            }
            return null;
        }

        if (currentPlugin instanceof VodRetreatePlugin) {
            VodRetreatePlugin voDRetreatePlugin = (VodRetreatePlugin) currentPlugin;
            if (voDRetreatePlugin.isComplete()) {
                complete = true;
            }
        }
        return null;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }


    @Override
    public void processPluginTermination() throws Exception {
        if (complete && obvPlayInfo.getVodEventDo() != null && eventLock.compareAndSet(0, 1)) {
            try {
                if (!obvPlayInfo.isVodEventSent()) {
                    EventPush eventPush = new EventPush();
                    eventPush.pushEvent(businessEvent, obvPlayInfo.getVodEventDo());
                    obvPlayInfo.setVodEventSent(true);
                }
            } finally {
                eventLock.set(0);
            }
        }
    }
}
