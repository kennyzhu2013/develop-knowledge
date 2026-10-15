package com.cmic.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * https://www.startelecom.ca/resources/q-850-cause-codes/
 */
public class FinishState {

    private static final Logger logger = LoggerFactory.getLogger(FinishState.class);
    /**
     * 被叫超时拒接的时间定义
     */
    public static final int OUT_TIME_DEFINCE = 42 * 1000;
    /**
     * 0:需要语音识别
     */
    public static final int NEED_ASR = 0;
    /**
     * 1:正常通话-主叫挂机
     */
    public static final int LEFT_LEG_BYE = 1;
    /**
     * 2:正常通话-被叫挂机
     */
    public static final int RIGHT_LEG_BYE = 2;
    /**
     * 3:主叫放弃
     */
    public static final int LEFT_LEG_CANCEL = 3;
    /**
     * 4:被叫无应答
     */
    public static final int RIGHT_LEG_RESPDND = 4;
    /**
     * 5:被叫忙
     */
    public static final int RIGHT_LEG_BUSY = 5;
    /**
     * 6:被叫不可及
     */
    public static final int RIGHT_LEG_UNACCESSED = 6;
    /**
     * 7:呼叫路由失败
     */
    public static final int RIGHT_LEG_ROUTING_FAIL = 7;
    /**
     * 8：呼叫无绑定
     */
    public static final int CALL_NO_BIND = 8;
    /**
     * 9：绑定信息查询返回拦截
     */
    public static final int BIND_QUERY_RETURN_INTERCEPT = 9;
    /**
     * 10：被叫关机
     */
    public static final int RIGHT_LEG_POWER_OFF = 10;
    /**
     * 11：被叫停机
     */
    public static final int RIGHT_LEG_DOWN_TIME = 11;
    /**
     * 12：被叫拒接
     */
    public static final int RIGHT_LEG_REJECT = 12;
    /**
     * 13：被叫空号
     */
    public static final int RIGHT_LEG_NO_EXITS = 13;
    /**
     * 14：呼叫转移
     */
    public static final int SECRETARY = 14;
    /**
     * 15：黑名单拦截
     */
    public static final int BLACK_LIST_INTERCEPT = 15;
    /**
     * 16:绑定信息返回参数异常
     */
    public static final int BIND_QUERY_INFO_INVALID = 16;
    /**
     * 16：接口返回失败
     */
    public static final int HTTP_ERROR = 16;
    /**
     * 17：绑定信息查询超时
     */
    public static final int BIND_QUERY_TIME_OUT = 17;
    /**
     * 18：绑定信息查询返回失败
     */
    public static final int BIND_QUERY_RETURN_FAIL = 18;
    /**
     * 18：http请求失败
     */
    public static final int HTTP_FAIL = 18;
    /**
     * 19：controller 超时，系统session异常。归入会话超时
     */
    public static final int CONTROLLER_OUT_TIME = 19;
    /**
     * 19：会话超时，系统拦截
     */
    public static final int SESSION_TIME_OUT = 19;
    /**
     * 20：号码拨测
     */
    public static final int NUM_CHECK = 20;
    /**
     * 21：无法识别的状态
     */
    public static final int UNRECOGNIZED_STATE = 21;
    /**
     * 22：呼叫策略拦截
     */
    public static final int STRATEGY_HIJACKING = 22;
    /**
     * 23：实名策略未通过
     */
    public static final int IDENTITY_UNVERIFY = 23;
    /**
     * 24：工业互联网标识检验失败
     */
    public static final int INDUSTRIAL_ID_CHECK_FAIL = 24;
    /**
     * 99:大网会话已释放，暂时归入99其他
     */
    public static final int CALL_ALREADY_RELEASE = 99;
    /**
     * 99：系统维护强制中断，暂时归入99其他
     */
    public static final int SYSTEM_RESTART = 99;
    /**
     * 99：其他
     */
    public static final int OTHER = 99;

    private static HashMap<Integer, Integer> CAUSE_FINISH_STATE = new HashMap<>();

    static {
        CAUSE_FINISH_STATE.put(404, RIGHT_LEG_NO_EXITS);
        CAUSE_FINISH_STATE.put(486, RIGHT_LEG_BUSY);
        CAUSE_FINISH_STATE.put(408, RIGHT_LEG_RESPDND);
        //487_Reason: SIP;cause=487
        CAUSE_FINISH_STATE.put(487, RIGHT_LEG_UNACCESSED);
        CAUSE_FINISH_STATE.put(600, RIGHT_LEG_REJECT);
        // 14:00:12 主叫 15210437238 中间号15202150583 被叫17835422639 ，被叫正在通话中，被叫挂机，主叫等待自动挂机，返回结果码12，有问题
        //返回603 应该修改为被叫拒接
        CAUSE_FINISH_STATE.put(603, RIGHT_LEG_REJECT);
        CAUSE_FINISH_STATE.put(503, RIGHT_LEG_POWER_OFF);
        CAUSE_FINISH_STATE.put(480, RIGHT_LEG_POWER_OFF);
        //返回481归为新话单状态
        CAUSE_FINISH_STATE.put(481, CALL_ALREADY_RELEASE);
        // 403_Reason: Q.850;cause=21
//            ,Reason: SIP;cause=403 测试多个为停机
        CAUSE_FINISH_STATE.put(21, RIGHT_LEG_DOWN_TIME);
//        487_Reason: Q.850;cause=31;text="Normal, unspecified"
//            ,Reason: SIP;cause=480,// 被叫正在通话中，没有理会中间号呼叫，主叫一直等待直到自动挂机
//        CAUSE_FINISH_STATE.put(31,RIGHT_LEG_POWER_OFF);
        CAUSE_FINISH_STATE.put(20, RIGHT_LEG_POWER_OFF);
        CAUSE_FINISH_STATE.put(18, RIGHT_LEG_UNACCESSED);
        //需要根据被叫提示音进行语音识别
        CAUSE_FINISH_STATE.put(4, NEED_ASR);
        CAUSE_FINISH_STATE.put(1, RIGHT_LEG_NO_EXITS);
        CAUSE_FINISH_STATE.put(2, RIGHT_LEG_NO_EXITS);
        CAUSE_FINISH_STATE.put(3, RIGHT_LEG_NO_EXITS);
        CAUSE_FINISH_STATE.put(28, RIGHT_LEG_NO_EXITS);
        CAUSE_FINISH_STATE.put(41, RIGHT_LEG_UNACCESSED);
        CAUSE_FINISH_STATE.put(34, RIGHT_LEG_ROUTING_FAIL);
        CAUSE_FINISH_STATE.put(44, RIGHT_LEG_ROUTING_FAIL);
        CAUSE_FINISH_STATE.put(17, RIGHT_LEG_BUSY);
        CAUSE_FINISH_STATE.put(19, RIGHT_LEG_RESPDND);
        CAUSE_FINISH_STATE.put(102, RIGHT_LEG_RESPDND);
        //偶发性无法接通
        CAUSE_FINISH_STATE.put(127, RIGHT_LEG_UNACCESSED);

    }


    public static int[] getFinishStateByLast180Msg(long lastSend180MsgTimestamp) {
        long now = System.currentTimeMillis();
        if (lastSend180MsgTimestamp == 0) {
            return new int[]{FinishType.RIGHT_LEG_STOP, NEED_ASR};
        } else if (now - lastSend180MsgTimestamp < OUT_TIME_DEFINCE) {
            return new int[]{FinishType.LEFT_LEG_STOP, LEFT_LEG_CANCEL};
        } else {
            return new int[]{FinishType.RIGHT_LEG_STOP, RIGHT_LEG_RESPDND};
        }
    }

    public static int[] getFinish18xStateByLast180Msg(long lastSend180MsgTimestamp) {
        long now = System.currentTimeMillis();
        if (lastSend180MsgTimestamp == 0) {
            return new int[]{FinishType.RIGHT_LEG_STOP, NEED_ASR};
        } else if (now - lastSend180MsgTimestamp < OUT_TIME_DEFINCE) {
            return new int[]{FinishType.RIGHT_LEG_STOP, RIGHT_LEG_REJECT};
        } else {
            return new int[]{FinishType.RIGHT_LEG_STOP, RIGHT_LEG_RESPDND};
        }
    }

    public static int causeToFinishState(int cause, boolean hasSend180Msg, int sipCause) {

        //发送180消息之后，用户拒接同样返回486，这里需要和用户忙分开
        //15210060594 180之后挂断，然后出现487里面的cause=31,这里应该
        boolean isReject = hasSend180Msg && (cause == 486 || cause == 17 || cause == 21 || cause == 31);
        // boolean isReject = hasSend180Msg;
        if (isReject) {
            return RIGHT_LEG_REJECT;
        }
        // 183_Reason: Q.850;cause=18;text="No user responding"
        //,Reason: SIP;cause=410;text="Gone"

        if (cause == 18 && sipCause == 410) {
            return RIGHT_LEG_DOWN_TIME;
        }
//        603_Reason: Q.850;cause=21
//            ,Reason: SIP;cause=603
        // 被叫正在通话中，没有理会中间号呼叫，主叫一直等待直到自动挂机
        if (cause == 21 && sipCause == 603) {
            //如果没有180，则视为被叫忙，否则视为拒接
            if (!hasSend180Msg) {
                return RIGHT_LEG_BUSY;
            }
            return RIGHT_LEG_REJECT;
        }
        Integer finishState = CAUSE_FINISH_STATE.get(cause);
        if (finishState == null) {
            finishState = CAUSE_FINISH_STATE.get(sipCause);
        }
        if (finishState == null) {
            logger.warn("can not find  finish state by cause ,and cause is :  " + cause);
            finishState = NEED_ASR;
        } else {
            return finishState;
        }
        if (cause >= 500 && cause < 600) {
            return RIGHT_LEG_ROUTING_FAIL;
        }
        return finishState;
    }

    public static HashMap<Integer, Integer> getCauseFinishState() {
        return CAUSE_FINISH_STATE;
    }
}

