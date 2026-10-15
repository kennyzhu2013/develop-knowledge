package com.cmic.utils;


import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ShotDownHook {
    @PreDestroy
    public void destory() {
        log.info("Application shot down ..........................");
    }
}
