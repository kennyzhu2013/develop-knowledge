package com.cmic.util;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * @author XH
 * @description: 自定义base64
 * @date 2025/4/16 18:22
 */
public class CustomBase64 {

    // 自定义编码表
    private static final char[] CUSTOM_TABLE = {
            'v','Q','8','-','W','x','K','l','C','o','@','h','F','m','s','D',
            'J','N','3','y','e','z','R','0','t','b','4','u','M','L','+','#',
            '2','i','O','q','Y','7','P','Z','E','5','j','6','U','k','X','n',
            'S','G','d','1','.','a','H','9','w','V','p','I','@','T','|','B'
    };


    // 反向解码表（预生成加速查询）
    private static final byte[] DECODE_TABLE = new byte[256];
    static {
        for (int i = 0; i < CUSTOM_TABLE.length; i++) {
            DECODE_TABLE[CUSTOM_TABLE[i]] = (byte) i;
        }
    }

    // 自定义填充符（示例用$代替标准=）
    private static final String PADDING = "Acgrf";
    private static final Random R = new Random();

    public static String encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int len = data.length;
        int i = 0;

        while (i < len) {
            int b1 = data[i++] & 0xFF;
            int b2;
            if (i < len) {
                b2 = data[i++] & 0xFF;
            } else {
                b2 = 0;
                i++;
            }
            int b3;
            if (i < len) {
                b3 = data[i++] & 0xFF;
            } else {
                b3 = 0;
                i++;
            }
            // 将3字节转换为4个6位组
            int group1 = (b1 >> 2) & 0x3F;
            int group2 = ((b1 << 4) | (b2 >> 4)) & 0x3F;
            int group3 = ((b2 << 2) | (b3 >> 6)) & 0x3F;
            int group4 = b3 & 0x3F;

            sb.append(CUSTOM_TABLE[group1]);
            sb.append(CUSTOM_TABLE[group2]);
            if (i > len + 1){
                sb.append(PADDING.charAt(R.nextInt(5)));
            } else {
                sb.append(CUSTOM_TABLE[group3]);
            }
            if (i > len){
                sb.append(PADDING.charAt(R.nextInt(5)));
            } else {
                sb.append(CUSTOM_TABLE[group4]);
            }
        }
        return sb.toString();
    }

    public static byte[] decode(String encoded) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] data = encoded.getBytes();
        int len = data.length;
        int i = 0;

        while (i < len) {
            byte b1 = DECODE_TABLE[data[i++]];
            byte b2 = DECODE_TABLE[data[i++]];
            byte b3;
            if(PADDING.indexOf((char)data[i]) > -1){
                b3=0;
                i++;
            } else {
                b3 = DECODE_TABLE[data[i++]];
            }
            byte b4;
            if(PADDING.indexOf((char)data[i]) > -1){
                b4=0;
                i++;
            } else {
                b4 = DECODE_TABLE[data[i++]];
            }

            // 将4个6位组还原为3字节
            int out1 = (b1 << 2) | (b2 >> 4);
            int out2 = ((b2 & 0xF) << 4) | (b3 >> 2);
            int out3 = ((b3 & 0x3) << 6) | b4;

            bos.write(out1);
            if(PADDING.indexOf((char)data[i-2]) < 0) bos.write(out2);
            if(PADDING.indexOf((char)data[i-1]) < 0) bos.write(out3);
        }
        return bos.toByteArray();
    }

    public static void main(String[] args) {
        String original = "12345678910";
        byte[] encrypted = CustomBase64.encode(original.getBytes()).getBytes();
        byte[] decrypted = CustomBase64.decode(new String(encrypted));

        System.out.println("原始数据: " + original);
        System.out.println("加密结果: " + new String(encrypted));
        System.out.println("解密结果: " + new String(decrypted));
    }
}
