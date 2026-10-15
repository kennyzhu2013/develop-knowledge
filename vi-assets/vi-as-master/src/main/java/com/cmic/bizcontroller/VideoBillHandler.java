package com.cmic.bizcontroller;

import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.recording.videocall.VideoChannelListener;
import com.cmic.recording.videocall.VideoChannelState;

public class VideoBillHandler implements VideoChannelListener {

    private NewTwoLegController controller;

    public VideoBillHandler(NewTwoLegController controller) {
        this.controller = controller;
    }

    @Override
    public void onOpen() {
        // if (controller.callRecorder.isHasRecv200ok()) {
        //     controller.getBill().setVideoCallStartTime(System.currentTimeMillis());
        //     controller.getBill().setVideoCallTimes(controller.getBill().getVideoCallTimes() + 1);
        // }
        controller.setVideoState(VideoChannelState.OPEN_STATE);

    }

    @Override
    public void onClose() {
        if (controller.callRecorder.isHasRecv200ok()) {
            //200OK之前打开了video通道，没记录起始时间和次数
            // if (controller.getBill().getVideoCallStartTime() == null) {
            //     controller.getBill().setVideoCallStartTime(controller.getBill().getCallStartTime().getTime());
            //     controller.getBill().setVideoCallTimes(1);
            // }
            // controller.getBill().setVideoCallStopTime(System.currentTimeMillis());
        }
        controller.setVideoState(VideoChannelState.CLOSE_STATE);
    }

}
