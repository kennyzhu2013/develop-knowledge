package com.cmic.service;

import com.cmic.dao.BillDao;
import com.cmic.metrics.BillMetrics;
import com.cmic.pojo.entity.Bill;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class QualityCheckRecreateService {

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    private static final ExecutorService BILL_PROCESSOR =
            Executors.newFixedThreadPool(8);

    // 使用 AtomicBoolean 保证原子性
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private BillDao billDao;
    @Autowired
    private QualityCheckService qualityCheckService;
    @Autowired
    private BillMetrics billMetrics;

    public boolean recreatingTask(Map<String, String> params) {
        // 尝试原子性置为 true，若已为 true 则直接返回
        if (!running.compareAndSet(false, true)) {
            log.warn("recreating task is already running, skip this request");
            return false;
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            long startTimestamp = System.currentTimeMillis();
            try {
                failBillReCreate(params);
            } catch (Exception e) {
                log.error("recreating task failed with exception", e);
            } finally {
                running.set(false); // 确保重置
                log.info("recreating task finished, cost time: {} ms", System.currentTimeMillis() - startTimestamp);
            } // <--- 关键点：传入这个线程池

            // 可选：加个异常日志，防止内部异常静默消失

            }, EXECUTOR);
        future.exceptionally(ex -> {
            log.error("异步任务执行异常", ex);
            return null;
        });
        return true;
    }

    public void failBillReCreate(Map<String, String> params) {

        String startDateStr = Optional.ofNullable(params.get("startTime"))
                .orElse("2026-07-02 00:00:00");
        String endDateStr = Optional.ofNullable(params.get("endTime"))
                .orElse("2026-07-05 00:00:00");
        String companyId = Optional.ofNullable(params.get("companyId"))
                .orElse("");
        log.info("recreating quality check task start, companyId: {} , time between：[{} - {}]", companyId, startDateStr, endDateStr);

        int limit = 1000;
        String lastId = "";
        int totalProcessed = 0;
        int totalSuccess = 0;
        int totalFailure = 0;

        while (true) {
            try {
                List<Bill> remainBillList = billDao.getCreateTaskFailBillNew(startDateStr, endDateStr, limit, companyId, lastId);
                if (remainBillList == null || remainBillList.isEmpty()) {
                    break;
                }

                AtomicInteger batchSuccess = new AtomicInteger(0);
                AtomicInteger batchFailure = new AtomicInteger(0);
                AtomicInteger batchProcessed = new AtomicInteger(0);

                List<CompletableFuture<Void>> futures = new ArrayList<>(remainBillList.size());

                // 批次处理
                for (Bill bill : remainBillList) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            billMetrics.inspectionCreateRequestCounterInc();
                            boolean checkRes = qualityCheckService.checkQuality(bill);
                            if (checkRes) {
                                bill.setQualityInspectionStatus(1);
                                billDao.updateBillNewInspectionStatus(bill);
                                billMetrics.inspectionRequestSuccessCounterInc();
                                batchSuccess.incrementAndGet();
                            } else {
                                billMetrics.inspectionRequestFailureCounterInc();
                                batchFailure.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("[{}] - recreate quality check task error: {}", bill.getId(), e);
                            // 不计入成功/失败，但可单独记录
                        } finally {
                            batchProcessed.incrementAndGet();
                        }
                    }, BILL_PROCESSOR);  // 使用子线程池
                    futures.add(future);
                }

                // 等待当前批次所有子任务完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                totalProcessed += batchProcessed.get();
                totalSuccess += batchSuccess.get();
                totalFailure += batchFailure.get();

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

        log.info("recreating quality check finished，total： {} ，success： {} ，failure： {}", totalProcessed, totalSuccess, totalFailure);
    }

    @PreDestroy
    public void destroy() {
        shutdownExecutor(EXECUTOR, "EXECUTOR");
        shutdownExecutor(BILL_PROCESSOR, "BILL_PROCESSOR");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("{} shutdown completed", name);
    }
}
