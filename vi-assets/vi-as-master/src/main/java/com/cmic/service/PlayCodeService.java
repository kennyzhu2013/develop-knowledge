package com.cmic.service;

import com.cmic.business.BusinessEvent;
import com.cmic.constant.MediaConst;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

//    100000 正在为您转接
//100001 正在转接
//100002 转接中
//
//100300 请输入四位分机号
//100301 请输入分机号
//100302 请输入六位分机号
//100303 请输入五位分机号
//
//100100 中间号提醒您，本次通话将被录音
//100101 欢迎使用中间号，您的通话将被录音
//100102 文明通话，本次通话将被录音
//
//100200 您拨打的电话是空号
//100201 您拨打的电话无法接通
//100202 您拨打的号码是空号
//100203 欢迎使用移动中间号服务，请使用正确的号码呼叫对方
//100305 您好，您的通话被叫出现违规呼叫转移，请稍后重试。
public class PlayCodeService {

    public static final String ERROR_NOTICE_URL = "global_100203";
    public static final String TENG_XUN_ERROR_NOTICE_URL = "/DIDI200";
    public static final String PLAY_NOTICE_URL = "global_100000";
    public static final String PLAY_COLLECT_URL = "global_100300";
    public static final String FOWARD_NOTICE_URL = "global_100002";
    public static final String PLAYCODE_HIJACKING = "global_100203";

    public static final String CALLEE_LIMIT_TIMES_HIJACKING = "global_100201";
    public static final String IDENTITY_INTERCEPT = "global_100307";
    public static final String IDENTITY_EARLY = "global_100308";
    public static final String CALL_FORWARD_HIJACKING = "global_100305";
    public static final String NO_PLAY = "NO_PLAY";

    //    public static final String PREFIX = "wav/";
    public static final String SUFFIX = ".wav";
    private static Logger logger = LoggerFactory.getLogger(PlayCodeService.class);

    public static final Pattern SPECIAL_CODE_PATTERN = Pattern.compile("^(DuDu|Mute|TTS:.*)$");//用于判断放音是否为特殊编码

    private static String processPlayCode(String[] arrCode, String audioPrefix) {
        String newCode = "";
        for (String item : arrCode) {
            String codeToAppend = "";
            if (item == null) {
                item = "";
            }
            if(item.startsWith("cmicytxzjh") || (item.startsWith("100") && item.length() == 6)) {
                codeToAppend = item;
            } else {
                if (StringUtils.isNotEmpty(audioPrefix)) {
                    codeToAppend = audioPrefix + "/" + item;
                } else {
                    codeToAppend = item;
                }
            }
            if (StringUtils.isNotEmpty(newCode)) {
                newCode = newCode + ",";
            }
            newCode = newCode + codeToAppend;
        }
        return newCode;
    }

    public static String multiPlayCode2Url(String code, int companyId, String audioPrefix, boolean isCaller, int opType, BusinessEvent businessEvent) {
        if (code == null || code.isEmpty()) {
            if (isCaller && opType == 0) {
                // if (!businessEvent.getDefaultErrorNotify().equalsIgnoreCase(ERROR_NOTICE_URL)) {
                //     return audioPrefix + "/" + businessEvent.getDefaultErrorNotify();
                // }
                return ERROR_NOTICE_URL;
            }
            return code;
        }

        logger.debug("multiPlayCode2Url code={}, audioPrefix={}", code, audioPrefix);

        if (code.contains(";")) {
            String codes[] = code.split(";");
            if (codes.length > 1) {
                String leftCode = codes[0];
                String rightCode = codes[1];
                String newLeftCode = "";
                String newRightCode = "";
                if (StringUtils.isNotEmpty(leftCode)) {
                    String[] arrLeftCode = leftCode.split(",");
                    newLeftCode = processPlayCode(arrLeftCode, audioPrefix);
                }
                if (StringUtils.isNotEmpty(rightCode)) {
                    String[] arrRightCode = rightCode.split(",");
                    newRightCode = processPlayCode(arrRightCode, audioPrefix);
                }
                return newLeftCode + ";" + newRightCode;
            } else if (codes.length == 1) {
                String leftCode = codes[0];
                String newLeftCode = "";
                if (StringUtils.isNotEmpty(leftCode)) {
                    String[] arrLeftCode = leftCode.split(",");
                    newLeftCode = processPlayCode(arrLeftCode, audioPrefix);
                }
                return newLeftCode + ";";
            } else {
                return "";
            }
        } else {
            String newCode = "";
            if (StringUtils.isNotEmpty(code)) {
                String[] arrCode = code.split(",");
                newCode = processPlayCode(arrCode, audioPrefix);
            }
            return newCode;
        }

    }

    public static void main(String[] args) {
        String code = "global_100300,user_567355,platform_wavrta";
        System.out.println(PlayCodeService.buildPlayFullPath(code.split(","),"/media",90));
    }

    private static String buildPlayFullPath(String[] arrCode, String platformFileLocation, int userId) {
        String newCode = "";
        StringBuilder sb = new StringBuilder();
        for (String item : arrCode) {
            String codeToAppend = "";
            if (item == null) {
                item = "";
            }
            if(item.startsWith("p_")){
                codeToAppend = platformFileLocation + "/" + item;
            }else if(item.startsWith("u_")){
                codeToAppend = platformFileLocation + "/" + userId +"/"+ item;
            }else if(item.startsWith("global_")){
                codeToAppend = "global/" + item;
            }else{
                codeToAppend = platformFileLocation + "/"+ item;
            }

            if (StringUtils.isNotEmpty(newCode)) {
                newCode = newCode + ",";
            }
            newCode = newCode + codeToAppend;
        }
        return newCode;
    }

    public static String convertPlayCode2Url(String code, int userId, String platformFileLocation, int direction, boolean isNoBinding, BusinessEvent businessEvent) {
        if (StringUtils.isBlank(code)) {
            if (direction == MediaConst.PLAY_DIRECTION.CALLER && isNoBinding) {
                // if (StringUtils.isNotBlank(businessEvent.getDefaultNoBindPlayCode())) {
                //     return platformFileLocation + "/" + businessEvent.getDefaultNoBindPlayCode();
                // }
                return "global/"+ERROR_NOTICE_URL;
            }
            return code;
        }

        logger.debug("convertPlayCode2Url code={}, platformFileLocation={}", code, platformFileLocation);

        if (code.contains(";")) {
            String codes[] = code.split(";");
            if (codes.length > 1) {
                String leftCode = codes[0];
                String rightCode = codes[1];
                String newLeftCode = "";
                String newRightCode = "";
                if (StringUtils.isNotEmpty(leftCode) && !SPECIAL_CODE_PATTERN.matcher(leftCode).matches()) {
                    String[] arrLeftCode = leftCode.split(",");
                    newLeftCode = buildPlayFullPath(arrLeftCode, platformFileLocation, userId);
                }else if (StringUtils.isNotEmpty(leftCode) && SPECIAL_CODE_PATTERN.matcher(leftCode).matches()){
                    newLeftCode = leftCode;
                }
                if (StringUtils.isNotEmpty(rightCode) && !SPECIAL_CODE_PATTERN.matcher(rightCode).matches()) {
                    String[] arrRightCode = rightCode.split(",");
                    newRightCode = buildPlayFullPath(arrRightCode, platformFileLocation, userId);
                } else if (StringUtils.isNotEmpty(rightCode) && SPECIAL_CODE_PATTERN.matcher(rightCode).matches()) {
                    newRightCode = rightCode;
                }
                return newLeftCode + ";" + newRightCode;
            } else if (codes.length == 1) {
                String leftCode = codes[0];
                String newLeftCode = "";
                if (StringUtils.isNotEmpty(leftCode) && !SPECIAL_CODE_PATTERN.matcher(leftCode).matches()) {
                    String[] arrLeftCode = leftCode.split(",");
                    newLeftCode = buildPlayFullPath(arrLeftCode, platformFileLocation, userId);
                } else if (StringUtils.isNotEmpty(leftCode) && SPECIAL_CODE_PATTERN.matcher(leftCode).matches()) {
                    newLeftCode = leftCode;
                }
                return newLeftCode + ";";
            } else {
                return "";
            }
        } else {
            String newCode = "";
            if (StringUtils.isNotEmpty(code)) {
                String[] arrCode = code.split(",");
                newCode = buildPlayFullPath(arrCode, platformFileLocation,userId);
            }
            return newCode;
        }

    }


    public static String playCode2Url(String code, int companyId, String audioPrefix, boolean isCaller, int opType, BusinessEvent businessEvent) {
        return multiPlayCode2Url(code, companyId, audioPrefix, isCaller, opType, businessEvent);
    }

    public static String collectionPlayCode2Url(String code, int companyId, String audioPrefix, boolean isCaller, int opType, BusinessEvent businessEvent) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        if (code.startsWith("cmicytxzjh") || (code.startsWith("100") && code.length() == 6)) {
            return code;
        }
        String url = "";
        if (StringUtils.isBlank(audioPrefix)) {
            url = code;
        } else {
            url = audioPrefix + "/" + code;
        }

        logger.debug("play url " + url);
        return url;
    }
}
