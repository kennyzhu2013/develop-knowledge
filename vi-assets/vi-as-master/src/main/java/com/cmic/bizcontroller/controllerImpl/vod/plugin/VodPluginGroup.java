package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.business.query.EventPush;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.util.StringUtil;
import com.cmic.constant.VodConstant;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * video on demand
 * <p>
 * Author: luohuanfa
 * Date: 2022/10/14
 */
public class VodPluginGroup extends AbstractPlugin {
    VodPlugin vodPlugin;
    VodPlugin lightScreenPlugin;
    VodRetreatePlugin voDRetreatePlugin;
    AbstractPlugin currentPlugin;
    VodEventProcessor vodEventProcessor;
    VodInfo vodInfo;

    boolean needToForwardAck = true;

    private volatile String lastAudio;
    private boolean complete = false;

    private VodStateInfo voDStateInfo;

    ReentrantLock lock = new ReentrantLock();

    private AtomicInteger eventLock = new AtomicInteger(0);

    private boolean isAudioVodPending = false;

    private PluginEvent audioVodEvent;

    @Override
    public PluginEventResultVo accept(PluginEvent event) throws Exception {
        if (StringUtil.isBlankOrNull(lastAudio)) {
            lastAudio = mediaSession.getMedia();
        }
        if (voDStateInfo == null) {
            voDStateInfo = new VodStateInfo();
        }

        if (vodEventProcessor == null) {
            vodEventProcessor = new VodEventProcessor(billNew, prodNum, mediaSession, callParameter);
        }

        PluginEventResultVo resultEvent = PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
        /*if (!isEventOn.compareAndSet(false, true)) {
            logger.warn("[{}], plugin processing event, could not process this event:{}", bill.getBillId(), event);
            return PluginEventResultVo.newPluginFailVo(VodConstants.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstants.VOD.VOD_HTTP_MSG.OTHER_REASON);
        }*/
        lock.lock();
        logger.info("[{}] VodPluginGroup accept start", billNew.getId());
        try {
            if (event.getType().equals(PluginEvent.EventTypeEnum.VOD)) {
                resultEvent = doVod(event);
            } else if (event.getType().equals(PluginEvent.EventTypeEnum.VODRE) && currentPlugin instanceof VodPlugin) {
                resultEvent = doVodRe(event);
            } else if (event.getType().equals(PluginEvent.EventTypeEnum.VODCOMPLETE) && currentPlugin instanceof VodPlugin) {
                resultEvent = doVodComplete(event);
            } else if (event.getType().equals(PluginEvent.EventTypeEnum.SIP_REQUEST) && event.getRequestEvent().getRequest().getMethod().equals(SIPRequest.ACK)) {
                logger.info("[{}], VodPluginGroup accept get ack request.", billNew.getId());
                resultEvent = doVodUponPickup(event);
            } else {
                if (currentPlugin == null && event.getType().equals(PluginEvent.EventTypeEnum.SIP_REQUEST)) {
                    SIPRequest request = event.getRequestEvent().getRequest();
                    String method = request.getMethod();
                    if (method.equalsIgnoreCase(SIPRequest.INVITE)) {
                        SIPResponse response500 = event.getLeg().createResponse(500, request);
                        event.getLeg().sendResponse(request, response500);
                        return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
                    }
                }
                logger.error("[{}], [VodPluginGroup-accept], error in accept, event:{}, currentPlugin:{}", billNew.getId(), event, currentPlugin);
            }
        } finally {
            //isEventOn.set(false);
            lock.unlock();
        }
        return resultEvent;
    }


    private VodInfo constructVodInfo(Map<String, Object> eventInfo) {
        VodInfo voDInfo = new VodInfo();
        String audioFile = (String) eventInfo.getOrDefault("audioFile", "");
        String videoFile = (String) eventInfo.getOrDefault("videoFile", "");
        String resId = (String) eventInfo.getOrDefault("resId", "");
        String uniCode = (String) eventInfo.getOrDefault("uniCode", "");
        String busCode = (String) eventInfo.getOrDefault("busCode", "");
        int vodDirection = (int) eventInfo.getOrDefault("direction", 0);
        //boolean aggressiveVod = (int) eventInfo.getOrDefault("aggressiveVod", 0) == 1;
        int aggressiveVod = (int) eventInfo.getOrDefault("aggressiveVod", 0);
        int playTimes = (int) eventInfo.getOrDefault("playTimes", 0);
        int mode = (int) eventInfo.getOrDefault("mode", 1);
        voDInfo.setDirection(vodDirection);
        //voDInfo.setVideoFile(videoFile);
        //voDInfo.setAudioFile(audioFile);
        voDInfo.setResId(resId);
        voDInfo.setAggressiveVod(aggressiveVod);
        voDInfo.setPlayTimes(playTimes);
        voDInfo.setMode(mode);
        voDInfo.setBusCode(busCode);
        switch (mode){
            case 1:
                voDInfo.setCalledVideoMode(VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY);
                break;
            case 2:
                voDInfo.setCalledVideoMode(VodConstant.VOD.PLAY_TYPE.VIDEO_ONLY);
                break;
            case 3:
                voDInfo.setCalledVideoMode(VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY);
                break;
            default:
                break;

        }
        /*if (StringUtils.isNotBlank(audioFile)) {
            voDInfo.setCalledVideoMode(VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY);
        } else {
            voDInfo.setCalledVideoMode(VodConstant.VOD.PLAY_TYPE.VIDEO_ONLY);
        }*/
        voDInfo.setUniCode(uniCode);
        voDInfo.setLastLeftRequest((SIPRequest) eventInfo.getOrDefault("lastLeftRequest", null));
        voDInfo.setLastRightResponse((SIPResponse) eventInfo.getOrDefault("lastRightResponse", null));
        voDInfo.setVodActivateSource(VodConstant.VOD.ACTIVATE_SOURCE.BY_REQUEST);
        voDInfo.setVodAudioCode(controllerState.getCallInfo().getVodAudioCode());
        buildVodFilePath(voDInfo);
        return voDInfo;
    }

//    public static void main(String[] args) {
//        VodInfo vodInfo = new VodInfo();
//        vodInfo.setMode(1);
//        vodInfo.setBusCode("workNum");
//        vodInfo.setResId("JsLJ58s220231205,hv760Rv420240517");
//        buildVodFilePath(vodInfo);
//        System.out.println(vodInfo.toString());
//    }

    private void buildVodFilePath(VodInfo vodInfo){
        String resId = vodInfo.getResId();
        String[] playCodes = resId.split(",");
        StringBuilder videoPlayBuilder = new StringBuilder();
        StringBuilder audioPlayBuilder = new StringBuilder();
        for(String playCode: playCodes){
            int len = playCode.length();
            String mediaEncode = playCode.substring(8, 10);
            boolean codeTemp = "T".equals(playCode.substring(len - 9, len - 8));
            String playFile = vodInfo.getBusCode().replace("_", "/") + "/" + playCode.substring(len - 8, len) + "/" + playCode;
            if (len == 20 && codeTemp) {
                playFile = "tmp/" + playFile;
            }
            //DtmfLogger.info(bill.getBillId(), "fillVodParams playFile:{}", playFile);
            // 当播放3gp格式视频时，xmedia会从3gp格式的视频中去找音频，而不会使用此音频参数
            // 当降级为音频播放时，使用的是amr格式接口
            if(vodInfo.getMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY ||
                    vodInfo.getMode() == VodConstant.VOD.PLAY_TYPE.VIDEO_ONLY){
                String videoPlayFile = "hS".equals(mediaEncode) ? playFile + ".h264" : playFile + ".3gp";
                videoPlayBuilder.append(videoPlayFile).append(",");
            }
            if (vodInfo.getMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_VIDEO_BOTH_PLAY ||
                    vodInfo.getMode() == VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY) {
                audioPlayBuilder.append(playFile).append(",");
            }
        }
        if(videoPlayBuilder.length() > 0){
            videoPlayBuilder.deleteCharAt(videoPlayBuilder.length()-1);
        }
        vodInfo.setVideoFile(videoPlayBuilder.toString());

        if(audioPlayBuilder.length() > 0){
            audioPlayBuilder.deleteCharAt(audioPlayBuilder.length()-1);
        }
        vodInfo.setAudioFile(audioPlayBuilder.toString());

    }

    private PluginEventResultVo doVod(PluginEvent event) throws Exception {
        Map<String, Object> eventInfo = event.getMapObject();
        int vodDirection = (int) eventInfo.getOrDefault("direction", 0);
        int mode = (int)eventInfo.getOrDefault("mode",0);
        if (currentPlugin != null) {
            if (currentPlugin instanceof VodPlugin) {
                //如果 direction 相同且当前播放的视频，则 1) 停掉当前视频的播放 2）播放新视频
                VodPlugin vodPlugin = (VodPlugin) currentPlugin;
                if(vodEventProcessor.isPlayingVideo() && mode == VodConstant.VOD.PLAY_TYPE.AUDIO_ONLY){
                    isAudioVodPending = true;
                    audioVodEvent = event;
                    logger.info("[{}] The direction is the same as previous and will play audio only!", billNew.getId());
                    //if(terminateCurrentPlay(vodPlugin.getVodInfo(),VodConstant.VOD.REASON_CODE.END_BY_FORCE)){
                    if(terminateCurrentPlay(vodPlugin.getVodInfo(),VodConstant.VOD.REASON_CODE.END_BY_REQUEST)){
                        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
                    }else{
                        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
                    }
                    //return vodPlugin.terminatePlugin();
                }

                if (vodDirection == vodPlugin.getVodInfo().getDirection() && vodEventProcessor.isPlayingVideo()) {
                    logger.info("[{}] The direction is the same as previous and currently it is playing video!", billNew.getId());
                    VodInfo newVodInfo = constructVodInfo(eventInfo);
                    //if(vodEventProcessor.terminateCurrentPlay(VodConstant.VOD.REASON_CODE.END_BY_REQUEST,vodPlugin.getVodInfo())){
                    if (terminateCurrentPlay(vodPlugin.getVodInfo(),VodConstant.VOD.REASON_CODE.END_BY_REQUEST)) {
                        vodPlugin.replaceNewVodInfo(newVodInfo);
                        int newPlayTid = vodEventProcessor.playVideo(null, newVodInfo);
                        newVodInfo.setPlayTid(newPlayTid);
                        vodInfo = newVodInfo;
                        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
                    } else {
                        logger.error("[{}] doVod direction is the same as previous, but fail to terminateCurrentPlay!", billNew.getId());
                        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
                    }

                } else if (vodEventProcessor.isPlayingVideo() || vodEventProcessor.isPlayingAudioOrVideo()) {
                    //如果 direction 不同或者之前播放的是语音，则 1）停掉当前视频的播放 2）结束当前插件
                    //if(!vodEventProcessor.terminateCurrentPlay(VodConstant.VOD.REASON_CODE.END_BY_REQUEST,vodPlugin.getVodInfo())){
                    if (!terminateCurrentPlay(vodPlugin.getVodInfo(),VodConstant.VOD.REASON_CODE.END_BY_REQUEST)) {
                        logger.error("[{}] doVod direction not the same as previous, fail to terminateCurrentPlay!", billNew.getId());
                        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.OTHER_REASON, VodConstant.VOD.VOD_HTTP_MSG.OTHER_REASON);
                    }
                    //currentPlugin = null;
                } else {
                    //如果当前 plugin 不是处于视频播放状态，而是其他状态，有可能在协商，需要返回失败
                    return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
                }
            } else {
                return currentPlugin.accept(event);
            }
        }

        int callerSupportWb = (int) eventInfo.getOrDefault("callerSupportWb", -1);
        int calleeSupportWb = (int) eventInfo.getOrDefault("calleeSupportWb", -1);
        int callerContactSupprtVideo = (int) eventInfo.getOrDefault("callerContactSupprtVideo", -1);
        int calleeContactSupportVideo = (int) eventInfo.getOrDefault("calleeContactSupportVideo", -1);

        voDStateInfo.setCalleeContactSupportVideo(calleeContactSupportVideo);
        voDStateInfo.setCallerContactSupprtVideo(callerContactSupprtVideo);
        voDStateInfo.setCallerSupportWb(callerSupportWb);
        voDStateInfo.setCalleeSupportWb(calleeSupportWb);

        // 检查是否能调起视频插播
        PluginEventResultVo pluginEventResultVo = validateVodActivation(voDStateInfo, vodDirection);
        if (!pluginEventResultVo.getCode().equals(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS)) {
            complete = true;
            return pluginEventResultVo;
        }

        vodPlugin = new VodPlugin();
        vodPlugin.setBillNew(billNew);
        vodPlugin.setEngine(engine);
        vodPlugin.setInitialInviteRequest(initialInviteRequest);
        vodPlugin.setLeftLeg(this.leftLeg);
        vodPlugin.setMediaSession(mediaSession);
        vodPlugin.setRightLeg(this.rightLeg);
        vodPlugin.setProdNum(this.prodNum);
        vodPlugin.setCallParameter(this.callParameter);
        vodPlugin.setControllerState(controllerState);
        vodInfo = constructVodInfo(eventInfo);
        vodPlugin.init(vodEventProcessor, vodInfo);
        currentPlugin = vodPlugin;
        return vodPlugin.accept(event);
    }

    private PluginEventResultVo doVodUponPickup(PluginEvent event) throws Exception {
        logger.info("[{}] doVodUponPickup start", billNew.getId());
        if (currentPlugin != null) {
            return currentPlugin.accept(event);
        }
        vodInfo = new VodInfo();
        logger.info("doVodUponPickup VodParam:{}", callParameter.getVodParam());
        vodInfo.setDirection(callParameter.getVodParam().getCalledVideoDirection());
        String calledVideoPlayFile = callParameter.getVodParam().getCalledVideoPlayFile();
        vodInfo.setVideoFile(calledVideoPlayFile);
        vodInfo.setAudioFile(callParameter.getVodParam().getCalledAudioPlayFile());
        vodInfo.setResId(callParameter.getVodParam().getVideoPlayCode());
        vodInfo.setUniCode("");
        vodInfo.setCalledVideoMode(callParameter.getVodParam().getCalledVideoMode());
        vodInfo.setLastLeftRequest(lastLeftRequest);
        vodInfo.setLastRightResponse(lastRightResponse);
        vodInfo.setVodActivateSource(VodConstant.VOD.ACTIVATE_SOURCE.BY_PARAM);
        vodInfo.setAggressiveVod(callParameter.getVodParam().getAggressiveVod());
        vodInfo.setPlayTimes(callParameter.getVodParam().getPlayTimes());
        vodInfo.setVodAudioCode(controllerState.getCallInfo().getVodAudioCode());
        lightScreenPlugin = new VodPlugin();
        lightScreenPlugin.setBillNew(billNew);
        lightScreenPlugin.setEngine(engine);
        lightScreenPlugin.setNeedToForwardAck(needToForwardAck);
        lightScreenPlugin.setInitialInviteRequest(initialInviteRequest);
        lightScreenPlugin.setControllerState(controllerState);
        lightScreenPlugin.setLeftLeg(this.leftLeg);
        lightScreenPlugin.setMediaSession(mediaSession);
        lightScreenPlugin.setRightLeg(this.rightLeg);
        lightScreenPlugin.setProdNum(this.prodNum);
        lightScreenPlugin.setCallParameter(this.callParameter);
        lightScreenPlugin.setPreciseCallState(this.preciseCallState);
        lightScreenPlugin.init(vodEventProcessor, vodInfo);
        currentPlugin = lightScreenPlugin;
        return currentPlugin.accept(event);
    }

    private PluginEventResultVo doVodRe(PluginEvent event) throws Exception {
        if (currentPlugin instanceof VodPlugin) {
            VodPlugin vodPlugin = (VodPlugin) currentPlugin;
            PluginEventResultVo resultVo = vodPlugin.completeStopVod(event.getMapObject());
            if (!StringUtils.equals(resultVo.getCode(), VodConstant.VOD.VOD_HTTP_CODE.SUCCESS)) {
                logger.error("[{}], [VodPluginGroup-doVodRe], recv vodre but vod stop fail!", billNew.getId());
                return resultVo;
            }
            if (vodPlugin.isComplete()) {
                voDRetreatePlugin = new VodRetreatePlugin();
                initVodRetreatePlugin(voDRetreatePlugin);
                currentPlugin = voDRetreatePlugin;
                return currentPlugin.accept(event);
            }
        }

        logger.error("[{}], [VodPluginGroup-doVodRe], recv vodre but vod is not complete", billNew.getId());
        return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.VOD_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.VOD_IS_IN_PROGRESS);
    }

    private PluginEventResultVo doVodComplete(PluginEvent event) throws Exception {
        logger.info("[{}] VodPluginGroup doVodComplete start.", billNew.getId());
        logger.info("[{}] VodPluginGroup doVodComplete voDPlugin:{},currentPlugin:{}", billNew.getId(), vodPlugin, currentPlugin);
        if (currentPlugin == null || !(currentPlugin instanceof VodPlugin)) {
            //如果找不到当前插件，或者当前插件不是插播插件, 则直接去 processor 里面处理，当插播结束回调返回比较晚时可能会发生这种情况
            vodEventProcessor.processVodCallback(event.getMapObject());
            return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
        }

        VodPlugin vodPlugin = (VodPlugin) currentPlugin;
        int currentPluginTid = vodPlugin.getVodInfo().getPlayTid();
        Map<String, Object> eventInfo = event.getMapObject();
        logger.info("[{}] doVodComplete currentPluginTid:{}, eventInfo:{}", billNew.getId(), currentPluginTid, eventInfo);
        int tid = (int) eventInfo.getOrDefault("tid", 0);
        if (tid != currentPluginTid) {
            //如果 tid 不等于当前插件的 playTid, 则直接去 processor 里面处理
            vodEventProcessor.processVodCallback(event.getMapObject());
            return PluginEventResultVo.newDealResultVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
        }

        //如果 tid 等于前插件的 playTid，则走普通的播放结束流程，需要结束插件
        PluginEventResultVo resultVo = vodPlugin.vodComplete(event.getMapObject());
        /*
        if (vodPlugin.isComplete() && vodPlugin.isNeedVodRetreate()) {
            voDRetreatePlugin = new VodRetreatePlugin();
            initVodRetreatePlugin(voDRetreatePlugin);
            currentPlugin = voDRetreatePlugin;
            return voDRetreatePlugin.accept(event);
        }*/
        dealSubPluginComplete();
        return resultVo;

        //return PluginEventResultVo.newDealResultVo(VodConstants.VOD.VOD_HTTP_CODE.VOD_IS_IN_PROGRESS, VodConstants.VOD.VOD_HTTP_MSG.VOD_IS_IN_PROGRESS);
    }


    @Override
    public PluginEventResultVo processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        PluginEventResultVo resultVo = null;
        PluginEventResultVo dealResult = null;
        SIPRequest request = requestEvent.getRequest();
        lock.lock();
        logger.info("************* VodPluginGroup processRequest method:{},currentPlugin{}", requestEvent.getRequest().getMethod(), currentPlugin);
        try {
            if (currentPlugin == null && requestEvent.getRequest().getMethod().equalsIgnoreCase(SIPRequest.INVITE)) {
                SIPResponse response500 = leg.createResponse(500, request);
                leg.sendResponse(request, response500);
                return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.NEGOTIATION_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.NEGOTIATION_IS_IN_PROGRESS);
            }
            resultVo = currentPlugin.doRequest(leg, requestEvent);
        } finally {
            lock.unlock();
        }
        dealResult = dealSubPluginComplete();
        if (dealResult != null) {
            logger.info("[{}], vodpluginGroup request:{} result replaced by retreate plugin:{}", billNew.getId(), requestEvent.getRequest().getCSeq(), dealResult);
        }
        // 回落插件的结果会覆盖原结果
        return dealResult == null ? resultVo : dealResult;
    }

    @Override
    public boolean isComplete() {
        lock.lock();
        try{
            boolean isCompleted =  complete || isPluginComplete(voDRetreatePlugin);
            if(isCompleted && isAudioVodPending){
                //如果有语音插播，则先结束当前的plugin，回落语音，再创建 vodplugin进行语音播放
                vodPlugin = new VodPlugin();
                vodPlugin.setBillNew(billNew);
                vodPlugin.setEngine(engine);
                vodPlugin.setInitialInviteRequest(initialInviteRequest);
                vodPlugin.setLeftLeg(this.leftLeg);
                vodPlugin.setMediaSession(mediaSession);
                vodPlugin.setRightLeg(this.rightLeg);
                vodPlugin.setProdNum(this.prodNum);
                vodPlugin.setCallParameter(this.callParameter);
                vodPlugin.setControllerState(controllerState);
                Map<String, Object> audioVodEventInfo = audioVodEvent.getMapObject();
                vodInfo = constructVodInfo(audioVodEventInfo);
                vodPlugin.init(vodEventProcessor, vodInfo);
                currentPlugin = vodPlugin;
                vodPlugin.accept(audioVodEvent);
                this.complete = false;
                voDRetreatePlugin = null;
                isAudioVodPending = false;
                audioVodEvent = null;
                return false;
            }else{
                return isCompleted;
            }
        }finally {
            lock.unlock();
        }

    }

    /**
     * 判断拉起插播的条件是否满足，若满足，则返回的消息为空，否则返回不满足的原因
     *
     * @param voDStateInfo
     * @param direction
     * @return
     */
    private PluginEventResultVo validateVodActivation(VodStateInfo voDStateInfo, int direction) {

        if (mediaSession.isVideoCallOnGoing()) {
            logger.warn("[{}] Video Call is on going, cannot trigger VOD!", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.VOD_IS_IN_PROGRESS, VodConstant.VOD.VOD_HTTP_MSG.VOD_IS_IN_PROGRESS);
        }

        if ((direction == VodConstant.VOD.TYPE.LEFT_ONLY || direction == VodConstant.VOD.TYPE.LEFT_RIGHT_BOTH) &&
                voDStateInfo.getLeftNegotiation() == VodConstant.VOD.NEGOTIATION.FAIL) {
            logger.warn("[{}] Caller does not support VOD", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.TERMINAL_NOT_SUPPORT_VOD, VodConstant.VOD.VOD_HTTP_MSG.TERMINAL_NOT_SUPPORT_VOD);
        }

        if ((direction == VodConstant.VOD.TYPE.RIGHT_ONLY || direction == VodConstant.VOD.TYPE.LEFT_RIGHT_BOTH) &&
                voDStateInfo.getRightNegotiation() == VodConstant.VOD.NEGOTIATION.FAIL) {
            logger.warn("[{}] Callee does not support VOD", billNew.getId());
            return PluginEventResultVo.newPluginFailVo(VodConstant.VOD.VOD_HTTP_CODE.TERMINAL_NOT_SUPPORT_VOD, VodConstant.VOD.VOD_HTTP_MSG.TERMINAL_NOT_SUPPORT_VOD);
        }

        return PluginEventResultVo.newDealSuccessVo(VodConstant.VOD.VOD_HTTP_CODE.SUCCESS, VodConstant.VOD.VOD_HTTP_MSG.SUCCESS);
    }

    @Override
    public PluginEventResultVo processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        PluginEventResultVo resultVo = currentPlugin.doResponse(leg, responseEvent);
        PluginEventResultVo dealResult = dealSubPluginComplete();
        if (dealResult != null) {
            logger.info("[{}], vodpluginGroup response:{} result replaced by retreate plugin:{}", billNew.getId(), responseEvent.getResponse().getCSeq(), dealResult);
        }
        // 回落插件的结果会覆盖原结果
        return dealResult == null ? resultVo : dealResult;
    }


    private PluginEventResultVo dealSubPluginComplete() throws Exception {
        if (currentPlugin instanceof VodPlugin) {
            VodPlugin voDPlugin = (VodPlugin) currentPlugin;
            if (voDPlugin.isComplete()) {
                voDStateInfo.setLeftNegotiation(voDPlugin.getLeftNegotiation());
                voDStateInfo.setRightNegotiation(voDPlugin.getRightNegotiation());
                if (voDPlugin.isNeedVodRetreate()) {
                    voDRetreatePlugin = new VodRetreatePlugin();
                    initVodRetreatePlugin(voDRetreatePlugin);
                    currentPlugin = voDRetreatePlugin;
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

    private void initVodRetreatePlugin(VodRetreatePlugin voDRetreatePlugin) {
        voDRetreatePlugin.setBillNew(billNew);
        voDRetreatePlugin.setEngine(engine);
        voDRetreatePlugin.setLeftLeg(leftLeg);
        voDRetreatePlugin.setRightLeg(rightLeg);
        voDRetreatePlugin.setMediaSession(mediaSession);
        voDRetreatePlugin.setInitialInviteRequest(initialInviteRequest);
        voDRetreatePlugin.setLastAudio(lastAudio);
    }

    @Override
    public void setLeftLeg(LeftLeg leftLeg) {
        this.leftLeg = leftLeg;
    }


    @Override
    public void processPluginTermination() throws Exception {
        logger.info("*** processPluginTermination vodInfo.getVodEventDo():{},complete:{}", vodInfo.getVodEventDo(), complete);
        if (complete && vodInfo.getVodEventDo() != null && eventLock.compareAndSet(0, 1)) {
            try {
                if (!vodInfo.isVodEventSent()) {
                    EventPush eventPush = new EventPush();
                    eventPush.pushEvent(businessEvent, vodInfo.getVodEventDo());
                    vodInfo.setVodEventSent(true);
                }
            } finally {
                eventLock.set(0);
            }

        }
    }

    private boolean terminateCurrentPlay(VodInfo vodInfo,int reasonCode) {
        boolean isTerminateSucc = vodEventProcessor.terminateCurrentPlay(reasonCode, vodInfo);
        logger.info("[{}] VodPluginGroup terminateCurrentPlay VodEventDo:{}", billNew.getId(), vodInfo.getVodEventDo());
        if (vodInfo.getVodEventDo() != null && eventLock.compareAndSet(0, 1)) {
            try {
                if (!vodInfo.isVodEventSent()) {
                    EventPush eventPush = new EventPush();
                    eventPush.pushEvent(businessEvent, vodInfo.getVodEventDo());
                    vodInfo.setVodEventSent(true);
                }
            } finally {
                eventLock.set(0);
            }
        }
        return isTerminateSucc;
    }

    public boolean isNeedToForwardAck() {
        return needToForwardAck;
    }

    public void setNeedToForwardAck(boolean needToForwardAck) {
        this.needToForwardAck = needToForwardAck;
    }
}
