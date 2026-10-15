package com.cmic.config.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Aspect
@Component
public class PrometheusAspect {

    @Resource
    private Counter task_create_count;
    @Resource
    private Counter task_callback_count;
    @Resource
    private Counter subtask_callback_count;
    @Resource
    private Histogram task_create_latency_seconds;
    @Resource
    private Histogram task_callback_latency_seconds;
    @Resource
    private Histogram subtask_callback_latency_seconds;

    // 监控createTask方法
    @Pointcut("execution(public * com.cmic.service.task.TaskService.handlerMqTask(..))")
    public void pointCutCreateTask() {
    }

    @Around("pointCutCreateTask()")
    public Object createTaskStat(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        task_create_count.inc();
        long startTime = System.currentTimeMillis();
        // 目标方法执行
        Object result = proceedingJoinPoint.proceed();
        //后置
        double time = System.currentTimeMillis() - startTime;
        task_create_latency_seconds.observe(time / 1000);
        return result;
    }

    // 监控子任务回调方法
    @Pointcut("execution(public * com.cmic.service.CallbackService.callbackJob(..))")
    public void pointCutDoSubTaskCallBack() {
    }

    @Around("pointCutDoSubTaskCallBack()")
    public Object doSubTaskCallBackStat(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // 前置
        subtask_callback_count.inc();
        long startTime = System.currentTimeMillis();
        // 目标方法执行
        Object result = proceedingJoinPoint.proceed();
        //后置
        double time = System.currentTimeMillis() - startTime;
        subtask_callback_latency_seconds.observe(time / 1000);
        return result;
    }


    // 监控主任务回调方法
    @Pointcut("execution(public * com.cmic.service.CallbackService.callbackTask(..))")
    public void pointCutDoTaskCallBack() {
    }

    @Around("pointCutDoTaskCallBack()")
    public Object doTaskCallBackStat(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        // 前置
        task_callback_count.inc();
        long startTime = System.currentTimeMillis();
        // 目标方法执行
        Object result = proceedingJoinPoint.proceed();
        //后置
        double time = System.currentTimeMillis() - startTime;
        task_callback_latency_seconds.observe(time / 1000);
        return result;
    }

}
