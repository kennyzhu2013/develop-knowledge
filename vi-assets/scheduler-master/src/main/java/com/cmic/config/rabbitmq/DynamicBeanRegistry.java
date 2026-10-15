package com.cmic.config.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class DynamicBeanRegistry {

    private final ApplicationContext applicationContext;

    @Autowired
    public DynamicBeanRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public Queue registerQueue(String queueName, String beanName) {
        Queue queue = QueueBuilder.durable(queueName)
                .maxPriority(10)
                .build();
        registerBean(beanName, Queue.class, queue);
        return queue;
    }

    public TopicExchange registerDirectExchange(String exchangeName, String beanName) {
        TopicExchange exchange = new TopicExchange(exchangeName);
        registerBean(beanName, TopicExchange.class, exchange);
        return exchange;
    }

    public FanoutExchange registerFanoutExchange(String exchangeName, String beanName){
        FanoutExchange exchange = new FanoutExchange(exchangeName);
        registerBean(beanName, FanoutExchange.class, exchange);
        return exchange;
    }

    public void registerBinding(Queue queue, TopicExchange exchange, String routingKey, String beanName) {
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);
        registerBean(beanName, Binding.class, binding);
    }

    private <T> void registerBean(String beanName, Class<T> beanClass, T beanInstance) {
        ConfigurableApplicationContext context = (ConfigurableApplicationContext)applicationContext;
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)context.getBeanFactory();
        beanFactory.registerSingleton(beanName, beanInstance);
    }
}


