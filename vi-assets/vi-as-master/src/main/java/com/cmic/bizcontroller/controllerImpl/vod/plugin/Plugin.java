package com.cmic.bizcontroller.controllerImpl.vod.plugin;

import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;

public interface Plugin {
    /**
     * 接收事件处理，这个方法接收的必然是此plugin应该处理的
     * 返回处理结果，正常处理此事件返回true，否则返回false
     *
     * @param event plugin处理的事件
     * @return result boolean
     * @throws Exception
     */
    PluginEventResultVo accept(PluginEvent event) throws Exception;

    PluginEventResultVo doResponse(Leg leg, SipResponseEvent response) throws Exception;

    PluginEventResultVo doRequest(Leg leg, SipRequestEvent request) throws Exception;

    void processPluginTermination() throws Exception;
}
