package com.cmic.service;

import com.cmic.config.ApplicationProperties;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@Slf4j
@SpringBootTest
public class PropertiesTest {
    @Autowired
    private ApplicationProperties conf;
    @MockBean
    private CollectorRegistry collectorRegistry;


    @Test
    public void test(){
    }
}
