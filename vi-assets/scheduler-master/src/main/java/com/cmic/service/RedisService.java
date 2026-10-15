package com.cmic.service;

import com.cmic.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisService {

    private static final int PROCESSED_URL_DURATION = 7;

    private static final String PROCESSED_URL_PREFIX = "processedUrl:";

    @Autowired
    private RedisUtil redisUtil;

    public void setProcessUrl(String url, Integer expire) {
        try {
            redisUtil.set(PROCESSED_URL_PREFIX + url, "1", Objects.nonNull(expire) ? expire : PROCESSED_URL_DURATION, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("redis error:", e);
        }
    }

    public boolean existProcessedUrl(String url) {
        try {
            return redisUtil.get(PROCESSED_URL_PREFIX + url) != null;
        } catch (Exception e) {
            log.error("redis error:", e);
            return false;
        }
    }

    public List<String> filterDataId(List<String> dataIds) {
        List<String> result = new ArrayList<>();
        int batchSize = 1000;

        for (int i = 0; i < dataIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dataIds.size());
            List<String> subList = dataIds.subList(i, end);

            try {
                result.addAll(redisUtil.getValidKeys(subList));
            } catch (Exception e) {
                log.error("redis error:", e);
            }
        }

        return result;
    }

    public void setDataId(String dataId, Integer expire) {
        try {
            redisUtil.set(dataId, "1", Objects.nonNull(expire) ? expire : PROCESSED_URL_DURATION, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("redis error:", e);
        }
    }

    private static final String ATTRIBUTE_JOB_CALLBACK = "attrs_callback";
    public void setAttributeJobCallback(long jobId, Map<String,Object> attributes){
        try {
            redisUtil.hmset(ATTRIBUTE_JOB_CALLBACK+jobId, attributes, 3, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("redis error:", e);
        }
    }

    public Map<String,Object> getAttributeJobCallback(long jobId){
        return redisUtil.hmget(ATTRIBUTE_JOB_CALLBACK+jobId);
    }

    public void deleteAttributeJobCallback(long jobId){
        redisUtil.delKey(ATTRIBUTE_JOB_CALLBACK+jobId);
    }

    private static final String ATTRIBUTE_CALLID = "attrs_callId";
    public void setAttributeCallId(String callId, Map<String,Object> attributes){
        try {
            redisUtil.hmset(ATTRIBUTE_CALLID+callId, attributes, 1, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("redis error:", e);
        }
    }

    public Map<String,Object> getAttributeCallId(String callId){
        return redisUtil.hmget(ATTRIBUTE_CALLID+callId);
    }

    public Object getAttributeCallIdItem(String callId, String item){
        return redisUtil.hget(ATTRIBUTE_CALLID+callId, item);
    }

    public void deleteAttributeCallId(String callId){
        redisUtil.delKey(ATTRIBUTE_CALLID+callId);
    }
}
