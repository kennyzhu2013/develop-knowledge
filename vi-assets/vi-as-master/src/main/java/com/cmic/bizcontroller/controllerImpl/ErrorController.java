package com.cmic.bizcontroller.controllerImpl;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.ConstantConf;
import com.cmic.constant.MediaConst;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.service.*;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.state.FinishType;
import com.cmic.state.PreciseCallState;
import com.cmic.state.SipInfoCollector;
import com.cmic.util.*;
import com.discovery.ServiceNode;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import static com.cmic.service.PlayCodeService.NO_PLAY;

public class ErrorController extends NewTwoLegController {
    private Logger logger = LoggerFactory.getLogger(getClass());
    boolean leftIsSuppotedPrecondition;
    byte[] sdp183;

    public ErrorController(BillNew billNew, BizNumAuthDo bizNumAuthDo, BusinessEvent businessEvent, CallParameter callParameter, SipEngine engine) {
        super(billNew.getId(), callParameter, engine);
        isNeedCacheSingallingLog = false;
        this.billNew = billNew;
        preciseCallState = new PreciseCallState(this.billNew);
        infoCollector = new SipInfoCollector(this.billNew);
        this.businessEvent = businessEvent;
        this.bizNumAuthDo = bizNumAuthDo;
        // this.callParameter = callParameter;
        isNeedCacheSingallingLog = false;
    }

    @Override
    public void start(SIPRequest sipRequest) throws Exception {
        /** 初始化左腿 **/
        this.initialInviteRequest = sipRequest;
        leftIsSuppotedPrecondition = SipUtil.isSupportedPrecondition(sipRequest);
        LeftLeg leftLeg = this.engine.createLeftLeg(sipRequest);
        bindLeftLeg(leftLeg);
        XmodePromExporterService.getInstance().incLeftLegNum();
        if (NO_PLAY.equals(callParameter.getCallerPlayUrl())) {
            SIPResponse response180 = leftLeg.createResponse(180, sipRequest);
            leftLeg.sendResponse(sipRequest, response180);
            ScheduleService.getInstance().schedule(() -> {
                try {
                    canTerminate = true;
                    this.engine.cleanControllerWithSpecificCode(this, 603, "currency call overflow!");
                } catch (Exception e) {
                    logger.error("[{}], releaseCallAfterOneMinute error", sessionId, e);
                }
            }, 200, TimeUnit.MILLISECONDS);
            return;
        }
        // int userId = prodNum.getUserId();
        String callerNum = SipUtil.getCallerNum(sipRequest);
        boolean isNeedPrecondition = leftIsSuppotedPrecondition && SipUtil.hasQos(sipRequest);

        int recordMode = ConstantConf.MEDIA_LOGIC_FLAG_COMMON;
        if (callParameter.isNeedEarly()) {
            // bill.setNeedEarly(true);
            recordMode = ConstantConf.MEDIA_LOGIC_FLAG_NEED_EARLY;
        }
        ServiceNode recordHost = MyRegistryService.getInstance().getRecordHost();
        DtmfLogger.info(sessionId, "recordMode {}", recordMode);
        //ObjectNode recordParam = (ObjectNode)MediaManager.objectMapper.readTree(callParameter.getRecordParams().toString());
        mediaSession = MediaManager.createMediaSession(String.valueOf(sessionId), "http://"+recordHost.getAddress(),
                Integer.parseInt(recordHost.getId()), billNew.getCompanyId() + "", billNew.getCallerNumber(),
                PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), null, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
        if (!mediaSession.isActive()) {
            DtmfLogger.info(sessionId, "mediaSession is not active, try to create mediaSession again");
            mediaSession = MediaManager.createMediaSession(String.valueOf(sessionId), "http://"+recordHost.getAddress(),
                    Integer.parseInt(recordHost.getId()), billNew.getCompanyId() + "", billNew.getCallerNumber(),
                    PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), null, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
            if (!mediaSession.isActive()) {
                DtmfLogger.info(sessionId, "mediaSession is still not active, try to create mediaSession again");
                try {
                    canTerminate = true;
                    // 返回500对主叫侧有大网放音提醒，603则无。若后续因影响大网指标等被投诉，再整改
                    this.engine.cleanControllerWithSpecificCode(this, 500, "media is not active!");
                } catch (Exception ee) {
                    logger.error("[{}], release call when media is not active failed!", sessionId, ee);
                }
                return;
            }
        }
        mediaSession.setPreferMediaCodec(DevHelp.forceCodec);

        mediaSession.processLeftSDP(sipRequest.getRawContent());
        sdp183 = mediaSession.processPlayRightSDP(sipRequest.getRawContent(), isNeedPrecondition);
        mediaSession.commit();
        SIPResponse relResponse;
        if (SipUtil.isSupported100rel(sipRequest)) {
            relResponse = leftLeg.createProvisionalResponse(183, true);
        } else {
            relResponse = leftLeg.createProvisionalResponse(183, false);
        }
        if (isNeedPrecondition) {
            relResponse.setHeader(engine.headerFactory.createRequireHeader("precondition"));
        }
        relResponse.setHeader(this.engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
        relResponse.setContent(sdp183, this.engine.contentTypeHeader);
        leftLeg.sendReliableProvisionalResponse(relResponse);
        if (!isNeedPrecondition) {
            leftLeg.sendResponse180(initialInviteRequest, response -> {
                ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("cmic");
                response.addHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
            });
            MediaService.dccPlayMedia(callParameter.getCallerPlayUrl(),"",
                    "", "", MediaConst.PLAY_PERIOD.EARLY, MediaConst.PRIORITY.LOCAL, MediaConst.PLAY_DIRECTION.CALLER,
                    callParameter.getRecordMode(), (mediaEvent) -> {
                        logger.info("bill id  {} playearly mediaEvent {}", sessionId, mediaEvent);
                        leftLeg.sendByeOr487();
                    }, mediaSession, businessEvent, true, billNew.getCompanyId(),true);


        }
    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        preciseCallState.extractRequestMsgPropertiesJudgeReject(leg.isLeft(), request);
        if (!callParameter.isCanForward()) {
            if (method.equals(Request.CANCEL)) {
                leg.sendResponse200(request);
                leg.sendResponse487();
            } else if (method.equals(Request.PRACK)) {
                leg.sendResponse200(request);
            } else if (method.equals(Request.UPDATE)) {
                PreconditionParam uacPreconditionParam = new PreconditionParam(request.getRawContent());
                PreconditionParam uasPreconditionParam = uacPreconditionParam.oppositeClone();
                byte[] sdpPrecondition = SdpUtil.processPrecondition(sdp183, uasPreconditionParam);
                leg.sendResponse200(request, message -> {
                    message.setContent(sdpPrecondition, leg.engine.contentTypeHeader);
                });
                leg.sendResponse180(initialInviteRequest, response -> {
                    ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("cmic");
                    response.addHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
                });
                MediaService.dccPlayMedia(callParameter.getCallerPlayUrl(),"",
                        "", "", MediaConst.PLAY_PERIOD.EARLY, MediaConst.PRIORITY.LOCAL, MediaConst.PLAY_DIRECTION.CALLER,
                        callParameter.getRecordMode(), (mediaEvent) -> {
                            logger.info("bill id  {} playearly mediaEvent {}", sessionId, mediaEvent);
                            leftLeg.sendByeOr487();
                        }, mediaSession, businessEvent, true, billNew.getCompanyId(),true);

            }
        }
    }

    @Override
    public MediaSession getMediaSession() {
        return mediaSession;
    }

    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        SIPResponse response = responseEvent.getResponse();

        preciseCallState.extractResponseMsgPropertiesJudgeReject(response);
    }

    @Override
    public void terminated() throws Exception {
        billNew.setHandleEndTime(new Timestamp(System.currentTimeMillis()));
        // 判断是否存在通话，存在则填充通话结束时间
        if (billNew.getCallStartTime() != null && billNew.getCallEndTime() == null) {
            billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
        }
        if (mediaSession != null) {
            // bill.setNegotiateTimes(mediaSession.getCommitTimes());
            mediaSession.stop(billNew.getHandleEndTime().getTime());
        }
        handleEnd();
    }

    private void handleEnd() {
        int billState[] = preciseCallState.getFinalState();
        //x模式中优先从控制接口返回的参数或者结果设置精准话单状态
        if (callParameter.getDoParameterFinishState() > 0 && !preciseCallState.isSystemExit()) {
            if (callParameter.getDoParameterFinishType() != null) {
                billState[0] = callParameter.getDoParameterFinishType();
            } else {
                billState[0] = FinishType.LEFT_LEG_STOP;
            }
            billState[1] = callParameter.getDoParameterFinishState();
            DtmfLogger.info(sessionId + "", "Disconnect error msg {},finishType:{}, finishState:{}",
                    callParameter.getDoParameterFinishStateErrorMsg(), billState[0], billState[1]);
        }

        billNew.setFinishType(billState[0]);
        billNew.setFinishState(billState[1]);
        int mediaServerId = mediaSession == null ? 0 : mediaSession.getServerId();
        int isNeedAsr = billNew.getFinishState() == 0 ? 1 : 0;
        int storeId = mediaSession == null ? 0 : mediaSession.getStoreId();
        billNew.setMsId(mediaServerId);
        // billNew.setIsNeedAsr(isNeedAsr);
        billNew.setStoreId(storeId);
        DbAsyncService.getInstance().submitDbUpdate(this);
    }


    @Override
    public void doAfterTerminate() {
        // do nothing
    }
}
