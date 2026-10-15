package com.cmic.recording;

import org.apache.commons.lang.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicLong;

public class SdpSession {
    //每通通话绑定一个SdpSession
    //o=<用户名> <会话ID> <会话版本> <网络类型> <地址类型> <单播地址>
    //o=HuaweiSoftx3000 1080563923 1080563924 IN IP4 10.184.152.165
    private String rightLocalSessionId = "";
    private String leftLocalSessionId = "";
    private String rightRemoteSessionId = "";
    private String leftRemoteSessionId = "";

    private long rightLocalSessionVersion;
    private long leftLocalSessionVersion;
    private long leftRemoteSessionVersion;
    private long rightRemoteSessionVersion;
    //RemoteVersion与SessionId可以由外部UAC,UAS返回的信息修改

    //LocalVersion初始化之后只能自己自增，LocalSessionId初始化之后无法改变





    public void acceptRightSdp(byte []sdp) throws Exception{
        InputStreamReader inputStreamReader = new InputStreamReader(new ByteArrayInputStream(sdp));
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line = bufferedReader.readLine();
        while(line!=null){
            if(line.startsWith("o=")){
                acceptRightO(line);
                break;
            }
            line = bufferedReader.readLine();
        }
    }

    public void acceptLeftSdp(byte []sdp) throws Exception{
        InputStreamReader inputStreamReader = new InputStreamReader(new ByteArrayInputStream(sdp));
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line = bufferedReader.readLine();
        while(line!=null){
            if(line.startsWith("o=")){
                acceptLeftO(line);
                break;
            }
            line = bufferedReader.readLine();
        }
    }


    public void acceptRightO(String line) throws Exception{
        String o[] = line.split(" ");
        String sessionId = o[1];
        long sessionVersion = Long.valueOf(o[2]);
        if (StringUtils.isEmpty(leftLocalSessionId)) {
            leftLocalSessionId = sessionId;
            rightRemoteSessionId = sessionId;
        }
        if(leftLocalSessionVersion == 0){
            leftLocalSessionVersion = sessionVersion;
        }
        if(!rightRemoteSessionId.equalsIgnoreCase(sessionId)){
            increaseLeftLocalSessionVersion();
            rightRemoteSessionId = sessionId;
            rightRemoteSessionVersion = sessionVersion;
        }else {
            if (rightRemoteSessionVersion == 0) {
                rightRemoteSessionVersion = sessionVersion;
                if (leftLocalSessionVersion == 0) {
                    leftLocalSessionVersion = sessionVersion;
                }
            }
            if (rightRemoteSessionVersion < sessionVersion) {
                rightRemoteSessionVersion = sessionVersion;
                increaseLeftLocalSessionVersion();
            }
        }



    }

    public void acceptLeftO(String line) throws Exception{
        String o[] = line.split(" ");
        String sessionId = o[1];
        long sessionVersion = Long.valueOf(o[2]);
        //一开始没有初始化的话，先初始化
        if (StringUtils.isEmpty(rightLocalSessionId)) {
            rightLocalSessionId = sessionId;
            leftRemoteSessionId = sessionId;
        }
        if(rightLocalSessionVersion==0){
           rightLocalSessionVersion = sessionVersion;
        }
        if(!leftRemoteSessionId.equalsIgnoreCase(sessionId)){
            increaseRightLocalSessionVersion();
            leftRemoteSessionId = sessionId;
            leftRemoteSessionVersion = sessionVersion;
        }else {
            if (leftRemoteSessionVersion == 0) {
                leftRemoteSessionVersion = sessionVersion;
                if (rightLocalSessionVersion == 0) {
                    rightLocalSessionVersion = sessionVersion;
                }
            }
            if (leftRemoteSessionVersion < sessionVersion) {
                leftRemoteSessionVersion = sessionVersion;
                increaseRightLocalSessionVersion();
            }
        }
    }

    public void increaseLeftLocalSessionVersion(){
        leftLocalSessionVersion++;
    }

    public void increaseRightLocalSessionVersion(){
        rightLocalSessionVersion++;
    }

    public long getLeftLocalSessionVersion() {
        return leftLocalSessionVersion;
    }

    public long getRightLocalSessionVersion() {
        return rightLocalSessionVersion;
    }

    public String getLeftLocalSessionId() {
        return leftLocalSessionId;
    }

    public String getRightLocalSessionId() {
        return rightLocalSessionId;
    }

    public String getLeftRemoteSessionId() {
        return leftRemoteSessionId;
    }

    public long getLeftRemoteSessionVersion() {
        return leftRemoteSessionVersion;
    }

    public long getRightRemoteSessionVersion() {
        return rightRemoteSessionVersion;
    }

    public String getRightRemoteSessionId() {
        return rightRemoteSessionId;
    }

    @Override
    public String toString() {
        return "SdpSession{" +
                "rightLocalSessionId='" + rightLocalSessionId + '\'' +
                ", leftLocalSessionId='" + leftLocalSessionId + '\'' +
                ", rightRemoteSessionId='" + rightRemoteSessionId + '\'' +
                ", leftRemoteSessionId='" + leftRemoteSessionId + '\'' +
                ", rightLocalSessionVersion=" + rightLocalSessionVersion +
                ", leftLocalSessionVersion=" + leftLocalSessionVersion +
                ", leftRemoteSessionVersion=" + leftRemoteSessionVersion +
                ", rightRemoteSessionVersion=" + rightRemoteSessionVersion +
                '}';
    }
}
