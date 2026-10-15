package com.cmic.engine.event;

import org.springframework.context.ApplicationEvent;

public class HttpEvent extends ApplicationEvent {
    public static final String START = "start";
    public static final String END = "end";
    public static final String HTTP_EVENT = "http_event";

    public HttpEvent(Object source) {
        super(source);
    }

    private String processId;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }
}
