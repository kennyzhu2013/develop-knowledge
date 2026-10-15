package com.cmic.bizcontroller.controllerImpl;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.ConstantConf;
import com.cmic.bizcontroller.VideoBillHandler;
import com.cmic.bizcontroller.callInfo.CallRecorder;
import com.cmic.bizcontroller.callInfo.CallSession;
import com.cmic.bizcontroller.controllerImpl.vod.ControllerState;
import com.cmic.bizcontroller.controllerImpl.vod.plugin.PluginManager;
import com.cmic.bizcontroller.controllerImpl.vod.state.AbstractCallState;
import com.cmic.bizcontroller.controllerImpl.vod.state.PickUpState;
import com.cmic.bizcontroller.feature.CallStateManager;
import com.cmic.bizcontroller.feature.StreamManager;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.pojo.BillAppendReasonEnum;
import com.cmic.pojo.SequenceParamsBill;
import com.cmic.pojo.VideoParam;
import com.cmic.recording.MediaManager;
import com.cmic.recording.MediaSession;
import com.cmic.recording.mediaevent.EnhanceDoneEvent;
import com.cmic.service.*;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.state.FinishState;
import com.cmic.state.FinishType;
import com.cmic.state.PreciseCallState;
import com.cmic.util.*;
import com.discovery.ServiceNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nist.javax.sip.header.SIPHeader;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.params.SetParams;

import javax.sip.DialogState;
import javax.sip.header.ContactHeader;
import javax.sip.message.Request;
import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.cmic.util.PhoneNumUtil.getPure11NumberWithout0or86;


public class NewTwoLegController extends TwoLegController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Setter
    @Getter
    public PreciseCallState preciseCallState;
    @Getter
    @Setter
    public BillNew billNew;
    @Setter
    @Getter
    public BusinessEvent businessEvent;
    @Getter
    public CallParameter callParameter;
    @Setter
    public MediaSession mediaSession;
    // 初始seq默认为1
    public long initialSeq = 1;
    public byte[] sdp183;

    @Setter
    @Getter
    public BizNumAuthDo bizNumAuthDo;
    @Setter
    @Getter
    public SIPRequest initialInviteRequest;
    boolean ifSupportedVideoPlay;
    @Setter
    public volatile int videoState;
    public SequenceParamsBill sequenceParamsBill;
    @Getter
    public PluginManager pluginManager;

    @Setter
    @Getter
    public ControllerState controllerState = new ControllerState();

    public CallStateManager callStateManager;

    public StreamManager streamManager;

    @Setter
    @Getter
    protected String vodAudioCode = "amr-wb";

    @Getter
    @Setter
    public CallRecorder callRecorder = new CallRecorder();

    public CallSession callSession = new CallSession();

    @Getter
    protected long initialRightSeqNo = 0;


    public NewTwoLegController(String sessionId, CallParameter callParameter, SipEngine engine) {
        super(sessionId, engine);
        this.callParameter = callParameter;
    }

    public void recordInitialRequest(SIPRequest initialRequest) {
        VideoParam videoParam = null;

        try {
            String userAgent = SipUtil.getUserAgent(initialRequest);
            boolean leftIsIOS = (userAgent.contains("iOS") || userAgent.contains("iPhone")) && callParameter.isDownPlayVoice();
            callRecorder.setLeftIsIOS(leftIsIOS);
            callRecorder.setCallerSupportVideo(SipUtil.ifSupportVideo(initialRequest));
            callRecorder.setLeftIsSuppotedPrecondition(SipUtil.isSupportedPrecondition(initialRequest));
            videoParam = SdpHelper.getVideoParam(initialRequest);
            if (videoParam.getVideoPort() > 0) {
                callRecorder.setCallStartWithVideo(true);
            }
        } catch (Exception e) {
            logger.error("[{}] recordInitialRequest exception!", billNew.getId(), e);
        }

    }


    public void initPreciseCallState(String className, Class<?> parameterType, Object[] args) {
        logger.info("[{}] initPreciseCallState className:{}", billNew.getId(), className);
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor(parameterType);
            Object obj = constructor.newInstance(args);
            if (obj instanceof PreciseCallState) {
                preciseCallState = (PreciseCallState) obj;
            } else {
                logger.error("[{}] initPreciseCallState no matched class!", billNew.getId());
            }
            logger.info("[{}] initPreciseCallState preciseCallState:{}", billNew.getId(), preciseCallState);
        } catch (Exception e) {
            logger.error("[{}] initPreciseCallState exception!", billNew.getId(), e);
        }
    }


    public void sessionReInit() {
        //callRecorder = new CallRecorder();
        callRecorder.setBillNew(billNew);
        callRecorder.setBusinessEvent(businessEvent);
        callRecorder.setLeftLeg(leftLeg);
        callRecorder.setMediaSession(mediaSession);
        callRecorder.setCallParameter(callParameter);
        callRecorder.setPluginManager(pluginManager);
        callRecorder.setControllerState(controllerState);
        callRecorder.setLastLeftRequest(initialInviteRequest);
        streamManager = new StreamManager(callRecorder);
        callStateManager = new CallStateManager(callRecorder);
        //callSession = new CallSession();
        callSession.setCallRecorder(callRecorder);
        callSession.setStreamManager(streamManager);
    }

    public void initMediaSession() throws JsonProcessingException {
        int recordMode = ConstantConf.MEDIA_LOGIC_FLAG_COMMON;
        if (callParameter.isNeedEarly()) {
            // billNew.setNeedEarly(true);
            recordMode = ConstantConf.MEDIA_LOGIC_FLAG_NEED_EARLY;
        }
        ServiceNode recordHost = MyRegistryService.getInstance().getRecordHost();
        ObjectNode recordParam = (ObjectNode) MediaManager.objectMapper.readTree(callParameter.getRecordParams().toString());
        MediaSession mediaSession = MediaManager.createMediaSession(String.valueOf(billNew.getId()), recordHost.getHost(),
                Integer.parseInt(recordHost.getId()), billNew.getCompanyId() + "", billNew.getCallerNumber(),
                PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recordParam, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
        if (!mediaSession.isActive()) {
            mediaSession = MediaManager.createMediaSession(String.valueOf(billNew.getId()), recordHost.getHost(),
                    Integer.parseInt(recordHost.getId()), billNew.getCompanyId() + "", billNew.getCallerNumber(),
                    PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recordParam, billNew.getHandleStartTime().getTime(), recordMode, callParameter.getSupportedCodec());
        }
        mediaSession.setPreferMediaCodec(DevHelp.forceCodec);
    }

    public void handleStreamBUpdate() {
        int recMode = callParameter.isNeedEarly() ? ConstantConf.MEDIA_LOGIC_FLAG_NEED_EARLY : ConstantConf.MEDIA_LOGIC_FLAG_COMMON;
        if (callParameter.isRealTimeStream()) {
            // 初始化计费时长控制字段
            callParameter.setStreamDurationWritten(false);
            // 通知media准需要实时媒体流传输
            proceedBUpdate(recMode);
        }
    }

    public void proceedBUpdate(int recMode) {
        mediaSession.businessUpdate(PhoneNumUtil.getPureNumer(billNew.getCalleeNumber()), recMode, null, callParameter.getStreamBusinessType(), callParameter.getStreamType(),
                callParameter.getStreamSource(), callParameter.getStreamWebSocketURL(), callParameter.getStreamExtParam(),
                mediaEvent -> {
                    EnhanceDoneEvent enhanceDoneEvent = (EnhanceDoneEvent) mediaEvent;
//                    todo streamEvent.getDuration()
                    logger.info("[{}] stream error code:{} message:{} duration:{}", billNew.getId(), enhanceDoneEvent.getCode(), enhanceDoneEvent.getMessage(), 0);
                    // 如果媒体回调中code取值不为"0"时为媒体侧异常回调，duration以回调返回值为准
                    if (!"0".equalsIgnoreCase(enhanceDoneEvent.getCode())) {
                        callParameter.setStreamDurationWritten(true);
                        // todo streamEvent.getDuration()
                        // billNew.setStreamDurationS(0);
                    } else if (!callParameter.isStreamDurationWritten() && callParameter.getStreamStartTime() != null) {
                        callParameter.setStreamDurationWritten(true);
                        // billNew.setStreamDurationS(System.currentTimeMillis() - callParameter.getStreamStartTime().getTime());
                    }
                });
    }

    /*public void handleStreamStartTime(Leg leg, int statusCode, String method, boolean hasSend180Event) {
        if (statusCode == Response.OK && method.equalsIgnoreCase(SIPRequest.INVITE)) {
            boolean ifStartCallStream = callParameter.getStreamSource() != 0 &&
                    (callParameter.getStreamType() == 2 || callParameter.getStreamType() == 3);
            if (ifStartCallStream) {
                if (callParameter.getStreamStartTime() == null || (hasSend180Event && !last180ResponseHasEarly)) {
                    if (billNew.getCallStartTime() != null) {
                        // 这里存在重复进入，但是每次都是通话开始时间
                        callParameter.setStreamStartTime(billNew.getCallStartTime().getTime());
                    }
                }
            } else if (callParameter.getStreamSource() != 0 && callParameter.getStreamType() == 1) {
                // 如果只需要早期，则要在收到200-INVITE的时候结束早期计费
                if (!callParameter.isStreamDurationWritten() && callParameter.getStreamStartTime() != null) {
                    callParameter.setStreamDurationWritten(true);
                    billNew.setStreamDurationS(System.currentTimeMillis() - callParameter.getStreamStartTime().getTime());
                    DtmfLogger.info(sessionid, "call need early real-time stream only,stop for receiving 200-INVITE streamDuration: {} s", billNew.getStreamDurationS());
                }
            }
        } else {
            boolean ifStartEarlyStream = callParameter.getStreamSource() != 0 &&
                    (callParameter.getStreamType() == 1 || callParameter.getStreamType() == 3) && mediaSession.getFirstCommitTime() != null;
            if (ifStartEarlyStream) {
                // 如果启动时间还未初始化并且还未收到180-Ringing，启动时间等于首次UPDATE时间
                if (callParameter.getStreamStartTime() == null && !hasSend180Event) {
                    callParameter.setStreamStartTime(mediaSession.getFirstCommitTime().getTime());
                }
                if (statusCode == Response.RINGING) {
                    boolean inactive = (leg.pearlyMedia.isEmpty() || "inactive".equalsIgnoreCase(leg.pearlyMedia)) || "supported".equalsIgnoreCase(leg.pearlyMedia);
                    // 如果未收到指示有早期媒体的180-Ringing
                    if (!last180ResponseHasEarly) {
                        // 如果180-Ringing指示有被叫早期媒体
                        if (!inactive) {
                            last180ResponseHasEarly = true;
                            callParameter.setStreamStartTime(System.currentTimeMillis());
                        } else {
                            // 180-Ringing指示无被叫侧早期媒体，则实时媒体流启动时间还原为未初始化状态
                            callParameter.setStreamStartTime(null);
                        }
                    }
                }
            }
        }
    }*/

    public void registerPlugins() {

    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {

    }


    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
    }


    @Override
    public void start(SIPRequest initialRequest) throws Exception {
        try {
            this.initialInviteRequest = initialRequest;
            /** 初始化左腿 **/
            LeftLeg leftLeg = this.engine.createLeftLeg(initialRequest);
            bindLeftLeg(leftLeg);
            this.initialSeq = initialRequest.getCSeq().getSeqNumber();
            preciseCallState.setInitialSeq(this.initialSeq);
            XmodePromExporterService.getInstance().incLeftLegNum();
            businessEvent.doRecvInvite();
            // AFEventService.getInstance().doCallOutEvent(billNew);
            mediaSession = MediaService.createMediaSession(callParameter, billNew, initialRequest);
            ifSupportedVideoPlay = SipUtil.ifSupportVideoPlay(initialRequest);
            handleFirstInvite(initialRequest);
        } catch (Exception e) {
            DtmfLogger.error(billNew.getId() + "", "something error happen when start ", e);
        }

    }


    public void handleFirstInvite(SIPRequest sipRequest) throws Exception {
    }

    @Override
    public MediaSession getMediaSession() {
        return mediaSession;
    }

    @Override
    public void terminated() throws Exception {
        if (canTerminate && !callParameter.getHasStartTerminated().getAndSet(true)) {
            DtmfLogger.info(billNew.getId(), "NewTwoLegController terminated start");
            billNew.setHandleEndTime(new Timestamp(System.currentTimeMillis()));
            // if (billNew.getVideoRingStartTime() != null && billNew.getVideoRingEndTime() == null) {
            //     billNew.setVideoRingEndTime(System.currentTimeMillis());
            // }
            if (mediaSession != null) {
                // billNew.setNegotiateTimes(mediaSession.getCommitTimes());
                mediaSession.stop(billNew.getHandleEndTime().getTime());
            }
            if (billNew.getCallStartTime() != null && billNew.getCallEndTime() == null) {
                billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
            }
            // 实时媒体流计费,入库流传输时长
            if (!callParameter.isStreamDurationWritten() && callParameter.getStreamStartTime() != null) {
                callParameter.setStreamDurationWritten(true);
                // billNew.setStreamDurationS(System.currentTimeMillis() - callParameter.getStreamStartTime().getTime());
            }
            handleEnd();
        } else {
            hasSendDialogTemrinate.getAndSet(false);
        }
    }

    private void handleEnd() throws Exception {
        int billNewState[] = preciseCallState.getFinalState();
        // x模式中优先从控制接口返回的参数或者结果设置精准话单状态
        if (callParameter.getDoParameterFinishState() > 0 && !preciseCallState.isSystemExit()) {
            billNewState[0] = FinishType.LEFT_LEG_STOP;
            billNewState[1] = callParameter.getDoParameterFinishState();
        }
        // 强制ASR
        if (DevHelp.forceAsr.equalsIgnoreCase("1")) {
            DtmfLogger.info(billNew.getId(), "open forceAsr");
            billNewState[1] = FinishState.NEED_ASR;
        }

        if ((billNewState[1] == FinishState.UNRECOGNIZED_STATE || billNewState[1] == FinishState.NEED_ASR) && !DevHelp.forceAsr.equalsIgnoreCase("1")) {
            ScheduleService.getInstance().schedule(() -> {
                DtmfLogger.info(billNew.getId(), "delay juge finishState, add a task to ScheduleService ");
                try {
                    handleTerminate();
                } catch (Exception e) {
                    DtmfLogger.info(billNew.getId(), "handleTerminate failed", e);
                }
                fillBill(preciseCallState.getFinalState());
            }, 300, TimeUnit.MILLISECONDS);
        } else {
            handleTerminate();
            fillBill(billNewState);
        }
    }

    private void fillBill(int[] newBillState) {
        // 原始话单的话单状态与顺振最后一个话单状态保持一致
        if (sequenceParamsBill != null && !sequenceParamsBill.getSequenceParamsList().isEmpty()) {
            newBillState[0] = sequenceParamsBill.getSequenceParamsList().get(sequenceParamsBill.getSequenceParamsList().size() - 1).getFinishtype();
            newBillState[1] = sequenceParamsBill.getSequenceParamsList().get(sequenceParamsBill.getSequenceParamsList().size() - 1).getFinishstate();
        }
        billNew.setFinishType(newBillState[0]);
        billNew.setFinishState(newBillState[1]);
        int mediaServerId = mediaSession == null ? 0 : mediaSession.getServerId();
        int storeId = mediaSession == null ? 0 : mediaSession.getStoreId();
        int isNeedAsr = billNew.getFinishState() == 0 ? 1 : 0;
        billNew.setMsId(mediaServerId);
        // billNew.setIsNeedAsr(isNeedAsr);
        billNew.setStoreId(storeId);
        //BillService.updateBill(billNew, callParameter);
        // AFEventService.getInstance().doFinishEvent(billNew);
        DbAsyncService.getInstance().submitDbUpdate(this);
        //XmodePromExporterService.getInstance().incBillFinishState(billNew.getFinishState());
        //XmodePromExporterService.getInstance().incBillFinishState(billNew.getFinishState(), billNew.getCompanyId());
        //BillCenterService.getInstance().sendMsgToBillCenter(billNew.getId(), billNew.getCompanyId(), callParameter.getAsrlevel());
    }

    public void handleTerminate() throws Exception {

    }

    /*public boolean isHasRecv200ok() {
        return hasRecv200ok;
    }*/


    private boolean isMatchTransferList(List<TransferLimitDto> list, String transferCallee, String name) {
        if (list == null) {
            logger.warn("list of TransferLimitDto is null");
            return false;
        }
        for (TransferLimitDto transferLimitDto : list) {
            if (transferLimitDto.isMatch.apply(transferCallee)) {
                logger.info("[{}] transfer call is matched by {}: {}", sessionId, name, transferLimitDto);
                return true;
            }
        }
        return false;
    }

    // /**
    //  * 根据response判断是否需要呼转拦截
    //  *
    //  * @param response
    //  * @return
    //  */
    // @Override
    // public boolean isCallForwardAllow(SIPResponse response) {
    //     try {
    //         int statusCode = response.getStatusCode();
    //         if (statusCode != 181) {
    //             // 非181不拦截 会有182的情况
    //             return true;
    //         }
    //         // 获得呼转transfer_call的号码
    //         String transferCallee = getPure11NumberWithout0or86(billNew.getTransferCallee());
    //         // 黑白名单检查
    //         if (StringUtils.isNotBlank(transferCallee)) {
    //             List<TransferLimitDto> transferBlacklistPrefixList = DevHelp.getTransferBlacklistPrefixList();
    //             if (isMatchTransferList(transferBlacklistPrefixList, transferCallee, "black list")) {
    //                 return false;
    //             }
    //
    //             List<TransferLimitDto> transferWhitelistPrefixList = DevHelp.getTransferWhitelistPrefixList();
    //             if (isMatchTransferList(transferWhitelistPrefixList, transferCallee, "white list")) {
    //                 return true;
    //             }
    //         }
    //         int callForwardMode = businessEvent.getDccAuthConf().getIs181Allow();
    //
    //         // 根据检测的结果来判断是否需要修正181拦截的问题
    //         AuditResponseEntity auditResponseEntity = callParameter.getAuditResponseEntity();
    //         if (auditResponseEntity != null && StringUtils.isNotBlank(auditResponseEntity.getAllowCalleeForwarding())) {
    //             callForwardMode = Integer.parseInt(auditResponseEntity.getAllowCalleeForwarding());
    //             logger.info("[{}], [isCallForwardAllow] callForwardMode set to:{} by auditStrategy", sessionId, callForwardMode);
    //         }
    //
    //         switch (callForwardMode) {
    //             case 0: // 全部不允许
    //                 // 如果发生呼转就拦截
    //                 logger.info("[{}], [isCallForwardAllow] db not allow call forward, transferCallee:{} , " +
    //                         "userId:{}, appId:{}", sessionId, transferCallee, billNew.getUserId(), billNew.getAppId());
    //                 return false;
    //             case 1: // 部分允许，根据呼转不同号码次数控制
    //                 if (DevHelp.isCallForwardTimesAllow()) { // 若不拦截，则全放通
    //                     logger.info("[{}], [isCallForwardAllow], etcd callForward allow", sessionId);
    //                     return true;
    //                 }
    //                 if (StringUtils.isBlank(transferCallee)) {
    //                     // 如果有181但是没有history-info就默认干掉
    //                     logger.info("[{}], [isCallForwardAllow], 181 but no history-info, userId:{}, appId:{}",
    //                             sessionId, billNew.getUserId(), billNew.getAppId());
    //                     return false;
    //                 }
    //                 // 允许呼转到非11位手机号或者特服号码（非1开头的）
    //                 if (transferCallee.length() != 11 || !transferCallee.startsWith("1")) {
    //                     logger.info("[{}], [isCallForwardAllow], callForward non-11-length phone and special phone", sessionId);
    //                     return true;
    //                 }
    //                 // 判断是否在质检返回的允许列表中
    //                 if (auditResponseEntity != null) {
    //                     if ("1".equals(auditResponseEntity.getAllowCalleeForwarding())) {
    //                         if (auditResponseEntity.getAllowForwardedNoList() != null) {
    //                             JSONArray allowForwardedNoList = auditResponseEntity.getAllowForwardedNoList();
    //                             boolean allow = allowForwardedNoList.stream().anyMatch(item -> ((String) item).contains(transferCallee));
    //                             logger.info("[{}], [isCallForwardAllow], audit query result : {}", sessionId, allow);
    //                             return allow;
    //                         } else {
    //                             // 和涛哥确认了，如果返回的列表为空，则全部放开
    //                             logger.info("[{}], [isCallForwardAllow], allow because audit query return blank", sessionId);
    //                             return true;
    //                         }
    //                     }
    //                 }
    //                 // 获取原被叫号码
    //                 ToHeader toHeader = response.getToHeader();
    //                 String toPhone = getPure11NumberWithout0or86(toHeader.getAddress().getURI());
    //                 String key = "cf-" + toPhone;
    //                 int is181AllowTTL = businessEvent.getDccAuthConf().getIs181AllowTTL();
    //                 // 判断是否存在此键
    //                 if (setToRedisNX(key, transferCallee, is181AllowTTL)) {
    //                     logger.info("[{}], [isCallForwardAllow], toPhone-callee:{} not exists in redis", sessionId, key + "-" + transferCallee);
    //                     // 设置成功则此键不存在，允许通话
    //                     return true;
    //                 }
    //                 // 如果键值已存在，则判断是否同一个号码
    //                 String redisCallee = getFromRedis(key);
    //                 // 极端情况下，如果前面setnx的时候已经有了，但是这里get的时候又过期了，那么放过这个幸运儿
    //                 if (StringUtils.isBlank(redisCallee)) {
    //                     logger.info("[{}], [isCallForwardAllow], callForward(in {}): catch when key ttl reach or redis error", statusCode, sessionId);
    //                     return true;
    //                 }
    //                 boolean phoneSame = transferCallee.equals(redisCallee);
    //                 if (!phoneSame) {
    //                     logger.info("[{}], [isCallForwardAllow],userId:{}, appId:{},  reject for transfer callee" +
    //                                     " change, redis:{}, now:{}", sessionId, billNew.getUserId(), billNew.getAppId(),
    //                             redisCallee, transferCallee);
    //                 }
    //                 return phoneSame;
    //             case 2: // 所有都允许
    //                 return true;
    //             case 3: // 拦截所有手机号呼转
    //                 // 允许呼转到非11位手机号或者特服号码（非1开头的）
    //                 if (transferCallee.length() != 11 || !transferCallee.startsWith("1")) {
    //                     logger.info("[{}], [isCallForwardAllow], 3 callForward non-11-length phone and special phone", sessionId);
    //                     return true;
    //                 }
    //                 return false;
    //             default:
    //                 return true;
    //         }
    //     } catch (Exception e) {
    //         logger.error("[{}], [isCallForwardAllow] error happened!", sessionId, e);
    //         return true;
    //     }
    // }

    // private String getFromRedis(String key) {
    //     try {
    //         return JedisClientPool.execute(jedis -> jedis.get(key));
    //     } catch (Exception e) {
    //         logger.error("[{}], getFromRedis error", sessionId, e);
    //         return "";
    //     }
    // }

    //新规整逻辑
    // public String formatCallee(String calleeNum, SIPRequest initialRequest) {
    //     String tempTel = calleeNum;
    //     logger.info("[{}] formateCallee tempTel:{}", billNew.getId(), tempTel);
    //     while (tempTel.startsWith("86")) {
    //         tempTel = tempTel.substring(2);
    //     }
    //     // HashMap<Integer, SpecialCallRegulationConfig> regulationConfigHashMap = DccConfigService.getInstance().getSpecialCallRegulationByNumber(tempTel);
    //     HashMap<Integer, SpecialCallRegulationConfig> regulationConfigHashMap = new HashMap<>();
    //     int provinceId = getProvinceId(initialRequest,tempTel);
    //     if (regulationConfigHashMap != null){
    //         SpecialCallRegulationConfig specialCallRegulationConfig = regulationConfigHashMap.get(provinceId);
    //         if (specialCallRegulationConfig != null){
    //             //按配置规则进行规则
    //             return formatByConfig(initialRequest, tempTel, specialCallRegulationConfig);
    //         }
    //         else {
    //             specialCallRegulationConfig = regulationConfigHashMap.get(999);
    //             if (specialCallRegulationConfig != null){
    //                 //按配置规则进行规则
    //                 return formatByConfig(initialRequest, tempTel, specialCallRegulationConfig);
    //             }
    //         }
    //     }
    //     logger.info("[{}] formate calleeNumber without using special_callee_regulation table:calleeNum={}", billNew.getId(), tempTel);
    //     boolean is95AreaCodeLandLine = (tempTel.startsWith("951") || tempTel.startsWith("952")
    //             || tempTel.startsWith("953") || tempTel.startsWith("954")
    //             || tempTel.startsWith("955")) && tempTel.length() >= 10;
    //     boolean is100SpecialCallee = tempTel.startsWith("1") && tempTel.length() < 6;
    //     if (is100SpecialCallee || (tempTel.startsWith("95") || tempTel.startsWith("96")) && !is95AreaCodeLandLine) {
    //         logger.info("[{}] calleeNumber is special service number", billNew.getId());
    //         return continueFormatSpecialCallee(initialRequest, tempTel);
    //     }
    //     boolean is400or800SpecialCallee = (tempTel.startsWith("400") || tempTel.startsWith("800"));
    //     if (is400or800SpecialCallee){
    //         return "tel:" + tempTel;
    //     }
    //     return "tel:+86" + tempTel;
    // }

    //按配置规则进行规整
    // public String formatByConfig(SIPRequest initialRequest, String calleeNum, SpecialCallRegulationConfig specialCallRegulationConfig){
    //     int type = specialCallRegulationConfig.getType();
    //     logger.info("[{}] formate calleeNumber based on special_callee_regulation table:calleeNum={},type={}, match_type={}", billNew.getId(), calleeNum, type, specialCallRegulationConfig.getMatchType());
    //     logger.info(specialCallRegulationConfig.toString());
    //     if (type == 1){
    //         int areaCode = specialCallRegulationConfig.getNumberAreaCode();//数据库为Null，这里返回0
    //         ConfigCity configCity = DccConfigService.getInstance().getConfigCityByAreaCode(areaCode);
    //         if (configCity != null){
    //             String regionCode = configCity.getRegionCode();
    //             if (!StringUtils.isBlank(regionCode)){
    //                 logger.info("[{}] regionCode exists in config_city table", billNew.getId());
    //                 return String.format("tel:+86%s12540%s%s",areaCode,regionCode,calleeNum);
    //             }
    //         }
    //         return continueFormatSpecialCallee(initialRequest, calleeNum);
    //     }
    //     else if (type == 2) {
    //         SIPHeader accessNetworkInfo = (SIPHeader) initialRequest.getHeader("P-Access-Network-Info");
    //         String areaCode = "";
    //         if (accessNetworkInfo != null){
    //             String[] codeAndAreaCode = getInfoFromSBCDomain(accessNetworkInfo);
    //             areaCode = codeAndAreaCode[0];
    //             if (areaCode.startsWith("0")){
    //                 areaCode = areaCode.substring(1);
    //             }
    //         }
    //         if (!areaCode.isEmpty()){
    //             logger.info("[{}] formate calleeNumber used sbc-domain info", billNew.getId());
    //             return String.format("tel:+86%s%s",areaCode,calleeNum);
    //         }
    //         String callerNum = billNew.getOriginalCallerNumber();
    //         callerNum = getPure11NumberWithout0or86(callerNum);
    //         PhoneSegmentCacheService.PhoneSegment segment = PhoneSegmentCacheService.getSegmentDtoFromPhone(callerNum);
    //         if (segment != null){
    //             areaCode = segment.getAreaCode();
    //             if (areaCode.startsWith("0")){
    //                 areaCode = areaCode.substring(1);
    //             }
    //             if (StringUtils.isNotBlank(areaCode)){
    //                 logger.info("[{}] formate calleeNumber used phone segment info", billNew.getId());
    //                 return String.format("tel:+86%s%s",areaCode,calleeNum);
    //             }
    //         }
    //
    //         SIPHeader viaInfo = (SIPHeader) initialRequest.getHeader("Via");
    //         if (viaInfo != null){
    //             String ip = "";
    //             Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    //             Matcher matcher = pattern.matcher(viaInfo.getHeaderValue());
    //             if (matcher.find()){
    //                 ip = matcher.group(1);
    //             }
    //             if (!ip.isEmpty()){
    //                 areaCode = ChinaMobileIcscfCacheService.getAreaCodeByICSCF(ip);
    //                 if (areaCode.startsWith("0")){
    //                     areaCode = areaCode.substring(1);
    //                 }
    //                 if (StringUtils.isNotBlank(areaCode)){
    //                     logger.info("[{}] formate calleeNumber used via info", billNew.getId());
    //                     return String.format("tel:+86%s%s", areaCode, calleeNum);
    //                 }
    //             }
    //         }
    //     }else if (type == 3){
    //         return "tel:+86" + calleeNum;
    //     }
    //     return "tel:" + calleeNum;
    // }

    //获取省份id
    // public int getProvinceId(SIPRequest initialRequest, String calleeNumber) {
    //     //1.优先从信令的SBC-domain中获得区号，再拿省份id
    //     String areaCode = "";//区号
    //     int provinceId = -1;
    //     SIPHeader accessNetworkInfo = (SIPHeader) initialRequest.getHeader("P-Access-Network-Info");
    //     if (accessNetworkInfo != null) {
    //         String[] codeAndAreaCode = getInfoFromSBCDomain(accessNetworkInfo);
    //         areaCode = codeAndAreaCode[0];
    //         if (areaCode.startsWith("0")){
    //             areaCode = areaCode.substring(1);
    //         }
    //     }
    //     if (!areaCode.isEmpty()){
    //         ConfigCity tmpConfigCity = DccConfigService.getInstance().getConfigCityByAreaCode(Integer.parseInt(areaCode));
    //         if (tmpConfigCity != null){
    //             return tmpConfigCity.getProvinceId();
    //         }
    //     }
    //     //2.从号段表中拿省份id
    //     PhoneSegmentCacheService.PhoneSegment segment = PhoneSegmentCacheService.getSegmentDtoFromPhone(calleeNumber);
    //     if (segment != null){
    //         return segment.getProvinceId();
    //     }
    //     //3.从chinamobile_icscf_ip中拿区号，再拿省份id
    //     SIPHeader viaInfo = (SIPHeader) initialRequest.getHeader("Via");
    //     if (viaInfo != null){
    //         String ip = null;
    //         Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    //         Matcher matcher = pattern.matcher(viaInfo.getHeaderValue());
    //         if (matcher.find()){
    //             ip = matcher.group(1);
    //         }
    //         if (ip != null) {
    //             String province = ChinaMobileIcscfCacheService.getProvinceByICSCF(ip); //icscf中没有省份id，只有省份名称，需要联查config_city表
    //             if (StringUtils.isNotBlank(province)){
    //                 HashMap<Integer, ConfigCity> configCityMap = DccConfigService.getInstance().getConfigCityMap();
    //                 for (ConfigCity configCity:configCityMap.values()){
    //                     if (province.equals(configCity.getName())){
    //                         return configCity.getProvinceId();
    //                     }
    //                 }
    //             }
    //         }
    //     }
    //     return provinceId;
    // }

    //判断主叫是否为固话或者特服，再接续处理
    // public String continueFormatSpecialCallee(SIPRequest initialRequest, String calleeNum){
    //     logger.info("[{}] continue format calleeNumber", billNew.getId());
    //     if (calleeNum.startsWith("86")){
    //         calleeNum = calleeNum.substring(2);
    //     }
    //     String callerNum = billNew.getOriginalCallerNumber();
    //     if (callerNum.startsWith("86")){
    //         callerNum = callerNum.substring(2);
    //     }
    //     //判断主叫是否为固话或者特服，则使用7001触发的icscf省份的区号和区域码
    //     boolean is95AreaCodeLandLine = (callerNum.startsWith("951") || callerNum.startsWith("952")
    //             || callerNum.startsWith("953") || callerNum.startsWith("954")
    //             || callerNum.startsWith("955")) && callerNum.length() >= 10;
    //     boolean is100SpecialCaller = callerNum.startsWith("1") && callerNum.length() < 6;
    //     if (PhoneUtil.isValidFixPhone(callerNum, "") || is100SpecialCaller || (callerNum.startsWith("95") || callerNum.startsWith("96")) && !is95AreaCodeLandLine){
    //         logger.info("[{}] callerNumber is fix number or special service number:{}", billNew.getId(), callerNum);
    //         SIPHeader viaInfo = (SIPHeader) initialRequest.getHeader("Via");
    //         if (viaInfo != null){
    //             String ip = "";
    //             Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    //             Matcher matcher = pattern.matcher(viaInfo.getHeaderValue());
    //             if (matcher.find()){
    //                 ip = matcher.group(1);
    //             }
    //             if (!ip.isEmpty()) {
    //                 String tmpAreaCode = ChinaMobileIcscfCacheService.getAreaCodeByICSCF(ip);
    //                 if (StringUtils.isNotBlank(tmpAreaCode)){
    //                     if (tmpAreaCode.startsWith("0")){
    //                         tmpAreaCode = tmpAreaCode.substring(1);
    //                     }
    //                     String tmpRegionCode = DccConfigService.getInstance().getConfigCityByAreaCode(Integer.parseInt(tmpAreaCode)).getRegionCode();
    //                     if (StringUtils.isNotBlank(tmpRegionCode)){
    //                         logger.info("[{}] get regionCode through ICSCF ip:regionCode={}", billNew.getId(), tmpRegionCode);
    //                         return String.format("tel:+86%s12540%s%s",tmpAreaCode,tmpRegionCode,calleeNum);
    //                     }
    //                 }
    //             }
    //         }
    //         return "tel:" + calleeNum;
    //     }
    //     SIPHeader accessNetworkInfo = (SIPHeader) initialRequest.getHeader("P-Access-Network-Info");
    //     String areaCode = "";
    //     String regionCode = "";
    //     if (accessNetworkInfo != null){
    //         String[] codeAndAreaCode = getInfoFromSBCDomain(accessNetworkInfo);
    //         areaCode = codeAndAreaCode[0];
    //         regionCode = codeAndAreaCode[1];
    //         if (areaCode.startsWith("0")){
    //             areaCode = areaCode.substring(1);
    //         }
    //     }
    //     if (StringUtils.isNotBlank(areaCode) && StringUtils.isNotBlank(regionCode)) {
    //         String operatorType = PhoneNumUtil.getPhoneTeleOperator(billNew.getOriginalCalleeNumber());
    //         if (operatorType.equals("CM")){//主叫用户为本网
    //             if (StringUtils.isNotBlank(areaCode) && StringUtils.isNotBlank(regionCode)){
    //                 logger.info("sbc-domain info exists and calleeNumber belongs to CM:{}",calleeNum);
    //                 return String.format("tel:+86%s12540%s%s",areaCode,regionCode,calleeNum);
    //             }
    //         }else {//主叫用户为异网
    //             ConfigCity tmpConfigCity = DccConfigService.getInstance().getConfigCityByAreaCode(Integer.parseInt(areaCode));
    //             if (tmpConfigCity != null){
    //                 logger.info("sbc-domain info exists but calleeNumber does not belong to CM:{}",calleeNum);
    //                 String tmpRegionCode = tmpConfigCity.getRegionCode();
    //                 if (StringUtils.isNotBlank(tmpRegionCode)){
    //                     return String.format("tel:+86%s12540%s%s",areaCode,tmpRegionCode,calleeNum);
    //                 }else {
    //                     return formatBySegment(initialRequest,calleeNum);
    //                 }
    //             }else {
    //                 DtmfLogger.info(billNew.getId(), "ConfigCity is null");
    //             }
    //         }
    //     }
    //     return formatBySegment(initialRequest, calleeNum);
    //
    // }

    // public String formatBySegment(SIPRequest initialRequest, String calleeNum){
    //     String callerNum = getPure11NumberWithout0or86(billNew.getOriginalCalleeNumber());
    //     logger.info("[{}] format calleeNumber based on phone segment table:calleeNumber={}, callerNumber={}",billNew.getId(), calleeNum, callerNum);
    //     PhoneSegmentCacheService.PhoneSegment segment = PhoneSegmentCacheService.getSegmentDtoFromPhone(callerNum);
    //     if (segment != null){
    //         String areaCode = segment.getAreaCode();
    //         if (areaCode.startsWith("0")){
    //             areaCode = areaCode.substring(1);
    //         }
    //         if (StringUtils.isNotBlank(areaCode)){
    //             String regionCode = DccConfigService.getInstance().getConfigCityByAreaCode(Integer.parseInt(areaCode)).getRegionCode();
    //             if (StringUtils.isNotBlank(regionCode)){
    //                 logger.info("[{}] phone segment exists in table",billNew.getId());
    //                 return String.format("tel:+86%s12540%s%s",areaCode,regionCode,calleeNum);
    //             }
    //         }
    //         return "tel:" + calleeNum;
    //     }else {
    //         SIPHeader viaInfo = (SIPHeader) initialRequest.getHeader("Via");
    //         if (viaInfo != null){
    //             String ip = "";
    //             Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    //             Matcher matcher = pattern.matcher(viaInfo.getHeaderValue());
    //             if (matcher.find()){
    //                 ip = matcher.group(1);
    //             }
    //             if (!ip.isEmpty()) {
    //                 String tmpAreaCode = ChinaMobileIcscfCacheService.getAreaCodeByICSCF(ip);
    //                 if (tmpAreaCode.startsWith("0")){
    //                     tmpAreaCode = tmpAreaCode.substring(1);
    //                 }
    //                 if (StringUtils.isNotBlank(tmpAreaCode)){
    //                     String tmpRegionCode = DccConfigService.getInstance().getConfigCityByAreaCode(Integer.parseInt(tmpAreaCode)).getRegionCode();
    //                     if (StringUtils.isNotBlank(tmpRegionCode)){
    //                         logger.info("[{}] format calleeNumber based on viaInfo",billNew.getId());
    //                         return String.format("tel:+86%s12540%s%s",tmpAreaCode,tmpRegionCode,calleeNum);
    //                     }
    //                 }
    //             }
    //         }
    //         return "tel:" + calleeNum;
    //     }
    // }

    public String[] getInfoFromSBCDomain(SIPHeader accessNetworkInfo){
        if (accessNetworkInfo != null) {
            String accessNetworkInfoValue = accessNetworkInfo.getHeaderValue();
            //DtmfLogger.info(billNew.getId(),"accessNetworkInfoValue: {}",accessNetworkInfoValue);
            String[] valueArr = accessNetworkInfoValue.split(";");
            String sbcDomain = "";
            String areaCode = "";//区号
            String regionCode = "";//区域码
            for (String value : valueArr) {
                if (value.startsWith("sbc-domain")) {
                    //DtmfLogger.info(billNew.getId(),"sbcDomain value:{}",value);
                    sbcDomain = value.split("=")[1];
                    //DtmfLogger.info(billNew.getId(),"sbcDomain:{}",sbcDomain);
                    String[] domainArr = sbcDomain.split("\\.");
                    areaCode = domainArr[1];
                    regionCode = domainArr[2];
                }
            }
            return new String[]{areaCode, regionCode};
        }
        return null;
    }

   /* public String formatCallee(String calleeNum, SIPRequest initialRequest) {
        String tempTel = calleeNum;
        logger.info("[{}] formateCallee tempTel:{}", billNew.getId(), tempTel);
        while (tempTel.startsWith("86")) {
            tempTel = tempTel.substring(2);
        }
        boolean is95AreaCodeLandLine = (tempTel.startsWith("951") || tempTel.startsWith("952")
                || tempTel.startsWith("953") || tempTel.startsWith("954")
                || tempTel.startsWith("955")) && tempTel.length() >= 10;
        boolean is100SpecialCallee = tempTel.startsWith("1") && tempTel.length() < 6;

        if (is100SpecialCallee || (tempTel.startsWith("95") || tempTel.startsWith("96")) && !is95AreaCodeLandLine) {
            String formattedCallee = formateSpecialCallee(tempTel, initialRequest);
            if (StringUtils.isNotBlank(formattedCallee)) {
                return formattedCallee;
            }
        }
        // 临时这么做，后续需要将带区号的95号码
        String phoneNumberWithoutAreaCode = removeAreaCode(tempTel);
        boolean is100SpecialCalleeNoAreaCode = phoneNumberWithoutAreaCode.startsWith("1") && phoneNumberWithoutAreaCode.length() < 6;
        if (is100SpecialCalleeNoAreaCode || (phoneNumberWithoutAreaCode.startsWith("95") || phoneNumberWithoutAreaCode.startsWith("96")) && StringUtils.length(phoneNumberWithoutAreaCode) < 10) {
            String formattedCallee = formateSpecialCallee(phoneNumberWithoutAreaCode, initialRequest);
            if (StringUtils.isNotBlank(formattedCallee)) {
                return formattedCallee;
            }
        }
        if (tempTel.startsWith("7000") || tempTel.startsWith("7002") || tempTel.startsWith("7003")) {
            String areaCodeFromFirstInvite = SipUtil.getAreaCodeFromFirstInvite(initialRequest);
            if (StringUtils.isNotBlank(areaCodeFromFirstInvite)) {
                return "tel:+86" + areaCodeFromFirstInvite + tempTel;
            }
        }
        return TelBRegulator.getInstance().regulate(calleeNum, initialRequest);

    }*/

    /**
     * 按配置规整特殊被叫号码格式
     *
     * @param calleeNumber
     * @param initialRequest
     * @return formatType=0: 则规整为 86+<区号>+<12540>+<区域码>+95号码
     * formatType=1: 则规整为 86+区号+号码出局；
     * formatType=2: 则规整为 裸号；
     * formatType=3: 则规整为 86+号码；
     */
    // private String formateSpecialCallee(String calleeNumber, SIPRequest initialRequest) {
    //     int formatType = 0;
    //     String cityCode = "";
    //     String areaCode = "";
    //     boolean isCalleeIncluded = false;
    //     try {
    //         for (SpecialCalleeFormatRule calleeFormatRule : TelBRegulator.getInstance().calleeFormatRules) {
    //             if (calleeFormatRule.getNumber().equals(calleeNumber)) {
    //                 formatType = calleeFormatRule.getFormatType();
    //                 cityCode = calleeFormatRule.getCityCode();
    //                 areaCode = calleeFormatRule.getAreaCode();
    //                 isCalleeIncluded = true;
    //                 break;
    //             }
    //         }
    //         if (!isCalleeIncluded) {
    //             return "";
    //         }
    //         String formattedCallee = "";
    //         switch (formatType) {
    //             case 0:
    //                 formattedCallee = formateTelBWithCityCodeAndAreaCode(calleeNumber, cityCode, areaCode, initialRequest);
    //                 break;
    //             case 1:
    //                 formattedCallee = String.format("tel:+86%s%s", getCityCode(initialRequest), calleeNumber);
    //                 break;
    //             case 2:
    //                 formattedCallee = String.format("tel:%s", calleeNumber);
    //                 break;
    //             case 3:
    //                 formattedCallee = String.format("tel:+86%s", calleeNumber);
    //                 break;
    //             default:
    //                 formattedCallee = calleeNumber;
    //                 break;
    //         }
    //         logger.info("[{}], [formateSpecialCallee] formattedCallee:{}", sessionId, formattedCallee);
    //         return formattedCallee;
    //     } catch (Exception e) {
    //         logger.error("[{}], [formateSpecialCallee] error happened!", sessionId, e);
    //         return "";
    //     }
    // }

    /**
     * 若 sbc-domain 有携带 citycode 和 areacode，则返回 “86+<区号>+<12540>+<区域码>+号码” ；
     * 若 sbc-domain 没有携带 citycode 和 areacode，则按配置的 areacode 和 citycode 规整成 86+<区号>+<12540>+<区域码>+号码” 返回；
     * 若 以上两种都没有，则返回裸号
     *
     * @param calleeNumber
     * @param defaultCityCode
     * @param defaultAreaCode
     * @param initialRequest
     * @return
     */
    private String formateTelBWithCityCodeAndAreaCode(String calleeNumber, String defaultCityCode, String defaultAreaCode, SIPRequest initialRequest) throws Exception {
        SIPHeader accessNetworkInfo = (SIPHeader) initialRequest.getHeader("P-Access-Network-Info");
        String formattedCallee = "";
        if (accessNetworkInfo != null) {
            String accessNetworkInfoValue = accessNetworkInfo.getHeaderValue();
            //DtmfLogger.info(billNew.getId(),"accessNetworkInfoValue: {}",accessNetworkInfoValue);
            String[] valueArr = accessNetworkInfoValue.split(";");
            String sbcDomain = "";
            String areaCode = "";
            String cityCode = "";
            for (String value : valueArr) {
                if (value.startsWith("sbc-domain")) {
                    //DtmfLogger.info(billNew.getId(),"sbcDomain value:{}",value);
                    sbcDomain = value.split("=")[1];
                    //DtmfLogger.info(billNew.getId(),"sbcDomain:{}",sbcDomain);
                    String[] domainArr = sbcDomain.split("\\.");
                    cityCode = domainArr[1];
                    areaCode = domainArr[2];
                }
            }
            boolean isAreaCodeValid = StringUtils.isNotBlank(areaCode) && CommonUtil.isNumericUsingRegex(areaCode);
            boolean isCityCodeValid = StringUtils.isNotBlank(cityCode) && CommonUtil.isNumericUsingRegex(cityCode);
            if (isAreaCodeValid && isCityCodeValid) {
                while (cityCode.startsWith("0")) {
                    cityCode = cityCode.substring(1);
                }
                //formattedCallee = "86"+cityCode+"12540"+areaCode+calleeNumber;
                //formattedCallee = String.format("tel:+%s", formattedCallee);

                formattedCallee = String.format("tel:+86%s12540%s%s", cityCode, areaCode, calleeNumber);

                return formattedCallee;
            }
        }
        if (StringUtils.isNotBlank(defaultCityCode) && StringUtils.isNotBlank(defaultAreaCode)) {
            String appendCityCode = defaultCityCode;
            while (appendCityCode.startsWith("0")) {
                appendCityCode = appendCityCode.substring(1);
            }
            //formattedCallee = "86"+appendCityCode+"12540"+defaultAreaCode+calleeNumber;
            //formattedCallee = String.format("tel:+%s", formattedCallee);

            formattedCallee = String.format("tel:+86%s12540%s%s", appendCityCode, defaultAreaCode, calleeNumber);
        }
        logger.info("[{}], [formateTelBWithCityCodeAndAreaCode] calleeNumber:{}", sessionId, formattedCallee);
        return formattedCallee;
    }

    private String getCityCode(SIPRequest initialRequest) {
        return SipUtil.getHomeAreaCodeFromFirstInvite(initialRequest);
    }

    /**
     * 设置呼转标志到redis
     *
     * @param key
     * @param value
     * @return 表示是否成功设置
     */
    // private boolean setToRedisNX(String key, String value, long ttl) {
    //     try {
    //         SetParams params = getJedisSetParams(true, ttl, true);
    //         return JedisClientPool.execute(jedis -> jedis.set(key, value, params) != null);
    //     } catch (Exception e) {
    //         logger.error("[{}], setToRedisNX error", sessionId, e);
    //         return true;
    //     }
    // }

    // @Override
    // public void doCallForwardBlock(SIPResponse response) throws Exception {
    //     callParameter.onDisconnect("callForward catch and block the session", "", FinishState.STRATEGY_HIJACKING,
    //             FinishType.SYSTEM_STOP);
    //     callParameter.setOptypeAppendReason(BillAppendReasonEnum.CALL_FORWARD_BLOCK_1.getCode());
    //     logger.info("[{}], [doCallForwardBlock] block this session!", sessionId);
    //     billNew.setCallState(ConstantConf.CALL_STATE_181);
    //     this.canTerminate = true;
    //     //this.engine.cleanControllerWithSpecificCode(this, 403, "illegal callForward detected!");
    //     try {
    //         unbindLeg(rightLeg);
    //         //不需要同步销毁controller
    //         rightLeg.sendByeOrCancel();
    //         rightLeg.setController(null);
    //         rightLeg = null;
    //         if (mediaSession.getCommitTimes() == 0) {
    //             SIPRequest request = (SIPRequest) leftLeg.firstInviteTransation.getRequest();
    //             sdp183 = mediaSession.processPlayRightSDP(request.getRawContent(), false);
    //             mediaSession.commit();
    //             SIPResponse relResponse;
    //             if (SipUtil.isSupported100rel(request)) {
    //                 relResponse = leftLeg.createProvisionalResponse(183, true);
    //             } else {
    //                 relResponse = leftLeg.createProvisionalResponse(183, false);
    //             }
    //             relResponse.setHeader(this.engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
    //             relResponse.setContent(sdp183, this.engine.contentTypeHeader);
    //             leftLeg.sendReliableProvisionalResponse(relResponse, req -> {
    //                 SIPRequest prack = req.getRequest();
    //                 if (prack.getMethod().equals(SIPRequest.PRACK)) {
    //                     leftLeg.sendResponse200(prack);
    //                 }
    //             });
    //         }
    //     } catch (Exception e) {
    //         logger.error("[{}], [doCallForwardBlock] error when process right sdp", sessionId, e);
    //     }
    //     String transferInterceptPlayCode = businessEvent.getDccAuthConf().getCallForwardedInterceptAudioCode();
    //     if (StringUtils.isBlank(transferInterceptPlayCode)) {
    //         transferInterceptPlayCode = CALL_FORWARD_HIJACKING;
    //     }
    //
    //     MediaService.dccPlayEarlyBeforeEnd(transferInterceptPlayCode, "", 1, MediaConst.PLAY_PERIOD.EARLY, callParameter.getRecordMode(), MediaConst.PRIORITY.LOCAL, event -> ScheduleService.getInstance().schedule(() -> {
    //         try {
    //             canTerminate = true;
    //             this.engine.cleanControllerWithSpecificCode(this, 603, "illegal callForward detected!");
    //         } catch (Exception ee) {
    //             logger.error("[{}], doCallForwardBlock cleanController error!", sessionId, ee);
    //         }
    //     }, 300, TimeUnit.MILLISECONDS), mediaSession, false, businessEvent, billNew.getUserId());
    //
    //     /*MediaService.xmPlayEarlyBeforeEnd(transferInterceptPlayCode, 1, false, event -> ScheduleService.getInstance().schedule(() -> {
    //         try {
    //             canTerminate = true;
    //             this.engine.cleanControllerWithSpecificCode(this, 603, "illegal callForward detected!");
    //         } catch (Exception ee) {
    //             logger.error("[{}], doCallForwardBlock cleanController error!", sessionid, ee);
    //         }
    //     }, 300, TimeUnit.MILLISECONDS), mediaSession, true, 1, businessEvent);*/
    // }

    public void forwardRequestUponState(Leg leg, SipRequestEvent requestEvent, boolean isCanPassVideo) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        Leg oppositeLeg = getOppositeLeg(leg);
        String method = request.getMethod();
        boolean isOppositePickup = controllerState.getCurrentState(oppositeLeg).getStateIndex() == PickUpState.PickUpStateCode;
        AbstractCallState oppositeState = controllerState.getCurrentState(oppositeLeg);
        AbstractCallState selfState = controllerState.getCurrentState(leg);
        DtmfLogger.info(billNew.getId(), "receive request:{}, oppositeState:{},selfState:{}", method, oppositeState, selfState);
        if (leg.isLeft() && method.equals(Request.CANCEL) && isOppositePickup) {
            //修复视频彩铃流程中，语音回落期间，收到主叫 cancel,透传给被叫时异常，导致话单状态为被叫挂机： 3227716751765997
            leg.sendResponse200(request);
            SIPRequest byeRequest = oppositeLeg.createRequest(Request.BYE);
            oppositeLeg.sendRequest(byeRequest, null);
        } else if (!leg.isLeft() && method.equals(Request.BYE) && oppositeLeg.dialog.getState().equals(DialogState.EARLY)) {
            leg.sendResponse200(request);
            oppositeLeg.sendResponse487();
        } else {
            oppositeLeg.forwardRequest(request, mediaSession, e -> {
                if (isCanPassVideo) {
                    if (SipHeaderHelper.ifSupported(request)) {
                        e.removeHeader(ContactHeader.NAME);
                        e.setHeader(this.engine.contactHeaderWithVideo);
                    }
                }
            }, new VideoBillHandler(this), false);
            callStateManager.pluginForwardStateFlushed(oppositeLeg, requestEvent);
        }

    }

    private SetParams getJedisSetParams(boolean setWhenNotExists, Long expireTime, boolean expireTimeInSeconds) {
        SetParams params = new SetParams();
        if (expireTimeInSeconds) {
            params.ex(expireTime); // expire time in second
        } else {
            params.px(expireTime); // expire time in microsecond
        }
        if (setWhenNotExists) {
            params.nx(); // 只有键值不存在才会设置
        }
        return params;
    }

    @Override
    public void onCleanOutEvent() throws Exception {
        if (preciseCallState != null)
            preciseCallState.setControllerTimeOut(true);
    }


    @Override
    public void stateAbnormalCallInfo() {
        //DtmfLogger.info(billNew.getId(),"stateAbnormalCallInfo CallAbnormalInfo:{}",mediaSession.getCallAbnormalInfo());
        if (mediaSession == null)
            return;
        try {
            if (mediaSession.getCallAbnormalInfo().isDoesCalleeContain3gOoBTC()) {
                XmodePromExporterService.getInstance().incRecording3gOoBTC();
                DtmfLogger.info(billNew.getId(), " sdp contain 3gOoBTC");
                if (callParameter.getOptypeAppendReason() == null || callParameter.getOptypeAppendReason() <= 0) {
                    callParameter.setOptypeAppendReason(BillAppendReasonEnum.ZHEJIANG_JILING_INTERCEPT_10.getCode());
                }
            }
            if (mediaSession.getCallAbnormalInfo().isDoesCalleeContainZjjlIP()) {
                XmodePromExporterService.getInstance().incRecordingZjjlIP();
                DtmfLogger.info(billNew.getId(), " contain zhejiang jiling ip");
                if (callParameter.getOptypeAppendReason() == null || callParameter.getOptypeAppendReason() <= 0) {
                    callParameter.setOptypeAppendReason(BillAppendReasonEnum.ZHEJIANG_JILING_INTERCEPT_10.getCode());
                }
            }
        } catch (Exception e) {
            logger.error("[{}] stateAbnormalCallInfo exception!", billNew.getId(), e);
        }
    }


    public SipEngine getSipEngine() {
        return this.engine;
    }

    @Override
    public void doAfterTerminate() {
        getAnalyseLogFlag().setCompanyId(String.valueOf(billNew.getCompanyId()));
        getAnalyseLogFlag().setCustomerId(String.valueOf(billNew.getCustomerId()));
        // getAnalyseLogFlag().setEarlyPlayCodes(billNew.getEarlyPlayCodes());
        // getAnalyseLogFlag().setAnsweredPlayCodes(billNew.getAnsweredPlayCodes());
        try {
            // boolean isNeedMaxCallingLimit = businessEvent.getDccAuthConf().isNeedMaxCallingLimit();
            // ConfigApplication configApplication = DccConfigService.getInstance().getConfigApplication(bizNumAuthDo.getCustomerId());
            // //ConfigCustomer configCustomer = AuthConfigService.getInstance().getConfigCustomer(authNum.getCustomerId());
            // if (isNeedMaxCallingLimit) {
            //     SessionLimitUtil.releaseSession(SessionLimitUtil.getCallKey(configApplication.getAppId()), billNew.getId() + "");
            // }
        } catch (Exception e) {
            logger.error("[{}] max Session release error!", billNew.getId(), e);
        }
    }

    @Override
    public void bindLeftLeg(LeftLeg left) {
        super.bindLeftLeg(left);
        if (callParameter != null) {
            Map<String, String> fileMap = callParameter.getVideo3gpFileMap();
            if (StringUtils.isNotBlank(callParameter.getCallerPlayUrl()) || StringUtils.isNotBlank(callParameter.getVideoFile()) || (fileMap != null && !fileMap.isEmpty())) {
                left.setWouldPlayEarly(true);
            }
        }
    }
}
