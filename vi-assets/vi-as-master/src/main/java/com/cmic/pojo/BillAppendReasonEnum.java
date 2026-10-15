package com.cmic.pojo;

public enum BillAppendReasonEnum {

    CALL_FORWARD_BLOCK_1(1, "呼转拦截通话"),
    CALLER_BLACKLIST_BLOCK_2(2, "主叫黑名单拦截通话"),
    CALLEE_BLACKLIST_BLOCK_3(3, "被叫黑名单拦截通话"),
    CALLER_NOT_WHITELIST_BLOCK_4(4, "主叫非白名单拦截通话"),
    CALL_FREQUENCY_BLOCK_5(5, "呼叫频次异常管控"),
    VIRTUAL_CALLEE_BLOCK_6(6, "虚商被叫拦截通话"),
    RED_LIST_CONTROL_7(7, "红名单管控"),
    STRATEGY_MODIFY_8(8, "策略修改"),
    MAX_CALL_NUM_9(9, "最大会话数拦截"),
    ZHEJIANG_JILING_INTERCEPT_10(10, "浙江机伶拦截"),
    IMS_FREQUENCY_INTERCEPT_11(11, "大网高频拦截"),
    IMS_FZ_INTERCEPT_12(12, "大网反诈拦截"),
    ALLOW_TIME_WINDOW_INTERCEPT_13(13, "呼叫时间段拦截"),
    IDENTITY_VERIFY_FAIL_14(14, "实名校验拦截"),
    COMPANY_OR_CUSTOMER_INACTIVE_15(15, "企业账户无效"),
    CALLER_CALLEE_NON_MOBILE_PHONE_16(16, "非手机号拦截"),
    CALL_QUERY_PARAM_INVALID_17(17, "转接查询参数非法拦截"),
    DCC_SECRET_NUM_TIMES_OVERFLOW_18(18, "中间号串打次数过多"),
    CALLEE_GREEN_LIST_BLOCK_19(19, "被叫绿名单拦截通话"),
    CUSTOMER_MAX_CALL_COUNT(20, "企业最大呼叫数管控"),
    OUT_OF_AREA_CALL_LIMIT(21, "异地外呼超限管控"),
    BLIND_CALL_RESTRICT(22, "超业务范围-盲呼外呼管控"),
    SHORT_DURATION_RESTRICT(23, "超业务范围-短秒率外呼管控"),
    LOW_CONNECT_RATE_RESTRICT(24, "超业务范围-接通率外呼管控"),
    LONG_DURATION_CALL_LIMIT(25, "长通话管控");


    private int code;
    private String desc;

    BillAppendReasonEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
