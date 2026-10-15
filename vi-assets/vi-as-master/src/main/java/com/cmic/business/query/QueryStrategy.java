package com.cmic.business.query;

import com.cmic.Java11HttpClient;
import com.cmic.business.BusinessEvent;
import com.cmic.business.ViConf;
import com.cmic.business.businessparam.CallParameter;
import net.sf.json.JSONObject;

public interface QueryStrategy {
    void callQuery(BusinessEvent businessEvent, String requestId, String callNo,
                   String secretNo, String callTime,
                   String callId, ViConf viConf, CallParameter callParameter, Java11HttpClient.httpCallback<JSONObject> action);

    void fireRetransCallQuery(CallParameter callParameter) throws Exception;
}
