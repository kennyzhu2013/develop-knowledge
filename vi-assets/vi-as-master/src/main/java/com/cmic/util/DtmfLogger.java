package com.cmic.util;


import com.dcc.config.util.BaseLogger;

/**
 * Author: luohuanfa
 * Date: 2022/10/13
 */
public class DtmfLogger {
    //public static final Logger logger = LoggerFactory.getLogger(DtmfLogger.class);

    /************===========================billId Logger start===================================************/
    public static void info(String billId, String format, Object... arguments) {
        BaseLogger.info(billId, format, arguments);
        /*if (logger.isInfoEnabled()) {
            logger.info("[{}],[xdtmf] " + format, billId, arguments);
        }*/
    }

    public static void debug(String billId, String format, Object... arguments) {
        BaseLogger.debug(billId, format, arguments);
        /*if (logger.isDebugEnabled()) {
            logger.debug("[{}],[xdtmf] " + format, billId, arguments);
        }*/
    }

    public static void error(String billId, String format, Object... arguments) {
        BaseLogger.error(billId, format, arguments);
        /*if (logger.isErrorEnabled()) {
            logger.error("[{}],[xdtmf] " + format, billId, arguments);
        }*/
    }

    public static void debug(String billId, String msg, Throwable t) {
        BaseLogger.debug(billId, msg, t);
        /*if (logger.isDebugEnabled()) {
            logger.debug("[{}],[xdtmf] " + msg, billId, t);
        }*/
    }

    public static void info(String billId, String msg, Throwable t) {
        BaseLogger.info(billId, msg, t);
        /*if (logger.isInfoEnabled()) {
            logger.info("[{}],[xdtmf] " + msg, billId, t);
        }*/
    }

    public static void error(String billId, String msg, Throwable t) {
        BaseLogger.error(billId, msg, t);
        /*if (logger.isErrorEnabled()) {
            logger.error("[{}],[xdtmf] " + msg, billId, t);
        }*/
    }

    public static void warn(String billId, String msg, Throwable t) {
        BaseLogger.warn(billId, msg, t);
        /*if(logger.isWarnEnabled()){
            logger.warn("[{}],[xdtmf] " + msg, billId, t);
        }*/
    }

    public static void warn(String billId, String format, Object... arguments) {
        BaseLogger.warn(billId, format, arguments);
        /*if(logger.isWarnEnabled()){
            logger.warn("[{}],[xdtmf] " + format, billId, arguments);
        }*/
    }


    /************===========================billId Logger end===================================************/

}
