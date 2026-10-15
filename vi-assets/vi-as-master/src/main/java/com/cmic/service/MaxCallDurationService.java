package com.cmic.service;

import com.cmic.BillNew;
import com.cmic.business.BusinessEvent;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.constant.MediaConst;
import com.cmic.recording.MediaSession;
import com.cmic.sip.leg.Leg;
import com.cmic.state.FinishState;
import com.cmic.util.DtmfLogger;

import javax.sip.DialogState;
import java.sql.Timestamp;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MaxCallDurationService {

    public static ScheduledFuture maxCallDurationControl(Leg leftLeg, Leg rightLeg, CallParameter callParameter, BillNew billNew, MediaSession mediaSession, BusinessEvent businessEvent) {
        return ScheduleService.getInstance().schedule(() -> {
            DtmfLogger.info(billNew.getId(),"maxCallDurationControl start!!!");
            if (!leftLeg.dialog.getState().equals(DialogState.TERMINATED) || !rightLeg.dialog.getState().equals(DialogState.TERMINATED)) {
                if (callParameter.getLastMinVoice() != null) {
                    MediaService.dccPlayMedia(callParameter.getLastMinVoice(),"",
                            "", "", MediaConst.PLAY_PERIOD.INCALL, MediaConst.PRIORITY.LOCAL, callParameter.getCalledPlayDirection(),
                            callParameter.getRecordMode(), event -> {
                                releaseCallAfterOneMinute(billNew, callParameter, leftLeg, rightLeg);
                            }, mediaSession, businessEvent, false, billNew.getCompanyId(),true);
                } else {
                    try {
                        releaseCall(billNew, callParameter, leftLeg, rightLeg);
                    } catch (Exception e) {
                        DtmfLogger.info(billNew.getId(), "something error happen when releaseCall :{}", e);
                    }

                }
            }
        }, callParameter.getMaxDuration(), callParameter.getMaxDurationTimeUnit());
    }

    private static void releaseCallAfterOneMinute(BillNew billNew, CallParameter callParameter, Leg leftLeg, Leg rightLeg) {
        ScheduleService.getInstance().schedule(() -> {
            try {
                releaseCall(billNew, callParameter, leftLeg, rightLeg);
            } catch (Exception e) {
                DtmfLogger.error(billNew.getId(), "something error happen when ReleasCallAfterOneMinute ");
            }
        }, 1, TimeUnit.MINUTES);
    }

    private static void releaseCall(BillNew billNew, CallParameter callParameter, Leg leftLeg, Leg rightLeg) throws Exception {
        DtmfLogger.info(billNew.getId(), "release call because of max call duration");
        billNew.setCallEndTime(new Timestamp(System.currentTimeMillis()));
        callParameter.setDoParameterFinishState(FinishState.SESSION_TIME_OUT);
        callParameter.setHasStartMaxCallController(true);
        leftLeg.sendByeOr487();
        rightLeg.sendByeOrCancel();
    }
}
