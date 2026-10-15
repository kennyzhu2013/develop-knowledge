package com.cmic.util;

import com.cmic.BillNew;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.dao.BillDao;
import com.cmic.dao.BillNewBuilder;
import com.cmic.dao.BillNewDo;
import com.cmic.dao.BizNumAuthDo;
import com.cmic.service.XmodePromExporterService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DBLog {
    public static final Logger logger = LoggerFactory.getLogger("dblog");

    private static final String moduleName = "xdtmf";

    public static void createBill(BillNew billNew, BizNumAuthDo bizNumAuthDo, int dbId, int asrLevel) {
        BillNewDo billNewDo = BillNewBuilder.buildInitialInsert(billNew, bizNumAuthDo);
        Pair<String, List<Object>> pair = BillDao.forInsert(billNewDo, BillDao.BILL_NEW_TABLENAME);
        String sql = pair.getLeft();
        List<Object> params = pair.getRight();
        logger.info("[{}][{}][{}][{}][{}][{}]", billNew.getId(), moduleName, dbId, asrLevel, sql,
                StringUtils.join(params, ","));
        DtmfLogger.info(billNew.getId(), "writing to dblog for createBill success");
        XmodePromExporterService.getInstance().incWriteDbLogCount();
    }

    public static void updateBill(BillNew billNew, CallParameter callParameter, int dbId) {
        int asrLevel = callParameter.getAsrlevel();
        BillNewDo billNewDo = BillNewBuilder.buildFinishUpdate(billNew, callParameter);
        Pair<String, List<Object>> pair = BillDao.forUpdate(billNewDo, BillDao.BILL_NEW_TABLENAME);
        String sql = pair.getLeft();
        List<Object> params = pair.getRight();
        logger.info("[{}][{}][{}][{}][{}][{}]", billNew.getId(), moduleName, dbId, asrLevel, sql,
                StringUtils.join(params, ","));
        DtmfLogger.info(billNew.getId(), "writing to dblog for updateBill success");
        XmodePromExporterService.getInstance().incWriteDbLogCount();
    }

}
