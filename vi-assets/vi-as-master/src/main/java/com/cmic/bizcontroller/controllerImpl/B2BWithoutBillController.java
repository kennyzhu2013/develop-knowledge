package com.cmic.bizcontroller.controllerImpl;

import com.cmic.sip.B2buaHelper;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.sip.engine.SipEngine;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.LeftLeg;
import com.cmic.sip.leg.Leg;
import com.cmic.sip.leg.RightLeg;
import com.cmic.util.DtmfLogger;
import com.cmic.util.PhoneNumUtil;
import com.cmic.util.SipHeaderHelper;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.Address;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;

/**
 * @author
 */
public class B2BWithoutBillController extends TwoLegController {

    public static final String GZH_SECOND_CALL_PREFIX = "4444";

    private Logger logger = LoggerFactory.getLogger(getClass());

    public B2BWithoutBillController(String sessionid, SipEngine engine) {
        super(sessionid, engine);
        this.isNeedCacheSingallingLog = false;
    }

    @Override
    public void start(SIPRequest initialRequest) throws Exception {
        logger.info("[{}] B2BWithoutBillController start.", sessionId);
        // 初始化左腿
        LeftLeg leftLeg = this.engine.createLeftLeg(initialRequest);
        bindLeftLeg(leftLeg);

        SIPRequest request = B2buaHelper.createInviteRequest(this.engine, initialRequest);
        B2buaHelper.linkRequest(initialRequest, request);
        String calleeNum = SipUtil.getCalleeNum(initialRequest);
        // 如果是700路由则为I-CSCF触发业务，否则为S-CSCF触发业务
        if(calleeNum.startsWith("700")) {
            // 70014前缀路由进来，被叫号码需删除70014前缀出局，主叫号码和号显无需任何变更
            SipUtil.setNextRoute4Dcc(request, initialRequest, this.engine);
            // 去掉前缀
            String telB = PhoneNumUtil.getPure11NumberWithout0or86(calleeNum).substring(5);
            telB = adaptTelB(telB);
            TelURL requestURI = (TelURL) this.engine.addressFactory.createURI(telB);
            request.setRequestURI(requestURI);
            request.setHeader(engine.headerFactory.createHeader("To", telB));
        } else {
            // 工作号签约进来，无需更改被叫信息，无需更改主叫和号显信息
            SipUtil.setNextRoute4Gzh(request, initialRequest, this.engine);
            URI requestURI = initialRequest.getRequestURI();
            request.setRequestURI(requestURI);
        }

        // 消息路由
        // final Address s_cscf = ((Route) initialRequest.getRouteHeaders().getLast()).getAddress();
        // request.setHeader(this.engine.headerFactory.createRouteHeader(s_cscf));
        String callerNum = SipUtil.getCallerNum(initialRequest);
        if(callerNum.endsWith(GZH_SECOND_CALL_PREFIX)){
            logger.info("callId:{}, callerNum:{} ends with 4444",request.getCallId().getCallId(),callerNum);
        }
        // todo 这个待确认是否需要保留
        // if (callerNum.endsWith(GZH_SECOND_CALL_PREFIX) && callerNum.length() != 11) {
        //     SipUtil.changeDisplayNum(engine, request, callerNum, callerNum.substring(0, callerNum.length() - 4), "", null);
        // }
        if (SipHeaderHelper.ifSupported(initialRequest)) {
            request.removeHeader(ContactHeader.NAME);
            request.setHeader(this.engine.contactHeaderWithVideo);
        }
        RightLeg rightLeg = this.engine.createRightLeg(request);
        bindRightLeg(rightLeg);
        rightLeg.sendInitialInvite();
    }

    @Override
    public void processResponse(Leg leg, SipResponseEvent responseEvent) throws Exception {
        Leg oppositeLeg = getOppositeLeg(leg);
        oppositeLeg.forwardResponse(responseEvent, false);
    }

    @Override
    public void processRequest(Leg leg, SipRequestEvent requestEvent) throws Exception {
        SIPRequest request = requestEvent.getRequest();
        Leg oppositeLeg = getOppositeLeg(leg);
        oppositeLeg.forwardRequest(request, e -> {
            if (SipHeaderHelper.ifSupported(request)) {
                e.removeHeader(ContactHeader.NAME);
                e.setHeader(this.engine.contactHeaderWithVideo);
            }
        }, false);
    }

    @Override
    public void terminated() throws Exception {
        logger.info("sessionId={} thread={} {}.terminated", sessionId, Thread.currentThread().getId(), this.getClass().getName());
    }

    /**
     * 对被叫号码进行出网规整
     *
     * @param calleeNum 被叫号码
     * @return 规整后的被叫号码
     */
    protected String adaptTelB(String calleeNum) {
        //return TelBRegulator.getInstance().regulate(calleeNum, authNum);
        return "tel:+86" + PhoneNumUtil.getPure11NumberWithout0or86(calleeNum);
    }

}
