package com.cmic.business.query.idaas;

import com.cmic.state.FinishState;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * IDaas接口使用的加解密
 */
public class AESUtil {
    private static final Logger log = LoggerFactory.getLogger(AESUtil.class);
    public AESUtil() {

    }

    public static void main(String[] args) throws Exception {
        /**
         * 生产环境
         */
//        String secret = "123456";
//        String imgBase64 = imgToBase64("/home/CMIC/Pictures/微信图片_20250324151912_113.jpg");
//        String encodeImg = encodeAES(imgBase64, secret);
//        System.out.println(encodeImg);

//        String appKey = "3154015D38B7AE712ECAA5E2C9C3BB23";
//        System.out.println(encodeAES("1980202", appKey, ""));
//        System.out.println(encodeAES("34079", appKey, ""));
//
//        String secret = "iA4eJCm7DW5AXK";
//        System.out.println(encodeAES("34078", secret, ""));
//        System.out.println(encodeAES("34079", secret, ""));
//        System.out.println(encodeAES("34005632", secret, ""));
//        System.out.println(decodeAES("Fp/==", secret));
//        System.out.println(decodeAES("+==", secret));
//        System.out.println(decodeAES("==", secret));
//        System.out.println(encodeAES("", secret));

        /**
         * bytes:141204
         * KB:137
         * length of encode:251052
         */
//        String imgBase64 = imgToBase64("/home/CMIC/Pictures/微信图片_20250324151912_113.jpg");
//        String imgBase64 = imgToBase64("/home/CMIC/Pictures/Wallpapers/abc-123.jpg");
//        String encodeImg = encodeAES(imgBase64, secret, "");
//        System.out.println("imgBase64:");
//        System.out.println(imgBase64);
//        System.out.println("encodeImg:");
//        System.out.println(encodeImg);
//        System.out.println("length of encode:" + encodeImg.length());
//        System.out.println("-----");
        /**
         * bytes:8057876
         * KB:7869
         * length of encode:14325120
         */
//        String imgBase642 = imgToBase64("/home/CMIC/Pictures/微信图片_2025-03-28_093405_174.png");
//        String encodeImg2 = encodeAES(imgBase642, secret);
//        System.out.println(imgBase642);
//        System.out.println("length of encode:" + encodeImg2.length());
//        System.out.println("-----");
        /**
         * bytes:2532725
         * KB:2473
         * length of encode:4502636
         */
//        String imgBase643 = imgToBase64("/home/CMIC/Pictures/微信图片_2025-03-28_093405_174_2M.png");
//        String encodeImg3 = encodeAES(imgBase643, secret);
//        System.out.println("length of encode:" + encodeImg3.length());
//        System.out.println("-----");
//        String key = "8734@r8!8ed1oo0r";
        String key = "testkey";
        //<industrialUserid>2528249469</industrialUserid>
        //        <industrialSecret>ede71b395a18cda0f2fe3494c5a1fff2</industrialSecret>
        String sec = key +"c1!m";
        String text = "ede71b395a18cda0f2fe3494c5a1fff2";
        String encodeText = encodeAES(text, sec, "");
        System.out.println(encodeText);
        String s = decodeAES(encodeText, sec, "");
        System.out.println(s);

    }

    private static String imgToBase64(String path) throws IOException {
        File imageFile = new File(path);
        FileInputStream fileInputStream = new FileInputStream(imageFile);
        byte[] bytes = new byte[(int) imageFile.length()];
        System.out.println("bytes:" + bytes.length);
        System.out.println("KB:" + bytes.length / 1024);
        fileInputStream.read(bytes);
        fileInputStream.close();
        return new String(Base64.encodeBase64(bytes));
    }

    /**
     * 使用appKey方式加密，此方式不用在快签侧配置appId
     * 优先推荐使用
     *
     * @param password
     * @param key
     * @return
     * @throws Exception
     */
    public static String encodeAES(String password, String key, String tag) {
        try {
            byte[] keybBytes = DigestUtils.md5(key);
            byte[] passwdBytes = password.getBytes("UTF-8");
            byte[] aesBytyes = encrypt(passwdBytes, keybBytes);
            return new String(Base64.encodeBase64(aesBytyes), "UTF-8");
        } catch (Exception e) {
            log.error("encodeAES failed", e);
            throw new RuntimeException(tag + "参数加密异常");
        }
    }

    /**
     * 使用appKey方式加密的使用此方法解密,此方式不用在快签侧配置appId
     *
     * @param encodeText
     * @param key
     * @return
     * @throws Exception
     */
    public static String decodeAES(String encodeText, String key, String tag) {
        try {
            byte[] keybBytes = DigestUtils.md5(key);
            byte[] debase64Bytes = Base64.decodeBase64(encodeText);
            return new String(decrypt(debase64Bytes, keybBytes), "UTF-8");
        } catch (Exception e) {
            log.error("decodeAES failed", e);
            throw new RuntimeException(tag + "参数解密异常");
        }
    }


    /**
     * 使用给定的固定秘钥加密，此方式需要再快签侧配置appId
     *
     * @param encryptKey
     * @param content
     * @param secretKey
     * @param iv
     * @return
     * @throws Exception
     */
    public static String aesEncryptInCBC(boolean encryptKey, String content, byte[] secretKey, byte[] iv) throws Exception {
        return Base64.encodeBase64String(aesEncrypt(1, encryptKey, content, secretKey, iv));
    }

    /**
     * 使用给定的固定秘钥加密后，用此方式解密，此方式需要再快签侧配置appId
     *
     * @param encryptKey
     * @param encryptedStr
     * @param secretKey
     * @param iv
     * @return
     * @throws Exception
     */
    public static String aesDecryptInCBC(boolean encryptKey, String encryptedStr, byte[] secretKey, byte[] iv) throws Exception {
        return StringUtils.isEmpty(encryptedStr) ? null : aesDecrypt(1, encryptKey, base64Decode(encryptedStr), secretKey, iv);
    }


    public static byte[] encrypt(byte[] text, byte[] key) throws Exception {
        SecretKeySpec aesKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey);
        return cipher.doFinal(text);
    }

    private static byte[] aesEncrypt(int aesMode, boolean encryptKey, String content, byte[] secretKey, byte[] iv) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(getSecretKey(encryptKey, secretKey), "AES");
        Cipher cipher = null;
        if (aesMode == 0) {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(1, skeySpec);
        } else {
            if (aesMode != 1) {
                throw new NoSuchAlgorithmException();
            }

            if (iv == null || iv.length != 16) {
                throw new InvalidAlgorithmParameterException("Wrong IV length: must be 16 bytes long");
            }

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(1, skeySpec, new IvParameterSpec(iv));
        }

        return cipher.doFinal(content.getBytes("UTF-8"));
    }

    private static String aesDecrypt(int aesMode, boolean encryptKey, byte[] encryptData, byte[] secretKey, byte[] iv) throws Exception {
        SecretKeySpec skeySpec = new SecretKeySpec(getSecretKey(encryptKey, secretKey), "AES");
        Cipher cipher = null;
        if (aesMode == 0) {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(2, skeySpec);
        } else {
            if (aesMode != 1) {
                throw new NoSuchAlgorithmException();
            }

            if (iv == null || iv.length != 16) {
                throw new InvalidAlgorithmParameterException("Wrong IV length: must be 16 bytes long");
            }

            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(2, skeySpec, new IvParameterSpec(iv));
        }

        byte[] decryptBytes = cipher.doFinal(encryptData);
        return new String(decryptBytes, "UTF-8");
    }

    private static byte[] getSecretKey(boolean encryptKey, byte[] secretKey) throws Exception {
        if (encryptKey) {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgenInit(kgen, secretKey);
            secretKey = kgen.generateKey().getEncoded();
        } else if (secretKey.length != 16) {
            throw new InvalidKeyException("Invalid AES key length: " + secretKey.length);
        }

        return secretKey;
    }

    private static void kgenInit(KeyGenerator kgen, byte[] bytes) throws NoSuchAlgorithmException {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        secureRandom.setSeed(bytes);
        kgen.init(128, secureRandom);
    }

    private static byte[] base64Decode(String base64String) {
        return StringUtils.isEmpty(base64String) ? null : Base64.decodeBase64(base64String);
    }

    public static byte[] decrypt(byte[] text, byte[] key) throws Exception {
        SecretKeySpec aesKey = new SecretKeySpec(key, "AES");

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey);
        return cipher.doFinal(text);
    }
}
