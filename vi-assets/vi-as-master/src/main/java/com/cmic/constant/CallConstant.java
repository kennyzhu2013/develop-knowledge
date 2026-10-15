package com.cmic.constant;


public interface CallConstant {


    /**
     * 录音文件声道
     * 主叫左声道，被叫右声道
     */
    int RECORD_TRACE_CALLER_LEFT = 1;

    /**
     * 录音文件声道
     * 主叫右声道，被叫左声道
     */
    int RECORD_TRACE_CALLER_RIGHT = 2;


    /**
     * 是否需要通话录音
     * 需要通话录音
     */
    int NEED_RECORD_YES = 1;

    /**
     * 是否需要通话录音
     * 不需要通话录音
     */
    int NEED_RECORD_NO = 2;


    /**
     * 是否需要振铃录音
     * 需要振铃录音
     */
    int NEED_RING_RECORD_YES = 1;

    /**
     * 是否需要振铃录音
     * 不需要振铃录音
     */
    int NEED_RING_RECORD_NO = 2;


    /**
     * 需要识别精准话单
     */
    int NEED_PRECISE_BILL_YES = 2;

    /**
     * 不需要识别精准话单
     */
    int NEED_PRECISE_BILL_NO = 1;

    int RECORD_FILE_TYPE_MP3 = 1;

    int RECORD_FILE_TYPE_WAV = 2;

    int OPTYPE_CALLOUT = 1;
    int OPTYPE_INTERCEPT = 0;




    interface RING_VIDEO {
        //双平台：被叫视频彩铃透传，指定了ringVideoCode无效：0-拦截 1-透传（默认）
        String TRANSMIT = "1";
        String INTERCEPT = "0";
    }


    interface VIDEO_CALL {
        //双平台：是否允许视频通话： 0 - 不允许（默认），1 - 允许
        String ALLOW = "1";
        String INTERCEPT = "0";
    }

    interface RECORD {

        //双平台：是否录制放音媒体：0 - 不录制（默认），1 - 录制
        String DONT_RECORD_PLAY_AUDIO = "0";
        String RECORD_PLAY_AUDIO = "1";

        //双平台： 录音优先级：0 - 通话优先（默认），1 - 录音优先，无法录音时中断通话
        String RECORD_PREFERRED = "1";
        String CALL_PREFERRED = "0";
    }

    interface PLAY_AUDIO_RECORD {
        String NO_RECORD = "00"; //不录制（默认）
        String RECORD_CALLER = "01"; //录制主叫侧放音，被叫侧补静音
        String RECORD_CALLEE = "10"; //录制被叫侧放音，主叫侧补静音
        String RECORD_BOTH = "11"; //录制主被叫两侧放音
    }



}
