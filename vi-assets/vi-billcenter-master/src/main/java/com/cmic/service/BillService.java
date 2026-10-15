package com.cmic.service;

import com.cmic.dao.BillDao;
import com.cmic.pojo.entity.Bill;
import com.cmic.metrics.BillMetrics;
import com.cmic.rocketmq.BillDelaySender;
import com.cmic.rocketmq.BillSender;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
@Slf4j
public class BillService {

    @Autowired
    private BillDao billDao;
    @Autowired
    private BillDelaySender billDelaySender;
    @Autowired
    private BillCommonFun billCommonFun;


    @Autowired
    private BillMetrics billMetrics;



    public void checkBill(Map<String, Object> params) {
        String id = params.get("billId").toString();
        //通话接通，等待合音完成进行质检，无需额外操作
        if (params.get("isPickUP") != null && Boolean.valueOf(params.get("isPickUP").toString())) {
            log.info("[{}] - this call is pickup, no need more operation", id);
            return;
        }
        //billRequestCounter.inc();
        billMetrics.billRequestCounterInc();

        Bill bill = null;
        try {
            bill = billDao.findBillNewById(id);

        } catch (Exception e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("wait millis")) {
                // GetConnectionTimeoutCounter.inc();
                billMetrics.getConnectionTimeoutCounterInc();
            }
            log.error("[" + id + "] - database opearation error : ", e);
            return;
        }
        if (bill == null) {
            log.error("[{}] - {}", id,  "bill can not found from database");
            return;
        }

        try {
            //as异步更新数据库，收到话单推送消息时，可能还没更新数据库，需要发送到延时队列等待一下
            if (bill.getFinishState() == 0) {
                billDelaySender.sendDelayBill(id, params);
            } else {
                //没有接通，无需质检，直接进行移表操作
                billCommonFun.createHistory(bill);
            }
        } catch (Exception e) {
            log.error("[" + id + "] - process bill that is not pickup error ", e);
        }
    }


}
