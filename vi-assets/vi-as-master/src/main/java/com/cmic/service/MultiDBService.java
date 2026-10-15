package com.cmic.service;

import com.cmic.dao.db.BaseDB;
import com.cmic.util.DtmfLogger;

import java.util.ArrayList;
import java.util.List;

public class MultiDBService {
    private static final List<BaseDB> billDBList = new ArrayList<>();

    public static void addBillDB(BaseDB db) {
        billDBList.add(db);
    }

    public static int getBillDBCount() {
        return billDBList.size();
    }

    // 如果有3个DB，那么传入dbId是0~2
    public static BaseDB getBillDB(String billId, int dbId) {
        if (dbId >= billDBList.size() || dbId < 0) {
            DtmfLogger.error(String.valueOf(billId), "billDBList size is {}, but got dbId {}", billDBList.size(), dbId);
            return billDBList.get(0);
        }
        DtmfLogger.info(billId, "get bill db by dbId={} success", dbId);
        return billDBList.get(dbId);
    }
}
