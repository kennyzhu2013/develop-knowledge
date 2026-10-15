package com.cmic.service;

import com.cmic.BillNew;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.bizcontroller.ControllerAgent;
import com.cmic.bizcontroller.controllerImpl.NewTwoLegController;
import com.cmic.config.AppConfig;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.dao.DBRetryException;
import com.cmic.keyaffinity.*;
import com.cmic.sip.controller.base.TwoLegController;
import com.cmic.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionHandler;

public class DbAsyncService {

    private static DbAsyncService instance = new DbAsyncService();
    private List<KeyAffinityExecutor> executors = new ArrayList<>();
    private static int dbInstanceCount = 0;
    private static final int currency = 40;
    private static final int QUEUE_LENGTH = 100;

    private static int maxConcurrency = 0;

    private final static long MAX_RETRY_DURATION = 2 * 60 * 1000;  // 2分钟（毫秒）
    private final static long RETRY_INTERVAL = 500;                // 重试间隔500毫秒


    enum DbOper {
        INSERT,
        UPDATE
    }

    public static DbAsyncService getInstance() {
        return instance;
    }

    private RejectedExecutionHandler getRejectHandler() {
        RejectedExecutionHandler abortReject = (r, e) -> {
            extractOriginalTask(r);
        };
        return abortReject;

    }

    public void init() {
        dbInstanceCount = MultiDBService.getBillDBCount();
        for (int i = 0; i < dbInstanceCount; i++) {
            KeyAffinityExecutorBuilder keyAffinityExecutorBuilder = KeyAffinityExecutor.newKeyAffinityExecutor();
            KeyAffinityExecutor executor = keyAffinityExecutorBuilder.usingRandom(false)
                    .parallelism(currency)
                    .executor(KeyAffinityExecutorUtils.executor("KeyAffinity-db-runner-db" + i, QUEUE_LENGTH, getRejectHandler()))
                    .build();
            executors.add(executor);
        }
        for (int i = 0; i < dbInstanceCount; i++) {
            DtmfLogger.info("", "executors {}:{}", i, executors);
        }
        maxConcurrency = (int) Math.floor(QUEUE_LENGTH * DevHelp.getDbOperRejectRatio());
        DtmfLogger.info("", "init db async service success");

    }


    public void refreshMaxConcurrency(float dbOperRejectRatio) {
        maxConcurrency = (int) Math.floor(QUEUE_LENGTH * dbOperRejectRatio);
        DtmfLogger.info("", "refresh db async service maxConcurrency success! New maxConcurrency:{}", maxConcurrency);
    }

    private ThrowableRunnable<?> extractOriginalTask(Runnable wrappedTask) {
        XmodePromExporterService.getInstance().incDbTaskRejectCount();
        try {
            // KeyAffinityExecutor 通常使用 WrappedTask 类包装原始任务
            Class<?> wrappedTaskClass = wrappedTask.getClass();
            // 查找存储原始任务的字段（通常是 "task" 或 "runnable"）
            for (Field field : wrappedTaskClass.getDeclaredFields()) {
                if (ThrowableRunnable.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object task = field.get(wrappedTask);
                    if (task instanceof DbOperTask) {
                        DbOperTask dbOperTask = (DbOperTask) task;
                        DtmfLogger.error(dbOperTask.getBillNew().getId(), "task was rejected by executor. Oper:{}", dbOperTask.getDbOper());
                        // 获取callParameter
                        CallParameter callParameter = null;
                        if (dbOperTask.getController() instanceof NewTwoLegController) {
                            NewTwoLegController newTwoLegController = (NewTwoLegController) dbOperTask.getController();
                            callParameter = newTwoLegController.getCallParameter();
                        }

                        if (dbOperTask.getController() instanceof ControllerAgent) {
                            ControllerAgent controllerAgent = (ControllerAgent) dbOperTask.getController();
                            callParameter = controllerAgent.getCallParameter();
                        }

                        if (callParameter == null) {
                            DtmfLogger.error(dbOperTask.getBillNew().getId(), "invalid dboper type and callParameter is null.Bill:{}", dbOperTask.getBillNew().toString());
                            continue;
                        }
                        // 打印db log
                        if (dbOperTask.getDbOper().equals(DbOper.INSERT)) {
                            DBLog.createBill(dbOperTask.getBillNew(), dbOperTask.getBizNumAuthDo(),
                                    dbOperTask.getExecutorAndDbIndex(), callParameter.getAsrlevel());
                        } else if (dbOperTask.getDbOper().equals(DbOper.UPDATE)) {
                            DBLog.updateBill(dbOperTask.getBillNew(), callParameter,
                                    dbOperTask.getExecutorAndDbIndex());
                        } else {
                            DtmfLogger.error(String.valueOf(dbOperTask.getBillNew().getId()), "invalid dboper type.Bill:{}", dbOperTask.getBillNew().getId());
                        }
                    }
                }
            }

        } catch (Exception ex) {
            DtmfLogger.error("", "Failed to extract original task", ex);
        }
        return null;
    }


    private int getDbExecutorIndex(BillNew billNew) throws Exception {
        try {
            String billId = billNew.getId();
            // 使用话单ID最后一位取模计算db id
            int dividend = Integer.parseInt(billId.substring(billId.length() - 1));
            int dbExecutorIndex = dividend % dbInstanceCount;


            // 尝试第一个索引
            KeyAffinityExecutor firstExecutor = executors.get(dbExecutorIndex);
            if (!isBeyondMaxConcurrency(billNew, firstExecutor.getMinConcurrency(), maxConcurrency)) {
                return dbExecutorIndex;
            }

            // 尝试第二个索引（dividend+1）
            int secondIndex = (dividend + 1) % dbInstanceCount;
            KeyAffinityExecutor secondExecutor = executors.get(secondIndex);
            if (!isBeyondMaxConcurrency(billNew, secondExecutor.getMinConcurrency(), maxConcurrency)) {
                return secondIndex;
            }
            DtmfLogger.warn(billNew.getId(), "getDbExecutorIndex billId:{} beyond maxConcurrency", billId);
            // 两个都超过并发限制
            return -1;
        } catch (Exception e) {
            DtmfLogger.error(billNew.getId(), "getDbExecutorIndex error", e);
            return -1; // 异常时返回null
        }
    }

    private boolean isBeyondMaxConcurrency(BillNew billNew, int minConcurrency, int maxConcurrency) {
        DtmfLogger.info(String.valueOf(billNew.getId()), "isBeyondMaxConcurrency minConcurrency:{},maxConcurrency:{}", minConcurrency, maxConcurrency);
        return minConcurrency >= maxConcurrency;
    }


    public int submitDbInsert(TwoLegController controller, BillNew billNew, BizNumAuthDo bizNumAuthDo, CallParameter callParameter) {
        try {
            if (DevHelp.currentMode == 1) {
                DtmfLogger.warn(String.valueOf(billNew.getId()), "startMode 1, pass db submitDbInsert!");
                DBLog.createBill(billNew, bizNumAuthDo, -1, callParameter.getAsrlevel());
                return -1;
            }
            int executorAndDbIndex = getDbExecutorIndex(billNew);
            DtmfLogger.info(billNew.getId(), "submitDbInsert executorAndDbIndex:{}", executorAndDbIndex);
            callParameter.setDbAndExecutorIndex(executorAndDbIndex);
            if (executorAndDbIndex != -1) {
                executors.get(executorAndDbIndex).executeEx(billNew.getId(), new DbOperTask(billNew, bizNumAuthDo,
                        executorAndDbIndex, controller, DbOper.INSERT, callParameter));
            } else {
                DtmfLogger.warn(String.valueOf(billNew.getId()), "submitDbInsert cannot push task to any db at this moment");
                DBLog.createBill(billNew, bizNumAuthDo, executorAndDbIndex, callParameter.getAsrlevel());
            }
            return executorAndDbIndex;
        } catch (Exception e) {
            DtmfLogger.error(String.valueOf(billNew.getId()), "submit db insert failed. ", e);
            return -1;
        }

    }


    public void submitDbUpdate(NewTwoLegController controller) {
        try {
            int executorAndDbIndex = controller.getCallParameter().getDbAndExecutorIndex();
            DtmfLogger.info(controller.getBillNew().getId(), "submitDbUpdate executorAndDbIndex:{}", executorAndDbIndex);
            if (executorAndDbIndex >= 0 && executorAndDbIndex < dbInstanceCount) {
                executors.get(executorAndDbIndex).executeEx(controller.getBillNew().getId(),
                        new DbOperTask(controller.getBillNew(), controller.getBizNumAuthDo(), executorAndDbIndex, controller,
                                DbOper.UPDATE, controller.getCallParameter()));
            } else {
                if (DevHelp.currentMode == 1) {
                    DtmfLogger.warn(controller.getBillNew().getId(), "startMode 1, pass db submitDbUpdate!");
                } else {
                    DtmfLogger.warn(controller.getBillNew().getId(), "The update will be logged to the file because the insert operation was logged to the file.");
                }
                DBLog.updateBill(controller.getBillNew(), controller.getCallParameter(), executorAndDbIndex);
            }
        } catch (Exception e) {
            DtmfLogger.error(controller.getBillNew().getId(), "submit update bill exception. ", e);
        }

    }


    private boolean dbOperWithRetry(BillNew billNew, BizNumAuthDo bizNumAuthDo, CallParameter callParameter, int executorAndDbIndex,
                                    DbOper dbOper) {
        boolean shouldContinue = true;
        long startTime = System.currentTimeMillis(); // 记录开始时间
        int retryTimes = 0;
        boolean isDbOperSucc = false;
        while (shouldContinue) {
            try {
                int updateCount = 0;
                if (dbOper == DbOper.INSERT) {
                    updateCount = BillService.createBill(billNew, bizNumAuthDo, executorAndDbIndex);
                } else {
                    updateCount = BillService.updateBill(billNew, callParameter, executorAndDbIndex);
                }
                isDbOperSucc = updateCount == 1;
                shouldContinue = false;  // 执行成功时退出循环
            } catch (Exception e) {
                DtmfLogger.error(String.valueOf(billNew.getId()), String.format("%s%s", dbOper, " DB failed"), e);
                if (e instanceof DBRetryException) {
                    // 检查是否超过最大重试时间
                    if (System.currentTimeMillis() - startTime >= MAX_RETRY_DURATION) {
                        // 重试超时（2分钟）记录错误日志
                        DtmfLogger.error(String.valueOf(billNew.getId()), "dbOperWithRetry failed after 2min retry. " +
                                        "Last exception: {} | Bill: {}",
                                e.getMessage(), billNew.toString());
                        shouldContinue = false;
                        isDbOperSucc = false;
                    } else {
                        // 未超时则等待后继续重试
                        try {
                            DtmfLogger.info(billNew.getId(), "sleep {}ms for retry {} DB, retryTimes:{}",
                                    RETRY_INTERVAL, dbOper, retryTimes);
                            Thread.sleep(RETRY_INTERVAL);
                            retryTimes += 1;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            DtmfLogger.warn(String.valueOf(billNew.getId()), "Retry interrupted for bill: {}", billNew.toString());
                            shouldContinue = false;
                            isDbOperSucc = false;
                        }
                    }
                } else {
                    // 其他异常直接记录错误日志并退出
                    DtmfLogger.error(String.valueOf(billNew.getId()), "dbOperWithRetry failed with critical error: {} | Bill: {}",
                            e.getMessage(), billNew.toString());
                    shouldContinue = false;
                    isDbOperSucc = false;
                }
            }
        }
        return isDbOperSucc;
    }

    @Data
    @AllArgsConstructor
    class DbOperTask implements ThrowableRunnable {
        private BillNew billNew;
        private BizNumAuthDo bizNumAuthDo;
        private int executorAndDbIndex;

        private TwoLegController controller;

        private DbOper dbOper;

        private CallParameter callParameter;

        @Override
        public String toString() {
            return "DbOperTask{" +
                    "billNew=" + billNew +
                    ", dbOper=" + dbOper +
                    '}';
        }

        @Override
        public void run() {
            DtmfLogger.info(billNew.getId(), "DbOperTask start run db {}", dbOper);
            if (DbOper.INSERT.equals(dbOper)) {
                if (!dbOperWithRetry(billNew, bizNumAuthDo, null, executorAndDbIndex, DbOper.INSERT)) {
                    DtmfLogger.error(billNew.getId(), "Insert db failed!");
                    DBLog.createBill(billNew, bizNumAuthDo, executorAndDbIndex, callParameter.getAsrlevel());
                } else {
                    //controller.setInsertDbSucc(true);
                    callParameter.setInsertDbSucc(true);
                    DtmfLogger.info(billNew.getId(), "Insert db success!");
                }
            } else if (DbOper.UPDATE.equals(dbOper) && controller instanceof NewTwoLegController) {
                NewTwoLegController newController = (NewTwoLegController) controller;
                if (callParameter.getDbAndExecutorIndex() >= 0 && callParameter.isInsertDbSucc()) {
                    if (newController.getCallParameter() == null) {
                        DtmfLogger.error(billNew.getId(), "Update db failed! CallParameter is null.");
                        DBLog.updateBill(newController.getBillNew(), newController.getCallParameter(), executorAndDbIndex);
                        return;
                    }
                    if (!dbOperWithRetry(newController.getBillNew(), newController.getBizNumAuthDo(),
                            newController.getCallParameter(), executorAndDbIndex, DbOper.UPDATE)) {
                        DBLog.updateBill(newController.getBillNew(), newController.getCallParameter(), executorAndDbIndex);
                    } else {
                        //数据库更新成功，发送MQ消息
                        XmodePromExporterService.getInstance().incBillFinishState(billNew.getFinishState());
                        XmodePromExporterService.getInstance().incBillFinishState(billNew.getFinishState(), billNew.getCustomerId());
                        // 只有呼叫时长大于0并且，不是一个只支持G729的呼叫才通知话单等合音
                        BillCenterService.getInstance().sendMsgToBillCenter(billNew.getId(), billNew.getCustomerId(), billNew.getCallDurationS() > 0 && !callParameter.isOnlySupportG729(), executorAndDbIndex);
                    }
                } else {
                    DtmfLogger.error(String.valueOf(billNew.getId()), "Insert db failed,will write update to log");
                    DBLog.updateBill(newController.getBillNew(), newController.getCallParameter(), executorAndDbIndex);
                }
            } else {
                DtmfLogger.error(String.valueOf(billNew.getId()), "Insert or update db precondition not matched! bill:{}", billNew.toString());
            }
        }
    }


    public static void main(String[] args) {
        //DbAsyncService dbAsyncService = DbAsyncService.getInstance();
        //dbAsyncService.test();
        //boolean isSuccess = dbAsyncService.testRetry();
        //System.out.println(isSuccess);

//        Person person = new Person("vickyvickyvickyvickyvickyvickyvicky",18);
//        person.add("uuuuu");
//        person.add("aaaaa");
//        person.add("bbbbb");
//        long personDeepSize = GraphLayout.parseInstance(person).totalSize();
//        System.out.println(personDeepSize);

    }

    private void test2() {
        try {

            System.out.println(DateUtil.getCurrentDateTimeStr() + ": test2 start");
            int i = 0;
            int b = 5 / i;
        } catch (Exception e) {
            throw new DBRetryException("test2 failed", e);
        }

    }

    private boolean testRetry() {
        boolean shouldContinue = true;
        long startTime = System.currentTimeMillis(); // 记录开始时间
        int retryTimes = 0;
        boolean isDbOperSucc = false;
        while (shouldContinue) {
            try {
                test2();
                shouldContinue = false;  // 执行成功时退出循环
                isDbOperSucc = true;
            } catch (Exception e) {
                //DtmfLogger.error(String.valueOf(bill.getBillId()), String.format("%s%s", dbOper," DB failed"),  e);
                if (e instanceof DBRetryException) {
                    // 检查是否超过最大重试时间
                    if (System.currentTimeMillis() - startTime >= MAX_RETRY_DURATION) {
                        // 重试超时（10分钟）记录错误日志
                        //DtmfLogger.error(String.valueOf(bill.getBillId()),"dbOperWithRetry failed after 10min retry. Last exception: {} | Bill: {}",
                        //        e.getMessage(), bill.toString());
                        System.out.println("Exceed max retry duration");
                        shouldContinue = false;
                        isDbOperSucc = false;
                    } else {
                        // 未超时则等待后继续重试
                        try {
                            //DtmfLogger.info(bill.getBillId(), "sleep {}ms for retry {} DB, retryTimes:{}",
                            //        RETRY_INTERVAL, dbOper,retryTimes);
                            System.out.println("sleep 1 second and retry later");
                            Thread.sleep(RETRY_INTERVAL);
                            retryTimes += 1;
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            System.out.println("get InterruptedException");
                            //DtmfLogger.warn(String.valueOf(bill.getBillId()),"Retry interrupted for bill: {}", bill.toString());
                            shouldContinue = false;
                            isDbOperSucc = false;
                        }
                    }
                } else {
                    // 其他异常直接记录错误日志并退出
                    System.out.println("get other exception");
                    shouldContinue = false;
                    isDbOperSucc = false;
                }
            }
        }
        return isDbOperSucc;
    }

    public void test() {
        RejectedExecutionHandler abortReject = (r, e) -> {
            extractOriginalTask(r);
        };

        KeyAffinityExecutorBuilder keyAffinityExecutorBuilder = KeyAffinityExecutor.newKeyAffinityExecutor();
        KeyAffinityExecutor executor = keyAffinityExecutorBuilder.usingRandom(false)
                .parallelism(1)
                .executor(KeyAffinityExecutorUtils.executor("KeyAffinity-db-runner", 1, abortReject))
                .build();

        BillNew billNew = new BillNew("11113311111");

        executor.executeEx("123456", new DbOperTask(billNew, null, 0, null, DbOper.INSERT, null));

        BillNew billNew2 = new BillNew("22222222222");
        executor.executeEx("45678", new DbOperTask(billNew2, null, 0, null, DbOper.INSERT, null));

        BillNew billNew3 = new BillNew("33333333333333");
        executor.executeEx("45678", new DbOperTask(billNew3, null, 0, null, DbOper.INSERT, null));
        System.out.println("done");
    }

}
