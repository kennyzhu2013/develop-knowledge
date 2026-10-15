package com.cmic.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class CaiNiaoUtil {
    private static final String ENC = "UTF-8";
    private static final Logger logger = LoggerFactory.getLogger(CaiNiaoUtil.class);

    /**
     * 计算签名报文
     *
     * @param secretKey 提供的密钥信息
     * @param param     供应商的入参信息，该入参信息包括签名信息和具体的业务入参信息
     */
    public static String digest(String secretKey, Map<String, String> param) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        // 利用sortMap，根据param的key对param进行排序
        TreeMap<String, String> sortMap = new TreeMap<>(param);
        // 去除携带的报文摘要
        sortMap.remove("signature");

        String body = buildStr(sortMap);
        return doDigest(secretKey, body);
    }

    private static String doDigest(String secretKey, String body) throws NoSuchAlgorithmException {
        String finalBody = secretKey + body;
        // 生成md5摘要
        byte[] digest = md5(finalBody.getBytes(StandardCharsets.UTF_8));
        // 进行base64编码
        logger.info("doDigest digest={}", digest);
        return base64(digest);
    }

    private static String base64(byte[] digest) {
        return Base64.getEncoder().encodeToString(digest);
    }

    private static byte[] md5(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest algorithm = MessageDigest.getInstance("MD5");
        algorithm.update(input);
        return algorithm.digest();
    }

    private static String buildStr(TreeMap<String, String> map) throws UnsupportedEncodingException {

        Iterator<String> it = map.keySet().iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            String key = it.next();
            if (!StringUtils.isEmpty(key)) {
                sb.append("&")
                        .append(specialUrlEncode(key))
                        .append("=")
                        .append(specialUrlEncode(map.get(key)));
            }
        }
        return specialUrlEncode(sb.substring(1));
    }

    private static String specialUrlEncode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, ENC);
    }

    public static String generateSinature(String secretKey, Map<String, String> param) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        return digest(secretKey, param);
    }

    public static void main(String[] args) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        Map<String, String> param = new HashMap<>();
        // 密钥信息，后续提供
        String secretKey = "";
        // 签名信息
        param.put("supplier", "");
        param.put("timestamp", "");
        param.put("signature", "");
        param.put("method", "");
        param.put("requestId", "");

        // 业务参数信息
        param.put("sessionId", "");
        param.put("serviceType", "");
        param.put("callInNUmber", "");
        param.put("secretNumber", "");
        param.put("bCallHistory", "");
        param.put("extNumber", "");
        param.put("callPhase", "");
        param.put("callTime", "");
        param.put("extend", "");

        System.out.println(digest(secretKey, param));
    }
}
