package com.cmic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SimpleApplicationEventMulticaster;

@Configuration
public class EventConfig {

    @Bean(name = "applicationEventMulticaster")
    public SimpleApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(null);
        return eventMulticaster;
    }
}
