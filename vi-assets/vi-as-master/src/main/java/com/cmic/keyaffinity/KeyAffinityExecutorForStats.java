package com.cmic.keyaffinity;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Iterator;
import java.util.concurrent.Callable;

/**
 * @author w.vela
 * Created on 2018-11-29.
 */
class KeyAffinityExecutorForStats<K> implements KeyAffinityExecutor<K> {

    private final KeyAffinityExecutor<K> delegate;

    private KeyAffinityExecutorForStats(KeyAffinityExecutor<K> delegate) {
        this.delegate = delegate;
    }

    static <K> KeyAffinityExecutor<K> wrapStats(KeyAffinityExecutor<K> executor) {
        if (executor instanceof KeyAffinityExecutorForStats) {
            return executor;
        } else {
            return new KeyAffinityExecutorForStats<>(executor);
        }
    }

    @Override
    public boolean inited() {
        return delegate.inited();
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<ListeningExecutorService> iterator() {
        return delegate.iterator();
    }

    @Override
    public <T> ListenableFuture<T> submit(K key, Callable<T> task) {
        return delegate.submit(key, task);
    }

    @Override
    public void executeEx(K key, ThrowableRunnable<Exception> task) {
        delegate.executeEx(key, task);
    }

    @Override
    public KeyAffinityExecutorStats stats() {
        if (delegate.inited()) {
            return delegate.stats();
        } else {
            return null;
        }
    }

    @Override
    public int getMinConcurrency() throws Exception {
        if (delegate.inited()) {
            return delegate.getMinConcurrency();
        } else {
            return 0;
        }
    }
}
