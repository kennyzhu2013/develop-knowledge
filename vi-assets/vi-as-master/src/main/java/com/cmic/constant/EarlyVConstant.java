package com.cmic.constant;

public interface EarlyVConstant {

    String VSDP = "m=video VIDEO_PORT RTP/AVP " + 114 + "\n" +
            "a=content:g.3gpp.cat\n" +
            "a=rtpmap:" + 114 + " H264/90000\n" +
            "a=fmtp:" + 114 + " max-br=960;profile-level-id=42C01E;packetization-mode=1;sprop-parameter-sets=Z0LAHtkAoD2hAAADAAEAAAMAPA8WLkg=,aMuAjLI=\n" +
            "a=framerate:30\n" +
            "a=framesize:114 640-480\n" +
            "a=sendonly\n" +
            "a=curr:qos remote none\n" +
            "a=des:qos optional remote recv\n" +
            "a=conf:qos remote recv\n" +
            "a=curr:qos local send\n" +
            "a=des:qos optional local send\n";

    String CLOSE_VIDEO_SDP = "m=video 0 RTP/AVP " + 114 + "\n";

    interface STATUS {
        int NONE = 0;                                      //初始状态
        int INIT = 1;                                      //已初始化
        int VIDEO_PREPARE = 2;                                   //插播准备开始
        int VIDEO_PLAYING = 3;                                     //已开始视频的媒体协商
        int VOICE_PREPARE = 4;                                    //视频媒体协商完成
        int END = 5;

    }

    interface RETURN_CODE {
        String SUCCESS = "0000";
        String NEGOTIATION_IN_PROGRESS = "1007";           //当前正在协商中，无法操作
        String OTHER_REASON = "1999";           //其他异常
    }

    interface RETURN_MSG {
        String SUCCESS = "成功";
        String NEGOTIATION_IN_PROGRESS = "当前正在协商中，无法操作";           //当前正在协商中，无法操作
        String OTHER_REASON = "其他异常";           //其他异常

    }

}
