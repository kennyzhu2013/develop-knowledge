package com.cmic.recording;

import com.cmic.manager.VersionManager;
import com.cmic.recording.mediaevent.CallBackEvent;
import com.cmic.service.PromExporterService;
import com.cmic.service.ScheduleService;
import com.cmic.service.Slf4jMDCService;
import com.cmic.sip.controller.base.Controller;
import com.cmic.sip.controller.base.ControllerManager;
import com.cmic.sip.leg.Hook;
import com.cmic.util.DateUitl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.util.HttpAgent;
import net.sf.json.JSONObject;
import org.apache.rocketmq.shaded.ch.qos.logback.core.encoder.EchoEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MediaManager {
//    public void init()

    public static final String HOST_TEST = "http://10.153.90.11:8092";
    public static final String CAll_SESSION_PATH = "/call/session/";
    public static final String CREATE = CAll_SESSION_PATH + "create";
    public static final String UPDATE = CAll_SESSION_PATH + "update";
    public static final String PICKUP = CAll_SESSION_PATH + "pickup";
    public static final String STOP = CAll_SESSION_PATH + "stop";
    public static final String CAll_MEDIA_PATH = "/call/media/";
    public static final String DTMF = CAll_MEDIA_PATH + "dtmf";
    public static final String PLAY = CAll_MEDIA_PATH + "play";
    public static final String MEDIA_STOP = CAll_MEDIA_PATH + "stop";
    public static final String ENHANCE = CAll_MEDIA_PATH + "enhance";

    public static final String BUSINESSUPDATE = "/v1/session/bupdate";
    public static final String PLAY_VEDIO = "/v1/video/playearly";
    public static final String NOTIFY = "/v1/session/notifyevent";
    public static final String PLAY_COLLECT = "/v1/audio/dtmf";
    public static final String STOP_DTMF = "/v1/audio/dtmf/stop";
    public static final String VOM_PLAY = "/v1/video/play";
    public static final String VOM_STOP = "/v1/video/stop";
    public static final String VOD_CONTROL = "/v1/video/control";
    public static final String START_TRANSLATE = "/v1/media/enhance";
    public static final String X_SESSION_ID = "X-Session-Id";
    public static final String  X_Sequence_Id = "X-Sequence-Id";
    // todo 后续可以统一使用工具类中的对象
    public static final ObjectMapper objectMapper = new ObjectMapper();

    public static String CALL_BACK_URL = "";
    private static Logger logger = LoggerFactory.getLogger(MediaManager.class);

    public static String HTTP_SERVER_BASE_URL = "";


    public static void init(String callbackHost) {
        CALL_BACK_URL = callbackHost + "/media";
        HTTP_SERVER_BASE_URL = callbackHost;
    }

    public static void main(String[] args) {
        MediaSession rs = MediaManager.createSession(HOST_TEST, System.currentTimeMillis(), "11111111", 100, "13879421399", "13802885277", null);
    }

    public static MediaSession createMediaSession(String sessionId, String host, int serverId, String userId, String callerNum, String calleeNum, ObjectNode mergeConfig, long startTime, int recordMode, String[] supportedCodec){
        if (host == null || host.isEmpty()) {
            return new MediaSession(false);
        }
        try {
            ObjectNode params = objectMapper.createObjectNode();
            params.put("sessionId", sessionId);
            params.put("userId", userId);
            params.put("callerNum", callerNum);
            params.put("calleeNum", calleeNum);
            if (mergeConfig != null) {
                // todo 此处需要合音模块确认能否正确处理这个json
                String mergeConfigStr = objectMapper.writeValueAsString(mergeConfig);
                params.put("mergeConfig", mergeConfigStr);
            } else {
                params.put("mergeConfig", "{}");
            }
            params.put("startTime", DateUitl.formatDateyyyyMMddHHmmss(new Date(startTime)));
            params.put("recordMode", recordMode);
            params.put("seqId", 1);
            params.put("callbackUrl", CALL_BACK_URL);
            Map<String, String> header = new HashMap<>();
            header.put(X_SESSION_ID, sessionId);
            // todo 需要media确认这里能不能填字符串
            header.put(X_Sequence_Id, "1");
            String url = host + MediaManager.CREATE;
            logger.info("createMediaSession params: {}", params);
            ObjectNode resp = HttpAgent.getInstance().postJson(url, header, params);
            logger.info("[" + sessionId + "],[httpsend-create] \n_params_" + params + "\n_resp_ " + resp.toString() + "\n_serverId_" + serverId);
            // 只有媒体服务响应成功才继续能创建可用的媒体会话
            if (!resp.has("code") || resp.get("code").asInt() != 200) {
                logger.error("[{}],connect media fail \n_params_" + params + "\n_resp_ " + resp.toString() + "\n_serverId_" + serverId);
                return new MediaSession(false);
            }
            ObjectNode data = (ObjectNode) resp.get("data");
            int videoPort = data.has("videoPort") ? data.get("videoPort").asInt() : 0;
            int storeFlag = data.has("storeFlag") ? data.get("storeFlag").asInt() : 0;
            return new MediaSession(host, sessionId, new IPPort(data.get("ip").asText(), data.get("audioPort").asInt()), serverId, videoPort, storeFlag, supportedCodec);
        } catch (Exception e) {
            logger.error("[{}],[httpsend-create] ", sessionId, e);
            return new MediaSession(false);
        }
    }


    public static MediaSession createSession(String host, long now, String sessionid, int companyId, String callerNum, String calleeNum, JSONObject recordParams) {
        return createSession(host, now, sessionid, companyId, callerNum, calleeNum, 1, recordParams,new String[]{"amr","pcma","pcmu","telephone-event"});
    }



    public static MediaSession createSession(String host, long now, String sessionid, int companyId, String callerNum, String calleeNum, int logicFlag, JSONObject recordParams) {
        return createSession(host, now, sessionid, companyId, callerNum, calleeNum, logicFlag, recordParams,new String[]{"amr","pcma","pcmu","telephone-event"});
    }

    /**
     * @param host
     * @param now
     * @param sessionid
     * @param companyId
     * @param callerNum
     * @param calleeNum
     * @param logicFlag    1表示只录通话的录音，2表示全量录音
     * @param recordParams
     * @return
     */

    public static MediaSession createSession(String host, long now, String sessionid, int companyId, String callerNum, String calleeNum, int logicFlag, JSONObject recordParams,String []supportedCodec) {
        if (host == null || host.isEmpty()) {
            return new MediaSession(false);
        }
        return new MediaSession(false);
//        JSONObject params = new JSONObject();
//        params.put("Sessionid", sessionid);
//        params.put("Companyid", String.valueOf(companyId));
//        params.put("Caller", callerNum);
//        params.put("Callee", calleeNum);
//        params.put("StartTime", DateUitl.formatDateyyyyMMddHHmmss(new Date(now)));
//        params.put("tid", 1);
////        全量录音create消息接口：LogicFlag 0x02表示全量录音,非全量1
//        params.put("LogicFlag", logicFlag);
//        params.put("Version ", String.valueOf(VersionManager.getInstance().getVersion()));
//        if (recordParams != null) {
//            params.put("Params", handleJson(recordParams));
//        }
//
//        String url = host + MediaManager.CREATE;
//        params.put("url", url);
//        logger.info("params " + params.toString());
//        HashMap<String, String> header = new HashMap<>();
//        header.put("sessionid", sessionid);
//
//        try {
//            Object[] resArr = HttpAgent.getInstance().postJsonResWithHeader(url, header, params);
//            JSONObject resp = (JSONObject) resArr[0];
////        logger.info("["+sessionid+"],[httpsend-create] \n_params_"+ params.toString()+"\n_resp_ "+resp.toString());
////        logger.info("record create sessionid is "+sessionid+"\n_params_"+params.toString()+"\n_resp_ "+resp.toString());
//            //可能recording没有响应
//            if (!resp.containsKey("ip")) {
//                return new MediaSession(false);
//            }
//
//            Map<String, String> resHeaderMap = (Map<String, String>) resArr[1];
//            int serverId = 0;
//            if(resHeaderMap.containsKey("X-Media-Server")){
//                serverId = Integer.valueOf(resHeaderMap.get("X-Media-Server"));
//            }else{
//                logger.info("[" + sessionid + "],[httpsend-create] response does not contain X-Media-Server!");
//                serverId = 99999;
//            }
//            logger.info("[" + sessionid + "],[httpsend-create] \n_params_" + params.toString() + "\n_resp_ " + resp.toString() + "\n_serverId_" + serverId);
//            int videoPort = resp.containsKey("videoport") ? resp.getInt("videoport") : 0;
//            int nfsId = resp.containsKey("nfsMark") ? resp.getInt("nfsMark") : 0;
//            String mediaHost = resp.containsKey("destination") ? "http://" + resp.getString("destination") : host;
//            //MediaSession recordSession = new MediaSession(mediaHost, sessionid, new IPPort(resp.getString("ip"), resp.getInt("port")), serverId, videoPort);
//            MediaSession recordSession = new MediaSession(mediaHost, sessionid, new IPPort(resp.getString("ip"), resp.getInt("port")), serverId, videoPort,nfsId,supportedCodec);
//            return recordSession;
//        } catch (Exception e) {
//            logger.error("[" + sessionid + "],[httpsend-create] ", e);
//            return new MediaSession(false);
//        }

    }

    public static Controller getController(String sessionId){
        return ControllerManager.getInstance().getController(sessionId);
    }

    public static void processMediaCallBack(String sessionId, int seqId, CallBackEvent callBackEvent) {
        Controller controller = getController(sessionId);
        if (controller == null) {
            return;
        }
        MediaSession recordSession = controller.getMediaSession();
        if (recordSession == null) {
            return;
        }
        recordSession.handleCallBack(seqId, callBackEvent);
    }

    public static String handleJson(JSONObject jsonObject) {
        StringBuilder sb = new StringBuilder();
        for (char ss : jsonObject.toString().toCharArray()) {
            if (ss == '"') {
                sb.append("\\");
            }
            sb.append(ss);
        }
        return sb.toString();
    }

//    public static void postJsonWithRetry(String url, String sessionId, Map<String, String> header, JSONObject params, String method) {
//        try {
//            ObjectNode objectNode = (ObjectNode) objectMapper.readTree(params.toString());
//            postJsonWithRetry(url, sessionId, header, objectNode, method);
//        } catch (Exception e){
//            logger.error("postJsonWithRetry trans JSONObject to ObjectNode error", e);
//        }
//    }

    public static void postJsonWithRetry(String url, String sessionId, Map<String, String> header, ObjectNode params, String method) {
        postJsonWithRetry(url, sessionId, header, params, method, null);
    }



    public static void postJsonWithRetry(String url, String sessionId, Map<String, String> header, ObjectNode params, String method, Hook<ObjectNode> failHook) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            logger.error("unexpect error,the url does not start with http :" + url);
        }
        ScheduleService.getInstance().execute(() -> {
            Slf4jMDCService.putMDCCustomerCallID(sessionId);
            ObjectNode resp = HttpAgent.getInstance().postJson(url, header, params);
            for (int i = 0; i < 2; i++) {
                logger.info("[" + sessionId + "],[httpsend-" + method + "] " + "\n_params_" + params.toString() + "\n_header_" + header.toString() + "\n_resp_ " + resp.toString());
                if (!resp.has("error msg")) {
                    break;
                }
                if(failHook != null) {
                    // 仅在失败的时候执行hook
                    try {
                        failHook.apply(resp);
                    } catch (Exception e) {
                        logger.error("[{}] deal with {} tid {} fail", sessionId, method, params.get("tid"));
                    }
                }
                if (resp.get("error msg").asText().contains("SocketTimeoutException")) {
                    PromExporterService.getInstance().incRecordingCallRetry(method, i);
                    resp = HttpAgent.getInstance().postJson(url, header, params);
                }
            }

        });
    }



}
