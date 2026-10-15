package com.cmic.util;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cmic.config.ConstantConf.TRANSFER_LIMIT_PREFIX_KEY;

public class TransferLimitDto {

    private static final Logger logger = LoggerFactory.getLogger(TransferLimitDto.class);

    private String type = "";

    private String value = "";

    private Pattern pattern = null;

    public IsMatch isMatch = null;

    private int pIdx = -1;

    private int pDividerIdx = -1;

    private String pMin = null;

    private String pMax = null;

    private int tIdx = -1;

    private String tNum = null;

    private int tNumEndIdx = -1;

    private int sIdx = -1;

    private int sDividerIdx = -1;

    private String sMin = null;

    private String sMax = null;

    @Override
    public String toString() {
        return type + "-" + value;
    }

    @FunctionalInterface
    public interface IsMatch {
        boolean apply(String number);
    }

    private boolean parsePTSFromNumber(String number) {
        // -1: in P
        // -2: in S
        int status = 0;
        boolean isAllDigit = true;
        for (int i = 0; i < number.length(); ++i) {
            char c = number.charAt(i);
            if (!Character.isDigit(c)) {
                isAllDigit = false;
            }
            switch (c) {
                case 'P':
                    pIdx = i;
                    status = -1;
                    break;
                case 'T':
                    tIdx = i;
                    break;
                case 'S':
                    sIdx = i;
                    status = -2;
                    break;
                case ':':
                    if (status == -1) {
                        pDividerIdx = i;
                    } else if (status == -2) {
                        sDividerIdx = i;
                    } else {
                        logger.error("[parse TransferLimitDto failed] got divider when status is {}", status);
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        if (isAllDigit) {
            // number 精确匹配
            isMatch = n -> StringUtils.equals(n, value);
            return true;
        }
        if (pIdx != -1) {
            if (pDividerIdx == -1) {
                logger.error("[parse TransferLimitDto failed] pIdx is {} but pDividerIdx is {}", pIdx, pDividerIdx);
                return false;
            }
            pMin = number.substring(pIdx + 1, pDividerIdx);
            pMax = number.substring(pDividerIdx + 1, tIdx);
        }
        if (sIdx != -1) {
            if (sDividerIdx == -1) {
                logger.error("[parse TransferLimitDto failed] sIdx is {} but sDividerIdx is {}", sIdx, sDividerIdx);
                return false;
            }
            sMin = number.substring(sIdx + 1, sDividerIdx);
            sMax = number.substring(sDividerIdx + 1, number.length());
        }
        tNumEndIdx = sIdx == -1 ? number.length() : sIdx;
        tNum = number.substring(tIdx + 1, tNumEndIdx);

        buildPatternFromPTS();
        return true;
    }

    private void buildPatternFromPTS() {
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        if (pMin != null && pMax != null) {
            sb.append("\\d{").append(pMin).append(",").append(pMax).append("}");
        }
        sb.append(tNum);
        if (sMin != null && sMax != null) {
            sb.append("\\d{").append(sMin).append(",").append(sMax).append("}");
        }
        sb.append("$");
        pattern = Pattern.compile(sb.toString());
        isMatch = new IsMatch() {
            @Override
            public boolean apply(String number) {
                Matcher matcher = pattern.matcher(number);
                return matcher.matches();
            }
        };
    }

    public static TransferLimitDto instanceOf(String type, String number) {
        TransferLimitDto result = new TransferLimitDto();
        result.type = type;
        result.value = number;

        if (StringUtils.equals(type, TRANSFER_LIMIT_PREFIX_KEY)) {
            result.isMatch = n -> n.startsWith(result.value);
            return result;
        }

        // 例如：P0:9T10086S1:2
        try {
            if (!result.parsePTSFromNumber(number)) {
                return null;
            }
        } catch (Exception e) {
            logger.warn("parse PTS from number failed", e);
            return null;
        }

        return result;
    }

}
