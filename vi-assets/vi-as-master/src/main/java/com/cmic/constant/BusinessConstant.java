package com.cmic.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class BusinessConstant {
    public static final String OPTYPE = "opType";
    public static final String AUDIO_PRE = "AudioPre";
    public static final String AUDIO_IN = "AudioIn";
    public static final String OUTBOUND_CALLEE_NO = "OutboundCalleeNo";
    public static final String OUTBOUND_CALLER_NO = "OutboundCallerNo";
    public static final String BIND_ID = "bindId";

    public static final String INDUSTRIAL_ID = "IndustrialID";


    public static final String NEED_RECORD = "needRecord";
    public static final String RECORD_TYPE = "record_type";
    public static final String NEED_RING_RECORD = "NeedRingRecord";
    public static final String CODE = "code";
    public static final String MESSAGE = "message";
    public static final String DATA = "data";
    public static final String COLLECTION_PLAY_CODE = "collectionPlayCode";
    public static final String VIDEO_PRE = "VideoPre";
    public static final String PAI = "pai";
    public static final String BIND_TEL_A = "bindTelA";
    public static final String SEQUENCE_MODE = "sequenceMode";
    public static final String IS_SEPARATE_EARLY = "isSeparateEarly";
    public static final String MAX_DURATION = "maxDuration";
    public static final String MAX_DURATION_TIME_UNIT = "maxDurationTimeUnit";
    public static final String LAST_MIN_VOICE = "lastMinVoice";
    public static final String CONTROL_MSG = "controlMsg";
    public static final String CALLED_VIDEO_PLAY = "calledVideoPlay";
    public static final String CALLED_VIDEO_PLAY_MODE = "calledVideoPlayMode";
    public static final String VOD_PLAY_CODE = "videoPlayCode";
    public static final String VOD_DIRECTION = "direction";
    public static final String VOD_MODE = "mode";
    public static final String STREAM_MODE = "streamMode";
    public static final String STREAM_TYPE = "streamType";
    public static final String STREAM_SEPARATE = "streamSeparate";
    public static final String WEBSOCKET_URL = "wsURL";
    public static final String STREAM_PRIORITY = "streamPriority";
    public static final String REALTIME_ASR = "realtimeASR";

    public static final String AGGRESSIVE_VOD = "aggressiveVod";
    public static final String PLAYTIMES_VOD = "playTimes";

    public static final String MID_BIZ_MODE = "midBizMode";
    public static final String MID_BIZ_SCENE = "midBizScene";
    public static final String CERT_MODE = "certMode";

    public static final String NEED_IDENTITY_VERIFY = "needIdentityVerify";

    /**
     * 接通后收号相关的控制参数
     */
    // 是否需要接通后dtmf收号事件上报：0-不需要（默认）1-需要
    public static final String DTMF_EVENT = "dtmfEvent";
    // dtmf收号控制参数（JSON），当且仅当dtmfEvent为1时必传
    public static final String DTMF_CONTROL = "dtmfControl";
    // 收号来源： 1-主叫侧收号 2-被叫侧收号 3-主被叫收号,用于指定向那一边收号
    public static final String DTMF_SOURCE = "dtmfSource";
    // 最大收号号间等待时长，默认20s
    public static final String MAX_COLLECTION_WAIT_TIME = "maxCollectionWaitTime";
    // 最大收号位数，默认1位，当需要按terminator结束收号时，最大收号位数需大于实际待收号位数
    public static final String MAX_COLLECTION_NO = "maxCollectionNo";
    // 收号结束符，默认为“#”
    public static final String TERMINATOR = "terminator";

    public static final String PRACTITIONER_NO = "practitionerNo";

    public static final String CONSUMER_NO = "consumerNo";

    //public static final String BIND_TIME = "bindTime";


    public static final ArrayList<String> DTMF_SOURCE_LIST = new ArrayList<>(Arrays.asList("1", "2", "3"));


    public static final int VDIEO_3GP_CHECK_STANDARD_MODE = 1;
    public static final int VDIEO_3GP_CHECK_GROUPSET_MODE = 2;

    public static final String VDIEO_3GP_CHECK_STANDARD_URL = "/midnumop/business/ring/standard/file";
    public static final String VDIEO_3GP_CHECK_GROUPSET_URL = "/midnumop/business/ring/groupset/file";

    /**
     * 通话事件类型：呼入、振铃、呼出、摘机
     */
    public enum EventType {
        /**
         * 事件枚举
         */
        CALLIN("CALLIN"), ALERTING("ALERTING"), CALLOUT("CALLOUT"), PICKUP("PICKUP");
        /**
         * 事件名称
         */
        private final String name;

        EventType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static final ArrayList<String> STREAM_MODE_LIST = new ArrayList<>(Arrays.asList("00", "10", "01", "11"));


    public static final String SELF_BIND_TIME = "selfBindTime";

    public static final String BIND_INFO = "bindInfo";

    public static final String NUMBER_A = "numberA";
    public static final String NUMBER_X = "numberX";
    public static final String NUMBER_Y = "numberY";
    public static final String NUMBER_B = "numberB";
    public static final String BIND_TIME = "bindTime";
    public static final String EXPIRE_TIME = "expireTime";
    public static final String EXTENSION_NO = "extensionNo";
    public static final String ORDER_NO = "orderNo";
    public static final HashSet<String> BIND_INFO_KEY_LIST = new HashSet<>(Arrays.asList(NUMBER_A, NUMBER_X, NUMBER_Y, NUMBER_B, BIND_TIME, EXPIRE_TIME, EXTENSION_NO));
    public static final String CALLER_DISPLAY_MATCH_NEED_IDENTITY = "needCallerDisplayMatchIdentityVerify";

}
