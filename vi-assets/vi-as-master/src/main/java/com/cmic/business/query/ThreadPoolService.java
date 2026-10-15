package com.cmic.business.query;


import com.cmic.service.XmodePromExporterService;
import com.cmic.util.DtmfLogger;
import gov.nist.core.NamingThreadFactory;

import java.util.concurrent.*;

public class ThreadPoolService {
    //处理策略运营平台查询
    private volatile static ThreadPoolExecutor AUDIT_QUERY_POOL;
    private volatile static ThreadPoolExecutor AUDIT_RESPONSE_POOL;
    //处理阿里转接查询及转接查询回调的线程池
    private volatile static ThreadPoolExecutor ALI_QUERY_POOL;
    private volatile static ThreadPoolExecutor DEFAULT_QUERY_POOL;
    private volatile static ThreadPoolExecutor INDUSTRIAL_ID_QUERY_SEND_POOL;
    private volatile static ThreadPoolExecutor GET_REDIS_IDENTITY_TAG_POOL;

    public static ThreadLocal<Boolean> queueFull = new ThreadLocal<>();

    public static ThreadLocal<String> billId = new ThreadLocal<>();

    public static int getAuditQueryQueueSize() {
        if (AUDIT_QUERY_POOL == null)
            return 0;
        return AUDIT_QUERY_POOL.getQueue().size();
    }

    public static int getIndustrialIdQueryQueueSize() {
        if (INDUSTRIAL_ID_QUERY_SEND_POOL == null)
            return 0;
        return INDUSTRIAL_ID_QUERY_SEND_POOL.getQueue().size();
    }

    public static int getAuditResponseQueueSize() {
        if (AUDIT_RESPONSE_POOL == null)
            return 0;
        return AUDIT_RESPONSE_POOL.getQueue().size();
    }

    public static ThreadPoolExecutor getAuditQueryExecutorInstance() {
        if (AUDIT_QUERY_POOL == null) {
            AUDIT_QUERY_POOL = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(50000), new NamingThreadFactory("AUDIT_QUERY_POOL"));
            AUDIT_QUERY_POOL.setRejectedExecutionHandler((r, executor) -> {
                queueFull.set(true);
                XmodePromExporterService.getInstance().incStrategyAuditFullCount();
                XmodePromExporterService.getInstance().incASDowngrade();
                DtmfLogger.error(billId.get(), "AUDIT_QUERY_POOL queue full");
            });
        }
        return AUDIT_QUERY_POOL;
    }


    public static ThreadPoolExecutor getAuditResponseExecutorInstance() {
        if (AUDIT_RESPONSE_POOL == null) {
            AUDIT_RESPONSE_POOL = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(100000), new NamingThreadFactory("AUDIT_QUERY_RESP_POOL"));
            AUDIT_RESPONSE_POOL.setRejectedExecutionHandler((r, executor) -> {
                XmodePromExporterService.getInstance().incStrategyAuditResponseFullCount();
                DtmfLogger.error(billId.get(), "AUDIT_RESPONSE_POOL queue full");
            });
        }
        return AUDIT_RESPONSE_POOL;
    }

    public static ThreadPoolExecutor getAliQueryExecutorInstance() {
        if (ALI_QUERY_POOL == null) {
            ALI_QUERY_POOL = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(1000), new NamingThreadFactory("ALI_QUERY_POOL"));
        }
        return ALI_QUERY_POOL;
    }

    public static ThreadPoolExecutor getDefaultQueryExecutorInstance() {
        if (DEFAULT_QUERY_POOL == null) {
            DEFAULT_QUERY_POOL = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(10000), new NamingThreadFactory("DefaultQuery"));
        }
        return DEFAULT_QUERY_POOL;
    }

    public static ThreadPoolExecutor getIndustrialIdQuerySendPool() {
        if (INDUSTRIAL_ID_QUERY_SEND_POOL == null) {
            INDUSTRIAL_ID_QUERY_SEND_POOL = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(10000), new NamingThreadFactory("IndustrialIdQuerySend"));
        }
        return INDUSTRIAL_ID_QUERY_SEND_POOL;
    }

    public static ThreadPoolExecutor getRedisIdentityTagPool() {
        if (GET_REDIS_IDENTITY_TAG_POOL == null) {
            synchronized (ThreadPoolService.class){
                if (GET_REDIS_IDENTITY_TAG_POOL == null){
                    GET_REDIS_IDENTITY_TAG_POOL = new ThreadPoolExecutor(10, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(1000), new NamingThreadFactory("IdentitySMSSend"));
                    GET_REDIS_IDENTITY_TAG_POOL.setRejectedExecutionHandler((r, executor) -> DtmfLogger.error(billId.get(), "GET_REDIS_IDENTITY_TAG_POOL queue full"));
                }
            }
        }
        return GET_REDIS_IDENTITY_TAG_POOL;
    }

}
