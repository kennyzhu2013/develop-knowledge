package com.cmic.rocketmq;


import com.alibaba.fastjson2.JSON;
import com.cmic.dao.BillDao;
import com.cmic.metrics.BillMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.service.BillCommonFun;
import com.cmic.service.BillService;
import com.cmic.utils.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumerBuilder;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.apache.rocketmq.client.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "GID_VI_ONE_BILLCENTER_BILL_PROCESS",
        topic = "TID_VI_ONE_BILLCENTER_BILL_PROCESS_BILLCENTER_DELAY",
        tag = "*"
)
public class BillDelayReceiver implements RocketMQListener, RocketMQPushConsumerLifecycleListener {

    @Autowired
    private BillCommonFun billCommonFun;
    @Autowired
    private BillDao billDao;


    @Autowired
    private BillMetrics billMetrics;

    @Autowired
    private Parameters parameters;

    private PushConsumerBuilder consumerBuilder;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        ByteBuffer byteBuffer = messageView.getBody();
        byte[] body = new byte[byteBuffer.remaining()];
        byteBuffer.get(body);
        String paramsJson = new String(body, StandardCharsets.UTF_8);

        Map<String, Object> params = JSON.toJavaObject(JSON.parseObject(paramsJson), Map.class);

        Object callIdObj = params.get("billId");
        String callId = (callIdObj != null) ? callIdObj.toString() : "unknown";

        log.info("[{}] BillDelayReceiver mq msgID: {}, message: {}", callId, messageView.getMessageId(), paramsJson);

        try {
            if (StringUtils.isNumeric(callId)) {
                Bill bill = billDao.findBillNewById(callId);
                if (bill == null) {
                    log.info("[{}] BillDelayReceiver can not find bill from db", callId);
                    return ConsumeResult.SUCCESS;
                }
                billCommonFun.createHistory(bill);
            }
        } catch (Exception e) {
            // 超时监控逻辑
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                // getConnectionTimeoutCounter.inc();
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("[{}] BillDelayReceiver handle bill msgID: {} fail", callId, messageView.getMessageId(), e);
        }

        return ConsumeResult.SUCCESS;
    }

    @Override
    public void prepareStart(PushConsumerBuilder consumerBuilder) {
        log.info("billCenter prepareStart,consumerBuilder={}, bill consumptionThreadCount={}", consumerBuilder, parameters.billConsumerCount);
        consumerBuilder.setConsumptionThreadCount(parameters.billConsumerCount);
        this.consumerBuilder = consumerBuilder;
    }
}
