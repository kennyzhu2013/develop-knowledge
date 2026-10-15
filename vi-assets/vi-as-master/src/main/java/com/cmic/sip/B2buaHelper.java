package com.cmic.sip;

import com.cmic.sip.engine.SipEngine;
import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.message.SIPRequest;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.Request;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

public class B2buaHelper {
    private static Logger logger = LoggerFactory.getLogger(B2buaHelper.class);

    /**
     * 根据协议栈提供的信息(callid, cseq, via, maxforwards, from)，创建初始invite模板消息，requestUri、to、route等字段需要后续手动补充
     **/
    public static SIPRequest createInviteRequest(SipEngine engine) throws Exception {
        // CallId header
        CallIdHeader callIdHeader = engine.sipProvider.getNewCallId();

        // Cseq header
        CSeqHeader cSeqHeader = engine.headerFactory.createCSeqHeader(1L, Request.INVITE);

        // via headers
        ArrayList viaHeaders = new ArrayList();
        ListeningPointImpl lp = (ListeningPointImpl) engine.sipProvider.getListeningPoint("udp");
        ViaHeader viaHeader = engine.headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), "udp", null);
        viaHeaders.add(viaHeader);

        // MaxForwards header
        MaxForwardsHeader maxForwardsHeader = engine.headerFactory.createMaxForwardsHeader(70);

        // default from
        SipURI fromAddress = engine.addressFactory.createSipURI("cmic", lp.getIPAddress() + ":" + lp.getPort());
        Address fromNameAddress = engine.addressFactory.createAddress(fromAddress);
        FromHeader fromHeader = engine.headerFactory.createFromHeader(fromNameAddress, "cmic");

        SIPRequest request = new SIPRequest();
        request.setMethod(Request.INVITE);
        request.setCallId(callIdHeader);
        request.setCSeq(cSeqHeader);
        request.setVia(viaHeaders);
        request.setMaxForwards(maxForwardsHeader);
        request.setHeader(engine.contactHeader);
        request.setFrom(fromHeader);
//        request.setTo(to);
        return request;
    }


    /**
     * 根据提供的originRequest，fork一个新invite
     */
    public static SIPRequest createInviteRequest(SipEngine engine, SIPRequest originRequest) throws Exception {
        SIPRequest request = createInviteRequest(engine);
        request.setFrom((FromHeader) originRequest.getFrom().clone());
        request.setTo((ToHeader) originRequest.getTo().clone());
        request.setRequestURI((URI) originRequest.getRequestURI().clone());
        // fill other header according to originRequest
        inflateMessage(request, originRequest);
        return request;
    }

    /**
     * b2bua模式下，将两腿的request进行双向绑定， 以便收到response时找到初始request
     * 回溯路径： responseEvent -> clientTransation -> forkedRequest -> originRequest
     **/
    public static void linkRequest(SIPRequest originRequest, SIPRequest forwardedRequest) {
        originRequest.setApplicationData(forwardedRequest);
        forwardedRequest.setApplicationData(originRequest);
    }

    /**
     * 参照消息（请求、响应）填充字段， keyHeaders列表中字段为关键字段，由sipstack自动产生或手动设置
     **/
    public static void inflateMessage(Message targetMsg, Message sourceMsg) throws ParseException {
        //Privacy (加了会导致号显显示私密密码，无法回拨)
        String[] keyHeaders = {"Via", "Call-ID", "CSeq", "RSeq", "RAck", "From", "To", "Route", "Record-Route", "Contact", "Max-Forwards","Privacy"};
        Iterator it = sourceMsg.getHeaderNames();
        while (it.hasNext()) {
            String headerName = (String) it.next();
            if (!ArrayUtils.contains(keyHeaders, headerName)) {
                targetMsg.removeHeader(headerName);
                ListIterator lit = sourceMsg.getHeaders(headerName);
                while (lit.hasNext()) {
                    targetMsg.addHeader((Header) lit.next());
                }
            }
        }
        // copy sdp
        if (sourceMsg.getContentLength().getContentLength() > 0) {
            targetMsg.setContent(sourceMsg.getRawContent(), (ContentTypeHeader) sourceMsg.getHeader(ContentTypeHeader.NAME));
        }
    }



    public static void inflateMessageWithoutContent(Message targetMsg, Message sourceMsg) throws ParseException {
        String[] keyHeaders = {"Via", "Call-ID", "CSeq", "RSeq", "RAck", "From", "To", "Route", "Record-Route", "Contact", "Max-Forwards","Privacy"};
        Iterator it = sourceMsg.getHeaderNames();
        while (it.hasNext()) {
            String headerName = (String) it.next();
            if (!ArrayUtils.contains(keyHeaders, headerName)) {
                targetMsg.removeHeader(headerName);
                ListIterator lit = sourceMsg.getHeaders(headerName);
                while (lit.hasNext()) {
                    targetMsg.addHeader((Header) lit.next());
                }
            }
        }
    }

    public static void inflateMessageWithSomeKeyHeader(Message targetMsg, Message sourceMsg,String []keyHeaders) throws Exception{
        Iterator it = sourceMsg.getHeaderNames();
        while (it.hasNext()) {
            String headerName = (String) it.next();
            if (ArrayUtils.contains(keyHeaders, headerName)) {
                targetMsg.removeHeader(headerName);
                ListIterator lit = sourceMsg.getHeaders(headerName);
                while (lit.hasNext()) {
                    targetMsg.addHeader((Header) lit.next());
                }
            }
        }
    }
}
