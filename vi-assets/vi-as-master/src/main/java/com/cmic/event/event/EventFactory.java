package com.cmic.event.event;

public class EventFactory {
    private static EventFactory instance = new EventFactory();

    public static EventFactory getInstance() {
        return instance;
    }


}
