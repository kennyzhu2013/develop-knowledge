package com.cmic.dao;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.cmic.pojo.entity.FileDownHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@DS("dcc_conf")
public class DccConfigDao {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<FileDownHost> selectAllInternFileDownHost() {
        String sql = "select * from file_down_host where host_type = 2";
        //String sql = "select * from file_down_host";
        // 安全查询处理空结果
        List<FileDownHost> result = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> FileDownHost.FileDownHostMap.convert(rs)
        );

        return result.isEmpty() ? null : result;
    }

}
