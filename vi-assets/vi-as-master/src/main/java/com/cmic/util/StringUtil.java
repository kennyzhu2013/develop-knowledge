package com.cmic.util;

public class StringUtil {
    private static final String ipReg = "([0,1]?\\d{1,2}|2([0-4][0-9]|5[0-5]))(\\.([0,1]?\\d{1,2}|2([0-4][0-9]|5[0-5]))){3}";

    public static boolean isIp(String input){
        if(input.matches(ipReg)){
            return true;
        }else {
            return false;
        }
    }

    public static boolean isBlankOrNull(String input){
        return input==null||input.isEmpty();
    }

    public static void main(String[] args) {
        System.out.println(isIp("invalid.invalid"));
        System.out.println(isIp("10.191.38.76"));
        System.out.println(isIp("2409:8086:8313:4b1::11"));
        String reg = "^\\[\\d{4}\\-\\d{2}\\-\\d{2}\\s\\d{2}\\:\\d{2}\\:\\d{2}\\,\\d{3}\\](.*|.*\\s)";
        String a = "[2021-05-28 20:50:10,837] [xdtmf] [DEBUG] -  - [1269114033778893],[signalling] SIP/2.0 200 OK \n";
        System.out.println(a
                .matches(reg));
    }
}
