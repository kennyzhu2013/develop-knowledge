package com.cmic.service;


import com.cmic.util.LRUMap;
import gov.nist.javax.sip.message.SIPRequest;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;

public class PromExporterService {
    private static final Logger logger = LoggerFactory.getLogger(PromExporterService.class);
    private static PromExporterService instance = new PromExporterService();
    private  final Counter inviteArrive = Counter.build().name("as_invite_arrive_total").help("as_invite_arrive").register();
    private  final Counter inviteRetran = Counter.build().name("as_invite_retran_total").help("as_invite_retran").register();
    /**
     * 根据call-id记录相同invite信令到达次数, 缓冲2分钟 120 * 100 CPS = 12000
     **/
    private LRUMap<String, Integer> inviteRecord = new LRUMap<String, Integer>(12000);
    private  final Counter inviteRetran1 = Counter.build().name("as_invite_retran1_total").help("as_invite_retran1").register();
    private  final Counter inviteRetran2 = Counter.build().name("as_invite_retran2_total").help("as_invite_retran2").register();
    private  final Counter inviteRetran3 = Counter.build().name("as_invite_retran3_total").help("as_invite_retran3").register();
    private  final Gauge controllerAliveNum = Gauge.build().name("as_controller_alive_num").help("as_controller_alive_num").register();
    private final Counter recordingCallRetry1 = Counter.build().name("recording_call_retry1").help("recording_call_retry1").labelNames("method").register();
    private final Counter recordingCallRetry2 = Counter.build().name("recording_call_retry2").help("recording_call_retry2").labelNames("method").register();
    private final Counter recordingCallRetry3 = Counter.build().name("recording_call_retry3").help("recording_call_retry3").labelNames("method").register();


    public static PromExporterService getInstance() {
        return instance;
    }

    /**
     * 监控Invite
     **/
    public void recordInvite(RequestEvent requestEvent) {
        inviteArrive.inc();

        String callId = ((SIPRequest) requestEvent.getRequest()).getCallId().getCallId();
        if (inviteRecord.containsKey(callId)) {
            inviteRetran.inc();
            int num = inviteRecord.getCache(callId);
            inviteRecord.putCache(callId, num + 1);
            if (1 == num) {
                inviteRetran1.inc();
            } else if (2 == num) {
                inviteRetran2.inc();
            } else if (3 == num) {
                inviteRetran3.inc();
            }
        } else {
            inviteRecord.putCache(callId, 1);
        }
    }


    public void refreshControllerNum(int count)
    {
        controllerAliveNum.set(count);
    }


    public void incRecordingCallRetry(String method, int count) {
        if (count == 0) {
            recordingCallRetry1.labels(method).inc();
        } else if (count == 1) {
            recordingCallRetry2.labels(method).inc();
        } else {
            recordingCallRetry3.labels(method).inc();
        }
    }
}
