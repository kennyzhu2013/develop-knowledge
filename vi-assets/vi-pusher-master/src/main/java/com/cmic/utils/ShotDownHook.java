package com.cmic.utils;


import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ShotDownHook {


    @PreDestroy
    public void destory() {
        try {
            // 等待10秒, 等待线程执行完毕
            Thread.sleep(10000);
        } catch (Exception e) {
            // 日志记录关闭过程中的异常
            log.error("Application shot down error", e);

        }
        log.info("Application shot down ..........................");
    }
}
