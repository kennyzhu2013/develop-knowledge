package com.cmic.config;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.time.Duration;
import java.util.List;

public class RedisConfig {
    public static void main(String[] args) throws Exception {
        // RedisConfig.init("config/redis.xml");
    }

    public static void init(String appConfigFile) throws Exception {
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(appConfigFile));
        @SuppressWarnings("unchecked")
        List<Element> listElement = document.getRootElement().elements();//所有一级子节点的list
        for (Element e : listElement) {//遍历所有一级子节点
            System.out.println("当前节点名称：" + e.getName());//当前节点名称
            System.out.println("当前节点的内容：" + e.getTextTrim());//当前节点名称

            int maxTotal = 20;
            int maxIdle = 10;
            int minIdle = 5;
            int maxWait_mill = 1000;

            if ("maxTotal".equals(e.getName())) {
                maxTotal = Integer.parseInt(e.getTextTrim());
            }

            if ("maxIdle".equals(e.getName())) {
                maxIdle = Integer.parseInt(e.getTextTrim());
            }

            if ("minIdle".equals(e.getName())) {
                minIdle = Integer.parseInt(e.getTextTrim());
            }

            if ("maxWait_mill".equals(e.getName())) {
                maxWait_mill = Integer.parseInt(e.getTextTrim());
            }

            if ("host".equals(e.getName())) {
                host = e.getTextTrim();
            }

            if ("port".equals(e.getName())) {
                port = Integer.parseInt(e.getTextTrim());
            }

            if ("password".equals(e.getName())) {
                password = e.getTextTrim();
            }

            if ("timeout_mill".equals(e.getName())) {
                timeout_mill = Integer.parseInt(e.getTextTrim());
            }
            if ("use_db".equals(e.getName())) {
                use_db = Integer.parseInt(e.getTextTrim());
            }

            // 1、连接池配置对象
            jedisPoolConfig = new JedisPoolConfig();
            jedisPoolConfig.setMaxTotal(maxTotal);// 最大连接数
            jedisPoolConfig.setMaxIdle(maxIdle);// 最大空闲连接数
            jedisPoolConfig.setMinIdle(minIdle);// 最小空闲连接数
            jedisPoolConfig.setMaxWait(Duration.ofMillis(maxWait_mill));// 连接池没有可用连接时最大等待时间
        }

    }

    private static JedisPoolConfig jedisPoolConfig;
    private static String host;
    private static int port;
    private static String password;
    private static int timeout_mill;
    private static int use_db;


    public static JedisPoolConfig getJedisPoolConfig() {
        return jedisPoolConfig;
    }

    public static String getHost() {
        return host;
    }

    public static int getPort() {
        return port;
    }

    public static String getPassword() {
        return password;
    }

    public static int getTimeout_mill() {
        return timeout_mill;
    }

    public static int getUse_db() {
        return use_db;
    }
}
