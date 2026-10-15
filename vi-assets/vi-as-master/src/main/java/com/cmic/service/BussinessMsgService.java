package com.cmic.service;

import com.alibaba.fastjson.JSON;
import com.cmic.business.ViConf;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class BussinessMsgService {

    public Logger logger = LoggerFactory.getLogger(getClass());
    private static BussinessMsgService instance = new BussinessMsgService();

    public static BussinessMsgService getInstance() {
        return instance;
    }

    public void sendBillEventMsg(Map<String, Object> map, ViConf viConf, String billId) {
        map.put("userId", viConf.getCompany().getId());
        map.put("appId", viConf.getCustomer().getId());
        EventRocketMQService.getInstance().sendBillEventMsg(JSON.toJSONString(map), billId);
    }

    public void sendBillGZHEventMsgWithTac8(JSONObject jsonObject, ViConf viConf, int calleeSystem, String calleeimei, String billId) {
        jsonObject.put("companyId", viConf.getCompany().getId());
        jsonObject.put("customerId", viConf.getCustomer().getId());
        jsonObject.put("calleeSystem", calleeSystem);
        if (StringUtils.isNotBlank(calleeimei)) {
            jsonObject.put("calleeimei", calleeimei);
        }
        EventRocketMQService.getInstance().sendBillEventMsg(jsonObject.toString(), billId);
    }
}
