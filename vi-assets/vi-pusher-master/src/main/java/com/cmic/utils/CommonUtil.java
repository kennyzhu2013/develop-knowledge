package com.cmic.utils;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CommonUtil {

    public static HashMap<Integer, Integer> UnconnectedCauseMap = new HashMap<>();
    private static HashMap<Integer, Integer> AliReleaseCauseMap = new HashMap<>();

    static {
        UnconnectedCauseMap.put(5, 6);
        UnconnectedCauseMap.put(7, 3);
        UnconnectedCauseMap.put(8, 2);
        UnconnectedCauseMap.put(10, 4);
        UnconnectedCauseMap.put(11, 5);
        UnconnectedCauseMap.put(12, 7);
        UnconnectedCauseMap.put(15, 1);


        AliReleaseCauseMap.put(1, 16);
        AliReleaseCauseMap.put(2, 16);
        AliReleaseCauseMap.put(3, 16);
        AliReleaseCauseMap.put(4, 19);
        AliReleaseCauseMap.put(5, 17);
        AliReleaseCauseMap.put(6, 44);
        AliReleaseCauseMap.put(7, 34);
        AliReleaseCauseMap.put(8, 16);
        AliReleaseCauseMap.put(9, 16);
        AliReleaseCauseMap.put(10, 3);
        AliReleaseCauseMap.put(11, 4);
        AliReleaseCauseMap.put(12, 21);
        AliReleaseCauseMap.put(13, 1);
        AliReleaseCauseMap.put(14, 22);
        AliReleaseCauseMap.put(15, 29);
        AliReleaseCauseMap.put(16, 63);
        AliReleaseCauseMap.put(17, 38);
        AliReleaseCauseMap.put(18, 38);
        AliReleaseCauseMap.put(19, 16);
        AliReleaseCauseMap.put(20, 16);
        AliReleaseCauseMap.put(21, 44);
        AliReleaseCauseMap.put(22, 29);
        AliReleaseCauseMap.put(23, 242);
        AliReleaseCauseMap.put(24, 999);
    }

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
