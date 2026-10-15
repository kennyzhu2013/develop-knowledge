package com.cmic.recording;

import com.cmic.cache.ConfigCache;
import com.cmic.recording.mediaevent.AudioDoneEvent;
import com.cmic.recording.mediaevent.CallBackEvent;
import com.cmic.recording.mediaevent.VideoDoneEvent;
import com.cmic.recording.params.MediaEnhanceParam;
import com.cmic.recording.params.VomPlayParam;
import com.cmic.recording.videocall.VideoSdp;
import com.cmic.recording.videocall.VideoChannelState;
import com.cmic.sdp.attributes.FormatParameterAttribute;
import com.cmic.sdp.attributes.RtpMapAttribute;
import com.cmic.sdp.attributes.attributegroup.RtpMapAttrGroup;
import com.cmic.sdp.fields.ConnectionField;
import com.cmic.sdp.fields.MediaDescField;
import com.cmic.sdp.fields.OriginField;
import com.cmic.sdp.msgentity.MultipartDescription;
import com.cmic.sdp.msgentity.SessionDescription;
import com.cmic.sdp.msgentity.parser.MultipartDescriptionParser;
import com.cmic.sdp.msgentity.parser.SessionDescriptionParser;
import com.cmic.sdp.parent.SdpField;
import com.cmic.sdp.utils.SdpParseUtil;
import com.cmic.sip.leg.Hook;
import com.cmic.util.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import lombok.Data;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.header.ContentTypeHeader;
import java.io.*;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static com.cmic.recording.MediaManager.*;

@Data
public class MediaSession {
    private static final String RECORD_SERVER_ID_KEY = "X-Media-Server";
    private static Logger logger = LoggerFactory.getLogger(MediaSession.class);
    String sessionId;
    String host;
    private int serverId;
    private int storeId = 0;
    public IPPort proxyEndpoint;

    // 最终协商结果
    public IPPort leftEndpoint;
    public IPPort rightEndpoint;
    public String media;    //最终协商的媒体名，如：amr, amr-octet, pcma, pcmu
    public int code = -1;     //最终协商的媒体编码，如： 0 8 106  (赋初始值为-1. 不能为0=pcmu)

    public int telephonePlayLoad;
    public int vedioCode;
    public String vedioName;

    // 本轮协商的临时结果： 如果本轮协商结果与最终协商结果不同，则覆盖最终协商结果； 每轮协商后，需将*Temp清除
    public IPPort leftEndpointTemp;
    public IPPort rightEndpointTemp;
    private String mediaTemp;
    private int codeTemp = -1;
    // media不接受此值小于0
    public int telephonePlayLoadTemp = 0;

    public String preferMediaCodec;
    private boolean active;
    private boolean stopIsImportant;
    private final static String LEG_LEFT = "left";
    private final static String LEG_RIGHT = "right";

    private boolean useSdpVersion = true;

    private int videoOfferPort = -1;
    private int videoAnswerPort = -1;
    private int audioOfferPort = -1;
    private int audioAnswerPort = -1;

    private SdpSession sdpSession;
    //标识每个HTTP请求事务的序列号,1被create用了，所以从2开始
    private AtomicInteger sqeIdGenerator;
    private Map<Integer, MediaHook> tid_Hook = new ConcurrentHashMap<>();
    private int videoport;
    private int leftVideoPort;
    private int videoPlayLoad;

    //判断ms是否在早期媒体放音，用于下发update时停止早期媒体放音
    private volatile boolean isPlaying = false;

    private volatile boolean isPickupAudioPlaying = false;

    private int playTid = 0;

    private int commitTimes = 0;

    private Timestamp firstCommitTime;

    public boolean isBUpdateSuccess = true;

    HashSet<String> supportedCodec;
    public static HashSet<String> testSupportedCodec;
    //视频彩铃编解码优先级
    String[] codePriority = {"amr-wb", "amr", "amr-octet", "pcma"};
    String[] preferCodePriority = {"amr-wb","amr-wb-octet", "amr", "amr-octet", "pcma","pcmu"};
    private boolean retainNetVideo = true;

    private static Set<Integer> supportedStaticPayloadType = new HashSet<Integer>(Arrays.asList(8, 0));
    private static Set<String> supportedCodeForAudioSet = new HashSet<String>(Arrays.asList("telephone-event", "amr", "amr-wb","pcma", "pcmu"));//AS 默认支持的编码列表
    private static Set<String> supportedCodeForVideoSet = new HashSet<String>(Arrays.asList("telephone-event", "amr", "amr-wb", "pcma", "pcmu"));//AS 视频透传或者视频彩铃支持的编码列表

    public static final String SHORT_SILENCE_FILE_NAME = "nosounds";
    public static final String LONG_SILENCE_FILE_NAME = "empty_30s";
    public static final String PCMA_PLAY_LOAD = "8";
    public static final String PCMU_PLAY_LOAD = "0";

    public static final Pattern SPECIAL_CODE_PATTERN = Pattern.compile("^(DuDu|Mute|TTS:.*)$");//用于判断放音是否为特殊编码

    private boolean isG711Preferred = false;

    private byte[] offerTemp;
    private byte[] answerTemp;

    private String videoOfferMediaLine;
    private String videoAnswerMediaLine;
    private String audioOfferMediaLine;
    private String audioAnswerMediaLine;

    private boolean hasPickUp = false;
    private int calledRecordIndex = 0;

    private boolean applyNewCodecStrategy = false;

    private CallAbnormalInfo callAbnormalInfo = new CallAbnormalInfo();

    private VideoChannelState videoChannelState = new VideoChannelState();

    private VideoLevelRecorder videoLevelRecorder;
    public static HashMap<String, String> defaultCodeClockRate = initDefaultCodeClockRate();

    public MediaSession(String host, String sessionId, IPPort proxyEndpoint, int serverId) {
        this.sessionId = sessionId;
        this.proxyEndpoint = proxyEndpoint;
        this.serverId = serverId;
        this.host = host;
        this.active = true;
        //1已经被create用了，所以从2开始
        this.sqeIdGenerator = new AtomicInteger(2);
        this.sdpSession = new SdpSession();
    }

    public boolean isActive(){
        DtmfLogger.info(sessionId, "mediaSession active：{}",active);
        return active;
    }

    public MediaSession(String host, String sessionId, IPPort proxyEndpoint, int serverId, int videoport) {
        this.sessionId = sessionId;
        this.proxyEndpoint = proxyEndpoint;
        this.serverId = serverId;
        this.host = host;
        this.active = true;
        this.videoport = videoport;
        this.sqeIdGenerator = new AtomicInteger(2);
        this.sdpSession = new SdpSession();
    }

    public MediaSession(String host, String sessionId, IPPort proxyEndpoint, int serverId, int videoport, int storeId) {
        this.sessionId = sessionId;
        this.proxyEndpoint = proxyEndpoint;
        this.serverId = serverId;
        this.host = host;
        this.active = true;
        this.videoport = videoport;
        this.storeId = storeId;
        this.sqeIdGenerator = new AtomicInteger(2);
        this.sdpSession = new SdpSession();
    }

    public MediaSession(String host, String sessionId, IPPort proxyEndpoint, int serverId, int videoport, int storeId, String[] supportedCodec) {
        this.sessionId = sessionId;
        this.proxyEndpoint = proxyEndpoint;
        this.serverId = serverId;
        this.host = host;
        this.active = true;
        this.videoport = videoport;
        this.storeId = storeId;
        this.sqeIdGenerator = new AtomicInteger(2);
        this.sdpSession = new SdpSession();
        this.supportedCodec = new HashSet<>();
        this.supportedCodec.addAll(Arrays.asList(supportedCodec));

    }

    public MediaSession(boolean isActive) {
        this.active = isActive;
        this.sdpSession = new SdpSession();

    }

    public void init(boolean useSdpVersion){
        this.useSdpVersion = useSdpVersion;
        //checkAndSdpSession();
    }


    public void dccPickup(long callStartTime) {
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        params.put("startTime", DateUitl.formatDateyyyyMMddHHmmss(new Date(callStartTime)));
        params.put("seqId", seqId);
        Map<String, String> header = new HashMap<>();
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
        String url = host + MediaManager.PICKUP;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PICKUP);
    }

    public void stop(long endTime) {
        if (!active && !stopIsImportant) {
            return;
        }
        try {
            int seqId = sqeIdGenerator.getAndIncrement();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("sessionId", sessionId);
            params.put("endTime", DateUitl.formatDateyyyyMMddHHmmss(new Date(endTime)));
            params.put("seqId", seqId);
            Map<String, String> header = new HashMap<>();
            header.put(X_SESSION_ID, sessionId);
            // todo 需要media确认这里能不能填字符串
            header.put(X_Sequence_Id, String.valueOf(seqId));
            String url = host + STOP;
            MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.STOP);
        } catch (Exception ex) {
            logger.error("[{}] record error", sessionId, ex);
        }
    }

    public void commit() {
        if (!active) {
            return;
        }
        String lastMediaCode = "";
        String newMediaCode = "";
        synchronized (this) {
            if (this.leftEndpointTemp == null || this.rightEndpointTemp == null || this.mediaTemp == null) {
                return;
            }
            // 修复美团现网测试出现的问题：重新媒体协商，若 payloadtype 有变更，没有通知 xmedia, 导致放音失败
            if (this.leftEndpointTemp.equals(this.leftEndpoint) && this.rightEndpointTemp.equals(this.rightEndpoint)
                    && this.mediaTemp.equals(this.media) && this.codeTemp == this.code
                    && this.telephonePlayLoadTemp == this.telephonePlayLoad) {
                this.leftEndpointTemp = null;
                this.rightEndpointTemp = null;
                return;
            }
            // 如果ip不合法，不发update
            if (!StringUtil.isIp(leftEndpointTemp.ip) || !StringUtil.isIp(rightEndpointTemp.ip)) {
                logger.info("messageGather, billId:{}, recv invalid ip: leftIp-{} or rightIp-{} , ignore the update request! ", sessionId, leftEndpointTemp.ip, rightEndpointTemp.ip);
                this.leftEndpointTemp = null;
                this.rightEndpointTemp = null;
                return;
            }

            // 开始同步
            int seqId = sqeIdGenerator.getAndIncrement();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("sessionId", sessionId);
            params.put("audioCallerIp", this.leftEndpointTemp.ip);
            params.put("audioCalleeIp", this.rightEndpointTemp.ip);
            params.put("audioCode", this.mediaTemp);
            // todo 暂时先填一样的，后续分离
            params.put("videoCallerIp", this.leftEndpointTemp.ip);
            params.put("videoCalleeIp", this.rightEndpointTemp.ip);
            params.put("videoCode", this.mediaTemp);
            params.put("audioCallerPort", this.leftEndpointTemp.port);
            params.put("audioCalleePort", this.rightEndpointTemp.port);
            params.put("audioPayload", this.codeTemp);
            params.put("audioDtmfPayload", this.telephonePlayLoadTemp);
            if (this.leftEndpointTemp.vedioport > 0 && this.rightEndpointTemp.vedioport > 0) {
                params.put("videoCallerPort", this.leftEndpointTemp.vedioport);
                params.put("videoCalleePort", this.rightEndpointTemp.vedioport);
            }
            params.put("videoPayload", this.videoPlayLoad);
            params.put("videoObserve", 0);
            params.put("seqId", seqId);
            Map<String, String> header = new HashMap<>();
            header.put(X_SESSION_ID, sessionId);
            // todo 需要media确认这里能不能填字符串
            header.put(X_Sequence_Id, String.valueOf(seqId));
            String url = host + MediaManager.UPDATE;
            MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.UPDATE);

            //协商次数递增
            commitTimes++;
            if (commitTimes == 1) {
                firstCommitTime = new Timestamp(System.currentTimeMillis());
            }
            this.leftEndpoint = this.leftEndpointTemp;
            this.rightEndpoint = this.rightEndpointTemp;
            lastMediaCode = this.media;
            newMediaCode = this.mediaTemp;
            this.media = this.mediaTemp;
            this.code = this.codeTemp;
            this.leftEndpointTemp = null;
            this.rightEndpointTemp = null;
            this.mediaTemp = null;
            this.codeTemp = -1;
            this.telephonePlayLoad = this.telephonePlayLoadTemp;

        }
        if(hasPickUp){
            if(!newMediaCode.equalsIgnoreCase(lastMediaCode)){
                calledRecordIndex++;
                if(newMediaCode.contains("amr-wb")){
                    AnalyseLog.logAudioCode(sessionId,this.media,calledRecordIndex);
                }
            }
        }
    }

    public void playAndCollect(String audioSource, String audioLoopSource, String audioDefaultSource,
                               String endSign, String backSign, int expectLen, int timeout, int intervalTimeout,
                               int timingMode, int interrupted, int direction, int recordMode, int playTimes, int mode,
                               MediaHook<CallBackEvent> hook) {
        if (!active) {
            return;
        }
        try {
            int seqId = sqeIdGenerator.getAndIncrement();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("sessionId", sessionId);
            params.put("audioSource", processAudioFileSuffix(audioSource));
            params.put("audioLoopSource", processAudioFileSuffix(audioLoopSource));
            params.put("audioDefaultSource", processAudioFileSuffix(audioDefaultSource));
            params.put("endSign", endSign);
            params.put("backSign", backSign);
            params.put("expectLen", expectLen);
            params.put("timeout", timeout);
            params.put("intervalTimeout", intervalTimeout);
            params.put("timingMode", timingMode);
            params.put("interrupted", interrupted);
            params.put("direction", direction);
            params.put("recordMode", recordMode);
            params.put("playTimes", playTimes);
            params.put("mode", mode);
            params.put("seqId", seqId);

            if (hook != null) {
                tid_Hook.put(seqId, hook);
            }
            String url = host + MediaManager.DTMF;
            Map<String, String> header = new HashMap<>();
            header.put(X_SESSION_ID, sessionId);
            // todo 需要media确认这里能不能填字符串
            header.put(X_Sequence_Id, String.valueOf(seqId));
            MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.DTMF);
        } catch (Exception ex) {
            logger.error("billId:{} collect error: {}", sessionId, ex);
        }
    }


    /**
     * @param audioSource  音频源，多个时采用“,”分割；当direction为4时，双边采用“;”分割；可指定DuDu或Mute或TTS:Content播放嘟嘟音或静音自定义音频
     * @param audioLoopSource 音频循环播放源，多个时采用“,”分割；当direction为4时，双边采用“;”分割；可指定DuDu或Mute或TTS:Content播放嘟嘟音或静音自定义音频
     * @param audioCode      音频编码，支持pcma、pcmu、amr、amr-octet、amr-wb、amr-wb-octet，仅当direction为4时生效，双边采用“;”分割，指定各自的音频编码
     * @param audioPayload  音频载荷，pcma=8、pcmu=0、amr/amr-wb≥96，仅当direction为4时生效，双边采用“;”分割，指定各自的音频载荷
     * @param videoSource   视频源，多个时采用“,”分割；当direction为4时，双边采用“;”分割
     * @param videoLoopSource  视频循环播放源，多个时采用“,”分割；当direction为4时，双边采用“;”分割
     * @param period   播放周期，Early=早期；InCall=通话
     * @param priority  优先级，Local=本地优先；Net=大网优先
     * @param direction  播放方向，1. 主叫；2. 被叫；3. 主被叫相同源；4. 主被叫不同源
     * @param recordMode  录音模式，0. 不录；1. 音频录制；2. 视频录制；3. 音视频录制
     * @param hook
     * @return
     */
    public int dccPlay(String audioSource, String audioLoopSource, String videoSource, String videoLoopSource, String period, String priority, int direction,
                    int recordMode, MediaHook<CallBackEvent> hook) {
        if (!active) {
            return 0;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        //根据新的中间号接口规范，如果说带有" ; "的话，就是接通后左右腿放不同音
        if (audioSource.contains(";")) {
            String[] files = audioSource.split(";");
            //根据file数组的长度分情况讨论
            if (files.length == 2) {//   ;100300
                String fileLeft = files[0];
                String fileRight = files[1];
                if (!fileLeft.isEmpty()) {
                    direction = 4;
                    audioSource = getCompletePlayCode(fileLeft) + ";" + getCompletePlayCode(fileRight);
                } else {
                    //只给右腿放音
                    direction = 2;
                    audioSource = getCompletePlayCode(fileRight);
                }
            } else if (files.length == 1) {
                //长度为1时，只有左腿放音
                direction = 1;
                String fileLeft = files[0];
                audioSource = getCompletePlayCode(fileLeft);
            } else {
                //长度为0时，不放音
                return 0;
            }
        } else {
            audioSource = getCompletePlayCode(audioSource);
        }

        params.put("audioSource", audioSource);
//        if("Early".equalsIgnoreCase(period)){
//            params.put("audioLoopSource", StringUtils.isBlank(audioLoopSource) ? "Mute" : audioLoopSource);
//        }else{
//            params.put("audioLoopSource", audioLoopSource);
//        }
        params.put("audioLoopSource", audioLoopSource);

        params.put("audioCode", this.media + ";" + this.media);
        params.put("audioPayload", this.code + ";" + this.code);
        params.put("videoSource", videoSource);
        params.put("videoLoopSource", videoLoopSource);
        params.put("period", period);
        params.put("priority", priority);
        params.put("direction", direction);
        params.put("recordMode",recordMode);
        params.put("seqId", seqId);
        if (hook != null) {
            tid_Hook.put(seqId, hook);
        }
        String url = host + MediaManager.PLAY;
        Map<String, String> header = new HashMap<>();
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PLAY);
        return seqId;
    }

    public void stopMedia(int mark){
        if (!active) {
            return;
        }
        if (isPlaying) {
            isPlaying = false;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        params.put("mark", mark == 2 ? "Dtmf" : "Media");
        params.put("seqId", seqId);
        String url = host + MediaManager.MEDIA_STOP;
        Map<String, String> header = new HashMap<>();
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.MEDIA_STOP);
    }

    public void dccStopMedia(){
        stopMedia(1);
    }



    public void enhance(String audioSource, String audioLoopSource, String mediaSource, String videoLoopSource,
                        ObjectNode config, int pushVideo, int recordMode, MediaHook<CallBackEvent> hook){
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        params.put("audioSource", audioSource);
        params.put("audioLoopSource", audioLoopSource);
        params.put("mediaSource", mediaSource);
        params.put("videoLoopSource", videoLoopSource);
        params.set("config", config);
        params.put("pushVideo", pushVideo);
        params.put("recordMode", recordMode);
        params.put("seqId", seqId);
        if (hook != null) {
            tid_Hook.put(seqId, hook);
        }
        logger.info("[{}] enhance params:{}", sessionId, params);
        Map<String, String> header = new HashMap<>();
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
        String url = host + MediaManager.ENHANCE;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.ENHANCE);
    }


























    public boolean isVideoCallOnGoing(){
        return videoChannelState.getState() == VideoChannelState.OPEN_STATE;
    }

    public void clearVideoData(){
        videoOfferMediaLine = null;
        videoAnswerMediaLine = null;
    }

    private byte[] processSDP(byte[] rawContent, String leg) {
        return processSDP(rawContent, leg, null);
    }

    /**
     * preferMedia: 指定优先协商编码，可用值：amr-octet, amr ,pcma, pcmu    当输入其他编码时，匹配不到有效编码，则对编码次序无影响
     **/
    private byte[] processSDP(byte[] rawContent, String leg, String preferMedia) {
        if (!active) {
            return rawContent;
        }
        logger.info("[{}] processSDP start", sessionId);
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);

        ArrayList<String> audioCodeSupport = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> audioNameSupport = new ArrayList<String>();   //支持的媒体名称


        IPPort ipportRemote = new IPPort();
        boolean is_contains_8 = true;
        boolean is_contains_0 = true;

        boolean isBeforeMediaLine = true;
        boolean isNeedAppend = false;
        boolean hasAddPtime = false;
        boolean hasAddMaxPtime = false;
        boolean isPreferMedia = false;
        String pcmaLine = "";
        String pcmuLine = "";
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        String ptime = "";
        String maxPtime = "";
        ArrayList<RtpAttribute> originRtp = new ArrayList<>();
        String currMediaCode = "";
        List<String> audioCodeList = null;
        ArrayList<String> finalAudioCode = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> finalAudioName = new ArrayList<String>();   //支持的媒体编码

        try {
            String line = br.readLine();
            while (line != null) {
                if(currMediaCode.equals("video") && !isRetainNetVideo()){
                    if (!line.startsWith("m=")) {
                        line = br.readLine();
                        continue;
                    }
                }
                if (line.contains("m=video")) {
                    if (videoLevelRecorder == null){
                        videoLevelRecorder = new VideoLevelRecorder(sessionId);
                    }
                    videoLevelRecorder.recordVideoParams(leg.equals(LEG_LEFT), rawContent);
                    ipportRemote.vedioport = Integer.parseInt(line.split(" ")[1]);
                    //若为0则直接透过去
                    if (ipportRemote.vedioport != 0) {
                        if(isRetainNetVideo()) {
                            line = line.replace(String.valueOf(ipportRemote.vedioport), String.valueOf(videoport));
                        } else {
                            // 不需要保留视频的话直接置零
                            line = line.replace(String.valueOf(ipportRemote.vedioport), String.valueOf(0));
                        }
                    }
                    currMediaCode = "video";
                    isNeedAppend = true;
                }
                else if (line.startsWith("o=")) {
                    String o[] = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    if (leg.equals(LEG_RIGHT)) {
                        sdpSession.acceptRightO(line);
                        o[1] = sdpSession.getLeftLocalSessionId();
                        o[2] = sdpSession.getLeftLocalSessionVersion() + "";
                    } else {
                        sdpSession.acceptLeftO(line);
                        o[1] = sdpSession.getRightLocalSessionId();
                        o[2] = sdpSession.getRightLocalSessionVersion() + "";
                    }
                    for (String s : o) {
                        sb.append(s).append(" ");
                    }
                    //去掉最末尾的" "
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    line = sb.toString();
                }
                else if (line.indexOf("m=audio") > -1) {
                    //m=audio 50706 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0
                    String portString = line.split(" ")[1];
                    currMediaCode = "audio";
                    if (portString.contains("/")) {
                        ipportRemote.port = Integer.parseInt(portString.split("/")[0]);
                        if (!portString.split("/")[1].equals("1")) {
                            logger.info("messageGather, billId:{}, number of port not 1 ! line:{}", sessionId, line);
                        }
                    } else {
                        ipportRemote.port = Integer.parseInt(line.split(" ")[1]);
                    }
                    String[] parts = line.split(" ");
                    audioCodeList = Arrays.asList(parts);
                    is_contains_8 = audioCodeList.contains("8");
                    is_contains_0 = audioCodeList.contains("0");
                    isBeforeMediaLine = false;
                    isNeedAppend = false;
                } else if(line.startsWith("m=")){
                    currMediaCode = "other";
                    isNeedAppend = true;
                }
                else if (line.indexOf("c=") == 0) {
                    //c=IN IP4 10.188.28.49
                    //ipportRemote.ip = line.split(" ")[2];
                    if(StringUtils.isNotBlank(currMediaCode) && !currMediaCode.equalsIgnoreCase("audio")){
                        //如果目前是video行的c行，那就纯透传
                    }else {
                        String ip = line.split(" ")[2];
                        ipportRemote.ip = ip;
                        if (StringUtil.isIp(ip)) {
                            line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                        }
                        //logger.info("[{}] processSDP zhejiang ip set:{}",sessionid,ConfigCache.getZjjlIpListSet());
                        String remoteIp = ipportRemote.ip.trim();
                        if(MediaSession.LEG_RIGHT.equalsIgnoreCase(leg) && ConfigCache.getZjjlIpListSet().contains(remoteIp)){
                            logger.info("[{}] hit zhejiang jiling ip: {}", sessionId,remoteIp);
                            callAbnormalInfo.setDoesCalleeContainZjjlIP(true);
                        }
                    }

                    isNeedAppend = true;
                } else if (line.indexOf("a=rtpmap") > -1) { //媒体申明行
                    // a=rtpmap:18 G729/8000
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    String codeName = parts[1].split("/")[0].toLowerCase();
                    String codeFullname = codeName;
                    RtpAttribute rtp = new RtpAttribute();
                    //支持优先编码
                    if (preferMediaCodec != null && !preferMediaCodec.equalsIgnoreCase("")) {
                        // 支持的编码
                        if (codeName.equalsIgnoreCase(preferMediaCodec) || codeName.equalsIgnoreCase("telephone-event")) {
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            isNeedAppend = true;
                            isPreferMedia = !isPreferMedia && !codeName.equalsIgnoreCase("telephone-event");
                        } else if (codeName.equalsIgnoreCase("pcma")) {
                            pcmaLine = line;
                            isNeedAppend = false;
                        } else if (codeName.equalsIgnoreCase("pcmu")) {
                            pcmuLine = line;
                            isNeedAppend = false;
                        } else {
                            isNeedAppend = false;
                        }

                    } else {
                        // 支持的编码
                        if (getSupportCodec() != null && getSupportCodec().contains(codeName)) {
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            isNeedAppend = true;
                        } else {
                            isNeedAppend = false;
                        }
                    }
                    rtp.setCode(codeNumber);
                    rtp.setName(codeFullname);
                    rtp.setIfSupported(isNeedAppend);
                    originRtp.add(rtp);
                } else if (line.indexOf("a=fmtp") > -1) { //媒体属性行
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    int pos = audioCodeSupport.indexOf(codeNumber);
                    if (pos > -1) {
                        isNeedAppend = true;
                        if (line.contains("octet-align=1")) {
                            String name = audioNameSupport.get(pos);
                            audioNameSupport.set(pos, name + "-octet");
                        }
                    } else {
                        isNeedAppend = false;
                    }
                    if (originRtp.size() > 0) {
                        originRtp.get(originRtp.size() - 1).setFmtp(line);
                    }
                } else if (line.indexOf("a=ptime") > -1) {
                    if (originRtp.size() > 0) {
                        RtpAttribute rtpAttribute = originRtp.get(originRtp.size() - 1);
                        rtpAttribute.setPtime(line);
                        if (rtpAttribute.isIfSupported()) {
                            hasAddPtime = true;
                            isNeedAppend = true;
                        } else {
                            isNeedAppend = false;
                        }
                    } else {
                        hasAddPtime = true;
                        isNeedAppend = true;
                    }
                    if (ptime.isEmpty()) {
                        ptime = line;
                    }
                } else if (line.indexOf("a=maxptime") > -1) {
                    if (hasAddPtime) {
                        line = ptime.replace("ptime", "maxptime");
                        isNeedAppend = true;
                        hasAddMaxPtime = true;
                    } else {
                        isNeedAppend = false;
                    }
                    maxPtime = line;
                } else {
                    if(MediaSession.LEG_RIGHT.equalsIgnoreCase(leg) && line.indexOf("a=3gOoBTC") > -1){
                        callAbnormalInfo.setDoesCalleeContain3gOoBTC(true);
                    }
                    isNeedAppend = true;
                }

                if (isNeedAppend) {
                    if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }

            // 当属性中没有出现8和0的参数描述时，根据m=media行中的编码补位
            if (is_contains_8 && !audioCodeSupport.contains("8") && !isPreferMedia) {
                audioCodeSupport.add("8");
                audioNameSupport.add("pcma");
                if(!StringUtils.isEmpty(pcmaLine)){
                    sbAfter.append(pcmaLine).append("\r\n");
                }

            }
            if (is_contains_0 && !audioCodeSupport.contains("0") && !isPreferMedia) {
                audioCodeSupport.add("0");
                audioNameSupport.add("pcmu");
                if(!StringUtils.isEmpty(pcmuLine)){
                    sbAfter.append(pcmuLine).append("\r\n");
                }
            }

            //加个mediaSession的锁保证与正在commit的线程互斥（防止当前线程在其他线程还在commit的时候，就把ip填入，后续会被commit误删，导致协商紊乱）id:1316385515413719
            synchronized (this) {
                if (leg.equals(LEG_LEFT)) {
                    this.leftEndpointTemp = ipportRemote;
                } else {
                    this.rightEndpointTemp = ipportRemote;
                }
                //判断是不是answer，如果是 answer，则保持原有的顺序
                if(this.leftEndpointTemp != null && this.rightEndpointTemp != null){
                    for(String audioCode: audioCodeList){
                        if(audioCodeSupport.contains(audioCode)){
                            int index  = audioCodeSupport.indexOf(audioCode);
                            finalAudioCode.add(audioCode);
                            finalAudioName.add(audioNameSupport.get(index));
                         }
                    }
                }
                if(finalAudioCode.isEmpty()){
                    finalAudioCode.addAll(audioCodeSupport);
                    finalAudioName.addAll(audioNameSupport);
                }

                if(isG711Preferred && (this.leftEndpointTemp == null || this.rightEndpointTemp == null)){
                    if(finalAudioCode.contains("8")){
                        finalAudioCode.remove("8");
                        finalAudioCode.add(0,"8");
                        finalAudioName.remove("pcma");
                        finalAudioName.add(0,"pcma");
                    }else if(finalAudioCode.contains("0")){
                        finalAudioCode.remove("0");
                        finalAudioCode.add(0,"0");
                        finalAudioName.remove("pcmu");
                        finalAudioName.add(0,"pcmu");
                    }
                }

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        logger.info("[{}] processSDP finalAudioCode:{},finalAudioName:{}", sessionId,finalAudioCode,finalAudioName);

// 调整优先使用的编码: 上一次协商的编码具有最高优先级、其次是参数中指定的编码
//        String prefer = this.media != null ? this.media : preferMedia;
//        for (int i = 0; i < audioNameSupport.size(); i++) {
//            if(audioNameSupport.get(i).equals(prefer)){
//                String c = audioCodeSupport.get(i);
//                String n = audioNameSupport.get(i);
//                audioNameSupport.remove(i);
//                audioCodeSupport.remove(i);
//                audioNameSupport.add(0, n);
//                audioCodeSupport.add(0, c);
//            }
//        }
        //若前面协商没加入ptime,增加一个补偿机制
        if (!hasAddPtime) {
            if (!ptime.isEmpty()) {
                sbAfter.append(ptime).append("\r\n");
                //同时新增maxPtime
                if (!maxPtime.isEmpty()) {
                    sbAfter.append(ptime.replace("ptime", "maxptime")).append("\r\n");
                }
            }
        } else {
            if (!hasAddMaxPtime && !maxPtime.isEmpty()) {
                sbAfter.append(ptime.replace("ptime", "maxptime")).append("\r\n");
            }
        }

        sbBefore = addConnectionIfSessionAbsence(sbBefore);

        sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, String.join(" ", finalAudioCode))).append("\r\n").append(sbAfter);

        // 判断本次协商结果： offer 与 answer 都处理完毕之后，把结果存入temp
        if (this.leftEndpointTemp != null && this.rightEndpointTemp != null) {
            // 正常情况下从answer中只会有2个code， 非telephone-event即时语音编码
            boolean isMediaCodeFix = false;
            boolean isTelephoneEventFix = false;
            for (int i = 0; i < finalAudioName.size(); i++) {
                if (!finalAudioName.get(i).equals("telephone-event") && !isMediaCodeFix) {
                    this.mediaTemp = finalAudioName.get(i);
                    this.codeTemp = Integer.valueOf(finalAudioCode.get(i));
                    isMediaCodeFix = true;
                }else if(finalAudioName.get(i).equals("telephone-event") && !isTelephoneEventFix){
                    this.telephonePlayLoadTemp = Integer.valueOf(finalAudioCode.get(i));
                    isTelephoneEventFix = true;
                }
                if(isMediaCodeFix && isTelephoneEventFix){
                    break;
                }
            }
        }

//        System.out.println(this.mediaTemp);
//        System.out.println(this.codeTemp);
//        System.out.println(audioNameSupport);
//        System.out.println(audioCodeSupport);

        return sbBefore.toString().getBytes();
    }

    private byte[] processSDPWithVideo(byte[] rawContent, String leg, String preferMedia) {
        if (!active) {
            return rawContent;
        }
        if (offerTemp == null) {
            offerTemp = rawContent;
        } else {
            answerTemp = rawContent;
        }
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);

        ArrayList<String> audioCodeSupport = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> audioNameSupport = new ArrayList<String>();   //支持的媒体名称

        logger.info("[{}] processSDPWithVideo start", sessionId);

        IPPort ipportRemote = new IPPort();
        boolean is_contains_8 = true;
        boolean is_contains_0 = true;

        boolean isBeforeMediaLine = true;
        boolean isNeedAppend = false;
        boolean hasAddPtime = false;
        boolean hasAddMaxPtime = false;
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        String ptime = "";
        String maxPtime = "";
        ArrayList<RtpAttribute> originRtp = new ArrayList<>();
        String currMediaCode = "";
        //boolean videoHasConnection = false;
        String originSessionConnection = "";
        StringBuilder sbSession = new StringBuilder();
        //StringBuilder sbAudio = new StringBuilder();
        //StringBuilder sbVideo = new StringBuilder();
        //StringBuilder sbOtherMedia = new StringBuilder();

        StringBuilder mediaInfoBuilder = null;

        List<StringBuilder> mediaInfoList = new ArrayList<>();


        List<String> mediaList = new ArrayList<String>();
        String mediaVideoLine = "";
        String lineNextToVideo = "";
        boolean isTheLineNextToVideoRead = false;
        String videoConnectionLine = "";
        boolean isPreferMedia = false;
        String pcmaLine = "";
        String pcmuLine = "";
        ArrayList<String> finalAudioCode = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> finalAudioName = new ArrayList<String>();   //支持的媒体编码
        List<String> audioCodeList = null;

        try {
            String line = br.readLine();
            while (line != null) {
                boolean ifNotNeedToAppendVideo = false;
                if (line.indexOf("m=") > -1) {
                    String[] arrMediaParam = line.split(" ");
                    if (arrMediaParam[0].equalsIgnoreCase("m=video")){
                        mediaList.add("video");
                        if (videoLevelRecorder == null){
                            videoLevelRecorder = new VideoLevelRecorder(sessionId);
                        }
                        videoLevelRecorder.recordVideoParams(leg.equals(LEG_LEFT), rawContent);
                    } else if (arrMediaParam[0].equalsIgnoreCase("m=audio")){
                        mediaList.add("audio");
                    } else {
                        mediaList.add(arrMediaParam[0].substring(2).trim());
                    }
                    if(mediaInfoBuilder != null){
                        mediaInfoList.add(mediaInfoBuilder);
                    }
                    mediaInfoBuilder = new StringBuilder();
                    //mediaVideoLine = "";
                    //isTheLineNextToVideoRead = false;
                }

                if (retainNetVideo && !StringUtils.isEmpty(mediaVideoLine) && currMediaCode.equals("video") && !isTheLineNextToVideoRead) {
                    lineNextToVideo = line;
                    isTheLineNextToVideoRead = true;
                    //video 后面一行不需要 append
                    ifNotNeedToAppendVideo = true;
                }
                if (line.indexOf("m=video") > -1) {
                    if (videoOfferMediaLine == null) {
                        videoOfferMediaLine = line;
                    } else {
                        videoAnswerMediaLine = line;
                    }
                    currMediaCode = "video";
                    ipportRemote.vedioport = Integer.parseInt(line.split(" ")[1]);
                    if (retainNetVideo && StringUtils.isEmpty(mediaVideoLine)) {
                        mediaVideoLine = line;
                        //video 一行不需要 append
                        ifNotNeedToAppendVideo = true;
                    }
                    //若为0则直接透过去
                    if (ipportRemote.vedioport != 0) {
                        if (!retainNetVideo) {
                            line = line.replace(String.valueOf(ipportRemote.vedioport), String.valueOf(videoport));
                        }
                    }
                }
                else if (line.startsWith("o=") && currMediaCode.equals("audio")) {
                    String o[] = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    if (leg.equals(LEG_RIGHT)) {
                        sdpSession.acceptRightO(line);
                        o[1] = sdpSession.getLeftLocalSessionId();
                        o[2] = sdpSession.getLeftLocalSessionVersion() + "";
                    } else {
                        sdpSession.acceptLeftO(line);
                        o[1] = sdpSession.getRightLocalSessionId();
                        o[2] = sdpSession.getRightLocalSessionVersion() + "";
                    }
                    for (String s : o) {
                        sb.append(s).append(" ");
                    }
                    //去掉最末尾的" "
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    line = sb.toString();
                }
                else if (line.indexOf("m=audio") > -1) {
                    if (audioOfferMediaLine == null) {
                        audioOfferMediaLine = line;
                    } else {
                        audioAnswerMediaLine = line;
                    }
                    currMediaCode = "audio";
                    //m=audio 50706 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0
                    String portString = line.split(" ")[1];
                    if (portString.contains("/")) {
                        ipportRemote.port = Integer.parseInt(portString.split("/")[0]);
                        if (!portString.split("/")[1].equals("1")) {
                            logger.info("messageGather, billId:{}, number of port not 1 ! line:{}", sessionId, line);
                        }
                    } else {
                        ipportRemote.port = Integer.parseInt(line.split(" ")[1]);
                    }
                    String[] parts = line.split(" ");
                    //List<String> codes = Arrays.asList(parts);
                    audioCodeList = Arrays.asList(parts);
                    is_contains_8 = audioCodeList.contains("8");
                    is_contains_0 = audioCodeList.contains("0");
                    isBeforeMediaLine = false;
                    isNeedAppend = false;
                } else if(line.indexOf("m=") > -1){
                    currMediaCode = "other";
                    isNeedAppend = true;
                } else if (line.indexOf("c=") == 0) {
                    //c=IN IP4 10.188.28.49
                    if (StringUtils.isEmpty(currMediaCode)) {
                        originSessionConnection = line;
                    }
                    if (retainNetVideo && currMediaCode.equalsIgnoreCase("video") && StringUtils.isEmpty(videoConnectionLine)) {
                        //videoHasConnection = true;
                        videoConnectionLine = line;
                        isNeedAppend = false;
                        ifNotNeedToAppendVideo = true;
                    } else if(StringUtils.isBlank(currMediaCode) || currMediaCode.equalsIgnoreCase("audio")){
                        ipportRemote.ip = line.split(" ")[2];
                        line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                    }
                    //logger.info("[{}] processSDPWithVideo zhejiang ip set:{}",sessionid,ConfigCache.getZjjlIpListSet());
                    String remoteIp = ipportRemote.ip.trim();
                    if(MediaSession.LEG_RIGHT.equalsIgnoreCase(leg) && ConfigCache.getZjjlIpListSet().contains(remoteIp)){
                        logger.info("[{}] hit zhejiang jiling ip: {}", sessionId,remoteIp);
                        callAbnormalInfo.setDoesCalleeContainZjjlIP(true);
                    }

                    isNeedAppend = true;
                } else if (line.indexOf("a=rtpmap") > -1 && currMediaCode.equals("audio")) { //媒体申明行
                    // a=rtpmap:18 G729/8000
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    String codeName = parts[1].split("/")[0].toLowerCase();
                    String codeFullname = codeName;
                    RtpAttribute rtp = new RtpAttribute();
                    //支持优先编码
                    if (preferMediaCodec != null && !preferMediaCodec.equalsIgnoreCase("")) {
                        // 支持的编码
                        if (codeName.equalsIgnoreCase(preferMediaCodec) || codeName.equalsIgnoreCase("telephone-event")) {
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            isNeedAppend = true;
                            isPreferMedia =  !isPreferMedia && !codeName.equalsIgnoreCase("telephone-event");
                        } else if(codeName.equalsIgnoreCase("pcma") ){
                            pcmaLine = line;
                            isNeedAppend = false;
                        } else if(codeName.equalsIgnoreCase("pcmu")){
                            pcmuLine = line;
                            isNeedAppend = false;
                        } else {
                            isNeedAppend = false;
                        }

                    } else {
                        // 支持的编码
                        if (getSupportCodec() != null && getSupportCodec().contains(codeName)) {
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            isNeedAppend = true;
                        } else {
                            isNeedAppend = false;
                        }
                   /* }
                    if (codeName.equalsIgnoreCase("pcma")
                            || codeName.equalsIgnoreCase("pcmu")
                            || codeName.equalsIgnoreCase("amr")
                            || codeName.equalsIgnoreCase("amr-wb")
                            || codeName.equalsIgnoreCase("telephone-event")
                    ) {*/
                    }
                    rtp.setCode(codeNumber);
                    rtp.setName(codeFullname);
                    rtp.setIfSupported(isNeedAppend);
                    originRtp.add(rtp);
                } else if (line.indexOf("a=fmtp") > -1 && currMediaCode.equals("audio")) { //媒体属性行
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    int pos = audioCodeSupport.indexOf(codeNumber);
                    if (pos > -1) {
                        isNeedAppend = true;
                        if (line.contains("octet-align=1")) {
                            String name = audioNameSupport.get(pos);
                            audioNameSupport.set(pos, name + "-octet");
                        }
                    } else {
                        isNeedAppend = false;
                    }
                    if (originRtp.size() > 0) {
                        originRtp.get(originRtp.size() - 1).setFmtp(line);
                    }
                } else if (line.indexOf("a=ptime") > -1 && currMediaCode.equals("audio")) {
                    if (originRtp.size() > 0) {
                        RtpAttribute rtpAttribute = originRtp.get(originRtp.size() - 1);
                        rtpAttribute.setPtime(line);
                        if (!hasAddPtime && rtpAttribute.isIfSupported()) {
                            isNeedAppend = true;
                            hasAddPtime = true;
                        } else {
                            isNeedAppend = false;
                        }
                    } else {
                        isNeedAppend = true;
                        hasAddPtime = true;
                    }
                    if (ptime.isEmpty()) {
                        ptime = line;
                    }
                } else if (line.indexOf("a=maxptime") > -1 && currMediaCode.equals("audio")) {
                    if (hasAddPtime) {
                        line = ptime.replace("ptime", "maxptime");
                        isNeedAppend = true;
                        hasAddMaxPtime = true;
                    } else {
                        isNeedAppend = false;
                    }
                    maxPtime = line;
                } else {
                    if(MediaSession.LEG_RIGHT.equalsIgnoreCase(leg) && line.indexOf("a=3gOoBTC") > -1){
                        callAbnormalInfo.setDoesCalleeContain3gOoBTC(true);
                    }
                    isNeedAppend = true;
                }

                if (isNeedAppend) {
                    /*if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }*/
                    if (StringUtils.isEmpty(currMediaCode)) {
                        sbSession.append(line).append("\r\n");
                    } else if (currMediaCode.equals("audio")) {
                        mediaInfoBuilder.append(line).append("\r\n");
                    } else if (currMediaCode.equals("video") ) {
                        if(!ifNotNeedToAppendVideo){
                            mediaInfoBuilder.append(line).append("\r\n");
                        }
                    } else {
                        mediaInfoBuilder.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }

            if(mediaInfoBuilder != null){
                mediaInfoList.add(mediaInfoBuilder);
            }

            // 当属性中没有出现8和0的参数描述时，根据m=media行中的编码补位
            if (is_contains_8 && !audioCodeSupport.contains("8") && !isPreferMedia) {
                audioCodeSupport.add("8");
                audioNameSupport.add("pcma");
                if(!StringUtils.isEmpty(pcmaLine)){
                    sbAfter.append(pcmaLine).append("\r\n");
                }

            }
            if (is_contains_0 && !audioCodeSupport.contains("0") && !isPreferMedia) {
                audioCodeSupport.add("0");
                audioNameSupport.add("pcmu");
                if(!StringUtils.isEmpty(pcmuLine)){
                    sbAfter.append(pcmuLine).append("\r\n");
                }
            }

            //加个mediaSession的锁保证与正在commit的线程互斥（防止当前线程在其他线程还在commit的时候，就把ip填入，后续会被commit误删，导致协商紊乱）id:1316385515413719
            synchronized (this) {
                if (leg.equals(LEG_LEFT)) {
                    this.leftEndpointTemp = ipportRemote;
                } else {
                    this.rightEndpointTemp = ipportRemote;
                }
                //判断是不是answer，如果是 answer，则保持原有的顺序
                if(this.leftEndpointTemp != null && this.rightEndpointTemp != null){
                    for(String audioCode: audioCodeList){
                        if(audioCodeSupport.contains(audioCode)){
                            int index  = audioCodeSupport.indexOf(audioCode);
                            finalAudioCode.add(audioCode);
                            finalAudioName.add(audioNameSupport.get(index));
                        }
                    }
                }
                if(finalAudioCode.isEmpty()){
                    finalAudioCode.addAll(audioCodeSupport);
                    finalAudioName.addAll(audioNameSupport);
                }

                if(isG711Preferred && (this.leftEndpointTemp == null || this.rightEndpointTemp == null)){
                    if(finalAudioCode.contains("8")){
                        finalAudioCode.remove("8");
                        finalAudioCode.add(0,"8");
                        finalAudioName.remove("pcma");
                        finalAudioName.add(0,"pcma");
                    }else if(finalAudioCode.contains("0")){
                        finalAudioCode.remove("0");
                        finalAudioCode.add(0,"0");
                        finalAudioName.remove("pcmu");
                        finalAudioName.add(0,"pcmu");
                    }
                }



            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        logger.info("[{}] processSDPWithVideo finalAudioCode:{},finalAudioName:{}", sessionId,finalAudioCode,finalAudioName);

        // 调整优先使用的编码: 上一次协商的编码具有最高优先级、其次是参数中指定的编码
//        String prefer = this.media != null ? this.media : preferMedia;
//        for (int i = 0; i < audioNameSupport.size(); i++) {
//            if(audioNameSupport.get(i).equals(prefer)){
//                String c = audioCodeSupport.get(i);
//                String n = audioNameSupport.get(i);
//                audioNameSupport.remove(i);
//                audioCodeSupport.remove(i);
//                audioNameSupport.add(0, n);
//                audioCodeSupport.add(0, c);
//            }
//        }
        //若前面协商没加入ptime,增加一个补偿机制
        StringBuilder sbAudio = null;
        for(int index = 0; index < mediaList.size();index++){
            String mediaCode = mediaList.get(index);
            if(mediaCode.equalsIgnoreCase("audio")){
                sbAudio = mediaInfoList.get(index);
                break;
            }
        }
        if (!hasAddPtime) {
            if (!ptime.isEmpty()) {
                sbAudio.append(ptime).append("\r\n");
                //同时新增maxPtime
                if (!maxPtime.isEmpty()) {
                    sbAudio.append(ptime.replace("ptime", "maxptime")).append("\r\n");
                }
            }
        } else {
            if (!hasAddMaxPtime && !maxPtime.isEmpty()) {
                sbAudio.append(ptime.replace("ptime", "maxptime")).append("\r\n");
            }
        }

        //sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, String.join(" ", audioCodeSupport))).append("\r\n").append(sbAfter);

//        System.out.println("******************************");
//        System.out.println(sbAudio.toString());
//        System.out.println("******************************");
//        System.out.println("mediaVideoLine="+mediaVideoLine);

        sbSession = addConnectionIfSessionAbsence(sbSession);

        for (int index = 0; index < mediaList.size(); index++) {
            String mediaCode = mediaList.get(index);
            StringBuilder tempBuilder = mediaInfoList.get(index);
            switch (mediaCode) {
                case "audio":
                    sbSession.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, String.join(" ", finalAudioCode))).append("\r\n").append(sbAudio.toString());
                    break;
                case "video":
                    if (retainNetVideo) {
                        sbSession.append(mediaVideoLine).append("\r\n");
                        //按照RFC8866 协议，video 下面一行有可能是 c 行 或者 i 行
                        if (lineNextToVideo.equals(videoConnectionLine)) {
                            if(StringUtils.isNotBlank(lineNextToVideo)){
                                if(ipportRemote.vedioport != 0){
                                    sbSession.append(videoConnectionLine).append("\r\n");
                                }
                            }
                        } else {
                            if (lineNextToVideo.startsWith("i=")) {
                                sbSession.append(lineNextToVideo).append("\r\n");
                                //如果video没有c行，则用session 层级的c行
                                if (StringUtils.isEmpty(videoConnectionLine)) {
                                    if(ipportRemote.vedioport != 0){
                                        sbSession.append(originSessionConnection).append("\r\n");
                                    }
                                } else {
                                    if(ipportRemote.vedioport != 0){
                                        sbSession.append(videoConnectionLine).append("\r\n");
                                    }
                                }
                            } else {
                                //如果video没有c行，则用session 层级的c行
                                if (StringUtils.isEmpty(videoConnectionLine)) {
                                    if(ipportRemote.vedioport != 0){
                                        sbSession.append(originSessionConnection).append("\r\n");
                                    }
                                } else {
                                    if(ipportRemote.vedioport != 0){
                                        sbSession.append(videoConnectionLine).append("\r\n");
                                    }
                                }
                                sbSession.append(lineNextToVideo).append("\r\n");
                            }
                        }
                    }
                    sbSession.append(tempBuilder.toString());
                    break;
               /* case "other":
                    sbSession.append(sbOther.toString());
                    break;*/
                default:
                    sbSession.append(tempBuilder.toString());
                    break;
            }
        }

        //System.out.println("sbSession="+sbSession.toString());


        // 判断本次协商结果： offer 与 answer 都处理完毕之后，把结果存入temp
        if (this.leftEndpointTemp != null && this.rightEndpointTemp != null) {
            // 正常情况下从answer中只会有2个code， 非telephone-event即时语音编码
            /*for (int i = 0; i < audioNameSupport.size(); i++) {
                if (!audioNameSupport.get(i).equals("telephone-event")) {
                    this.mediaTemp = audioNameSupport.get(i);
                    this.codeTemp = Integer.valueOf(audioCodeSupport.get(i));
                    break;
                }
            }*/

            boolean isMediaCodeFix = false;
            boolean isTelephoneEventFix = false;
            for (int i = 0; i < finalAudioName.size(); i++) {
                if (!finalAudioName.get(i).equals("telephone-event") && !isMediaCodeFix) {
                    this.mediaTemp = finalAudioName.get(i);
                    this.codeTemp = Integer.valueOf(finalAudioCode.get(i));
                    isMediaCodeFix = true;
                }else if(finalAudioName.get(i).equals("telephone-event") && !isTelephoneEventFix){
                    this.telephonePlayLoadTemp = Integer.valueOf(finalAudioCode.get(i));
                    isTelephoneEventFix = true;
                }
                if(isMediaCodeFix && isTelephoneEventFix){
                    break;
                }
            }
        }


        return sbSession.toString().getBytes();
    }


    public static void main(String[] args) {
        try {
            //MediaSession ms = new MediaSession("", "", new IPPort("8.8.8.8", 8888), 1,1, 1, new String[]{"pcma","pcmu","amr","amr-wb","telephone-event"});
            // MediaSession.testSupportedCodec = new HashSet<>(Arrays.asList(new String[]{"amr-wb","amr","pcma","pcmu","telephone-event"}));
            MediaSession ms = new MediaSession("", "", new IPPort("8.8.8.8", 8888), 1,1, 1, new String[]{"amr-wb","amr","pcma","pcmu","telephone-event"});
            ms.rightEndpointTemp = new IPPort("111", 111);
            ms.media = "amr-octet";
            ms.setRetainNetVideo(false);
            String sdp = "v=0\n" +
                    "o=- 1740463863 1740463863 IN IP4 10.132.118.144\n" +
                    "s=SBC call\n" +
                    "c=IN IP4 10.188.180.68\n" +
                    "t=0 0\n" +
                    "m=audio 15634 RTP/AVP 109 104 110 102 108 105 100 18\n" +
                    "b=AS:80\n" +
                    "b=RS:625\n" +
                    "b=RR:1875\n" +
                    "a=rtpmap:109 EVS/16000\n" +
                    "a=fmtp:109 br=24.4; bw=wb; max-red=220; ch-aw-recv=-1\n" +
                    "a=rtpmap:104 AMR-WB/16000\n" +
                    "a=fmtp:104 max-red=220;mode-change-capability=2\n" +
                    "a=rtpmap:110 AMR-WB/16000\n" +
                    "a=fmtp:110 octet-align=1;max-red=220;mode-change-capability=2\n" +
                    "a=rtpmap:102 AMR/8000\n" +
                    "a=fmtp:102 max-red=220;mode-change-capability=2\n" +
                    "a=rtpmap:108 AMR/8000\n" +
                    "a=fmtp:108 octet-align=1;max-red=220;mode-change-capability=2\n" +
                    "a=ptime:20\n" +
                    "a=maxptime:240\n" +
                    "a=sendrecv\n" +
                    "a=des:qos mandatory local sendrecv\n" +
                    "a=curr:qos local none\n" +
                    "a=des:qos optional remote sendrecv\n" +
                    "a=curr:qos remote none\n" +
                    "a=rtpmap:18 G729/8000\n" +
                    "a=fmtp:18 annexb=no\n";

            ms.setApplyNewCodecStrategy(true);
            byte[] dtmf2LeftSdp = ms.processDtmfRightSDP(sdp.getBytes(), false, false);
            System.out.println(new String(dtmf2LeftSdp));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processWithSdpVersion(byte[] rawContent, String fromLeg, String preferMedia) throws Exception {
        SessionDescription remoteSdp =  transferStrToSdp(rawContent, sessionId);
        System.out.println(remoteSdp.toString());
        //saveRemoteIPPort(remoteSdp,fromLeg);
        //saveRemoteSessionInfo(remoteSdp,fromLeg);
        System.out.println("******************************6666************************");
        SessionDescription forwardSdp = modifySdpToForward(remoteSdp, getSupportCodec(), preferMedia);
        //System.out.println("=============================================");
        System.out.println(forwardSdp.toString());
        System.out.println("**************************************************************");
        //saveMediaNegotiationResult(forwardSdp);
        //updateForwardSdpVersion(forwardSdp,fromLeg);
    }

    public byte[] processPlayRightSDP(byte[] rawContent) {
        return processPlayRightSDP(rawContent, true);
    }

    public byte[] processPlayRightSDP(byte[] rawContent, boolean needPrecondition) {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);

        String mediaCode = "";
        String telephone_event = "";
        List<String> codes = new ArrayList();
        ArrayList<String> audioNameSupport = new ArrayList<String>();   //支持的媒体名称


        IPPort ipportRemote = new IPPort();


        boolean isBeforeMediaLine = true;
        boolean isNeedAppend = false;
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        String pcmaLine = "";
        String pcmuLine = "";
        try {
            String line = br.readLine();
            System.out.println("-----" + line);
            while (line != null) {
                if (line.startsWith("o=")) {

                    String o[] = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    sdpSession.acceptRightO(line);
                    o[1] = sdpSession.getLeftLocalSessionId();
                    o[2] = sdpSession.getLeftLocalSessionVersion() + "";

                    for (String s : o) {
                        sb.append(s).append(" ");
                    }
                    //去掉最末尾的" "
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    line = sb.toString();
                }
                if (line.indexOf("m=audio") > -1) {
                    //m=audio 50706 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0
                    ipportRemote.port = Integer.parseInt(line.split(" ")[1]);
                    String[] parts = line.split(" ");
                    codes = Arrays.asList(parts);
                    isBeforeMediaLine = false;
                    isNeedAppend = false;
                } else if (line.indexOf("c=") > -1) {
                    //c=IN IP4 10.188.28.49
                    //该方法用于半段协商，模拟右腿Ip，commit时右腿ip设为xmedia的ip。（因为目前只有对主叫放音的早期业务）
                    ipportRemote.ip = this.proxyEndpoint.ip;
                    line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                    isNeedAppend = true;
                } else if (line.indexOf("a=rtpmap") > -1) { //媒体申明行
                    // a=rtpmap:18 G729/8000
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    String codeName = parts[1].split("/")[0].toLowerCase();
                    // 支持的编码
                    if (preferMediaCodec != null && !preferMediaCodec.equalsIgnoreCase("")) {
                        if (codeName.equalsIgnoreCase(preferMediaCodec) || codeName.equalsIgnoreCase("telephone-event")) {
                            if (codeName.contains("telephone-event")) {
                                if (telephone_event.isEmpty()) {
                                    telephone_event = codeNumber;
                                    isNeedAppend = true;
                                } else {
                                    isNeedAppend = false;
                                }
                            } else if (mediaCode.isEmpty()) {
                                audioNameSupport.add(codeName);
                                mediaCode = codeNumber;
                                isNeedAppend = true;
                            } else {
                                //因为isNeedAppend是共有变量，有可能上次读进来的结果是需要添加的，那么这次如果不设值，会把不需要添加的也添加进去
                                isNeedAppend = false;
                            }
                        } else {
                            if(codeName.equalsIgnoreCase("pcma")) {
                                pcmaLine = line;
                            }
                            if(codeName.equalsIgnoreCase("pcmu")){
                                pcmuLine = line;
                            }
                            isNeedAppend = false;
                        }
                    } else {
                        // 支持的编码
                        if (getSupportCodec() != null && getSupportCodec().contains(codeName)) {
                            if (codeName.contains("telephone-event")) {
                                if (telephone_event.isEmpty()) {
                                    telephone_event = codeNumber;
                                    isNeedAppend = true;
                                } else {
                                    isNeedAppend = false;
                                }
                            } else if (mediaCode.isEmpty()) {
                                audioNameSupport.add(codeName);
                                mediaCode = codeNumber;
                                isNeedAppend = true;
                            } else {
                                isNeedAppend = false;
                            }
                        } else {
                            isNeedAppend = false;
                        }
                    }

                } else if (line.indexOf("a=fmtp") > -1) {
                    //媒体属性行
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    if (mediaCode.equalsIgnoreCase(codeNumber) || telephone_event.equalsIgnoreCase(codeNumber)) {
                        isNeedAppend = true;
                    } else {
                        isNeedAppend = false;
                    }
                } else if (line.startsWith("a=curr:") || line.startsWith("a=des:") || line.startsWith("a=sendrecv")) {
                    isNeedAppend = false;
                } else {
                    isNeedAppend = true;
                }

                if (isNeedAppend) {
                    if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }


            this.rightEndpointTemp = ipportRemote;


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (StringUtils.isEmpty(mediaCode)) {
            if (codes.size() != 0) {
                for (String str : codes) {
                    if (str.equalsIgnoreCase(PCMA_PLAY_LOAD) ) {
                        mediaCode = str;
                        if(!StringUtils.isEmpty(pcmaLine)){
                            sbAfter.append(pcmaLine).append("\r\n");
                        }

                        break;
                    } else if(str.equalsIgnoreCase(PCMU_PLAY_LOAD)){
                        mediaCode = str;
                        if(!StringUtils.isEmpty(pcmuLine)){
                            sbAfter.append(pcmuLine).append("\r\n");
                        }
                        break;
                    }

                }
            } else {
                mediaCode = PCMA_PLAY_LOAD;
                if(!StringUtils.isEmpty(pcmaLine)){
                    sbAfter.append(pcmaLine).append("\r\n");
                }
            }
        }

        sbBefore = addConnectionIfSessionAbsence(sbBefore);

        if (!StringUtils.isEmpty(telephone_event)) {
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode + " " + telephone_event)).append("\r\n").append(sbAfter);
        } else {
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode)).append("\r\n").append(sbAfter);
        }


//        System.out.println("-----------------");
//        System.out.println(String.join(" ", audioCodeSupport));
//        System.out.println(String.join(" ", audioNameSupport));
//        System.out.println("-----------------");

        if (audioNameSupport.size() >= 1) {
            this.mediaTemp = audioNameSupport.get(0);
            this.codeTemp = Integer.valueOf(mediaCode);
            if (!StringUtils.isEmpty(telephone_event)) {
                this.telephonePlayLoadTemp = Integer.valueOf(telephone_event);
            }
            System.out.println("-----------------" + codeTemp + " " + mediaTemp);
        } else {
            if (mediaCode.equals(PCMA_PLAY_LOAD)) {
                this.mediaTemp = "pcma";
                this.codeTemp = 8;
            }
            if (mediaCode.equals(PCMU_PLAY_LOAD)) {
                this.mediaTemp = "pcmu";
                this.codeTemp = 0;
            }
            if (!StringUtils.isEmpty(telephone_event)) {
                this.telephonePlayLoadTemp = Integer.valueOf(telephone_event);
            }
        }
        if (needPrecondition) {
            sbBefore.append("a=sendrecv\r\n" +
                    "a=curr:qos local sendrecv\r\n" +
                    "a=curr:qos remote none\r\n" +
                    "a=des:qos optional local sendrecv\r\n" +
                    "a=des:qos mandatory remote sendrecv\r\n" +
                    "a=conf:qos remote sendrecv\r\n");
        }


        return sbBefore.toString().getBytes();
    }


    /**
     * 收号前协商,优先选择G711编码
     * @param rawContent sdp content
     * @param needPrecondition qos
     * @param forceInBand force in band dtmf
     * @return sdp with only chosen media
     *
     */
    public byte[] processDtmfRightSDP(byte[] rawContent, boolean needPrecondition, boolean forceInBand) {

        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(rawContent)));
        String mediaCode = "";
        String telephoneEventCodeConfirm = "";
        List<String> supportAudioCodeList = new ArrayList<>();
        IPPort remoteIpPort = new IPPort();
        boolean isBeforeMediaLine = true;
        boolean isNeedAppend;
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        // 保证一下加入的顺序，优先选择排在前面的audio编码
        LinkedHashMap<String, RtpAttribute> supportCodeRtpAttributeMap = new LinkedHashMap<>();
        HashMap<String, RtpAttribute> supportTelephoneEventMap = new HashMap<>();
        // 初始化一下免得反复判断
        if(supportedCodec == null) {
            supportedCodec = new HashSet<>();
        }

        try {
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("o=")) {
                    String[] o = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    sdpSession.acceptRightO(line);
                    o[1] = sdpSession.getLeftLocalSessionId();
                    o[2] = sdpSession.getLeftLocalSessionVersion() + "";
                    for (String s : o) { sb.append(s).append(" "); }
                    line = sb.toString().trim();
                    isNeedAppend = true;
                }else if (line.contains("m=audio")) {
                    // 此时没有右腿，此时传给xmedia的ip+port需是不可达地址
                    remoteIpPort.port = 0;
                    supportAudioCodeList = Arrays.asList(line.split(" "));
                    isBeforeMediaLine = false;
                    isNeedAppend = false;
                } else if (line.contains("c=")) {
                    // c=IN IP4 10.188.28.49
                    remoteIpPort.ip = "0.0.0.0";
                    line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                    isNeedAppend = true;
                } else if (line.contains("a=rtpmap")) {
                    // a=rtpmap:18 G729/8000
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    String codeName = parts[1].split("/")[0].toLowerCase();
                    // 收号前与主叫侧的协商优先选择pcma
                    isNeedAppend = false;
                    if (getSupportCodec().contains(codeName)) {
                        if (codeName.contains("telephone-event")) {
                            supportTelephoneEventMap.put(codeNumber, new RtpAttribute(line));
                        } else {
                            supportCodeRtpAttributeMap.put(codeNumber, new RtpAttribute(line));
                        }
                    }
                } else if (line.contains("a=fmtp")) {
                    // 媒体属性行, 只有前面被选择的编码才能保留，这里默认a=rtpmap一定会出现在a=fmtp前面
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    if (supportCodeRtpAttributeMap.containsKey(codeNumber)) {
                        RtpAttribute tempRtpAttribute = supportCodeRtpAttributeMap.get(codeNumber);
                        tempRtpAttribute.acceptFmtp(line);
                        supportCodeRtpAttributeMap.put(codeNumber, tempRtpAttribute);
                    } else if(supportTelephoneEventMap.containsKey(codeNumber)) {
                        RtpAttribute tempRtpAttribute = supportTelephoneEventMap.get(codeNumber);
                        tempRtpAttribute.acceptFmtp(line);
                        supportTelephoneEventMap.put(codeNumber, tempRtpAttribute);
                    }
                    isNeedAppend = false;
                } else {
                    isNeedAppend = !(line.startsWith("a=curr:") || line.startsWith("a=des:") || line.startsWith("a=sendrecv") || line.startsWith("a=conf"));
                }
                if (isNeedAppend) {
                    if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }
            this.rightEndpointTemp = remoteIpPort;
        } catch (Exception e) {
            logger.error("process sdp error {}", (Object) e.getStackTrace());
        }
        // 补偿PCMA或PCMU不提供rtpmap属性的
        if (supportAudioCodeList.contains(PCMA_PLAY_LOAD) && getSupportCodec().contains("pcma") && !supportCodeRtpAttributeMap.containsKey(PCMA_PLAY_LOAD)) {
            supportCodeRtpAttributeMap.put(PCMA_PLAY_LOAD, new RtpAttribute("a=rtpmap:8 PCMA/8000"));
        }
        if (supportAudioCodeList.contains(PCMU_PLAY_LOAD) && getSupportCodec().contains("pcmu") && !supportCodeRtpAttributeMap.containsKey(PCMU_PLAY_LOAD)) {
            supportCodeRtpAttributeMap.put(PCMU_PLAY_LOAD, new RtpAttribute("a=rtpmap:0 PCMU/8000"));
        }
        if (!forceInBand) {
            // 基于audio编码出现的顺序进行选择对应的telephone-event编码
            outer:
            for (Map.Entry<String, RtpAttribute> audioEntry : supportCodeRtpAttributeMap.entrySet()) {
                 for (Map.Entry<String, RtpAttribute> telephoneEventEntry : supportTelephoneEventMap.entrySet()){
                    if (telephoneEventEntry.getValue().getClockRate().equals(audioEntry.getValue().getClockRate())) {
                        telephoneEventCodeConfirm = telephoneEventEntry.getValue().getCode();
                        mediaCode = audioEntry.getValue().getCode();
                        if (applyNewCodecStrategy) {
                            break outer;
                        } else {
                            // 不使用新策略则维持原样，PCMA优先
                            if ("PCMA".equalsIgnoreCase(audioEntry.getValue().getName())) {
                                break outer;
                            } else if ("PCMU".equalsIgnoreCase(audioEntry.getValue().getName()) && !supportAudioCodeList.contains(PCMA_PLAY_LOAD)) {
                                break outer;
                            }
                        }
                    }
                }
            }

        }
        // 在强制带内的情况下和没有与telephone-event采样率相匹配的两种情况下mediacode会为空
        if (StringUtils.isEmpty(mediaCode)) {
            if (!supportAudioCodeList.isEmpty()) {
                for (String str : supportAudioCodeList) {
                    if (PCMA_PLAY_LOAD.equalsIgnoreCase(str) || PCMU_PLAY_LOAD.equalsIgnoreCase(str)) {
                        mediaCode = str;
                        if (supportCodeRtpAttributeMap.containsKey(str)) {
                            break;
                        }
                    }
                }
                // 如果上面的循环没有选择到PCMA或者PCMU则表明主叫发送过来的offer中没有8或者0，在下面进行正常的编码选择
                if (StringUtils.isEmpty(mediaCode)) {
                    outer:
                    for (Map.Entry<String, RtpAttribute> audioEntry : supportCodeRtpAttributeMap.entrySet()) {
                        for (Map.Entry<String, RtpAttribute> telephoneEventEntry : supportTelephoneEventMap.entrySet()){
                            if (telephoneEventEntry.getValue().getClockRate().equals(audioEntry.getValue().getClockRate())) {
                                telephoneEventCodeConfirm = telephoneEventEntry.getValue().getCode();
                                mediaCode = audioEntry.getValue().getCode();
                                break outer;
                            }
                        }
                    }
                }
            } else {
                mediaCode = "8";
            }
        }

        if (!StringUtils.isEmpty(mediaCode)) {
            this.mediaTemp = supportCodeRtpAttributeMap.get(mediaCode).getName().toLowerCase();
            this.codeTemp = Integer.parseInt(mediaCode);
            String rtpmap = supportCodeRtpAttributeMap.get(mediaCode).getRtpmap();
            if (rtpmap != null) {
                sbAfter.append(rtpmap).append("\r\n");
            }
            String fmtp = supportCodeRtpAttributeMap.get(mediaCode).getFmtp();
            if (fmtp != null) {
                sbAfter.append(fmtp).append("\r\n");
            }
        } else {
            if (mediaCode.equals(PCMA_PLAY_LOAD)) {
                this.mediaTemp = "pcma";
                this.codeTemp = 8;
            }
            if (mediaCode.equals(PCMU_PLAY_LOAD)) {
                this.mediaTemp = "pcmu";
                this.codeTemp = 0;
            }
        }
        if (!StringUtils.isEmpty(telephoneEventCodeConfirm)) {
            this.telephonePlayLoadTemp = Integer.parseInt(telephoneEventCodeConfirm);
            sbAfter.append(supportTelephoneEventMap.get(telephoneEventCodeConfirm).getRtpmap()).append("\r\n");
            if (StringUtils.isNotBlank(supportTelephoneEventMap.get(telephoneEventCodeConfirm).getFmtp())) {
                sbAfter.append(supportTelephoneEventMap.get(telephoneEventCodeConfirm).getFmtp()).append("\r\n");
            }
        }

        sbBefore = addConnectionIfSessionAbsence(sbBefore);

        if (!StringUtils.isEmpty(telephoneEventCodeConfirm)) {
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode + " " + telephoneEventCodeConfirm)).append("\r\n").append(sbAfter);
        } else {
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode)).append("\r\n").append(sbAfter);
        }
        if (needPrecondition) {
            sbBefore.append("a=sendrecv\r\n" +
                    "a=curr:qos local sendrecv\r\n" +
                    "a=curr:qos remote none\r\n" +
                    "a=des:qos optional local sendrecv\r\n" +
                    "a=des:qos mandatory remote sendrecv\r\n" +
                    "a=conf:qos remote sendrecv\r\n");
        }
        return sbBefore.toString().getBytes();
    }

    public byte[] processVideoPlayRightSDP(byte[] rarContent){
        return processVideoPlayRightSDP(rarContent,true);
    }

    public byte[] processVideoPlayRightSDP(byte[] rawContent,boolean needPrecondition) {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);

        String media = "";
        String mediaCode = "";
        String telephone_event = "";


        IPPort ipportRemote = new IPPort();
        ArrayList<String> audioCodeSupport = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> audioNameSupport = new ArrayList<String>();   //支持的媒体名称
        ArrayList<String> rtpLines = new ArrayList<>();                 //支持的媒体RTP行
        HashMap<String, ArrayList<String>> fmtpLines = new HashMap<>();  //支持的媒体FMTP行
        boolean isBeforeMediaLine = true;
        boolean isNeedAppend = false;
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        try {
            String line = br.readLine();
            System.out.println("-----" + line);
            while (line != null) {
                if (line.contains("m=video")) {

                    break;
                }
                if (line.indexOf("m=audio") > -1) {
                    //m=audio 50706 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0
                    ipportRemote.port = Integer.parseInt(line.split(" ")[1]);
                    String[] parts = line.split(" ");
                    isBeforeMediaLine = false;
                    isNeedAppend = false;
                } else if (line.startsWith("o=")) {
                    String[] o = line.split(" ");
                    StringJoiner sj = new StringJoiner(" ");
                    sdpSession.acceptRightO(line);
                    o[1] = sdpSession.getLeftLocalSessionId();
                    o[2] = sdpSession.getLeftLocalSessionVersion() + "";
                    for (String s : o) {
                        sj.add(s);
                    }
                    line = sj.toString();
                    isNeedAppend = true;
                } else if (line.indexOf("c=") > -1) {
                    //c=IN IP4 10.188.28.49
                    ipportRemote.ip = this.proxyEndpoint.ip;
                    line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                    isNeedAppend = true;
                } else if (line.indexOf("a=rtpmap") > -1) { //媒体申明行
                    // a=rtpmap:18 G729/8000
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    String codeName = parts[1].split("/")[0].toLowerCase();
                    String codeFullname = codeName;
                    // 支持的编码(视频彩铃只支持amr-wb)
                    if (codeName.equalsIgnoreCase("amr-wb")
                            || codeName.equalsIgnoreCase("telephone-event")
                    ) {
                        if (codeFullname.contains("telephone-event") && telephone_event.isEmpty()) {
                            telephone_event = codeNumber;
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            rtpLines.add(line);
                        } else {
                            audioCodeSupport.add(codeNumber);
                            audioNameSupport.add(codeFullname);
                            rtpLines.add(line);
                        }
                        isNeedAppend = false;
                    } else {
                        isNeedAppend = false;
                    }
                } else if (line.indexOf("a=fmtp") > -1) {
                    //媒体属性行
                    String[] parts = line.split(" ");
                    String codeNumber = parts[0].split(":")[1];
                    int pos = audioCodeSupport.indexOf(codeNumber);
                    if (pos > -1) {
                        if (line.contains("octet-align=1")) {
                            String name = audioNameSupport.get(pos);
                            audioNameSupport.set(pos, name + "-octet");
                        }
                        ArrayList<String> fmtpLineList = fmtpLines.get(codeNumber);
                        if (fmtpLineList != null) {
                            fmtpLineList.add(line);
                        } else {
                            fmtpLineList = new ArrayList<>();
                            fmtpLineList.add(line);
                            fmtpLines.put(codeNumber, fmtpLineList);
                        }
                    }
                    isNeedAppend = false;
                } else if (line.startsWith("a=curr:") || line.startsWith("a=des:") || line.startsWith("a=sendrecv")) {
                    isNeedAppend = false;
                } else {
                    isNeedAppend = true;
                }

                if (isNeedAppend) {
                    if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }


            this.rightEndpointTemp = ipportRemote;
            //视频彩铃编解码优先级
            for (String codeName : codePriority) {
                int pos = audioNameSupport.indexOf(codeName);
                if (pos > -1) {
                    media = codeName;
                    mediaCode = audioCodeSupport.get(pos);
                    sbAfter.append(rtpLines.get(pos)).append("\r\n");
                    ArrayList<String> fmtpLineList = fmtpLines.get(mediaCode);
                    if (fmtpLineList != null) {
                        for (String fmtpLine : fmtpLineList) {
                            sbAfter.append(fmtpLine).append("\r\n");
                        }
                    }
                    break;
                }
            }
            //telephone-event添加
            if (audioNameSupport.contains("telephone-event")) {
                sbAfter.append(rtpLines.get(audioNameSupport.indexOf("telephone-event"))).append("\r\n");
                ArrayList<String> fmtpLineList = fmtpLines.get(audioCodeSupport.get(audioNameSupport.indexOf("telephone-event")));
                if (fmtpLineList != null) {
                    for (String fmtpLine : fmtpLineList) {
                        sbAfter.append(fmtpLine).append("\r\n");
                    }
                }
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        sbBefore = addConnectionIfSessionAbsence(sbBefore);

        sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode + " " + telephone_event)).append("\r\n").append(sbAfter);

//        System.out.println("-----------------");
//        System.out.println(String.join(" ", audioCodeSupport));
//        System.out.println(String.join(" ", audioNameSupport));
//        System.out.println("-----------------");


        this.mediaTemp = media;
        this.codeTemp = Integer.valueOf(mediaCode);
        if (!telephone_event.equalsIgnoreCase("")) {
            this.telephonePlayLoadTemp = Integer.valueOf(telephone_event);
        }
        System.out.println("-----------------" + codeTemp + " " + mediaTemp);

        if(needPrecondition){
            sbBefore.append("a=sendrecv\n" +
                    "a=curr:qos local sendrecv\n" +
                    "a=curr:qos remote none\n" +
                    "a=des:qos optional local sendrecv\n" +
                    "a=des:qos mandatory remote sendrecv\n" +
                    "a=conf:qos remote sendrecv\n");
        }

        return sbBefore.toString().getBytes();
    }

    public String getMedia(){
        return this.media;
    }

    public int getTelephoneEventCode(){
        return telephonePlayLoad;
    }

    public enum VIDEO_MODE {
        CLOSE,             //关闭 video
        THROUGH_XMEDIA,    //video 经过 xmedia
        WITHOUT_XMEDIA,    //video 透传
        REMOVE_VIDEO_SDP   //删除 video SDP
    }
    /**
     *
     * @param rawContent
     * @param retainedCode
     * @param fromLeg
     * @param videoMode 0 - 关闭 video； 1 - video 经过 xmedia; 2 - video 透传; 3 - 删除 video SDP
     * @return
     */
    public String filterSDPWithSpecifiedCode(byte[] rawContent, String retainedCode, String fromLeg, VIDEO_MODE videoMode) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);

        String media = "";
        String mediaCode = "";
        String telephone_event = "";

        IPPort ipportRemote = new IPPort();
        HashSet<String> telephoneEventCodeSet = new HashSet<>();
        ArrayList<String> audioCodeSupport = new ArrayList<String>();   //支持的媒体编码
        ArrayList<String> audioNameSupport = new ArrayList<String>();   //支持的媒体名称
        ArrayList<String> rtpLines = new ArrayList<>();                 //支持的媒体RTP行
        HashMap<String, ArrayList<String>> fmtpLines = new HashMap<>();  //支持的媒体FMTP行
        boolean isBeforeMediaLine = true;
        boolean isNeedAppend = false;
        StringBuilder sbBefore = new StringBuilder();
        StringBuilder sbAfter = new StringBuilder();
        StringBuilder sbVideo = new StringBuilder();
        String currentMedia = "";
        ArrayList<String> listMedia = new ArrayList<>();
        String line = br.readLine();
        String audioLine = "";
        while (line != null) {
            if (line.contains("m=video")) {
                if (videoLevelRecorder==null){
                    videoLevelRecorder = new VideoLevelRecorder(sessionId);
                }
                videoLevelRecorder.recordVideoParams(MediaSession.LEG_LEFT.equals(fromLeg), rawContent);
                if (videoMode == VIDEO_MODE.REMOVE_VIDEO_SDP) {
                    break;
                }
                currentMedia = "video";
                listMedia.add(currentMedia);
                String[] parts = line.split(" ");
                if (videoMode == VIDEO_MODE.CLOSE) {
                    parts[1] = "0";
                } else if (videoMode == VIDEO_MODE.THROUGH_XMEDIA) {
                    parts[1] = String.valueOf(this.videoport);
                }
                StringJoiner sj = new StringJoiner(" ");
                for (String part : parts) {
                    sj.add(part);
                }
                line = sj.toString();
                isNeedAppend = true;
            }
            if (line.indexOf("m=audio") > -1) {
                currentMedia = "audio";
                audioLine = line;
                listMedia.add(currentMedia);
                //m=audio 50706 RTP/AVP 104 110 102 108 105 100 111 106 18 8 0
                ipportRemote.port = Integer.parseInt(line.split(" ")[1]);
                String[] parts = line.split(" ");
                isBeforeMediaLine = false;
                isNeedAppend = false;
            } else if (line.startsWith("o=")) {
                String[] o = line.split(" ");
                StringJoiner sj = new StringJoiner(" ");
                if (fromLeg != null && MediaSession.LEG_LEFT.equals(fromLeg)) {
                    sdpSession.acceptLeftO(line);
                    o[1] = sdpSession.getRightLocalSessionId();
                    o[2] = sdpSession.getRightLocalSessionVersion() + "";
                } else {
                    sdpSession.acceptRightO(line);
                    o[1] = sdpSession.getLeftLocalSessionId();
                    o[2] = sdpSession.getLeftLocalSessionVersion() + "";
                }

                for (String s : o) {
                    sj.add(s);
                }
                line = sj.toString();
                isNeedAppend = true;
            } else if (line.indexOf("c=") > -1) {
                //c=IN IP4 10.188.28.49
                if(StringUtils.isBlank(currentMedia) || "audio".equalsIgnoreCase(currentMedia)){
                    ipportRemote.ip = line.split(" ")[2];
                }
                //TODO: 针对 videoMode 处理 videoIP
                line = String.format("c=IN IP4 %s", proxyEndpoint.ip);
                isNeedAppend = true;
            } else if (!currentMedia.equals("video") && line.indexOf("a=rtpmap") > -1) { //媒体申明行
                // a=rtpmap:18 G729/8000
                String[] parts = line.split(" ");
                String codeNumber = parts[0].split(":")[1];
                String codeName = parts[1].split("/")[0].toLowerCase();
                String codeFullname = codeName;
                // 支持的编码(视频彩铃只支持amr-wb)
                if (codeName.equalsIgnoreCase(retainedCode)
                        || codeName.equalsIgnoreCase("telephone-event")
                ) {
                    // 把所有的出现的telephone-event都存下来
                    if (codeFullname.contains("telephone-event")) {
                        // telephone_event = codeNumber;
                        telephoneEventCodeSet.add(codeNumber);
                        audioCodeSupport.add(codeNumber);
                        audioNameSupport.add(codeFullname);
                        rtpLines.add(line);
                    } else {
                        audioCodeSupport.add(codeNumber);
                        audioNameSupport.add(codeFullname);
                        rtpLines.add(line);
                    }
                    isNeedAppend = false;
                } else {
                    isNeedAppend = false;
                }
            } else if (!currentMedia.equals("video") && line.indexOf("a=fmtp") > -1) {
                //媒体属性行
                String[] parts = line.split(" ");
                String codeNumber = parts[0].split(":")[1];
                int pos = audioCodeSupport.indexOf(codeNumber);
                if (pos > -1) {
                    if (line.contains("octet-align=1")) {
                        String name = audioNameSupport.get(pos);
                        audioNameSupport.set(pos, name + "-octet");
                    }
                    ArrayList<String> fmtpLineList = fmtpLines.get(codeNumber);
                    if (fmtpLineList != null) {
                        fmtpLineList.add(line);
                    } else {
                        fmtpLineList = new ArrayList<>();
                        fmtpLineList.add(line);
                        fmtpLines.put(codeNumber, fmtpLineList);
                    }
                }
                isNeedAppend = false;
            } else if (line.startsWith("a=curr:") || line.startsWith("a=des:") || line.startsWith("a=conf:")) {
                isNeedAppend = false;
            } else {
                isNeedAppend = true;
            }

            if (isNeedAppend) {
                if (currentMedia.equals("video")) {
                    sbVideo.append(line).append("\r\n");
                } else {
                    if (isBeforeMediaLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }

            }
            line = br.readLine();
        }


        //this.rightEndpointTemp = ipportRemote;
        //视频彩铃编解码优先级
        for (String codeName : preferCodePriority) {
            int pos = audioNameSupport.indexOf(codeName);
            if (pos > -1) {
                media = codeName;
                mediaCode = audioCodeSupport.get(pos);
                sbAfter.append(rtpLines.get(pos)).append("\r\n");
                ArrayList<String> fmtpLineList = fmtpLines.get(mediaCode);
                if (fmtpLineList != null) {
                    for (String fmtpLine : fmtpLineList) {
                        sbAfter.append(fmtpLine).append("\r\n");
                    }
                }
                break;
            }
        }
        if(StringUtils.isBlank(mediaCode) || StringUtils.isBlank(media)){
            String tempMedia = "";
            String tempMediaCode = "";
            if(StringUtils.equalsIgnoreCase(retainedCode,"pcma")){
                tempMedia = "pcma";
                tempMediaCode = "8";
            }else if(StringUtils.equalsIgnoreCase(retainedCode,"pcmu")){
                tempMedia = "pcmu";
                tempMediaCode = "0";
            }
            String[] parts = audioLine.split(" ");
            for(String part:parts){
                if(part.equals(tempMediaCode)){
                    media = tempMedia;
                    mediaCode = tempMediaCode;
                    break;
                }
            }
        }
        // 如果编码中有telephone-event
        if (audioNameSupport.contains("telephone-event")) {

            String currentMediaCodeClockRate = "";
            // 如果当前的audio编码是由原SDP中选择的
            int mediaCodeIndex = audioCodeSupport.indexOf(mediaCode);
            if (mediaCodeIndex > -1) {
                RtpAttribute mediaRtpMap = new RtpAttribute(rtpLines.get(mediaCodeIndex));
                currentMediaCodeClockRate = mediaRtpMap.getClockRate();
            }
            if (StringUtils.isBlank(currentMediaCodeClockRate)) {
                currentMediaCodeClockRate = defaultCodeClockRate.get(media.toUpperCase());
            }
            // 遍历出现过的所有telephone-event编码，一般是两个
            for (String teCode : telephoneEventCodeSet){
                int currentIndex = audioCodeSupport.indexOf(teCode);
                RtpAttribute telePhoneEventRtpAttribute = new RtpAttribute(rtpLines.get(currentIndex));
                // 选择与当前编码的clock_rate一致的telephone-event编码
                if (telePhoneEventRtpAttribute.getClockRate().equals(currentMediaCodeClockRate)){
                    telephone_event = teCode;
                    sbAfter.append(rtpLines.get(currentIndex)).append("\r\n");
                    ArrayList<String> fmtpLineList = fmtpLines.get(teCode);
                    if (fmtpLineList != null) {
                        for (String fmtpLine : fmtpLineList) {
                            sbAfter.append(fmtpLine).append("\r\n");
                        }
                    }
                    break;
                }
            }
            // 有可能选不到匹配的telephone-event，目前时直接不协商，不append
        }

        sbBefore = addConnectionIfSessionAbsence(sbBefore);

        String firstMedia = listMedia.get(0);
        if("audio".equalsIgnoreCase(firstMedia)){
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode + " " + telephone_event)).append("\r\n").append(sbAfter).append(sbVideo);
        }else if("video".equalsIgnoreCase(firstMedia)){
            sbBefore.append(sbVideo).append(String.format("m=audio %d RTP/AVP %s", proxyEndpoint.port, mediaCode + " " + telephone_event)).append("\r\n").append(sbAfter);
        }

        if(fromLeg != null){
            synchronized (this) {
                if (MediaSession.LEG_LEFT.equals(fromLeg)) {
                    this.leftEndpointTemp = ipportRemote;
                } else {
                    this.rightEndpointTemp = ipportRemote;
                }
            }
        }
        logger.info("[{}], filterSdp mediaCode:{} media:{} telephone_event:{}", sessionId,mediaCode,media,telephone_event);
        this.mediaTemp = media;
        this.codeTemp = Integer.valueOf(mediaCode);
        if (!telephone_event.equalsIgnoreCase("")) {
            this.telephonePlayLoadTemp = Integer.valueOf(telephone_event);
        }
        //System.out.println("filterSdp mediaCode:"+mediaCode+" media:"+ media+" telephone_event:"+telephone_event);

        /*sbBefore.append("a=sendrecv\n" +
                "a=curr:qos local sendrecv\n" +
                "a=curr:qos remote none\n" +
                "a=des:qos optional local sendrecv\n" +
                "a=des:qos mandatory remote sendrecv\n" +
                "a=conf:qos remote sendrecv\n");
        logger.info("======\n" + sbBefore.toString());*/

        return sbBefore.toString();
    }

    public byte[] processLeftSDP(byte[] rawContent) {
        if (isRetainNetVideo()) {
            return this.processSDPWithVideo(rawContent, MediaSession.LEG_LEFT, null);
        }
        return this.processSDP(rawContent, MediaSession.LEG_LEFT, null);
    }

    public byte[] processLeftSDP(byte[] rawContent, String preferMedia) {
        if (isRetainNetVideo()) {
            return this.processSDPWithVideo(rawContent, MediaSession.LEG_LEFT, preferMedia);
        }
        return this.processSDP(rawContent, MediaSession.LEG_LEFT, preferMedia);
    }

    public byte[] processRightSDP(byte[] rawContent) {
        if (isRetainNetVideo()) {
            return this.processSDPWithVideo(rawContent, MediaSession.LEG_RIGHT, null);
        }
        return this.processSDP(rawContent, MediaSession.LEG_RIGHT, null);
    }

    public byte[] processRightSDP(byte[] rawContent, String preferMedia) {
        if (isRetainNetVideo()) {
            return this.processSDPWithVideo(rawContent, MediaSession.LEG_RIGHT, preferMedia);
        }
        return this.processSDP(rawContent, MediaSession.LEG_RIGHT, preferMedia);
    }

    /**
     * 判断是否完成视频协商
     * 返回值0,1,2
     * 0表示无变化
     * 1表示视频通道关闭
     * 2表示视频通道打开
     */

    /*public int isCompleteVideoNegotiation() {
        if(audioOfferMediaLine!=null && audioAnswerMediaLine!=null){
            int oldState = videoChannelState.getState();
            videoChannelState.acceptSdp(videoOfferMediaLine == null?null:new VideoSdp(videoOfferMediaLine),videoAnswerMediaLine == null? null: new VideoSdp(videoAnswerMediaLine));
            videoOfferMediaLine = null;
            videoAnswerMediaLine = null;
            int newState = videoChannelState.getState();
            Integer x = ifChangeState(oldState, newState);
            audioOfferMediaLine = null;
            audioAnswerMediaLine = null;
            if (x != null) return x;
        }
        return 0;
    }*/

    private Integer ifChangeState(int oldState, int newState) {
        if(oldState == VideoChannelState.CLOSE_STATE && newState == VideoChannelState.OPEN_STATE){
            return 2;
        }
        if(oldState == VideoChannelState.OPEN_STATE && newState == VideoChannelState.CLOSE_STATE){
            return 1;
        }
        return null;
    }



    public void resetEndPoint(){
        synchronized (this){
            this.leftEndpointTemp = null;
            this.rightEndpointTemp = null;
            this.mediaTemp = null;
            this.codeTemp = -1;
        }
    }


    public void processUponPickup(boolean hasPickUp) {
        this.hasPickUp = hasPickUp;
        if(StringUtils.isNotBlank(this.media) && this.media.contains("amr-wb")){
            AnalyseLog.logAudioCode(sessionId,this.media,calledRecordIndex);
        }
    }

    public void businessUpdate(String calleeNumber, int recordMode, JSONObject recordParams) {
        businessUpdate(calleeNumber, recordMode, recordParams, 0, 0, null, null, null);
    }

    public void businessUpdate(String calleeNumber, int recordMode, JSONObject recordParams, int streamMode, int streamSource, String streamWebSocketURL, JSONObject streamExtParam, MediaHook<CallBackEvent> hook) {
        businessUpdate(calleeNumber, recordMode, recordParams, 0, streamMode, streamSource, streamWebSocketURL, streamExtParam, hook);
    }

    public void vodControl(int opType, int opData) {
        if (!active) {
            return;
        }
        synchronized (this) {
            //开始同步
            int seqId = sqeIdGenerator.getAndIncrement();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("tid", seqId);
            params.put("sessionid", sessionId);
            params.put("opType", opType);
            params.put("opData", opData);
            Map<String, String> header = new HashMap<>();
            header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
            header.put("sessionid", sessionId);
            String url = host + MediaManager.VOD_CONTROL;
            MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.VOD_CONTROL);
        }

    }
    /**
     * inform xmedia of some necessary parameters the record or stream would need
     * @param calleeNumber real callee number
     * @param recordMode 1: call recording only 2: both early and call recording needed 16: no recording
     * @param recordParams record parameters which would be pass to xmerge
     * @param businessMode 1: realtime Stream only 2: realtime ASR only 3: both  (new in 1.5.42)
     * @param streamMode real time streaming mode 0:close 1:early only 2:pickup only 3:both
     * @param streamSource stream source 1:caller 2:callee 3:both
     * @param streamWebSocketURL WebSocket URL to stream
     * @param extParams businessParams for realtime stream or ASR (new in 1.5.42)
     * @param hook callback URL for media to inform xdtmf while error occur or stream over
     */
    public void businessUpdate(String calleeNumber, int recordMode, JSONObject recordParams, int businessMode,
                               int streamMode, int streamSource, String streamWebSocketURL, JSONObject extParams, MediaHook<CallBackEvent> hook) {
        if (!active) {
            return;
        }
        int tid = sqeIdGenerator.getAndIncrement();
        if (hook != null) {
            tid_Hook.put(tid, hook);
        }
        ObjectNode params = MediaManager.objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        params.put("tid", tid);
        params.put("callee", calleeNumber);
        params.put("recMode", recordMode);
        if (recordParams != null) {
            params.put("mergeParam", MediaManager.handleJson(recordParams));
        }
        params.put("businessMode", businessMode);
        params.put("streamMode", streamMode);
        params.put("streamSource", streamSource);
        params.put("streamURL", streamWebSocketURL);
        if (extParams == null || extParams.isEmpty()) {
            extParams = new JSONObject();
            extParams.put("extId", 1);
            extParams.put("separated", false);
        }
        String streamExtParamName = (String)extParams.getOrDefault("streamExtParamName", "businessParams");
        params.put(streamExtParamName, "\"" + extParams + "\"");
        params.put("streamCallback", MediaManager.CALL_BACK_URL);
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.BUSINESSUPDATE;
        Hook<ObjectNode> bUpdateHook = resp -> {
            if (resp.get("http_code").asInt() != 200) {
                this.isBUpdateSuccess = false;
            }
        };
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.BUSINESSUPDATE, bUpdateHook);
    }

    public void arriveInv200() {
        arriveInv200(0);
    }
    public void arriveInv200(long callStartTime) {
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("seqId", seqId);
        params.put("sessionid", sessionId);
        if (callStartTime > 0) {
            params.put("startTime", DateUitl.formatDateyyyyMMddHHmmss(new Date(callStartTime)));
        }
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + PICKUP;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PICKUP);
    }

    public String getCompletePlayCode(String playCode) {
        if (SPECIAL_CODE_PATTERN.matcher(playCode).matches()){ //放音url为特殊编码不做处理
            return playCode;
        }
        String[] arrPlayCode = playCode.split(",");
        String newPlayCode = "";
        for (String item : arrPlayCode) {
            String newCode;
            if (StringUtils.isBlank(item)) {
                newCode = "";
            } else if (this.media.startsWith("amr-wb")) {
                newCode = item + "_wb.amr";
            } else if (this.media.startsWith("amr")) {
                newCode = item + "_nb.amr";
            } else if(this.media.startsWith("pcmu")){
                newCode = item + "_pcmu.wav";
            } else if(this.media.startsWith("pcma")){
                newCode = item + "_pcma.wav";
            } else {
                newCode = item + ".wav";
            }
            if (StringUtils.isNotEmpty(newPlayCode)) {
                newPlayCode += ",";
            }
            newPlayCode += newCode;
        }
        return newPlayCode;
    }


    public void play(String file, int direc, int recMode) {
        play(file,direc,recMode,null);
    }

    public int play(String file, int direc, int recMode, MediaHook<CallBackEvent> hook) {
        return play(file, direc, recMode, hook, null,false);
    }

    /**
     * file 参数是文件路径
     * Direc = 1 代表给左腿放音
     * Direc = 2 代表给右腿放音
     * Direc = 3 代表两条腿都放音
     * Direc = 4 代表两条腿都放音并且放不同的音，file参数格式 fileLeft.wav;fileRight.wav
     */
    public int play(String file, int direc, int recMode, MediaHook<CallBackEvent> hook, String callBackUrl, boolean passThrough) {
        if (!active) {
            return 0;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        //根据新的中间号接口规范，如果说带有" ; "的话，就是接通后左右腿放不同音
        if (file.contains(";")) {
            String files[] = file.split(";");
            //根据file数组的长度分情况讨论
            if (files.length == 2) {
                String fileLeft = file.split(";")[0];
                String fileRight = file.split(";")[1];
                if (!fileLeft.isEmpty()) {
                    direc = 4;
                    file = getCompletePlayCode(fileLeft) + ";" + getCompletePlayCode(fileRight);
                } else {
                    //只给右腿放音
                    direc = 2;
                    file = getCompletePlayCode(fileRight);
                }
            } else if (files.length == 1) {
                //长度为1时，只有左腿放音
                direc = 1;
                String fileLeft = file.split(";")[0];
                file = getCompletePlayCode(fileLeft);
            } else {
                //长度为0时，不放音
                return 0;
            }

        } else {
            file = getCompletePlayCode(file);
        }

        params.put("file", file);
        params.put("direc", direc);
        params.put("seqId", seqId);
        params.put("code", this.media);
        params.put("recMode",recMode);
        params.put("passThrough",passThrough);

        if (hook == null) {
            params.put("event", false);
        } else {
            params.put("event", true);
            //params.put("backurl", MediaManager.CALL_BACK_URL);
            if(StringUtils.isBlank(callBackUrl)){
                params.put("backurl", MediaManager.CALL_BACK_URL);
            }else{
                params.put("backurl", callBackUrl);
            }
            tid_Hook.put(seqId, hook);
        }
        String url = host + PLAY;
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
//        HttpAgent.getInstance().postJson(url, params);
        MediaManager.postJsonWithRetry(url, sessionId, header, params, PLAY);
        return seqId;
//        logger.info("[" + sessionid + "],[httpsend-play] \n_params_" + params.toString() + "\n_header_" + header.toString());
    }



    public void playEarlySilence(int direc, MediaHook<CallBackEvent> hook, boolean isForce) {
        if (isForce) {
            playearly(SHORT_SILENCE_FILE_NAME, direc, true, hook, true);
        } else {
            playearly(LONG_SILENCE_FILE_NAME, direc, false, hook, true);
        }
    }

    public void playearly(String file, int direc, boolean isForce, MediaHook<CallBackEvent> hook) {
        playearly(file, direc, isForce, hook, false);
    }

    public void playearly(String file, int direc, boolean isForce, MediaHook<CallBackEvent> hook, boolean preferRightRtp) {
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        playTid = seqId;
        isPlaying = true;
        params.put("sessionid", sessionId);

        String[] arrPlayCode = file.split(",");
        String newPlayCode = "";
        for (String item : arrPlayCode) {
            String newCode = item;
            if (this.media.startsWith("amr-wb")) {
                newCode = item + "_wb.amr";
            } else if (this.media.startsWith("amr")) {
                newCode = item + "_nb.amr";
            } else if(this.media.startsWith("pcmu")){
                newCode = item + "_pcmu.wav";
            } else if(this.media.startsWith("pcma")){
                newCode = item + "_pcma.wav";
            }else{
                newCode = item + ".wav";
            }
            if (StringUtils.isNotEmpty(newPlayCode)) {
                newPlayCode += ",";
            }
            newPlayCode += newCode;
        }

        params.put("file", newPlayCode);
        params.put("direc", direc);
        params.put("seqId", seqId);
        params.put("code", this.media);
        params.put("isForce", isForce);
        params.put("event", true);
        params.put("backurl", MediaManager.CALL_BACK_URL);
        params.put("preferRightRtp", preferRightRtp);

        if (hook == null) {
            tid_Hook.put(seqId, event -> {
            });
        } else {
            tid_Hook.put(seqId, hook);
        }

        Map<String, String> header = new HashMap<>();
        header.put("X-Session-Id", sessionId);
        header.put("X-Sequence-Id", String.valueOf(seqId));
        String url = host + PLAY;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, PLAY);
    }

    public void ovsTranslate(MediaEnhanceParam enhanceParam, MediaHook<CallBackEvent> hook){
        if (!active) {
            return;
        }
        logger.info("[{}] ovsTranslate enhanceParam:{}", sessionId,enhanceParam);
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        params.put("seqId", seqId);
        params.put("audioCode", this.media);
        params.put("audioSource", enhanceParam.getAudioSource());
        params.put("audioDirection", enhanceParam.getAudioDirection());
        params.put("playTimes", enhanceParam.getPlayTimes());
        params.put("backurl", enhanceParam.getBackUrl());
        params.put("payloadtype", this.code);
        params.put("dtmfEndSign", enhanceParam.getDtmfEndSign());
        params.put("dtmfBackSign",enhanceParam.getDtmfBackSign());
        params.put("dtmfMode",enhanceParam.getDtmfMode());
        params.put("dtmfPType",this.telephonePlayLoadTemp);
        params.put("dtmfExpectLen",enhanceParam.getDtmfExpectLen());
        params.put("dtmfTimeout",enhanceParam.getDtmfTimeOut());
        params.put("dtmfIntervalTimeout",enhanceParam.getDtmfIntervalTimeout());
        params.put("dtmfTimingMode",enhanceParam.getDtmfTimingMode());
        params.put("dtmfDirection",enhanceParam.getDtmfDirection());
        params.put("recMode",enhanceParam.getRecMode());

        JSONObject audioConfig = new JSONObject();
        audioConfig.put("foreignDirection",enhanceParam.getAudioConfig().getForeignDirection());
        audioConfig.put("translateMode",enhanceParam.getAudioConfig().getTranslateMode());
        //HashMap<String,Integer> audioConfigMap = new HashMap<>();
        //audioConfigMap.put("foreignDirection",enhanceParam.getAudioConfig().getForeignDirection());

        params.put("audioConfig", "\"" + audioConfig + "\"");

        if (hook == null) {
            tid_Hook.put(seqId, event -> {
            });
        } else {
            tid_Hook.put(seqId, hook);
        }
        logger.info("[{}] ovsTranslate params:{}",params);
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.START_TRANSLATE;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.START_TRANSLATE);

    }


    public void stopearly() {
        if (!active) {
            return;
        }
        if (isPlaying) {
            isPlaying = false;
        }
        /*if (isPlayingEarly) {
            isPlayingEarly = false;
        } else {
            return;
        }*/

        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        String url = host + MEDIA_STOP;
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
//        int tid = sqeIdGenerator.getAndIncrement();
        params.put("seqId", seqId);
        header.put("sessionId", sessionId);
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
//        JSONObject resp = HttpAgent.getInstance().postJson(url, params);
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.MEDIA_STOP);
//        logger.info("[" + sessionid + "],[httpsend-stopearly] \n params_ " + params.toString() + "\n_header_" + header.toString());
    }

    public void stopDtmf() {
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        String url = host + MediaManager.STOP_DTMF;
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        params.put("seqId", seqId);
        header.put("sessionid", sessionId);
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.STOP_DTMF);
    }

    public void stopAudioPlay() {
        if (!active) {
            return;
        }
        isPickupAudioPlaying = false;
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionId", sessionId);
        params.put("mark", "Media");
        params.put("seqId", seqId);
        Map<String, String> header = new HashMap<>();
        header.put(X_SESSION_ID, sessionId);
        // todo 需要media确认这里能不能填字符串
        header.put(X_Sequence_Id, String.valueOf(seqId));
        String url = host + MEDIA_STOP;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.MEDIA_STOP);
    }


    public void playvedio(String videCode, String audioFile, int videPayLoadType, int xRate, int yRate, String leftVideoIp, int leftVideoPort, String videoFile) {

        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        if (this.media.startsWith("amr-wb")) {
            audioFile += "_wb.amr";
        } else if (this.media.startsWith("amr")) {
            audioFile += ".amr";
        } else {
            audioFile += ".wav";
        }
        isPlaying = true;
        params.put("seqId", seqId);
        params.put("file", audioFile);
        params.put("code", this.media);
        params.put("VideoCode", videCode);
        params.put("VideoPayLoadType", videPayLoadType);
        params.put("XRate", xRate);
        params.put("sessionid", sessionId);
        params.put("YRate", yRate);
        params.put("LeftVideoIp", leftVideoIp);
        params.put("LeftVideoPort", leftVideoPort);
        params.put("VideoFile", videoFile);
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.PLAY_VEDIO;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PLAY_VEDIO);
    }


    /**
     * 播放3gp视频文件，从传入的fileMap中挑选适合当前播放条件的视频文件
     * @param videCode
     * @param fileMap
     * @param videPayLoadType
     * @param leftVideoIp
     * @param leftVideoPort
     * @return 返回最终播放的文件的resolution
     */
    public String play3gpVideo(String videCode, Map<String, String> fileMap, int videPayLoadType, String leftVideoIp, int leftVideoPort) {
        if (videoLevelRecorder == null){
            logger.error("[{}], [MediaSession-play3gpVideo], no video message recorded!", sessionId);
            return null;
        }
        VideoLevelRecorder.Video3gpFile finalVideoFile = videoLevelRecorder.selectProper3gpFile(fileMap, true);
        String fileLevel = finalVideoFile.getFileLevel();
        String filePath = finalVideoFile.getFilePath();
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        isPlaying = true;
        params.put("seqId", seqId);
        params.put("file", filePath);
        params.put("code", this.media);
        params.put("VideoCode", videCode);
        params.put("VideoPayLoadType", videPayLoadType);
        params.put("sessionid", sessionId);
        params.put("LeftVideoIp", leftVideoIp);
        params.put("LeftVideoPort", leftVideoPort);
        params.put("VideoFile", filePath);
        params.put("level", fileLevel);
        params.put("frameRate", "30");
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.PLAY_VEDIO;
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PLAY_VEDIO);
        return finalVideoFile.getFormatName();
    }


    public int playVomVideo(VomPlayParam vomPlayParam, MediaHook<CallBackEvent> hook){

        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        String audioFile = vomPlayParam.getAudioFile();
        String videoFile = vomPlayParam.getVideoFile();

        params.put("sessionid", sessionId);
        params.put("seqId", seqId);
        params.put("file", audioFile);
        params.put("code", this.media);
        params.put("videoFile", videoFile);
        params.put("videoCode", vomPlayParam.getVideoCode());
        params.put("videoPayloadType", vomPlayParam.getVideoPayload());
        params.put("leftVideoIp", vomPlayParam.getLeftVideoIp());
        params.put("leftVideoPort", vomPlayParam.getLeftVideoPort());
        params.put("rightVideoIp", vomPlayParam.getRightVideoIp());
        params.put("rightVideoPort", vomPlayParam.getRightVideoPort());
        params.put("direction", vomPlayParam.getDirection());
        params.put("xRate", vomPlayParam.getxRate());
        params.put("yRate", vomPlayParam.getyRate());
        params.put("backUrl", vomPlayParam.getBackUrl());
        params.put("playTimes", vomPlayParam.getPlayTimes());
        params.put("noCache", vomPlayParam.getNoCache());

        if (videoLevelRecorder != null){
            switch (vomPlayParam.getDirection()){
                case 1: // LEFT_ONLY
                    params.put("level", videoLevelRecorder.getLeftVideoLevel());
                    break;
                case 2: //RIGHT_ONLY
                    params.put("level", videoLevelRecorder.getRightVideoLevel());
                    break;
                case 3: // BOTH
                    params.put("level", videoLevelRecorder.getBothLowerVideoLevel());
                    break;
                default: // invalid
                    logger.error("[{}], [MediaSession-playVomVideo], invalid direction", sessionId);
            }
            params.put("frameRate", "30");
        } else {
            logger.error("[{}], [MediaSession-playVomVideo], no video message recorded!", sessionId);
        }

        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.VOM_PLAY;
        params.put("event", true);
        if (hook == null) {
            tid_Hook.put(seqId, event -> {
            });
        } else {
            tid_Hook.put(seqId, hook);
        }
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.VOM_PLAY);
        return seqId;
    }

    public int stopVomVideo(){
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        params.put("seqId", seqId);

        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        header.put("sessionid", sessionId);
        String url = host + MediaManager.VOM_STOP;
        tid_Hook.put(seqId, event -> {
        });
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.VOM_STOP);
        return seqId;
    }

    public void handleCallBack(int seqId, CallBackEvent callBackEvent) {
        try {
            // 收到playEarlyEvent 置isPlayingEarly为false;
            if (callBackEvent instanceof AudioDoneEvent || callBackEvent instanceof VideoDoneEvent) {
                // 若当前记录播放早期媒体的事务id与回调的id相同则置false,防止错误设置了其他放音事务所设的标志位
                if (seqId == playTid && isPlaying) {
                    isPlaying = false;
                }
            }
            DtmfLogger.info(sessionId, "handleCallBack tid {} event {}, tid_Hook:{}", seqId, callBackEvent.getEvent(), tid_Hook);
            MediaHook hook = tid_Hook.get(seqId);
            if(hook != null){
                hook.apply(callBackEvent);
            }
            logger.info("sessionid {} handleCallBack tid {} eventName {}", sessionId, seqId, callBackEvent.getEvent());
        } catch (Exception e) {
            logger.error("handleCallBack sessionId " + sessionId, e);
        }

    }

    /**
     * 该接口用于分机号时候，需要获得被叫之后才能更新被叫号码
     * calleeName 被叫号码
     */

    public void notifyCalleeName(String calleeName, JSONObject recordParams) {
        if (!active) {
            return;
        }
        int seqId = sqeIdGenerator.getAndIncrement();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("sessionid", sessionId);
        params.put("calleeNo", calleeName);
        if (recordParams != null) {
            params.put("Params", MediaManager.handleJson(recordParams));
        }
        String url = host + MediaManager.NOTIFY;
        Map<String, String> header = new HashMap<>();
        header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
        MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.NOTIFY);
    }
    /**
     * 放音收号接口
     * playTimes 放音次数
     * url 放音文件路径
     * isInterrupt 放音是否可打断
     * firWaitTime 首次收号等待时间
     * idlTime 次间收号等待时间
     * tryNums 收号次数
     * maxNums 最大收号长度
     * minNums 最小收号长度
     * endChar 收号结束字符
     * cancelChar 收号取消字符
     **/
    @Deprecated
    public void playAndCollect(int playTimes, String file, boolean isInterrupt, int firWaitTime, int idlTime, int tryNums, int maxNums, int minNums, String endChar, String cancelChar, MediaHook<CallBackEvent> hook) {
        playAndCollect(1, maxNums, firWaitTime, isInterrupt ? 1 : 0, endChar, cancelChar, file, null, hook);
    }

    public void playAndCollect(int mode, int expectLen, int timeout, int interrupted, String endSign, String backSign, String audioFile, String callbackUrl, MediaHook<CallBackEvent> hook) {
        // 使用旧接口则 号间超时与timeout默认一致，计时方式默认修改为放音完成后及时
        playAndCollect(mode, expectLen, timeout, timeout, 1, 1, "", interrupted, 1, endSign, backSign, audioFile, callbackUrl, hook);
    }

    /**
     *
     * @param mode 收号模式  1:一次收号;2:持续性收号
     * @param expectLen 收号长度
     * @param interrupted 放音是否可打断  0:不能;1:能（默认）
     * @param endSign 结束标识,一般是 "#"
     * @param backSign 回退标识，一般是"*"
     * @param audioFile 放音路径
     * @param callbackUrl 回调地址
     * --20240829 新增号间超时控制参数--
     *  @param timeout 沿用原字段，首次按键超时，单位s (原先为全局超时时间，单位为s)
     *  @param intervalTimeout 号间超时，单位为秒
     *  @param timingMode 计时方式：1、放音结束后；2、全程
     *  @param playTimes 放音次数
     *  @param  defaultAudioFile 默认收号放音,兜底收号放音文件不存在的情况
     */
    public void playAndCollect(int mode, int expectLen, int timeout, int intervalTimeout, int timingMode, int playTimes,
                               String defaultAudioFile, int interrupted, int direction, String endSign, String backSign, String audioFile, String callbackUrl, MediaHook<CallBackEvent> hook) {
        if (!active) {
            return;
        }
        try {
            int seqId = sqeIdGenerator.getAndIncrement();
            ObjectNode params = objectMapper.createObjectNode();
            Map<String, String> header = new HashMap<>();
            audioFile = processAudioFileSuffix(audioFile);
            defaultAudioFile = processAudioFileSuffix(defaultAudioFile);
            params.put("sessionId", sessionId);
            params.put("seqId", seqId);
            // 左腿的收号payloadType，目前两条腿一样
            params.put("payloadtype", this.telephonePlayLoadTemp);
            // 右腿的收号payloadType，目前两条腿一样
            params.put("rightPayloadType", this.telephonePlayLoadTemp);
            params.put("mode", mode);
            params.put("expectLen", expectLen);
            params.put("timeout", timeout);
            params.put("intervalTimeout", intervalTimeout);
            params.put("timingMode", timingMode);
            params.put("playTimes", playTimes);
            params.put("interrupted", interrupted);
            params.put("direction", direction);
            params.put("endSign", endSign);
            params.put("backSign", backSign);
            params.put("audioFile", audioFile);
            params.put("defaultAudioFile", defaultAudioFile);
            if (StringUtil.isBlankOrNull(callbackUrl)) {
                params.put("callbackUrl", MediaManager.CALL_BACK_URL);
            } else{
                params.put("callbackUrl", callbackUrl);
            }
            if (hook != null) {
                tid_Hook.put(seqId, hook);
            }
            String url = host + MediaManager.PLAY_COLLECT;
            header.put(RECORD_SERVER_ID_KEY, String.valueOf(serverId));
            header.put("sessionId", sessionId);
            MediaManager.postJsonWithRetry(url, sessionId, header, params, MediaManager.PLAY_COLLECT);
        } catch (Exception ex) {
            logger.error("billId:{} collect error: {}", sessionId, ex);
        }
    }

    private String processAudioFileSuffix(String audioFile){
        String[] arrPlayCode = audioFile.split(",");
        String newPlayCode = "";
        for (String item : arrPlayCode) {
            String newCode = item.trim();
            if (StringUtils.isNotBlank(item)) {
                if (media.startsWith("amr-wb")) {
                    newCode = newCode + "_wb.amr";
                } else if (media.startsWith("amr")) {
                    newCode = newCode + ".amr";
                } else {
                    newCode = newCode + ".wav";
                }
                if (StringUtils.isNotEmpty(newPlayCode)) {
                    newPlayCode += ",";
                }
                newPlayCode += newCode;
            }
        }
        return newPlayCode;
    }

    /**
     * for test
     **/
    public static byte[] readFile(String filename) throws IOException {
        File file = new File(filename);
        long fileSize = file.length();

        FileInputStream fi = new FileInputStream(file);
        byte[] buffer = new byte[(int) fileSize];
        int offset = 0;
        int numRead = 0;
        while (offset < buffer.length
                && (numRead = fi.read(buffer, offset, buffer.length - offset)) >= 0) {
            offset += numRead;
        }
        if (offset != buffer.length) {
            throw new IOException("Could not completely read file " + file.getName());
        }
        fi.close();
        return buffer;
    }

    private static HashMap<String, String> initDefaultCodeClockRate() {
        HashMap<String, String> defaultCodeClockRateTemp = new HashMap<>();
        defaultCodeClockRateTemp.put("AMR-WB", "16000");
        defaultCodeClockRateTemp.put("AMR", "8000");
        defaultCodeClockRateTemp.put("PCMA", "8000");
        defaultCodeClockRateTemp.put("PCMU", "8000");
        return defaultCodeClockRateTemp;
    }

    public int checkIfVideoNegotiationCompleted() {
        if(audioOfferMediaLine!=null && audioAnswerMediaLine!=null){
            int oldState = videoChannelState.getState();
            videoChannelState.acceptSdp(videoOfferMediaLine == null?null:new VideoSdp(videoOfferMediaLine),videoAnswerMediaLine == null? null: new VideoSdp(videoAnswerMediaLine));
            videoOfferMediaLine = null;
            videoAnswerMediaLine = null;
            int newState = videoChannelState.getState();
            Integer x = ifChangeState(oldState, newState);
            audioOfferMediaLine = null;
            audioAnswerMediaLine = null;
            if (x != null) return x;
        }
        return 0;
    }

    public int isCompleteVideoNegotiation(){
        if(!useSdpVersion){
            return checkIfVideoNegotiationCompleted();
        }else{
            return checkIfVideoNegotiationCompletedV3();
        }
    }

    public int checkIfVideoNegotiationCompletedV3() {
        if(audioOfferPort!=-1 && audioAnswerPort!=-1){
            int oldState = videoChannelState.getState();
            videoChannelState.acceptSdp(videoOfferPort == -1?null:new VideoSdp(videoOfferPort),videoAnswerPort == -1? null: new VideoSdp(videoAnswerPort));

            videoOfferPort = -1;
            videoAnswerPort = -1;
            int newState = videoChannelState.getState();
            Integer x = ifChangeState(oldState, newState);
            audioOfferPort = -1;
            audioAnswerPort = -1;
            if (x != null) return x;
        }
        return 0;
    }

    public byte[] processLeftSDPV3(SIPMessage sipMessage) {
        if(useSdpVersion){
            return this.transferAndProcessSdp(sipMessage, MediaSession.LEG_LEFT, preferMediaCodec);
        }else{
            byte[] rawContent = sipMessage.getRawContent();
            if (isRetainNetVideo()) {
                return this.processSDPWithVideo(rawContent, MediaSession.LEG_LEFT, null);
            }
            return this.processSDP(rawContent, MediaSession.LEG_LEFT, null);
        }
    }

    public byte[] processLeftSDPV3(SIPMessage sipMessage, String preferMedia) {
        if(useSdpVersion){
            return this.transferAndProcessSdp(sipMessage, MediaSession.LEG_LEFT, preferMediaCodec);
        }else{
            byte[] rawContent = sipMessage.getRawContent();
            if (isRetainNetVideo()) {
                return this.processSDPWithVideo(rawContent, MediaSession.LEG_LEFT, preferMedia);
            }
            return this.processSDP(rawContent, MediaSession.LEG_LEFT, preferMedia);
        }

    }

    private String getMethod(SIPMessage sipMessage) {
        String method = "";
        if(sipMessage instanceof SIPRequest){
            method = ((SIPRequest) sipMessage).getMethod();
        }else if(sipMessage instanceof SIPResponse){
            method = ((SIPResponse) sipMessage).getCSeq().getMethod();
        }
        return method;
    }

    public byte[] processRightSDPV3(SIPMessage sipMessage) {
        if(useSdpVersion){
            return this.transferAndProcessSdp(sipMessage, MediaSession.LEG_RIGHT, preferMediaCodec);
        }else{
            byte[] rawContent = sipMessage.getRawContent();
            if (isRetainNetVideo()) {
                return this.processSDPWithVideo(rawContent, MediaSession.LEG_RIGHT, null);
            }
            return this.processSDP(rawContent, MediaSession.LEG_RIGHT, null);
        }
    }

    public byte[] processRightSDPV3(SIPMessage sipMessage, String preferMedia) {
        if(useSdpVersion){
            return this.transferAndProcessSdp(sipMessage, MediaSession.LEG_RIGHT, preferMediaCodec);
        }else{
            byte[] rawContent = sipMessage.getRawContent();
            if (isRetainNetVideo()) {
                return this.processSDPWithVideo(rawContent, MediaSession.LEG_RIGHT, preferMedia);
            }
            return this.processSDP(rawContent, MediaSession.LEG_RIGHT, preferMedia);
        }
    }

    /**
     * 更新发给对端的SDP，保存本次协商的信息
     *
     * @param sipMessage
     * @param fromLeg
     * @param preferMedia
     * @return
     */
    public byte[] transferAndProcessSdp(SIPMessage sipMessage, String fromLeg, String preferMedia) {
        //logger.info("[{}] transferAndProcessSdp start!",sessionid);
        if(!active){
            logger.info("[{}] transferAndProcessSdp media inactive,return original sdp!", sessionId);
            return sipMessage.getRawContent();
        }
        MultipartDescription multipartDesc = null;
        SessionDescription remoteSdp = null;
        SessionDescription forwardSdp = null;
        ContentTypeHeader contentTypeHeader = sipMessage.getContentTypeHeader();
        String contentType = contentTypeHeader.getContentType();
        String boundary = contentTypeHeader.getParameter("boundary");
        boolean isMultipart = contentType != null && contentType.startsWith("multipart");
        byte[] rawContent = sipMessage.getRawContent();
        boolean isSdpParsedSucc = false;

        try {
            if (isMultipart) {
                //message body 是 multipart
                multipartDesc = transferStrToMultipart(rawContent, boundary, sessionId);
                remoteSdp = multipartDesc.getSessionDescription();
            } else {
                //如果不是multipart, 则按 application/sdp 做默认处理
                remoteSdp =  transferStrToSdp(rawContent, sessionId);
            }

            checkIfContainThirdPartyInfo(remoteSdp,fromLeg);

            isSdpParsedSucc = true;

            if (remoteSdp != null && active) {
                List<MediaDescField> videoMedia = remoteSdp.getAllMediaAsSpecified(MediaDescField.MediaType.VIDEO);
                if (videoMedia != null && videoMedia.size() > 0){
                    if (videoLevelRecorder == null){
                        videoLevelRecorder = new VideoLevelRecorder(sessionId);
                    }
                    videoLevelRecorder.recordVideoParams(fromLeg.equals(MediaSession.LEG_LEFT), rawContent);
                }
                saveRemoteIPPort(remoteSdp,fromLeg);
                saveRemoteSessionInfo(remoteSdp,fromLeg);

                //forwardSdp = modifySdpToForward(remoteSdp, supportedCodeForAudioSet, audioPreferCode);
                forwardSdp = modifySdpToForward(remoteSdp, getSupportCodec(), preferMedia);

                saveMediaNegotiationResult(forwardSdp);
                //updateForwardSdpVersion(forwardSdp,fromLeg);

            }

            if(isMultipart)
                return multipartDesc.toString().getBytes();
            else
                return forwardSdp.toString().getBytes();

        } catch (Exception e) {
            logger.error("[{}] transferAndProcessSDP exception!", sessionId, e);
            if(isSdpParsedSucc){
                return rawContent;
            }else{
                logger.error("[{}] will use original method to process SDP!", sessionId);
                return degradeProcessSdp(rawContent,fromLeg,preferMedia);
            }

        }
    }

    private void checkIfContainThirdPartyInfo(SessionDescription remoteSdp,String fromLeg){
        if(remoteSdp == null || remoteSdp.getConnection() == null){
            return;
        }

        if(MediaSession.LEG_RIGHT.equalsIgnoreCase(fromLeg)){
            if(ConfigCache.getZjjlIpListSet().contains(remoteSdp.getConnection().getAddress())){
                logger.info("[{}] hit zhejiang jiling ip: {}", sessionId,remoteSdp.getConnection().getAddress());
                callAbnormalInfo.setDoesCalleeContainZjjlIP(true);
            }
            MediaDescField audioMedia = remoteSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.AUDIO);
            List<SdpField> sdpFieldList = audioMedia.getUnknowSdpFieldList();
            if(sdpFieldList != null && sdpFieldList.size() > 0){
                for(SdpField sdpField : sdpFieldList){
                    if(sdpField.toString().equalsIgnoreCase("3gOoBTC")){
                        callAbnormalInfo.setDoesCalleeContain3gOoBTC(true);
                        break;
                    }
                }
            }
        }
    }

    private byte[] degradeProcessSdp(byte[] rawContent, String fromLeg, String preferMedia){
        if(fromLeg.equals(MediaSession.LEG_LEFT)){
            return processLeftSDP(rawContent,preferMedia);
        }else {
            return processRightSDP(rawContent,preferMedia);
        }
    }

    /**
     * 更新 SDP 的版本号，版本号加+1
     * @param forwardSdp
     * @param fromLeg
     */
    /*private void updateForwardSdpVersion(SessionDescription forwardSdp,String fromLeg){
        if(fromLeg.equals(LEG_LEFT)){
            sdpSessionV3.incAndGetRightLocalVersion();
        }else{
            sdpSessionV3.incAndGetLeftLocalVersion();
        }
    }*/

    private void saveRemoteIPPort(SessionDescription forwardSdp, String fromLeg){
        if(forwardSdp == null){
            return;
        }
        IPPort remoteIpPort = new IPPort();
        MediaDescField audioMedia = forwardSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.AUDIO);
        if(audioMedia != null){
            ConnectionField connection = audioMedia.getConnection();
            if(connection != null){
                remoteIpPort.setIp(connection.getAddress());
                remoteIpPort.setPort(audioMedia.getPort());
            }
        }
        MediaDescField videoMedia = forwardSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.VIDEO);
        if(videoMedia != null){
            ConnectionField connection = videoMedia.getConnection();
            if(connection != null){
                remoteIpPort.setVideoIp(connection.getAddress());
                remoteIpPort.setVedioport(videoMedia.getPort());
            }
        }

        synchronized (this) {
            if (fromLeg.equals(LEG_LEFT)) {
                this.leftEndpointTemp = remoteIpPort;
            } else {
                this.rightEndpointTemp = remoteIpPort;
            }
        }
    }

    public static MultipartDescription transferStrToMultipart(byte[] byteSdp, String boundary, String sessionId) throws Exception {
        //将sdp文本转化成 MultipartDescription对象
        return MultipartDescriptionParser.parse(byteSdp, boundary,sessionId);
    }

    public static SessionDescription transferStrToSdp(byte[] byteSdp, String sessionId) throws Exception {
        //将sdp文本转化成SessionDescription对象
        return SessionDescriptionParser.parse(byteSdp,sessionId);
    }

    /**
     * 修改将要发给对端的SDP
     *
     * @param originSdp
     * @param supportedCodeArray
     * @param preferMedia
     * @return
     * @throws Exception
     */
    public SessionDescription modifySdpToForward(SessionDescription originSdp, Set<String> supportedCodeArray, String preferMedia) throws Exception {
        //logger.info("[{}] modifySdpToForward start!",sessionid);
        ConnectionField sdpConnection = originSdp.getConnection();
        ConnectionField sdpOriginConnection = null;
        //如果session level的connection不为null,则保存以设置video的connection
        if (sdpConnection != null) {
            sdpOriginConnection = new ConnectionField(sdpConnection.getNetworkType(), sdpConnection.getAddressType(), sdpConnection.getAddress());
        }

        SessionDescription forwardSdp = originSdp;
        //过滤媒体属性
        //更新第一个audio的IP和port，如果第一个audio没有connection，则更新到 session 的 connection
        forwardSdp.updateFirstConnectionByMedia(MediaDescField.MediaType.AUDIO, proxyEndpoint.ip, proxyEndpoint.port);

        //更新session的IP
        forwardSdp.updateSessionConnection(proxyEndpoint.ip);

        //更新端口
        Set<String> allMediaKeySet = forwardSdp.getAllMediaContained();
        boolean isFirstAudioProcessed = false;
        for (String mediaKey : allMediaKeySet) {
            List<MediaDescField> mediaDescFieldList = forwardSdp.getAllMediaAsSpecified(mediaKey);
            for (MediaDescField media : mediaDescFieldList) {
                if(media == null){
                    logger.error("billId:[] media is null!", sessionId);
                    continue;
                }
                //第一个audio不处理，因为前面已经处理过了，第一个audio的媒体流需经过 xmedia
                if (mediaKey.equalsIgnoreCase(MediaDescField.MediaType.AUDIO) && !isFirstAudioProcessed) {
                    isFirstAudioProcessed = true;
                    //过滤编码
                    if(StringUtils.isNotBlank(preferMedia)){
                        Set<String> supportedCodes = new HashSet<String>();
                        supportedCodes.add(preferMedia);
                        Set<Integer> staticPayloadTypes = new HashSet<>();
                        media.filterCodeAndPayload(supportedCodes, staticPayloadTypes, preferMedia);
                    }else{
                        media.filterCodeAndPayload(supportedCodeArray, supportedStaticPayloadType, preferMedia);
                    }
                    //调整 maxPtime 的值，使 maxptime 和 ptime 一样，保持跟现网逻辑一致
                    media.setMaxptimeTheSameAsPtime();

                    //调整 ptime，若 ptime < 20, 则调整为 20
                    if(media.getPtime() != null && media.getPtime().getTime() < SdpParseUtil.MIN_PTIME){
                        media.getPtime().setTime(SdpParseUtil.MIN_PTIME);
                    }

                    //调整 maxptime，若 maxptime < 20, 则调整为 20
                    if(media.getMaxptime() != null && media.getMaxptime().getTime() < SdpParseUtil.MIN_MAXPTIME){
                        media.getMaxptime().setTime(SdpParseUtil.MIN_MAXPTIME);
                    }
                    continue;
                }
                //如果需关闭视频流，则将 video 端口都置为0
                if (mediaKey.equalsIgnoreCase(MediaDescField.MediaType.VIDEO) && !retainNetVideo) {
                    media.setPort(0);
                    continue;
                }
                //如果该媒体不为 null 且端口不为0（即媒体流打开）
                if (media != null && media.getPort() > 0) {
                    if (media.isConnectionNull()) {
                        media.setConnection(sdpOriginConnection);
                    }
                }
            }
        }

        if(retainNetVideo){
            MediaDescField audioMedia = forwardSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.AUDIO);
            if(audioMedia != null){
                if (audioOfferPort == -1) {
                    audioOfferPort = audioMedia.getPort();
                } else {
                    audioAnswerPort = audioMedia.getPort();
                }
            }

            MediaDescField videoMedia = forwardSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.VIDEO);
            if(videoMedia != null){
                if (videoOfferPort == -1) {
                    videoOfferPort = videoMedia.getPort();
                } else {
                    videoAnswerPort = videoMedia.getPort();
                }
            }
        }

        return forwardSdp;
    }

    /**
     * 保存媒体协商结果
     *
     * @param forwardSdp
     * @throws Exception
     */
    public void saveMediaNegotiationResult(SessionDescription forwardSdp) throws Exception {
        //如果当前是一个answer,则设置 mediaTemp 和 codeTemp
        if (this.leftEndpointTemp != null && this.rightEndpointTemp != null) {
            MediaDescField mediaDescField = forwardSdp.getFirstMediaAsSpecified(MediaDescField.MediaType.AUDIO);
            if (mediaDescField != null) {
                Integer[] payLoadTypes = mediaDescField.getPayloadTypesAsArray();
                boolean isAudioCodeConfirm = false, isTelephoneEventConfirm = false;
                for (int payLoadType : payLoadTypes) {
                    int nPayLoadType = Integer.valueOf(payLoadType);
                    RtpMapAttrGroup rtpMapAttrGroup = mediaDescField.getRtpMapAttributeGroup(nPayLoadType);
                    //处理m行有8和0,但是属性行没有8和0的情况
                    if (rtpMapAttrGroup == null && (nPayLoadType == 8 || nPayLoadType == 0) && !isAudioCodeConfirm) {
                        switch (nPayLoadType) {
                            case 8:
                                if(StringUtils.isBlank(this.mediaTemp)){
                                    this.mediaTemp = "pcma";
                                    this.codeTemp = nPayLoadType;
                                }
                                break;
                            case 0:
                                if(StringUtils.isBlank(this.mediaTemp)){
                                    this.mediaTemp = "pcmu";
                                    this.codeTemp = nPayLoadType;
                                }
                                break;
                            default:
                                break;
                        }
                        continue;
                    }

                    RtpMapAttribute rtpMapAttribute = rtpMapAttrGroup.getRtpMapAttribute();

                    //取第一个非telephone-event的净荷类型
                    if (rtpMapAttribute != null) {
                        String codec = rtpMapAttribute.getCodec();

                        FormatParameterAttribute formatParameterAttribute = rtpMapAttrGroup.getFmtpAttribute();
                        //如果fmtp含有 octet-align=1,则编码改为 codec-octet 例如 amr-octet
                        if (formatParameterAttribute != null && formatParameterAttribute.getParams().contains("octet-align=1")) {
                            codec = codec + "-octet";
                        }

                        // 如果除了G711编码之外，还没有选定一个语音编码 并且 当前不是telephone-event编码
                        if (!isAudioCodeConfirm && !rtpMapAttribute.getCodec().equals("telephone-event")) {
                            this.mediaTemp = codec.toLowerCase();
                            this.codeTemp = nPayLoadType;
                            isAudioCodeConfirm = true;
                        }
                        // 这里是从answer中的选择一个telephone-event编码，如果answer不规范出现了多个，则选择的结果可能存在不确定性，导致clock_rate不一致，产生一些核心网兼容性问题
                        if (!isTelephoneEventConfirm && rtpMapAttribute.getCodec().equals("telephone-event")) {
                            // TODO: 2024/11/7 这里暂时忽略2833clock_rate需要和语音编码一样，直接选择首次出现的,大概率左右腿的answer会按照规范回复
                            this.telephonePlayLoadTemp = nPayLoadType;
                            isTelephoneEventConfirm = true;
                        }
                        // 如果已经满足条件则退出本次选择，保存当前结果
                        if (isAudioCodeConfirm && isTelephoneEventConfirm) break;
                        // if (!rtpMapAttribute.getCodec().equals("telephone-event")) {
                        //     this.mediaTemp = codec.toLowerCase();
                        //     this.codeTemp = nPayLoadType;
                        //     break;
                        // }
                    }


                }
            }

        }
    }

    /**
     * 更新、维护发给主被叫的 SDP，若非第一次发送 SDP 给对端，则 将 SDP的版本号加1
     *
     * @param remoteSdp      收到的SDP
     * @param fromLeg        来自哪一条腿
     */
    public void saveRemoteSessionInfo(SessionDescription remoteSdp, String fromLeg) throws Exception{
        OriginField originField = remoteSdp.getOrigin();
        String originLine = originField.toString();

        if (fromLeg.equals(LEG_RIGHT)) {
            sdpSession.acceptRightO(originLine);
            String leftLocalSessionId = sdpSession.getLeftLocalSessionId();
            String leftLocalSessionVersion = sdpSession.getLeftLocalSessionVersion() + "";
            originField.setSessionId(leftLocalSessionId);
            originField.setSessionVersion(leftLocalSessionVersion);
        } else {
            sdpSession.acceptLeftO(originLine);
            String rightLocalSessionId = sdpSession.getRightLocalSessionId();
            String rightLocalSessionVersion = sdpSession.getRightLocalSessionVersion() + "";
            originField.setSessionId(rightLocalSessionId);
            originField.setSessionVersion(rightLocalSessionVersion);
        }

    }


    /**
     * An RtpAttribute represents an a=fmtp or a=rtpmap fields contained within either a MediaDescription.
     *
     * Here are some examples:
     *
     * example sdp lines:
     *  a=rtpmap:104 AMR-WB/16000
     *  a=fmtp:104 max-red=220;mode-change-capability=2
     *
     *     identifies the media format:
     *     fmtp="a=fmtp:104 max-red=220;mode-change-capability=2"
     *     rtpmap="a=rtpmap:104 AMR-WB/16000"
     *     code="0"
     *     name="AMR-WB"
     *     clockRate="16000"

     * @author  XH
     * @version 1.1
     */
    @Data
    class RtpAttribute {

        String fmtp;
        String rtpmap;
        String code;
        String name;
        String clockRate;
        String ptime;

        boolean ifSupported;

        public RtpAttribute() {

        }

        public RtpAttribute(String rtpmap){
            acceptRtpMap(rtpmap);
        }
        private void acceptRtpMap(String rtpmap) {
            if (StringUtils.isBlank(rtpmap)) return;
            String[] parts = rtpmap.split(":")[1].split(" ");
            String[] nameAndClockRate = parts[1].split("/");
            this.rtpmap = rtpmap;
            this.code = parts[0].trim();
            this.name = nameAndClockRate[0].trim();
            this.clockRate = nameAndClockRate.length > 1 ? nameAndClockRate[1].trim() : defaultCodeClockRate.get(name);
        }
        private void acceptFmtp(String fmtp){
            String[] parts = fmtp.split(":")[1].split(" ");
            this.code = parts[0].trim();
            this.fmtp = fmtp;
        }

    }

    /**
     * 如果session不存在则增加session的connection行
     * Session description
     *  v= (protocol version)
     *  o= (originator and session identifier)
     *  s= (session name)
     *  i=* (session information)
     *  u=* (URI of description)
     *  e=* (email address)
     *  p=* (phone number)
     *  c=* (connection information -- not required if included in
     *  all media descriptions)
     *  b=* (zero or more bandwidth information lines)
     *  One or more time descriptions:
     *  ("t=", "r=" and "z=" lines; see below)
     *  k=* (obsolete)
     *  a=* (zero or more session attribute lines)
     *  Zero or more media descriptions
     *
     * @param sessionSdp 输入的为sdp的session部分，不包括media部分
     */
    private StringBuilder addConnectionIfSessionAbsence(StringBuilder sessionSdp){
        if (sessionSdp.indexOf("c=IN IP4") > -1){
            return sessionSdp;
        }
        String sessionSdpStr = sessionSdp.toString();
        StringReader sr = new StringReader(sessionSdpStr);
        BufferedReader br = new BufferedReader(sr);
        StringBuilder resultSdp = new StringBuilder();
        try {
            String line = br.readLine();
            int readState = 0;
            while (line != null) {
                // 已经读过s=行，并且下一行不为i、u、e、p，则添加c行
                if (readState == 1 && !line.contains("i=") && !line.contains("u=") && !line.contains("e=") &&
                        !line.contains("p=")){
                    resultSdp.append(String.format("c=IN IP4 %s\r\n", proxyEndpoint.ip));
                    readState = 2;
                }
                if (readState == 0 && line.contains("s=")){
                    readState = 1;
                }
                resultSdp.append(line).append("\r\n");
                line = br.readLine();
            }
            // 若s行为最后一行，则需添加c行
            if (readState == 1){
                resultSdp.append(String.format("c=IN IP4 %s\r\n", proxyEndpoint.ip));
            }
        } catch (IOException e) {
            logger.error("{}, readLine error, return origin sessionSdp", sessionId, e);
            return sessionSdp;
        }
        return resultSdp;
    }

    private HashSet<String> getSupportCodec(){
        if (testSupportedCodec != null && testSupportedCodec.size() > 0){
            logger.error("{}, use force codec:{}", sessionId, testSupportedCodec);
            return testSupportedCodec;
        }
        return supportedCodec;
    }
}
