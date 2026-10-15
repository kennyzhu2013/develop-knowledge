package com.cmic.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SpringContextHolder implements ApplicationContextAware {
    private static ApplicationContext applicationContext = null;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (SpringContextHolder.applicationContext == null) {
            SpringContextHolder.applicationContext = applicationContext;
        }
    }

    public static ApplicationContext getApplicationContext() {
        checkApplicationContext();
        return applicationContext;
    }

    public static <T> T getBean(String name) {
        checkApplicationContext();
        return (T) applicationContext.getBean(name);
    }

    public static <T> T getBean(String beanName, Class<T> type) {
        checkApplicationContext();
        return applicationContext.getBean(beanName, type);
    }

    public static <T> T getBean(Class<T> clazz) {
        checkApplicationContext();

        Map<String, T> beanMaps = applicationContext.getBeansOfType(clazz);
        if ((beanMaps != null) && (!beanMaps.isEmpty())) {
            return beanMaps.values().iterator().next();
        }
        return null;
    }

    private static void checkApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("applicaitonContext未注入,请在applicationContext.xml中定义SpringContextHolder");
        }
    }
}
