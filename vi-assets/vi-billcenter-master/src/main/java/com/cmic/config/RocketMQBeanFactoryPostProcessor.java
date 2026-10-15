package com.cmic.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/*
自定义一个 BeanFactoryPostProcessor，将这些 Bean 的注入方式改为延迟或标记为 ROLE_INFRASTRUCTURE，避免被过早初始化
 */
@Component
public class RocketMQBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        String[] beanNames = {
                "org.apache.rocketmq.client.autoconfigure.MessageConverterConfiguration",
                "createMessageConverter",
                "rocketmq-org.apache.rocketmq.client.autoconfigure.RocketMQProperties",
                "org.apache.rocketmq.client.autoconfigure.ListenerContainerConfiguration"
        };

        for (String beanName : beanNames) {
            if (beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
                bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            }
        }
    }
}
