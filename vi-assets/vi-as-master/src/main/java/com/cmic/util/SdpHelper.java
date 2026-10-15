package com.cmic.util;

import com.cmic.constant.CommonConstant;
import com.cmic.pojo.VideoParam;
import com.cmic.sdp.msgentity.SessionDescription;
import com.cmic.sdp.msgentity.parser.SessionDescriptionParser;
import com.cmic.state.MediaDirection;
import gov.nist.javax.sip.message.SIPMessage;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SdpHelper {

    private static final Logger logger = LoggerFactory.getLogger(SdpHelper.class);

    public static VideoParam getVideoParam(SIPMessage sipMessage) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(sipMessage.getRawContent()));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        String ip = "";
        int port = 0;
        boolean isVideo = false;
        boolean isVideoInactive = false;
        while (line != null) {
            if (line.indexOf("m=video") > -1) {
                isVideo = true;
                port = Integer.parseInt(line.split(" ")[1]);
            } else if (line.indexOf("c=") > -1) {
                //c=IN IP4 10.188.28.49
                ip = line.split(" ")[2];
            }
            if (isVideo && line.contains("a=inactive")) {
                isVideoInactive = true;
            }
            line = br.readLine();
        }
        return new VideoParam(ip, port, isVideoInactive);
    }

    public static boolean isSDPMediaContainAmrWb(byte[] rawContent) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        while (line != null) {
            if (line.contains(" AMR-WB")) {
                return true;
            }
            line = br.readLine();
        }
        return false;
    }


    public static void main(String[] args) throws Exception {
        String sdp = "v=0\n" +
                "o=HuaweiATS9900 11932361 11932365 IN IP4 10.59.133.179\n" +
                "s=SBC call\n" +
                "c=IN IP4 10.193.192.57\n" +
                "b=AS:49\n" +
                "b=RS:600\n" +
                "b=RR:2000\n" +
                "t=0 0\n" +
                "m=audio 26524 RTP/AVP 107 101\n" +
                "b=AS:41\n" +
                "b=RS:600\n" +
                "b=RR:2000\n" +
                "a=rtpmap:107 AMR-WB/16000/1\n" +
                "a=fmtp:107 mode-change-capability=2;max-red=0\n" +
                "a=curr:qos local none\n" +
                "a=curr:qos remote sendrecv\n" +
                "a=des:qos mandatory local sendrecv\n" +
                "a=des:qos mandatory remote sendrecv\n" +
                "a=sendrecv\n" +
                "a=ptime:20\n" +
                "a=rtpmap:101 telephone-event/16000\n" +
                "a=fmtp:101 0-15\n" +
                "a=maxptime:20\n" +
                "m=video 0 RTP/AVP 116\n" +
                "b=AS:0\n" +
                "b=RS:0\n" +
                "b=RR:0\n" +
                "a=curr:qos local sendrecv\n" +
                "a=curr:qos remote sendrecv\n" +
                "a=des:qos mandatory local sendrecv\n" +
                "a=des:qos mandatory remote sendrecv\n" +
                "a=inactive";

        System.out.println(sdp);
        System.out.println("======================================================");

        //String newRespSdp = sdp.replace("a=curr:qos local none","a=curr:qos local sendrecv");
        //System.out.println(newRespSdp);
        //System.out.println(new String(SdpHelper.increaseSessionVersion(sdp.getBytes())));
        //System.out.println(getVodMedia(sdp));
        //String audioSdp = extractAudioSDP(sdp.getBytes());
        //System.out.println(audioSdp);

//        String[] supportedCode = {"pcma","pcmu","telephone-event","amr","amr-wb"};
//        Map<String, Object> resultMap = SdpHelper.getVodMedias(audioSdp.getBytes(),supportedCode);
//        Object obj = resultMap.get("media");
//        ArrayList<String> list = (ArrayList<String>) resultMap.get("media");
//        System.out.println(list);

        HashSet<Integer> set = new HashSet<>();

        String vodSupportMutipleMedia = "1,4,a,9,10";
        if (StringUtils.isNotBlank(vodSupportMutipleMedia)) {
            Arrays.stream(vodSupportMutipleMedia.split("\\s*,\\s*")) // 自动处理逗号前后空格
                    .filter(StringUtils::isNumeric) // 需要引入StringUtils
                    .map(Integer::valueOf)
                    .forEach(set::add);
        }

        System.out.println(set);

        System.out.println("======================================================");

    }



    public static Map<String,Object> getVodMedias(byte[] rawContent,String[] supportedCodes){
        LinkedHashSet<String> vodMedias = new LinkedHashSet<>();
        ArrayList<String> vodMediaList = new ArrayList<>();
        Map<String,Object> resultMap = new HashMap<>();
        int resultCode = 0;
        List audioCodeList = null;
        try{

            // 截取 audio 部分的 SDP 内容
            String audioSDP = extractAudioSDP(rawContent);
            if (audioSDP == null) {
                resultCode = -1;
                resultMap.put("code",resultCode);
                return resultMap; // 如果没有 audio 媒体，返回空
            }

            HashMap<String, Integer> codecClockRates = new HashMap<>();
            HashMap<String, Integer> telephoneEventClockRates = new HashMap<>();

            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            StringBuilder resultBuilder = new StringBuilder();
            while (line != null) {
                if (line.startsWith("m=video")) {
                    break;
                }else if(line.startsWith("m=audio")){
                    String[] parts = line.split(" ");
                    audioCodeList = Arrays.asList(parts);
                }else if(line.startsWith("a=rtpmap")){
                    Pattern rtpMapPattern = Pattern.compile("a=rtpmap:(\\d+) ([^/]+)/(\\d+)(?:/(\\d+))?");
                    Matcher matcher = rtpMapPattern.matcher(line);
                    if(matcher.find()){
                        int payloadType = Integer.parseInt(matcher.group(1));
                        String codecName = matcher.group(2).toLowerCase();
                        int clockRate = Integer.parseInt(matcher.group(3));

                        if (codecName.equalsIgnoreCase(CommonConstant.MEDIA.TELEPHONE_EVENT)) {
                            telephoneEventClockRates.put(payloadType+"_"+codecName, clockRate);
                        } else {
                            codecClockRates.put(payloadType+"_"+codecName, clockRate);
                        }
                    }
                }
                line = br.readLine();
            }

            // 如果没有telephone-event，返回空
            if (!telephoneEventClockRates.isEmpty()) {
                if(audioCodeList.contains(CommonConstant.PAYLOAD.PCMU) && !codecClockRates.containsKey("0_pcmu")){
                    codecClockRates.put("0_pcmu", 8000);
                }
                if(audioCodeList.contains(CommonConstant.PAYLOAD.PCMA) && !codecClockRates.containsKey("8_pcma")){
                    codecClockRates.put("8_pcma", 8000);
                }

                List<String> supportedCodeList = new ArrayList<>(Arrays.asList(supportedCodes));

                Map<String, Integer> sortedTelephoneEventClockRateMap = sortByValue(telephoneEventClockRates);
                // 找出与telephone-event clock rate相同的媒体编码
                for (Map.Entry<String, Integer> telephoneEventClockRateEntry : sortedTelephoneEventClockRateMap.entrySet()) {
                    int telephoneEventClockRate = telephoneEventClockRateEntry.getValue();
                    for (Map.Entry<String, Integer> codeEntry : codecClockRates.entrySet()) {
                        if(codeEntry.getValue() == telephoneEventClockRate){
                            String media = codeEntry.getKey().split("_")[1];
                            if(supportedCodeList.contains(media)){
                                vodMedias.add(media);
                            }
                        }
                    }
                }
            }

        }catch (Exception e){
            logger.error("getVodMedias exception.",e);
        }
        if(vodMedias.isEmpty()){
            if(audioCodeList.contains(CommonConstant.PAYLOAD.PCMA)){
                vodMedias.add(CommonConstant.MEDIA.PCMA);
            }
            resultCode = -2;
        }else{
            resultCode = 0;
        }

        vodMediaList.addAll(vodMedias);

        resultMap.put("code",resultCode);
        resultMap.put("media",vodMediaList);
        return resultMap;
    }

    public static Map<String, Integer> sortByValue(HashMap<String, Integer> map) {
        // 将HashMap的entrySet转换成List
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());

        // 使用Collections.sort()方法对List进行排序，根据Map的值进行降序排序
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue()); // 降序排序
            }
        });

        // 创建一个新的LinkedHashMap，以保持排序后的顺序
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : list) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    private static String extractAudioSDP(byte[] rawContent) throws Exception{
        // 正则表达式匹配 m=audio 行及其后续内容
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        StringBuilder resultBuilder = new StringBuilder();
        while (line != null) {
            if (line.startsWith("m=video")) {
                break;
            }else{
                resultBuilder.append(line).append("\r\n");
                line = br.readLine();
            }
        }
        return resultBuilder.toString();
    }

    public static boolean isRemoteVideoClose(byte[] rawContent) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        boolean isRemoteVideoClosed = false;
        while (line != null) {
            if (line.startsWith("m=video")) {
                String[] parts = line.split(" ");
                String strVideoPort = parts[1];
                if (strVideoPort.equals("0")) {
                    isRemoteVideoClosed = true;
                } else {
                    isRemoteVideoClosed = false;
                }
                break;
            }
            line = br.readLine();
        }
        return isRemoteVideoClosed;
    }


    public static long getSessionVersion(byte[] rawContent) {
        long sessionVersion = 1;
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("o=")) {
                    String[] o = line.split(" ");
                    sessionVersion = Long.valueOf(o[2]);
                    break;
                }
                line = br.readLine();
            }
            return sessionVersion;
        } catch (Exception e) {
            return sessionVersion;
        }
    }

    public static byte[] increaseSessionVersion(byte[] rawContent) {
        StringBuilder rtnBuiler = new StringBuilder();
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("o=")) {
                    String[] o = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    long sessionVersion = Long.valueOf(o[2]);
                    o[2] = String.valueOf(sessionVersion + 1);
                    for (String s : o) {
                        sb.append(s).append(" ");
                    }
                    //去掉最末尾的" "
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    line = sb.toString();
                }
                rtnBuiler.append(line).append("\r\n");
                line = br.readLine();
            }
            return rtnBuiler.toString().getBytes();
        } catch (Exception e) {
            return rawContent;
        }
    }

    public static byte[] changeSessionInfo(byte[] rawContent, String sessionId, long newSessionVersion) {
        StringBuilder rtnBuiler = new StringBuilder();
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("o=")) {
                    String[] o = line.split(" ");
                    StringBuilder sb = new StringBuilder();
                    o[1] = sessionId;
                    o[2] = String.valueOf(newSessionVersion);
                    for (String s : o) {
                        sb.append(s).append(" ");
                    }
                    //去掉最末尾的" "
                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    line = sb.toString();
                }
                rtnBuiler.append(line).append("\r\n");
                line = br.readLine();
            }
            return rtnBuiler.toString().getBytes();
        } catch (Exception e) {
            return rawContent;
        }
    }


    public static VideoParam getRemoteVideoInfo(byte[] rawContent) throws Exception {
        VideoParam videoParam = new VideoParam();
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        String sessionIp = "";
        String videoIp = "";
        String currentMedia = "";
        String videoPort = "";
        boolean hasVideo = false;
        while (line != null) {
            if (line.startsWith("m=video")) {
                currentMedia = "video";
                hasVideo = true;
                String[] parts = line.split(" ");
                videoPort = parts[1];
            } else if (line.startsWith("m=")) {
                currentMedia = "other";
            }
            if (line.startsWith("c=")) {
                String ip = line.split(" ")[2];
                if (currentMedia.equals("video")) {
                    videoIp = ip;
                } else if (StringUtils.isBlank(currentMedia)) {
                    sessionIp = ip;
                }
            }
            line = br.readLine();
        }
        if (hasVideo) {
            videoParam.setVideoIp(StringUtils.isBlank(videoIp) ? sessionIp : videoIp);
            videoParam.setVideoPort(Integer.parseInt(videoPort));
        }
        return videoParam;
    }

    public static String trimVideoLines(byte[] rawContent) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("m=video")) {
                    break;
                } else {
                    sb.append(line).append("\r\n");
                }
                line = br.readLine();
            }
            return sb.toString();
        }
        return "";
    }

    public static String processAudioSdp(byte[] rawContent) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            boolean isVideo = false;
            while (line != null) {
                if (line.startsWith("m=video")) {
                    isVideo = true;
                }
                if (!isVideo && !line.contains("a=conf:qos remote sendrecv")) {
                    if (line.contains("mandatory")) {
                        line = line.replace("mandatory", "optional");
                    }
                    sb.append(line).append("\r\n");
                } else if (isVideo) {
                    sb.append(line).append("\r\n");
                }
                line = br.readLine();
            }
            return sb.toString();
        }
        return "";
    }


    /**
     * b=AS:2211
     * b=RS:8600
     * b=RR:8000
     */
    public static String replaceBLineBeforeMedia(String content) throws IOException {
        if (StringUtils.isNotEmpty(content)) {
            int mIndex = content.indexOf("m=");
            String sessionDesc = content.substring(0, mIndex);
            String remainDesc = content.substring(mIndex);
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(sessionDesc.getBytes()));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("b=AS:") || line.startsWith("b=RS:") || line.startsWith("b=RR:")) {
                    line = br.readLine();
                    continue;
                } else {
                    sb.append(line).append("\r\n");
                    line = br.readLine();
                }
            }

            StringBuilder newBr = new StringBuilder();
            boolean isBrAppend = false;
            String[] newDescArr = sb.toString().split("\r\n");
            for (String newLine : newDescArr) {
                if (!isBrAppend) {
                    String lineHead = newLine.substring(0, 2);
                    if (lineHead.equals("r=") || lineHead.equals("t=") || lineHead.equals("z=") || lineHead.equals("k=")
                            || lineHead.equals("a=")) {
                        newBr.append("b=AS:2211").append("\r\n");
                        newBr.append("b=RS:8600").append("\r\n");
                        newBr.append("b=RR:8000").append("\r\n");
                        newBr.append(newLine).append("\r\n");
                        isBrAppend = true;
                    } else {
                        newBr.append(newLine).append("\r\n");
                    }
                } else {
                    newBr.append(newLine).append("\r\n");
                }
            }
            if (!isBrAppend) {
                newBr.append("b=AS:2211").append("\r\n");
                newBr.append("b=RS:8600").append("\r\n");
                newBr.append("b=RR:8000").append("\r\n");
            }
            newBr.append(remainDesc);
            return newBr.toString();
        }
        return "";
    }

    public static String replaceAudioBLine(String content) {
        if (StringUtils.isNotEmpty(content)) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(content.getBytes()));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = null;
            try {
                line = br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            boolean isAudio = false;
            while (line != null) {
                if (line.startsWith("m=")) {
                    if (line.startsWith("m=audio")) {
                        isAudio = true;
                    } else {
                        isAudio = false;
                    }
                    sb.append(line).append("\r\n");
                    try {
                        line = br.readLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                if (isAudio) {
                    if (line.startsWith("b=AS:")) {
                        line = "b=AS:82";
                        sb.append(line).append("\r\n");
                    } else if (line.startsWith("b=RS:")) {
                        line = "b=RS:600";
                        sb.append(line).append("\r\n");
                    } else if (line.startsWith("b=RR:")) {
                        line = "b=RR:2000";
                        sb.append(line).append("\r\n");
                    } else if (line.endsWith("AMR-WB/16000")) {
                        line = line + "/1";
                        sb.append(line).append("\r\n");
                    } else if (line.contains("max-red")) {
                        line = line.replace("max-red=220", "max-red=0");
                        sb.append(line).append("\r\n");
                    } else if (line.startsWith("a=maxptime")) {
                        line = "a=maxptime:240";
                        sb.append(line).append("\r\n");
                    } else {
                        sb.append(line).append("\r\n");
                    }
                } else {
                    sb.append(line).append("\r\n");
                }

                try {
                    line = br.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println(line);
            }
            return sb.toString();
        }
        return "";
    }

    public static String getMediaDirection(byte[] sdpContent, String media) {
        if (sdpContent == null)
            return "";
        String mediaDirection = "";
        String sessionLevelDirection = "";
        String mediaLinePrefix = "m=" + media;
        try {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(sdpContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            String line = br.readLine();
            boolean isSpecifiedMedia = false; // 标识当前media 是否是 audio
            boolean isSessionLevel = true;
            boolean hasSpecifiedMedia = false; //标识 m=audio 是否已出现
            String sessionConnIp = "";
            String mediaConnIp = "";
            while (line != null) {
                String lineContent = line.toLowerCase();
                if (lineContent.startsWith("m=")) {
                    isSessionLevel = false;
                    if (hasSpecifiedMedia)
                        break;
                }
                if (lineContent.startsWith(mediaLinePrefix)) {
                    isSpecifiedMedia = true;
                    hasSpecifiedMedia = true;
                }


                if (lineContent.startsWith("c=")) {
                    String ip = lineContent.split(" ")[2];
                    if (isSessionLevel) {
                        sessionConnIp = ip;
                    } else if (isSpecifiedMedia) {
                        mediaConnIp = ip; // 记录 media=audio 的IP
                    }
                }

                String direction = "";
                if (lineContent.startsWith("a=sendrecv")) {
                    direction = MediaDirection.DEC_SENDRECV;
                } else if (lineContent.startsWith("a=sendonly")) {
                    direction = MediaDirection.DEC_SENDONLY;
                } else if (lineContent.startsWith("a=recvonly")) {
                    direction = MediaDirection.DEC_RECVONLY;
                } else if (lineContent.startsWith("a=inactive")) {
                    direction = MediaDirection.DEC_INACTIVE;
                } else if (lineContent.startsWith("a=none")) {
                    direction = MediaDirection.DEC_NONE;
                }

                if (StringUtils.isNotEmpty(direction)) {
                    if (isSessionLevel) {
                        sessionLevelDirection = direction;
                    } else if (isSpecifiedMedia) {
                        mediaDirection = direction;
                        break;
                    }
                }
                line = br.readLine();
            }
            //hasSpecifiedMedia 放在 if 判断表示只记录 audio 的 media direction，如果协商中没有 audio, 则不记录
            if (hasSpecifiedMedia && StringUtils.isEmpty(mediaDirection)) {
                //根据RFC8866 6.7：Media Direction Attributes：If none appears in a media description, then the one from session level,
                // if any, applies to that media description.
                if (StringUtils.isNotEmpty(sessionLevelDirection)) {
                    mediaDirection = sessionLevelDirection;
                } else {
                    //根据RFC8866 6.7： Media Direction Attributes：If none of the media direction attributes is present at either session level or media level,
                    //"a=sendrecv" be assumed as the default.
                    mediaDirection = "sendrecv";
                }
            }

            //如果 c 行的IP 为 invalid, 则 media direction 为 invalid, 此时无需看 a=sendrecv/sendonly/recvonly/inactive
            //如果 session 的 c 行 IP 为 invalid, 但是 media=audio 的 c 行 IP 非 invalid, 则不应记为 invalid
            if (hasSpecifiedMedia) {
                if (mediaConnIp.contains("invalid") || (sessionConnIp.contains("invalid") && StringUtils.isEmpty(mediaConnIp))) {
                    mediaDirection = MediaDirection.DEC_INVALID;
                }
            }

        } catch (Exception e) {
            logger.warn("getMediaDirection exception ", e);
        }

        return mediaDirection;
    }

    public static String generateReinviteSdp(byte[] rawContent, String[] amrwbPayload, String amrwbSDP, boolean isVideoNeed) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sbBefore = new StringBuilder();
            StringBuilder sbAfter = new StringBuilder();
            String line = br.readLine();
            String telEventPayload = "";
            boolean isBeforeAudioLine = true;
            int telLineNumber = 0;
            ArrayList<String> audioCodeSupport = new ArrayList<>();
            audioCodeSupport.addAll(Arrays.asList(amrwbPayload));
            String audioPort = "";
            while (line != null) {
                if (line.startsWith("m=video") && isVideoNeed) {
                    break;
                } else if (line.startsWith("m=audio")) {
                    isBeforeAudioLine = false;
                    String[] items = line.split(" ");
                    audioPort = items[1];
                } else if (line.startsWith("a=rtpmap")) {
                    if (line.contains("telephone-event") && telLineNumber == 0) {
                        String[] items = line.split(" ");
                        telEventPayload = items[0].split(":")[1];
                        sbAfter.append(line).append("\r\n");
                        telLineNumber = sbAfter.indexOf(line);
                        audioCodeSupport.add(telEventPayload);
                    }
                } else if (line.startsWith("a=fmtp")) {
                    String[] items = line.split(" ");
                    String itemPayload = items[0].split(":")[1];
                    if (itemPayload.equals(telEventPayload)) {
                        sbAfter.append(line).append("\r\n");
                    }
                } else if (line.startsWith("c=")) {
                    /*String ip = line.split(" ")[2];
                    if (StringUtil.isIp(ip)) {
                        line = String.format("c=IN IP4 %s", mediaSession.getProxyEndpoint().getIp());
                    }*/
                    if (isBeforeAudioLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                } else {
                    if (isBeforeAudioLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }
                line = br.readLine();
            }
            if (telLineNumber > 0) {
                sbAfter.insert(telLineNumber, amrwbSDP);
            } else {
                sbAfter.append(amrwbSDP);
            }
            sbBefore.append(String.format("m=audio %s RTP/AVP %s", audioPort, String.join(" ", audioCodeSupport))).append("\r\n").append(sbAfter);
            return sbBefore.toString();
        }
        return "";
    }

    public static String createG711Answer(byte[] rawContent, String ip, int port) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sbBefore = new StringBuilder();
            StringBuilder sbAfter = new StringBuilder();
            String line = br.readLine();
            String telEventPayload = "";
            String g711Payload = "";
            boolean isBeforeAudioLine = true;
            ArrayList<String> audioCodeSupport = new ArrayList<>();
            while (line != null) {
                if (line.startsWith("m=audio")) {
                    isBeforeAudioLine = false;
                } else if (line.startsWith("a=rtpmap")) {
                    String[] items = line.split(" ");
                    String itemPayload = items[0].split(":")[1];
                    if (line.contains("telephone-event") && StringUtils.isBlank(telEventPayload)) {
                        telEventPayload = itemPayload;
                        sbAfter.append(line).append("\r\n");
                        audioCodeSupport.add(itemPayload);
                    } else if (itemPayload.equals("0") || itemPayload.equals("8")) {
                        if (StringUtils.isBlank(g711Payload)) {
                            g711Payload = itemPayload;
                            sbAfter.append(line).append("\r\n");
                            audioCodeSupport.add(itemPayload);
                        }
                    }
                } else if (line.startsWith("a=fmtp")) {
                    String[] items = line.split(" ");
                    String itemPayload = items[0].split(":")[1];
                    if (itemPayload.equals(telEventPayload) || itemPayload.equals(g711Payload)) {
                        sbAfter.append(line).append("\r\n");
                    }
                } else if (line.startsWith("c=")) {
                    String tempIp = line.split(" ")[2];
                    if (StringUtil.isIp(tempIp)) {
                        line = String.format("c=IN IP4 %s", ip);
                    }
                    if (isBeforeAudioLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                } else if (!(line.startsWith("a=curr:") || line.startsWith("a=des:"))) {
                    if (isBeforeAudioLine) {
                        sbBefore.append(line).append("\r\n");
                    } else {
                        sbAfter.append(line).append("\r\n");
                    }
                }

                line = br.readLine();
            }
            sbBefore.append(String.format("m=audio %d RTP/AVP %s", port, String.join(" ", audioCodeSupport))).append("\r\n").append(sbAfter);
            return sbBefore.toString();
        }
        return "";
    }



    public static String getCloseVideoLine(byte[] rawContent) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        String videoLine = "";
        while (line != null) {
            if (line.startsWith("m=video")) {
                String[] items = line.split(" ");
                items[1] = "0";
                videoLine = String.join(" ", items);
                break;
            }
            line = br.readLine();
        }
        return videoLine;
    }

    /**
     * 生成动态 payload
     *
     * @param rawContent - 主叫初始 invite 的 SDP
     * @param payloadCnt - 生成动态 payload 的个数
     * @return
     * @throws Exception
     */
    public static Integer[] generateDynamicPayload(byte[] rawContent, int payloadCnt) throws Exception {
        InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
        BufferedReader br = new BufferedReader(bufferedReader);
        String line = br.readLine();
        Set<Integer> payloadArray = new HashSet<>();
        Set<Integer> payloadSet = new HashSet<>();
        int maxPayload = 0;
        while (line != null) {
            if (line.startsWith("m=audio") || line.startsWith("m=video")) {
                String[] parts = line.split(" ");
                if (parts.length > 3) {
                    for (int i = 3; i < parts.length; i++) {
                        int payload = Integer.valueOf(parts[i]);
                        payloadSet.add(payload);
                        maxPayload = payload > maxPayload ? payload : maxPayload;
                    }
                }
            }
            line = br.readLine();
        }

        if (maxPayload < 113) {
            maxPayload = 113;
        }

        int payload = maxPayload;
        for (int i = 0; i < payloadCnt; i++) {
            boolean isPayloadFixed = false;
            //先往上加
            while (payload < 127) {
                ++payload;
                if (payload <= 127 && !payloadSet.contains(payload) && !payloadArray.contains(payload)) {
                    payloadArray.add(payload);
                    isPayloadFixed = true;
                    break;
                }
            }
            if (isPayloadFixed)
                continue;

            //若前面往上加获取不到，则往下减获取
            while (payload > 96) {
                --payload;
                if (payload >= 96 && !payloadSet.contains(payload) && !payloadArray.contains(payload)) {
                    payloadArray.add(payload);
                    isPayloadFixed = true;
                    break;
                }
            }

            if (isPayloadFixed)
                continue;

            logger.info("generateValidPayload fail");
        }
        return payloadArray.toArray(new Integer[0]);
    }

    public static String trimAudioQosLine(byte[] rawContent) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (!line.startsWith("a=curr:qos") && !line.startsWith("a=des:qos")) {
                    sb.append(line).append("\r\n");
                }
                line = br.readLine();
            }
            return sb.toString();
        }
        return "";
    }

    public static String addAudioQosLine(byte[] rawContent) throws IOException {
        if (rawContent != null) {
            InputStreamReader bufferedReader = new InputStreamReader(new ByteArrayInputStream(rawContent));
            BufferedReader br = new BufferedReader(bufferedReader);
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                if (line.startsWith("m=video")) {
                    sb.append("a=curr:qos local none\r\n");
                    sb.append("a=curr:qos remote sendrecv\r\n");
                    sb.append("a=des:qos mandatory local sendrecv\r\n");
                    sb.append("a=des:qos mandatory remote sendrecv\r\n");
                    sb.append(line).append("\r\n");
                } else {
                    sb.append(line).append("\r\n");
                }
                line = br.readLine();
            }
            return sb.toString();
        }
        return "";
    }

    public static SessionDescription transferStrToSdp(byte[] byteSdp, String sessionId) {
        //将sdp文本转化成SessionDescription对象
        try {
            return SessionDescriptionParser.parse(byteSdp, sessionId);
        } catch (Exception e) {
            return null;
        }
    }

}
