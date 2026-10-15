package com.cmic.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LRUMap<K, V> extends LinkedHashMap<K, V> {
    private Lock putlock = new ReentrantLock();
    private Lock getlock = new ReentrantLock();
    private int MaxSize;

    public LRUMap(int maxSize) {
        super(maxSize, 0.75f, true);
        this.MaxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return this.size() >= MaxSize;
    }

    public V putCache(K key, V value) {
        putlock.lock();
        V v = this.put(key, value);
        putlock.unlock();
        return v;
    }

    public V getCache(K key) {
        getlock.lock();
        V v = this.get(key);
        getlock.unlock();
        return v;
    }

}
