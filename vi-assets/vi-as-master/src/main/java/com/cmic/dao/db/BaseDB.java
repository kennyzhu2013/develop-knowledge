package com.cmic.dao.db;

import com.alibaba.druid.TransactionTimeoutException;
import com.alibaba.druid.pool.GetConnectionTimeoutException;
import com.cmic.dao.DBRetryException;
import com.cmic.service.XmodePromExporterService;
import com.common.dao.db.Db;
import com.common.dao.db.DbException;
import com.common.dao.util.DbHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLTimeoutException;

public class BaseDB extends Db {

    private static final int QUERY_TIMEOUT_SECOND = 10;

    public BaseDB(String dbName) {
        super(dbName);
    }

    public final int executeCommandTimeout(String sql, Object[] params) throws DBRetryException, DbException {
        Connection conn = null;
        PreparedStatement stmt = null;

        int var11;
        try {
            conn = this.getConnection();
            stmt = conn.prepareStatement(sql);
            stmt.setQueryTimeout(QUERY_TIMEOUT_SECOND);
            DbHelper.setParams(stmt, params);
            var11 = stmt.executeUpdate();
        } catch (SQLTimeoutException | GetConnectionTimeoutException | TransactionTimeoutException t) {
            String failType;
            if (t instanceof SQLTimeoutException) {
                failType = "sql_timeout";
            } else if (t instanceof GetConnectionTimeoutException) {
                failType = "get_conn_timeout";
            } else {
                failType = "trans_timeout";
            }
            XmodePromExporterService.getInstance().incDbFailCount(failType);
            throw new DBRetryException(String.format("executeCommand error, sql:%s params:%s", sql, DbHelper.toString(params)), t);
        } catch (Throwable var9) {
            XmodePromExporterService.getInstance().incDbFailCount("other");
            Throwable t = var9;
            throw new DbException(String.format("executeCommand error, sql:%s params:%s", sql, DbHelper.toString(params)), t);
        } finally {
            this.releaseResources((ResultSet)null, stmt, conn);
        }

        return var11;
    }
}
