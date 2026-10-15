package com.cmic.config;

public class ThreadPoolConfig {
    private int poolSize;

    private int queueSize;

    public ThreadPoolConfig(int poolSize, int queueSize) {
        this.poolSize = poolSize;
        this.queueSize = queueSize;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
}
