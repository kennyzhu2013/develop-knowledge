package com.cmic.dao;

import com.cmic.BillNew;
import com.cmic.business.businessparam.CallParameter;
import com.cmic.config.AppConfig;

public class BillNewBuilder {
    public static BillNewDo buildInitialInsert(BillNew billNew, BizNumAuthDo bizNumAuthDo) {
        BillNewDo billNewDo = new BillNewDo();
        billNewDo.setId(billNew.getId());
        billNewDo.setLocation(AppConfig.getLocationId());
        billNewDo.setAs_id(AppConfig.getServerId());
        billNewDo.setMobile_id(billNew.getMobileId());
        billNewDo.setChannel_id(billNew.getChannelId());
        billNewDo.setCompany_id(billNew.getCompanyId());
        billNewDo.setCustomer_id(billNew.getCustomerId());
        billNewDo.setApp_id(billNew.getAppId());
        billNewDo.setAccount_type(billNew.getAccountType());
        billNewDo.setOriginal_caller_number(billNew.getOriginalCallerNumber());
        billNewDo.setCaller_number(billNew.getCallerNumber());
        billNewDo.setOriginal_callee_number(billNew.getOriginalCalleeNumber());
        billNewDo.setCallee_number(billNew.getCalleeNumber());
        billNewDo.setBiz_number(billNew.getBizNumber());
        billNewDo.setHandle_start_time(billNew.getHandleStartTime());
        billNewDo.setCall_in_province_id(billNew.getCallInProvinceId());
        billNewDo.setCall_out_province_id(billNew.getCallOutProvinceId());
        billNewDo.setCall_in_700_area_code(billNew.getCallIn700AreaCode());
        billNewDo.setCall_in_area_code(billNew.getCallInAreaCode());
        billNewDo.setCall_out_area_code(billNew.getCallOutAreaCode());
        return billNewDo;
    }

    public static BillNewDo buildFinishUpdate(BillNew billNew, CallParameter callParameter) {
        BillNewDo billNewDo = new BillNewDo();
        billNewDo.setId(billNew.getId());
        billNewDo.setMs_id(billNew.getMsId());
        billNewDo.setHandle_end_time(billNew.getHandleEndTime());
        billNewDo.setCall_forward_time(billNew.getCallForwardTime());
        billNewDo.setFirst_resp_time(billNew.getFirstRespTime());
        billNewDo.setRing_time(billNew.getRingTime());
        billNewDo.setCall_start_time(billNew.getCallStartTime());
        billNewDo.setCall_end_time(billNew.getCallEndTime());
        billNewDo.setCall_duration_s(billNew.getCallDurationS());
        billNewDo.setCall_duration_m(billNew.getCallDurationM());
        billNewDo.setCall_state(billNew.getCallState());
        billNewDo.setFinish_type(billNew.getFinishType());
        billNewDo.setFinish_state(billNew.getFinishState());
        billNewDo.setRecord_type(billNew.getRecordType());
        billNewDo.setQ850(billNew.getQ850());
        billNewDo.setMedia_l_direction(billNew.getMediaLDirection());
        billNewDo.setMedia_r_direction(billNew.getMediaRDirection());
        billNewDo.setPfs_id(billNew.getStoreId());
        billNewDo.setAppend_reason(billNew.getAppendReason());
        return billNewDo;
    }

}
