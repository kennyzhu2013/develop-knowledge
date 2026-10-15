package com.cmic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@Slf4j
public class ViPusherApplication {

    public static void main(String[] args) {
        ApplicationContext context = null;
        try {
            context = SpringApplication.run(ViPusherApplication.class, args);
            log.info("StartSuccess");
        } catch (Exception e) {
            log.error("StartFail", e);
            if (context != null) {
                SpringApplication.exit(context);
            } else {
                System.exit(1);
            }
        }
    }

}
