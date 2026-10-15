package com.cmic.bizcontroller.controllerImpl.vod;

import java.util.*;


/**
 * sip消息的描述体
 */
public class SipMessageDesc {
    private SendOrRecv sendOrRecv;
    private RequestOrResponse requestOrResponse;
    private String method;
    private List<Integer> stateCodeList;
    private List<Integer> abbrevStateList;

    private static final Map<String, SipMessageDesc> cacheRequestSMPSet = new HashMap<>();
    private static final Map<String, SipMessageDesc> cacheResponseSMPSet = new HashMap<>();

    private SipMessageDesc() {
    }

    public static SipMessageDesc getSipRequestInstance(SendOrRecv isRecv, String method) {
        String cacheKey = isRecv.name() + method;
        SipMessageDesc cacheSMP = cacheRequestSMPSet.get(cacheKey);
        if (cacheSMP != null) {
            return cacheSMP;
        }
        SipMessageDesc SipMessageDesc = new SipMessageDesc();
        SipMessageDesc.sendOrRecv = isRecv;
        SipMessageDesc.requestOrResponse = RequestOrResponse.REQUEST;
        SipMessageDesc.method = method;
        cacheRequestSMPSet.put(cacheKey, SipMessageDesc);
        return SipMessageDesc;
    }

    public static SipMessageDesc getSipResponseInstance(SendOrRecv isRecv, String method, Integer stateCode, Integer... stateCodeArray) {
        List<Integer> stateCodeTempList = new ArrayList<>(3);
        List<Integer> abbrevStateTempList = new ArrayList<>();
        addStateCode(stateCode, stateCodeTempList, abbrevStateTempList);
        if (stateCodeArray != null && stateCodeArray.length != 0) {
            Arrays.stream(stateCodeArray).forEach(code -> addStateCode(code, stateCodeTempList, abbrevStateTempList));
        }
        String cacheKey = isRecv.name() + method + stateCodeTempList + abbrevStateTempList;
        SipMessageDesc cacheSMP = cacheResponseSMPSet.get(cacheKey);
        if (cacheSMP != null) {
            return cacheSMP;
        }
        SipMessageDesc sipMessageDesc = new SipMessageDesc();
        sipMessageDesc.sendOrRecv = isRecv;
        sipMessageDesc.requestOrResponse = RequestOrResponse.RESPONSE;
        sipMessageDesc.method = method;
        sipMessageDesc.stateCodeList = stateCodeTempList;
        sipMessageDesc.abbrevStateList = abbrevStateTempList;
        cacheResponseSMPSet.put(cacheKey, sipMessageDesc);
        return sipMessageDesc;
    }

    private static void addStateCode(Integer stateCode, List<Integer> stateCodeTempList, List<Integer> abbrevStateTempList) {
        if (stateCode < 10) {
            abbrevStateTempList.add(stateCode);
            return;
        }
        stateCodeTempList.add(stateCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SipMessageDesc that = (SipMessageDesc) o;
        return getSendOrRecv() == that.getSendOrRecv() && getRequestOrResponse() == that.getRequestOrResponse() && Objects.equals(getMethod(), that.getMethod()) && Objects.equals(stateCodeList, that.stateCodeList);
    }


    public boolean partOf(SipMessageDesc desc) {
        if (desc == null || getClass() != desc.getClass()) {
            return false;
        }
        if (getSendOrRecv() != desc.getSendOrRecv()
                || getRequestOrResponse() != desc.getRequestOrResponse()
                || !Objects.equals(getMethod(), desc.getMethod())) {
            return false;
        }
        List<Integer> descState = desc.getStateCodeList();
        List<Integer> descAbbrev = desc.getAbbrevStateList();
        List<Integer> sourceState = getStateCodeList();
        List<Integer> sourceAbbrev = getAbbrevStateList();
        return descState.containsAll(sourceState) && descAbbrev.containsAll(sourceAbbrev);
    }


    public enum SendOrRecv {
        /**
         * 发送
         */
        SEND,
        /**
         * 接收
         */
        RECV,
    }

    public enum RequestOrResponse {
        /**
         * 请求
         */
        REQUEST,
        /**
         * 响应
         */
        RESPONSE;

        RequestOrResponse() {

        }
    }


    public SendOrRecv getSendOrRecv() {
        return sendOrRecv;
    }

    public String getMethod() {
        return method;
    }

    public List<Integer> getStateCodeList() {
        return stateCodeList;
    }

    public Integer getFirstStateCode() {
        return stateCodeList.get(0);
    }

    public List<Integer> getAbbrevStateList() {
        return abbrevStateList;
    }

    public RequestOrResponse getRequestOrResponse() {
        return requestOrResponse;
    }


    @Override
    public String toString() {
        return "SipMessageDesc{" +
                "sendOrRecv=" + sendOrRecv +
                ", requestOrResponse=" + requestOrResponse +
                ", method='" + method + '\'' +
                ", stateCodeList=" + stateCodeList +
                '}';
    }
}