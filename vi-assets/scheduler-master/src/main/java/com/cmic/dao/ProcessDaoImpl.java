package com.cmic.dao;

import com.cmic.pojo.dto.ProcessPageResponse;
import com.cmic.pojo.entity.Process;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class ProcessDaoImpl {
    @Autowired
    private MongoTemplate mongoTemplate;

    public ProcessPageResponse findByCriteria(String processId, String processName, Integer status, String xmlFileName, int page, int size) {
        Criteria criteria = new Criteria();

        // 添加条件查询
        if (StringUtils.hasLength(processId)) {
            criteria.and("processId").is(processId);
        }
        if (StringUtils.hasLength(processName)) {
            criteria.and("processName").regex(processName, "i");
        }
        if (status != null) {
            criteria.and("status").is(status);
        }
        if (StringUtils.hasLength(xmlFileName)) {
            criteria.and("xmlFileName").is(xmlFileName);
        }

        Query query = new Query(criteria);

        long count = mongoTemplate.count(query, Process.class);

        // 设置分页
        query.skip((page-1) * size); // 跳过前 page * size 条
        query.limit(size); // 限制返回 size 条

        List<Process> processList = mongoTemplate.find(query, Process.class);
        ProcessPageResponse response = new ProcessPageResponse();
        response.setProcessList(processList);
        response.setCount(count);
        return response;
    }
}
