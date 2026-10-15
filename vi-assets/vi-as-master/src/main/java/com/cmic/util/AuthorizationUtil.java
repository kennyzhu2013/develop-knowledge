package com.cmic.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthorizationUtil {

    /**
     * 生成签名
     * @param timestamp
     * @param secret
     * @return
     */
    public static String generateSignature(String timestamp, String secret){
        try{
            // 1. 拼接timestamp和secret
            String input = timestamp + secret;

            // 2. 创建SHA-256消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 3. 计算哈希值
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 4. 将字节数组转换为大写的十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().toUpperCase();

        }catch (Exception e){
            return "";
        }
    }

    /**
     * 生成鉴权头部信息
     * @param secret
     * @param platformId
     * @return
     */
    public static String getAuthorizationHeader(String secret, String platformId) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature(timestamp,secret);
        String authorizationHeader = "AUTH Signature=\""+signature+ "\""+",PlatformId=\"" + platformId + "\"" + ",Timestamp=\"" + timestamp + "\"";
        return authorizationHeader;
    }

    public static String getAliAuthorizationHeader(String secret, String userAccId) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = generateSignature(timestamp,secret);
        String authorizationHeader = "AUTH Signature=\""+signature+ "\""+",UserId=\"" + userAccId + "\"" + ",Timestamp=\"" + timestamp + "\"";
        return authorizationHeader;
    }

    public static void main(String[] args) {
        //System.out.println(generateSignature("1630479002", "1408103878"));
        System.out.println(getAuthorizationHeader("1408103878","ali"));
    }




}
