package com.cmic.util;

import com.ctrip.communication.vn.encrypt.util.EncryptUtils;
import com.ctrip.communication.vn.encrypt.util.Md5EncryptChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XieChengEncryptHelp {
    private static final Logger logger = LoggerFactory.getLogger(XieChengEncryptHelp.class);
    private static EncryptUtils encryptUtils;

    public static final int USE_ASE_ENCRYPT_CODE = 1;

    /**
     * 解密方法 用于传输号码的解密
     * 参数cipherText为需要解密数据
     */
    public static String decrypt(String cipherText) {
        encryptUtils = new EncryptUtils();
        return encryptUtils.decrypt(cipherText);
    }

    /**
     * 加密方法
     * 参数plainText为需要加密数据
     * 参数code用于加密方式，
     * 0 为非对称加密用于将号码保存数据的加密，
     * 1 为对称加密用于用于传输号码的加密，
     */
    public static String encrypt(String plainText, int code) {
        if (USE_ASE_ENCRYPT_CODE == code) {
            encryptUtils = new EncryptUtils();
        } else {
            encryptUtils = new EncryptUtils(EncryptUtils.EncryptType.ASYMMETRIC);
        }
        return encryptUtils.encrypt(plainText);
    }

    /**
     * MD5加密验证签名
     */
    public static String MD5EncryptChecker(String corpCode, int ts) {
        encryptUtils = new EncryptUtils(new Md5EncryptChecker(corpCode, ts));
        return encryptUtils.encrypt(null);
    }

}