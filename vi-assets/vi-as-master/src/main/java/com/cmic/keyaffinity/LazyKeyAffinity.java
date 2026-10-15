package com.cmic.keyaffinity;


import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.function.Supplier;

import static com.cmic.keyaffinity.MoreSuppliers.lazy;


/**
 * @author w.vela
 * Created on 2018-02-08.
 */
class LazyKeyAffinity<K, V> implements KeyAffinity<K, V> {

    private final MoreSuppliers.CloseableSupplier<KeyAffinityImpl<K, V>> factory;

    LazyKeyAffinity(Supplier<KeyAffinityImpl<K, V>> factory) {
        this.factory = lazy(factory, false);
    }

    @Nonnull
    public V select(K key) {
        return factory.get().select(key);
    }

    public void finishCall(K key) {
        factory.get().finishCall(key);
    }

    @Override
    public boolean inited() {
        return factory.isInitialized();
    }

    @Override
    public void close() throws Exception {
        factory.tryClose(KeyAffinity::close);
    }

    @Override
    public Iterator<V> iterator() {
        return factory.get().iterator();
    }

    public int getPoolMinConcurrency() throws Exception{
        return factory.get().getMinConcurrencyFromPool();
    }
}
