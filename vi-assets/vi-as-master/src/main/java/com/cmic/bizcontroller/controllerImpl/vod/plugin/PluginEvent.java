package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.bizcontroller.controllerImpl.vod.SipMessageDesc;
import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;
import gov.nist.javax.sip.message.SIPMessage;

import java.util.Map;

/**
 * Author: luohuanfa
 * Date: 2022/9/28
 */
public class PluginEvent {
    // type: sip, http, and so on
    private EventTypeEnum type;
    private SipRequestEvent requestEvent;
    private SipResponseEvent responseEvent;
    private SipMessageDesc sipMessageDesc;
    private Map<String, Object> mapObject;
    private Leg leg;

    public static PluginEvent getSipRequestEventInstance(Leg leg, SipRequestEvent requestEvent, SipMessageDesc sipMessageDesc) {
        return new PluginEvent(leg, EventTypeEnum.SIP_REQUEST, requestEvent, sipMessageDesc);
    }

    public static PluginEvent getSipResponseEventInstance(Leg leg, SipResponseEvent responseEvent, SipMessageDesc sipMessageDesc) {
        return new PluginEvent(leg, EventTypeEnum.SIP_RESPONSE, responseEvent, sipMessageDesc);
    }

    public static PluginEvent getHttpEventInstance(EventTypeEnum type, Map<String, Object> mapObject) {
        return new PluginEvent(type, mapObject);
    }

    private PluginEvent(Leg leg, EventTypeEnum type, SipRequestEvent requestEvent, SipMessageDesc sipMessageDesc) {
        this.leg = leg;
        this.type = type;
        this.requestEvent = requestEvent;
        this.sipMessageDesc = sipMessageDesc;
    }

    private PluginEvent(Leg leg, EventTypeEnum type, SipResponseEvent responseEvent, SipMessageDesc sipMessageDesc) {
        this.leg = leg;
        this.type = type;
        this.responseEvent = responseEvent;
        this.sipMessageDesc = sipMessageDesc;
    }

    private PluginEvent(EventTypeEnum type, Map<String, Object> mapObject) {
        this.type = type;
        this.mapObject = mapObject;
    }

    public EventTypeEnum getType() {
        return type;
    }

    public Map<String, Object> getMapObject() {
        return mapObject;
    }

    public SipRequestEvent getRequestEvent() {
        return requestEvent;
    }

    public SipResponseEvent getResponseEvent() {
        return responseEvent;
    }

    public SIPMessage getSipMessage() {
        if (type == null) {
            return null;
        }
        if (type.equals(EventTypeEnum.SIP_REQUEST)) {
            return requestEvent.getRequest();
        }
        if (type.equals(EventTypeEnum.SIP_RESPONSE)) {
            return responseEvent.getResponse();
        }
        return null;
    }

    public SipMessageDesc getSipMessageDesc() {
        return sipMessageDesc;
    }

    public Leg getLeg() {
        return leg;
    }

    public enum EventTypeEnum {
        /**
         * Sip请求，SipRequest
         */
        SIP_REQUEST {
            @Override
            public PluginEvent wrapMapToEvent(Map<String, Object> httpData) {
                throw new UnsupportedOperationException("unsupport sip in http interface");
            }
        },
        /**
         * Sip响应，SipResponse
         */
        SIP_RESPONSE {
            @Override
            public PluginEvent wrapMapToEvent(Map<String, Object> httpData) {
                throw new UnsupportedOperationException("unsupport sip in http interface");
            }
        },
        /**
         * 视频插播调起
         */
        VOD {
            @Override
            public PluginEvent wrapMapToEvent(Map<String, Object> httpData) {
                return new PluginEvent(VOD, httpData);
            }
        },
        /**
         * 视频插播回落
         */
        VODRE {
            @Override
            public PluginEvent wrapMapToEvent(Map<String, Object> httpData) {
                return new PluginEvent(VODRE, httpData);
            }
        },
        /**
         * 视频插播播放完成
         */
        VODCOMPLETE {
            @Override
            public PluginEvent wrapMapToEvent(Map<String, Object> httpData) {
                return new PluginEvent(VODCOMPLETE, httpData);
            }
        };


        /**
         * 复杂的转换放在这，如果只是直接包装，建议直接使用前面的getHttpEventInstance方法
         *
         * @param httpData
         * @return
         */
        public abstract PluginEvent wrapMapToEvent(Map<String, Object> httpData);

        EventTypeEnum() {

        }
    }

}
