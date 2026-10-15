package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.cmic.pojo.entity.Bill;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.text.SimpleDateFormat;
import java.util.List;

@Repository
@DS("vi_bill")
public class BillHistoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public Bill findBillFromHistory(long billid, String date) {
        String sql = "SELECT * FROM bill_" + date + " WHERE id = ?";

        // 安全查询处理空结果
        List<Bill> result = jdbcTemplate.query(
                sql,
                new Object[]{billid},
                (rs, rowNum) -> BillDao.billMap.convert(rs)
        );

        return result.isEmpty() ? null : result.get(0);

    }

    public int createBillHistory(Bill bill) {
        int row = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd");
        String date = sdf.format(bill.getHandleStartTime());
        String sql = "INSERT INTO bill_" + date + "(id,mobile_id,channel_id,company_id,customer_id,app_id,account_type," +
                "location,as_id,ms_id,pfs_id,original_caller_number,caller_number,original_callee_number,callee_number," +
                "biz_number,call_in_province_id,call_out_province_id,call_in_700_area_code,handle_start_time," +
                "handle_end_time,call_forward_time,ring_time,call_start_time,call_end_time,call_duration_s,call_duration_m," +
                "file_duration,left_file_length,right_file_length,first_resp_time,q850,media_l_direction,media_r_direction," +
                "call_state,finish_type,finish_state,append_reason,is_bill_ok,is_record_ok,record_type,quality_inspection_status,call_out_area_code,call_in_area_code" +
                ") " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        return jdbcTemplate.update(sql, bill.getId(), bill.getMobileId(), bill.getChannelId(), bill.getCompanyId(), bill.getCustomerId(), bill.getAppId(), bill.getAccountType(),
                bill.getLocation(), bill.getAsId(), bill.getMsId(), bill.getPfsId(), bill.getOriginalCallerNumber(), bill.getCallerNumber(), bill.getOriginalCalleeNumber(), bill.getCalleeNumber(),
                bill.getBizNumber(), bill.getCallInProvinceId(), bill.getCallOutProvinceId(), bill.getCallIn700AreaCode(), bill.getHandleStartTime(),
                bill.getHandleEndTime(), bill.getCallForwardTime(), bill.getRingTime(), bill.getCallStartTime(), bill.getCallEndTime(), bill.getCallDurationS(), bill.getCallDurationM(),
                bill.getFileDuration(), bill.getLeftFileLength(), bill.getRightFileLength(), bill.getFirstRespTime(), bill.getQ850(), bill.getMediaLDirection(), bill.getMediaRDirection(),
                bill.getCallState(), bill.getFinishType(), bill.getFinishState(), bill.getAppendReason(), bill.getIsBillOk(), bill.getIsRecordOk(),
                bill.getRecordType(), bill.getQualityInspectionStatus(), bill.getCallOutAreaCode(), bill.getCallInAreaCode());
    }
}
