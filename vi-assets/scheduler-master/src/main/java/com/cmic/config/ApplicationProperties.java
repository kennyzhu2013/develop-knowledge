package com.cmic.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties
@Data
public class ApplicationProperties {
    private Xml xml;
    private RateLimiter rateLimiter;
    private Common common;

    @Data
    public static class Xml {
        private String path;
    }

    @Data
    public static class RateLimiter {
        private int rate;
        private int rateInterval = 1;
    }

    @Data
    public static class Common {
        private List<String> atomicProcessId;
    }
}
