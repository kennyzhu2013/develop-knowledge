package com.cmic.service;

import java.util.concurrent.*;

public class ScheduleService {
    private static ScheduleService instance = new ScheduleService();

    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(20);
    private ThreadPoolExecutor threadPoolExecutorService = new ThreadPoolExecutor(20,100,10,TimeUnit.MICROSECONDS,new ArrayBlockingQueue<>(2000));

    public static ScheduleService getInstance() {
        return instance;
    }

    /**
     * 一定时间delay后执行一次
     **/
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.schedule(command, delay, unit);

    }

    /**
     * 从initialDelay后开始周期执行，后一次任务从前一次任务的starttime开始算delay
     **/
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    /**
     * 从initialDelay后开始周期执行， 后一次任务以前一次任务的endtime开始算delay
     **/
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return this.scheduledExecutorService.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    public void execute(Runnable command)
    {
        this.threadPoolExecutorService.execute(command);
    }

    public static void main(String args[]) {
        try {
            ScheduleService.getInstance().scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    System.out.println("fuck");
                }
            }, 2, 1, TimeUnit.SECONDS);
            System.out.println("start.......");
        } catch (Exception e) {

        }
    }
}