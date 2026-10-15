package com.cmic.config.rabbitmq;

import cn.hutool.core.lang.UUID;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Data
@Configuration
public class SouthRabbitMqProducerConfig  extends SouthRabbitMqConfig{
//    @Autowired
//    private ReturnCallBack returnCallBack;
//    @Autowired
//    private ConfirmCallBack confirmCallBack;

    @Bean(name = "southProducerConnectionFactory")
    @Primary
    public ConnectionFactory southProducerConnectionFactory(){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(getHost());
        connectionFactory.setPort(getPort());
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword());
        connectionFactory.setVirtualHost(getVirtualHost());

        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.NONE);
        connectionFactory.setChannelCacheSize(25);
        connectionFactory.setPublisherReturns(false);

        connectionFactory.setRequestedHeartBeat(300);
        connectionFactory.setConnectionTimeout(3000);
//        connectionFactory.setConnectionNameStrategy(c -> "producer-" + UUID.randomUUID());
        return connectionFactory;
    }

    @Bean(name = "southRabbitTemplate")
    public RabbitTemplate southRabbitTemplate(@Qualifier("southProducerConnectionFactory") ConnectionFactory connectionFactory, Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        rabbitTemplate.setMandatory(true);
//        rabbitTemplate.setConfirmCallback(confirmCallBack);
//        rabbitTemplate.setReturnsCallback(returnCallBack);
        return rabbitTemplate;
    }
}
