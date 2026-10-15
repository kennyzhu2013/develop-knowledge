package com.cmic.utils;

import java.util.Calendar;
import java.util.Date;

public class TimeUtil {


    public static Date date2UTC(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int zoneOffset = calendar.get(Calendar.ZONE_OFFSET);
        int dstOffset = calendar.get(Calendar.DST_OFFSET);
        calendar.add(Calendar.MILLISECOND, -(zoneOffset + dstOffset));
        Date utcDate = new Date(calendar.getTimeInMillis());
        return utcDate;
    }
}
