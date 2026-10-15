package com.cmic.util;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规整TransactionId的类
 * <p>
 * Author: luohuanfa
 * Date: 2022/8/3
 */
public class TransactionIdUtil {
    private static final Logger logger = LoggerFactory.getLogger(TransactionIdUtil.class);

    public static String fixZ9hg4bk(String transactionId) {
        if (StringUtils.isBlank(transactionId)) {
            return transactionId;
        }
        if (transactionId.startsWith("z9hG4bK")) {
            return transactionId;
        }
        if (isStartWithBranchMagic(transactionId)) {
            return "z9hG4bK" + transactionId.substring(7);
        } else {
            logger.error("find transaction not start with z9hG4bK, {}", transactionId);
        }
        return transactionId;
    }

    private static final String BRANCH_MAGIC_COOKIE_LOWER_CASE = "z9hg4bk";

    public static boolean isStartWithBranchMagic(String branchId) {
        return branchId.length() >= 7 && branchId.regionMatches(true, 0, BRANCH_MAGIC_COOKIE_LOWER_CASE, 0, BRANCH_MAGIC_COOKIE_LOWER_CASE.length());
    }
}
