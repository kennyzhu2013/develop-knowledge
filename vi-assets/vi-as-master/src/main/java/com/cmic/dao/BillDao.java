package com.cmic.dao;

import com.cmic.dao.db.BaseDB;
import com.cmic.dao.db.BillDB;
import com.common.dao.db.DbException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BillDao {
    private static final Logger logger = LoggerFactory.getLogger(BillDao.class);

    private static final String INSERT_BILL_FAIL = "INSERT INTO `bill_fail` (`id`, `company_id`, `location`, " +
            "`reason`, `failcount`, `gmt_create`) VALUES (?, ?, ?, ?, ?, ?)";

    public static final String BILL_NEW_TABLENAME = "bill_new";

    public static final String SEQUENCE_PARAMS_TABLENAME = "sequence_param";

    public static void insertBillFial(String billId, int companyId, int location, int reason, int failcount, Timestamp gmtCreate) throws DBRetryException, DbException {
        BillDB.getInstance().executeCommandTimeout(INSERT_BILL_FAIL, new Object[]{billId, companyId, location,
                reason, failcount, gmtCreate});

    }

    public static <T> Pair<String, List<Object>> forInsert(T entity, String tableName) {
        List<Object> lo = new ArrayList<>();
        // 创建拼接对象
        StringBuilder insertMain = new StringBuilder();
        StringBuilder insertValue = new StringBuilder();
        // 首先以select开头，接下来拼接
        insertMain.append("INSERT INTO ").append(tableName).append("(");
        insertValue.append(" values(");
        // 获取当前传入的对象的类
        Class<?> aClass = entity.getClass();
        // 获取当前传入的类的属性
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        for (int i = 0; i < declaredFields.length; i++) {
            // 获取当前类的属性的名字
            String fieldName = declaredFields[i].getName();
            PropertyDescriptor descriptor;
            Object getResult;
            try {
                descriptor = new PropertyDescriptor(fieldName, aClass);
                Method readMethod = descriptor.getReadMethod();
                getResult = readMethod.invoke(entity);
            } catch (Exception e) {
                throw new RuntimeException("reflect error!", e);
            }
            // 判断属性是否为空，不为空则拼接sql
            if (getResult != null) {
                insertMain.append("`").append(fieldName).append("`,");
                insertValue.append("?,");
                lo.add(getResult);
            }
        }
        if (insertMain.charAt(insertMain.length() - 1) == ',') {
            insertMain.deleteCharAt(insertMain.length() - 1);
        }
        insertMain.append(")");
        if (insertValue.charAt(insertValue.length() - 1) == ',') {
            insertValue.deleteCharAt(insertValue.length() - 1);
        }
        insertValue.append(")");
        insertMain.append(insertValue);
        return Pair.of(insertMain.toString(), lo);
    }

    public static <T> int insertTableSelective(T entity, String tableName, BaseDB billDB) throws DBRetryException, DbException {
        Pair<String, List<Object>> pair = forInsert(entity, tableName);
        String insertMain = pair.getLeft();
        List<Object> lo = pair.getRight();
        return billDB.executeCommandTimeout(insertMain, lo.toArray());
    }

    public static <T> Pair<String, List<Object>> forUpdate(T entity, String tableName) {
        List<Object> lo = new ArrayList<>();
        // 创建拼接对象
        StringBuilder insertMain = new StringBuilder();
        // 首先以select开头，接下来拼接
        insertMain.append("update ").append(tableName).append(" set ");
        // 获取当前传入的对象的类
        Class<?> aClass = entity.getClass();
        // 获取当前传入的类的属性
        Field[] declaredFields = entity.getClass().getDeclaredFields();
        Object id = null;
        for (int i = 0; i < declaredFields.length; i++) {
            // 获取当前类的属性的名字
            String fieldName = declaredFields[i].getName();
            PropertyDescriptor descriptor = null;
            Object getResult;
            try {
                descriptor = new PropertyDescriptor(fieldName, aClass);
                Method readMethod = descriptor.getReadMethod();
                getResult = readMethod.invoke(entity);
            } catch (Exception e) {
                throw new RuntimeException("reflect error!", e);
            }

            if ("id".equalsIgnoreCase(fieldName)) {
                id = getResult;
                continue;
            }
            // 判断属性是否为空，不为空则拼接sql
            if (getResult != null) {
                insertMain.append("`").append(fieldName).append("`=?,");
                lo.add(getResult);
            }
        }
        if (insertMain.charAt(insertMain.length() - 1) == ',') {
            insertMain.deleteCharAt(insertMain.length() - 1);
        }
        insertMain.append(" where id = ?");
        lo.add(id);
        return Pair.of(insertMain.toString(), lo);
    }

    public static <T> int updateTableSelectiveById(T entity, String tableName, BaseDB billDB) throws DBRetryException, DbException {
        Pair<String, List<Object>> pair = forUpdate(entity, tableName);
        String insertMain = pair.getLeft();
        List<Object> lo = pair.getRight();
        return billDB.executeCommandTimeout(insertMain, lo.toArray());
    }

}
