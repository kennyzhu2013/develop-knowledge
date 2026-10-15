package com.cmic.dao;

import com.common.dao.db.ResultSetConvertor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * caller_num_auth表记录结构
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-13
 */

@Data
@AllArgsConstructor
@ToString
public class BizNumAuthDo {


    private Long id;
    private String num;
    private Integer matchType;
    private int companyId;
    private int customerId;
    private Timestamp createTime;
    private Timestamp updateTime;

    /**
     * BUILDER：用于将数据库查询结果转换为 CallerNumAuth 对象
     */
    public static final ResultSetConvertor<BizNumAuthDo> BUILDER = new ResultSetConvertor<BizNumAuthDo>() {
        @Override
        public BizNumAuthDo convert(ResultSet rs) throws SQLException {
            return new BizNumAuthDo(
                    rs.getLong("id"),
                    rs.getString("num"),
                    rs.getInt("match_type"),
                    rs.getInt("company_id"),
                    rs.getInt("customer_id"),
                    rs.getTimestamp("create_time"),
                    rs.getTimestamp("update_time")
            );
        }
    };

}
