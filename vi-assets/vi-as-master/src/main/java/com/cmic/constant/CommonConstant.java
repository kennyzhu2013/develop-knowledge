package com.cmic.constant;

public interface CommonConstant {

    interface OPTYPE {
        int DISCONNECT = 0;
        int CONNECT = 1;
        int COLLECT = 2;
    }

    interface PLAY_MODE {
        String NO_VOD = "0";                    // 不插播（默认）
        String RESUME_CALL_AFTER_PLAY = "1";    // 插播视频后通话正常进行
        String STOP_CALL_AFTER_PLAY = "2";      // 插播视频后结束通话（视频通知类场景）
    }

    interface SPECIAL_SECRET {
        String SECRET_125836 = "125836";
        String SECRET_125838 = "125838";
    }

    interface MEDIA{
        String PCMA = "pcma";
        String PCMU = "pcmu";

        String AMR = "amr";
        String AMR_WB = "amr-wb";
        String TELEPHONE_EVENT = "telephone-event";

    }

    interface PAYLOAD{
        String PCMA = "8";
        String PCMU = "0";
    }



}
