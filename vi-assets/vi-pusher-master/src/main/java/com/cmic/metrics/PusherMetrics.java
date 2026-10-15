package com.cmic.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PusherMetrics {

    private final MeterRegistry meterRegistry;
    //计数器
    /**
     * @Bean(name = "BillPushSuccessCounter")
     * public Counter BillPushSuccessCounter() {
     * return Counter.build().name("Bill_Push_Success_Total").help("话单推送成功总数").register(collectorRegistry);
     * }
     * @Bean(name = "BillPushErrorCounter")
     * public Counter BillPushErrorCounter() {
     * return Counter.build().name("Bill_Push_Error_Total").help("话单推送失败总数").register(collectorRegistry);
     * }
     * @Bean(name = "BillRepushSuccessCounter")
     * public Counter BillRepushSuccessCounter() {
     * return Counter.build().name("Bill_Repush_Success_Total").help("话单重新推送成功总数").register(collectorRegistry);
     * }
     * @Bean(name = "BillRepushErrorCounter")
     * public Counter BillRepushErrorCounter() {
     * return Counter.build().name("Bill_Repush_Error_Total").help("话单重新推送失败总数").register(collectorRegistry);
     * }
     * @Bean(name = "RecordPushSuccessCounter")
     * public Counter RecordPushSuccessCounter() {
     * return Counter.build().name("Record_Push_Success_Total").help("通话录音推送成功总数").register(collectorRegistry);
     * }
     * @Bean(name = "RecordPushErrorCounter")
     * public Counter RecordPushErrorCounter() {
     * return Counter.build().name("Record_Push_Error_Total").help("通话录音推送失败总数").register(collectorRegistry);
     * }
     * @Bean(name = "RecordRepushSuccessCounter")
     * public Counter RecordRepushSuccessCounter() {
     * return Counter.build().name("Record_Repush_Success_Total").help("通话录音重新推送成功总数").register(collectorRegistry);
     * }
     * @Bean(name = "RecordRepushErrorCounter")
     * public Counter RecordRepushErrorCounter() {
     * return Counter.build().name("Record_Repush_Error_Total").help("通话录音重新推送失败总数").register(collectorRegistry);
     * }
     * @Bean(name = "UnUseHistoryTableCount")
     * public Gauge UnUseHistoryTableCount() {
     * return Gauge.build().name("UnUse_HistoryTable_Count").help("未使用的bill_history数量").register(collectorRegistry);
     * }
     * @Bean(name = "GetConnectionTimeoutCounter")
     * public Counter GetConnectionTimeoutCounter() {
     * return Counter.build().name("Get_Connection_Timeout_Total").help("获取数据库连接失败数量").register(collectorRegistry);
     * }
     * @Bean(name = "DbUpdateTime")
     * public Histogram DbUpdateTime() {
     * return Histogram.build().name("Db_Update_Time").help("数据库Update操作耗时").register(collectorRegistry);
     * }
     */

    private final Counter getConnectionTimeoutCount;

    private final Counter billPushSuccessCount;

    private final Counter billPushErrorCount;

    private final Counter billRepushSuccessCount;

    private final Counter billRepushErrorCount;

    private final Counter recordPushSuccessCount;

    private final Counter recordPushErrorCount;

    private final Counter recordRepushSuccessCount;

    private final Counter recordRepushErrorCount;

    public AtomicInteger unUseHistoryTableCount = new AtomicInteger(0);

    private final Timer dbUpdateTimer;

    private final Counter inspectionPushSuccessCount;

    private final Counter inspectionPushErrorCount;


    public PusherMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.getConnectionTimeoutCount = Counter.builder("vi.pusher.db.connection.timeout.count")
                .description("获取数据库连接失败总数")
                .baseUnit("times")
                .register(meterRegistry);

        //dcc_merge_receive_count_times_total{merge_type="normal"} 2.0
        this.billPushSuccessCount = Counter.builder("vi.pusher.bill.push.success.count")
                .description("话单推送成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.billPushErrorCount = Counter.builder("vi.pusher.bill.push.error.count")
                .description("话单推送失败总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.billRepushSuccessCount = Counter.builder("vi.pusher.bill.repush.success.count")
                .description("话单重推成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.billRepushErrorCount = Counter.builder("vi.pusher.bill.repush.error.count")
                .description("话单重推失败总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.recordPushSuccessCount = Counter.builder("vi.pusher.record.push.success.count")
                .description("录音推送成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.recordPushErrorCount = Counter.builder("vi.pusher.record.push.error.count")
                .description("录音推送失败总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.recordRepushSuccessCount = Counter.builder("vi.pusher.record.repush.success.count")
                .description("录音重推成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.recordRepushErrorCount = Counter.builder("vi.pusher.record.repush.error.count")
                .description("录音重推失败总数")
                .baseUnit("times")
                .register(meterRegistry);


        Gauge.builder("vi.pusher.unused.historytable.count", unUseHistoryTableCount, AtomicInteger::get)
                .description("未使用历史表计数")
                .baseUnit("个")
                .register(meterRegistry);

        this.dbUpdateTimer = Timer.builder("vi.pusher.db.update.time")
                .description("db更新耗费时间")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .register(meterRegistry);

        this.inspectionPushSuccessCount = Counter.builder("vi.pusher.inspection.push.success.count")
                .description("质检结果推送成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.inspectionPushErrorCount = Counter.builder("vi.pusher.inspection.push.error.count")
                .description("质检结果推送失败总数")
                .baseUnit("times")
                .register(meterRegistry);
    }

    public void getConnectionTimeoutCountInc() {
        getConnectionTimeoutCount.increment();
    }

    public void billPushSuccessCountInc() {
        billPushSuccessCount.increment();
    }

    public void billPushErrorCountInc() {
        billPushErrorCount.increment();
    }

    public void billRePushSuccessCountInc() {
        billRepushSuccessCount.increment();
    }

    public void billRePushErrorCountInc() {
        billRepushErrorCount.increment();
    }

    public void recordPushSuccessCountInc() {
        recordPushSuccessCount.increment();
    }

    public void recordPushErrorCountInc() {
        recordPushErrorCount.increment();
    }

    public void recordRePushSuccessCountInc() {
        recordRepushSuccessCount.increment();
    }

    public void recordRePushErrorCountInc() {
        recordRepushErrorCount.increment();
    }

    public void dbUpdateTime(long duration, TimeUnit unit) {
        dbUpdateTimer.record(duration, unit);
    }

    public void inspectionPushSuccessCountInc() {
        inspectionPushSuccessCount.increment();
    }

    public void inspectionPushErrorCountInc() {
        inspectionPushErrorCount.increment();
    }
}
