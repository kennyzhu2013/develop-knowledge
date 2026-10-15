package com.cmic.service;

import com.cmic.config.RocketMQConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.SessionCredentialsProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.producer.ProducerBuilder;

@Slf4j
public class EventRocketMQService extends BaseRocketMQService {
    @Getter
    private static final EventRocketMQService instance = new EventRocketMQService();

    private static final String TOPIC = "TID_VI_AS_EVENT_PUSH_PUSHER_NORMAL";

    @Override
    protected void customInit(RocketMQConfig rocketMQConfig) throws Exception {
        serviceName = "event";
        String ak = rocketMQConfig.getAk();
        String sk = rocketMQConfig.getSk();
        String endpoints = rocketMQConfig.getEndpoints();

        log.info("EventRocketMQService init endpoints:{}", endpoints);

        final ClientServiceProvider provider = ClientServiceProvider.loadService();
        SessionCredentialsProvider sessionCredentialsProvider =
                new StaticSessionCredentialsProvider(ak, sk);
        ClientConfiguration clientConfiguration =
                ClientConfiguration.newBuilder().setEndpoints(endpoints).setCredentialProvider(sessionCredentialsProvider).build();
        final ProducerBuilder builder = provider.newProducerBuilder().setClientConfiguration(clientConfiguration);
        producer = builder.build();
    }

    public void sendBillEventMsg(String msg, String billId) {
        basicPublish(TOPIC, msg, billId, "BillEventMsg");
    }
}
