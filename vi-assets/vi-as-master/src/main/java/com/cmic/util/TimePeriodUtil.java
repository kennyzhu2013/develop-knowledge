package com.cmic.util;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 时间段通用方法
 * <p>
 * Author: luohuanfa
 * Date: 2025/3/3
 */
public class TimePeriodUtil {
    // 显式指定时区为北京
    private static final ZoneId beijingZone = ZoneId.of("Asia/Shanghai");
    public static boolean isNowInPeriod(int startHour, int startMinute, int endHour, int endMinute) {
        // 获取当前北京时间的时区时间
        ZonedDateTime nowInBeijing = ZonedDateTime.now(beijingZone);
        LocalTime start = LocalTime.of(startHour, startMinute);
        LocalTime end = LocalTime.of(endHour, endMinute);
        // 判断当前是否在该时间段内
        return nowInBeijing.toLocalTime().isAfter(start)
                && nowInBeijing.toLocalTime().isBefore(end);
    }


}
