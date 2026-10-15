package com.cmic.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class HmacSha256Util {

    /**
     * 使用HMAC-SHA256算法生成签名，并返回Base64编码的结果
     * @param data 待签名的数据
     * @param key  密钥
     * @return Base64编码的签名字符串
     * @throws NoSuchAlgorithmException 如果算法不存在
     * @throws InvalidKeyException 如果密钥无效
     */
    public static String generateHmacSha256Base64(String data, String key) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * 使用HMAC-SHA256算法生成签名，并返回十六进制字符串
     * @param data 待签名的数据
     * @param key  密钥
     * @return 十六进制表示的签名字符串
     * @throws NoSuchAlgorithmException 如果算法不存在
     * @throws InvalidKeyException 如果密钥无效
     */
    public static String generateHmacSha256Hex(String data, String key) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacBytes);
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static void main(String[] args) {
        try {
            String data = "Hello, HMAC!";
            String key = "mySecretKey";
            
            // 生成Base64编码的签名
            String hmacBase64 = generateHmacSha256Base64(data, key);
            System.out.println("HMAC-SHA256 (Base64): " + hmacBase64);
            
            // 生成十六进制的签名
            String hmacHex = generateHmacSha256Hex(data, key);
            System.out.println("HMAC-SHA256 (Hex): " + hmacHex);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
    }
}