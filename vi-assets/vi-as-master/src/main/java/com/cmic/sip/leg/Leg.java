package com.cmic.sip.leg;

import com.cmic.recording.MediaSession;
import com.cmic.recording.videocall.VideoChannelListener;
import com.cmic.service.ScheduleService;
import com.cmic.signaling.Signaling;
import com.cmic.sip.B2buaHelper;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.util.AbnormalBillAnalyser;
import com.cmic.util.AnalyseLog;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.header.Require;
import gov.nist.javax.sip.header.Supported;
import gov.nist.javax.sip.header.extensions.SessionExpiresHeader;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.*;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class Leg {
    private Logger logger = LoggerFactory.getLogger(getClass());
    public Controller controller;
    public Dialog dialog;
    public SipEngine engine;
    public String pearlyMedia = "";
    public Transaction firstInviteTransation;

    /* 保存200-invite、rel183、rel180事件，用于后面ack、prack*/
    private ResponseEvent lastRelResponseEvent;
    private ResponseEvent lastFinalResponseEvent;
    private boolean wouldPlayEarly;

    private final static int MAX_RETRY = 5;

    private final static RequireHeader requireTimer = new Require("timer");

    private final static SupportedHeader supportedTimer = new Supported("timer");

    protected SessionExpiresHeader sessionExpires = null;

    protected boolean fixTimer = false;

    public abstract boolean isLeft();

    public final Map<String, Hook> hooks = new ConcurrentHashMap<String, Hook>();

    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();

    public final Set<String> hooksHasDeal = ConcurrentHashMap.newKeySet();

    public Transaction getFirstInviteTransation() {
        return firstInviteTransation;
    }

    public SIPRequest getFirstInviteRequest() {
        return (SIPRequest) firstInviteTransation.getRequest();
    }

    boolean sdp183 = false;

    private String standbyToTag;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public final void bindDialog(Dialog dlg) {
        dlg.setApplicationData(this);
        this.dialog = dlg;
    }

    public final boolean isNewRelResponse(ResponseEvent responseEvent) {
        // 根据rseq判断该【可靠临时响应】是否属于重发
        if (lastRelResponseEvent == null) return true;
        RSeqHeader lastRseqHeader = (RSeqHeader) lastRelResponseEvent.getResponse().getHeader("RSeq");
        RSeqHeader currentRseqHeader = (RSeqHeader) responseEvent.getResponse().getHeader("RSeq");
        return currentRseqHeader.getSequenceNumber() > lastRseqHeader.getSequenceNumber();
    }

    public final boolean isNewFinalResponse(ResponseEvent responseEvent) {
        // 根据cseq判断该【最终响应】是否属于重发
        if (lastFinalResponseEvent == null) return true;
        CSeqHeader lastCseqHeader = (CSeqHeader) lastFinalResponseEvent.getResponse().getHeader("CSeq");
        CSeqHeader currentCseqHeader = (CSeqHeader) responseEvent.getResponse().getHeader("CSeq");
        return currentCseqHeader.getSeqNumber() > lastCseqHeader.getSeqNumber();
    }

    /**
     * 事务钩子
     *
     * @param response
     * @return
     */
    public Hook findResponseHook(Response response) {

        String branch = ((SIPResponse) response).getTopmostViaHeader().getBranch();
        Hook responseHook = hooks.getOrDefault(branch, null);
        if (responseHook != null) {
            if (hooksHasDeal.contains(branch)) {
                return event -> {
                    //若已处理过，就返回一个空hook，既不传到as也不重复触发钩子
                };
            }
            hooksHasDeal.add(branch);
        }
        return responseHook;
    }

    public Hook findRequestHook(Request request) throws Exception {
        SIPRequest req = (SIPRequest) request;
        String method = req.getMethod();
        if (method.equalsIgnoreCase(Request.PRACK)) {
            RAckHeader rAckHeader = (RAckHeader) req.getHeader(RAckHeader.NAME);
            String hookkey = String.format("prack_%s", rAckHeader.getRSeqNumber());
            Hook hook = hooks.getOrDefault(hookkey, null);
            if (hook != null) {
                if (hooksHasDeal.contains(hookkey)) {
                    return event -> {
                        //若已处理过，就返回一个空hook，既不传到as也不重复触发钩子
                    };
                }
                hooksHasDeal.add(hookkey);
                return hook;
            }
        }

        Hook hook = hooks.getOrDefault(method, null);
        if (hook != null) return hook;

        return null;
    }

    /**
     * 功能：
     * 1、暂存183/180、200-invite等响应，以便后续prack/ack时找到response
     * 2、屏蔽重发的上述几种response， 仅暴露收到的第一个response给controller处理
     *
     * @param
     * @return 该response是否需要交由controller进行处理, 首个返回true、重发返回false
     */
    public boolean prepareResponse(ResponseEvent responseEvent) throws Exception {
        SIPResponse response = (SIPResponse) responseEvent.getResponse();
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
        String method = cseqHeader.getMethod();
        int statuscode = response.getStatusCode();
//        logger.info("prepareResponse: {}  {}", method, response.getStatusCode());
        if (statuscode == 183 && response.getContent() != null) sdp183 = true;

        if (statuscode > 100 && statuscode < 200) {
            String currPearlyMedia = SipUtil.getPearlyMediaValue(response);
            if (!currPearlyMedia.isEmpty()) {
                pearlyMedia = currPearlyMedia;
            }
            if (SipUtil.isRequire100rel(response)) {
                if (isNewRelResponse(responseEvent)) {
//                    logger.info("is newwwwww");
                    lastRelResponseEvent = responseEvent;
                    return true;
                } else {
//                    logger.info("is not newwwwww");
                    return false;
                }
            }


        } else if (statuscode >= 200) {
            if (method.equalsIgnoreCase(Request.INVITE)) {
//                if (isNewFinalResponse(responseEvent)) {
//                    lastFinalResponseEvent = responseEvent;
//                    return true;
//                } else {
//                    return false;
//                }
                //fix: 200-invite的重发需要全部透传
                if (isNewFinalResponse(responseEvent)) {
                    lastFinalResponseEvent = responseEvent;
                }
            }
        }
        return true;
    }

    public boolean prepareRequest(RequestEvent requestEvent) throws Exception {
        return true;
    }

    public void sendRequest(SIPRequest request) throws Exception {
        this.sendRequest(request, null);
    }


    public void sendRequest(SIPRequest request, Hook<SipResponseEvent> hook) throws Exception {
        AnalyseLog.logMsg(this, controller, request, AnalyseLog.MessageTypeEnum.SEND_REQ);
        String method = request.getMethod();
        processTimerForRequest(request);

        //keypoint [信令日志]：全局发送request信令日志记录点
        if (this.controller.isNeedCacheSingallingLog) {
//            logger.info(">>>>>>>>>>>>>>>: thread={} {} reqeust={}", Thread.currentThread().getId(), method, request.toString());
            this.controller.singalling.append(controller.signalParam.setContent(request).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST));
        } else {
            Signaling.log(controller.signalParam.setContent(request), Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST);

//            logger.info(">>>>>>>>>>>>>>>: thread={} {} controler={} reqeust={}", Thread.currentThread().getId(), method, this.controller, request.toString());
        }

        if (method.equals(Request.CANCEL)) {
            ClientTransaction ct = engine.sipProvider.getNewClientTransaction(request);
            ct.sendRequest();
        } else if (method.equals(Request.ACK)) {
            dialog.sendAck(request);
        } else {
            //todo: 待验证reinvite 怎么发？ 貌似也是发事务
            final ClientTransaction ct = engine.sipProvider.getNewClientTransaction(request);
            dialog.sendRequest(ct);
        }
//        logger.info("DialogState={}", this.dialog.getState());

        // 添加基于事务的消息响应钩子,收到response时将触发hook
        // 注意：消息发送完后，sipstack才会为request创建via（自动）；会不会出现response已经回来，但hook还没加上？
        if (hook != null) {
            hooks.put(request.getTopmostViaHeader().getBranch(), hook);
        }

    }

    public void sendRequestRetry(Supplier<SIPRequest> requestSupplier, Hook<SipResponseEvent> hook, int retryTime) throws Exception {
        SIPRequest request = requestSupplier.get();
        AnalyseLog.logMsg(this, controller, request, AnalyseLog.MessageTypeEnum.SEND_REQ);
        String method = request.getMethod();
        Hook<SipResponseEvent> responseHook = hook;
        processTimerForRequest(request);
        if (this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent(request).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST));
        } else {
            Signaling.log(controller.signalParam.setContent(request), Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST);
        }
        if (method.equals(Request.CANCEL)) {
            ClientTransaction ct = engine.sipProvider.getNewClientTransaction(request);
            ct.sendRequest();
        } else if (method.equals(Request.ACK)) {
            dialog.sendAck(request);
        } else {
            final ClientTransaction ct = engine.sipProvider.getNewClientTransaction(request);
            dialog.sendRequest(ct);
            responseHook = event -> {
                if (event.getResponse().getStatusCode() == Response.OK) {
                    hook.apply(event);
                } else if (retryTime > 0) {
                    sendRequestRetry(requestSupplier, hook, retryTime - 1);
                } else {
                    logger.error("[{}] {} fail for {} response", controller.sessionId, method, event.getResponse().getStatusCode());
                }
            };
        }
        // 添加基于事务的消息响应钩子,收到response时将触发hook
        // 注意：消息发送完后，sipstack才会为request创建via（自动）；会不会出现response已经回来，但hook还没加上？
        if (hook != null) {
            hooks.put(request.getTopmostViaHeader().getBranch(), responseHook);
        }
    }

    protected void processTimerForRequest(SIPRequest request) {
        if (!fixTimer) {
            return;
        }
        if (sessionExpires == null) {
            // 与左腿的交互，sessionExpires在200-INVITE时赋值
            // 与右腿的交互，在右腿初始化时创建
            return;
        }
        // AS发请求，当前版本都让左腿刷新
        // 发送request是新的transaction，可以根据需求放心设置refresher
        if (isLeft()) {
            sessionExpires.setRefresher("uas");
        } else {
            sessionExpires.setRefresher("uac");
        }
        request.setHeader(sessionExpires);
        if (!SipUtil.isSupportedTimer(request)) {
            request.addHeader(supportedTimer);
        }
    }

    public void sendReinvite(SIPRequest request, Hook<SipResponseEvent> successHook, Hook<SipResponseEvent> failHook) throws Exception {
        sendReinvite(request, successHook, failHook, 0);
    }

    public void sendReinvite(SIPRequest request, Hook<SipResponseEvent> successHook, Hook<SipResponseEvent> failHook, int retryCount) throws Exception {
        AnalyseLog.logMsg(this, controller, request, AnalyseLog.MessageTypeEnum.SEND_REQ);
        retryCount++;
        processTimerForRequest(request);
        final ClientTransaction ct = engine.sipProvider.getNewClientTransaction(request);
        if (this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent(request).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST));
        } else {
            Signaling.log(controller.signalParam.setContent(request), Signaling.getDirection(this, Signaling.SEND), Signaling.REQUEST);
        }
        int tempRetryCount = retryCount;
        Hook<SipResponseEvent> finallyHook = e -> {
            if (e.getResponse().getStatusCode() / 100 == 2) {
                if (successHook != null) {
                    successHook.apply(e);
                }
            } else if (e.getResponse().getStatusCode() == 491 && tempRetryCount <= MAX_RETRY) {
                ScheduledFuture<?> schedule = ScheduleService.getInstance().schedule(() -> {
                    try {
                        SIPRequest newRequest = this.createRequest(request.getMethod());
                        B2buaHelper.inflateMessage(newRequest, request);
                        sendReinvite(newRequest, successHook, failHook, tempRetryCount);
                    } catch (Exception ee) {
                        logger.error("[{}]reinvite resend fail", controller.sessionId, ee);
                    }
                }, tempRetryCount * 500, TimeUnit.MILLISECONDS);
                scheduledFutures.add(schedule);
            } else if (e.getResponse().getStatusCode() >= 400) {
                if (failHook != null) {
                    failHook.apply(e);
                }
            }
        };
        hooks.put(request.getTopmostViaHeader().getBranch(), finallyHook);
        dialog.sendRequest(ct);
    }

    public void sendReliableProvisionalResponse(Response relResponse) throws Exception {
        this.controller.getAbnormalBillAnalyser().analyse((SIPResponse) relResponse,
                AbnormalBillAnalyser.Type.SEND_RELIABLE_PROVISIONAL_RESPONSE, System.currentTimeMillis());
        if (relResponse.getStatusCode() == 183 && relResponse.getContent() != null) {
            sdp183 = true;
        }
        dialog.sendReliableProvisionalResponse(relResponse);

        //keypoint [信令日志]: 所有发送可靠响应的日志关键节点
//        logger.info("response={}", relResponse.toString());
        if (this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent((SIPMessage) relResponse).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE));
        } else {
            Signaling.log(controller.signalParam.setContent((SIPMessage) relResponse), Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE);
        }
    }

    public void sendNotReliableProvisionalResponse(Response response) throws Exception {
        ServerTransaction st = (ServerTransaction) this.getFirstInviteTransation();
        st.sendResponse(response);
        if (controller.isNeedCacheSingallingLog) {
            controller.singalling.append(controller.signalParam.setContent((SIPMessage) response).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE));
        } else {
            Signaling.log(controller.signalParam.setContent((SIPMessage) response), Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE);
        }
    }

    public void sendReliableProvisionalResponse(SIPResponse response, Hook<SipRequestEvent> action) throws Exception {
        this.sendReliableProvisionalResponse(response);

        //添加消息响应钩子,发送完response后，才能拿到rseqHeader（协议栈自动生成）
        RSeqHeader rSeqHeader = (RSeqHeader) response.getHeader(RSeqHeader.NAME);
        String hookkey = String.format("prack_%s", rSeqHeader.getSequenceNumber());
        hooks.put(hookkey, action);
    }

    /**
     * 功能：支持所有类型（invite、cancel、update、bye、prack、ack）的原消息（originRequest）在当前腿同类型转发
     * tip：
     * 1、自动将两侧消息进行关联（link）， 以便后续reponse的转发
     * 2、支持消息的修饰（DecorateMessage）,可在此接口对header、sdp进行修改
     **/
    public final void forwardRequest(SIPRequest originRequest, MediaSession recordSession, DecorateMessage<SIPRequest> action, VideoChannelListener videoChannelListener, boolean forwardSdp) throws Exception {
        SIPRequest forkedRequest = forkRequest(originRequest, action);
        if (recordSession != null) {
            inflateSdp(recordSession, videoChannelListener, originRequest, forkedRequest, forwardSdp);
        }
        this.sendRequest(forkedRequest);
    }


    private SIPRequest forkRequest(SIPRequest originRequest, DecorateMessage<SIPRequest> action) throws Exception {
        String method = originRequest.getMethod();
        SIPRequest forkedRequest = null;
        if (method.equals(Request.UPDATE) || method.equals(Request.BYE) || method.equals(Request.INVITE) || method.equals(Request.INFO)) {
            // 这里的是 reinvite
            forkedRequest = (SIPRequest) dialog.createRequest(method);
            B2buaHelper.inflateMessage(forkedRequest, originRequest);
            if (action != null) action.apply(forkedRequest);
            B2buaHelper.linkRequest(originRequest, forkedRequest);
        } else if (method.equals(Request.PRACK)) {
            forkedRequest = (SIPRequest) dialog.createPrack(lastRelResponseEvent.getResponse());
            B2buaHelper.inflateMessage(forkedRequest, originRequest);
            if (action != null) action.apply(forkedRequest);
            B2buaHelper.linkRequest(originRequest, forkedRequest);
        } else if (method.equals(Request.ACK)) {
            //ACK是一个新的事务（相对于INVITE,via.branch已经变化）
            SIPResponse response = (SIPResponse) lastFinalResponseEvent.getResponse();
            forkedRequest = (SIPRequest) dialog.createAck(response.getCSeq().getSeqNumber());
            B2buaHelper.inflateMessage(forkedRequest, originRequest);
            if (action != null) action.apply(forkedRequest);
            B2buaHelper.linkRequest(originRequest, forkedRequest);
        } else if (method.equals(Request.CANCEL)) {
            //SIP协议栈只会把第一个cancel暴露上来，UA重发的CANCEL会屏蔽掉
            ClientTransaction inviteClientTransation = (ClientTransaction) getFirstInviteTransation();
            forkedRequest = (SIPRequest) inviteClientTransation.createCancel();
            if (action != null) action.apply(forkedRequest);
            B2buaHelper.linkRequest(originRequest, forkedRequest);
        }
        return forkedRequest;
    }

    public final void forwardRequest(SIPRequest request, MediaSession recordSession, DecorateMessage<SIPRequest> action, boolean forwardSdp) throws Exception {
        this.forwardRequest(request, recordSession, action, null, forwardSdp);
    }

    public final void forwardRequest(SIPRequest request, boolean forwardSdp) throws Exception {
        this.forwardRequest(request, null, null, forwardSdp);
    }

    public final void forwardRequest(SIPRequest request, MediaSession recordSession, boolean forwardSdp) throws Exception {
        this.forwardRequest(request, recordSession, null, forwardSdp);
    }

    public final void forwardRequest(SIPRequest request, DecorateMessage<SIPRequest> action, boolean forwardSdp) throws Exception {
        this.forwardRequest(request, null, action, forwardSdp);
    }


    public final void forwardResponse(SipResponseEvent responseEvent, MediaSession recordSession, DecorateMessage<SIPResponse> action, VideoChannelListener videoChannelListener, boolean forwardSdp) throws Exception {
        SIPResponse originResponse = responseEvent.getResponse();

        DecorateMessage<SIPResponse> processRecording = message -> {
            inflateSdp(recordSession, videoChannelListener, originResponse, message, forwardSdp);
        };

        int statuscode = originResponse.getStatusCode();
        if (statuscode > 100 && statuscode < 200 && SipUtil.isRequire100rel(originResponse)) {
            // 临时响应forward条件：当前的invite事务是否处在early-state?  183来时，dialogState=null
            //if (!this.dialog.getState().equals(DialogState.EARLY)) return;

            SIPResponse linkedRelResponse = (SIPResponse) dialog.createReliableProvisionalResponse(originResponse.getStatusCode());
            ((ToHeader) linkedRelResponse.getHeader(ToHeader.NAME)).setTag("cmic");
            if (isOriginHeaderContainVideo(originResponse)) {
                linkedRelResponse.setHeader(engine.contactHeaderWithVideo);
            } else {
                linkedRelResponse.setHeader(engine.contactHeader);
            }
            B2buaHelper.inflateMessage(linkedRelResponse, originResponse);
            if (wouldPlayEarly && linkedRelResponse.getHeader("P-Early-Media") != null && (statuscode == 181 || (statuscode == 183 && SipUtil.isRequirePrecondition(linkedRelResponse)))) {
                Header pem = engine.headerFactory.createHeader("P-Early-Media", "sendrecv");
                linkedRelResponse.setHeader(pem);
            }
            if (action != null) {
                action.apply(linkedRelResponse);
            }
            if (recordSession != null) {
                processRecording.apply(linkedRelResponse);
            }
            this.sendReliableProvisionalResponse(linkedRelResponse);
        } else {
            // 非临时响应forward条件: 能否找到关联的request (response->clientTransation->request->getApplicationData = linkedRequest)
            ClientTransaction clientTransaction = responseEvent.getClientTransaction();
            if (clientTransaction == null) return;

            SIPRequest request = (SIPRequest) clientTransaction.getRequest();
            if (request == null) return;

            SIPRequest linkedRequest = (SIPRequest) request.getApplicationData();
            if (linkedRequest == null) return;

            SIPResponse linkedResponse = linkedRequest.createResponse(originResponse.getStatusCode());
            if (statuscode > 100 && statuscode < 200) {
                ((ToHeader) linkedResponse.getHeader(ToHeader.NAME)).setTag("cmic");
            }
            B2buaHelper.inflateMessage(linkedResponse, originResponse);
            if (action != null) {
                action.apply(linkedResponse);
            }
            if (recordSession != null) {
                processRecording.apply(linkedResponse);
            }
            this.sendResponse(linkedRequest, linkedResponse);
        }
    }

    private boolean isOriginHeaderContainVideo(SIPResponse originResponse) {
        ContactHeader contactHeader = ((ContactHeader) originResponse.getHeader(ContactHeader.NAME));
        Iterator iterator = contactHeader.getParameterNames();
        while (iterator.hasNext()) {
            String name = (String) iterator.next();
            if (name.equals("video")) {
                return true;
            }
        }
        return false;
    }

    private void inflateSdp(MediaSession recordSession, VideoChannelListener videoChannelListener, SIPMessage originMessage, SIPMessage message, boolean forwardSdp) throws ParseException {
        byte[] sdp = originMessage.getRawContent();
        if (sdp != null) {
            if (forwardSdp) {
                message.setContent(originMessage.getRawContent(), originMessage.getContentTypeHeader());
                return;
            }
            if (this.isLeft()) {
                //message.setContent(recordSession.processRightSDPV3(originMessage), this.engine.contentTypeHeader);
                message.setContent(recordSession.processRightSDPV3(originMessage), originMessage.getContentTypeHeader());
                recordSession.commit();
            } else {
                //message.setContent(recordSession.processLeftSDPV3(originMessage), this.engine.contentTypeHeader);
                message.setContent(recordSession.processLeftSDPV3(originMessage), originMessage.getContentTypeHeader());
                recordSession.commit();
            }
            if (videoChannelListener != null) {
                int state = recordSession.isCompleteVideoNegotiation();
                if (state == 1) {
                    videoChannelListener.onClose();
                } else if (state == 2) {
                    videoChannelListener.onOpen();
                }
            }
        }
    }

    public final void forwardResponse(SipResponseEvent responseEvent, MediaSession recordSession, DecorateMessage<SIPResponse> action, boolean forwardSdp) throws Exception {
        forwardResponse(responseEvent, recordSession, action, null, forwardSdp);
    }

    public final void forwardResponse(SipResponseEvent responseEvent, MediaSession recordSession, boolean forwardSdp) throws Exception {
        forwardResponse(responseEvent, recordSession, null, forwardSdp);
    }

    public final void forwardResponse(SipResponseEvent responseEvent, DecorateMessage<SIPResponse> action, boolean forwardSdp) throws Exception {
        forwardResponse(responseEvent, null, action, forwardSdp);
    }

    public final void forwardResponse(SipResponseEvent responseEvent, boolean forwardSdp) throws Exception {
        forwardResponse(responseEvent, null, null, forwardSdp);
    }

    /**
     * 功能：全局非临时响应发送入口，主要用途为:
     * 1、日志记录
     * 2、ContactHeader检查及补充
     **/
    public void sendResponse(SIPRequest linkedRequest, Response originResponse) throws Exception {
        AnalyseLog.logMsg(this, controller, (SIPResponse) originResponse, AnalyseLog.MessageTypeEnum.SEND_RESP);
//        logger.info(">>>>>>>>>>>>>>>: thread={} {}-{}", Thread.currentThread().getId(), linkedRequest.getMethod(), originResponse.getStatusCode());
//        logger.info("DialogState={}", this.dialog.getState());

        if (originResponse.getHeader(ContactHeader.NAME) == null) {
            originResponse.setHeader(engine.contactHeader);
        }
        processTimerForResponse2xx(linkedRequest, (SIPResponse) originResponse);
        if (this.controller.isNeedCacheSingallingLog) {
            this.controller.singalling.append(controller.signalParam.setContent((SIPMessage) originResponse).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE));
        } else {
            Signaling.log(controller.signalParam.setContent((SIPMessage) originResponse), Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE);
        }
        ToHeader toHeader = ((SIPResponse) originResponse).getToHeader();
        if (toHeader != null && StringUtils.isBlank(toHeader.getTag()) && StringUtils.isNotBlank(standbyToTag)){
            ((SIPResponse) originResponse).setToTag(standbyToTag);
        }
        ServerTransaction linkedServerTransaction = (ServerTransaction) linkedRequest.getTransaction();
        linkedServerTransaction.sendResponse(originResponse);
    }

    public void sendResponse180(Response originResponse) throws Exception {
        SIPResponse ringResponse = this.createProvisionalResponse(180, false);
        ((ToHeader) ringResponse.getHeader(ToHeader.NAME)).setTag("cmic");
        if (originResponse != null) {
            B2buaHelper.inflateMessage(ringResponse, originResponse);
        }
        ringResponse.setHeader(engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
        ServerTransaction st = (ServerTransaction) this.getFirstInviteTransation();
        st.sendResponse(ringResponse);
        if (controller.isNeedCacheSingallingLog) {
            controller.singalling.append(controller.signalParam.setContent(ringResponse).toLogString(Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE));
        } else {
            Signaling.log(controller.signalParam.setContent(ringResponse), Signaling.getDirection(this, Signaling.SEND), Signaling.RESPONSE);
        }
    }


    public void sendResponse200(SIPRequest request) throws Exception {
        this.sendResponse200(request, null);
    }

    private void processTimerForResponse2xx(SIPRequest request, SIPResponse response) {
        if (!fixTimer) {
            return;
        }
        if (response.getStatusCode() / 100 != 2) {
            return;
        }
        if (!SipUtil.isSupportedTimer(request)) {
            return;
        }
        SessionExpiresHeader sessionExpires = (SessionExpiresHeader) request.getHeader(SessionExpiresHeader.NAME);
        if (sessionExpires == null) {
            // 请求不带Session-Expires，但响应是由业务拷贝构建的，有可能带上，为了稳妥去掉，与请求一致
            response.removeHeader(SessionExpiresHeader.NAME);
            return;
        }
        if (StringUtils.isEmpty(sessionExpires.getRefresher())) {
            // 如果请求带了refresher，当前先不改。
            if (isLeft()) {
                sessionExpires.setRefresher("uac");
            } else {
                sessionExpires.setRefresher("uas");
            }
            // debug for timer
//            try {
//                sessionExpires.setExpires(600);
//            } catch (InvalidArgumentException ignored) {}
        }
        // 对Session-Expires的2xx响应，需带Require: timer
        if (!SipUtil.isRequireTimer(response)) {
            response.addHeader(requireTimer);
        }
        response.setHeader(sessionExpires);
        this.sessionExpires = sessionExpires;
    }

    public void sendResponse200(SIPRequest request, DecorateMessage<SIPResponse> action) throws Exception {
        SIPResponse response = request.createResponse(200);
        response.setHeader(engine.contactHeader);
        if (action != null) {
            action.apply(response);
        }
        this.sendResponse(request, response);

        //keypoint [信令日志]: 所有发送非可靠响应日志记录点
//        logger.info("response={}", response.toString());

    }

    public void sendResponse180(SIPRequest linkedRequest, DecorateMessage<SIPResponse> action) throws Exception {
        SIPResponse response180 = linkedRequest.createResponse(SIPResponse.RINGING);
        response180.setHeader(engine.contactHeader);
        if (action != null) {
            action.apply(response180);
        }
        this.sendResponse(linkedRequest, response180);
    }

    public void sendResponse488(SIPRequest request) throws Exception {
        this.sendResponse488(request, null);
    }

    public void sendResponse488(SIPRequest request, DecorateMessage<SIPResponse> action) throws Exception {
        SIPResponse response = request.createResponse(488);
        response.setHeader(engine.contactHeader);
        if (action != null) {
            action.apply(response);
        }
        this.sendResponse(request, response);
    }

    /**
     * 发送200-invite， ack无200确认机制，所以无ResponseHook机制；
     * ack中可能带sdp， 因此提供DecorateHook
     */
    public void sendAck() throws Exception {
        this.sendAck(lastFinalResponseEvent.getResponse());
    }

    public void sendAck(Response finalResponse) throws Exception {
        SIPRequest ackRequest = (SIPRequest) dialog.createAck(((SIPResponse) finalResponse).getCSeq().getSeqNumber());
        this.sendRequest(ackRequest);
    }

    public SIPRequest createAck(Response finalResponse) throws Exception {
        SIPRequest ackRequest = (SIPRequest) dialog.createAck(((SIPResponse) finalResponse).getCSeq().getSeqNumber());
        return ackRequest;
    }

    public void sendPrack(Response relPresponse) throws Exception {
        sendPrack(relPresponse, null);
    }

    public void sendPrack(Response relPresponse, Hook<SipResponseEvent> hook) throws Exception {
        SIPRequest prackRequest = (SIPRequest) dialog.createPrack(relPresponse);
        this.sendRequest(prackRequest, hook);
    }

    public void sendResponse487() throws Exception {
        ServerTransaction st = (ServerTransaction) this.getFirstInviteTransation();
        SIPResponse response487 = ((SIPRequest) st.getRequest()).createResponse(487);
        this.sendResponse((SIPRequest) st.getRequest(), response487);
    }

    public void sendResponse500() throws Exception {
        ServerTransaction st = (ServerTransaction) this.getFirstInviteTransation();
        SIPResponse response500 = ((SIPRequest) st.getRequest()).createResponse(500);
        this.sendResponse((SIPRequest) st.getRequest(), response500);
    }

    public void sendSpecificErrorCodeResponse(int Code, String warningMessage) throws Exception {
        ServerTransaction st = (ServerTransaction) this.getFirstInviteTransation();
        SIPResponse response = ((SIPRequest) st.getRequest()).createResponse(Code);
        /*
         *  399的含义：Miscellaneous warning: The warning text can include arbitrary
         *  information to be presented to a human user or logged. A
         *  system receiving this warning MUST NOT take any automated
         *  action.
         */
        WarningHeader warningHeader = engine.headerFactory.createWarningHeader("cmic.com", 399, warningMessage);
        response.setHeader(warningHeader);
        this.sendResponse((SIPRequest) st.getRequest(), response);
    }


    public void sendByeOrCancel() throws Exception {
        sendByeOrCancel(null);
    }


    /**
     * serverDialog拆线
     */
    public void sendByeOrCancel(Hook hook) throws Exception {
        if (dialog == null) return;

        if (dialog.getState() == null) {
            ClientTransaction inviteClientTransation = (ClientTransaction) getFirstInviteTransation();
            SIPRequest cancelRequest = (SIPRequest) inviteClientTransation.createCancel();
            if (hook != null) {
                this.sendRequest(cancelRequest, hook);
            } else {
                this.sendRequest(cancelRequest);
            }
            return;
        }
        if (dialog.getState().equals(DialogState.CONFIRMED)) {
            SIPRequest byeRequest = this.createRequest(Request.BYE);
            if (hook != null) {
                this.sendRequest(byeRequest, hook);
            } else {
                this.sendRequest(byeRequest, null);
            }
        } else if (dialog.getState().equals(DialogState.EARLY)) {
            ClientTransaction inviteClientTransation = (ClientTransaction) getFirstInviteTransation();
            SIPRequest cancelRequest = (SIPRequest) inviteClientTransation.createCancel();
            if (hook != null) {
                this.sendRequest(cancelRequest, hook);
            } else {
                this.sendRequest(cancelRequest);
            }

        }
    }

    /**
     * clientDialog拆线
     */
    public void sendByeOr500() throws Exception {
        if (dialog == null) {
            return;
        }

        if (dialog.getState() == null) {
            this.sendResponse500();
            return;
        }


        if (dialog.getState().equals(DialogState.CONFIRMED)) {
            SIPRequest byeRequest = this.createRequest(Request.BYE);
            this.sendRequest(byeRequest, null);
        } else if (dialog.getState().equals(DialogState.EARLY)) {
            this.sendResponse500();
        }
    }

    /**
     * clientDialog拆线
     */
    public void sendByeOrSpecificCodeResp(int code, String warningMessage) throws Exception {
        if (dialog == null) {
            return;
        }

        if (dialog.getState() == null) {
            this.sendSpecificErrorCodeResponse(code, warningMessage);
            return;
        }
        if (dialog.getState().equals(DialogState.CONFIRMED)) {
            SIPRequest byeRequest = this.createRequest(Request.BYE);
            this.sendRequest(byeRequest, null);
        } else if (dialog.getState().equals(DialogState.EARLY)) {
            this.sendSpecificErrorCodeResponse(code, warningMessage);
        }
    }


    /**
     * clientDialog拆线
     */
    public void sendByeOr487() throws Exception {
        if (dialog == null) return;

        if (dialog.getState() == null) {
            this.sendResponse487();
            return;
        }


        if (dialog.getState().equals(DialogState.CONFIRMED)) {
            SIPRequest byeRequest = this.createRequest(Request.BYE);
            this.sendRequest(byeRequest, null);
        } else if (dialog.getState().equals(DialogState.EARLY)) {
            this.sendResponse487();
        }
    }

    /******************* create message *****************/

    public SIPResponse createProvisionalResponse(int statusCode, boolean isReliable) throws Exception {
        if (isReliable) {
            SIPResponse relResponse = (SIPResponse) dialog.createReliableProvisionalResponse(statusCode);
            ((ToHeader) relResponse.getHeader(ToHeader.NAME)).setTag("cmic");
            relResponse.setHeader(engine.contactHeader);
            relResponse.addHeader(engine.headerFactory.createSupportedHeader("100rel,timer,histinfo,precondition"));
            relResponse.addHeader(engine.headerFactory.createAllowHeader("INVITE, ACK, CANCEL, OPTIONS, BYE, UPDATE, SUBSCRIBE, REFER, PRACK, REGISTER, INFO, PUBLISH, MESSAGE, NOTIFY"));
            return relResponse;
        } else {
            SIPRequest inviteRequest = (SIPRequest) firstInviteTransation.getRequest();
            SIPResponse response = inviteRequest.createResponse(statusCode);
            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("cmic");
            response.setHeader(engine.contactHeader);
            response.addHeader(engine.headerFactory.createSupportedHeader("timer,histinfo,precondition"));
            response.addHeader(engine.headerFactory.createAllowHeader("INVITE, ACK, CANCEL, OPTIONS, BYE, UPDATE, SUBSCRIBE, REFER, PRACK, REGISTER, INFO, PUBLISH, MESSAGE, NOTIFY"));
            return response;
        }
    }

    public SIPResponse createResponse(int statusCode, Request linkedRequest) throws Exception {
        SIPResponse response = ((SIPRequest) linkedRequest).createResponse(statusCode);
        return response;
    }

    // update info
    public SIPRequest createRequest(String method) throws Exception {
        return (SIPRequest) dialog.createRequest(method);
    }

    /******************* hook *****************/

    /**
     * 功能：按消息类型（method）监听消息
     */
    public void onRequest(String method, Hook<SipRequestEvent> action) throws Exception {
        hooks.put(method, action);
    }

    /**
     * 功能：收到消息后，执行hook，然后把钩子销毁;
     * 示例场景：向主叫侧发送180-rel后，监听收到的第一个update(precondition)
     */
    public void waitRequest(String method, Hook<SipRequestEvent> hook) throws Exception {
        hooks.put(method, hook.addBefore(event -> {
            hooks.remove(method);
        }));
    }

    /**
     * 清除leg残留任务
     */
    public void terminated() {
        for (ScheduledFuture<?> future : scheduledFutures) {
            if (!future.isDone() && !future.isCancelled()) {
                try {
                    future.cancel(true);
                } catch (Exception e) {
                    logger.warn("[{}]cancel future failed when terminate leg", controller.sessionId);
                }
            }
        }

    }

    public ResponseEvent getLastFinalResponseEvent() {
        return lastFinalResponseEvent;
    }

    public ResponseEvent getLastRelResponseEvent() {
        return lastRelResponseEvent;
    }

    public void setWouldPlayEarly(boolean wouldPlayEarly) {
        this.wouldPlayEarly = wouldPlayEarly;
    }

    public void setStandbyToTag(String toTag){
        this.standbyToTag = toTag;
    }
}
