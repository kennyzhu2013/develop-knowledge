package com.cmic.sip.event;

import net.sf.json.JSONObject;

public class HttpRequestEvent {
    public String sessionId;
    public String cmd;
    public JSONObject params;

    public HttpRequestEvent(String sessionId, String cmd, JSONObject params) {
        this.sessionId = sessionId;
        this.cmd = cmd;
        this.params = params;
    }
}