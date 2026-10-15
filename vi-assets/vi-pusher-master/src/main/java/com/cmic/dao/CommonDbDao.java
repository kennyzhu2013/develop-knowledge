package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@DS("vi_bill")
public class CommonDbDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;


    public List<Map<String, Object>> checkTableExistence(String dateString) {
        String tableName = "bill_" + dateString;
        String sql = "SHOW TABLES LIKE ?";

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql, tableName);

        return result.isEmpty() ? null : result;

    }

}
