package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.cmic.pojo.entity.Bill;
import com.common.dao.db.ResultSetConvertor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
@DS("vi_bill")
public class BillDao {


    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BillHistoryDao billHistoryDao;


    public static final ResultSetConvertor<Bill> billMap = new ResultSetConvertor<Bill>() {
        @Override
        public Bill convert(ResultSet rs) throws SQLException {
            Bill bill = new Bill();
            bill.setId(rs.getString("id"));
            bill.setMobileId(rs.getInt("mobile_id"));
            bill.setChannelId(rs.getInt("channel_id"));
            bill.setCompanyId(rs.getInt("company_id"));
            bill.setCustomerId(rs.getInt("customer_id"));
            bill.setAppId(rs.getString("app_id"));
            bill.setAccountType(rs.getInt("account_type"));
            bill.setLocation(rs.getInt("location"));
            bill.setAsId(rs.getInt("as_id"));
            bill.setMsId(rs.getInt("ms_id"));
            bill.setPfsId(rs.getInt("pfs_id"));
            bill.setOriginalCallerNumber(rs.getString("original_caller_number"));
            bill.setCallerNumber(rs.getString("caller_number"));
            bill.setOriginalCalleeNumber(rs.getString("original_callee_number"));
            bill.setCalleeNumber(rs.getString("callee_number"));
            bill.setBizNumber(rs.getString("biz_number"));
            bill.setCallInProvinceId(rs.getInt("call_in_province_id"));
            bill.setCallOutProvinceId(rs.getInt("call_out_province_id"));
            bill.setCallIn700AreaCode(rs.getString("call_in_700_area_code"));
            bill.setHandleStartTime(rs.getTimestamp("handle_start_time"));
            bill.setHandleEndTime(rs.getTimestamp("handle_end_time"));
            bill.setCallForwardTime(rs.getTimestamp("call_forward_time"));
            bill.setRingTime(rs.getTimestamp("ring_time"));
            bill.setCallStartTime(rs.getTimestamp("call_start_time"));
            bill.setCallEndTime(rs.getTimestamp("call_end_time"));
            bill.setCallDurationS(rs.getInt("call_duration_s"));
            bill.setCallDurationM(rs.getInt("call_duration_m"));
            bill.setFileDuration(rs.getInt("file_duration"));
            bill.setLeftFileLength(rs.getInt("left_file_length"));
            bill.setRightFileLength(rs.getInt("right_file_length"));
            bill.setFirstRespTime(rs.getTimestamp("first_resp_time"));
            bill.setQ850(rs.getInt("q850"));
            bill.setMediaLDirection(rs.getInt("media_l_direction"));
            bill.setMediaRDirection(rs.getInt("media_r_direction"));
            bill.setCallState(rs.getInt("call_state"));
            bill.setFinishType(rs.getInt("finish_type"));
            bill.setFinishState(rs.getInt("finish_state"));
            bill.setAppendReason(rs.getInt("append_reason"));
            bill.setIsBillOk(rs.getInt("is_bill_ok"));
            bill.setIsRecordOk(rs.getInt("is_record_ok"));
            bill.setRecordType(rs.getInt("record_type"));
            bill.setQualityInspectionStatus(rs.getInt("quality_inspection_status"));
            bill.setCallOutAreaCode(rs.getString("call_out_area_code"));
            bill.setCallInAreaCode(rs.getString("call_in_area_code"));
            return bill;
        }
    };


    public Bill findBillNewById(String id) {
        String sql = "SELECT * FROM bill_new WHERE id = ?";

        // 安全查询处理空结果
        List<Bill> result = jdbcTemplate.query(
                sql,
                new Object[]{id},
                (rs, rowNum) -> billMap.convert(rs)
        );

        return result.isEmpty() ? null : result.get(0);
    }

    public int deleteBillNew(Bill bill) {
        String sql = "DELETE FROM bill_new WHERE id = ?";
        return jdbcTemplate.update(sql, bill.getId());
    }

    public void updateBillNewRecord(Bill bill) {
        String sql = "UPDATE bill_new SET left_file_length = ?, right_file_length = ?, file_duration = ? WHERE id = ? ";
        Object[] args = {
                bill.getLeftFileLength(),
                bill.getRightFileLength(),
                bill.getFileDuration(),
                bill.getId()
        };
        jdbcTemplate.update(sql, args);
    }

    public void updateBillNewInspectionStatus(Bill bill) {
        String sql = "UPDATE bill_new SET quality_inspection_status = ? WHERE id = ? ";
        Object[] args = {
                bill.getQualityInspectionStatus(),
                bill.getId()
        };
        jdbcTemplate.update(sql, args);
    }

    public int createBillHistory(Bill bill) {
        return billHistoryDao.createBillHistory(bill);
    }

    public List<Bill> getCreateTaskFailBillNew(String startDate, String endDate, int limit, String companyId, String lastId) {
        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT * FROM bill_new WHERE quality_inspection_status = -1 " +
                        "AND handle_start_time BETWEEN ? AND ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(startDate);
        params.add(endDate);

        if (StringUtils.isNotBlank(companyId)) {
            try {
                int compId = Integer.parseInt(companyId);
                sqlBuilder.append("AND company_id = ? ");
                params.add(compId);
            } catch (NumberFormatException e) {
                log.warn("Invalid companyId format: {}, ignoring", companyId);
            }
        }

        if (StringUtils.isNotBlank(lastId)) {
            sqlBuilder.append("AND id > ? ");
            params.add(lastId);
        }

        sqlBuilder.append("ORDER BY id LIMIT ?");
        params.add(limit);

        List<Bill> result = jdbcTemplate.query(
                sqlBuilder.toString(),
                params.toArray(),
                (rs, rowNum) -> billMap.convert(rs)
        );

        return result;
    }

}

