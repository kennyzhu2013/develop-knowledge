package com.cmic.sip.engine;

import com.cmic.service.PromExporterService;
import com.cmic.service.Slf4jMDCService;
import com.cmic.signaling.SignalParam;
import com.cmic.signaling.Signaling;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Hook;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.util.AbnormalBillAnalyser;
import com.cmic.util.AnalyseLog;
import com.cmic.util.SipUtil;
import com.util.IPPort;
import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.util.Properties;

/**
 * sip协议栈基类对请求/响应的默认操作：找到
 **/

public abstract class SipEngine implements SipListener {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private static final int LF = 10; // \n
    private static final int CR = 13; // \r

    public static SipEngine instance;
    public AddressFactory addressFactory;
    public MessageFactory messageFactory;
    public HeaderFactory headerFactory;
    public SipStack sipStack;
    public SipProvider sipProvider;
    public ContactHeader contactHeader;
    public Contact contactHeaderWithVideo;
    public ContentTypeHeader contentTypeHeader;

    /**
     * 消息引擎是否在线（是否对Option消息回复200-OK）
     **/
    private boolean isOnline = true;

    private String host = "";

    private int sipProxyMode = 0;

    public final void initStack(String ip, int port, Properties properties) throws Exception {
        SipFactory sipFactory = SipFactory.getInstance();
        sipFactory.setPathName("gov.nist");
        sipStack = sipFactory.createSipStack(properties);
        headerFactory = sipFactory.createHeaderFactory();
        addressFactory = sipFactory.createAddressFactory();
        messageFactory = sipFactory.createMessageFactory();
        ListeningPointImpl lp = (ListeningPointImpl) sipStack.createListeningPoint(ip, port, "udp");
        sipProvider = sipStack.createSipProvider(lp);
        contactHeader = lp.createContactHeader();
        contactHeaderWithVideo = (Contact) lp.createContactHeader();
        contactHeaderWithVideo.setVideoParam();
        contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

        // invite事务自动触发创建dialog
        sipProvider.setAutomaticDialogSupportEnabled(false);
        sipProvider.addSipListener(this);
        instance = this;
        this.host = ip;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public final void turnOnOff(boolean isOnline) {
        this.isOnline = isOnline;
    }

    public final int getState() {
        //  2: （离线）不接收新请求   3：在线
        return this.isOnline ? 3 : 2;
    }

    public int getSipProxyMode() {
        return sipProxyMode;
    }

    public void setSipProxyMode(int sipProxyMode) {
        this.sipProxyMode = sipProxyMode;
    }

    public IPPort getSipProxy() {
        return null;
    }

    public final LeftLeg createLeftLeg(SIPRequest inviteRequest, boolean fixTimer) throws Exception {
        ServerTransaction firstInviteTransation = (ServerTransaction) inviteRequest.getTransaction();
        if (firstInviteTransation == null) {
            firstInviteTransation = sipProvider.getNewServerTransaction(inviteRequest);
        }
        if (firstInviteTransation.getDialog() == null) {
            sipProvider.getNewDialog(firstInviteTransation);
        }

        return new LeftLeg(this, firstInviteTransation, firstInviteTransation.getDialog(), fixTimer);
    }

    public final LeftLeg createLeftLeg(SIPRequest inviteRequest) throws Exception {
        // fixTimer为true则支持会话刷新，默认为false，后续逐步开放至全业务
        return createLeftLeg(inviteRequest, false);
    }

    public final RightLeg createRightLeg(SIPRequest inviteRequest, boolean fixTimer) throws Exception {
        // 质检流程无需默认添加xsipproxy，接续被叫前会根据是I送进来还是S送进来生成不同的信令格式
        // 如果指定了信令代理，则在初始INVITE添加相关的Route头域
        // if (this.getSipProxyMode() == 3) {
        //     IPPort proxy = this.getSipProxy();
        //     if (proxy != null) {
        //         SipUtil.addProxyRoute(inviteRequest, proxy, this);
        //     } else {
        //         logger.error("sipProxyMode=3 but SipProxy is null");
        //     }
        // }
        ClientTransaction firstInviteTransation = (ClientTransaction) inviteRequest.getTransaction();
        if (firstInviteTransation == null) {
            firstInviteTransation = sipProvider.getNewClientTransaction(inviteRequest);
        }
        if (firstInviteTransation.getDialog() == null) {
            sipProvider.getNewDialog(firstInviteTransation);
        }
        return new RightLeg(this, firstInviteTransation, firstInviteTransation.getDialog(), fixTimer);
    }

    public final RightLeg createRightLeg(SIPRequest inviteRequest) throws Exception {
        // fixTimer为true则支持会话刷新，默认为false，后续逐步开放至全业务
        return createRightLeg(inviteRequest, false);
    }

    private void preprocessContent(Message message, String sessionId) {
        byte[] rawContent = message.getRawContent();
        if (rawContent == null)
            return;
        if (!(message instanceof SIPMessage)) {
            logger.warn("[{}]message is not instance of SIPMessage", sessionId);
            return;
        }

        int length = rawContent.length;
        try {
            // 正常最末尾就是CRLF结尾，如果倒数第三和第四也是CRLF，证明多了一个空行
            // 目前只处理多了一个CRLF的情况，如果后面遇到多个的，再考虑优化
            if (rawContent[length - 3] == LF && rawContent[length - 4] == CR) {
                logger.info("[{}]preprocess content, message have double CRLF at the end", sessionId);
                byte[] newContent = new byte[length - 2];
                System.arraycopy(rawContent, 0, newContent, 0, length - 2);
                message.setContent(newContent, ((SIPMessage) message).getContentTypeHeader());
            } else {
                logger.info("[{}]preprocess content, CRLF is normal at the end of message", sessionId);
            }
        } catch (Exception e) {
            logger.error("[{}]preprocess content failed", sessionId, e);
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        Controller controller = null;
        try {
            Request request = requestEvent.getRequest();
            String method = request.getMethod();
//            logger.info("<<<<<<<<<<<<<<<: thread={} {}", Thread.currentThread().getId(), method);

            if (method.equals(Request.OPTIONS)) {
                doOption(requestEvent);
                return;
            }
            //reinvite和update一样可以拿到dialog
            if (requestEvent.getDialog() != null) {
                doRequest(requestEvent);
                return;
            }
            if (method.equals(Request.INVITE)) {

                PromExporterService.getInstance().recordInvite(requestEvent);
                /** 线程互斥：以防两个相同Invite同时到达时，争抢创建controller
                 * 后发现：不加锁也没问题， sipProvider.getNewServerTransaction（）具有互斥的作用， 抓到TransactionAlreadyExistsException退出即可
                 **/
                Transaction st = requestEvent.getServerTransaction();
                if (st == null) {
                    try {
                        this.sipProvider.getNewServerTransaction(requestEvent.getRequest());
                    } catch (TransactionAlreadyExistsException e) {
//                                logger.warn("Got retransmited invite", e);
                        return;
                    }
                    if (shouldSessionBlock(requestEvent)) {
                        blockInvite(requestEvent);
                        return;
                    }
                    controller = doBuildController(requestEvent);
                    if (controller == null) {
                        logger.info("request-uri is {}, request-from is {} controller is null", request.getRequestURI(), request.getHeaders(From.NAME));
                        return;
                    }
                    Slf4jMDCService.putMDCCustomerCallID(controller.sessionId);
                    ControllerManager.getInstance().addController(controller.sessionId, controller);
                    //keypoint [信令日志]: 所有接受请求的日志关键节点
//                    logger.info("request={}", requestEvent.getRequest().toString());
                    String msgContent = requestEvent.getRequest().toString();
                    if (controller.isNeedCacheSingallingLog) {
                        controller.singalling.append(controller.signalParam.setContent((SIPMessage) requestEvent.getRequest()).toLogString(Signaling.INITIAL, Signaling.REQUEST));
                    } else {
                        Signaling.log(controller.signalParam.setContent((SIPMessage) requestEvent.getRequest()), Signaling.INITIAL, Signaling.REQUEST);
                    }
                    AnalyseLog.logMsg(null, controller, (SIPRequest) request, AnalyseLog.MessageTypeEnum.FIRST_INVITE);
                    controller.start((SIPRequest) request);
                } else {
                    //某些发送bye之后会回cseq=1的invite？？？？？？？？？？？？？？？
                    doRequest(requestEvent);
                    return;
                }
            } else {
                logger.error("Dialog is not found and non invite method. method:{},request:{}", method, request);
            }


        } catch (Exception e) {
            logger.error("[{}] siperror.processRequest", controller == null ? 0 : controller.sessionId, e);
            //todo: 回收session
        }
    }

    private void blockInvite(RequestEvent requestEvent) {
        SIPRequest req = (SIPRequest) requestEvent.getRequest();
        try {
            Signaling.log(new SignalParam().setSessionId("99999999999").setContent(req), Signaling.INITIAL, Signaling.REQUEST);
            logger.info("session block, callID:{}", req.getHeaders(From.CALL_ID));
            SIPResponse response500 = req.createResponse(500, "cmic session block");
            if (response500.getHeader("Contact") == null) {
                response500.setHeader(contactHeader);
            }
            response500.setToTag("cmic");
            ServerTransaction serverTransaction = (ServerTransaction) req.getTransaction();
            serverTransaction.sendResponse(response500);
        } catch (Exception e) {
            logger.error("block invite error! , request:{}", requestEvent.getRequest(), e);
        }
    }

    //option默认直接回200-OK
    public void doOption(RequestEvent requestEvent) {
        if (this.isOnline) {
            final Request request = requestEvent.getRequest();
            try {
                final Response response = this.messageFactory.createResponse(200, request);
                this.sipProvider.sendResponse(response);
            } catch (Exception e) {
                logger.info("", e);
            }
        }
    }

    //收到初始Invite
    public abstract Controller doBuildController(RequestEvent requestEvent);

    public boolean shouldSessionBlock(RequestEvent requestEvent) {
        return false;
    }

    //收到非初始Invite的其他请求
    public void doRequest(RequestEvent requestEvent) {
        String sessionId = "";
        try {
            sessionId = ((SIPMessage) requestEvent.getRequest()).getCallId().getCallId();
            if (requestEvent.getDialog() != null) {
                Leg leg = (Leg) requestEvent.getDialog().getApplicationData();
//                logger.info("controller===={}", leg.controller);

                //keypoint [信令日志]: 所有接受请求的日志关键节点


//                logger.info("request={}", requestEvent.getRequest().toString());

                if (leg.controller != null) {
                    Slf4jMDCService.putMDCCustomerCallID(leg.controller.sessionId);
                    String logContent = leg.controller.signalParam.setContent((SIPMessage) requestEvent.getRequest()).toLogString(Signaling.getDirection(leg, Signaling.RECV), Signaling.REQUEST);
                    if (leg.controller.isNeedCacheSingallingLog) {
                        leg.controller.singalling.append(logContent);
                    } else {
                        Signaling.log(leg.controller.signalParam.setContent((SIPMessage) requestEvent.getRequest()), Signaling.getDirection(leg, Signaling.RECV), Signaling.REQUEST);
                    }
                    sessionId = String.valueOf(leg.controller.sessionId);
//                    logger.info("leg.size={}", leg.controller.sizeOfLegs());
                }
//                logger.info("DialogState={}", leg.dialog.getState());
                if (leg.prepareRequest(requestEvent)) {
                    if (leg.controller != null) {
                        if (leg.controller.infoCollector != null) {
                            leg.controller.infoCollector.saveMediaDirection(leg, new SipRequestEvent(requestEvent).getRequest());
                        }
                        leg.controller.getAbnormalBillAnalyser().analyse((SIPMessage) requestEvent.getRequest(),
                                AbnormalBillAnalyser.Type.RECV_REQUEST, System.currentTimeMillis());
                        preprocessContent(requestEvent.getRequest(), sessionId);
                    }
                    Hook<SipRequestEvent> function = leg.findRequestHook(requestEvent.getRequest());
                    if (function != null) {
                        function.apply(new SipRequestEvent(requestEvent));
                        return;
                    }
                    if (leg.controller != null) {
                        if (requestEvent.getRequest().getMethod().equalsIgnoreCase(Request.UPDATE)) {
                            leg.controller.resetLastUpdateTime();
                        }
                        if (requestEvent.getRequest().getMethod().equalsIgnoreCase(Request.INVITE)) {
                            leg.controller.resetLastUpdateTime();
                        }
                        AnalyseLog.logMsg(leg, leg.controller, (SIPRequest) requestEvent.getRequest(), AnalyseLog.MessageTypeEnum.RECV_REQ);
                        leg.controller.processRequest(leg, new SipRequestEvent(requestEvent));

                    }
                }
            }
        } catch (Exception e) {
            logger.error("[{}] siperror.processRequest msg is {} ", sessionId, requestEvent.getRequest().toString(), e);
            if (requestEvent.getDialog() != null) {
                Leg leg = (Leg) requestEvent.getDialog().getApplicationData();
                leg.controller.canTerminate = true;
                this.cleanController(leg.controller);
            }
        }
    }


    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        String sessionId = "";
        try {
            SIPResponse response = (SIPResponse) responseEvent.getResponse();
            sessionId = response.getCallId().getCallId();
            CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
            String method = cseqHeader.getMethod();
//            logger.info("<<<<<<<<<<<<<<<: thread={} {}-{}", Thread.currentThread().getId(), method, response.getStatusCode());

//            logger.info("responseEvent.getDialog={}", responseEvent.getDialog());


            if (responseEvent.getResponse().getStatusCode() == 100) {
                Leg leg = (Leg) responseEvent.getDialog().getApplicationData();
                Signaling.log(leg.controller.signalParam.setContent((SIPMessage) responseEvent.getResponse()), Signaling.getDirection(leg, Signaling.RECV), Signaling.RESPONSE);
//                logger.info("DialogState={}", leg.dialog.getState());
                //呼出率统计指标：被叫回 100-trying 也算
                if (leg.controller != null) {
                    if (leg.controller.infoCollector != null) {
                        leg.controller.infoCollector.saveQ850AndFirstRspTime(leg, response);
                    }
                }
                return;
            }

            if (responseEvent.getDialog() != null) {
                Leg leg = (Leg) responseEvent.getDialog().getApplicationData();

                //keypoint [信令日志]: 所有接受响应的日志关键节点
                if (leg.controller != null) {
                    Slf4jMDCService.putMDCCustomerCallID(leg.controller.sessionId);
                    if (leg.controller.isNeedCacheSingallingLog) {
                        leg.controller.singalling.append(leg.controller.signalParam.setContent((SIPMessage) responseEvent.getResponse()).toLogString(Signaling.getDirection(leg, Signaling.RECV), Signaling.RESPONSE));
                    } else {
                        Signaling.log(leg.controller.signalParam.setContent((SIPMessage) responseEvent.getResponse()), Signaling.getDirection(leg, Signaling.RECV), Signaling.RESPONSE);
                    }
                }

                if (leg.controller != null) {
                    sessionId = String.valueOf(leg.controller.sessionId);
                }
//            logger.info("response={}", responseEvent.getResponse().toString());

                //                logger.info("DialogState={}", leg.dialog.getState());
                if (leg.prepareResponse(responseEvent)) {
//                    logger.info("controller={}", leg.controller);
                    if (leg.controller != null) {
//                        logger.info("leg.size={}", leg.controller.sizeOfLegs());
                        if (leg.controller.infoCollector != null) {
                            leg.controller.infoCollector.saveMediaDirection(leg, response);
                            leg.controller.infoCollector.checkCalleeTransfer(response);
                            leg.controller.infoCollector.saveQ850AndFirstRspTime(leg, response);
                        }
                    }
                    AnalyseLog.logMsg(leg, leg.controller, response, AnalyseLog.MessageTypeEnum.RECV_RESP);
                    if (leg.controller != null) {
                        leg.controller.getAbnormalBillAnalyser().analyse(response,
                                AbnormalBillAnalyser.Type.RECV_RESPONSE, System.currentTimeMillis());
                    }
                    preprocessContent(responseEvent.getResponse(), sessionId);
                    Hook<SipResponseEvent> function = leg.findResponseHook(responseEvent.getResponse());
                    if (function != null) {
                        function.apply(new SipResponseEvent(responseEvent));
                        return;
                    }

                    // 与controller绑定的leg， 才由controller对消息进行处理；mrgleg一般不和controller进行绑定，消息全部由hook进行处理；
                    if (leg.controller != null) {
                        if (response.getStatusCode() / 10 == 18 && !leg.controller.isCallForwardAllow(response)) {
                            leg.controller.doCallForwardBlock(response);
                        } else {
                            leg.controller.processResponse(leg, new SipResponseEvent(responseEvent));
                        }
                    }
                }

            } else {
                logger.info("dialogCallID:{}, responseEvent.getDialog() is null", sessionId);
            }
        } catch (Exception e) {
            SIPResponse response = (SIPResponse) responseEvent.getResponse();
            CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
            logger.error("[{}] siperror.processResponse, msg is {}", sessionId, response, e);
            if (responseEvent.getDialog() != null) {
                Leg leg = (Leg) responseEvent.getDialog().getApplicationData();
                leg.controller.canTerminate = true;
                this.cleanController(leg.controller);
            }
        }

    }

    /**
     * 功能：全局异常清道夫, 负责对controller管理的所有leg发送拆线指令(根据dialog的状态发bye、cancel、487)
     * tip: 是否会导致循环exception？
     * 1、发送bye时，会立刻触发processDialogTerminated, 无需等待200-ok
     * 2、发送487室，会立刻触发processDialogTerminated
     * 3、发送cancel时，收到487，sitstack会触发processDialogTerminated
     * 因此,能保证dialog的正常释放, 后续的消息均进不了流程（controller、leg）,不会导致无限循环处理
     **/
    public void cleanController(Controller controller) {
        // 先浅拷贝一份legs出来; 不能用Iterator方式，遍历的同时，拆线会导致leg从controller.legs中删除；
        logger.info("thread={} cleanController.......{}", Thread.currentThread().getId(), controller.sessionId);
//        if(true) return;
        Leg[] legs = controller.getLegs();

        for (Leg leg : legs) {
            try {
                if (leg.dialog.isServer()) {
                    leg.sendByeOr487();
                } else {
                    leg.sendByeOrCancel();
                }
            } catch (Exception e) {
                logger.error("[{}] cleanController exception", controller.sessionId, e);
            }
        }
    }

    /**
     * 功能：全局异常清道夫, 负责对controller管理的所有leg发送拆线指令(根据dialog的状态发bye、cancel、487)
     * tip: 是否会导致循环exception？
     * 1、发送bye时，会立刻触发processDialogTerminated, 无需等待200-ok
     * 2、发送487室，会立刻触发processDialogTerminated
     * 3、发送cancel时，收到487，sitstack会触发processDialogTerminated
     * 因此,能保证dialog的正常释放, 后续的消息均进不了流程（controller、leg）,不会导致无限循环处理
     **/
    public void cleanControllerWith500(Controller controller) {
        // 先浅拷贝一份legs出来; 不能用Iterator方式，遍历的同时，拆线会导致leg从controller.legs中删除；
        logger.info("thread={} cleanController.......{}", Thread.currentThread().getId(), controller.sessionId);
//        if(true) return;
        Leg[] legs = controller.getLegs();

        for (Leg leg : legs) {
            try {
                if (leg.dialog.isServer()) {
                    leg.sendByeOr500();
                } else {
                    leg.sendByeOrCancel();
                }
            } catch (Exception e) {
                logger.error("[{}] cleanControllerWith500 exception", controller.sessionId, e);
            }
        }
    }

    public void cleanControllerWithSpecificCode(Controller controller, int respCode, String warningMessage) {
        logger.info("thread={} cleanController.......{}", Thread.currentThread().getId(), controller.sessionId);
//        if(true) return;
        Leg[] legs = controller.getLegs();

        for (Leg leg : legs) {
            try {
                if (leg.dialog.isServer()) {
                    leg.sendByeOrSpecificCodeResp(respCode, warningMessage);
                } else {
                    leg.sendByeOrCancel();
                }
            } catch (Exception e) {
                logger.error("[{}] cleanControllerWithSpecificCode exception", controller.sessionId, e);
            }
        }
    }


    public ToHeader createToHeader(String user, String ipport) throws Exception {
        SipURI toUri = addressFactory.createSipURI(user, ipport);
        Address toAddress = addressFactory.createAddress(toUri);
        return headerFactory.createToHeader(toAddress, null);
    }


    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        logger.info("thread={}: {}", Thread.currentThread().getId(), "processTimeout");
        if (timeoutEvent.isServerTransaction()) {
            logger.info("thread={}: {}", Thread.currentThread().getId(), timeoutEvent.getServerTransaction().getRequest());
        } else {
            logger.info("thread={}: {}", Thread.currentThread().getId(), timeoutEvent.getClientTransaction().getRequest());
        }
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        logger.info("thread={}: {}", Thread.currentThread().getId(), "processIOException");
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        //todo: 事务过期时，还能拿到dialog吗
        if (transactionTerminatedEvent.isServerTransaction()) {
            ServerTransaction serverTransaction = transactionTerminatedEvent.getServerTransaction();
            try {
//                logger.info("thread={}: ServerTransaction(request={})", Thread.currentThread().getId(), serverTransaction.getRequest().getMethod());
            } catch (Exception e) {
//                logger.info("thread={}: ServerTransaction(request=null)", Thread.currentThread().getId());
            }
        } else {
            ClientTransaction clientTransaction = transactionTerminatedEvent.getClientTransaction();
            try {
//                logger.info("thread={}: ClientTransaction(request={})", Thread.currentThread().getId(), clientTransaction.getRequest().getMethod());
            } catch (Exception e) {
//                logger.info("thread={}: ClientTransaction(request=null)", Thread.currentThread().getId());
            }
        }
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        Slf4jMDCService.initMDCCustomerCallID();
        String sessionid = "";
        /**
         * dialog的销毁时机：
         * 1、收到cancel/bye时，回200-ok后触发processDialogTerminated； 不回200-ok则需等待timeout？;
         * 2、收到invite-487时, transation和dialog都还在，
         * sitstack收到invite-487时，会立即触发processDialogTerminated, 随后触发engine.processResponse, responseEvent中仍然有dialog对象；
         * 收到其他invite的finalResponse会怎么样？
         * 发完bye后，会立即触发processDialogTerminated，但dialog对象不会被立即销毁， 等收到对端的200-bye时，responseEvent中仍然有dialog，内含有leg；
         * **/
        try {
            //对Dialog对象的销毁需注意多线程同步问题
            Dialog dialog = dialogTerminatedEvent.getDialog();
            if (dialog == null) {
                return;
            }

            Leg leg = (Leg) dialog.getApplicationData();
            if (leg == null) {
                return;
            }
            leg.terminated();

            Controller controller = leg.controller;
            if (controller == null) {
                return;
            }
            sessionid = controller.sessionId;
            Slf4jMDCService.putMDCCustomerCallID(sessionid);
            // controller.unbindLeg为线程安全
            controller.unbindLeg(leg);
            //logger.info("[{}] dialog terminate event. unbind leg is left:{}",sessionid,leg.isLeft());
            if (controller.canTerminate && !controller.hasSendDialogTemrinate.getAndSet(true)) {
                controller.stateAbnormalCallInfo();
                controller.recordAndTerminated(dialog);
                controller.doAfterTerminate();
                controller.getAbnormalBillAnalyser().analyse(null, AbnormalBillAnalyser.Type.TERMINATE, System.currentTimeMillis());
                AnalyseLog.logMsg(null, controller, null, AnalyseLog.MessageTypeEnum.TERMINATE);
            }
            if (controller.sizeOfLegs() == 0) {
                // 诡异：同一个dialog可能会触发2次processDialogTerminated
                // 比如： 左腿收到invite后， AS马上回487、或5XX、6XX，会触发2次。 是因为还没回183， 这时DialogState=null ？
                ControllerManager.getInstance().removeController(sessionid);
                //logger.info("[{}] controller is removed from controller manager",sessionid);
//                Controller c = ControllerManager.getInstance().removeController(controller.sessionid);
//                if (c != null) {
//                    controller.terminated();
//                }
            }
//            logger.info("#### thread={}： processDialogTerminated({})", Thread.currentThread().getId(), leg.toString());
        } catch (Exception e) {
            logger.error("[{}], processDialogTerminated error", sessionid, e);
        }

    }
}
