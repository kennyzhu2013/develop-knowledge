package com.cmic.util;

import redis.clients.jedis.Jedis;

public interface CallJedis<T> {
    T call(Jedis jedis) throws Exception;
}