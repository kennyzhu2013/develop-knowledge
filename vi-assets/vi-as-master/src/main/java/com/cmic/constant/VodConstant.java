package com.cmic.constant;

public interface VodConstant {

    interface VOD {

        String PLAY_BACK_URL = "/vod/complete";
        String PLAY_URL = "/v1/vod/play";
        String STOP_URL = "/v1/vod/stop";
        String CONTROL_URL = "/v1/vod/control";

        String UPDATE_VOD_MEDIA = "/vod/change/media";

        interface ACTIVATE_SOURCE {
            int BY_REQUEST = 1;
            int BY_PARAM = 2;
        }

        /**
         * 对端手机不支持不拉起摄像头视频时的回落策略：
         * 0 - 播放视频语音
         * 1 - 尝试拉起摄像头播放视频，失败再播放视频语音
         * 2 - 直接回落语音通话
         * 3 - 尝试拉起摄像头播放视频，失败回落语音通话
         */
        interface AGGRESSIVE_VOD {
            int FALLBACK_PLAY_AUDIO = 0;
            int SENDRECV_AND_FALLBACK_PLAY_AUDIO = 1;
            int FALLBACK_AUDIO_CALL = 2;
            int SENDRECV_AND_FALLBACK_AUDIO_CALL = 3;
        }

        /*interface PLAY_MODE{
            String NO_VOD = "0";                    // 不插播（默认）
            String RESUME_CALL_AFTER_PLAY = "1";    // 插播视频后通话正常进行
            String STOP_CALL_AFTER_PLAY = "2";      // 插播视频后结束通话（视频通知类场景）
        }*/

        interface PLAY_TYPE {
            int AUDIO_VIDEO_BOTH_PLAY = 1;  // 音视频播放
            int VIDEO_ONLY = 2;             // 仅视频，通话语音透传

            int AUDIO_ONLY = 3;             // 仅播放音频
        }


        /*String YONG_TAO_VIDEO = "m=video VIDEO_PORT RTP/AVP 114 113\n" +
                "c=IN IP4 MEDIA_IP\n" +
                "b=AS:2162\n" +
                "b=RS:8000\n" +
                "b=RR:6000\n" +
                "a=rtpmap:114 H264/90000\n" +
                "a=fmtp:114 profile-level-id=42C01F;packetization-mode=1;sar-understood=16;sar-supported=1;sprop-parameter-sets=Z0LAH9oC0ChoBtChNQ==,aM4G4g==\n" +
                "a=imageattr:114 send [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176] recv [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176]\n" +
                "a=rtpmap:113 H264/90000\n" +
                "a=fmtp:113 profile-level-id=42C01F;packetization-mode=0;sar-understood=16;sar-supported=1;sprop-parameter-sets=Z0LAH9oC0ChoBtChNQ==,aM4G4g==\n" +
                "a=imageattr:113 send [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176] recv [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176]\n" +
                "a=rtcp-fb:* nack\n" +
                "a=rtcp-fb:* nack pli\n" +
                "a=rtcp-fb:* ccm fir\n" +
                "a=rtcp-fb:* ccm tmmbr\n" +
                //"a=sendrecv\n" +
                "a=sendonly\n" +
                "a=tcap:1 RTP/AVPF\n" +
                "a=pcfg:1 t=1\n" +
                "a=extmap:2 urn:3gpp:video-orientation\n";*/

        String YONG_TAO_VIDEO = "m=video VIDEO_PORT RTP/AVP PAYLOAD\n" +
                //"c=IN IP4 MEDIA_IP\n" +
                "b=AS:2162\n" +
                "b=RS:8000\n" +
                "b=RR:6000\n" +
                "a=rtpmap:PAYLOAD H264/90000\n" +
                "a=fmtp:PAYLOAD profile-level-id=42C01F;packetization-mode=1;sar-understood=16;sar-supported=1;sprop-parameter-sets=Z0LAH9oC0ChoBtChNQ==,aM4G4g==\n" +
                "a=imageattr:PAYLOAD send [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176] recv [x=720,y=1280] [x=480,y=640] [x=240,y=320] [x=144,y=176]\n" +
                "a=rtcp-fb:* nack\n" +
                "a=rtcp-fb:* nack pli\n" +
                "a=rtcp-fb:* ccm fir\n" +
                "a=rtcp-fb:* ccm tmmbr\n" +
                //"a=sendrecv\n" +
                "a=sendonly\n" +
                "a=tcap:1 RTP/AVPF\n" +
                "a=pcfg:1 t=1\n" +
                "a=extmap:2 urn:3gpp:video-orientation\n";


        interface TYPE {
            int LEFT_ONLY = 1;
            int RIGHT_ONLY = 2;
            int LEFT_RIGHT_BOTH = 3;
        }

        interface STATUS {
            int NONE = 0;                                      //初始状态
            int INIT = 1;                                      //已初始化
            int PREPARE = 2;                                   //插播准备开始
            int START = 3;                                     //已开始视频的媒体协商
            int ACTIVE = 4;                                    //视频媒体协商完成
            int SWITCH_TO_AUDIO = 5;
            int END = 6;

        }

        interface REASON_CODE {
            int INIT = 1;                                      //已初始化
            int PREPARE = 2;                                   //插播准备开始
            int START = 3;                                     //已开始视频的媒体协商
            int ACTIVE = 4;                                    //视频媒体协商完成
            int FAIL_IN_START = 5;                             //失败状态，表示协商未正式开始就失败
            int FAIL_IN_NEGOTIATION = 6;                       //失败状态，表示协商过程中失败
            int FAIL_BECUASE_OF_NOT_SUPPORT = 7;               //失败状态，表示终端不支持插播，端口返回0
            int FAIL_BECAUSE_OF_RESID_NOT_FOUND = 8;           //失败状态，表示插播文件不存在
            int END_BY_BYE = 9;                                //用户挂机结束插播
            int END_BECAUSE_OF_COMPLETE = 10;                  //插播正常结束
            int END_BY_REQUEST = 11;                           //用户在APP客户端发起的停止插播
            int REMOTE_VIDEO_CLOSE = 12;                       //用户在终端通过“切换语音通话”结束插播

            //int END_BY_FORCE = 13;                             //强制停止

            int OTHER_EXCEPTION = 99;                          //其他异常



        }

        interface CONTACT_HEADER {
            int CONTAIN_VIDEO = 1;
            int NOT_CONTAIN_VIDEO = 0;
        }

        interface SDP {
            int CONTAIN_AMRWB = 1;
            int NOT_CONTAIN_AMRWB = 0;
        }

        interface NEGOTIATION {
            int UNKNOW = 0;
            int FAIL = 1;
            int SUCC = 2;
        }

        //用于插播事件推送的状态码 VOD_RESULT_CODE
        interface VOD_EVENT_CODE {
            String SUCCESS = "0000";                   //播放完成
            String RESOURCE_NOT_FOUND = "1001";        //找不到resId对应资源
            String TERMINAL_NOT_SUPPORT_VOD = "1002";  //对端手机不支持关闭摄像头
            String NEGOTIATION_FAIL = "1003";          //媒体协商失败
            String STOP_FROM_HTTP = "1004";            //请求停止播放
            String STOP_BY_BYE = "1005";               //通话挂机
            String SWITCH_TO_AUDIO_VIA_PHONE = "1006"; //主被叫手机请求回落语音
            String RETURN_TO_PLAY_AUDIO = "1007";      //协商失败回落成语音播放
            String OTHER_EXCEPTION = "1999";           //其他异常
        }

        interface VOD_HTTP_CODE {
            String SUCCESS = "0000";                   //success
            String TERMINAL_NOT_SUPPORT_VOD = "1001";        //对端手机不支持视频插播
            String TERMINAL_NOT_SUPPORT_REQUIRED_CODE = "1002";  //对端手机不支持媒体编码格式
            String COMPANY_NOT_ALLOW_VOD = "1003";          //企业没有开通权限
            String CALL_NOT_ANSWERED = "1004";            //通话暂未接通，禁止插播
            String VOD_IS_IN_PROGRESS = "1005";               //当前已有视频正在播放，禁止操作（视频播放接口专用）
            String NO_VOD_IS_IN_PROGRESS = "1006";           //当前无视频播放，禁止操作（停止播放接口专用）
            String NEGOTIATION_IS_IN_PROGRESS = "1007";           //当前正在协商中，无法操作
            String CALL_NOT_EXIST = "1008";           // Callid不存在
            String PARAM_INVALID = "1009";           // 输入参数非法
            String OTHER_REASON = "1999";           //其他异常
        }

        interface VOD_HTTP_MSG {
            String SUCCESS = "成功";                   //播放完成
            String TERMINAL_NOT_SUPPORT_VOD = "端手机不支持视频插播";        //对端手机不支持视频插播
            String TERMINAL_NOT_SUPPORT_REQUIRED_CODE = "对端手机不支持媒体编码格式";  //对端手机不支持媒体编码格式
            String COMPANY_NOT_ALLOW_VOD = "企业没有开通权限";          //企业没有开通权限
            String CALL_NOT_ANSWERED = "通话暂未接通，禁止插播";            //通话暂未接通，禁止插播
            String VOD_IS_IN_PROGRESS = "当前已有视频正在播放，禁止操作（视频播放接口专用）";               //当前已有视频正在播放，禁止操作（视频播放接口专用）
            String NO_VOD_IS_IN_PROGRESS = "当前无视频播放，禁止操作（停止播放接口专用）";           //当前无视频播放，禁止操作（停止播放接口专用）
            String NEGOTIATION_IS_IN_PROGRESS = "当前正在协商中，无法操作";           //当前正在协商中，无法操作
            String CALL_NOT_EXIST = "Callid不存在";           // Callid不存在
            String PARAM_INVALID = "输入参数非法";           // 输入参数非法
            String OTHER_REASON = "其他异常";           //其他异常
        }

    }


    interface TRANSACTION_INVITE {
        int STARTED = 1;
        int RESPONSED = 2;
        int ACKED = 3;
        int ENDED = 3;
    }

    interface TRANSACTION_UPDATE {
        int STARTED = 1;
        int RESPONSED = 2;
    }
}
