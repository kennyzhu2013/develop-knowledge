package com.cmic.util;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class MD5Util {

    public static String encrypt32(String encryptStr) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = md5.digest(encryptStr.getBytes());
            StringBuffer hexValue = new StringBuffer();
            for (int i = 0; i < md5Bytes.length; i++) {
                int val = ((int) md5Bytes[i]) & 0xff;
                if (val < 16)
                    hexValue.append("0");
                hexValue.append(Integer.toHexString(val));
            }
            encryptStr = hexValue.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return encryptStr;
    }

    public static String encrypt16(String encryptStr) {
        return encrypt32(encryptStr).substring(8, 24);
    }

    private static String encryBody(Map<String, String> param) {
        // 利用sortMap，根据param的key对param进行排序
        TreeMap<String, String> sortMap = new TreeMap<>(param);
        String body = buildStr(sortMap);
        System.out.println("encryBody body=" + body);
        return MD5Util.encrypt32(body).toLowerCase();
    }

    private static String buildStr(TreeMap<String, String> map) {
        Iterator<String> it = map.keySet().iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            String key = it.next();
            if (!StringUtils.isEmpty(key)) {
                sb.append("&")
                        .append(key)
                        .append("=")
                        .append(map.get(key));
            }
        }
        return sb.substring(1);
    }


    public static void main(String[] args) {
        String translateMode = "En2Ch";
        translateMode = translateMode.toLowerCase();
        String firstLanguage = translateMode.substring(0,2);
        String secondLanguage = translateMode.substring(3,5);
        String callerAudioFile = "";
        String calleeAudioFile = "";

        if(!com.alibaba.druid.util.StringUtils.isEmpty(firstLanguage)){
            if(firstLanguage.equalsIgnoreCase("CH")){
                firstLanguage = "zh";
            }else{
                firstLanguage = firstLanguage.toLowerCase();
            }
        }

        if(!com.alibaba.druid.util.StringUtils.isEmpty(secondLanguage)){
            if(secondLanguage.equalsIgnoreCase("CH")){
                secondLanguage = "zh";
            }else{
                secondLanguage = secondLanguage.toLowerCase();
            }
        }

        System.out.println(firstLanguage);
        System.out.println(secondLanguage);
    }
}
