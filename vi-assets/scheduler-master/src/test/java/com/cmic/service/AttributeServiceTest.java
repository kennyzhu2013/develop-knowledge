package com.cmic.service;

import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=test")
public class AttributeServiceTest {
    @Resource
    private AttributeService attributeService;
    @MockBean
    private CollectorRegistry collectorRegistry;
    @Autowired
    private RedisService redisService;

    @Test
    public void test() {
        Map<String, Object> newAttrs = Map.of("riskLevel", "1");
        Map<String, Object> existingAttrs = Map.of();
        Map<String, Object> updateAttrs = attributeService.getAttributeUpdate(newAttrs, existingAttrs);
        log.info(updateAttrs.toString());
        redisService.setAttributeJobCallback(123456, updateAttrs);
        log.info(redisService.getAttributeJobCallback(123456).toString());
    }

    @Test
    public void test2() {
        Map<String, Object> newAttrs = Map.of("riskLevel", "2");
        Map<String, Object> existingAttrs = Map.of("riskLevel", "3");
        Map<String, Object> updateAttrs = attributeService.getAttributeUpdate(newAttrs, existingAttrs);
        log.info(updateAttrs.toString());
        redisService.setAttributeJobCallback(123456, updateAttrs);
        log.info(redisService.getAttributeJobCallback(123456).toString());
    }

    @Test
    public void test3() {
        Map<String, Object> newAttrs = Map.of("riskLevel", "3");
        Map<String, Object> existingAttrs = Map.of("riskLevel", "2");
        Map<String, Object> updateAttrs = attributeService.getAttributeUpdate(newAttrs, existingAttrs);
        log.info(updateAttrs.toString());
        redisService.setAttributeJobCallback(123456, updateAttrs);
        log.info(redisService.getAttributeJobCallback(123456).toString());
    }
}