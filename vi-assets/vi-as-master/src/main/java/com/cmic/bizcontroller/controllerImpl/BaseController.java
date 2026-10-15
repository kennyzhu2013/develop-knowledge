package com.cmic.bizcontroller.controllerImpl;

import com.cmic.BillNew;
import com.cmic.bizcontroller.VideoBillHandler;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.CallInfo;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginEventResultVo;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginManager;
import com.cmic.bizcontroller.controllerImpl.vod.state.PickUpState;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.ConstantConf;
import com.cmic.constant.MediaConst;
import com.cmic.constant.VodConstant;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.event.leg.SzLeftLeg;
import com.cmic.service.*;
import com.cmic.sip.B2buaHelper;
import com.cmic.sip.engine.DtmfEngine;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.state.FinishState;
import com.cmic.state.FinishType;
import com.cmic.state.PreciseCallState;
import com.cmic.state.SipInfoCollector;
import com.cmic.util.*;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.message.Response;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;


/**
 * X模式流程 主叫支持precondition
 * 1、主被叫都支持precondition 支持主被叫放音
 * 2、被叫非P 被叫不能放音，主叫也不放音
 * 3、转接查询接口opType = 0，主叫放音 是否需要先查询接口再确定流程？
 * 4、主被叫都支持precondition，被叫没有sendrecv ，怎么兼容
 */
public class BaseController extends NewTwoLegController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // 流程相关：参数 & 事件 （可在构造函数植入）
    boolean isSendPlayEarlyCommand;
    private long initialRightSeqNo = 0;
    @Setter
    @Getter
    private boolean isHasHandleTerminate = false;

    public BaseController(BillNew billNew, BizNumAuthDo bizNumAuthDo, BusinessEvent businessEvent, CallParameter callParameter, SipEngine engine) {
        super(billNew.getId(), callParameter, engine);
        isNeedCacheSingallingLog = false;
        isSendPlayEarlyCommand = false;
        this.billNew = billNew;
        this.businessEvent = businessEvent;
        this.bizNumAuthDo = bizNumAuthDo;
        preciseCallState = new PreciseCallState(billNew);
        infoCollector = new SipInfoCollector(billNew);
        controllerState.getCallInfo().setBillNew(billNew);
        pluginManager = new PluginManager(controllerState);
    }

    @Override
    public void registerPlugins() {
        if (callParameter.isNeedVideoInsert()) {
            // 注册插播
            pluginManager.registerPlugin(PluginManager.PluginKeeperSelectorEnum.VOD);
        }
        if (callParameter.isNeedVideoRing()) {
            // 注册视频彩铃
            pluginManager.registerPlugin(PluginManager.PluginKeeperSelectorEnum.VRBT);
        }
    }

    private void pluginRecordStart(SIPRequest initialRequest, LeftLeg leftLeg, RightLeg rightLeg) throws Exception {
        CallInfo callInfo = controllerState.getCallInfo();
        // callInfo.setBill(bill);
        callInfo.setEngine(engine);
        callInfo.setInitialInviteRequest(initialRequest);
        // callInfo.setProdNum(this.prodNum);
        callInfo.setCallParameter(callParameter);
        callInfo.setBusinessEvent(businessEvent);
        callInfo.setPreciseCallState(preciseCallState);
        callInfo.setNeedCacheSignallingLog(isNeedCacheSingallingLog);
        callInfo.setInitialRightSeqNo(initialRightSeqNo);
        callInfo.setOriginController(this);
        callInfo.setCallerSupportedVideo(callRecorder.isCallerSupportVideo());
        callInfo.setCallSession(callSession);
        callInfo.setVodAudioCode(vodAudioCode);

        if (callRecorder.isCallerSupportVideo()) {
            callRecorder.setCallerContactSupprtVideo(VodConstant.VOD.CONTACT_HEADER.CONTAIN_VIDEO);
            callInfo.setCallerContactSupportVideo(true);
        } else {
            callInfo.setCallerContactSupportVideo(false);
            callRecorder.setCallerContactSupprtVideo(VodConstant.VOD.CONTACT_HEADER.NOT_CONTAIN_VIDEO);
        }

        byte[] rawContent = initialRequest.getRawContent();
        if (rawContent != null && rawContent.length > 0) {
            if (SdpHelper.isSDPMediaContainAmrWb(rawContent)) {
                callRecorder.setCallerSupportWb(VodConstant.VOD.SDP.CONTAIN_AMRWB);
            } else {
                callRecorder.setCallerSupportWb(VodConstant.VOD.SDP.NOT_CONTAIN_AMRWB);
            }
        }

        controllerState.consumeFirstInviteRequest(leftLeg);
        controllerState.consumeFirstInviteRequest(rightLeg);
        callInfo.setLeftLeg(leftLeg);
        callInfo.setRightLeg(rightLeg);
        callInfo.setMediaSession(mediaSession);
    }



    @Override
    public void start(SIPRequest initialRequest) throws Exception {
        logger.info("[{}] BaseController start.", sessionId);
        this.initialInviteRequest = initialRequest;
        /* 初始化左腿 **/
        SzLeftLeg leftLeg = ((DtmfEngine) engine).createSZLeg(initialRequest);
        bindLeftLeg(leftLeg);
        leftLeg.acceptRequest(initialRequest);
        this.initialSeq = initialRequest.getCSeq().getSeqNumber();
        preciseCallState.setInitialSeq(initialSeq);
        XmodePromExporterService.getInstance().incLeftLegNum();
        // 终端型号等
        // TerminalUtil.recordTerminateBrand(leftLeg, initialRequest, bill);
        try {


            mediaSession = MediaService.createMediaSession(callParameter, billNew, initialRequest);
            sessionReInit();
            recordInitialRequest(initialRequest);
            handleStreamBUpdate();
            // bill.setCallForwardTime(bill.getStartHandleTime().getTime());
            // 获取forward result
            SIPRequest request = B2buaHelper.createInviteRequest(this.engine, initialRequest);
            B2buaHelper.linkRequest(initialRequest, request);
            // 如果是700路由则为I-CSCF触发业务，否则为S-CSCF触发业务
            if(billNew.getOriginalCalleeNumber().startsWith("700")) {
                // 70014前缀路由进来，被叫号码需删除70014前缀出局，主叫号码和号显无需任何变更
                SipUtil.setNextRoute4Dcc(request, initialRequest, this.engine);
                DtmfLogger.info(sessionId, "BaseController bill calleeNumber={}", billNew.getCalleeNumber());
                String telB = adaptTelB(callParameter.getForwardCallee());
                TelURL requestURI = (TelURL) this.engine.addressFactory.createURI(telB);
                request.setRequestURI(requestURI);
                request.setHeader(engine.headerFactory.createHeader("To", telB));
                controllerState.getCallInfo().setOriginRightTelUrl(requestURI);
            } else {
                // 工作号签约进来，无需更改被叫信息，无需更改主叫和号显信息
                SipUtil.setNextRoute4Gzh(request, initialRequest, this.engine);
                URI requestURI = initialRequest.getRequestURI();
                request.setRequestURI(requestURI);
                controllerState.getCallInfo().setOriginRightTelUrl(requestURI);
            }
            if (request.getRawContent() != null) {
                if (callParameter.isUseSDP()) {
                    DtmfLogger.info(sessionId, "use SDP process version");
                    request.setContent(mediaSession.processLeftSDPV3(request), request.getContentTypeHeader());
                } else {
                    request.setContent(mediaSession.processLeftSDP(request.getRawContent()), this.engine.contentTypeHeader);
                }
            }
            if (callParameter.isCanPassVideo() && callRecorder.isCallerSupportVideo() || callParameter.isForceContactWithVideo()) {
                request.removeHeader(ContactHeader.NAME);
                request.setHeader(this.engine.contactHeaderWithVideo);
            }

            billNew.setCallForwardTime(billNew.getHandleStartTime());
            RightLeg rightLeg = this.engine.createRightLeg(request);
            bindRightLeg(rightLeg);
            pluginRecordStart(initialRequest, leftLeg, rightLeg); // 如果183回来得太快，这里必须放发送前面
            rightLeg.sendInitialInvite();
            callRecorder.setRightLeg(rightLeg);
            // businessEvent.doRecvInvite();
            businessEvent.doSendInvite();
            initialRightSeqNo = request.getCSeq().getSeqNumber();
            XmodePromExporterService.getInstance().incRightLegNum();
        } catch (Exception e) {
            logger.error("billId:{}", sessionId, e);
        }

    }

    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        String method = responseEvent.getMethod();

        int statusCode = response.getStatusCode();
        Leg oppositeLeg = getOppositeLeg(leg);
        // bill.setLastRespCode(statusCode);

        if (statusCode == Response.SESSION_PROGRESS) {
            if (!leg.isLeft()) {
                controllerState.getCallInfo().setHasSend183(true);
            }
            callRecorder.setRightIsRequirePrecondition(SipUtil.isRequirePrecondition(response));
        }

        if (statusCode == Response.RINGING && !leg.isLeft()) {
            controllerState.getCallInfo().setHasSend180(true);
        }

        // 收集消息用于判断精准话单，如果判断被叫拒接会返回true
        boolean isReject = preciseCallState.extractResponseMsgPropertiesJudgeReject(response);
        if (isSendPlayEarlyCommand && isReject) {
            mediaSession.dccStopMedia();
        }

        // =====================do Plugin
        PluginEventResultVo pluginEventResultVo = callStateManager.pluginTakeResponse(leg, responseEvent);
        boolean isPass = pluginEventResultVo.isHaveFoundPlugin() && pluginEventResultVo.isNeitherPassOn();
        if (isPass) {
            return;
        }

        if (!pluginEventResultVo.isHaveFoundPlugin() || (pluginEventResultVo.isHaveFoundPlugin() && pluginEventResultVo.isSignalPassOn())) {
            // 先收集消息然后再触发结束事件
            oppositeLeg.forwardResponse(responseEvent, mediaSession, message -> {
                // 如果双腿不是标准p流程就不修改P-Early-Media字段
                if (statusCode == Response.RINGING && callRecorder.isLeftIsSuppotedPrecondition() && callRecorder.isRightIsRequirePrecondition() && !callParameter.getCallerPlayUrl().isEmpty()) {
                    // 若主叫为IOS时，同时右腿还要非inactive
                    if (!callRecorder.isLeftIsIOS() || !(leg.pearlyMedia.isEmpty() || "inactive".equalsIgnoreCase(leg.pearlyMedia) || "supported".equalsIgnoreCase(leg.pearlyMedia))) {
                        if (preciseCallState.canPlayearly()) {
                            message.setHeader(this.engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
                        }
                    }
                }
                if (statusCode == Response.RINGING || statusCode == Response.SESSION_PROGRESS || statusCode == Response.OK) {
                    message.removeHeader(ContactHeader.NAME);
                    message.setHeader(this.engine.contactHeaderWithVideo);
                }
            }, new VideoBillHandler(this), false);
            // plugin更新状态机
            callStateManager.pluginForwardStateFlushed(oppositeLeg, responseEvent);
            if (statusCode / 100 >= 4 && method.equalsIgnoreCase(SIPRequest.UPDATE) && response.getRawContent() == null) {
                mediaSession.resetEndPoint();
            }
        }

        // 双腿支持precondition才可以进行放音
        if (statusCode == Response.RINGING && callRecorder.isLeftIsSuppotedPrecondition() && callRecorder.isRightIsRequirePrecondition() && preciseCallState.canPlayearly()) {
            if (!isSendPlayEarlyCommand) {
                if (StringUtils.isNotBlank(callParameter.getCallerPlayUrl())) {
                    // 亲测还要加上supported，当180带supported时也是没有媒体声音
                    boolean isForce = (leg.pearlyMedia.isEmpty() || "inactive".equalsIgnoreCase(leg.pearlyMedia)) || "supported".equalsIgnoreCase(leg.pearlyMedia);
                    // 左腿是ios且inactive的话不放音
                    if (!callRecorder.isLeftIsIOS() || !isForce) {
                         MediaService.dccPlayMedia(callParameter.getCallerPlayUrl(),"DuDu",
                                "", "", MediaConst.PLAY_PERIOD.EARLY, MediaConst.PRIORITY.LOCAL, MediaConst.PLAY_DIRECTION.CALLER,
                                callParameter.getRecordMode(), null, mediaSession, businessEvent, false, billNew.getCompanyId(),true);
                    } else {
                        logger.info("======bill ID:{}===the case of that ios didn't play early media", sessionId);
                    }
                    isSendPlayEarlyCommand = true;
                }
            }
        }

        if (!leg.isLeft() && statusCode == 200 && method.equalsIgnoreCase(SIPRequest.INVITE) && !callRecorder.isHasRecv200ok()) {
            mediaSession.processUponPickup(true);
        }
        callSession.consumeResponse(leg, responseEvent);
        streamManager.handleStreamStartTime(leg, statusCode, method, callRecorder.isHasSend180Event());

    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {

        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();

        boolean isReject = preciseCallState.extractRequestMsgPropertiesJudgeReject(leg.isLeft(), request);
        if (isReject && isSendPlayEarlyCommand) {
            mediaSession.dccStopMedia();
        }

        PluginEventResultVo pluginEventResultVo = callStateManager.pluginTakeRequest(leg, requestEvent);
        if (pluginEventResultVo.isHaveFoundPlugin() && pluginEventResultVo.isNeitherPassOn()) {
            logger.info("[{}] processRequest have found plugin and no need to process this signal further", sessionId);
            return;
        }

        if (method.equalsIgnoreCase(SIPRequest.BYE) && billNew.getCallStartTime() != null) {
            billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
            logger.info("[{}] set call end time :{}", sessionId, billNew.getCallEndTime());
        }
        long seqNumber = request.getCSeq().getSeqNumber();
        // 右腿的invite必定是reinvite，若是左腿的，大于initialSeq就是reinvite
        if (method.equalsIgnoreCase(SIPRequest.INVITE) && (!leg.isLeft() || seqNumber > initialSeq)) {
            billNew.setCallState(ConstantConf.CALL_STATE_REINVITE);
        }
        // 只有左腿ACK我们才需要放音
        if (leg.isLeft() && method.equalsIgnoreCase(SIPRequest.ACK) && seqNumber == initialSeq) {
            XmodePromExporterService.getInstance().incCall200Num();
            // XmodePromExporterService.getInstance().incInviteOKInfoCount(bill);
            // 收到主叫ACK，设置通话状态为6-收到ACK（200-INVITE之后）
            billNew.setCallState(ConstantConf.CALL_STATE_ACK);
            if (StringUtils.isNotBlank(callParameter.getCalledPlayUrl())) {
                if (!mediaSession.isPickupAudioPlaying()) {
                    mediaSession.setPickupAudioPlaying(true);
                    MediaService.dccPlayMedia(callParameter.getCalledPlayUrl(),"",
                            "", "", MediaConst.PLAY_PERIOD.INCALL, MediaConst.PRIORITY.LOCAL, callParameter.getCalledPlayDirection(),
                            callParameter.getRecordMode(), null, mediaSession, businessEvent, false, billNew.getCompanyId(), false);
                }
            }
            //判断是否需要视频通话透传，如果需要则设置retainNetVideo为true
            // mediaSession.setRetainNetVideo(businessEvent.isVideoCallTransmission());

        }

        if (!pluginEventResultVo.isHaveFoundPlugin() || (pluginEventResultVo.isHaveFoundPlugin() && pluginEventResultVo.isSignalPassOn())) {
            // 如果主叫视频彩铃和被叫视频彩铃冲突了，则关闭被叫视频彩铃
            stopCalleeVideoRBTIfConflict(request, leg);
            forwardRequestUponState(leg, requestEvent, callParameter.isCanPassVideo());
        }
    }

    private void stopCalleeVideoRBTIfConflict(SIPRequest request, Leg leg) throws ParseException {
        String method = request.getMethod();
        // 被叫视频彩铃：1. 未摘机 2. update请求 3. 右腿 4. 有video(节省内存将此条件代码后置)
        boolean didNotPickUp = controllerState.getCurrentState(leftLeg).getStateIndex() < PickUpState.PickUpStateCode;
        boolean isCalleeVideoRBTApply = didNotPickUp && method.equals(SIPRequest.UPDATE) && !(leg instanceof LeftLeg);
        boolean isCallerVideoRBTReady = !callParameter.noVideoFileToPlay();
        // 需要关闭被叫视频彩铃的情况: 1. 被叫确实要放视频彩铃 2. 我们自己要放
        if (mediaSession.isRetainNetVideo() && isCalleeVideoRBTApply && isCallerVideoRBTReady) {
            String sdpStr = new String(request.getRawContent());
            boolean containsVideo = sdpStr.contains("m=video");
            if (containsVideo) {
                controllerState.getCallInfo().setCalleeHasVideoRing(true);
                sdpStr = sdpStr.replaceFirst("(\\nm=video\\s+)\\d+", "$10");
                if (callParameter.isUseSDP()) {
                    request.setContent(sdpStr.getBytes(), request.getContentTypeHeader());
                } else {
                    request.setContent(sdpStr.getBytes(), this.engine.contentTypeHeader);
                }
            }
        }
    }

    // private void releaseCallAfterOneMinute() {
    //     ScheduleService.getInstance().schedule(() -> {
    //         try {
    //             // 超时系统挂机状态设为25
    //             billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
    //             callParameter.setDoParameterFinishState(FinishState.UNRECOGNIZED_STATE);
    //             leftLeg.sendByeOr487();
    //             rightLeg.sendByeOrCancel();
    //         } catch (Exception e) {
    //             logger.error("[{}], releaseCallAfterOneMinute error", sessionId, e);
    //         }
    //     }, 1, TimeUnit.MINUTES);
    // }

    @Override
    public void terminated() throws Exception {
        //最大通话时长和sipEngine两条线程同时触发时，先进来的触发
        if (!callParameter.getHasStartTerminated().getAndSet(true)) {

            if (!callParameter.isHasStartMaxCallController() && callSession != null && callSession.getMaxCallControlTask() != null && !callSession.getMaxCallControlTask().isDone()) {
                callSession.getMaxCallControlTask().cancel(true);
            }
            logger.info("[{}] thread={} {}.terminated() ", sessionId, Thread.currentThread().getId(), this.getClass().getName());
            billNew.setHandleEndTime(new Timestamp(System.currentTimeMillis()));
            if (!isHasHandleTerminate()) {
                if (mediaSession != null) {
                    mediaSession.stop(billNew.getHandleEndTime().getTime());
                }
                // 判断是否存在通话，存在则填充通话结束时间
                if (billNew.getCallStartTime() != null && billNew.getCallEndTime() == null) {
                    billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
                    logger.info("set call end time12 :{}", billNew.getCallEndTime());
                }
            }
            handleEnd();
        }

    }

    @Override
    public void onSystemExitEvent() {
        logger.info("onSystemExitEvent id={}", sessionId);
        preciseCallState.setSystemExit(true);
    }

    @Override
    public void onCleanOutEvent() {
        preciseCallState.setControllerTimeOut(true);
    }

    private void handleEnd() {
        if (!isHasHandleTerminate()) {
            int[] billState = preciseCallState.getFinalState();
            if (callParameter.getDoParameterFinishState() == 0 && (billState[1] == FinishState.UNRECOGNIZED_STATE || billState[1] == FinishState.NEED_ASR) && !"1".equalsIgnoreCase(DevHelp.forceAsr)) {
                logger.info("delay judge finishState, add a task to ScheduleService ; bill id is {}", sessionId);
                ScheduleService.getInstance().schedule(() -> {
                    int[] newBillState = preciseCallState.getFinalState();
                    billNew.setFinishType(newBillState[0]);
                    billNew.setFinishState(newBillState[1]);
                    insertAndMonitor();
                }, 300, TimeUnit.MILLISECONDS);
            } else {
                billNew.setFinishType(billState[0]);
                billNew.setFinishState(billState[1]);
                logger.info("[{}] finishState is {}, bill finishState:{}", sessionId, billState[1], billNew.getFinishState());
                insertAndMonitor();
            }
        } else {
            insertAndMonitor();
        }
    }

    private void insertAndMonitor() {
        // x模式中优先从控制接口返回的参数或者结果设置精准话单状态
        if (callParameter.getDoParameterFinishState() > 0 && !preciseCallState.isSystemExit()) {
            billNew.setFinishType(FinishType.LEFT_LEG_STOP);
            billNew.setFinishState(callParameter.getDoParameterFinishState());
        }
        // 强制ASR
        if ("1".equalsIgnoreCase(DevHelp.forceAsr)) {
            logger.info("bill id {} open forceAsr", sessionId);
            billNew.setFinishState(FinishState.NEED_ASR);
        }
        int mediaServerId = mediaSession == null ? 0 : mediaSession.getServerId();
        // int isNeedAsr = billNew.getFinishState() == 0 ? 1 : 0;
        int storeId = mediaSession == null ? 0 : mediaSession.getStoreId();
        billNew.setMsId(mediaServerId);
        // billNew.setIsNeedAsr(isNeedAsr);
        billNew.setStoreId(storeId);
        DbAsyncService.getInstance().submitDbUpdate(this);
    }

    /**
     * 对特服号码进行出网规整
     *
     * @param calleeNum 被叫号码
     * @return 规整后的被叫号码
     */
    protected String adaptTelB(String calleeNum) {
        //return TelBRegulator.getInstance().regulate(calleeNum, authNum);
        // 如果是国际号码，先不规整
        if (calleeNum.startsWith("+") && !calleeNum.startsWith("+86")) {
            return "tel:" + calleeNum;
        }
        if(calleeNum.startsWith("00") && !calleeNum.startsWith("0086")) {
            return "tel:+" + calleeNum.substring(2);
        }
        return "tel:+86" + PhoneNumUtil.getPure11NumberWithout0or86(calleeNum);
    }

}
