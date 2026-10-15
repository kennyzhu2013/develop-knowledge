package com.cmic.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CommonThreadPool {

    public static ThreadPoolExecutor httpThreadPool = new ThreadPoolExecutor(30, 100, 20, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(5000),
            new ThreadFactoryBuilder().setNameFormat("java11HttpClient-pool-%d").build(),
            (r, e) -> {
                log.error("[Java11HttpClientSender-rejectedExecution], queue full , reject it");
                throw new RejectedExecutionException("Task " + r.toString() +
                        " rejected from " +
                        e.toString());

            }
    );

    public static ThreadPoolExecutor adapterCallbackThreadPool = new ThreadPoolExecutor(100, 120, 20, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(8000),
            new ThreadFactoryBuilder().setNameFormat("adapterCallback-pool-%d").build(),
            (r, e) -> {
                log.error("[AdapterCallback-rejectedExecution], queue full , reject it");
                throw new RejectedExecutionException("Task " + r.toString() +
                        " rejected from " +
                        e.toString());

            }
    );

    public static ThreadPoolExecutor ebCallbackThreadPool = new ThreadPoolExecutor(100, 120, 20, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(8000),
            new ThreadFactoryBuilder().setNameFormat("ebCallback-pool-%d").build(),
            (r, e) -> {
                log.error("[EbCallback-rejectedExecution], queue full , reject it");
                throw new RejectedExecutionException("Task " + r.toString() +
                        " rejected from " +
                        e.toString());

            }
    );

}