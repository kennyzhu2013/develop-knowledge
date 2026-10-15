package com.cmic.pojo;

import lombok.Data;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;

/**
 * 策略运营平台的返回对象
 * <p>
 * Author: luohuanfa
 * Date: 2024/4/19
 */
@Data
public class AuditResponseEntity {
    private String opType;
    private String callNoPlayCode;
    private String calledPlayCode;
    private String calledDisplayNo;
    private String allowCalleeForwarding;
    private String appendReason;

    JSONArray allowForwardedNoList;

    public AuditResponseEntity(String opType, String callNoPlayCode, String calledPlayCode, String calledDisplayNo, String allowCalleeForwarding, String appendReason) {
        this.opType = opType;
        this.callNoPlayCode = callNoPlayCode;
        this.calledPlayCode = calledPlayCode;
        this.calledDisplayNo = calledDisplayNo;
        this.allowCalleeForwarding = allowCalleeForwarding;
        this.appendReason = appendReason;
    }

    public AuditResponseEntity(String opType, String callNoPlayCode, String calledPlayCode, String appendReason) {
        this.opType = opType;
        this.callNoPlayCode = callNoPlayCode;
        this.calledPlayCode = calledPlayCode;
        this.calledDisplayNo = calledDisplayNo;
        this.allowCalleeForwarding = allowCalleeForwarding;
        this.appendReason = appendReason;
    }

    public boolean passButChangeResponse() {
        return "1".equals(opType) && StringUtils.isBlank(appendReason) &&
                (StringUtils.isNotBlank(callNoPlayCode)
                        || StringUtils.isNotBlank(calledPlayCode)
                        || StringUtils.isNotBlank(calledDisplayNo)
                        || StringUtils.isNotBlank(allowCalleeForwarding)
                        || allowForwardedNoList != null);
    }
}
