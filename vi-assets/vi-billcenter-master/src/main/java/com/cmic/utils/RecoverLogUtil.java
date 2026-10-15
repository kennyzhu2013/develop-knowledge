package com.cmic.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoverLogUtil {

    public static final Logger logger = LoggerFactory.getLogger("recoverlog");

    public static void logRecordReushfail(String billId) {
        logger.info("[record_repush_fail] [{}]", billId);
    }
}
