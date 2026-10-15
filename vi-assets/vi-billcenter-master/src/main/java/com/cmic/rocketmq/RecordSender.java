package com.cmic.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.cmic.pojo.entity.Bill;
import com.cmic.metrics.BillMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RecordSender {
    @Autowired
    private RocketMQClientTemplate rocketMQClientTemplate;

    // @Resource(name = "RecordingCounter")
    // private Counter recordingCounter;
    @Autowired
    private BillMetrics billMetrics;


    public static final int X_CAINIAO = 18;
    public static final int EXT_ALI = 15;

    public void send(Bill bill, String fileName, String msId, boolean separateEarly, boolean fullRecord, String bindId) {

        Map<String, Object> params = new HashMap<>();
        params.put("billId", bill.getId());
        params.put("fileName", fileName);
        params.put("msId", msId);
        params.put("separateEarly", separateEarly);
        params.put("fullRecord", fullRecord);
        params.put("bindId", bindId);
        String mapJson = JSON.toJSONString(params);
        byte[] messageBody = mapJson.getBytes(StandardCharsets.UTF_8);

        String destination = "TID_VI_BILLCENTER_RECORD_PUSH_PUSHER_NORMAL";

        org.springframework.messaging.Message<byte[]> recordMessage = MessageBuilder
                .withPayload(messageBody)
                .build();
        rocketMQClientTemplate.send(destination, recordMessage);
        log.info("[{}] - Send a recording to RocketMQ: {}  \nmessage:{}",
                bill.getId(), destination, mapJson);
        // recordingCounter.inc();
        billMetrics.recordingCounterInc();
    }
}
