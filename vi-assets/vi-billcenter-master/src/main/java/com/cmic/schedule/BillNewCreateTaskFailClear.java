/*
package com.cmic.schedule;


import com.cmic.dao.BillDao;
import com.cmic.metrics.BillMetrics;
import com.cmic.pojo.entity.Bill;
import com.cmic.service.QualityCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "pusher.billnewclear",havingValue = "true", matchIfMissing = true)
public class BillNewCreateTaskFailClear {
    @Autowired
    private BillDao billDao;
    @Autowired
    private QualityCheckService qualityCheckService;
    @Autowired
    private BillMetrics billMetrics;

    //@Scheduled(cron = "0 0/1 * * * ?")
    //@Scheduled(cron = "0 0 3 * * ?")
    public void FaillBillReCreate() {

        String startDateStr = "2026-07-02 00:00:00";
        String endDateStr = "2026-07-05 00:00:00";
        log.info("recreating quality check task start，time between：[{} - {}]", startDateStr, endDateStr);

        int limit = 1000;
        String lastId = "";
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFailure = 0;

        while (true) {
            try {
                List<Bill> remainBillList = billDao.getCreateTaskFailBillNew(startDateStr, endDateStr, limit, lastId);
                if (remainBillList == null || remainBillList.isEmpty()) {
                    break;

                }

                // 批次处理
                for (Bill bill : remainBillList) {
                    try {
                        billMetrics.inspectionCreateRequestCounterInc();
                        boolean checkRes = qualityCheckService.checkQuality(bill);
                        if (checkRes) {
                            bill.setQualityInspectionStatus(1);
                            billDao.updateBillNewInspectionStatus(bill);
                            billMetrics.inspectionRequestSuccessCounterInc();
                            totalSuccess++;
                        } else {
                            billMetrics.inspectionRequestFailureCounterInc();
                            totalFailure++;
                        }
                    } catch (Exception e) {
                        log.error("[{}] - recreate quality check task error: {}", bill.getId(), e);
                        // 失败不计入成功/失败，但可单独记录
                    }
                    totalProcessed++;
                }

                // 更新游标为当前批次最后一条记录的 ID
                lastId = remainBillList.get(remainBillList.size() - 1).getId();

                if (remainBillList.size() < limit) {
                    break; // 最后一批，退出
                }

            } catch (Exception e) {
                log.error("recreating quality check task error: ", e);
                break;
            }
        }

        log.info("recreating quality check finshed，total {} 条，success {} 条，failure {} 条", totalProcessed, totalSuccess, totalFailure);
    }
}*/
