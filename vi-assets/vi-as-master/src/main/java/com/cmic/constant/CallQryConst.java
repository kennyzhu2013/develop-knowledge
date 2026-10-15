package com.cmic.constant;

public interface CallQryConst {

    interface REQ{
        String INBOUND_CALLER_NO = "InboundCallerNo";
        String INBOUND_CALLEE_NO = "InboundCalleeNo";

        String REQUEST_ID = "RequestId";

        String CALL_TIME = "CallTime";

        String CALL_ID = "CallId";

    }

    interface RES{
        String CODE = "Code";
        String MESSAGE = "Message";
        String BIND_INFO_DTO = "BindInfoDTO";
        String BIND_ID = "BindID";
        String INDUSTRIAL_ID = "IndustrialID";
        String OPTYPE = "OpType";
        String OUTBOUND_CALLER_NO = "OutboundCallerNo";
        String OUTBOUND_CALLEE_NO = "OutboundCalleeNo";
        String CREATE_TIME = "CreateTime";
        String CALLER_AUDIO_PRE = "CallerAudio_Pre";
        String CALLER_AUDIO_IN = "CallerAudio_In";

        String CALLEE_AUDIO_IN = "CalleeAudio_In";
        String VIDEO_PRE = "VideoPre";
        String NEED_RECORD = "NeedRecord";
        String NEED_RING_RECORD = "NeedRingRecord";

        String RECORD_TRACE = "RecordTrace";

        String NEED_PRECISE_BILL = "NeedPreciseBill";

        String MAX_CALL_DURATION = "MaxCallDuration";

        String LAST_MINUTE_AUDIO = "LastMinuteAudio";

        String SCENARIO = "Scenario";
        String USER_DATA = "UserData";

        String BIND_DETAIL = "BindDetail";

        String BIND_MODE = "BindMode";

        String TEL_A = "TelA";

        String TEL_B = "TelB";

        String TEL_X = "TelX";

        String TEL_Y = "TelY";

        String TEL_G = "TelG";

        String EXP_TIME = "ExpireTime";

        String RECORD_FILE_TYPE = "RecordFileType";

        String SEQ_MODE = "SequenceMode";

        String SEQ_RULES = "SequenceRules";

        String SEQ_CALLS = "SequenceCalls";

        String SEQ_RULE_CALL_NO_RING = "CallNoRing";

        String SEQ_RULE_NO_RESPONSE_TIME = "NoResponseTime";

        String SEQ_RULE_CALL_FORWARD = "CallForward";

        String SEQ_RULE_BUSY_OR_REJECT = "BusyOrReject";

        String SEQ_CALLS_CALLED_NO = "SequenceCalledNo";

        String SEQ_CALLS_DISPLAY_NO = "SequenceDisplayNo";

        String SEQ_CALLS_CALLER_AUDIO_IN = "SequenceCallerAudioIn";

        String SEQ_CALLS_CALLEE_AUDIO_IN = "SequenceCalleeAudioIn";

    }

    interface ALI{
        String CONTROL_OPERATE = "ControlOperate";
        String CONTROL_MESSAGE = "ControlMsg";
        String PRODUCT_TYPE = "ProductType";
        String AUDIO = "Audio";
        String CALLER_AUDIO_PRE = "CallerAudioPre";
        String CALLEE_AUDIO_IN = "CalleeAudioIn";
        String CALLER_AUDIO_IN = "CallerAudioIn";
        String CALL_INTERCEPT_PLAY_CODE = "callInterceptPlayCode";
        String SUBS = "Subs";
        String CALL_TYPE = "CallType";
        String RECORD_TYPE = "RecType";
        String OUT_ID = "OutId";
        String RECORD_MODE = "RecordMode";
        String FAST_RECORD = "FastRecord";
        String RRDS_CONTROL = "RrdsControl";
        String WS_ADDR = "WsAddr";
        String WS_ADDR_CALLED = "WsAddrCalled";
        String NEED_REAL_TIME_MEDIA = "NeedRealTimeMedia";
        String RTY_TIME = "RtpType";
        String SEQUENCE_TIME_OUT = "SequenceTimeout";
        String SEQUENCE_CALLS = "SequenceCalls";
        String SEQUENCE_CALL_RULE = "SequenceCallRule";
        String END_CALL_IVR = "EndCallIvr";
        String SEQ_CALL_NO_PLAY_CODE = "CallNoPlayCode";
        String SEQ_CALLED_NO_PLAY_CODE = "CalledNoPlayCode";
        String SEQ_CALLED_NO_CALLER_PLAY_CODE = "CalledNoCallerPlayCode";
        String SEQ_POLLING_NO = "PollingNo";
        String SEQ_CALLED_DISPLAY_NO = "CalledDisplayNo";
        String SEQ_CALLED_NO = "CalledNo";

        String SEQ_RULE_CALL_NO_RINGING = "CallNoRinging";

        String SEQ_RULE_CALL_FORWARDED = "CallForwarded";

        String SEQ_RULE_BUSY_REJECT = "BusyReject";

        String SEQ_RULE_CALL_TIMEOUT = "CallTimeout";

        String ENDIVR_END_CALL_IVR = "endCallIvr";
        String ENDIVR_STEP1_FILE = "step1file";
        String ENDIVR_STEP2_FILE = "step2file";
        String ENDIVR_VALID_KEY = "validKey";
        String ENDIVR_WAITING_DTMF_TIME = "waitingDtmfTime";

        String ENDIVR_MAX_LOOP = "maxLoop";

        String ENDIVR_WAITING_END_CALL = "waitingEndCall";

        String ENDIVR_DIRECTION = "direction";

    }

    interface CERT{
        String CERT_BIZ_SCENE = "midBizScene";
        String CERT_MODE = "certMode";

        String CERT_BIZ_MODE = "midBizMode";

        String CERT_PRACTIONER = "practitionerNo";

        String CERT_CONSUMER = "consumerNo";
    }


}
