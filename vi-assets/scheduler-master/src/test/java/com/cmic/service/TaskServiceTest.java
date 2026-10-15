package com.cmic.service;

import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=test")
public class TaskServiceTest {
    @MockBean
    private CollectorRegistry collectorRegistry;

    @Test
    public void test1(){

    }
}
