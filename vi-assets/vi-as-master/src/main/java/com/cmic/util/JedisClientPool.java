package com.cmic.util;

import com.cmic.config.RedisConfig;
import com.cmic.start.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisClientPool {
    private static Logger logger = LoggerFactory.getLogger(JedisClientPool.class);
    private static JedisPool jedisPool;

    // 实例化连接池
    public static void init(JedisPoolConfig jedisPoolConfig, String host, int port, int timeout, String password) {
        if (jedisPool != null) {
            return;
        }
        // 初始化连接池对象
        jedisPool = new JedisPool(jedisPoolConfig, host, port, timeout, password);
    }

    // 获取Redis连接资源，并确保在使用后归还
    public static <T> T execute(CallJedis<T> caller) throws Exception {
        if (jedisPool == null) {
            logger.info("jedisPool null, redis init");
            Application.initRedis();
        }
        try (Jedis jedis = jedisPool.getResource()) {
            if (RedisConfig.getUse_db() != jedis.getDB()) {
                logger.info("set redis db:{}", RedisConfig.getUse_db());
                jedis.select(RedisConfig.getUse_db());
            }
            return caller.call(jedis);
        }
    }
}