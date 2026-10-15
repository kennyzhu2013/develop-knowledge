package com.cmic.dao;

import com.cmic.pojo.entity.AIModel;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IAIModelDao extends MongoRepository<AIModel, ObjectId> {
    AIModel findByModel(String model);
}
