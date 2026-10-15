package com.cmic.business.businessparam;

import com.cmic.constant.MediaConst;
import com.cmic.pojo.AuditResponseEntity;
import com.cmic.pojo.IdentityVerifyResponseEntity;
import com.cmic.pojo.SequenceCallsList;
import com.cmic.state.TransferStateJudge;
import com.cmic.util.AnalyseLog;
import com.cmic.constant.CallConstant;
import com.dcc.config.model.BaseProdNum;
import lombok.Data;
import lombok.ToString;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.cmic.state.FinishType.SYSTEM_STOP;

@Data
@ToString
public class CallParameter {

    private boolean isNeedRecord;
    private String callerPlayUrl;
    private int opType;
    private int certOpType = 1;
    private String calledPlayUrl;
    private String calledDisplayNo;
    private String bindId;
    private JSONObject recordParams;
    private int doParameterFinishState;
    private Integer doParameterFinishType;
    private String doParameterFinishStateErrorMsg;
    private String paiDisplayNo;
    //最大通话时长
    private int maxDuration;
    //最后一分钟时放音
    private String lastMinVoice;

    //是否需要全链路录音
    private boolean isNeedEarly;
    //录音格式
    private String suffix;
    //用于收完号后提醒主叫开始接续的提示音
    private String collectionPlayCode = "";

    private boolean downPlayVoice = false;

    private boolean canPassVideo = false;

    private boolean isDownCallQuery = false;

    private boolean isMediaChannelReversed;

    private BaseProdNum prodNum;

    //视频文件编码
    private String videoFile;

    private boolean isNeedTransferSecretary;

    private SequenceCallsList scl;
    //顺振模式
    private int sequenceMode;

    //双平台：被叫视频彩铃透传，指定了ringVideoCode无效：0-拦截 1-透传（默认）
    private String ringVideoTransmission = CallConstant.RING_VIDEO.TRANSMIT;

    //双平台：是否允许视频通话： 0 - 不允许（默认），1 - 允许
    private String allowVideoCall = CallConstant.VIDEO_CALL.INTERCEPT;

    //双平台：未接呼转号码，当识别被叫未能接通时，将呼叫呼转到该号码
    private String callTransferNo;

    //双平台：是否录制放音媒体：0 - 不录制（默认），1 - 录制
    private String playAudioRecord = CallConstant.RECORD.DONT_RECORD_PLAY_AUDIO;

    //双平台： 录音优先级：0 - 通话优先（默认），1 - 录音优先，无法录音时中断通话
    private String recordPriority = CallConstant.RECORD.CALL_PREFERRED;

    //双平台：最大收号等待时长，默认20s
    private int maxCollectionWaitTime;

    //双平台： 状态ASR开关（针对呼叫等待等信令可能识别错误的状态，叠加早期媒体asr判断）: 0 - 关闭（默认），1 - 打开
    private int asrlevel = 0;

    //双平台： 是否允许被叫呼转 0 - 拦截被叫呼转 1 - 允许被叫呼转（默认）
    private String allowCalleeForwarding = "1";

    String[] supportedCodec;

    //private PluginModeAppendix pluginModeAppendix;
    //是否需要声道反转
    private boolean isReverseLeg;

    //不同声道的腿是否需要独立存放
    private boolean isSeparateLeg;

    private boolean isSeparateEarly;

    //最大收号长度
    private int maxCollectNumLength;

    //最小收号长度
    private int minCollectNumLength;

    //收号最大等待时间，客户不输终止符会用到
    private int collectWaitTime;

    private int collectIntervalWaitTime;

    private boolean isRetryCallQuery = false;

    private String calledVideoPlayMode;           // 接通后视频插播模式: 0-不插播（默认）; 1-插播视频后通话正常进行; 2-插播视频后结束通话（视频通知类场景）
    private VodParam vodParam;
    private boolean isNeedVideoInsert;
    private boolean isNeedVideoRing;
    private int videoRingType;

    //智能代接配置（默认0）：0-不需要 1-需要代接不需要话单 2-需要代接需要话单
    //private boolean needAIAnsweringAndNoBill = false;

    private int aiAnsweringType = 0;

    private AuditResponseEntity auditResponseEntity;
    private IdentityVerifyResponseEntity identityVerifyResponseEntity;

    private AnalyseLog firstInviteAnalyzeLog = new AnalyseLog();
    private volatile boolean isCallerShouldIdSubmit = false;

    /**
     * 咪咕视频彩铃的文件Map，用于记录发送给media的文件列表
     */
    private Map<String, String> video3gpFileMap;

    /**
     * 咪咕视频彩铃的记录Map，用于填写话单信息
     */
    private Map<String, String> video3gpRecordMap;
    private boolean isNeedVideoInsertStatus;

    private boolean isNeedDtmfTransfer;

    private boolean hasStartMaxCallController = false;

    private String forwardCallee;

    private boolean isToCallForwardCallee = false;

    private boolean forceContactWithVideo = false;

    private String bindTime;

    private AtomicBoolean hasStartTerminated = new AtomicBoolean(false);

    private TransferStateJudge transferStateJudge;

    private boolean useSDP = false;

    //默认是分钟
    private TimeUnit maxDurationTimeUnit = TimeUnit.MINUTES;

    private String bCallHistory = "0";

    private String incomeForwardNo;

    // 标识
    private boolean isRouteContainZte = false;

    private Integer optypeAppendReason = 0;

    private boolean isNeedPickUpDtmfEvent = false;

    private boolean callNeedHWNumCheck;
    private String bindInfo;

    private String midBizMode;

    private String practitionerNo;
    private String consumerNo;
    private boolean identityAudioRemindFlag;

    private int callTimeInspectThreshold;

    private volatile boolean isInsertDbSucc = false;

    private volatile int dbAndExecutorIndex = -1;

    private boolean needIdentityCallBackEvent;

    // 0: none 1: realtime Stream only 2: realtime ASR only
    private int streamBusinessType;
    private boolean isRealTimeStream = false;

    private Timestamp streamStartTime;

    // streamSource 1:caller 2:callee 3:both
    private int streamSource = 0;

    private String streamWebSocketURL;

    // 0:close 1:early stream only 2:pickup stream only 3:both
    private int streamType;

    private JSONObject streamExtParam;

    private boolean isStreamDurationWritten;

    private String origCallerX1X2;

    private boolean isSkipIdentityVerify4MidNum = false;

    private int calledPlayDirection = MediaConst.PLAY_DIRECTION.BOTH_SAME;

    private int recordMode = MediaConst.RECORD_MODE.NO_RECORDING;

    //录音推送方式
    //0:http 1:sftp(默认0)
    private int recordPullType;

    //各模式特殊的录音参数
    private HashMap<String, String> specialRecordParams;

    private String hlyNumber;

    private String industrialID;

    // 标识呼叫是否支支持G729
    private boolean isOnlySupportG729 = false;

    public void addSpecialRecordParams(String key, String value) {
        if (specialRecordParams == null) {
            specialRecordParams = new HashMap<>();
        }
        specialRecordParams.put(key, value);
    }

    public void setStreamStartTime(Long streamStartTime) {
        if (streamStartTime == null) {
            this.streamStartTime = null;
        } else {
            this.streamStartTime = new Timestamp(streamStartTime);
        }
    }

    public CallParameter(boolean isNeedRecord) {
        this.isNeedRecord = isNeedRecord;
    }

    public CallParameter(boolean isNeedRecord, String callerPlayUrl, int opType, String calledPlayUrl, String calledDisplayNo, String bindId) {
        this.isNeedRecord = isNeedRecord;
        this.callerPlayUrl = callerPlayUrl;
        this.opType = opType;
        this.calledPlayUrl = calledPlayUrl;
        this.calledDisplayNo = calledDisplayNo;
        this.bindId = bindId;
        this.doParameterFinishStateErrorMsg = "";
    }

    public int getOpType() {
        if (certOpType == 0) {
            return 0;
        }
        return opType;
    }

    public boolean isCanForward() {
        return opType != 0 && certOpType != 0;
    }

    public boolean isChangeDoParameterFinishState() {
        return doParameterFinishState != 0;
    }

    public void setDoParameterFinishStateErrorMsg(String doParameterFinishStateErrorMsg) {
        if (!doParameterFinishStateErrorMsg.isEmpty() && doParameterFinishStateErrorMsg.length() > 200) {
            this.doParameterFinishStateErrorMsg = doParameterFinishStateErrorMsg.substring(0, 199);
        } else {
            this.doParameterFinishStateErrorMsg = doParameterFinishStateErrorMsg;
        }

    }

    public void checkParameter() {
        if (!isNeedRecord()) {
            setNeedEarly(false);
        }
        if (getOpType() == 0) {
            setNeedEarly(false);
        }
    }

    public void onDisconnect(String errorMsg, String playUrl, int finishState, int finishType) {
        onDisconnect(errorMsg, playUrl, finishState);
        setDoParameterFinishType(finishType);
    }

    public void onDisconnect(String errorMsg, String playUrl, int finishState) {
        this.opType = 0;
        this.callerPlayUrl = playUrl;
        setDoParameterFinishStateErrorMsg(errorMsg);
        setDoParameterFinishState(finishState);
    }

    public void onCertDisconnect(String errorMsg, String playUrl, int finishState, int finishType, String billId) {
        this.certOpType = 0;
        this.callerPlayUrl = playUrl;
        setDoParameterFinishType(finishType);
        setDoParameterFinishStateErrorMsg(errorMsg);
        setDoParameterFinishState(finishState);
        AnalyseLog.printBusinessLog("identityPlayCodes", "[{}] [{}] [{}] [{}]", prodNum.getAppId(), "certDisconnect",
                billId, playUrl);
    }

    public void onCertDisconnect(String errorMsg, String playUrl, int finishState, String billId) {
        onCertDisconnect(errorMsg, playUrl, finishState, SYSTEM_STOP, billId);
    }

    public void onDisconnect(String playUrl) {
        this.opType = 0;
        this.callerPlayUrl = playUrl;
    }

    public void setCollectNumLength(int maxLength, int collectWaitTime, int collectIntervalWaitTime) {
        setMaxCollectNumLength(maxLength);
        setCollectWaitTime(collectWaitTime);
        setCollectIntervalWaitTime(collectIntervalWaitTime);
    }

    boolean markHistoryInfo = false;

    public void markHistoryInfo() {
        this.markHistoryInfo = true;
    }

    public boolean ifMarkHistoryInfo() {
        return markHistoryInfo;
    }

    public boolean isRingVideoTransmission() {
        return CallConstant.RING_VIDEO.TRANSMIT.equals(this.ringVideoTransmission);
    }

    public boolean isAllowVideoCall() {
        return CallConstant.VIDEO_CALL.ALLOW.equals(this.allowVideoCall);
    }

    public boolean isRecordPreferred() {
        return CallConstant.RECORD.RECORD_PREFERRED.equals(this.recordPriority);
    }

    // public void setCollectNumLength(int maxLength, int minLength, int collectWaitTime) {
    //     setMaxCollectNumLength(maxLength);
    //     setMinCollectNumLength(minLength);
    //     setCollectWaitTime(collectWaitTime);
    // }

    public boolean noVideoFileToPlay() {
        boolean video3gpFileEmpty = video3gpFileMap == null || video3gpFileMap.size() == 0;
        return StringUtils.isEmpty(videoFile) && video3gpFileEmpty;
    }

}
