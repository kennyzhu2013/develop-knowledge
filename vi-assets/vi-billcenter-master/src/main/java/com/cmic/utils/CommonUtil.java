package com.cmic.utils;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CommonUtil {


    public static String generateRequestId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

/*    public static String generateRequestId(String userId) {
        // 1. 处理用户ID（补足或截断为10位）
        String processedUserId = processUserId(userId);

        // 2. 生成14位时间戳（YYYYMMDDHHMMSS格式）
        String timestamp = getCurrentTimestamp();

        // 3. 生成6位随机流水号（不足6位前面补0）
        String serialNumber = generateSerialNumber();

        return processedUserId + timestamp + serialNumber;
    }*/

    private static String processUserId(String userId) {
        if (userId == null) {
            return "0000000000"; // 默认值处理
        }

        if (userId.length() > 10) {
            return userId.substring(0, 10); // 截断前10位
        } else if (userId.length() < 10) {
            return String.format("%010d", Integer.parseInt(userId)); // 补0至10位
        }
        return userId;
    }

    private static String getCurrentTimestamp() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private static String generateSerialNumber() {
        int randomNum = ThreadLocalRandom.current().nextInt(0, 1000000);
        return String.format("%06d", randomNum); // 补0至6位
    }


}
