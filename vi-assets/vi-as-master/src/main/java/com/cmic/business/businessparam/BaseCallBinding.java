package com.cmic.business.businessparam;

import com.cmic.business.businessparam.subparam.BindingSeqceCall;
import com.cmic.business.businessparam.subparam.BindingSeqceRule;
import lombok.Data;
import lombok.ToString;

import java.util.List;

import static com.cmic.constant.CallConstant.*;

@Data
@ToString
public class BaseCallBinding {

    /**
     * http 请求响应码
     */
    protected int httpCode;

    /**
     * 响应码
     * 0000：成功
     * 1007：请求参数InboundCallerNo格式错误
     * 1012：请求参数InboundCalleeNo格式错误
     * 1020：绑定关系不存在
     * 1021：黑名单号码
     * 2001：失败
     * 9999：系统异常
     */
    protected String code = "";

    /**
     * 响应信息
     */
    protected String message = "";

    /**
     * 绑定关系ID，作为号码绑定关系唯一标识
     */
    protected String bindID = "";

    /**
     * 绑定关系对应的工业互联网标识
     */
    protected String industrialID;

    /**
     * 操作指令，指示中间号平台后续操作：
     * 0：挂断
     * 1：接通
     */
    protected int opType;

    /**
     * 呼出主叫号码。默认X号码
     */
    protected String outboundCallerNo = "";

    /**
     * 呼出被叫号码。opType=1必填
     */
    protected String outboundCalleeNo = "";

    /**
     * 绑定关系建立时间。YYYYMMDDHHMMSS格式
     */
    protected String createTime = "";

    /**
     * 超时时间。YYYYMMDDHHMMSS格式。
     */
    private String expireTime = "";

    /**
     * 呼叫接通前，向主叫的语音提示放音编码
     */
    protected String callerAudioPre = "";

    /**
     * 呼叫接通后，向主叫的语音提示放音编码
     */
    protected String callerAudioIn = "";

    /**
     * 呼叫接通后，向被叫的语音提示放音编码
     */
    protected String calleeAudioIn = "";

    /**
     * 接通呼叫前向主叫播放视频名片编码
     */
    protected String videoPre = "";

    /**
     * 用于表示业务使用方是否需要该号码绑定关系产生的所有通话录音，取值：
     * 1：需要通话录音(默认)；
     * 2：不需要通话录音。
     */
    protected int needRecord = NEED_RECORD_YES;

    /**
     * 用于表示业务使用方是否需要该号码绑定关系产生的所有振铃录音，取值：
     * 1：需要振铃录音(默认)；2：不需要振铃录音。
     */
    protected int needRingRecord = NEED_RING_RECORD_YES;

    /**
     * 用于表示业务使用方是否需要该呼叫识别精准话单（对应3.6接口的BillState字段），取值：
     * 1：不需要（默认）
     * 2：需要
     */
    protected int needPreciseBill = NEED_PRECISE_BILL_NO;

    /**
     * 录音文件声道
     * 1：主叫左声道，被叫右声道(默认)
     * 2：被叫左声道，主叫右声道
     */
    protected int recordTrace = RECORD_TRACE_CALLER_LEFT;

    /**
     * 呼叫最大通话时长限制，单位秒。呼叫时长
     * 达到最大通话时长时挂断呼叫。
     */
    protected int maxCallDuration;

    /**
     * 指定了MaxCallDuration时生效，达到通话时长前1分钟向主被叫播放提醒音。
     */
    protected String lastMinuteAudio = "";


    /**
     * 使用用途，取值：
     * 0：快递；
     * 1：外卖；
     * 2：闪送；
     * 3：跑腿；
     * 4：在线旅游；
     * 5：网约车；
     * 6：代驾；
     * 7：租车；
     * 8：家政服务；
     * 9：装修维修；
     * 10：货运；
     * 99：其他。
     */
    protected int scenario = -1;


    /**
     * 业务透传数据
     */
    protected String userData = "";


    /**
     * 录音文件类型,，若不传此字段将根据账户配置进行录音
     * 1：mp3
     * 2：wav
     */
    protected int recordFileType;


    protected int sequenceMode;
    protected List<BindingSeqceCall> seqCallList;

    /**
     * 顺振规则
     */
    private BindingSeqceRule seqRule;
    protected String streamMode = "";
    protected String streamType = "";
    protected String streamSeparate = "";
    protected String streamPriority = "";
    protected String streamRealtimeASR = "";
    protected String streamWsURL = "";
    protected String dtmfEvent = "";
    protected String dtmfSource = "";
    protected String dtmfTerminator = "#";
    protected String vodVideoPlayMode = "";
    protected String vodVideoPlayCode = "";
    protected int vodDirection;
    protected int vodMode;
    protected int vodAggressive;
    protected int vodPlayTimes;
    protected int dtmfMaxCollectWaitTime = 20;
    protected int dtmfMaxCollectNo = 1;

    public boolean isNeedRecord() {
        return this.needRecord == NEED_RECORD_YES;
    }

    public boolean isNeedPreciseBill() {
        return this.needPreciseBill == NEED_PRECISE_BILL_YES;
    }

    public static void main(String[] args) {
        BaseCallBinding baseCallBinding = new BaseCallBinding();
        System.out.println(baseCallBinding);
    }

}

