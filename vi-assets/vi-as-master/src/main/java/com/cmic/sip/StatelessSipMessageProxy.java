package com.cmic.sip;

import com.cmic.sip.engine.DtmfEngine;
import com.cmic.sip.engine.SipEngine;
import com.cmic.util.DtmfLogger;
import com.cmic.util.PhoneNumUtil;
import com.cmic.util.SipMessageSendUtil;
import com.cmic.util.SipUtil;
import gov.nist.javax.sip.ListeningPointImpl;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.NettyUDPMessageProxyChannel;
import gov.nist.javax.sip.stack.SipMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.TelURL;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.ViaHeader;

/**
 * 无状态代理类
 * <p>
 * Author: lyh
 * Date: 2026/6/08
 * <p>
 * 本代理类只代理初始invite，只有初始invite及相应的响应消息会经过此代理类的处理。
 */
public class StatelessSipMessageProxy {
    private static final Logger logger = LoggerFactory.getLogger(StatelessSipMessageProxy.class);
    public static volatile SipEngine engine;
    public static ProxyStatelessSipMessageProcess proxyStatelessSipMessageProcess = new ProxyStatelessSipMessageProcess();

    public static class ProxyStatelessSipMessageProcess implements SipMessageProcessor {
        private final Logger logger = LoggerFactory.getLogger(ProxyStatelessSipMessageProcess.class);
        @Override
        public void processSipRequest(SIPRequest sipRequest, NettyUDPMessageProxyChannel channel) {
            CallIdHeader callId = sipRequest.getCallId();
            logger.info("[{}], processSipRequest coming, sipRequest:{}", callId, sipRequest);
            try {
                String outputTid = sipRequest.getTopmostVia().getBranch().toLowerCase() + "_cmic";
                ListeningPointImpl lp = (ListeningPointImpl) engine.sipProvider.getListeningPoint("udp");
                // 都需要添加本AS的via头域
                ViaHeader viaHeader = engine.headerFactory.createViaHeader(lp.getIPAddress(), lp.getPort(), "udp", outputTid);
                sipRequest.addFirst(viaHeader);
                // 初始INVITE统一都添加RecordRoute
                if (SIPRequest.INVITE.equals(sipRequest.getMethod())) {
                    Address recordRouteAddress = new AddressImpl();
                    SipUri sipUri = new SipUri();
                    sipUri.setHost(lp.getIPAddress());
                    sipUri.setPort(lp.getPort());
                    sipUri.setLrParam();
                    recordRouteAddress.setURI(sipUri);
                    RecordRouteHeader recordRoute = DtmfEngine.getInstance().headerFactory.createRecordRouteHeader(recordRouteAddress);
                    sipRequest.addFirst(recordRoute);
                }
                // 如果是700路由则为I-CSCF触发业务，否则为S-CSCF触发业务
                String calleeNum = SipUtil.getCalleeNum(sipRequest);
                if(calleeNum.startsWith("700")) {
                    // 70014前缀路由进来，被叫号码需删除70014前缀出局，主叫号码和号显无需任何变更
                    SipUtil.setNextRoute4Dcc(sipRequest, sipRequest, DtmfEngine.getInstance());
                    String telB = "tel:+86" + PhoneNumUtil.getPure11NumberWithout0or86(calleeNum);
                    TelURL requestURI = (TelURL) DtmfEngine.getInstance().addressFactory.createURI(telB);
                    sipRequest.setRequestURI(requestURI);
                    String mode = "0";
                    // 先判断原来To号码的形式
                    String originTo = SipUtil.GetPhoneNumber(sipRequest.getTo().getAddress().getURI());
                    if(originTo.startsWith("700")) {
                        mode = "1";
                    }
                    String newCallId = callId.getCallId() + "-70014-" + mode;
                    sipRequest.setCallId(newCallId);
                    sipRequest.setHeader(engine.headerFactory.createHeader("To", telB));
                } else {
                    // 工作号签约进来，无需更改被叫信息，无需更改主叫和号显信息
                    SipUtil.setNextRoute4Gzh(sipRequest, sipRequest, DtmfEngine.getInstance());
                    URI requestURI = sipRequest.getRequestURI();
                    sipRequest.setRequestURI(requestURI);
                }
                // RouteList routeHeaders = sipRequest.getRouteHeaders();
                // if (routeHeaders != null) {
                //     routeHeaders.removeFirst();
                // }
                logger.info("[{}], proxy sendRequest :{}", callId, sipRequest);
                SipMessageSendUtil.sendSipMessageWithoutDialog(engine.sipProvider, sipRequest);
            } catch (Exception e) {
                logger.error("[{}], proxy processSipRequest error", callId, e);
            }
        }

        /**
         * 判断Route是否指向本AS（监听地址），用于无xsipproxy场景下剥离上游写入的self-Route。
         */
        private boolean isSelfRoute(Route route, ListeningPointImpl lp) {
            try {
                URI uri = route.getAddress().getURI();
                if (uri instanceof SipURI) {
                    return ((SipURI) uri).getHost().contains(lp.getIPAddress());
                }
            } catch (Exception e) {
                logger.error("isSelfRoute error", e);
            }
            return false;
        }

        private String[] getCallIdParts(String callId) {
            // 如果收到的消息callId符合指定的格式，则它一定是从右边过来的
            String[] callIdParts = callId.split("-");
            if(callIdParts.length > 2 && callIdParts[callIdParts.length-2].equals("70014")) {
                // 原来请求的格式
                String mode = callIdParts[callIdParts.length-1];
                return new String[]{"70014", mode};
            }else{
                return null;
            }
        }

        @Override
        public void processSipResponse(SIPResponse sipResponse, NettyUDPMessageProxyChannel channel) {
            CallIdHeader callId = sipResponse.getCallId();
            logger.info("[{}], processSipResponse coming, sipResponse:{}", callId, sipResponse);
            try {
                // 如果收到的消息callId符合指定的格式，则它一定是从右边过来的
                String[] callIdParts = callId.getCallId().split("-");
                if(callIdParts.length > 2 && callIdParts[callIdParts.length-2].equals("70014")) {
                    // 原来请求的格式
                    String mode = callIdParts[callIdParts.length-1];
                    if(mode.equals("1")) {
                        String toNumber = SipUtil.GetPhoneNumber(sipResponse.getTo().getAddress().getURI());
                        toNumber = toNumber + "70014";
                        sipResponse.setHeader(engine.headerFactory.createHeader("To", toNumber));
                    }
                }
                SIPResponse newResponse = (SIPResponse) sipResponse.clone();
                newResponse.removeFirst(ViaHeader.NAME);
                logger.info("[{}], proxy sendResponse :{}", callId, sipResponse);
                SipMessageSendUtil.sendSipMessageWithoutDialog(engine.sipProvider, newResponse);
            } catch (Exception e) {
                logger.error("[{}], proxy processSipResponse error", callId, e);
            }
        }
    }

}
