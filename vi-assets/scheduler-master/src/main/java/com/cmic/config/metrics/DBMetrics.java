package com.cmic.config.metrics;

import io.prometheus.client.Counter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Aspect
public class DBMetrics {
    @Resource
    private Counter database_error_count;
    @Resource
    private Counter mongodb_error_count;
    @Resource
    private Counter redis_error_count;

    public void databaseErrorInc(String dbName, String operation, Throwable ex) {
        database_error_count.labels(dbName, operation, ex.getClass().getSimpleName()).inc();
    }

    public void mongodbErrorInc(String operation, Throwable ex) {
        mongodb_error_count.labels(operation, ex.getClass().getSimpleName()).inc();
    }

    public void redisErrorInc(String operation, Throwable ex) {
        redis_error_count.labels(operation, ex.getClass().getSimpleName()).inc();
    }

    @Around("execution(* com.cmic.util.RedisUtil.*(..))")
    public Object monitorRedis(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            redisErrorInc(joinPoint.getSignature().getName(), ex);
            throw ex;
        }
    }
}
