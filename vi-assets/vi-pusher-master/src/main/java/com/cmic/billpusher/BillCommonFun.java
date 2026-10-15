package com.cmic.billpusher;


import com.cmic.pojo.entity.Bill;
import com.cmic.rocketmq.RecordDelaySender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BillCommonFun {

    private RecordDelaySender recordDelaySender;


    public void checkRecrodDelay(Bill bill) {
        if ((bill.getFinishState() == 1 || bill.getFinishState() == 2) && bill.getCallDurationS() < 3 && bill.getIsRecordOk() == 0) {
            recordDelaySender.sendBillDelay(bill, 60);
        } else if (bill.getCallForwardTime() != null) {
            if (bill.getHandleEndTime().getTime() - bill.getCallForwardTime().getTime() < 3000) {
                recordDelaySender.sendBillDelay(bill, 60);
            }
        }
    }


}