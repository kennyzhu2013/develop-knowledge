package com.cmic.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class FixedSizeLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public FixedSizeLinkedHashMap(int maxSize) {
        super(maxSize, 0.75f, true); // true 表示按访问顺序排序
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize; // 超过最大容量时移除最老的条目
    }
}
