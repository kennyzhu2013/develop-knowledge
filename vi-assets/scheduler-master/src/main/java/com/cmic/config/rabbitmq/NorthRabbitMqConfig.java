package com.cmic.config.rabbitmq;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;

//@Data
//@Configuration
//@ConfigurationProperties(prefix = "spring.rabbitmq.north")
public class NorthRabbitMqConfig extends RabbitMqConfig{
//    @Autowired
    private ReturnCallBack returnCallBack;
//    @Autowired
    private ConfirmCallBack confirmCallBack;
    @Value("${spring.rabbitmq.concurrency}")
    private int concurrency;
    @Value("${spring.rabbitmq.max-concurrency}")
    private int maxConcurrency;
    @Value("${spring.rabbitmq.prefetchCount}")
    private int prefetchCount;

    @Bean(name = "northConnectionFactory")
    public ConnectionFactory northConnectionFactory(){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(getHost());
        connectionFactory.setPort(getPort());
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword());
        connectionFactory.setVirtualHost(getVirtualHost());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setChannelCacheSize(100);
        connectionFactory.setPublisherReturns(isPublisherReturns());
        return connectionFactory;
    }

    @Bean(name = "northRabbitTemplate")
    public RabbitTemplate northRabbitTemplate(@Qualifier("northConnectionFactory") ConnectionFactory connectionFactory,Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setConfirmCallback(confirmCallBack);
        rabbitTemplate.setReturnsCallback(returnCallBack);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        return rabbitTemplate;
    }

    @Bean(name = "northFactory")
    public SimpleRabbitListenerContainerFactory secondFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Qualifier("northConnectionFactory") ConnectionFactory connectionFactory,
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
}
