package com.cmic.config;

public class ConstantConf {
    /**
     * ETCD中PROXY服务名称
     */
    public static final String HTTP_SERVICE_NAME = "com.cmic.proxy";
    /**
     * 标识通话振铃事件（180响应）
     */
    public static final int CALL_STATE_RINGING = 1;
    /**
     * 标识收到200-INVITE
     */
    public static final int CALL_STATE_OK = 2;
    /**
     * 标识通话是否收到181响应
     */
    public static final int CALL_STATE_181 = 3;
    /**
     * 标识通话是否有REINVITE
     */
    public static final int CALL_STATE_REINVITE = 4;
    /**
     * 标识通话过程是否播放视频
     */
    public static final int CALL_VIDEO_PLAY = 5;
    /**
     * 标识通话收到200-INVITE后的ACK
     */
    public static final int CALL_STATE_ACK = 6;
    /**
     * 企业信息配置缓存刷新
     */
    public static final String ETCD_RELOAD_CONF_KEY = "/config/version";
    /**
     * 号码报备配置缓存刷新
     */
    public static final String ETCD_RELOAD_NUM_CONF_KEY = "/config/num/version";
    /**
     * icscf信息配置缓存刷新
     */
    public static final String ETCD_RELOAD_CHINA_MOBILE_CSCF_VERSION = "/config/china_mobile_icscf/version";
    /**
     * 号段表配置缓存刷新
     */
    public static final String ETCD_RELOAD_PHONE_SEGMENT_VERSION = "/config/phone_segment/version";
    /**
     * 录音服务名
     */
    public static final String RECORD_SERVICE_NAME = "com.cmic.vi.media";

    public static final String GO_STRATEGY_SERVICE_NAME = "com.cmic.vi.strategy";

    /**
     * 全局主叫黑名单拦截开关
     */
    public static final String ETCD_GLOBAL_CALLER_INTERCEPT_KEY = "/config/Intercept_international_caller_switch";
    /**
     * 仅需要接通后录音
     */
    public static final int MEDIA_LOGIC_FLAG_COMMON = 1;
    /**
     * 需要早期媒体，全量录音
     */
    public static final int MEDIA_LOGIC_FLAG_NEED_EARLY = 2;
    /**
     * 不需要录音
     */
    public static final int MEDIA_LOGIC_FLAG_NOT_REQUIRED = 16;
        /**
     * 支持的音频编码
     */
    public static final String SUPPORTED_CODEC = "/config/supported_codec";

    public static final String MAX_SESSION = "/config/max_sessions";

    /**
     * 是否开启异常话单分析
     */
    public static final String ETCD_ANALYSE_ABNORMAL_BILL_PREFIX = "/config/analyse_abnormal_bill/";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE1_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case1_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE2_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case2_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE3_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case3_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE4_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case4_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE5_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case5_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE6_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case6_switch";

    public static final String ETCD_ANALYSE_ABNORMAL_BILL_CASE7_SWITCH = ETCD_ANALYSE_ABNORMAL_BILL_PREFIX + "case7_switch";

    /**
     * AB限制的被叫呼叫次数（即被叫为设定的prefix时才记录频次）
     */
    public static final String CALLEE_LIMIT_NUM_PREFIX = "/config/callee_limit_num_prefix";

    public static final String AUDIT_STRATEGY_ALLOW = "/config/audit_strategy_allow";

    public static final String TRANSFER_LIMIT_NUM_KEY = "number";
    public static final String TRANSFER_LIMIT_PREFIX_KEY = "prefix";
    public static final String FORCE_TO_GD_ICSCF_CITY = "/config/force_to_gd_icscf_city";

    public static final String SIP_PROXY_SERVICE_NAME = "com.cmic.xsipproxy";
    public static final String PUBLISH_ROCKET_MQ_THREAD_POOL = "/config/publish_rocketmq_thread_pool";

    public static final String ZHEJIANG_JILING_IP = "/config/zhejiang_jiling_ip";

    public static final String CERT_SKIPPED_BY_BIND_TIME = "/config/cert_skipped_by_bind_time";

    public static final String DB_OPER_REJECT_RATIO = "/config/db_oper_reject_ratio";

    public static final String CHINAMOBILE_AREACODE = "/config/chinamobile_icscf_areacode_version";
    public static final String PHONE_SEGMENT = "/config/phone_segment_version";
    public static final String XSIPPROXY_TAG = "internal-as";

    public static final String GZH_DOMAIN_REGEX = ".*\\.gzh\\.*[a-z]*\\.chinamobile\\.com.*";

}
