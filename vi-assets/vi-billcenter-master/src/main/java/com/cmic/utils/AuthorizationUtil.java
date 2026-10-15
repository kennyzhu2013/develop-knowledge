package com.cmic.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthorizationUtil {

    /**
     * 生成签名
     * @param timestamp
     * @param appkey
     * @return
     */
    public static String generateSignature(String timestamp, String appkey) {
        try {
            // 1. 拼接 timestamp 和 appkey
            String input = timestamp + appkey;

            // 2. 创建 MD5 消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("MD5");

            // 3. 计算哈希值（MD5 输出 16 字节）
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 4. 将字节数组转换为大写的十六进制字符串（32 字符）
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();

        } catch (Exception e) {
            return "";  // 异常时返回空字符串（与原逻辑一致）
        }
    }

    /**
     * 生成鉴权头部信息
     * @param appId
     * @param appKey
     * @return
     */
    public static String getAuthorizationHeader(String appId, String appKey) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature(timestamp, appKey);  // 使用已修改的 MD5 签名方法
        return "EOPAUTH appid=\"" + appId + "\",timestamp=\"" + timestamp + "\",signature=\"" + signature + "\"";
    }


    public static void main(String[] args) {
        //System.out.println(generateSignature("1630479002", "1408103878"));
        System.out.println(getAuthorizationHeader("1408103878","ali"));
    }




}
