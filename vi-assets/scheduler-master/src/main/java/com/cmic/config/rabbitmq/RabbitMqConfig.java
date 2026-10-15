package com.cmic.config.rabbitmq;

import lombok.Data;
import org.springframework.amqp.core.AcknowledgeMode;

@Data
public class RabbitMqConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private String virtualHost;
    private boolean publisherConfirms = false;
    private boolean publisherReturns = false;
    private Listener listener;

    @Data
    public static class Listener {
        private String acknowledgeMode;
    }

    public AcknowledgeMode getAcknowledgeMode(String acknowledgeMode){
        switch (acknowledgeMode){
            case "manual":
                return AcknowledgeMode.MANUAL;
            case "auto":
                return AcknowledgeMode.AUTO;
            default:
                return AcknowledgeMode.AUTO;
        }
    }
}
