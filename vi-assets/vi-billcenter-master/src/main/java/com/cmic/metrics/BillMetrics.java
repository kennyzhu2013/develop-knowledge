package com.cmic.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BillMetrics {

    // 话单请求计数器
    private final Counter billRequestCounter;
    // 话单生成计数器
    private final Counter billCreateCounter;
    // ASR请求计数器
    private final Counter asrCounter;
    // 录音推送计数器
    private final Counter recordingCounter;
    // 录音错误计数器
    private final Counter recordErrorCounter;
    // ASR错误计数器
    private final Counter asrErrorCounter;
    // 数据库连接超时计数器
    private final Counter getConnectionTimeoutCounter;
    // 质检请求计数器
    private final Counter inspectionCreateRequestCounter;
    // 质检请求成功计数器
    private final Counter inspectionCreateSuccessRequestCounter;
    // 质检请求失败计数器
    private final Counter inspectionCreateFailureRequestCounter;
    // 质检回调成功计数器
    private final Counter inspectionCallbackSuccessCounter;
    // 质检回调失败计数器
    private final Counter inspectionCallbackFailureCounter;
    // 质检结果发送计数器
    private final Counter inspectionResultSendCounter;

    // 通过构造函数注入MeterRegistry
    public BillMetrics(MeterRegistry meterRegistry) {
        // 初始化所有计数器
        this.billRequestCounter = Counter.builder("vi.bill.request.total")
                .description("话单请求总数")
                .baseUnit("times")
                .tag("bill_type", "request")
                .register(meterRegistry);

        this.billCreateCounter = Counter.builder("vi.bill.create.total")
                .description("话单生成总数")
                .baseUnit("times")
                .tag("bill_type", "create")
                .register(meterRegistry);

        this.asrCounter = Counter.builder("vi.asr.request.total")
                .description("需进行ASR话单总数")
                .baseUnit("times")
                .tag("type", "asr")
                .register(meterRegistry);

        this.recordingCounter = Counter.builder("vi.record.total")
                .description("推送录音总数")
                .baseUnit("times")
                .tag("type", "recording")
                .register(meterRegistry);

        this.recordErrorCounter = Counter.builder("vi.record.error.total")
                .description("录音合成失败总数")
                .baseUnit("times")
                .tag("error_type", "recording")
                .register(meterRegistry);

        this.asrErrorCounter = Counter.builder("vi.asr.error.total")
                .description("语音转文字失败总数")
                .baseUnit("times")
                .tag("error_type", "asr")
                .register(meterRegistry);

        this.getConnectionTimeoutCounter = Counter.builder("vi.get.connection.timeout.total")
                .description("获取数据库连接失败数量")
                .baseUnit("times")
                .tag("error_type", "db_connection")
                .register(meterRegistry);

        this.inspectionCreateRequestCounter = Counter.builder("vi.inspection.create.total")
                .description("质检请求总数")
                .baseUnit("times")
                .tag("type", "inspection_create")
                .register(meterRegistry);

        this.inspectionCreateSuccessRequestCounter = Counter.builder("vi.inspection.success.total")
                .description("质检请求成功总数")
                .baseUnit("times")
                .tag("type", "inspection_success")
                .register(meterRegistry);

        this.inspectionCreateFailureRequestCounter = Counter.builder("vi.inspection.failure.total")
                .description("质检请求失败总数")
                .baseUnit("times")
                .tag("type", "inspection_failure")
                .register(meterRegistry);

        this.inspectionCallbackSuccessCounter = Counter.builder("vi.inspection.callback.success.total")
                .description("质检回调成功总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.inspectionCallbackFailureCounter = Counter.builder("vi.inspection.callback.failure.total")
                .description("质检回调失败总数")
                .baseUnit("times")
                .register(meterRegistry);

        this.inspectionResultSendCounter = Counter.builder("vi.inspection.result.send.total")
                .description("质检结果发送总数")
                .baseUnit("times")
                .register(meterRegistry);
    }

    // 话单请求计数方法
    public void billRequestCounterInc() {
        this.billRequestCounter.increment();
    }

    // 话单生成计数方法
    public void billCreateCounterInc() {
        this.billCreateCounter.increment();
    }

    // ASR请求计数方法
    public void asrCounterInc() {
        this.asrCounter.increment();
    }

    // 录音推送计数方法
    public void recordingCounterInc() {
        this.recordingCounter.increment();
    }

    // 录音错误计数方法
    public void recordErrorCounterInc() {
        this.recordErrorCounter.increment();
    }

    // ASR错误计数方法
    public void asrErrorCounterInc() {
        this.asrErrorCounter.increment();
    }

    // 数据库连接超时计数方法
    public void getConnectionTimeoutCounterInc() {
        this.getConnectionTimeoutCounter.increment();
    }

    // 质检请求计数方法
    public void inspectionCreateRequestCounterInc() {
        this.inspectionCreateRequestCounter.increment();
    }

    // 质检请求成功计数方法
    public void inspectionRequestSuccessCounterInc() {
        this.inspectionCreateSuccessRequestCounter.increment();
    }

    // 质检请求失败计数方法
    public void inspectionRequestFailureCounterInc() {
        this.inspectionCreateFailureRequestCounter.increment();
    }

    // 质检回调成功计数方法
    public void inspectionCallbackSuccessCounterInc() {
        this.inspectionCallbackSuccessCounter.increment();
    }

    // 质检回调失败计数方法
    public void inspectionCallbackFailureCounterInc() {
        this.inspectionCallbackFailureCounter.increment();
    }

    // 质检结果发送计数方法
    public void inspectionResultSendCounterInc() {
        this.inspectionResultSendCounter.increment();
    }

}