package com.cmic.business.businessparam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AliCallBinding extends BaseCallBinding{

    private String callInterceptPlayCode;

    /**
     * 呼叫类型
     * MASTER(A->X->B),
     * CALLED(B->X->A),
     * SMS_SENDER,
     * SMS_RECEIVER
     */
    private String subsCallType;

    /**
     * 录⾳类型，mp3/wav
     */
    private String subsRecType;

    /**
     * 接续控制描述
     */
    private String controlMsg;


    /**
     * 外部回传字段
     */
    private String outId;

    /**
     * 录⾳模式，1：仅录制通话录⾳、2：放⾳
     * 录⾳+通话录⾳
     */
    private String recordMode;

    /**
     * 主叫媒体流推送地址
     */
    private String wsAddr;

    /**
     * 被叫媒体流推送地址
     */
    private String wsAddrCalled;

    /**
     * 是否实时媒体
     */
    private boolean needRealTimeMedia;

    /**
     * 实时媒体类型: 1-彩铃; 2-通话; 3-彩铃和通话
     */
    private String rtpType;

    /**
     * 顺振超时时间
     */
    private int sequenceTimeout;


}
