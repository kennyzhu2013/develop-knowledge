package com.cmic.util;

import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class PreconditionParam {
    public String currLocalDirection;

    public String currRemoteDirection;

    public String destLocalDirection;
    public String destRemoteDirection;

    public String destLocalStrength;
    public String destRemoteStrength;

    public String confDirection;

    public PreconditionParam() {
        currLocalDirection = DIRECTION_NONE;
        currRemoteDirection = DIRECTION_NONE;
        destLocalDirection = DIRECTION_SENDRECV;
        destRemoteDirection = DIRECTION_SENDRECV;
        destLocalStrength = STRENGTH_MANDATORY;
        destRemoteStrength = STRENGTH_MANDATORY;
    }

    public PreconditionParam opposite() {
        String temp;
        temp = currLocalDirection;
        currLocalDirection = currRemoteDirection;
        currRemoteDirection = temp;

        temp = destLocalDirection;
        destLocalDirection = destRemoteDirection;
        destRemoteDirection = temp;

        temp = destLocalStrength;
        destLocalStrength = destRemoteStrength;
        destRemoteStrength = temp;
        return this;
    }

    public PreconditionParam oppositeClone() {
        PreconditionParam ret = new PreconditionParam();
        ret.currLocalDirection = currRemoteDirection;
        ret.currRemoteDirection = currLocalDirection;
        ret.destLocalDirection = destRemoteDirection;
        ret.destRemoteDirection = destLocalDirection;
        ret.destLocalStrength = destRemoteStrength;
        ret.destRemoteStrength = destLocalStrength;
        ret.confDirection = confDirection;
        return ret;
    }

    public PreconditionParam setCurrLocal(String direction) {
        this.currLocalDirection = direction;
        return this;
    }

    public PreconditionParam setCurrRemoteDirection(String currRemoteDirection) {
        this.currRemoteDirection = currRemoteDirection;
        return this;
    }

    public PreconditionParam setConf(String direction) {
        this.confDirection = direction;
        return this;
    }

    public PreconditionParam setDestRemoteStrength(String destRemoteStrength) {
        this.destRemoteStrength = destRemoteStrength;
        return this;
    }

    public PreconditionParam setDestLocalStrength(String destLocalStrength) {
        this.destLocalStrength = destLocalStrength;
        return this;
    }


    public String getCurrLocal() {
        return String.format("a=curr:qos local %s", currLocalDirection);
    }

    public String getCurrRemote() {
        return String.format("a=curr:qos remote %s", currRemoteDirection);
    }

    public String getDestLocal() {
        return String.format("a=des:qos %s local %s", destLocalStrength, destLocalDirection);
    }

    public String getDestRemote() {
        return String.format("a=des:qos %s remote %s", destRemoteStrength, destRemoteDirection);
    }

    public String getConf() {
        return String.format("a=conf:qos remote %s", confDirection);
    }


    @Override
    public String toString() {
        if(StringUtils.isEmpty(currLocalDirection)||StringUtils.isEmpty(currRemoteDirection)||StringUtils.isEmpty(destLocalStrength)||StringUtils.isEmpty(destLocalDirection)||StringUtils.isEmpty(destRemoteStrength)||StringUtils.isEmpty(destRemoteDirection)){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("a=curr:qos local %s\r\n", currLocalDirection));
        sb.append(String.format("a=curr:qos remote %s\r\n", currRemoteDirection));
        sb.append(String.format("a=des:qos %s local %s\r\n", destLocalStrength, destLocalDirection));
        sb.append(String.format("a=des:qos %s remote %s\r\n", destRemoteStrength, destRemoteDirection));

        if (confDirection != null && !getCurrRemote().equalsIgnoreCase(getDestRemote())) {
            sb.append(String.format("a=conf:qos remote %s\r\n", confDirection));
        }
        return sb.toString();
    }

    public PreconditionParam(byte[] sdpContent) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(sdpContent)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("m=video") || line.contains("m=application")) {
                    // 默认只处理m=audio下面的qos参数
                    break;
                }
                if (line.indexOf("a=des") > -1) {
                    String[] attributes = line.split(" ");
                    if (attributes[2].equals("local")) {
                        this.destLocalStrength = attributes[1];
                        this.destLocalDirection = attributes[3];
                    }
                    if (attributes[2].equals("remote")) {
                        this.destRemoteStrength = attributes[1];
                        this.destRemoteDirection = attributes[3];
                    }
                }
                if (line.indexOf("a=curr") > -1) {
                    String[] attributes = line.split(" ");
                    if (attributes[1].equals("local")) this.currLocalDirection = attributes[2];
                    if (attributes[1].equals("remote")) this.currRemoteDirection = attributes[2];
                }
                if (line.indexOf("a=conf") > -1) {
                    String[] attributes = line.split(" ");
                    this.confDirection = attributes[2];
                }
            }
        } catch (Exception e) {
        }
    }

    public static Map<String, PreconditionParam> getPreconditionParam(byte[] sdpContent) throws IOException {
        HashMap<String, PreconditionParam> preconditionParamHashMap = new HashMap<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(sdpContent)));
        String line;
        String currentMediaField = MEDIA_FIELD_AUDIO;
        // 默认初始化先读取audio下的Qos参数
        PreconditionParam currentPrecondition = null;
        boolean ifSkipCurrentMediaField = false;
        while ((line = br.readLine()) != null) {
            if (line.contains("m=video") || line.contains("m=application")) {
                if (currentPrecondition != null) {
                    preconditionParamHashMap.put(currentMediaField, currentPrecondition);
                    currentPrecondition = null;
                }
                if (Integer.parseInt(line.split(" ")[1]) > 0){
                    if (line.contains("m=video")) {
                        currentMediaField = MEDIA_FIELD_VIDEO;
                    } else {
                        currentMediaField = MEDIA_FIELD_APPLICATION;
                    }
                    ifSkipCurrentMediaField = false;
                } else {
                    ifSkipCurrentMediaField = true;
                }
            }
            if (!ifSkipCurrentMediaField) {
                if (currentPrecondition == null) {
                    if (line.contains("a=des") || line.contains("a=curr") || line.contains("a=conf")) {
                        currentPrecondition = new PreconditionParam();
                    }
                }
            } else {
                continue;
            }
            if (currentPrecondition != null) {
                if (line.contains("a=des")) {
                    String[] attributes = line.split(" ");
                    if (attributes[2].equals("local")) {
                        currentPrecondition.destLocalStrength = attributes[1];
                        currentPrecondition.destLocalDirection = attributes[3];
                    }
                    if (attributes[2].equals("remote")) {
                        currentPrecondition.destRemoteStrength = attributes[1];
                        currentPrecondition.destRemoteDirection = attributes[3];
                    }
                }
                if (line.contains("a=curr")) {
                    String[] attributes = line.split(" ");
                    if (attributes[1].equals("local")) currentPrecondition.currLocalDirection = attributes[2];
                    if (attributes[1].equals("remote")) currentPrecondition.currRemoteDirection = attributes[2];
                }
                if (line.contains("a=conf")) {
                    String[] attributes = line.split(" ");
                    currentPrecondition.confDirection = attributes[2];
                }
            }
        }
        if (currentPrecondition != null) {
            preconditionParamHashMap.put(currentMediaField, currentPrecondition);
        }
        return preconditionParamHashMap;
    }


    public static final String DIRECTION_NONE = "none";
    public static final String DIRECTION_SEND = "send";
    public static final String DIRECTION_RECV = "recv";
    public static final String DIRECTION_SENDRECV = "sendrecv";


    public static final String STRENGTH_FAILURE = "failure";
    public static final String STRENGTH_NONE = "none";
    public static final String STRENGTH_OPTIONAL = "optional";
    public static final String STRENGTH_MANDATORY = "mandatory";

    public static final String[] STRENGTH = {
            "failure",
            "none",
            "optional",
            "mandatory"
    };

    public static final String[] DIRECTION = {
            "none",
            "send",
            "recv",
            "sendrecv"
    };

    public static final String MEDIA_FIELD_AUDIO = "audio";
    public static final String MEDIA_FIELD_VIDEO = "video";
    public static final String MEDIA_FIELD_APPLICATION = "application";

    public static void main(String[] args) {
        String sdp = "m=video 29688 RTP/AVP 114\n" +
                "b=AS:946\n" +
                "b=RR:6000\n" +
                "b=RS:8000\n" +
                "a=rtpmap:114 H264/90000\n" +
                "a=fmtp:114 profile-level-id=42C01E;packetization-mode=1;max-br=960;sprop-parameter-sets=Z0LAHtqHgUSagwCCg8IBCoA=,aM48gA==\n" +
                "a=recvonly\n" +
                "a=curr:qos local sendrecv\n" +
                "a=curr:qos remote send\n" +
                "a=des:qos mandatory local sendrecv\n" +
                "a=des:qos optional remote send\n" +
                "a=des:qos mandatory remote recv\n";
        try {
            PreconditionParam pp = new PreconditionParam(sdp.getBytes());
            System.out.println(pp);
            //pp.opposite();
            //System.out.println(pp);
        } catch (Exception e) {
        }
    }
}
