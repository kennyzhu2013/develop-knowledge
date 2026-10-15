package com.cmic.dao;

import com.cmic.pojo.bo.AIModelWithType;
import com.cmic.pojo.dto.AiModelPageResponse;
import com.cmic.pojo.entity.AIModel;
import com.cmic.pojo.entity.AIType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class AIModelDaoImpl {
    @Autowired
    private MongoTemplate mongoTemplate;

    public AiModelPageResponse findByCriteria(String model, AIType aiType, String desc, int page, int size) {
        Criteria criteria = new Criteria();
        // 添加条件查询
        if (StringUtils.hasLength(model)) {
            criteria.and("model").is(model);
        }
        if (StringUtils.hasLength(desc)) {
            criteria.and("desc").regex(desc, "i");
        }
        if (aiType != null) {
            criteria.and("type").is(aiType.getName());
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria), // 按条件过滤
                Aggregation.lookup("ai_type", "type", "name", "aiTypeInfo"), // 关联表 AIType
                Aggregation.unwind("aiTypeInfo", true), // 展开结果，允许 null
                Aggregation.project("desc", "model", "requestParameters", "httpUrl", "responseParameters", "type", "authentication")
                        .and("aiTypeInfo._id").as("aiTypeId")
        );

        aggregation.skip((page-1) * size);
        aggregation.limit(size);

        Query query = new Query(criteria);

        long count = mongoTemplate.count(query, AIModel.class);

        // 执行查询
        AggregationResults<AIModelWithType> results =
                mongoTemplate.aggregate(aggregation, "ai_model", AIModelWithType.class);

        AiModelPageResponse response = new AiModelPageResponse();
        response.setAiModels(results.getMappedResults());
        response.setCount(count);
        return response;
    }
}
