package com.cmic.service;


import com.cmic.dao.BillDao;
import com.cmic.pojo.entity.Bill;
import com.cmic.rocketmq.RecordDelaySender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BillCommonFun {

    @Autowired
    private BillDao billDao;


    public void createHistory(Bill bill) {
        int row = billDao.createBillHistory(bill);
        if (row == 1) {
            billDao.deleteBillNew(bill);
        } else {
            log.error("[{}] - {}", bill.getId(), "create bill_history data failed!");
        }
    }

}