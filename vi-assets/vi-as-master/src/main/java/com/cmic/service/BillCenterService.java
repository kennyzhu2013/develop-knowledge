package com.cmic.service;

import com.cmic.config.AppConfig;
import com.cmic.dao.BillDao;
import lombok.Getter;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;

public class BillCenterService {
    @Getter
    private static final BillCenterService instance = new BillCenterService();
    private static final Logger logger = LoggerFactory.getLogger(BillCenterService.class);

    public void sendMsgToBillCenter(String billID, int customerId, boolean isPickUP, int dbId) {
        JSONObject params = new JSONObject();
        params.put("billId", billID);
        params.put("isPickUP", isPickUP);
        params.put("dbId", dbId);
        processMsgSending(params, customerId);
    }

    private void processMsgSending(JSONObject params, int customerId) {
        XmodePromExporterService.getInstance().sendBillCenter("total");
        boolean needInsertBillFail = true;
        String billId = params.containsKey("billId") ? params.getString("billId") : "0";
        try {
            BillRocketMQService.getInstance().sendBillPushMsg(params.toString(), billId);
            needInsertBillFail = false;
        } catch (Exception e) {
            logger.error("[{}] sendMsgToBillCenter processMsgSending error", billId, e);
        }
        if (needInsertBillFail) {
            XmodePromExporterService.getInstance().sendBillCenter("fail");
            BillDao.insertBillFial(billId, customerId, AppConfig.getLocationId(), -1, 1, new Timestamp(System.currentTimeMillis()));
        } else {
            XmodePromExporterService.getInstance().sendBillCenter("success");
        }
    }

}
