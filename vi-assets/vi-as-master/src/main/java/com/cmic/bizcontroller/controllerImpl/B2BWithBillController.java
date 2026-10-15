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
import com.cmic.recording.MediaSession;
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
public class B2BWithBillController extends NewTwoLegController {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    // 流程相关：参数 & 事件 （可在构造函数植入）
    boolean isSendPlayEarlyCommand;
    private long initialRightSeqNo = 0;
    @Setter
    @Getter
    private boolean isHasHandleTerminate = false;

    public B2BWithBillController(BillNew billNew, BizNumAuthDo bizNumAuthDo, BusinessEvent businessEvent, CallParameter callParameter, SipEngine engine) {
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
    }

    @Override
    public void start(SIPRequest initialRequest) throws Exception {
        logger.info("[{}] B2BWithBillController start.", sessionId);
        this.initialInviteRequest = initialRequest;
        /* 初始化左腿 **/
        SzLeftLeg leftLeg = ((DtmfEngine) engine).createSZLeg(initialRequest);
        bindLeftLeg(leftLeg);
        leftLeg.acceptRequest(initialRequest);
        this.initialSeq = initialRequest.getCSeq().getSeqNumber();
        preciseCallState.setInitialSeq(initialSeq);
        XmodePromExporterService.getInstance().incLeftLegNum();
        try {
            mediaSession = new MediaSession(false);
            sessionReInit();
            recordInitialRequest(initialRequest);
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

        // 双腿支持precondition才可以进行放音
        // if (statusCode == Response.RINGING && callRecorder.isLeftIsSuppotedPrecondition() && callRecorder.isRightIsRequirePrecondition() && preciseCallState.canPlayearly()) {
        //     if (!isSendPlayEarlyCommand) {
        //         // if (StringUtils.isNotBlank(callParameter.getCallerPlayUrl())) {
        //         //     // 亲测还要加上supported，当180带supported时也是没有媒体声音
        //         //     boolean isForce = (leg.pearlyMedia.isEmpty() || "inactive".equalsIgnoreCase(leg.pearlyMedia)) || "supported".equalsIgnoreCase(leg.pearlyMedia);
        //         //     // 左腿是ios且inactive的话不放音
        //         //     if (!callRecorder.isLeftIsIOS() || !isForce) {
        //         //         MediaService.dccPlayMedia(callParameter.getCallerPlayUrl(),"DuDu",
        //         //                 "", "", MediaConst.PLAY_PERIOD.EARLY, MediaConst.PRIORITY.LOCAL, MediaConst.PLAY_DIRECTION.CALLER,
        //         //                 callParameter.getRecordMode(), null, mediaSession, businessEvent, false, billNew.getCompanyId(),true);
        //         //     } else {
        //         //         logger.info("======bill ID:{}===the case of that ios didn't play early media", sessionId);
        //         //     }
        //         //     isSendPlayEarlyCommand = true;
        //         // }
        //     }
        // }
        callSession.consumeResponse(leg, responseEvent);
    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {

        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        Leg oppositeLeg = getOppositeLeg(leg);

        boolean isReject = preciseCallState.extractRequestMsgPropertiesJudgeReject(leg.isLeft(), request);
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
            billNew.setCallState(ConstantConf.CALL_STATE_ACK);
        }
        // forwardRequestUponState(leg, requestEvent, callParameter.isCanPassVideo());
        oppositeLeg.forwardRequest(request, mediaSession, e -> {
            if (callParameter.isCanPassVideo()) {
                if (SipHeaderHelper.ifSupported(request)) {
                    e.removeHeader(ContactHeader.NAME);
                    e.setHeader(this.engine.contactHeaderWithVideo);
                }
            }
        }, new VideoBillHandler(this), false);
    }


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
        billNew.setMsId(0);
        // billNew.setIsNeedAsr(isNeedAsr);
        billNew.setStoreId(0);
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
