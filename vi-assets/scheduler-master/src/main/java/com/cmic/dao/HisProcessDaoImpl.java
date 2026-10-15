package com.cmic.dao;

import com.cmic.pojo.dto.HisProcessPageResponse;
import com.cmic.pojo.entity.HisProcess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

@Repository
public class HisProcessDaoImpl {
    @Autowired
    private MongoTemplate mongoTemplate;

    public HisProcessPageResponse findByCriteria(String processId, String processName,Integer status,  String xmlFileName, int page, int size) {
        Criteria criteria = new Criteria();

        // 添加条件查询
        if (StringUtils.hasLength(processId)) {
            criteria.and("processId").is(processId);
        }
        if (StringUtils.hasLength(processName)) {
            criteria.and("processName").regex(processName, "i");
        }
        if (StringUtils.hasLength(xmlFileName)) {
            criteria.and("xmlFileName").is(xmlFileName);
        }

        if (status != null) {
            criteria.and("status").is(status);
        }


        Query query = new Query(criteria);

        long count = mongoTemplate.count(query, HisProcess.class);

        // 设置分页，按startTime降序排序
        query.with(Sort.by(Sort.Direction.DESC, "startTime"));
        query.skip((long) (page - 1) * size);
        query.limit(size);

        List<HisProcess> hisProcessList = mongoTemplate.find(query, HisProcess.class);
        HisProcessPageResponse response = new HisProcessPageResponse();
        response.setHisProcessList(hisProcessList);
        response.setCount(count);
        return response;
    }
}
