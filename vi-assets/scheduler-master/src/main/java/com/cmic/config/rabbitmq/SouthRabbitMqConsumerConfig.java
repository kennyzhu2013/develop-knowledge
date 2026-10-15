package com.cmic.config.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;
@Slf4j
@Data
@Configuration
public class SouthRabbitMqConsumerConfig extends SouthRabbitMqConfig{
    @Value("${spring.rabbitmq.concurrency}")
    private int concurrency;
    @Value("${spring.rabbitmq.max-concurrency}")
    private int maxConcurrency;
    @Value("${spring.rabbitmq.prefetchCount}")
    private int prefetchCount;

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter(new ObjectMapper());
    }

    @Bean(name = "southConsumerConnectionFactory")
    public ConnectionFactory southConsumerConnectionFactory(){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(getHost());
        connectionFactory.setPort(getPort());
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword());
        connectionFactory.setVirtualHost(getVirtualHost());
        connectionFactory.setChannelCacheSize(25);
        connectionFactory.setRequestedHeartBeat(300);
        return connectionFactory;
    }

    @Bean(name = "southFactory")
    public SimpleRabbitListenerContainerFactory secondFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("southConsumerConnectionFactory") ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setPrefetchCount(prefetchCount);
        factory.setAcknowledgeMode(getAcknowledgeMode(getListener().getAcknowledgeMode()));
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        return factory;
    }

    @Bean
    public Semaphore mqSendSemaphore(MqSendProperties props) {
        return new Semaphore(props.getMaxConcurrency(), true);
    }

}
