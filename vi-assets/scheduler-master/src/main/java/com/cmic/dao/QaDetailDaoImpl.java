package com.cmic.dao;


import com.cmic.pojo.entity.QaDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Repository
public class QaDetailDaoImpl {

    @Qualifier("starRocksJdbcTemplate")
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public QaDetail findByUrl(String url) {
        String sql = "SELECT id, url, content, duration, asr_info FROM qa_detail " +
                     "WHERE url = ? " +
                     "ORDER BY create_time DESC " +
                     "LIMIT 1";

        try {
            return jdbcTemplate.queryForObject(sql, new Object[]{url}, new BeanPropertyRowMapper<>(QaDetail.class));
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            log.error("ck error:", e);
            return null;
        }
    }

    public List<QaDetail> findByDataIds(List<String> dataIds) {
        List<QaDetail> result = new ArrayList<>();
        int batchSize = 1000;

        for (int i = 0; i < dataIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dataIds.size());
            List<String> subList = dataIds.subList(i, end);

            String placeholders = String.join(",", Collections.nCopies(subList.size(), "?"));
            String sql = "SELECT data_id, content, duration, asr_info, create_time FROM qa_detail " +
                         "WHERE data_id IN (" + placeholders + ") ";

            try {
                List<QaDetail> batchResult = jdbcTemplate.query(sql, subList.toArray(),
                        new BeanPropertyRowMapper<>(QaDetail.class));
                result.addAll(batchResult);
            } catch (Exception e) {
                log.error("ck error:", e);
                // 防止多次查询超时
                return result;
            }
        }

        return result;
    }
}
