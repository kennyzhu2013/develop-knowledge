package com.cmic.util;


import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class TimeUtil {
    public static final String MINUTES = "minutes";
    public static final String SECONDS = "seconds";
    public static final String HOURS = "hours";
    public static ThreadLocal<SimpleDateFormat> df = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    public static String covertTimeToString(Timestamp ts) {
        return df.get().format(ts);
    }

    public static String covertTimeToString(Timestamp ts, SimpleDateFormat simpleDateFormat) {
        return simpleDateFormat.format(ts);
    }

    public static TimeUnit covertStringToTimeUnit(String timeUnit) {
        switch (timeUnit) {
            case MINUTES:
                return TimeUnit.MINUTES;
            case SECONDS:
                return TimeUnit.SECONDS;
            case HOURS:
                return TimeUnit.HOURS;
        }
        return TimeUnit.MINUTES;
    }

    public static boolean isBindTimeBeforeMayFirst(String billId, String bindTime) {

        try{
            // 定义日期时间格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

            // 解析输入时间
            LocalDateTime inputDateTime = LocalDateTime.parse(bindTime, formatter);

            // 构建当年5月1日的00:00:00时间
            LocalDateTime mayFirst = LocalDate.of(inputDateTime.getYear(), 5, 1)
                    .atStartOfDay();

            // 比较时间并返回结果
            return inputDateTime.isBefore(mayFirst);
        }catch (Exception e){
            DtmfLogger.error(billId,"bindTime format invalid! ",e);
           return false;
        }

    }
}
