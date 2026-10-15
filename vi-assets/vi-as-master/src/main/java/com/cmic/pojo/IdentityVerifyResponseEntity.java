package com.cmic.pojo;

import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;

/**
 * 策略运营平台的返回对象
 * <p>
 * Author: luohuanfa
 * Date: 2024/4/19
 */
public class IdentityVerifyResponseEntity {
    private String opType;
    private String desc;


    public IdentityVerifyResponseEntity() {

    }


    public String getOpType() {
        return opType;
    }

    public void setOpType(String opType) {
        this.opType = opType;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
