package com.cmic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableMongoAuditing
@Slf4j
public class SchedulerApplication {
    public static void main(String[] args) {
        ApplicationContext context = null;
        try {
            context = SpringApplication.run(SchedulerApplication.class, args);
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
