package com.cmic.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class SnowflakeUtils {
    private static int workerId;
    private static Snowflake snowflake;

    @Value("${service.instance.id}")
    public void setWorkerId(int id) {
        workerId = id;
        snowflake = IdUtil.getSnowflake(workerId, 1);
    }

    /**
     * 生成雪花算法生成的 ID
     */
    public static long generateId() {
        return snowflake.nextId();
    }

    /**
     * 解析雪花算法生成的 ID，获取生成时的 LocalDateTime 对象
     */
    public static LocalDateTime parseIdToDateTime(long id) {
        long timestamp = snowflake.getGenerateDateTime(id);
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.ofHours(8)); // 转换为东八区时间
    }

    /**
     * 解析雪花算法生成的 ID，获取生成时的日期时间字符串
     */
    public static String parseIdToDateTimeString(long id) {
        LocalDateTime dateTime = parseIdToDateTime(id);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * 解析雪花算法生成的 ID，获取生成时的日期字符串
     */
    public static String parseIdToDateString(long id) {
        LocalDateTime dateTime = parseIdToDateTime(id);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return dateTime.format(formatter);
    }

    public static String parseIdToDateString(long id, String pattern) {
        LocalDateTime dateTime = parseIdToDateTime(id);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }

    public static void main(String[] args) {
        long id = SnowflakeUtils.generateId();
        String randomCode = "p"+ RandomUtil.randomString(RandomUtil.BASE_CHAR_NUMBER, 6) + id;
        System.out.println(randomCode);
    }
}
