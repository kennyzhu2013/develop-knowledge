package com.cmic.config;

import com.cmic.config.metrics.DBMetrics;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * JDBC数据源、JdbcTemplate监控代理工具
 * 1. DataSource代理：捕获getConnection()阶段异常（事务开启拿连接、僵尸连接ConnectionIsClosedException）
 * 2. JdbcTemplate代理：捕获SQL执行耗时、查询超时、SQL错误
 */
public class JdbcMonitorHelper {

    /**
     * 包装DataSource，拦截 getConnection()，捕获连接层异常：SQLState、ConnectionIsClosedException
     */
    public static DataSource wrapDataSource(DataSource originDs, String dbName, DBMetrics dbMetrics) {
        ProxyFactory proxyFactory = new ProxyFactory(originDs);
        proxyFactory.addAdvice(new GetConnectionInterceptor(dbName, dbMetrics));
        return (DataSource) proxyFactory.getProxy();
    }

    private static class GetConnectionInterceptor implements MethodInterceptor {
        private final String dbName;
        private final DBMetrics dbMetrics;

        public GetConnectionInterceptor(String dbName, DBMetrics dbMetrics) {
            this.dbName = dbName;
            this.dbMetrics = dbMetrics;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                dbMetrics.databaseErrorInc(dbName, invocation.getMethod().getName(), ex);
                throw ex;
            }
        }
    }

    /**
     * 拦截JdbcTemplate全部public方法
     * 分类异常：query_timeout / connection_exception / sql_error / unknown
     */
    public static JdbcTemplate wrapJdbc(JdbcTemplate target, String dbName, DBMetrics dbMetrics) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new JdbcInvokeInterceptor(dbName, dbMetrics));
        return (JdbcTemplate) proxyFactory.getProxy();
    }

    public static NamedParameterJdbcTemplate wrapNamedJdbc(NamedParameterJdbcTemplate target, String dbName, DBMetrics dbMetrics) {
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new JdbcInvokeInterceptor(dbName, dbMetrics));
        return (NamedParameterJdbcTemplate) proxyFactory.getProxy();
    }

    private static class JdbcInvokeInterceptor implements MethodInterceptor {
        private final String dbName;
        private final DBMetrics dbMetrics;

        public JdbcInvokeInterceptor(String dbName, DBMetrics dbMetrics) {
            this.dbName = dbName;
            this.dbMetrics = dbMetrics;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            try {
                return invocation.proceed();
            } catch (Throwable ex) {
                dbMetrics.databaseErrorInc(dbName, invocation.getMethod().getName(), ex);
                throw ex;
            }
        }
    }
}
