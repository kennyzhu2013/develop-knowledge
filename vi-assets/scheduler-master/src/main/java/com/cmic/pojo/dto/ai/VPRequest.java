package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class VPRequest extends MQBaseDto {
    /**
     * 接口类型
     * 1-1:N
     * 2-1:1
     * 3-注册
     * 4-删除
     * 5-创建库
     * 6-删除库
     */
    private int type;
    private String url;
    private int channel;
    private String audioTagDb;
    private String audioTag;
    private int topN;
}
