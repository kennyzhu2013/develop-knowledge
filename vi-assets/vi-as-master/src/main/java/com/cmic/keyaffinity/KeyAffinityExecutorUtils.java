package com.cmic.keyaffinity;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * @author w.vela
 * Created on 2020-08-17.
 */
public class KeyAffinityExecutorUtils {

    public static final int RANDOM_THRESHOLD = 20;

    static Supplier<ExecutorService> executor(String threadName, IntSupplier queueBufferSize) {
        return new Supplier<ExecutorService>() {

            private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(threadName)
                    .build();

            @Override
            public ExecutorService get() {
                BlockingQueue<Runnable> queue = new DynamicCapacityLinkedBlockingQueue<Runnable>(queueBufferSize) {
                    @Override
                    public boolean offer(Runnable o) {
                        try {
                            put(o);
                            return true;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return false;
                    }
                };
                return new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, queue, threadFactory);
            }
        };
    }

    public static Supplier<ExecutorService> executor(String threadName, int queueBufferSize) {
        return new Supplier<ExecutorService>() {

            private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(threadName)
                    .build();

            @Override
            public ExecutorService get() {
                LinkedBlockingQueue<Runnable> queue;
                if (queueBufferSize > 0) {
                    queue = new LinkedBlockingQueue<Runnable>(queueBufferSize) {

                        @Override
                        public boolean offer(Runnable e) {
                            try {
                                put(e);
                                return true;
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            return false;
                        }
                    };
                } else {
                    queue = new LinkedBlockingQueue<>();
                }
                return new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, queue, threadFactory);
            }
        };
    }

    public static Supplier<ExecutorService> executor(String threadName, int queueBufferSize, RejectedExecutionHandler rejectedExecutionHandler) {
        return new Supplier<ExecutorService>() {

            private final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat(threadName)
                    .build();

            @Override
            public ExecutorService get() {
                LinkedBlockingQueue<Runnable> queue;
                if (queueBufferSize > 0) {
                    queue = new LinkedBlockingQueue<Runnable>(queueBufferSize) ;
                } else {
                    queue = new LinkedBlockingQueue<>();
                }
                return new ThreadPoolExecutor(1, 1, 0L, MILLISECONDS, queue, threadFactory, rejectedExecutionHandler);
            }
        };
    }
}
