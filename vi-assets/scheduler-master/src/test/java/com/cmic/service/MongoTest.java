package com.cmic.service;

import com.cmic.dao.ISubTaskDao;
import com.cmic.pojo.entity.SubTask;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import javax.annotation.Resource;
import java.util.Optional;

@Slf4j
@SpringBootTest
public class MongoTest {
    @Resource
    private ISubTaskDao iSubTaskDao;
    @MockBean
    private CollectorRegistry collectorRegistry;

    @Test
    public void save(){

        Optional<SubTask> byId = iSubTaskDao.findById(1820790794341192783L);
        SubTask result = byId.get();
        log.info(result.toString());
    }
}
