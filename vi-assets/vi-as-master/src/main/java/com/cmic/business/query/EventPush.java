package com.cmic.business.query;

import com.cmic.Java11HttpClient;
import com.cmic.business.BusinessEvent;
import com.cmic.service.MyRegistryService;
import com.cmic.sip.leg.Hook;
import com.cmic.util.DtmfLogger;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class EventPush {

    public static int DEFAULT_MAX_PUSH_RETRY_COUNT = 1;
    Hook callQuery = null;
    private int retryCount = 0;

    public void pushToClient(BusinessEvent businessEvent, JSONObject eventJson, String url, Java11HttpClient.httpCallback<JSONObject> action) {
        // String url = businessEvent.getAuthConfig().getUrlVideoInsertStatus();
        // 区分短信url
        if (StringUtils.isNotBlank(url)) {
            String[] queryUrls = url.split(";");
            url = queryUrls[0];
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("zy-url", url);
        headers.put("zy-concurrent", "10");
        headers.put("zy-timeout", "10");
        // businessEvent.addRequestHeader(url.contains("https") ? "" : ((Object) eventJson).toString(), headers);
        String proxyUrl = MyRegistryService.getInstance().getHttpProxyHost() + "/httpproxy";
        String callId = String.valueOf(businessEvent.getBillNew().getId());
        DtmfLogger.info(callId, " pushToClient Authorization Header: {},proxyUrl:{},params:{} ",
                headers.toString(), proxyUrl, ((Object) eventJson).toString());
        // callQuery = b -> Java11HttpClient.postJson(callId, businessEvent.getDccAuthConf().getUserId(),
        //         businessEvent.getDccAuthConf().getAppId(), proxyUrl, headers, eventJson, action, businessEvent.getHttpVersion());

        try {
            callQuery.apply(null);
        } catch (Exception e) {
            DtmfLogger.error(String.valueOf(businessEvent.getBillNew().getId()), "pushToClient something error happen, callQuery fail ", e);
        }
    }

    public void pushEvent(BusinessEvent businessEvent, Object eventObj) {
        // 插播完成事件推送
        // pushEvent2Url(businessEvent, eventObj, businessEvent.getDccAuthConf().getVideoInsertCallbackUrl());
    }

    public void pushDtmfEvent(BusinessEvent businessEvent, Object eventObj, Java11HttpClient.httpCallback<JSONObject> eventRespAction) {
        // 收号事件上报
        // pushEvent2Url(businessEvent, eventObj, businessEvent.getVi().getDtmfCallbackUrl(), eventRespAction);
    }

    public void pushEvent2Url(BusinessEvent businessEvent, Object eventObj, String url) {
        pushEvent2Url(businessEvent, eventObj, url, null);
    }

    public void pushEvent2Url(BusinessEvent businessEvent, Object eventObj, String url, Java11HttpClient.httpCallback<JSONObject> eventRespAction) {
        // 将事件对象转为JSONObject对象
        JSONObject eventJson = JSONObject.fromObject(eventObj);
        String callId = String.valueOf(businessEvent.getBillNew().getId());
        if (StringUtils.isBlank(url)) {
            DtmfLogger.error(callId, " pushEvent2Url {}:{},error url is empty!", eventObj.getClass().getSimpleName(), eventJson);
            return;
        }
        pushToClient(businessEvent, eventJson, url, resp -> {
            int http_code = resp.getInt("http_code");
            DtmfLogger.info(callId, " pushEvent {}:{},pushEvent resp:{}", eventObj.getClass().getSimpleName(), eventJson, resp);
            if (http_code == 200) {
                if (eventRespAction != null) {
                    eventRespAction.apply(resp);
                }
                return;
            }
            if (retryCount < EventPush.DEFAULT_MAX_PUSH_RETRY_COUNT) {
                try {
                    callQuery.apply(null);
                    retryCount++;
                    DtmfLogger.info(callId, "retry to push vod event");
                } catch (Exception e) {
                    DtmfLogger.error(callId, "pushToClient something error happen, callQuery fail ", e);
                }
            }
        });
    }

}
