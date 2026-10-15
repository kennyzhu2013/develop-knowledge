package com.cmic.recording.videocall;

import com.cmic.sdp.fields.MediaDescField;

public class VideoSdp {
    int port;
    String[] codec;

    public VideoSdp(String line){
        String portString = line.split(" ")[1];
        if (portString.contains("/")) {
            this.port = Integer.parseInt(portString.split("/")[0]);
        } else {
            this.port = Integer.parseInt(line.split(" ")[1]);
        }
    }

    public VideoSdp(int mediaPort){
        this.port = mediaPort;
    }

    public VideoSdp(int port, String[] codec){
        this.port = port;
        this.codec = codec;
    }

    public int getPort() {
        return port;
    }
}
