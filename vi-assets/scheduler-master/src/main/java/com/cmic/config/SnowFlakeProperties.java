package com.cmic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sf")
@Data
public class SnowFlakeProperties {
    //起始时间戳
    private long startTimeStamp;
    //机器ID
    private long workID;
    //数据中心ID
    private long dataCenterID;
    //序列号
    private long sequence;
    //数据中心ID移动位数
    private long dataCenterIndex;
    //机器ID移动位数
    private long workIDIndex;
    //时间戳移动位数
    private long timeStampIndex;
    //记录上一次时间戳
    private long lastTimeStamp;
    //序列号掩码
    private long sequenceMask;
    //序列号长度12位
    private long sequenceLength;
}
