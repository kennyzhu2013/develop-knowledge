package com.cmic.business.query.idaas;

import com.alibaba.fastjson.serializer.SerializerFeature;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Locale;

/**
 * @author Administrator
 */
public class SignUtil {

    public static final SerializerFeature SERIALIZER_FEATURE = SerializerFeature.MapSortField;
    public static final String ALGORITHM_SHA256WITHRSA = "SHA256withRSA";
    public static final String ALGORITHM_HMACSHA256 = "HmacSHA256";


    /**
     * 全局数组
     */
    private final static String[] hexDigits = {"0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

    /**
     * 使用私钥进行签名
     *
     * @param original
     * @param pk
     * @return
     */
    public static byte[] signByPK(byte[] original, PrivateKey pk, String algorithm) {
        byte[] data = null;
        try {
            Signature sig = Signature.getInstance(algorithm);
            sig.initSign(pk);
            sig.update(original);
            data = sig.sign();
        } catch (NoSuchAlgorithmException e) {
        } catch (InvalidKeyException e) {
        } catch (SignatureException e) {
        }
        return data;
    }

    public static String encryptWithKeyToHEX(String preSign, String hmacAlgName, String secretKey) {
        try {
            //对json进行排序，SERIALIZER_FEATURE ：SerializerFeature.MapSortField
//			String preSign = JSONObject.toJSONString(request, SERIALIZER_FEATURE);
            SecretKey key = new SecretKeySpec(secretKey.getBytes("UTF-8"), hmacAlgName);
            Mac mac = Mac.getInstance(hmacAlgName);
            mac.init(key);
            mac.update(preSign.getBytes("utf-8"));
            byte[] out = mac.doFinal();

            String hexString = byteArrayToHexString(out);
            return hexString.toUpperCase(Locale.ENGLISH);
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 将一个字节转化成十六进制形式的字符串
     *
     * @param b 字节数组
     * @return 字符串
     */
    private static String byteToHexString(byte b) {
        int ret = b;
        //System.out.println("ret = " + ret);  
        if (ret < 0) {
            ret += 256;
        }
        int m = ret / 16;
        int n = ret % 16;
        return hexDigits[m] + hexDigits[n];
    }

    /**
     * 转换字节数组为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String byteArrayToHexString(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(byteToHexString(bytes[i]));
        }
        return sb.toString();
    }


}
