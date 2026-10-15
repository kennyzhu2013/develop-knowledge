package com.cmic.business;

import com.cmic.BillNew;
import com.cmic.business.businessparam.ExtensionCtrlParam;
import com.cmic.dao.BizNumAuthDo;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;



public class StandardEvent extends BusinessEvent {

    public Logger logger = LoggerFactory.getLogger(getClass());
    public static final String SUCCESS_CODE = "0000";
    public static ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMddHHmmss"));

    private StandardEvent() {
    }

    public StandardEvent(BillNew billNew, BizNumAuthDo bizNumAuthDo, ExtensionCtrlParam extensionCtrlParam) {
        this.billNew = billNew;
        this.sessionId = billNew.getId();
        this.bizNumAuthDo = bizNumAuthDo;
        this.extensionCtrlParam = extensionCtrlParam;
    }

    public StandardEvent(BillNew billNew, BizNumAuthDo bizNumAuthDo) {
        this(billNew, bizNumAuthDo, null);
    }

    public JSONObject buildMQEvent(String eventName) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("callId", sessionId);
        jsonObject.put("event", eventName);
        // jsonObject.put("bindId", bill.getBindId());
        jsonObject.put("callerNo", billNew.getCallerNumber());
        jsonObject.put("calleeNo", billNew.getCalleeNumber());
        jsonObject.put("secretNo", billNew.getBizNumber());
        jsonObject.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        // jsonObject.put("extId", String.valueOf(viConf.getExtId()));
        jsonObject.put("userId", String.valueOf(bizNumAuthDo.getCompanyId()));
        // jsonObject.put("platformId", String.valueOf(bizNumAuthDo.getPlatformId()));
        jsonObject.put("appId", String.valueOf(bizNumAuthDo.getCustomerId()));
        return jsonObject;
    }


}
