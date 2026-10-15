package com.cmic.service;

import com.cmic.BillNew;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.business.businessparam.subparam.BindDetail;
import com.cmic.dao.*;
import com.cmic.dao.db.BaseDB;
import com.cmic.dao.db.BillDB;
import com.cmic.pojo.SequenceParamsBill;
import com.cmic.util.AnalyseLog;
import com.cmic.util.DtmfLogger;
import com.common.dao.db.DbException;

// import static com.cmic.dao.XBindInfoBuilder.SIMPLE_DATE_FORMAT;

/**
 * @author XXX
 * @date 2023-9-15
 */
public class BillService {

    public static int updateBill(BillNew billNew, CallParameter callParameter, int dbId) throws DBRetryException, DbException {
        long startTime = System.currentTimeMillis();
        BaseDB billDB = MultiDBService.getBillDB(billNew.getId(), dbId);
        DtmfLogger.info(billNew.getId(), "update bill start, final state:{}, appId:{}", billNew.getFinishState(), billNew.getCustomerId());
        BillNewDo billNewDo = BillNewBuilder.buildFinishUpdate(billNew, callParameter);
        int updateCount = BillDao.updateTableSelectiveById(billNewDo, BillDao.BILL_NEW_TABLENAME, billDB);
        long endTime = System.currentTimeMillis();
        long timeUse = endTime - startTime;
        XmodePromExporterService.getInstance().setDbUseTime("lastUpdate", timeUse);
        DtmfLogger.info(billNew.getId(), "update bill complete, time use:{}, updateCount:{}", timeUse, updateCount);
        return updateCount;
    }

    public static int createBill(BillNew billNew, BizNumAuthDo bizNumAuthDo, int dbId) throws DBRetryException, DbException {
        long startTime = System.currentTimeMillis();
        BaseDB billDB = MultiDBService.getBillDB(billNew.getId(), dbId);
        BillNewDo billNewDo = BillNewBuilder.buildInitialInsert(billNew, bizNumAuthDo);
        int insertCount = BillDao.insertTableSelective(billNewDo, BillDao.BILL_NEW_TABLENAME, billDB);
        long endTime = System.currentTimeMillis();
        long timeUse = endTime - startTime;
        XmodePromExporterService.getInstance().setDbUseTime("firstInsert", timeUse);
        DtmfLogger.info(billNew.getId(), "insert new bill complete, time use:{}, insertCount:{}", timeUse, insertCount);
        return insertCount;
    }

    // public static int updateSequenceBill(SequenceParamsBill sequenceParamsBill, Bill bill) throws DBRetryException, DbException {
    //     long startTime = System.currentTimeMillis();
    //     SequenceParamDo sequenceParamDo = SequenceParamBuilder.buildInitialInsert(sequenceParamsBill);
    //     String realSequenceTableName = SequenceTableSplittingPlugin.getRealTableNames(BillDao.SEQUENCE_PARAMS_TABLENAME, bill);
    //     // CommonDb.getInstance() ֻ����history��
    //     int insertCount = BillDao.insertTableSelective(sequenceParamDo, realSequenceTableName, BillDB.getInstance());
    //     long endTime = System.currentTimeMillis();
    //     long timeUse = endTime - startTime;
    //     XmodePromExporterService.getInstance().setDbUseTime("insertSequenceBill", timeUse);
    //     DtmfLogger.info(bill.getBillId(), "insert sequenceBill complete, time use:{}, insertCount:{}", timeUse, insertCount);
    //     return insertCount;
    // }

    // public static void createXBindInfo(Bill bill, BindDetail bindDetail) {
    //     XBindInfoDo xBindInfoDo = null;
    //     try {
    //         xBindInfoDo = XBindInfoBuilder.buildInitBindInfo(bill, bindDetail);
    //         String currentDay = SIMPLE_DATE_FORMAT.get().format(bill.getStartHandleTime().getTime());
    //         if (xBindInfoDo != null) {
    //             //BillDao.insertTableSelective(xBindInfoDo, "x_bind_info_" + currentDay);
    //             AnalyseLog.printBusinessLog("XBindInfo", "[{}] [{}] [{}] [{}] [{}] [{}] [{}] [{}] [{}]", xBindInfoDo.getId(), xBindInfoDo.getBindId(),  xBindInfoDo.getTelA(), xBindInfoDo.getBindMode(), xBindInfoDo.getTelX(), xBindInfoDo.getTelY(), xBindInfoDo.getTelB(), xBindInfoDo.getBindTime(),  xBindInfoDo.getExpTime());
    //         } else {
    //             DtmfLogger.info(String.valueOf(bill.getBillId()), "customerid={} bindInfo empty skip insert", bill.getAppId());
    //         }
    //     } catch (Exception e) {
    //         DtmfLogger.error(String.valueOf(bill.getBillId()), "customerid={} insert x_bind_info fail bindInfo : {}", bill.getAppId(), xBindInfoDo, e);
    //     }
    // }

}
