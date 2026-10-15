package com.cmic.util;

import com.cmic.BillNew;
import com.cmic.business.ViConf;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.pojo.AuditResponseEntity;
import com.cmic.pojo.BillAppendReasonEnum;
import com.cmic.pojo.SequenceMode;

import com.cmic.service.PlayCodeService;
import com.cmic.state.FinishState;
import com.cmic.state.FinishType;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 呼叫鉴权限制相关
 * <p>
 * Author: luohuanfa
 * Date: 2024/2/2
 */
public class SessionLimitUtil {
    public enum CallState {
        COLLECT,
        CALL_BUILD,
        PICKUP,
        SZ
    }


    /**
     * 会话异常检查判断
     *
     * @param stage         当前协商阶段 0:收号阶段 1：普通接通前 2：接通后 3：顺振阶段
     * @param caller
     * @param callee
     * @param callParameter
     // * @param dccAuthConf
     // * @param bill
     * @return
     */
    public static boolean sessionLimitIntercept(CallState stage, String caller, String callee, CallParameter callParameter, ViConf viConf, BillNew billNew) {
        String pureCaller = PhoneNumUtil.getPure11NumberWithout0or86(caller);
        String pureCallee = PhoneNumUtil.getPure11NumberWithout0or86(callee);
        String billId = billNew.getId();
        switch (stage) {
            case CALL_BUILD:
                if (!auditLimitIntercept(pureCaller, pureCallee, callParameter, billId)) {
                    return false;
                }
                return false;
            default:

                // 呼叫次数限制
                boolean callTimesCheckPass2 = callTimesLimitIntercept(pureCaller, pureCallee, callParameter, viConf, billId);
                if (!callTimesCheckPass2) {
                    return false;
                }
                DtmfLogger.info(billId, "not call_build state, ignore auditStrategy");
                return true;
        }
    }



    // todo 串打校验？
    // private static boolean phoneTypeReject(DccAuthConf dccAuthConf, String caller, String callee, Bill bill, CallParameter callParameter) {
    //     String calledDisplayNo = callParameter.getCalledDisplayNo();
    //     String secretNum = bill.getSecretNum();
    //     if (callee.startsWith("700") && caller.startsWith("700")) {
    //         DtmfLogger.info(bill.getBillId(), "caller and callee all 700x, caller:{}, callee:{}", caller, callee);
    //         callParameter.onDisconnect("caller and callee all 700x", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
    //         callParameter.setOptypeAppendReason(BillAppendReasonEnum.DCC_SECRET_NUM_TIMES_OVERFLOW_18.getCode());
    //         return true;
    //     }
    //     if ((callee.startsWith("700") || caller.startsWith("700")) && !secretNum.equals(calledDisplayNo) && calledDisplayNo.startsWith("700")) {
    //         DtmfLogger.info(bill.getBillId(), "caller and callee one 700x but change displayNo, caller:{}, callee:{}", caller, callee);
    //         callParameter.onDisconnect("caller and callee one 700x but change displayNo", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
    //         callParameter.setOptypeAppendReason(BillAppendReasonEnum.DCC_SECRET_NUM_TIMES_OVERFLOW_18.getCode());
    //         return true;
    //     }
    //     if (dccAuthConf.rejectNonMobilePhone()) {
    //         boolean isCallerValid = caller.startsWith("700") || Pattern.matches("^1[3456789]\\d{9}", caller);
    //         boolean isCalleeValid = callee.startsWith("700") || Pattern.matches("^1[3456789]\\d{9}", callee);
    //         if (!isCallerValid || !isCalleeValid) {
    //             DtmfLogger.info(bill.getBillId(), "non mobile phone reject, caller:{}, callee:{}", caller, callee);
    //             callParameter.onDisconnect("non mobile phone reject", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
    //             callParameter.setOptypeAppendReason(BillAppendReasonEnum.CALLER_CALLEE_NON_MOBILE_PHONE_16.getCode());
    //             return true;
    //         }
    //     }
    //     return false;
    // }

    /**
     * 拦截AB呼叫次数过多的呼叫，拦截规则为被叫是否符合ETCD拦截的配置
     *
     * @param pureCaller
     * @param pureCallee
     * @return true检查成功，也就是不拦截
     * false表示检查失败，也就是拦截
     */
    private static boolean callTimesLimitIntercept(String pureCaller, String pureCallee, CallParameter callParameter, ViConf viConf, String billId) {
        try {
            List<String> calleeLimitNumPrefixList = DevHelp.getCalleeLimitNumPrefixList();
            if (calleeLimitNumPrefixList == null || calleeLimitNumPrefixList.size() == 0) {
                DtmfLogger.info(billId, "[callTimes], calleeLimitNumPrefixList size 0");
                return true;
            }
            if (calleeLimitNumPrefixList.stream().noneMatch(pureCallee::startsWith)) {
                return true;
            }
            String key = "AB-" + pureCaller + "-" + pureCallee;
            // int calleeLimitTimes = dccAuthConf.getAbCallTimesLimit();
            int calleeLimitTimes = -1;
            // int calleeLimitTTL = dccAuthConf.getAbCallTimesTTL();
            int calleeLimitTTL = -1;
            if (calleeLimitTimes == 0) {
                DtmfLogger.info(billId, "[callTimes], companyId:{}, customerId:{} , reject for calleeLimitTimes is 0, key: {}", viConf.getCompany().getId(), viConf.getCustomer().getId(), key);
                callParameter.onDisconnect("callTimes/reject for calleeLimitTimes is 0", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
                callParameter.setOptypeAppendReason(BillAppendReasonEnum.VIRTUAL_CALLEE_BLOCK_6.getCode());
                return false;
            }
            // 如果要设置的TTL为0（设置了也过期），或者次数限制为-1（无限制），都认为是放通
            if (calleeLimitTTL <= 0 || calleeLimitTimes <= -1) {
                DtmfLogger.info(billId, "[callTimes], accept for TTL or Times pass, key: {}", key);
                return true;
            }
            long abTimes = incrRedisKeyWithTTL(key, calleeLimitTTL, billId);
            if (abTimes > calleeLimitTimes) {
                DtmfLogger.info(billId, "[callTimes], ABTimes overflow, key: {}, companyId:{}, customerId:{}", key, viConf.getCompany().getId(), viConf.getCustomer().getId());
                callParameter.onDisconnect("callTimes/ABTimes overflow", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
                callParameter.setOptypeAppendReason(BillAppendReasonEnum.VIRTUAL_CALLEE_BLOCK_6.getCode());
                return false;
            }
            DtmfLogger.info(billId, "[callTimes], intercept accept, key: {}, abTimes: {}, limitTimes: {}", key, abTimes, calleeLimitTimes);
        } catch (Exception e) {
            DtmfLogger.error(billId, "[callTimes], query redis error", e);
        }
        return true;
    }

    /**
     * 终端客户最大呼叫数限制
     *
     * @return true检查成功，也就是不拦截
     * false表示检查失败，也就是拦截
     */
    private static boolean customerMaxCallingLimitCheck(CallParameter callParameter, ViConf viConf, String billId) {
        try {
            // if (!viConf.isNeedMaxCallingLimit()) {
            //     DtmfLogger.info(billId, "[callTimes], isNeedMaxCallingLimit is not opened! ");
            //     return true;
            // }
            // int maxCallingThreshold = dccAuthConf.getMaxCallingThreshold();
            int maxCallingThreshold = -1;
            //DtmfLogger.info(billId, "[callTimes], maxCallingThreshold:{} ",maxCallingThreshold);
            if (!acquireSession(billId, String.valueOf(viConf.getCustomer().getId()), maxCallingThreshold)) {
                DtmfLogger.info(billId, "[callTimes], call num bigger than:{}, appId:{}, ", maxCallingThreshold, viConf.getCustomer().getId());
                callParameter.onDisconnect("call num overflow", PlayCodeService.NO_PLAY, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
                callParameter.setOptypeAppendReason(BillAppendReasonEnum.MAX_CALL_NUM_9.getCode());
                return false;
            }
            DtmfLogger.info(billId, "max call check pass");
        } catch (Exception e) {
            DtmfLogger.error(billId, "max call check error", e);
        }
        return true;
    }


    public static boolean auditLimitIntercept(String caller, String callee, CallParameter callParameter, String billId) {
        try {
            AuditResponseEntity auditResponseEntity = callParameter.getAuditResponseEntity();
            if (auditResponseEntity == null) {
                return true;
            }
            String opType = auditResponseEntity.getOpType();
            String callNoPlayCode = auditResponseEntity.getCallNoPlayCode();
            if ("0".equals(opType)) {
                DtmfLogger.info(billId, "[auditStrategy], opType 0, decline the session");
                callParameter.onDisconnect("auditStrategy decline for opType 0", PlayCodeService.CALLEE_LIMIT_TIMES_HIJACKING, FinishState.STRATEGY_HIJACKING, FinishType.SYSTEM_STOP);
                if (StringUtils.isNotBlank(auditResponseEntity.getAppendReason())) {
                    callParameter.setOptypeAppendReason(Integer.parseInt(auditResponseEntity.getAppendReason()));
                } else {
                    callParameter.setOptypeAppendReason(BillAppendReasonEnum.CALL_FREQUENCY_BLOCK_5.getCode());
                }
                callParameter.setCallerPlayUrl(callNoPlayCode);
                return false;
            }
            if (StringUtils.isNotBlank(auditResponseEntity.getAppendReason())) {
                callParameter.setOptypeAppendReason(Integer.parseInt(auditResponseEntity.getAppendReason()));
            }
            String calledPlayCode = auditResponseEntity.getCalledPlayCode();
            boolean auditChangePlayCode = false;
            // 如果返回为空，暂时先不设置，表示策略运营平台认为通话不用修正
            if (StringUtils.isNotBlank(calledPlayCode)) {
                callParameter.setCalledPlayUrl(calledPlayCode);
                auditChangePlayCode = true;
            }
            // 如果返回为空，暂时先不设置，表示策略运营平台认为通话不用修正
            if (StringUtils.isNotBlank(callNoPlayCode)) {
                callParameter.setCallerPlayUrl(callNoPlayCode);
                auditChangePlayCode = true;
            }
            if (auditChangePlayCode) {
                DtmfLogger.info(billId, "[auditStrategy], set called calledPlayCode:{}, callNoPlayCode:{}", calledPlayCode, callNoPlayCode);
            }
        } catch (Exception e) {
            DtmfLogger.error(billId, "[auditStrategy], error happened", e);
        }
        return true;
    }

    private static long incrRedisKeyWithTTL(String key, long keyTTL, String billId) throws Exception {
        return JedisClientPool.execute(jedis -> {
            long incrValue = jedis.incr(key);
            if (incrValue == 1L) {
                DtmfLogger.info(billId, "[callTimes], expire key:{} TTL:{}", key, keyTTL);
                jedis.expire(key, keyTTL);
            }
            if (incrValue == 10L || incrValue == 15L) { // 防止执行了incr后就报错退出导致redis中键值永不过期，应该运气没那么差1和10、15的时候都报错了
                DtmfLogger.info(billId, "[callTimes], key:{} incrValue 10/15 but no TTL?", key);
                long ttl = jedis.ttl(key);
                if (ttl < 0) {
                    jedis.expire(key, keyTTL);
                }
            }
            if (incrValue > 999L) { // 正常不应该出现999次的键值吧，打个日志记录下
                DtmfLogger.error(billId, "[callTimes], incrValue:{} too large, check it out", incrValue);
            }
            return incrValue;
        });
    }


    private static final String SESSION_DETAIL_KEY = "max_call_limit_";

    public static boolean acquireSession(String sessionId, String appendKey, long maxSession) throws Exception {
        return JedisClientPool.execute(jedis -> {
            if (maxSession == -1) {
                return true;
            }
            if (maxSession == 0) {
                return false;
            }
            // 获取当前会话数
            String sessionKey = getCallKey(appendKey);
            long currentSessions = jedis.hlen(sessionKey);
            DtmfLogger.info(sessionId, "Current sessions 1: {},maxSession:{}", currentSessions, maxSession);
            if (currentSessions >= maxSession) {
                // 清理过期会话后再次尝试
                cleanupExpiredSessions(sessionKey);
                currentSessions = jedis.hlen(sessionKey);
                DtmfLogger.info(sessionId, "Current sessions 2: {},maxSession:{}", currentSessions, maxSession);
                if (currentSessions >= maxSession) {
                    DtmfLogger.info(sessionId, "Session limit reached: {}", currentSessions);
                    return false;
                }
            }
            // 存储会话时间
            String sessionInfo = System.currentTimeMillis() + "";
            jedis.hset(sessionKey, sessionId, sessionInfo);
            DtmfLogger.info(sessionId, "Session acquired successfully");
            return true;
        });
    }

    public static void releaseSession(String sessionKey, String sessionId) throws Exception {
        JedisClientPool.execute(jedis -> {
            jedis.hdel(sessionKey, sessionId);
            DtmfLogger.info(sessionId, "{}, Session released", sessionKey);
            return null;
        });
    }

    public static String getCallKey(String customId) {
        return SESSION_DETAIL_KEY + customId;
    }

    private static void cleanupExpiredSessions(String sessionKey) throws Exception {
        JedisClientPool.execute(jedis -> {
            long currentTime = System.currentTimeMillis();
            long sessionTimeout = 7200000; // 2小时超时
            // 获取所有会话详情
            Map<String, String> allSessions = jedis.hgetAll(sessionKey);
            for (Map.Entry<String, String> entry : allSessions.entrySet()) {
                String billId = entry.getKey();
                String sessionTime = entry.getValue();
                long timestamp = Long.parseLong(sessionTime);
                if (currentTime - timestamp > sessionTimeout) {
                    // 删除过期会话
                    releaseSession(sessionKey, billId);
                    DtmfLogger.info(billId, "Expired session removed");
                }
            }
            return null;
        });
    }


    public static boolean isInAllowTimeWindow(String billId, String allowTimeWindow) {
        if (StringUtils.isBlank(allowTimeWindow)) {
            return true;
        }
        try {
            String[] timeWindows = allowTimeWindow.split(",");
            for (String timeWindow : timeWindows) {
                String[] startEnd = timeWindow.split("-");
                String[] start = startEnd[0].split(":");
                String[] end = startEnd[1].split(":");
                if (TimePeriodUtil.isNowInPeriod(Integer.parseInt(start[0].trim()), Integer.parseInt(start[1].trim()), Integer.parseInt(end[0].trim()), Integer.parseInt(end[1].trim()))) {
                    return true;
                }
            }
        } catch (Exception e) {
            DtmfLogger.error(billId, "[isInAllowTimeWindow], timeWindows:{} format invalid!", allowTimeWindow, e);
            return true;
        }
        return false;
    }


    public static boolean isPhoneNumber(String number) {
        return StringUtils.isNotBlank(number) && number.matches("^1[3456789]\\d{9}");
    }
}
