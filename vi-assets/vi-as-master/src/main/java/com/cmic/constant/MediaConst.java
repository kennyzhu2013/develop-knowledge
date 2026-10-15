package com.cmic.constant;

public interface MediaConst {
    interface PLAY_DIRECTION{
        int CALLER = 1;  //Caller
        int CALLEE = 2;  //Callee
        int BOTH_SAME = 3;  //Both caller and callee
        int BOTH_DIFFERENT = 4;  //Diff caller and callee
    }

    interface PLAY_PERIOD{
        String EARLY = "Early";
        String INCALL = "InCall";
    }

    interface RECORD_MODE{
        int NO_RECORDING = 0; //不录放音
        int RECORDING_AUDIO = 1; //放音也录进去
        int RECORDING_VIDEO = 2; //视频也录进去
        int RECORDING_AUDIO_AND_VIDEO = 3; //音视频都录进去

    }

    interface PRIORITY{
        String LOCAL = "Local";
        String NET = "Net";
    }



}
