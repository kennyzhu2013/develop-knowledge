package com.cmic.util;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUitl {

    private static ThreadLocal<DateFormat> sdfThreadLocal = new ThreadLocal<DateFormat>() {
        @Override
        public SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    public static String formatDateyyyyMMddHHmmss(Date date)  {
        return sdfThreadLocal.get().format(date);
    }

    public static Date parse(String strDate) throws ParseException {
        return sdfThreadLocal.get().parse(strDate);
    }
}