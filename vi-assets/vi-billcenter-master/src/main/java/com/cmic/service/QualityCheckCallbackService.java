package com.cmic.service;

import com.cmic.dao.BillDao;
import com.cmic.pojo.entity.Bill;
import com.cmic.rocketmq.InspectionResultSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class QualityCheckCallbackService {

    @Autowired
    private InspectionResultSender inspectionResultSender;
    @Autowired
    private BillDao billDao;

    public void processSubtackCallback(Map<String, Object> params) {
        try{
            Bill bill = billDao.findBillNewById(params.get("listId").toString());
            if (bill == null) {
                log.error("[{}]- can not find bill by listId", params.get("listId"));
                return;
            }
            if (params.get("status") != null && "1".equals(params.get("status").toString())) {
                bill.setQualityInspectionStatus(2);
            } else {
                bill.setQualityInspectionStatus(-2);
            }
            billDao.updateBillNewInspectionStatus(bill);
            inspectionResultSender.send(params);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
