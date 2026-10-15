package com.cmic.sip.controller.base;

import com.cmic.recording.MediaSession;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.util.AbnormalBillAnalyser;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.CSeqHeader;
import javax.sip.message.Request;

/**
 * 双腿流程控制器基类： 封装双腿状态判断、信令透传、自动化拆线、通话录音
 **/
public abstract class TwoLegController extends Controller {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    public LeftLeg leftLeg;
    public RightLeg rightLeg;

    //public volatile int dbAndExecutorIndex = -1;
    //public volatile boolean isInsertDbSucc = false;

    public TwoLegController(String sessionId, SipEngine engine) {
        super(sessionId, engine);
    }

    /**
     * 双向绑定 Leg & Controller
     **/
    public void bindLeftLeg(LeftLeg left) {
        this.leftLeg = left;
        this.bindLeg(left);
        Request firstRequest = left.firstInviteTransation.getRequest();
        if (firstRequest instanceof SIPRequest) {
            this.getAbnormalBillAnalyser().analyse((SIPRequest) firstRequest, AbnormalBillAnalyser.Type.FIRST_INVITE,
                    System.currentTimeMillis());
        }
    }

    public void bindRightLeg(RightLeg right) {
        this.rightLeg = right;
        this.bindLeg(right);
    }

    public LeftLeg getLeftLeg() {
        return this.leftLeg;
    }

    public RightLeg getRightLeg() {
        return this.rightLeg;
    }


    /**
     * 获取另一条腿
     **/
    public final Leg getOppositeLeg(Leg leg) {
        if (leg == this.getLeftLeg()) {
            return this.getRightLeg();
        } else {
            return this.getLeftLeg();
        }
    }

    public void doProcessResponse(Leg leg, SipResponseEvent responseEvent, MediaSession recordSession) throws Exception {
        SIPResponse response = responseEvent.getResponse();
        CSeqHeader cseqHeader = (CSeqHeader) response.getHeader("CSeq");
        String method = cseqHeader.getMethod();
        Leg oppositeLeg = getOppositeLeg(leg);

        if (method.equalsIgnoreCase(Request.INVITE) && response.getStatusCode() == 200) {
            oppositeLeg.forwardResponse(responseEvent,false);
            recordSession.arriveInv200();
            return;
        }

        if (response.getStatusCode() == 180) {
            oppositeLeg.forwardResponse(responseEvent, message -> {
                message.setHeader(this.engine.headerFactory.createHeader("P-Early-Media", "sendrecv"));
            },false);
//            recordSession.playearly();
            return;
        }

        if (response.getContent() != null) {
            oppositeLeg.forwardResponse(responseEvent, message -> {
                byte[] sdp = message.getRawContent();
                if (leg.isLeft()) {
                    message.setContent(recordSession.processLeftSDPV3(message), this.engine.contentTypeHeader);
                    recordSession.commit();
                } else {
                    message.setContent(recordSession.processRightSDPV3(message), this.engine.contentTypeHeader);
                    recordSession.commit();
                }
            },false);
        } else {
            oppositeLeg.forwardResponse(responseEvent,false);
        }

        if (response.getStatusCode() == 487) {
            logger.info("============================================");
            logger.info("dialog={}", responseEvent.getDialog());

        }
    }

    public void doProcessRequest(Leg leg, SipRequestEvent requestEvent, MediaSession recordSession) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        String method = request.getMethod();
        Leg oppositeLeg = getOppositeLeg(leg);

        // 如果cancel到来时，右腿还没创建完毕
//        if (oppositeLeg == null) return;

        if (method.equals(Request.INVITE)) {
            oppositeLeg.forwardRequest(request,false);
        } else if (method.equals(Request.CANCEL)) {
            oppositeLeg.forwardRequest(request,false);
        } else if (method.equals(Request.PRACK)) {
            oppositeLeg.forwardRequest(request,false);
        } else if (method.equals(Request.UPDATE)) {
            if (request.getContent() != null) {
                oppositeLeg.forwardRequest(request, message -> {
                    byte[] sdp = message.getRawContent();
                    if (leg.isLeft()) {
                        message.setContent(recordSession.processLeftSDPV3(message), this.engine.contentTypeHeader);
                        recordSession.commit();
                    } else {
                        message.setContent(recordSession.processRightSDPV3(message), this.engine.contentTypeHeader);
                        recordSession.commit();
                    }
                },false);
            } else {
                oppositeLeg.forwardRequest(request,false);
            }
        } else if (method.equals(Request.ACK)) {
            oppositeLeg.forwardRequest(request,false);
        } else if (method.equals(Request.BYE)) {
            oppositeLeg.forwardRequest(request,false);
        }
    }


    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
//        this.doProcessResponse(leg, responseEvent, null);
    }


    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
//        this.doProcessRequest(leg, requestEvent, null);
    }

    @Override
    public  void onSystemExitEvent() throws Exception{

    }

    @Override
    public  void onCleanOutEvent() throws Exception{

    }

}
