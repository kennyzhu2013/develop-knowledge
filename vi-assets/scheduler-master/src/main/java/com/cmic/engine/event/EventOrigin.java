package com.cmic.engine.event;

public enum EventOrigin {
    MQ("mq"),
    HTTP("http"),
    ;
    private String origin;

    EventOrigin(String origin) {
        this.origin = origin;
    }

    public String getOrigin() {
        return origin;
    }
}
