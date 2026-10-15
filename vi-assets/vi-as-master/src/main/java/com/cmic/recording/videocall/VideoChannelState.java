package com.cmic.recording.videocall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoChannelState {
    /**
     * 表示视频通道状态
     * 0 表示关闭
     * 1 表示打开
     */

    public Logger logger = LoggerFactory.getLogger(getClass());
    int state = 0;

    public final static int CLOSE_STATE = 0;
    public final static int OPEN_STATE = 1;

    public int getState() {
        return state;
    }

    public void acceptSdp(VideoSdp offer, VideoSdp answer) {
        if (state == OPEN_STATE) {
            if (offer == null || answer == null) {
                state = CLOSE_STATE;
            } else {
                if (answer.getPort() == 0 || offer.getPort() == 0) {
                    state = CLOSE_STATE;
                }
            }
        } else if (state == CLOSE_STATE) {
            if (offer != null && answer != null) {
                if (offer.getPort() != 0 && answer.getPort() != 0) {
                    state = OPEN_STATE;
                }
            }
        }
    }
}
