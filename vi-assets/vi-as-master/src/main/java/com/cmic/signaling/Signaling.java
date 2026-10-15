package com.cmic.signaling;


import com.cmic.sip.leg.Leg;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import gov.nist.core.SignalLogger;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.NettyUDPMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class Signaling implements SignalLogger {

    public static final Logger logger = LoggerFactory.getLogger("aslog");
    private static final Signaling instance = new Signaling();
    /*public static void logMsg(String sessionId,String content)
    {
        logger.debug("[{}],[signalling] \n{}",sessionId,content);
    }*/

    public static final int INITIAL = 0;
    public static final int LEFT_TO_AS = 1;
    public static final int AS_TO_RIGHT = 2;
    public static final int RIGHT_TO_AS = 3;
    public static final int AS_TO_LEFT = 4;

    public static final int REQUEST = 1;

    public static final int RESPONSE = 2;

    public static final int SEND = 2;
    public static final int RECV = 1;
    public static final int DEFAULT_RECV = 5;
    public static final int DEFAULT_SEND = 6;

    public static Cache<String, Map<String, Object>> signalCache = CacheBuilder.newBuilder().maximumSize(2000000).expireAfterAccess(1200, TimeUnit.SECONDS).concurrencyLevel(48).build();

    private Signaling(){
        NettyUDPMessageChannel.signalLogger = this;
    }


    public static int getDirection(Leg leg, int sendOrRecv) {
        if (leg.isLeft()) {
            if (sendOrRecv == 1) {
                return Signaling.LEFT_TO_AS;
            } else {
                return Signaling.AS_TO_LEFT;
            }
        } else {
            if (sendOrRecv == 1) {
                return Signaling.RIGHT_TO_AS;
            } else {
                return Signaling.AS_TO_RIGHT;
            }
        }

    }

    public static Signaling getLogInstance() {
        return instance;
    }

    @Override
    public Map<String, Object> getSignalCache(String callId, SIPMessage sipMessage, boolean isRecv) {
        Map<String, Object> cacheItem = signalCache.getIfPresent(callId);
        if (cacheItem == null) {
            return null;
        }
        cacheItem.put("direction", isRecv ? DEFAULT_RECV : DEFAULT_SEND);
        int requestOrResponse = sipMessage instanceof SIPRequest ? REQUEST : RESPONSE;
        cacheItem.put("requestOrResponse", requestOrResponse);
        cacheItem.put("content", sipMessage);
        return cacheItem;
    }

    @Override
    public void logSipMessage(Map<String, Object> sipMessageContainer) {
        String billId = (String) sipMessageContainer.get("sessionId");
        Integer companyId = (Integer) sipMessageContainer.get("companyId");
        String appId = (String) sipMessageContainer.get("appId");
        Integer provinceId = (Integer) sipMessageContainer.get("provinceId");
        Integer cityId = (Integer) sipMessageContainer.get("cityId");
        Integer direction = (Integer) sipMessageContainer.get("direction");
        Integer requestOrResponse = (Integer) sipMessageContainer.get("requestOrResponse");
        SIPMessage content = (SIPMessage) sipMessageContainer.get("content");
        logSipMessage(billId, companyId, appId, provinceId, cityId, direction, requestOrResponse, content);
    }

    private void logSipMessage(String billId, Integer companyId, String appId, Integer provinceId, Integer cityId,
                               Integer direction, Integer requestOrResponse, SIPMessage content) {
        String callId = content.getCallId().getCallId();
        if (signalCache.getIfPresent(callId) == null){
            Map<String, Object> cacheItem = new HashMap<>();
            cacheItem.put("sessionId", billId);
            cacheItem.put("companyId", companyId);
            cacheItem.put("appId", appId);
            cacheItem.put("provinceId", provinceId);
            cacheItem.put("cityId", cityId);
            signalCache.put(callId, cacheItem);
        }
        logger.debug("[{}][{}][{}][{}][{}][{}][{}][signalling] \n{}", billId, companyId, appId,
                provinceId, cityId, direction, requestOrResponse, content);
    }

    public static void log(SignalParam signalParam, int direction, int requestOrResponse) {
        getLogInstance().logSipMessage(signalParam.getSessionId(), signalParam.getCompanyId(), signalParam.getAppId(),
                signalParam.getProvinceId(), signalParam.getCityId(), direction, requestOrResponse, signalParam.getContent());
    }
}
