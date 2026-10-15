package com.cmic.config.rabbitmq;

import com.google.common.util.concurrent.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MQConsumerRateLimiter {
    private final static Map<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    public static RateLimiter getRateLimiter(String queueName, int initPermits){
        RateLimiter rateLimiter = rateLimiterMap.get(queueName);
        if(rateLimiter == null){
            rateLimiter = RateLimiter.create(initPermits);
            rateLimiterMap.put(queueName, rateLimiter);
        }
        return rateLimiter;
    }

    /**
     * 动态更新队列的消费速率
     */
    public void updateRate(String queueName, double permitsPerSecond) {
        rateLimiterMap.compute(queueName, (key, existingRateLimiter) -> {
            if (existingRateLimiter == null) {
                System.out.println("Creating new RateLimiter for queue: " + queueName);
                return RateLimiter.create(permitsPerSecond);
            } else {
                System.out.println("Updating RateLimiter for queue: " + queueName + " to " + permitsPerSecond + " permits/sec");
                existingRateLimiter.setRate(permitsPerSecond);
                return existingRateLimiter;
            }
        });
    }
}
