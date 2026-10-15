package com.cmic.config.rabbitmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.rabbitmq.send")
@Data
public class MqSendProperties {

    /**
     * 最大发送并发（全局）
     */
    private int maxConcurrency = 16;

    /**
     * 获取许可超时时间
     */
    private long acquireTimeoutMs = 2000;
}
